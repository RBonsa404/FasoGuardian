package bf.fasoguardian.audit;

import java.util.OptionalLong;
import java.util.UUID;

/**
 * Journal d'audit en ajout seul, chaîné par empreinte SHA-256 (FG-DOC-06 §7.3). Y sont consignés les accès
 * aux données sensibles, les actions des agents internes, les autorisations de retrait et les transmissions
 * aux forces de sécurité. Les entrées ne portent que des identifiants techniques, jamais de donnée personnelle.
 */
public interface JournalAudit {

    enum Resultat {
        SUCCES,
        REFUS
    }

    /**
     * @param acteurId  compte à l'origine de l'action, ou {@code null} pour le système
     * @param role      rôle sous lequel l'action est effectuée (PARENT, KYC, ADMIN, SYSTEME…)
     * @param action    code stable de l'action (par exemple {@code AGENT_CREE})
     * @param typeCible nature de la ressource visée
     * @param cibleId   identifiant technique de la ressource, ou {@code null}
     */
    void consigner(UUID acteurId, String role, String action, String typeCible, String cibleId, Resultat resultat);

    /** Parcourt toute la chaîne ; renvoie l'identifiant de la première entrée altérée, ou vide si elle est intègre. */
    OptionalLong premiereEntreeAlteree();
}
