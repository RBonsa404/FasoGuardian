package bf.fasoguardian.famille;

import java.util.UUID;

/**
 * Rattachement du QR code d'un bracelet à un enfant, piloté par le module dispositifs (appairage,
 * remplacement, perte et vol). La page publique reste accessible quel que soit l'état de l'abonnement.
 */
public interface ProfilsQr {

    /**
     * Rattache à l'enfant le jeton gravé sur son bracelet.
     *
     * @param jetonSha256    empreinte SHA-256 (hexadécimale) du jeton ; le jeton en clair n'est jamais transmis
     * @param numeroBracelet numéro gravé sur le bracelet (FG-XXXX), seul identifiant affiché sur la page publique
     */
    void associer(UUID enfantId, String jetonSha256, String numeroBracelet);

    /** Désactive la page publique (bracelet perdu ou volé) : elle affiche alors l'écran « bracelet désactivé ». */
    void suspendre(UUID enfantId);

    void reactiver(UUID enfantId);
}
