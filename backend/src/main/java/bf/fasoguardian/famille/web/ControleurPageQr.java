package bf.fasoguardian.famille.web;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import bf.fasoguardian.famille.application.PagePubliqueQr;
import bf.fasoguardian.famille.application.PagePubliqueQr.Etat;
import bf.fasoguardian.famille.application.PagePubliqueQr.VuePublique;
import bf.fasoguardian.famille.web.GabaritPageQr.Modele;
import bf.fasoguardian.plateforme.erreurs.CodeErreur;
import bf.fasoguardian.plateforme.erreurs.ErreurMetier;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Page publique QR, servie en HTML complet sans aucun script (écrans 51 à 54). Jeton valide ou non, la
 * réponse est un 200 rendu dans le même délai minimal : rien ne distingue un jeton inconnu d'un jeton
 * abîmé, ni ne révèle l'existence d'un bracelet.
 */
@RestController
@Hidden
class ControleurPageQr {

    private static final MediaType HTML = MediaType.parseMediaType("text/html;charset=UTF-8");
    private static final String CSP = "default-src 'none'; style-src 'unsafe-inline'; img-src data:; "
            + "form-action 'self'; base-uri 'none'; frame-ancestors 'none'";

    private final PagePubliqueQr page;
    private final GabaritPageQr gabarit;
    private final Duration delaiMinimal;

    ControleurPageQr(PagePubliqueQr page, GabaritPageQr gabarit,
            @Value("${fasoguardian.qr.delai-minimal:PT0.3S}") Duration delaiMinimal) {
        this.page = page;
        this.gabarit = gabarit;
        this.delaiMinimal = delaiMinimal;
    }

    @GetMapping("/q/{jeton}")
    ResponseEntity<String> consulter(@PathVariable String jeton, HttpServletRequest requete) {
        long debut = System.nanoTime();
        try {
            return html(HttpStatus.OK, modele(page.consulter(jeton, requete.getRemoteAddr()), jeton, false, null));
        } catch (ErreurMetier erreur) {
            return tropDeRequetes(erreur);
        } finally {
            attendre(debut);
        }
    }

    @PostMapping(path = "/q/{jeton}/prevenir", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    ResponseEntity<String> prevenir(@PathVariable String jeton, @RequestParam(required = false) String telephone,
            @RequestParam(required = false) String lieu, HttpServletRequest requete) {
        long debut = System.nanoTime();
        try {
            return html(HttpStatus.OK, modele(page.prevenir(jeton, requete.getRemoteAddr(), telephone, lieu), jeton, true, null));
        } catch (ErreurMetier erreur) {
            if (erreur.code() == CodeErreur.TELEPHONE_INVALIDE) {
                // La page est rendue à nouveau avec le message d'erreur, sans recompter un scan.
                return html(HttpStatus.OK, modele(page.relire(jeton), jeton, false, erreur.getMessage()));
            }
            if (erreur.code() == CodeErreur.RESSOURCE_INTROUVABLE) {
                return html(HttpStatus.OK, modele(null, jeton, false, null));
            }
            return tropDeRequetes(erreur);
        } finally {
            attendre(debut);
        }
    }

    private Modele modele(VuePublique vue, String jeton, boolean prevenu, String erreurFormulaire) {
        if (vue == null || vue.etat() == Etat.INCONNU) {
            return new Modele("inconnu", Map.of(), Set.of(), Map.of());
        }
        Map<String, String> valeurs = new HashMap<>();
        Set<String> conditions = new HashSet<>(Set.of("numero"));
        valeurs.put("numero", vue.numeroBracelet());
        if (vue.etat() == Etat.DESACTIVE) {
            return new Modele("desactive", valeurs, conditions, Map.of());
        }
        valeurs.put("jeton", jeton);
        List<Map<String, String>> elements = new ArrayList<>();
        vue.informations().forEach(information -> elements.add(Map.of("type", information.type(), "libelle", information.libelle())));
        List<Map<String, String>> contacts = new ArrayList<>();
        vue.contacts().forEach(contact -> contacts.add(Map.of("lien", contact.lien(), "telephone", contact.telephone())));
        if (!elements.isEmpty()) {
            conditions.add("medical");
        }
        if (erreurFormulaire != null) {
            conditions.add("erreurFormulaire");
            valeurs.put("erreurFormulaire", erreurFormulaire);
        }
        return new Modele(prevenu ? "prevenu" : "trouve", valeurs, conditions, Map.of("elements", elements, "contacts", contacts));
    }

    private ResponseEntity<String> html(HttpStatus statut, Modele modele) {
        return ResponseEntity.status(statut).contentType(HTML)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Content-Security-Policy", CSP)
                .header("X-Robots-Tag", "noindex, nofollow")
                .body(gabarit.rendre(modele));
    }

    /** Source bloquée ou trop de messages : page générique, statut 429. */
    private ResponseEntity<String> tropDeRequetes(ErreurMetier erreur) {
        if (erreur.code() != CodeErreur.TROP_DE_REQUETES) {
            throw erreur;
        }
        return html(HttpStatus.TOO_MANY_REQUESTS, new Modele("inconnu", Map.of(), Set.of(), Map.of()));
    }

    /** Délai de réponse plancher, identique pour un jeton valide et un jeton invalide (anti-énumération). */
    private void attendre(long debutNanos) {
        long reste = delaiMinimal.toNanos() - (System.nanoTime() - debutNanos);
        if (reste > 0) {
            try {
                Thread.sleep(Duration.ofNanos(reste));
            } catch (InterruptedException interruption) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
