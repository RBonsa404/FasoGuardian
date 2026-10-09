package bf.fasoguardian.alertes.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import bf.fasoguardian.alertes.domaine.Alerte;
import bf.fasoguardian.alertes.domaine.Alerte.Gravite;
import bf.fasoguardian.alertes.domaine.Alerte.Ouverture;
import bf.fasoguardian.alertes.domaine.Alerte.Statut;
import bf.fasoguardian.alertes.domaine.Alerte.Type;
import bf.fasoguardian.alertes.infrastructure.DepotActions;
import bf.fasoguardian.alertes.infrastructure.DepotAlertes;
import bf.fasoguardian.dispositifs.Commandes;
import bf.fasoguardian.identite.LiensTutelle;
import bf.fasoguardian.notifications.Notifications;
import bf.fasoguardian.notifications.Notifications.Message;
import bf.fasoguardian.notifications.Notifications.Urgence;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ouvre les alertes et prévient les tuteurs (US-ENF-001, US-ENF-002). Une alerte critique part aussitôt par
 * push et par SMS ; une alerte importante par push, puis par SMS si personne ne l'a reçue ni prise en charge au
 * bout de 60 secondes. Les messages ne portent ni position ni donnée de santé (FG-DOC-06 §6.6).
 */
@Component
public class OuvertureAlertes {

    private static final List<Statut> NON_CLOSES = List.of(Statut.OUVERTE, Statut.ACQUITTEE, Statut.ESCALADEE);

    private final DepotAlertes alertes;
    private final DepotActions actions;
    private final Commandes commandes;
    private final LiensTutelle liens;
    private final Notifications notifications;
    private final MeterRegistry metriques;
    private final Clock horloge;

    OuvertureAlertes(DepotAlertes alertes, DepotActions actions, Commandes commandes, LiensTutelle liens, Notifications notifications,
            MeterRegistry metriques, Clock horloge) {
        this.alertes = alertes;
        this.actions = actions;
        this.commandes = commandes;
        this.liens = liens;
        this.notifications = notifications;
        this.metriques = metriques;
        this.horloge = horloge;
    }

    /**
     * Ouvre une alerte, sauf si une alerte de même nature (et de même zone) est déjà en cours pour l'enfant :
     * un événement répété ne multiplie pas les alertes.
     *
     * @param acteurId tuteur qui signale, ou {@code null} pour un événement détecté
     * @return l'alerte ouverte, ou vide si elle existait déjà
     */
    @Transactional
    public Optional<Alerte> ouvrir(UUID enfantId, Type type, UUID acteurId, UUID zoneId, String libelle, Double latitude,
            Double longitude) {
        boolean dejaEnCours = alertes.findByEnfantIdAndTypeAndStatutIn(enfantId, type, NON_CLOSES).stream()
                .anyMatch(existante -> zoneId == null || zoneId.equals(existante.zoneId()));
        if (dejaEnCours) {
            return Optional.empty();
        }
        Instant maintenant = horloge.instant();
        Ouverture ouverture = Alerte.ouvrir(enfantId, type, acteurId, zoneId, libelle, latitude, longitude, maintenant);
        Alerte alerte = alertes.save(ouverture.alerte());
        actions.save(ouverture.action());
        metriques.counter("fasoguardian.alertes.ouvertes", "type", type.name()).increment();
        prevenir(alerte, acteurId);
        if (alerte.gravite() == Gravite.CRITIQUE) {
            // Le bracelet passe à une position toutes les 60 secondes (US-ENF-002).
            commandes.modeAlerte(enfantId, true);
        }
        return Optional.of(alerte);
    }

    /** Une alerte vient d'être close : le bracelet quitte le mode alerte s'il ne reste aucune alerte critique. */
    @Transactional
    public void relacherModeAlerte(UUID enfantId) {
        boolean critiqueEnCours = NON_CLOSES.stream()
                .flatMap(statut -> alertes.findByEnfantIdAndStatut(enfantId, statut).stream())
                .anyMatch(alerte -> alerte.gravite() == Gravite.CRITIQUE);
        if (!critiqueEnCours) {
            commandes.modeAlerte(enfantId, false);
        }
    }

    /** L'alerte est prise en charge ou close : ses notifications n'appellent plus de repli SMS. */
    public void accuser(Alerte alerte) {
        notifications.accuser(reference(alerte));
    }

    /** Prévient tous les tuteurs de l'enfant, hormis celui qui est à l'origine de l'alerte. */
    private void prevenir(Alerte alerte, UUID acteurId) {
        String titre = switch (alerte.type()) {
            case SOS -> "Alerte SOS";
            case RETRAIT -> "Bracelet retiré";
            case SIGNALEMENT -> "Signalement ouvert";
            case SORTIE_ZONE -> "Sortie de zone";
            case BATTERIE_CRITIQUE -> "Batterie critique";
            case CHUTE -> "Chute détectée";
        };
        String texte = switch (alerte.type()) {
            case SOS -> "ALERTE SOS. Le bouton SOS du bracelet de votre enfant a été déclenché.";
            case RETRAIT -> "ALERTE. Le bracelet de votre enfant a été retiré sans autorisation.";
            case SIGNALEMENT -> "ALERTE. Un signalement vient d'être ouvert pour votre enfant.";
            case SORTIE_ZONE -> "votre enfant est sorti d'une Safe Zone.";
            case BATTERIE_CRITIQUE -> "la batterie du bracelet de votre enfant est critique.";
            case CHUTE -> "le bracelet de votre enfant a détecté une chute.";
        } + " Ouvrez l'application.";
        Message message = new Message("ALERTE_" + alerte.type(), titre, texte, "/alertes/" + alerte.id(), reference(alerte));
        Urgence urgence = alerte.gravite() == Gravite.CRITIQUE ? Urgence.CRITIQUE : Urgence.IMPORTANTE;
        liens.tuteursActifsDe(alerte.enfantId()).stream().filter(tuteur -> !tuteur.equals(acteurId))
                .forEach(tuteur -> notifications.notifier(tuteur, urgence, message));
    }

    private static String reference(Alerte alerte) {
        return "alerte:" + alerte.id();
    }
}
