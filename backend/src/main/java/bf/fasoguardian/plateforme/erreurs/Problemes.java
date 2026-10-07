package bf.fasoguardian.plateforme.erreurs;

import java.net.URI;

import org.springframework.http.ProblemDetail;

/** Fabrique des réponses d'erreur RFC 9457 portant un code métier stable. */
public final class Problemes {

    public static final String PROPRIETE_CODE = "code";
    private static final String BASE_TYPE = "https://fasoguardian.bf/erreurs/";

    private Problemes() {
    }

    public static ProblemDetail de(CodeErreur code, String detail) {
        ProblemDetail probleme = ProblemDetail.forStatusAndDetail(code.statut(), detail);
        probleme.setTitle(code.titre());
        return completer(probleme, code);
    }

    static ProblemDetail completer(ProblemDetail probleme, CodeErreur code) {
        probleme.setType(URI.create(BASE_TYPE + code.name().toLowerCase().replace('_', '-')));
        probleme.setProperty(PROPRIETE_CODE, code.name());
        return probleme;
    }
}
