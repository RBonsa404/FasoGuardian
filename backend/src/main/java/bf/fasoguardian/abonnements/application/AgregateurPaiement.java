package bf.fasoguardian.abonnements.application;

import java.util.UUID;

import bf.fasoguardian.abonnements.domaine.Moyen;

/**
 * Agrégateur de paiement mobile money (FG-DOC-06 §6.7). La plateforme lui demande de solliciter un
 * portefeuille, puis attend sa notification signée : elle seule fait foi.
 */
public interface AgregateurPaiement {

    /** @param numeroE164 portefeuille à solliciter ; l'adaptateur ne le conserve ni ne le journalise */
    record Demande(UUID paiementId, int montantFcfa, Moyen moyen, String numeroE164, String libelle) {
    }

    /** @param motif raison de l'échec donnée par l'opérateur, ou {@code null} */
    record Notification(String reference, boolean confirme, int montantFcfa, String motif) {
    }

    /**
     * Demande le paiement ; le client le valide ensuite sur son téléphone.
     *
     * @return la référence de l'opération chez l'agrégateur
     * @throws AgregateurIndisponible si la demande n'a pas pu être déposée
     */
    String initier(Demande demande);

    /**
     * Authentifie une notification reçue et la lit.
     *
     * @param signature en-tête de signature tel que reçu, ou {@code null}
     * @param corps     corps de la requête, octet pour octet
     * @throws NotificationRejetee si la signature est absente, fausse ou trop ancienne, ou le corps illisible
     */
    Notification lire(String signature, byte[] corps);

    class AgregateurIndisponible extends RuntimeException {
        public AgregateurIndisponible(String message, Throwable cause) {
            super(message, cause);
        }
    }

    class NotificationRejetee extends RuntimeException {
        public NotificationRejetee(String message) {
            super(message);
        }
    }
}
