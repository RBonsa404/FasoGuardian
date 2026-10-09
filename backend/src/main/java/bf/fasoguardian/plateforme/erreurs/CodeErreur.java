package bf.fasoguardian.plateforme.erreurs;

import org.springframework.http.HttpStatus;

/**
 * Codes d'erreur métier stables exposés dans les réponses RFC 9457 (propriété {@code code}).
 * Un code publié n'est jamais renommé : les clients traduisent leurs messages à partir de lui.
 */
public enum CodeErreur {

    NON_AUTHENTIFIE(HttpStatus.UNAUTHORIZED, "Authentification requise"),
    ACCES_REFUSE(HttpStatus.FORBIDDEN, "Accès refusé"),
    RESSOURCE_INTROUVABLE(HttpStatus.NOT_FOUND, "Ressource introuvable"),
    REQUETE_INVALIDE(HttpStatus.BAD_REQUEST, "Requête invalide"),
    CONFLIT(HttpStatus.CONFLICT, "Conflit avec l'état de la ressource"),
    TROP_DE_REQUETES(HttpStatus.TOO_MANY_REQUESTS, "Trop de requêtes"),
    ERREUR_INTERNE(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne"),

    TELEPHONE_INVALIDE(HttpStatus.BAD_REQUEST, "Numéro de téléphone invalide"),
    CODE_INCORRECT(HttpStatus.BAD_REQUEST, "Code incorrect"),
    CODE_EXPIRE(HttpStatus.BAD_REQUEST, "Code expiré"),
    CODE_EPUISE(HttpStatus.BAD_REQUEST, "Nombre d'essais dépassé"),
    MOT_DE_PASSE_REFUSE(HttpStatus.BAD_REQUEST, "Mot de passe refusé"),
    CONSENTEMENT_REQUIS(HttpStatus.BAD_REQUEST, "Consentement obligatoire manquant"),
    IDENTIFIANTS_INVALIDES(HttpStatus.UNAUTHORIZED, "Identifiants invalides"),
    COMPTE_VERROUILLE(HttpStatus.LOCKED, "Compte temporairement verrouillé"),
    SESSION_EXPIREE(HttpStatus.UNAUTHORIZED, "Session expirée"),
    TOTP_A_ACTIVER(HttpStatus.FORBIDDEN, "Second facteur à activer"),
    CODE_TOTP_REQUIS(HttpStatus.UNAUTHORIZED, "Code de l'application d'authentification requis"),
    DOSSIER_INCOMPLET(HttpStatus.BAD_REQUEST, "Dossier incomplet"),
    PIECE_REFUSEE(HttpStatus.BAD_REQUEST, "Pièce refusée"),
    SECOND_FACTEUR_REQUIS(HttpStatus.FORBIDDEN, "Confirmation par code SMS requise"),
    CODE_APPAIRAGE_INVALIDE(HttpStatus.BAD_REQUEST, "Code d'appairage invalide"),
    ENFANT_DEJA_EQUIPE(HttpStatus.CONFLICT, "L'enfant porte déjà un bracelet"),
    ZONES_MAXIMUM_ATTEINT(HttpStatus.CONFLICT, "Nombre maximal de Safe Zones atteint"),
    PAIEMENT_EN_COURS(HttpStatus.CONFLICT, "Un paiement est déjà en attente de validation");

    private final HttpStatus statut;
    private final String titre;

    CodeErreur(HttpStatus statut, String titre) {
        this.statut = statut;
        this.titre = titre;
    }

    public HttpStatus statut() {
        return statut;
    }

    public String titre() {
        return titre;
    }
}
