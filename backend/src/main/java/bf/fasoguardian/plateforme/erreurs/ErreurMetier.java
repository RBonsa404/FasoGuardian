package bf.fasoguardian.plateforme.erreurs;

/**
 * Erreur métier levée par un cas d'usage et traduite en réponse RFC 9457.
 * Le détail ne doit contenir aucune donnée personnelle : il est renvoyé au client et journalisé.
 */
public class ErreurMetier extends RuntimeException {

    private final CodeErreur code;

    public ErreurMetier(CodeErreur code, String detail) {
        super(detail);
        this.code = code;
    }

    public CodeErreur code() {
        return code;
    }
}
