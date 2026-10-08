package bf.fasoguardian.telemetrie.application;

/** Port d'entrée des messages des bracelets, quel que soit le transport (MQTT aujourd'hui, SMS de repli ensuite). */
public interface ReceptionMessages {

    /** Flux montants du protocole (FG-DOC-08, tableau 11). */
    enum Flux {
        TELEMETRY,
        STATUS,
        ALERT
    }

    /**
     * @param identifiantAppareil identifiant sous lequel le bracelet s'est authentifié (nom commun de son certificat)
     */
    void recevoir(Flux flux, String identifiantAppareil, byte[] message);
}
