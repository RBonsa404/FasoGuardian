package bf.fasoguardian.plateforme.idempotence;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import bf.fasoguardian.plateforme.erreurs.CodeErreur;
import bf.fasoguardian.plateforme.erreurs.Problemes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Écritures rejouables (FG-DOC-06 §5.4, US-SYS-002). L'application Parents garde hors ligne les actions de
 * configuration et les renvoie au retour du réseau avec la clé d'idempotence choisie au premier essai : si la
 * première tentative avait en fait abouti, la seconde n'a aucun effet et reçoit le même statut, sans corps.
 * Placé après l'authentification, le filtre range les clés par compte : une clé ne sert qu'à son auteur.
 */
public class FiltreIdempotence extends OncePerRequestFilter {

    public static final String ENTETE = "Idempotency-Key";
    public static final String ENTETE_REJOUEE = "Idempotent-Replayed";
    private static final Pattern CLE = Pattern.compile("[A-Za-z0-9_-]{16,80}");
    private static final Set<String> ECRITURES = Set.of("POST", "PUT", "PATCH", "DELETE");
    /** Au-delà, la requête n'est pas une action de configuration : elle passe sans idempotence générique. */
    private static final int CORPS_MAXIMUM = 256 * 1024;

    private final DepotIdempotence depot;
    private final JsonMapper json;
    private final Clock horloge;

    public FiltreIdempotence(DepotIdempotence depot, JsonMapper json, Clock horloge) {
        this.depot = depot;
        this.json = json;
        this.horloge = horloge;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest requete) {
        String chemin = requete.getRequestURI();
        return requete.getHeader(ENTETE) == null || !ECRITURES.contains(requete.getMethod())
                || !chemin.startsWith("/api/v1/")
                // Les paiements ont leur propre idempotence, qui rend le paiement d'origine (module abonnements).
                || chemin.endsWith("/abonnement/paiements")
                || (requete.getContentType() != null && requete.getContentType().startsWith(MediaType.MULTIPART_FORM_DATA_VALUE));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse, FilterChain suite)
            throws ServletException, IOException {
        String cle = requete.getHeader(ENTETE);
        if (!CLE.matcher(cle).matches()) {
            refuser(reponse, CodeErreur.REQUETE_INVALIDE,
                    "L'en-tête Idempotency-Key doit compter 16 à 80 lettres, chiffres, tirets ou soulignés.");
            return;
        }
        byte[] corps = requete.getInputStream().readNBytes(CORPS_MAXIMUM + 1);
        if (corps.length > CORPS_MAXIMUM) {
            refuser(reponse, CodeErreur.REQUETE_INVALIDE, "Requête trop volumineuse pour être rejouée.");
            return;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String sujet = auth != null && auth.isAuthenticated() ? auth.getName() : "anonyme";
        String empreinte = empreinte(requete.getMethod(), requete.getRequestURI(), corps);

        if (!depot.reserver(sujet, cle, empreinte, horloge.instant())) {
            Optional<DepotIdempotence.Requete> deja = depot.lire(sujet, cle);
            if (deja.isEmpty() || !deja.get().empreinte().equals(empreinte)) {
                refuser(reponse, CodeErreur.CONFLIT, "Cette clé d'idempotence a déjà servi pour une autre requête.");
            } else if (deja.get().statut() == null) {
                refuser(reponse, CodeErreur.CONFLIT, "La même requête est déjà en cours de traitement.");
            } else {
                reponse.setStatus(deja.get().statut());
                reponse.setHeader(ENTETE_REJOUEE, "true");
            }
            return;
        }
        try {
            suite.doFilter(new RequeteRelue(requete, corps), reponse);
        } catch (IOException | ServletException | RuntimeException erreur) {
            depot.oublier(sujet, cle);
            throw erreur;
        }
        if (reponse.getStatus() >= 500) {
            // Échec du serveur : l'action n'a pas eu lieu, le client pourra la renvoyer avec la même clé.
            depot.oublier(sujet, cle);
        } else {
            depot.terminer(sujet, cle, reponse.getStatus());
        }
    }

    private void refuser(HttpServletResponse reponse, CodeErreur code, String detail) throws IOException {
        reponse.setStatus(code.statut().value());
        reponse.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        reponse.setCharacterEncoding("UTF-8");
        json.writeValue(reponse.getOutputStream(), Problemes.de(code, detail));
    }

    static String empreinte(String methode, String chemin, byte[] corps) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update((methode + " " + chemin + "\n").getBytes(StandardCharsets.UTF_8));
            sha.update(corps);
            return HexFormat.of().formatHex(sha.digest());
        } catch (NoSuchAlgorithmException erreur) {
            throw new IllegalStateException(erreur);
        }
    }

    /** Requête dont le corps, déjà lu pour l'empreinte, est relu par le contrôleur. */
    private static final class RequeteRelue extends HttpServletRequestWrapper {

        private final byte[] corps;

        RequeteRelue(HttpServletRequest requete, byte[] corps) {
            super(requete);
            this.corps = corps;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream flux = new ByteArrayInputStream(corps);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return flux.read();
                }

                @Override
                public int read(byte[] tampon, int debut, int longueur) {
                    return flux.read(tampon, debut, longueur);
                }

                @Override
                public boolean isFinished() {
                    return flux.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener ecouteur) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public java.io.BufferedReader getReader() {
            return new java.io.BufferedReader(new java.io.InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public int getContentLength() {
            return corps.length;
        }

        @Override
        public long getContentLengthLong() {
            return corps.length;
        }
    }
}
