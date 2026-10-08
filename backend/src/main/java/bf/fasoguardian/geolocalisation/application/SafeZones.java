package bf.fasoguardian.geolocalisation.application;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import bf.fasoguardian.audit.JournalAudit;
import bf.fasoguardian.audit.JournalAudit.Resultat;
import bf.fasoguardian.famille.AccesEnfant;
import bf.fasoguardian.geolocalisation.domaine.Coordonnee;
import bf.fasoguardian.geolocalisation.domaine.PlageHoraire;
import bf.fasoguardian.geolocalisation.domaine.SafeZone;
import bf.fasoguardian.geolocalisation.domaine.SafeZone.Categorie;
import bf.fasoguardian.geolocalisation.domaine.SafeZone.Statut;
import bf.fasoguardian.geolocalisation.domaine.SuiviZone;
import bf.fasoguardian.geolocalisation.domaine.ZoneCirculaire;
import bf.fasoguardian.geolocalisation.domaine.ZonePolygonale;
import bf.fasoguardian.geolocalisation.infrastructure.DepotZones;
import bf.fasoguardian.identite.SecondFacteur;
import bf.fasoguardian.identite.SecondFacteur.ActionSensible;
import bf.fasoguardian.plateforme.erreurs.CodeErreur;
import bf.fasoguardian.plateforme.erreurs.ErreurMetier;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Safe Zones d'un enfant (US-PAR-007) : création, modification, suspension, réactivation, suppression.
 * Toute action qui modifie ou affaiblit la surveillance exige le second facteur MODIFIER_SAFE_ZONE
 * (FG-DOC-06 §8.1) ; la réactivation, qui la rétablit, ne l'exige pas. Tout est journalisé.
 */
@Service
public class SafeZones {

    private static final String ROLE = "PARENT";

    public enum Forme {
        CERCLE,
        POLYGONE
    }

    public record Point(double latitude, double longitude) {
    }

    /** @param jours 1 = lundi … 7 = dimanche ; {@code debut} et {@code fin} au format HH:mm, égaux pour la journée entière */
    public record SaisieZone(Forme forme, String nom, Categorie categorie, Point centre, Integer rayonM,
            List<Point> sommets, List<Integer> jours, String debut, String fin, int toleranceS) {
    }

    /**
     * @param dansLaPlage   la zone est surveillée en ce moment (active et dans sa plage horaire)
     * @param sortieEnCours une sortie a été signalée et l'enfant n'est pas revenu
     */
    public record ZoneVue(UUID id, Forme forme, String nom, Categorie categorie, Point centre, Integer rayonM,
            List<Point> sommets, List<Integer> jours, String debut, String fin, int toleranceS, Statut statut,
            boolean dansLaPlage, boolean sortieEnCours) {
    }

    public record ZonesVue(List<ZoneVue> zones, int maximum) {
    }

    private final DepotZones depot;
    private final AccesEnfant acces;
    private final SecondFacteur secondFacteur;
    private final JournalAudit journal;
    private final Clock horloge;
    private final ZoneId fuseau;
    private final int maximum;
    // Zones par enfant, consultées à chaque position reçue ; vidées à toute modification (FG-DOC-07 §7.2).
    private final Cache<UUID, List<SafeZone>> parEnfant =
            Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(10)).maximumSize(50_000).build();

    SafeZones(DepotZones depot, AccesEnfant acces, SecondFacteur secondFacteur, JournalAudit journal, Clock horloge,
            @Value("${fasoguardian.fuseau:Africa/Ouagadougou}") ZoneId fuseau,
            @Value("${fasoguardian.geolocalisation.zones-maximum:3}") int maximum) {
        this.depot = depot;
        this.acces = acces;
        this.secondFacteur = secondFacteur;
        this.journal = journal;
        this.horloge = horloge;
        this.fuseau = fuseau;
        this.maximum = maximum;
    }

    @Transactional(readOnly = true)
    public ZonesVue zones(UUID tuteurId, UUID enfantId) {
        acces.exigerTuteur(tuteurId, enfantId);
        return new ZonesVue(depot.deLEnfant(enfantId).stream().map(this::vue).toList(), maximum);
    }

    @Transactional
    public ZoneVue creer(UUID tuteurId, UUID enfantId, SaisieZone saisie, String codeSecondFacteur) {
        acces.exigerTuteur(tuteurId, enfantId);
        if (depot.deLEnfant(enfantId).size() >= maximum) {
            throw new ErreurMetier(CodeErreur.ZONES_MAXIMUM_ATTEINT,
                    "Votre offre permet " + maximum + " Safe Zones. Supprimez-en une pour en créer une autre.");
        }
        SafeZone zone = construire(UUID.randomUUID(), enfantId, saisie, Statut.ACTIVE, 0);
        secondFacteur.exiger(tuteurId, ActionSensible.MODIFIER_SAFE_ZONE, codeSecondFacteur);
        depot.creer(zone, horloge.instant());
        return apres(tuteurId, "SAFE_ZONE_CREEE", zone);
    }

    @Transactional
    public ZoneVue modifier(UUID tuteurId, UUID enfantId, UUID zoneId, SaisieZone saisie, String codeSecondFacteur) {
        SafeZone actuelle = zone(tuteurId, enfantId, zoneId);
        SafeZone zone = construire(zoneId, enfantId, saisie, actuelle.statut(), actuelle.version());
        secondFacteur.exiger(tuteurId, ActionSensible.MODIFIER_SAFE_ZONE, codeSecondFacteur);
        if (!depot.remplacer(zone, horloge.instant())) {
            throw conflit();
        }
        // La forme ou la plage a pu changer : le suivi en cours ne vaut plus.
        depot.oublierSuivi(zoneId);
        return apres(tuteurId, "SAFE_ZONE_MODIFIEE", zone);
    }

    /** La configuration est conservée ; aucune sortie n'est signalée tant que la zone est suspendue. */
    @Transactional
    public ZoneVue suspendre(UUID tuteurId, UUID enfantId, UUID zoneId, String codeSecondFacteur) {
        SafeZone zone = zone(tuteurId, enfantId, zoneId);
        secondFacteur.exiger(tuteurId, ActionSensible.MODIFIER_SAFE_ZONE, codeSecondFacteur);
        if (!zone.active() || !depot.changerStatut(zoneId, Statut.SUSPENDUE, zone.version(), horloge.instant())) {
            throw conflit();
        }
        depot.oublierSuivi(zoneId);
        return apres(tuteurId, "SAFE_ZONE_SUSPENDUE", zone);
    }

    @Transactional
    public ZoneVue reactiver(UUID tuteurId, UUID enfantId, UUID zoneId) {
        SafeZone zone = zone(tuteurId, enfantId, zoneId);
        if (zone.active() || !depot.changerStatut(zoneId, Statut.ACTIVE, zone.version(), horloge.instant())) {
            throw conflit();
        }
        return apres(tuteurId, "SAFE_ZONE_REACTIVEE", zone);
    }

    @Transactional
    public void supprimer(UUID tuteurId, UUID enfantId, UUID zoneId, String codeSecondFacteur) {
        SafeZone zone = zone(tuteurId, enfantId, zoneId);
        secondFacteur.exiger(tuteurId, ActionSensible.MODIFIER_SAFE_ZONE, codeSecondFacteur);
        depot.supprimer(zoneId);
        parEnfant.invalidate(enfantId);
        journal.consigner(tuteurId, ROLE, "SAFE_ZONE_SUPPRIMEE", "SAFE_ZONE", zone.id().toString(), Resultat.SUCCES);
    }

    /** Zones de l'enfant pour l'évaluation des positions, servies depuis le cache. */
    List<SafeZone> pourEvaluation(UUID enfantId) {
        return parEnfant.get(enfantId, depot::deLEnfant);
    }

    LocalDateTime heureLocale(Instant instant) {
        return LocalDateTime.ofInstant(instant, fuseau);
    }

    // -------------------------------------------------------------------- aides

    private ZoneVue apres(UUID tuteurId, String action, SafeZone zone) {
        parEnfant.invalidate(zone.enfantId());
        journal.consigner(tuteurId, ROLE, action, "SAFE_ZONE", zone.id().toString(), Resultat.SUCCES);
        return vue(depot.parId(zone.id()).orElseThrow());
    }

    private SafeZone zone(UUID tuteurId, UUID enfantId, UUID zoneId) {
        acces.exigerTuteur(tuteurId, enfantId);
        return depot.parId(zoneId).filter(zone -> zone.enfantId().equals(enfantId)).orElseThrow(
                () -> new ErreurMetier(CodeErreur.RESSOURCE_INTROUVABLE, "Safe Zone introuvable."));
    }

    private static SafeZone construire(UUID id, UUID enfantId, SaisieZone saisie, Statut statut, long version) {
        try {
            PlageHoraire plage = new PlageHoraire(
                    saisie.jours() == null ? null : saisie.jours().stream().map(DayOfWeek::of).collect(Collectors.toSet()),
                    LocalTime.parse(saisie.debut()), LocalTime.parse(saisie.fin()));
            if (saisie.forme() == Forme.CERCLE) {
                if (saisie.centre() == null || saisie.rayonM() == null) {
                    throw new IllegalArgumentException("Une zone circulaire a un centre et un rayon");
                }
                return new ZoneCirculaire(id, enfantId, saisie.nom(), saisie.categorie(), plage, saisie.toleranceS(), statut,
                        version, coordonnee(saisie.centre()), saisie.rayonM());
            }
            return new ZonePolygonale(id, enfantId, saisie.nom(), saisie.categorie(), plage, saisie.toleranceS(), statut,
                    version, saisie.sommets() == null ? null : saisie.sommets().stream().map(SafeZones::coordonnee).toList());
        } catch (IllegalArgumentException | DateTimeException | NullPointerException erreur) {
            throw new ErreurMetier(CodeErreur.REQUETE_INVALIDE, erreur instanceof IllegalArgumentException
                    ? erreur.getMessage() + "." : "Les jours vont de 1 à 7 et les heures s'écrivent HH:mm.");
        }
    }

    private static Coordonnee coordonnee(Point point) {
        return new Coordonnee(point.latitude(), point.longitude());
    }

    private ZoneVue vue(SafeZone zone) {
        boolean dansLaPlage = zone.active() && zone.plage().contient(heureLocale(horloge.instant()));
        boolean sortie = dansLaPlage && depot.suivi(zone.id()).map(SuiviZone::sortieSignalee).orElse(false);
        List<Integer> jours = zone.plage().jours().stream().map(DayOfWeek::getValue).sorted().toList();
        if (zone instanceof ZoneCirculaire cercle) {
            return new ZoneVue(zone.id(), Forme.CERCLE, zone.nom(), zone.categorie(),
                    new Point(cercle.centre().latitude(), cercle.centre().longitude()), cercle.rayonM(), null, jours,
                    zone.plage().debut().toString(), zone.plage().fin().toString(), zone.toleranceS(), zone.statut(),
                    dansLaPlage, sortie);
        }
        return new ZoneVue(zone.id(), Forme.POLYGONE, zone.nom(), zone.categorie(), null, null,
                ((ZonePolygonale) zone).sommets().stream().map(c -> new Point(c.latitude(), c.longitude())).toList(), jours,
                zone.plage().debut().toString(), zone.plage().fin().toString(), zone.toleranceS(), zone.statut(),
                dansLaPlage, sortie);
    }

    private static ErreurMetier conflit() {
        return new ErreurMetier(CodeErreur.CONFLIT, "Cette zone a changé entre-temps. Rechargez la liste.");
    }
}
