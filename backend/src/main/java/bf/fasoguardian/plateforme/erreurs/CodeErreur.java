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
    ERREUR_INTERNE(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne");

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
