package bf.fasoguardian.plateforme.erreurs;

import bf.fasoguardian.plateforme.securite.JournalisationRefus;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduit toutes les erreurs des API en « problem details » (RFC 9457) avec un code métier stable.
 */
@RestControllerAdvice
class GestionnaireErreurs extends ResponseEntityExceptionHandler {

    private static final Logger journal = LoggerFactory.getLogger(GestionnaireErreurs.class);

    private final JournalisationRefus refus;

    GestionnaireErreurs(JournalisationRefus refus) {
        this.refus = refus;
    }

    @ExceptionHandler(ErreurMetier.class)
    ResponseEntity<ProblemDetail> erreurMetier(ErreurMetier erreur) {
        ProblemDetail probleme = Problemes.de(erreur.code(), erreur.getMessage());
        erreur.proprietes().forEach(probleme::setProperty);
        return ResponseEntity.status(erreur.code().statut()).body(probleme);
    }

    /** Refus levé par une règle @PreAuthorize : même réponse et même journalisation qu'un refus de route. */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> accesRefuse(AccessDeniedException erreur, HttpServletRequest requete) {
        refus.publier(requete);
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Problemes.de(CodeErreur.ACCES_REFUSE, "Vous n'êtes pas autorisé à accéder à cette ressource."));
    }

    /** Deux acteurs ont modifié la même ressource en même temps : le second doit recharger avant de réessayer. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> modificationConcurrente(OptimisticLockingFailureException erreur) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Problemes.de(CodeErreur.CONFLIT,
                "Cette ressource vient d'être modifiée par quelqu'un d'autre. Rechargez puis réessayez."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> erreurInattendue(Exception erreur) {
        // Le message d'origine peut contenir des données personnelles : seul le type est journalisé au niveau erreur.
        journal.error("Erreur non gérée de type {}", erreur.getClass().getName(), erreur);
        ProblemDetail probleme = Problemes.de(CodeErreur.ERREUR_INTERNE, "Une erreur inattendue est survenue.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(probleme);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> reponse = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (reponse != null && reponse.getBody() instanceof ProblemDetail probleme) {
            Problemes.completer(probleme, codePour(statusCode));
        } else if (reponse != null && reponse.getBody() instanceof ErrorResponse erreur) {
            Problemes.completer(erreur.getBody(), codePour(statusCode));
        }
        return reponse;
    }

    private static CodeErreur codePour(HttpStatusCode statut) {
        return switch (statut.value()) {
            case 401 -> CodeErreur.NON_AUTHENTIFIE;
            case 403 -> CodeErreur.ACCES_REFUSE;
            case 404 -> CodeErreur.RESSOURCE_INTROUVABLE;
            case 409 -> CodeErreur.CONFLIT;
            case 429 -> CodeErreur.TROP_DE_REQUETES;
            default -> statut.is4xxClientError() ? CodeErreur.REQUETE_INVALIDE : CodeErreur.ERREUR_INTERNE;
        };
    }
}
