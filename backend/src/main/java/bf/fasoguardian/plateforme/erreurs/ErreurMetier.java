package bf.fasoguardian.plateforme.erreurs;

/**
 * Erreur métier levée par un cas d'usage et traduite en réponse RFC 9457.
 * Le détail ne doit contenir aucune donnée personnelle : il est renvoyé au client et journalisé.
 */
public class ErreurMetier extends RuntimeException {

    private final CodeErreur code;

    private final java.util.Map<String, Object> proprietes;

    public ErreurMetier(CodeErreur code, String detail) {
        this(code, detail, java.util.Map.of());
    }

    /** Les propriétés sont ajoutées telles quelles à la réponse RFC 9457. */
    public ErreurMetier(CodeErreur code, String detail, java.util.Map<String, Object> proprietes) {
        super(detail);
        this.code = code;
        this.proprietes = java.util.Map.copyOf(proprietes);
    }

    public java.util.Map<String, Object> proprietes() {
        return proprietes;
    }

    public CodeErreur code() {
        return code;
    }
}
