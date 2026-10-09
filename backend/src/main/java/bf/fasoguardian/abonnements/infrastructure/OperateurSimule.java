package bf.fasoguardian.abonnements.infrastructure;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import bf.fasoguardian.abonnements.application.ReceptionPaiements;
import bf.fasoguardian.abonnements.infrastructure.AgregateurBacASable.DemandeRecue;
import bf.fasoguardian.abonnements.infrastructure.AgregateurBacASable.NotificationSignee;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tient le rôle du parent qui valide sur son téléphone, pour le développement et les parcours de bout en
 * bout : après le délai configuré, chaque demande du bac à sable reçoit sa notification signée, par le même
 * chemin qu'une notification réelle. Un portefeuille dont le numéro se termine par 00 refuse le paiement.
 * N'existe qu'avec l'adaptateur bac à sable et si {@code fasoguardian.paiements.bac-a-sable.validation-apres}
 * est renseigné.
 */
@Component
@ConditionalOnExpression("'${fasoguardian.paiements.adaptateur:}' == 'bac-a-sable' "
        + "and '${fasoguardian.paiements.bac-a-sable.validation-apres:}' != ''")
class OperateurSimule {

    private final AgregateurBacASable agregateur;
    private final ReceptionPaiements reception;
    private final Clock horloge;
    private final Duration delai;

    OperateurSimule(AgregateurBacASable agregateur, ReceptionPaiements reception, Clock horloge,
            @Value("${fasoguardian.paiements.bac-a-sable.validation-apres}") Duration delai) {
        this.agregateur = agregateur;
        this.reception = reception;
        this.horloge = horloge;
        this.delai = delai;
    }

    @Scheduled(fixedDelay = 1000)
    void repondre() {
        Instant limite = horloge.instant().minus(delai);
        for (DemandeRecue demande : agregateur.enAttente()) {
            if (!demande.recueLe().isAfter(limite)) {
                NotificationSignee notification = agregateur.notification(demande.reference(), !demande.refusee(),
                        demande.montantFcfa(), demande.refusee() ? "Solde insuffisant" : null);
                reception.recevoir(notification.signature(), notification.corps());
            }
        }
    }
}
