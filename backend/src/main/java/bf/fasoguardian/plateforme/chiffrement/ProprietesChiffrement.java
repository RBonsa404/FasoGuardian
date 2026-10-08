package bf.fasoguardian.plateforme.chiffrement;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Clés de chiffrement applicatif, fournies hors de la base par le coffre de secrets.
 *
 * @param cles         par catégorie, les clés AES-256 en base64 indexées par numéro de version (1 à 255) ;
 *                     la version la plus élevée chiffre, les précédentes ne servent qu'à déchiffrer (rotation)
 * @param cleEmpreinte clé HMAC en base64 des empreintes de recherche exacte ({@code *_hash})
 */
@ConfigurationProperties("fasoguardian.chiffrement")
public record ProprietesChiffrement(Map<CategorieDonnee, Map<Integer, String>> cles, String cleEmpreinte) {
}
