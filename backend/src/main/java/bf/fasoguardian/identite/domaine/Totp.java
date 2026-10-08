package bf.fasoguardian.identite.domaine;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.OptionalLong;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Codes TOTP (RFC 6238, HMAC-SHA-1, 6 chiffres, pas de 30 s) des agents internes, compatibles avec les
 * applications d'authentification courantes. Une dérive d'un pas est tolérée de part et d'autre.
 */
public final class Totp {

    public static final int PAS_SECONDES = 30;
    private static final int TOLERANCE_PAS = 1;
    private static final String ALPHABET_BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Totp() {
    }

    public static byte[] nouveauSecret() {
        byte[] secret = new byte[20];
        new SecureRandom().nextBytes(secret);
        return secret;
    }

    public static String code(byte[] secret, long pas) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] sceau = mac.doFinal(ByteBuffer.allocate(8).putLong(pas).array());
            int decalage = sceau[sceau.length - 1] & 0x0F;
            int valeur = ((sceau[decalage] & 0x7F) << 24) | ((sceau[decalage + 1] & 0xFF) << 16)
                    | ((sceau[decalage + 2] & 0xFF) << 8) | (sceau[decalage + 3] & 0xFF);
            return String.format("%06d", valeur % 1_000_000);
        } catch (GeneralSecurityException erreur) {
            throw new IllegalStateException(erreur);
        }
    }

    /**
     * Vérifie un code et renvoie le pas de temps auquel il correspond. Le pas doit être strictement
     * supérieur au dernier pas accepté pour ce secret : un code intercepté ne peut pas être rejoué.
     */
    public static OptionalLong verifier(byte[] secret, String saisie, Instant maintenant, long dernierPasAccepte) {
        if (saisie == null || !saisie.trim().matches("\\d{6}")) {
            return OptionalLong.empty();
        }
        byte[] attendu = saisie.trim().getBytes();
        long pasCourant = maintenant.getEpochSecond() / PAS_SECONDES;
        OptionalLong trouve = OptionalLong.empty();
        for (long pas = pasCourant - TOLERANCE_PAS; pas <= pasCourant + TOLERANCE_PAS; pas++) {
            if (MessageDigest.isEqual(attendu, code(secret, pas).getBytes()) && pas > dernierPasAccepte) {
                trouve = OptionalLong.of(pas);
            }
        }
        return trouve;
    }

    public static byte[] depuisBase32(String base32) {
        java.io.ByteArrayOutputStream sortie = new java.io.ByteArrayOutputStream();
        int tampon = 0;
        int bits = 0;
        for (char caractere : base32.toUpperCase().toCharArray()) {
            int valeur = ALPHABET_BASE32.indexOf(caractere);
            if (valeur < 0) {
                throw new IllegalArgumentException("Caractère hors de l'alphabet base32");
            }
            tampon = (tampon << 5) | valeur;
            bits += 5;
            if (bits >= 8) {
                sortie.write((tampon >> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return sortie.toByteArray();
    }

    /** Secret encodé en base32 sans remplissage, tel que saisi ou scanné dans l'application d'authentification. */
    public static String enBase32(byte[] secret) {
        StringBuilder sortie = new StringBuilder();
        int tampon = 0;
        int bits = 0;
        for (byte octet : secret) {
            tampon = (tampon << 8) | (octet & 0xFF);
            bits += 8;
            while (bits >= 5) {
                sortie.append(ALPHABET_BASE32.charAt((tampon >> (bits - 5)) & 0x1F));
                bits -= 5;
            }
        }
        if (bits > 0) {
            sortie.append(ALPHABET_BASE32.charAt((tampon << (5 - bits)) & 0x1F));
        }
        return sortie.toString();
    }
}
