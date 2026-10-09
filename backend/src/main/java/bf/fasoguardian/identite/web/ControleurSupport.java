package bf.fasoguardian.identite.web;

import java.util.List;
import java.util.UUID;

import bf.fasoguardian.identite.application.Support;
import bf.fasoguardian.identite.application.Support.ArticleVue;
import bf.fasoguardian.identite.application.Support.Categorie;
import bf.fasoguardian.identite.application.Support.CategorieVue;
import bf.fasoguardian.identite.application.Support.DemandeVue;
import bf.fasoguardian.identite.application.Support.SaisieArticle;
import bf.fasoguardian.identite.application.Support.Statut;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Aide et demandes de support, côté parent et côté opérateur (US-PAR-017, US-SUP-001). */
@RestController
@Tag(name = "Support")
@SecurityRequirement(name = "jetonAcces")
class ControleurSupport {

    private static final String PARENT = "hasRole('PARENT')";
    private static final String OPERATEUR = "hasRole('SUPPORT')";

    private final Support support;

    ControleurSupport(Support support) {
        this.support = support;
    }

    record DemandeOuverture(@NotBlank @Size(max = 120) String objet, @NotBlank @Size(max = 2000) String message) {
    }

    record DemandeMessage(@NotBlank @Size(max = 2000) String message) {
    }

    record DemandeReponse(@NotBlank @Size(max = 2000) String message, @NotNull Statut suite) {
    }

    record DemandeArticle(@NotNull Categorie categorie, @NotBlank @Size(max = 120) String titre, @NotBlank @Size(max = 6000) String contenu) {
    }

    // ------------------------------------------------------------------ parent

    @Operation(summary = "Catégories de l'aide et nombre d'articles publiés")
    @PreAuthorize(PARENT)
    @GetMapping("/api/v1/aide/categories")
    List<CategorieVue> categories() {
        return support.categories();
    }

    @Operation(summary = "Articles publiés, les plus lus d'abord ; filtre par catégorie ou par mots")
    @PreAuthorize(PARENT)
    @GetMapping("/api/v1/aide/articles")
    List<ArticleVue> articles(@RequestParam(required = false) Categorie categorie, @RequestParam(required = false) @Size(max = 80) String q) {
        return support.articlesPublies(categorie, q);
    }

    @Operation(summary = "Un article publié")
    @PreAuthorize(PARENT)
    @GetMapping("/api/v1/aide/articles/{slug}")
    ArticleVue article(@PathVariable String slug) {
        return support.lire(slug);
    }

    @Operation(summary = "Mes demandes de support, la plus récemment modifiée d'abord")
    @PreAuthorize(PARENT)
    @GetMapping("/api/v1/support/demandes")
    ResponseEntity<List<DemandeVue>> mesDemandes(@AuthenticationPrincipal Jwt jeton) {
        return sansCache(support.mesDemandes(id(jeton)));
    }

    @Operation(summary = "Ouvre une demande de support")
    @PreAuthorize(PARENT)
    @PostMapping("/api/v1/support/demandes")
    ResponseEntity<DemandeVue> ouvrir(@AuthenticationPrincipal Jwt jeton, @Valid @RequestBody DemandeOuverture demande) {
        return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(support.ouvrir(id(jeton), demande.objet(), demande.message()));
    }

    @Operation(summary = "Une de mes demandes : statut et réponses")
    @PreAuthorize(PARENT)
    @GetMapping("/api/v1/support/demandes/{demandeId}")
    ResponseEntity<DemandeVue> maDemande(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID demandeId) {
        return sansCache(support.maDemande(id(jeton), demandeId));
    }

    @Operation(summary = "Ajoute un message à ma demande ; elle est rouverte si elle attendait ma réponse ou était résolue")
    @PreAuthorize(PARENT)
    @PostMapping("/api/v1/support/demandes/{demandeId}/messages")
    ResponseEntity<DemandeVue> completer(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID demandeId,
            @Valid @RequestBody DemandeMessage demande) {
        return sansCache(support.completer(id(jeton), demandeId, demande.message()));
    }

    // ---------------------------------------------------------------- opérateur

    @Operation(summary = "Demandes dans l'état donné, les plus anciennes d'abord")
    @PreAuthorize(OPERATEUR)
    @GetMapping("/api/v1/console/support/demandes")
    ResponseEntity<List<DemandeVue>> demandes(@AuthenticationPrincipal Jwt jeton, @RequestParam(defaultValue = "OUVERTE") Statut statut) {
        return sansCache(support.demandes(id(jeton), statut));
    }

    @Operation(summary = "Une demande et les coordonnées du parent (consultation journalisée)")
    @PreAuthorize(OPERATEUR)
    @GetMapping("/api/v1/console/support/demandes/{demandeId}")
    ResponseEntity<DemandeVue> demande(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID demandeId) {
        return sansCache(support.demande(id(jeton), demandeId));
    }

    @Operation(summary = "Répond au parent, qui en est notifié ; la demande attend ensuite le parent ou est résolue")
    @PreAuthorize(OPERATEUR)
    @PostMapping("/api/v1/console/support/demandes/{demandeId}/reponse")
    ResponseEntity<DemandeVue> repondre(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID demandeId,
            @Valid @RequestBody DemandeReponse demande) {
        return sansCache(support.repondre(id(jeton), demandeId, demande.message(), demande.suite()));
    }

    @Operation(summary = "Tous les articles, brouillons compris")
    @PreAuthorize(OPERATEUR)
    @GetMapping("/api/v1/console/support/articles")
    ResponseEntity<List<ArticleVue>> tousLesArticles() {
        return sansCache(support.articles());
    }

    @Operation(summary = "Rédige un article, en brouillon")
    @PreAuthorize(OPERATEUR)
    @PostMapping("/api/v1/console/support/articles")
    ResponseEntity<ArticleVue> rediger(@AuthenticationPrincipal Jwt jeton, @Valid @RequestBody DemandeArticle demande) {
        return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(support.rediger(id(jeton), new SaisieArticle(demande.categorie(), demande.titre(), demande.contenu())));
    }

    @Operation(summary = "Modifie un article")
    @PreAuthorize(OPERATEUR)
    @PutMapping("/api/v1/console/support/articles/{articleId}")
    ResponseEntity<ArticleVue> modifier(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID articleId,
            @Valid @RequestBody DemandeArticle demande) {
        return sansCache(support.modifier(id(jeton), articleId, new SaisieArticle(demande.categorie(), demande.titre(), demande.contenu())));
    }

    @Operation(summary = "Publie l'article : il devient consultable par les parents")
    @PreAuthorize(OPERATEUR)
    @PostMapping("/api/v1/console/support/articles/{articleId}/publication")
    ResponseEntity<ArticleVue> publier(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID articleId) {
        return sansCache(support.publier(id(jeton), articleId, true));
    }

    @Operation(summary = "Retire l'article de la publication")
    @PreAuthorize(OPERATEUR)
    @PostMapping("/api/v1/console/support/articles/{articleId}/retrait")
    ResponseEntity<ArticleVue> retirer(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID articleId) {
        return sansCache(support.publier(id(jeton), articleId, false));
    }

    private static UUID id(Jwt jeton) {
        return UUID.fromString(jeton.getSubject());
    }

    private static <T> ResponseEntity<T> sansCache(T corps) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(corps);
    }
}
