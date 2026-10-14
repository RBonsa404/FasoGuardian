package bf.fasoguardian.plateforme.console;

import java.util.List;
import java.util.UUID;

/**
 * Indicateurs du tableau de bord de la console (écran 57). Chaque module fournit ceux de la file de travail
 * d'un rôle ; le socle les rassemble sans savoir ce qu'ils mesurent. Ce ne sont que des décomptes et des
 * durées : aucune donnée personnelle.
 */
public interface IndicateursConsole {

    /**
     * @param code   identifiant stable, mis en mots par la console (« kyc.enFile »)
     * @param valeur nombre mesuré, ou {@code null} s'il n'y a rien à mesurer (aucune décision sur la période)
     * @param repere objectif ou seuil associé, ou {@code null}
     * @param alerte la valeur appelle une action
     */
    record Indicateur(String code, Long valeur, Long repere, boolean alerte) {
    }

    /** Rôle interne (KYC, SUPPORT, SAV, FDS, ADMIN) dont relève la file. */
    String role();

    List<Indicateur> pour(UUID agentId);
}
