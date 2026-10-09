package bf.fasoguardian.alertes.application;

/**
 * Couche anticorruption vers le protocole des forces de sécurité (REQ-SYS-022). Tant qu'aucune convention
 * n'est signée, elle ne transmet rien : le dossier est remis par le parent lui-même.
 */
public interface PasserelleFds {

    boolean conventionActive();

    /** Transmet le dossier au point de contact désigné par la convention. */
    void transmettre(String reference, byte[] dossierPdf);
}
