package bf.fasoguardian.plateforme.chiffrement;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * Chiffrement applicatif AES-256-GCM avec une clé par catégorie de données, et empreintes HMAC-SHA-256
 * pour la recherche exacte sans déchiffrement (FG-DOC-06 §8.3, FG-DOC-07 §7.2).
 *
 * <p>Format d'un chiffré : 1 octet de version de clé, 12 octets de vecteur d'initialisation, puis le
 * chiffré et son sceau de 128 bits. La catégorie est liée au chiffré (données authentifiées) : une valeur
 * ne peut pas être déplacée d'une catégorie à une autre.
 */
@Service
@EnableConfigurationProperties(ProprietesChiffrement.class)
public class ServiceChiffrement {

    private static final String ALGORITHME = "AES/GCM/NoPadding";
    private static final int TAILLE_IV = 12;
    private static final int TAILLE_SCEAU_BITS = 128;
    private static final int TAILLE_CLE = 32;

    private final Map<CategorieDonnee, Map<Integer, SecretKey>> cles = new EnumMap<>(CategorieDonnee.class);
    private final Map<CategorieDonnee, Integer> versionsCourantes = new EnumMap<>(CategorieDonnee.class);
    private final SecretKey cleEmpreinte;
    private final SecureRandom alea = new SecureRandom();

    public ServiceChiffrement(ProprietesChiffrement proprietes) {
        for (CategorieDonnee categorie : CategorieDonnee.values()) {
            Map<Integer, String> versions = proprietes.cles() == null ? null : proprietes.cles().get(categorie);
            if (versions == null || versions.isEmpty()) {
                throw new IllegalStateException("Aucune clé de chiffrement configurée pour la catégorie " + categorie
                        + " (fasoguardian.chiffrement.cles." + categorie + ".<version>)");
            }
            Map<Integer, SecretKey> decodees = new HashMap<>();
            versions.forEach((version, base64) -> {
                if (version < 1 || version > 255) {
                    throw new IllegalStateException("Version de clé hors de 1..255 pour " + categorie);
                }
                decodees.put(version, new SecretKeySpec(decoder(base64, "clé " + categorie + " v" + version), "AES"));
            });
            cles.put(categorie, decodees);
            versionsCourantes.put(categorie, decodees.keySet().stream().max(Integer::compare).orElseThrow());
        }
        if (proprietes.cleEmpreinte() == null) {
            throw new IllegalStateException("Clé d'empreinte absente (fasoguardian.chiffrement.cle-empreinte)");
        }
        cleEmpreinte = new SecretKeySpec(decoder(proprietes.cleEmpreinte(), "clé d'empreinte"), "HmacSHA256");
    }

    public byte[] chiffrer(CategorieDonnee categorie, byte[] clair) {
        int version = versionsCourantes.get(categorie);
        byte[] iv = new byte[TAILLE_IV];
        alea.nextBytes(iv);
        try {
            Cipher chiffre = Cipher.getInstance(ALGORITHME);
            chiffre.init(Cipher.ENCRYPT_MODE, cles.get(categorie).get(version), new GCMParameterSpec(TAILLE_SCEAU_BITS, iv));
            chiffre.updateAAD(categorie.name().getBytes(StandardCharsets.US_ASCII));
            byte[] corps = chiffre.doFinal(clair);
            return ByteBuffer.allocate(1 + TAILLE_IV + corps.length).put((byte) version).put(iv).put(corps).array();
        } catch (GeneralSecurityException erreur) {
            throw new IllegalStateException("Chiffrement impossible pour la catégorie " + categorie, erreur);
        }
    }

    public byte[] dechiffrer(CategorieDonnee categorie, byte[] chiffreComplet) {
        if (chiffreComplet == null || chiffreComplet.length < 1 + TAILLE_IV + TAILLE_SCEAU_BITS / 8) {
            throw new DonneeIllisibleException(categorie);
        }
        int version = chiffreComplet[0] & 0xFF;
        SecretKey cle = cles.get(categorie).get(version);
        if (cle == null) {
            throw new DonneeIllisibleException(categorie);
        }
        try {
            Cipher chiffre = Cipher.getInstance(ALGORITHME);
            chiffre.init(Cipher.DECRYPT_MODE, cle, new GCMParameterSpec(TAILLE_SCEAU_BITS, chiffreComplet, 1, TAILLE_IV));
            chiffre.updateAAD(categorie.name().getBytes(StandardCharsets.US_ASCII));
            return chiffre.doFinal(chiffreComplet, 1 + TAILLE_IV, chiffreComplet.length - 1 - TAILLE_IV);
        } catch (GeneralSecurityException erreur) {
            throw new DonneeIllisibleException(categorie);
        }
    }

    public byte[] chiffrerTexte(CategorieDonnee categorie, String clair) {
        return chiffrer(categorie, clair.getBytes(StandardCharsets.UTF_8));
    }

    public String dechiffrerTexte(CategorieDonnee categorie, byte[] chiffreComplet) {
        return new String(dechiffrer(categorie, chiffreComplet), StandardCharsets.UTF_8);
    }

    /** Indique si la valeur a été chiffrée avec une clé antérieure à la clé courante (à rechiffrer). */
    public boolean aRechiffrer(CategorieDonnee categorie, byte[] chiffreComplet) {
        return (chiffreComplet[0] & 0xFF) != versionsCourantes.get(categorie);
    }

    /**
     * Empreinte déterministe d'une valeur dans un domaine nommé, hors catégories de chiffrement :
     * pseudonymisation (adresses IP du journal des consultations). Changer de domaine change tous les pseudonymes.
     */
    public String empreinteLibre(String domaine, String valeur) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(cleEmpreinte);
            mac.update(("LIBRE:" + domaine + ":").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(mac.doFinal(valeur.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException erreur) {
            throw new IllegalStateException("Calcul d'empreinte impossible", erreur);
        }
    }

    /** Empreinte déterministe d'une valeur normalisée, pour les colonnes de recherche exacte. */
    public String empreinte(CategorieDonnee categorie, String valeurNormalisee) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(cleEmpreinte);
            mac.update(categorie.name().getBytes(StandardCharsets.US_ASCII));
            mac.update((byte) ':');
            return HexFormat.of().formatHex(mac.doFinal(valeurNormalisee.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException erreur) {
            throw new IllegalStateException("Calcul d'empreinte impossible", erreur);
        }
    }

    private static byte[] decoder(String base64, String nom) {
        byte[] octets;
        try {
            octets = Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException erreur) {
            throw new IllegalStateException("La " + nom + " n'est pas du base64 valide");
        }
        if (octets.length != TAILLE_CLE) {
            throw new IllegalStateException("La " + nom + " doit faire 32 octets (256 bits)");
        }
        return octets;
    }

    /** Valeur altérée, chiffrée pour une autre catégorie ou avec une clé inconnue. Ne porte aucune donnée. */
    public static class DonneeIllisibleException extends RuntimeException {
        public DonneeIllisibleException(CategorieDonnee categorie) {
            super("Donnée chiffrée illisible pour la catégorie " + categorie);
        }
    }
}
