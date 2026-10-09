package bf.fasoguardian.notifications.application;

/** Livraison d'une notification à un navigateur abonné (port). */
public interface CanalPush {

    enum Issue {
        LIVREE,
        /** Le service de push ne connaît plus cet abonnement : il doit être retiré. */
        ABONNEMENT_PERIME,
        ECHEC
    }

    /** @param urgent demande une livraison immédiate, même sur un appareil en économie d'énergie */
    Issue pousser(String pointDeLivraison, String cleP256dh, String secretAuth, byte[] contenu, boolean urgent);

    /** Faux tant que les clés du serveur d'application ne sont pas configurées. */
    boolean disponible();

    /** Clé publique du serveur d'application, à remettre au navigateur qui s'abonne. */
    String clePublique();

    /** Vrai si l'adresse désigne un service de push admis. */
    boolean admet(String pointDeLivraison);
}
