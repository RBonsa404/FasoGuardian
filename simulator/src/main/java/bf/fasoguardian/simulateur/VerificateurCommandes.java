package bf.fasoguardian.simulateur;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Vérification d'une commande reçue de la plateforme, telle que le logiciel embarqué doit la faire
 * (docs/protocole-bracelet.md) : signature de la plateforme, destinataire, expiration, numéro jamais vu.
 * Une commande qui échoue à l'un de ces contrôles est ignorée et signalée (US-SYS-011).
 */
final class VerificateurCommandes {

    enum Rejet {
        FORMAT,
        SIGNATURE,
        DESTINATAIRE,
        EXPIREE,
        REJEU
    }

    /** Commande authentifiée. {@code corps} est le JSON signé, pour en lire les paramètres. */
    record Commande(String id, String code, long numero, String corps) {

        /** Paramètre entier de la commande, ou la valeur par défaut s'il manque. */
        long parametre(String nom, long defaut) {
            Matcher valeur = Pattern.compile("\"" + Pattern.quote(nom) + "\":(-?\\d+)").matcher(corps);
            return valeur.find() ? Long.parseLong(valeur.group(1)) : defaut;
        }
    }

    /**
     * @param commande la commande si elle est acceptée, sinon {@code null}
     * @param rejet    le motif du refus, sinon {@code null}
     * @param idLu     identifiant lu dans le message, même refusé, pour le signaler à la plateforme
     * @param dejaExecutee la commande, authentique, a déjà été exécutée : elle est accusée de nouveau sans être rejouée
     */
    record Resultat(Commande commande, Rejet rejet, String idLu, boolean dejaExecutee) {

        Resultat(Commande commande, Rejet rejet, String idLu) {
            this(commande, rejet, idLu, false);
        }

        /** La commande est à exécuter. */
        boolean acceptee() {
            return commande != null;
        }

        /** Valeur de {@code ok} dans l'accusé : vrai pour une commande exécutée, maintenant ou auparavant. */
        boolean accusePositif() {
            return acceptee() || dejaExecutee;
        }
    }

    /** Nombre d'identifiants de commandes exécutées gardés pour reconnaître une réémission. */
    private static final int MEMOIRE = 32;

    private static final Pattern ID = Pattern.compile("\"id\":\"([0-9a-f-]{36})\"");
    private static final Pattern DESTINATAIRE = Pattern.compile("\"dev\":\"([A-Za-z0-9-]{1,32})\"");
    private static final Pattern CODE = Pattern.compile("\"cmd\":\"([a-z]{1,8})\"");
    private static final Pattern NUMERO = Pattern.compile("\"n\":(\\d{1,19})");
    private static final Pattern EXPIRATION = Pattern.compile("\"exp\":(\\d{1,12})");

    private final String identifiant;
    private final PublicKey clePlateforme;
    private long dernierNumero;
    private final java.util.ArrayDeque<String> executees = new java.util.ArrayDeque<>();

    /** @param clePlateforme clé publique de la plateforme ; sans elle, aucune commande ne peut être authentifiée */
    VerificateurCommandes(String identifiant, PublicKey clePlateforme) {
        this.identifiant = identifiant;
        this.clePlateforme = clePlateforme;
    }

    synchronized Resultat verifier(String message, Instant maintenant) {
        String[] parties = message == null ? new String[0] : message.split("\\.");
        byte[] contenu;
        byte[] signature;
        try {
            if (parties.length != 2) {
                return new Resultat(null, Rejet.FORMAT, null);
            }
            contenu = Base64.getUrlDecoder().decode(parties[0]);
            signature = Base64.getUrlDecoder().decode(parties[1]);
        } catch (IllegalArgumentException erreur) {
            return new Resultat(null, Rejet.FORMAT, null);
        }
        String corps = new String(contenu, StandardCharsets.UTF_8);
        String id = groupe(ID, corps);
        if (!signatureValide(contenu, signature)) {
            return new Resultat(null, Rejet.SIGNATURE, id);
        }
        String code = groupe(CODE, corps);
        String numero = groupe(NUMERO, corps);
        String expiration = groupe(EXPIRATION, corps);
        if (id == null || code == null || numero == null || expiration == null) {
            return new Resultat(null, Rejet.FORMAT, id);
        }
        if (!identifiant.equals(groupe(DESTINATAIRE, corps))) {
            return new Resultat(null, Rejet.DESTINATAIRE, id);
        }
        if (maintenant.getEpochSecond() >= Long.parseLong(expiration)) {
            return new Resultat(null, Rejet.EXPIREE, id);
        }
        long n = Long.parseLong(numero);
        if (n <= dernierNumero) {
            // La plateforme réémet une commande dont l'accusé s'est perdu : elle n'est pas rejouée, mais accusée.
            return new Resultat(null, Rejet.REJEU, id, executees.contains(id));
        }
        dernierNumero = n;
        executees.addLast(id);
        if (executees.size() > MEMOIRE) {
            executees.removeFirst();
        }
        return new Resultat(new Commande(id, code, n, corps), null, id);
    }

    private boolean signatureValide(byte[] contenu, byte[] signature) {
        if (clePlateforme == null) {
            return false;
        }
        try {
            Signature verification = Signature.getInstance("SHA256withECDSAinP1363Format");
            verification.initVerify(clePlateforme);
            verification.update(contenu);
            return verification.verify(signature);
        } catch (GeneralSecurityException erreur) {
            return false;
        }
    }

    private static String groupe(Pattern motif, String texte) {
        Matcher correspondance = motif.matcher(texte);
        return correspondance.find() ? correspondance.group(1) : null;
    }
}
