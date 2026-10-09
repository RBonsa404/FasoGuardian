package bf.fasoguardian.dispositifs.application;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import bf.fasoguardian.audit.JournalAudit;
import bf.fasoguardian.audit.JournalAudit.Resultat;
import bf.fasoguardian.dispositifs.VersionsLogicielles;
import bf.fasoguardian.dispositifs.infrastructure.DepotBracelets;
import bf.fasoguardian.dispositifs.infrastructure.DepotOta;
import bf.fasoguardian.dispositifs.infrastructure.DepotOta.Avancement;
import bf.fasoguardian.dispositifs.infrastructure.DepotOta.Campagne;
import bf.fasoguardian.dispositifs.infrastructure.DepotOta.Porte;
import bf.fasoguardian.identite.LiensTutelle;
import bf.fasoguardian.notifications.Notifications;
import bf.fasoguardian.notifications.Notifications.Message;
import bf.fasoguardian.notifications.Notifications.Urgence;
import bf.fasoguardian.plateforme.erreurs.CodeErreur;
import bf.fasoguardian.plateforme.erreurs.ErreurMetier;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Campagnes de mise à jour du logiciel embarqué (US-PAR-013, FG-DOC-08 §7, écran 68). Une image n'entre en
 * campagne que si son manifeste est signé par la clé de publication du logiciel ; chaque bracelet refait la
 * vérification avant d'installer. Le déploiement avance par vagues lancées à la main (1 %, 10 %, 25 %, tout
 * le parc), et les parents sont informés avant l'installation.
 */
@Service
public class CampagnesOta implements VersionsLogicielles {

    /** Part cumulée du parc visée à chaque vague. */
    static final int[] VAGUES = {1, 10, 25, 100};
    /** Un bracelet éteint ou hors réseau reçoit de nouveau la demande, tant que la campagne est en cours. */
    static final Duration DELAI_DE_RELANCE = Duration.ofHours(1);
    private static final String ROLE = "SAV";

    public enum EtatVague {
        TERMINEE,
        EN_COURS,
        /** Vague suivante : elle attend son déclenchement manuel. */
        PRETE,
        PLANIFIEE
    }

    /** @param cibles bracelets visés, ou estimés pour une vague qui n'est pas encore lancée */
    public record VagueVue(int numero, int pourcentage, int cibles, int installes, EtatVague etat) {
    }

    public record CampagneVue(UUID id, String version, long tailleOctets, String note, String statut, int vagueCourante, Instant creeeLe,
            List<VagueVue> vagues) {
    }

    public record Image(String version, String urlImage, long tailleOctets, String sha256, String signature, String note) {
    }

    private final DepotOta depot;
    private final DepotBracelets bracelets;
    private final CommandesBracelet commandes;
    private final LiensTutelle liens;
    private final Notifications notifications;
    private final JournalAudit journal;
    private final Clock horloge;
    private final PublicKey clePublication;

    CampagnesOta(DepotOta depot, DepotBracelets bracelets, CommandesBracelet commandes, LiensTutelle liens, Notifications notifications,
            JournalAudit journal, Clock horloge, @Value("${fasoguardian.ota.cle-publique:}") String clePublique) {
        this.depot = depot;
        this.bracelets = bracelets;
        this.commandes = commandes;
        this.liens = liens;
        this.notifications = notifications;
        this.journal = journal;
        this.horloge = horloge;
        this.clePublication = lire(clePublique);
    }

    /** Texte signé par la clé de publication : il lie la version à la taille et à l'empreinte de l'image. */
    public static String manifeste(String version, long tailleOctets, String sha256) {
        return "FG-OTA|" + version + "|" + tailleOctets + "|" + sha256.toLowerCase(Locale.ROOT);
    }

    @Transactional(readOnly = true)
    public List<CampagneVue> campagnes() {
        int parc = depot.parcPorte().size();
        return depot.campagnes().stream().map(campagne -> vue(campagne, parc)).toList();
    }

    /** Enregistre une image à déployer. Une image dont la signature ne se vérifie pas est refusée et journalisée. */
    @Transactional(noRollbackFor = ErreurMetier.class)
    public CampagneVue preparer(UUID agentId, Image image) {
        if (clePublication == null) {
            throw new ErreurMetier(CodeErreur.CONFLIT,
                    "La clé de publication du logiciel embarqué n'est pas configurée : aucune image ne peut être acceptée.");
        }
        String sha256 = image.sha256().toLowerCase(Locale.ROOT);
        if (!signatureValide(manifeste(image.version(), image.tailleOctets(), sha256), image.signature())) {
            journal.consigner(agentId, ROLE, "IMAGE_OTA_REFUSEE", "LOGICIEL_EMBARQUE", image.version(), Resultat.REFUS);
            throw new ErreurMetier(CodeErreur.REQUETE_INVALIDE,
                    "La signature de l'image ne correspond pas à la clé de publication : l'image est refusée.");
        }
        if (depot.versionDejaPubliee(image.version())) {
            throw new ErreurMetier(CodeErreur.CONFLIT, "Une campagne existe déjà pour cette version.");
        }
        Campagne campagne = new Campagne(UUID.randomUUID(), image.version(), image.urlImage(), image.tailleOctets(), sha256,
                image.signature(), image.note() == null || image.note().isBlank() ? null : image.note().strip(), "PREPAREE", 0,
                horloge.instant());
        depot.creer(campagne, agentId);
        journal.consigner(agentId, ROLE, "CAMPAGNE_OTA_PREPAREE", "LOGICIEL_EMBARQUE", image.version(), Resultat.SUCCES);
        return vue(campagne, depot.parcPorte().size());
    }

    /** Lance la vague suivante : les bracelets visés reçoivent la demande signée, leurs parents sont informés. */
    @Transactional
    public CampagneVue lancerLaVagueSuivante(UUID agentId, UUID campagneId) {
        Campagne campagne = trouver(campagneId);
        if (campagne.statut().equals("EN_PAUSE")) {
            throw new ErreurMetier(CodeErreur.CONFLIT, "La campagne est en pause : reprenez-la avant de lancer une vague.");
        }
        if (campagne.statut().equals("TERMINEE") || campagne.vagueCourante() >= VAGUES.length) {
            throw new ErreurMetier(CodeErreur.CONFLIT, "Toutes les vagues de cette campagne ont été lancées.");
        }
        // Deux campagnes en cours se disputeraient les mêmes bracelets : une seule déploie à la fois.
        depot.autreEnCours(campagneId).ifPresent(version -> {
            throw new ErreurMetier(CodeErreur.CONFLIT,
                    "La campagne " + version + " est en cours : mettez-la en pause ou attendez sa fin avant de lancer celle-ci.");
        });
        int vague = campagne.vagueCourante() + 1;
        Instant maintenant = horloge.instant();
        Set<UUID> dejaVises = new HashSet<>(depot.cibles(campagneId));
        List<Porte> restants = depot.parcPorte().stream()
                .filter(porte -> !dejaVises.contains(porte.braceletId()) && !campagne.version().equals(porte.version())).toList();
        int aViser = Math.max(0, quota(vague, dejaVises.size() + restants.size()) - dejaVises.size());
        Map<String, Object> demande = demande(campagne);
        for (Porte porte : restants.subList(0, Math.min(aViser, restants.size()))) {
            depot.viser(campagneId, porte.braceletId(), vague, maintenant);
            bracelets.findById(porte.braceletId()).ifPresent(bracelet -> {
                commandes.mettreAJour(bracelet, demande);
                Message message = new Message("MISE_A_JOUR_BRACELET", "Mise à jour du bracelet", "une mise à jour (" + campagne.version()
                        + ") va être installée sur le bracelet " + bracelet.numeroSerie() + ". Il reste utilisable ; gardez-le chargé.",
                        "/enfants/" + porte.enfantId() + "/bracelet", null);
                liens.tuteursActifsDe(porte.enfantId()).forEach(tuteur -> notifications.notifier(tuteur, Urgence.INFORMATION, message));
            });
        }
        depot.noterVague(campagneId, vague);
        journal.consigner(agentId, ROLE, "VAGUE_OTA_LANCEE", "LOGICIEL_EMBARQUE", campagne.version() + " · vague " + vague, Resultat.SUCCES);
        clorSiTerminee(campagneId);
        return vue(trouver(campagneId), depot.parcPorte().size());
    }

    /** Suspend les relances : aucune nouvelle demande ne part tant que la campagne n'est pas reprise. */
    @Transactional
    public CampagneVue mettreEnPause(UUID agentId, UUID campagneId) {
        Campagne campagne = trouver(campagneId);
        if (!campagne.statut().equals("EN_COURS")) {
            throw new ErreurMetier(CodeErreur.CONFLIT, "Seule une campagne en cours peut être mise en pause.");
        }
        depot.changerStatut(campagneId, "EN_PAUSE");
        journal.consigner(agentId, ROLE, "CAMPAGNE_OTA_EN_PAUSE", "LOGICIEL_EMBARQUE", campagne.version(), Resultat.SUCCES);
        return vue(trouver(campagneId), depot.parcPorte().size());
    }

    @Transactional
    public CampagneVue reprendre(UUID agentId, UUID campagneId) {
        Campagne campagne = trouver(campagneId);
        if (!campagne.statut().equals("EN_PAUSE")) {
            throw new ErreurMetier(CodeErreur.CONFLIT, "Cette campagne n'est pas en pause.");
        }
        depot.changerStatut(campagneId, "EN_COURS");
        journal.consigner(agentId, ROLE, "CAMPAGNE_OTA_REPRISE", "LOGICIEL_EMBARQUE", campagne.version(), Resultat.SUCCES);
        return vue(trouver(campagneId), depot.parcPorte().size());
    }

    @Override
    @Transactional
    public void versionConstatee(UUID braceletId, String version) {
        bracelets.findById(braceletId).filter(bracelet -> bracelet.constaterVersion(version)).ifPresent(bracelet -> journal
                .consigner(null, "SYSTEME", "LOGICIEL_EMBARQUE_CONSTATE", "BRACELET", braceletId.toString(), Resultat.SUCCES));
        depot.noterInstallation(braceletId, version, horloge.instant()).forEach(this::clorSiTerminee);
    }

    @Scheduled(fixedDelayString = "${fasoguardian.ota.relance:PT10M}")
    @SchedulerLock(name = "dispositifs-relance-ota", lockAtMostFor = "PT10M")
    @Transactional
    public void planifierLesRelances() {
        relancer();
    }

    /** Redemande l'installation aux bracelets visés qui n'ont pas encore changé de version. @return demandes réémises */
    @Transactional
    public int relancer() {
        Instant maintenant = horloge.instant();
        int relances = 0;
        for (UUID[] cible : depot.aRelancer(maintenant.minus(DELAI_DE_RELANCE))) {
            Campagne campagne = depot.campagne(cible[0]).orElseThrow();
            if (bracelets.findById(cible[1]).map(bracelet -> commandes.mettreAJour(bracelet, demande(campagne))).orElse(false)) {
                depot.noterRelance(cible[0], cible[1], maintenant);
                relances++;
            }
        }
        return relances;
    }

    // -------------------------------------------------------------------- aides

    private Campagne trouver(UUID campagneId) {
        return depot.campagne(campagneId).orElseThrow(() -> new ErreurMetier(CodeErreur.RESSOURCE_INTROUVABLE, "Campagne introuvable."));
    }

    /** Toutes les vagues lancées et plus aucun bracelet en attente : la campagne est terminée. */
    private void clorSiTerminee(UUID campagneId) {
        depot.campagne(campagneId).filter(campagne -> campagne.vagueCourante() == VAGUES.length && !campagne.statut().equals("TERMINEE")
                && depot.enAttente(campagneId) == 0).ifPresent(campagne -> {
                    depot.changerStatut(campagneId, "TERMINEE");
                    journal.consigner(null, "SYSTEME", "CAMPAGNE_OTA_TERMINEE", "LOGICIEL_EMBARQUE", campagne.version(), Resultat.SUCCES);
                });
    }

    /** Nombre cumulé de bracelets visés au terme de la vague ; la première en vise au moins un. */
    static int quota(int vague, int parc) {
        if (parc == 0) {
            return 0;
        }
        return vague == VAGUES.length ? parc : Math.max(1, (int) Math.ceil(parc * VAGUES[vague - 1] / 100.0));
    }

    private static Map<String, Object> demande(Campagne campagne) {
        Map<String, Object> demande = new LinkedHashMap<>();
        demande.put("v", campagne.version());
        demande.put("url", campagne.urlImage());
        demande.put("size", campagne.tailleOctets());
        demande.put("sha", campagne.sha256());
        demande.put("sig", campagne.signature());
        return demande;
    }

    private CampagneVue vue(Campagne campagne, int parcPorte) {
        Map<Integer, Avancement> lances = new LinkedHashMap<>();
        int vises = 0;
        for (Avancement avancement : depot.avancement(campagne.id())) {
            lances.put(avancement.vague(), avancement);
            vises += avancement.cibles();
        }
        List<VagueVue> vagues = new ArrayList<>();
        int cumul = vises;
        for (int numero = 1; numero <= VAGUES.length; numero++) {
            Avancement lance = lances.get(numero);
            if (numero <= campagne.vagueCourante()) {
                int cibles = lance == null ? 0 : lance.cibles();
                int installes = lance == null ? 0 : lance.installes();
                vagues.add(new VagueVue(numero, VAGUES[numero - 1], cibles, installes,
                        installes >= cibles ? EtatVague.TERMINEE : EtatVague.EN_COURS));
            } else {
                // Estimation sur le parc porté à cet instant ; le compte exact est fait au lancement.
                int prevu = Math.max(0, quota(numero, Math.max(parcPorte, vises)) - cumul);
                cumul += prevu;
                vagues.add(new VagueVue(numero, VAGUES[numero - 1], prevu, 0,
                        numero == campagne.vagueCourante() + 1 ? EtatVague.PRETE : EtatVague.PLANIFIEE));
            }
        }
        return new CampagneVue(campagne.id(), campagne.version(), campagne.tailleOctets(), campagne.note(), campagne.statut(),
                campagne.vagueCourante(), campagne.creeeLe(), vagues);
    }

    private boolean signatureValide(String manifeste, String signature) {
        try {
            Signature verification = Signature.getInstance("SHA256withECDSAinP1363Format");
            verification.initVerify(clePublication);
            verification.update(manifeste.getBytes(StandardCharsets.UTF_8));
            return verification.verify(Base64.getUrlDecoder().decode(signature));
        } catch (GeneralSecurityException | IllegalArgumentException erreur) {
            return false;
        }
    }

    private static PublicKey lire(String clePublique) {
        if (clePublique == null || clePublique.isBlank()) {
            return null;
        }
        try {
            return KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(clePublique.strip())));
        } catch (GeneralSecurityException | IllegalArgumentException erreur) {
            throw new IllegalStateException("fasoguardian.ota.cle-publique n'est pas une clé publique EC en base64 (X.509).", erreur);
        }
    }
}
