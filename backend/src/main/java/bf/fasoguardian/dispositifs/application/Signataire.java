package bf.fasoguardian.dispositifs.application;

/** Signe les commandes au nom de la plateforme ; la clé privée ne sort jamais de l'adaptateur. */
public interface Signataire {

    /** Signature ECDSA P-256 / SHA-256 au format brut R‖S (64 octets), celui que vérifie l'élément sécurisé. */
    byte[] signer(byte[] contenu);
}
