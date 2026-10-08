package bf.fasoguardian.identite;

import java.util.UUID;

/**
 * Événement de domaine : un dossier KYC est approuvé et le lien de tutelle est actif. Ne porte que des
 * identifiants techniques — il est persisté dans le registre de publication. Le module famille crée la
 * fiche de l'enfant à partir de {@code enfantId}.
 */
public record DossierKycApprouve(UUID dossierId, UUID tuteurId, UUID enfantId) {
}
