package bf.fasoguardian.famille.web;

import java.util.List;
import java.util.UUID;

import bf.fasoguardian.famille.application.DossierMedical;
import bf.fasoguardian.famille.application.DossierMedical.ContactVue;
import bf.fasoguardian.famille.application.DossierMedical.ElementMedical;
import bf.fasoguardian.famille.application.DossierMedical.FicheSanteVue;
import bf.fasoguardian.famille.application.DossierMedical.RevisionVue;
import bf.fasoguardian.famille.application.DossierMedical.SaisieContact;
import bf.fasoguardian.famille.application.Familles;
import bf.fasoguardian.famille.application.Familles.FicheEnfant;
import bf.fasoguardian.famille.application.Familles.Revision;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fiche enfant, fiche médicale et contacts d'urgence (US-PAR-003, 004, 011).
 * Aucune réponse de ce contrôleur ne peut être mise en cache.
 */
@RestController
@RequestMapping("/api/v1/enfants")
@Tag(name = "Famille")
@SecurityRequirement(name = "jetonAcces")
@PreAuthorize("hasRole('PARENT')")
class ControleurFamille {

    private final Familles familles;
    private final DossierMedical medical;

    ControleurFamille(Familles familles, DossierMedical medical) {
        this.familles = familles;
        this.medical = medical;
    }

    record DemandeModification(@Size(max = 80) String prenom, @Size(max = 80) String nom) {
    }

    record ElementDto(@NotNull DossierMedical.TypeElement type, @NotBlank @Size(max = 120) String libelle, boolean critique) {
    }

    record DemandeSante(@Size(max = 3) String groupeSanguin, Boolean groupeSanguinSurQr,
            @Valid @Size(max = 20) List<ElementDto> elements) {
    }

    record DemandeContact(@NotBlank @Size(max = 40) String lien, @NotBlank @Size(max = 80) String nom,
            @NotBlank @Size(max = 32) String telephone, boolean visibleSurQr) {
    }

    @Operation(summary = "Enfants rattachés au parent par un lien de tutelle actif")
    @GetMapping
    ResponseEntity<List<FicheEnfant>> mesEnfants(@AuthenticationPrincipal Jwt jeton) {
        return sansCache(familles.enfantsDe(id(jeton)));
    }

    @Operation(summary = "Fiche d'un enfant")
    @GetMapping("/{enfantId}")
    ResponseEntity<FicheEnfant> fiche(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID enfantId) {
        return sansCache(familles.fiche(id(jeton), enfantId));
    }

    @Operation(summary = "Corrige le prénom ou le nom ; la modification est horodatée et historisée")
    @PatchMapping("/{enfantId}")
    ResponseEntity<FicheEnfant> modifier(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID enfantId,
            @Valid @RequestBody DemandeModification demande) {
        return sansCache(familles.modifier(id(jeton), enfantId, demande.prenom(), demande.nom()));
    }

    @Operation(summary = "Historique des modifications de la fiche enfant")
    @GetMapping("/{enfantId}/historique")
    ResponseEntity<List<Revision>> historique(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID enfantId) {
        return sansCache(familles.historique(id(jeton), enfantId));
    }

    @Operation(summary = "Fiche médicale (consultation journalisée)")
    @GetMapping("/{enfantId}/sante")
    ResponseEntity<FicheSanteVue> sante(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID enfantId) {
        return sansCache(medical.fiche(id(jeton), enfantId));
    }

    @Operation(summary = "Remplace la fiche médicale ; les éléments marqués critiques apparaissent sur la page publique QR")
    @PutMapping("/{enfantId}/sante")
    ResponseEntity<FicheSanteVue> enregistrerSante(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID enfantId,
            @Valid @RequestBody DemandeSante demande) {
        List<ElementMedical> elements = demande.elements() == null ? List.of() : demande.elements().stream()
                .map(e -> new ElementMedical(e.type(), e.libelle(), e.critique())).toList();
        return sansCache(medical.enregistrer(id(jeton), enfantId, demande.groupeSanguin(),
                Boolean.TRUE.equals(demande.groupeSanguinSurQr()), elements));
    }

    @Operation(summary = "Journal des révisions de la fiche médicale")
    @GetMapping("/{enfantId}/sante/revisions")
    ResponseEntity<List<RevisionVue>> revisionsSante(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID enfantId) {
        return sansCache(medical.revisions(id(jeton), enfantId));
    }

    @Operation(summary = "Contacts d'urgence de l'enfant")
    @GetMapping("/{enfantId}/contacts")
    ResponseEntity<List<ContactVue>> contacts(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID enfantId) {
        return sansCache(medical.contacts(id(jeton), enfantId));
    }

    @Operation(summary = "Ajoute un contact d'urgence (cinq au maximum)")
    @PostMapping("/{enfantId}/contacts")
    ResponseEntity<ContactVue> ajouterContact(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID enfantId,
            @Valid @RequestBody DemandeContact demande) {
        return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(medical.ajouter(id(jeton), enfantId, saisie(demande)));
    }

    @Operation(summary = "Modifie un contact d'urgence")
    @PutMapping("/{enfantId}/contacts/{contactId}")
    ResponseEntity<ContactVue> modifierContact(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID enfantId,
            @PathVariable UUID contactId, @Valid @RequestBody DemandeContact demande) {
        return sansCache(medical.modifier(id(jeton), enfantId, contactId, saisie(demande)));
    }

    @Operation(summary = "Supprime un contact d'urgence")
    @DeleteMapping("/{enfantId}/contacts/{contactId}")
    ResponseEntity<Void> supprimerContact(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID enfantId,
            @PathVariable UUID contactId) {
        medical.supprimer(id(jeton), enfantId, contactId);
        return ResponseEntity.noContent().build();
    }

    private static SaisieContact saisie(DemandeContact d) {
        return new SaisieContact(d.lien(), d.nom(), d.telephone(), d.visibleSurQr());
    }

    private static UUID id(Jwt jeton) {
        return UUID.fromString(jeton.getSubject());
    }

    private static <T> ResponseEntity<T> sansCache(T corps) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").contentType(MediaType.APPLICATION_JSON)
                .body(corps);
    }
}
