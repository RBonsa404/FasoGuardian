package bf.fasoguardian.alertes.application;

import java.time.Clock;
import java.time.Duration;
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
import bf.fasoguardian.identite.MessagesTuteurs;
import io.micrometer.core.instrument.MeterRegistry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ouvre les alertes et prévient les tuteurs (US-ENF-001, US-ENF-002). Une alerte critique part aussitôt par
 * SMS ; une alerte importante n'y recourt que si personne ne l'a prise en charge au bout de 60 secondes.
 * Les SMS ne portent ni position ni donnée de santé : ils renvoient à l'application (FG-DOC-06 §6.6).
 */
@Component
public class OuvertureAlertes {

    /** Délai sans prise en charge au bout duquel une alerte importante est doublée par SMS. */
    public static final Duration DELAI_AVANT_SMS = Duration.ofSeconds(60);
    private static final List<Statut> NON_CLOSES = List.of(Statut.OUVERTE, Statut.ACQUITTEE, Statut.ESCALADEE);

    private final DepotAlertes alertes;
    private final DepotActions actions;
    private final Commandes commandes;
    private final LiensTutelle liens;
    private final MessagesTuteurs messages;
    private final MeterRegistry metriques;
    private final Clock horloge;

    OuvertureAlertes(DepotAlertes alertes, DepotActions actions, Commandes commandes, LiensTutelle liens, MessagesTuteurs messages,
            MeterRegistry metriques, Clock horloge) {
        this.alertes = alertes;
        this.actions = actions;
        this.commandes = commandes;
        this.liens = liens;
        this.messages = messages;
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
        if (alerte.gravite() == Gravite.CRITIQUE) {
            prevenir(alerte, acteurId, maintenant);
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

    /** Repli SMS des alertes importantes restées sans prise en charge (US-ENF-001). */
    @Scheduled(fixedDelayString = "${fasoguardian.alertes.relance:PT15S}")
    @SchedulerLock(name = "alertes-relance-sms", lockAtMostFor = "PT1M")
    @Transactional
    public void relancerParSms() {
        Instant maintenant = horloge.instant();
        alertes.findByStatutAndSmsEnvoyeLeIsNullAndOuverteLeBefore(Statut.OUVERTE, maintenant.minus(DELAI_AVANT_SMS))
                .forEach(alerte -> prevenir(alerte, null, maintenant));
    }

    /** Prévient tous les tuteurs de l'enfant, hormis celui qui est à l'origine de l'alerte. */
    private void prevenir(Alerte alerte, UUID acteurId, Instant maintenant) {
        String texte = "FasoGuardian : " + switch (alerte.type()) {
            case SOS -> "ALERTE SOS. Le bouton SOS du bracelet de votre enfant a été déclenché.";
            case RETRAIT -> "ALERTE. Le bracelet de votre enfant a été retiré sans autorisation.";
            case SIGNALEMENT -> "ALERTE. Un signalement vient d'être ouvert pour votre enfant.";
            case SORTIE_ZONE -> "votre enfant est sorti d'une Safe Zone.";
            case BATTERIE_CRITIQUE -> "la batterie du bracelet de votre enfant est critique.";
            case CHUTE -> "le bracelet de votre enfant a détecté une chute.";
        } + " Ouvrez l'application.";
        liens.tuteursActifsDe(alerte.enfantId()).stream().filter(tuteur -> !tuteur.equals(acteurId))
                .forEach(tuteur -> messages.envoyerSms(tuteur, texte));
        alerte.noterSmsEnvoye(maintenant);
    }
}
