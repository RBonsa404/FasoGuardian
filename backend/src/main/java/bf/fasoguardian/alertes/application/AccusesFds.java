package bf.fasoguardian.alertes.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import bf.fasoguardian.alertes.application.Escalades.Dossier;
import bf.fasoguardian.alertes.domaine.Alerte;
import bf.fasoguardian.alertes.domaine.SignalementFds;
import bf.fasoguardian.alertes.domaine.SignalementFds.Canal;
import bf.fasoguardian.alertes.infrastructure.DepotAlertes;
import bf.fasoguardian.alertes.infrastructure.DepotSignalements;
import bf.fasoguardian.audit.JournalAudit;
import bf.fasoguardian.audit.JournalAudit.Resultat;
import bf.fasoguardian.identite.LiensTutelle;
import bf.fasoguardian.notifications.Notifications;
import bf.fasoguardian.notifications.Notifications.Message;
import bf.fasoguardian.notifications.Notifications.Urgence;
import bf.fasoguardian.plateforme.chiffrement.CategorieDonnee;
import bf.fasoguardian.plateforme.chiffrement.ServiceChiffrement;
import bf.fasoguardian.plateforme.erreurs.CodeErreur;
import bf.fasoguardian.plateforme.erreurs.ErreurMetier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Espace des forces de sécurité (US-FDS-001). Un agent retrouve un signalement par sa référence, celle qui
 * figure sur le dossier, et en accuse réception : l'accusé est horodaté, journalisé et notifié aux parents.
 * Il n'existe aucune liste des signalements, et l'accès se ferme au bout de trente jours. Le dossier lui-même n'est téléchargeable que s'il a été transmis
 * par la passerelle convenue ; remis par le parent, l'agent l'a déjà entre les mains.
 */
@Service
public class AccusesFds {

    private static final String ROLE = "FDS";

    /** @param dossierConsultable le dossier a été transmis par la passerelle et n'est pas encore effacé */
    public record Constat(String reference, String nature, Instant etabliLe, Instant accuseLe, boolean dossierConsultable) {
    }

    private final DepotSignalements signalements;
    private final DepotAlertes alertes;
    private final LiensTutelle liens;
    private final Notifications notifications;
    private final ServiceChiffrement chiffrement;
    private final JournalAudit journal;
    private final Clock horloge;

    AccusesFds(DepotSignalements signalements, DepotAlertes alertes, LiensTutelle liens, Notifications notifications,
            ServiceChiffrement chiffrement, JournalAudit journal, Clock horloge) {
        this.signalements = signalements;
        this.alertes = alertes;
        this.liens = liens;
        this.notifications = notifications;
        this.chiffrement = chiffrement;
        this.journal = journal;
        this.horloge = horloge;
    }

    @Transactional
    public Constat constat(UUID agentId, String reference) {
        SignalementFds signalement = trouver(reference);
        journal.consigner(agentId, ROLE, "SIGNALEMENT_CONSULTE_PAR_LES_FDS", "SIGNALEMENT", signalement.reference(), Resultat.SUCCES);
        return constat(signalement);
    }

    /** Accuse réception ; un second accusé ne change rien et ne renotifie pas les parents. */
    @Transactional
    public Constat accuser(UUID agentId, String reference) {
        SignalementFds signalement = trouver(reference);
        if (signalement.accuser(agentId, horloge.instant())) {
            journal.consigner(agentId, ROLE, "SIGNALEMENT_ACCUSE", "SIGNALEMENT", signalement.reference(), Resultat.SUCCES);
            Alerte alerte = alertes.findById(signalement.alerteId()).orElseThrow();
            Message message = new Message("SIGNALEMENT_ACCUSE", "Signalement pris en charge",
                    "FasoGuardian : les forces de sécurité ont accusé réception du signalement " + signalement.reference() + ".",
                    "/alertes/" + alerte.id() + "/signalement", null);
            liens.tuteursActifsDe(alerte.enfantId()).forEach(tuteur -> notifications.notifier(tuteur, Urgence.IMPORTANTE, message));
        }
        return constat(signalement);
    }

    /** Dossier transmis par la passerelle convenue ; téléchargement journalisé. */
    @Transactional
    public Dossier dossier(UUID agentId, String reference) {
        SignalementFds signalement = trouver(reference);
        if (signalement.canal() != Canal.PASSERELLE || !signalement.dossierDisponible()) {
            journal.consigner(agentId, ROLE, "DOSSIER_REFUSE_AUX_FDS", "SIGNALEMENT", signalement.reference(), Resultat.REFUS);
            throw new ErreurMetier(CodeErreur.RESSOURCE_INTROUVABLE,
                    "Ce dossier n'est pas consultable ici : il a été remis par le parent, ou il a été effacé au bout de trente jours.");
        }
        journal.consigner(agentId, ROLE, "DOSSIER_TELECHARGE_PAR_LES_FDS", "SIGNALEMENT", signalement.reference(), Resultat.SUCCES);
        return new Dossier(signalement.reference(), chiffrement.dechiffrer(CategorieDonnee.SIGNALEMENT, signalement.dossierChiffre()));
    }

    private SignalementFds trouver(String reference) {
        SignalementFds signalement = signalements.findByReference(reference == null ? "" : reference.strip().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new ErreurMetier(CodeErreur.RESSOURCE_INTROUVABLE, "Aucun signalement ne porte cette référence."));
        if (horloge.instant().isAfter(signalement.disponibleJusquAu())) {
            throw new ErreurMetier(CodeErreur.RESSOURCE_INTROUVABLE,
                    "Ce signalement n'est plus consultable : l'accès est limité à trente jours.");
        }
        return signalement;
    }

    private Constat constat(SignalementFds signalement) {
        Alerte alerte = alertes.findById(signalement.alerteId()).orElseThrow();
        String nature = switch (alerte.type()) {
            case SOS -> "Alerte SOS";
            case RETRAIT -> "Bracelet retiré sans autorisation";
            case SIGNALEMENT -> "Disparition signalée par un tuteur";
            case SORTIE_ZONE -> "Sortie d'une zone de sécurité";
            case BATTERIE_CRITIQUE -> "Batterie critique du bracelet";
            case CHUTE -> "Chute détectée";
        };
        return new Constat(signalement.reference(), nature, signalement.creeLe(), signalement.accuseLe(),
                signalement.canal() == Canal.PASSERELLE && signalement.dossierDisponible());
    }
}
