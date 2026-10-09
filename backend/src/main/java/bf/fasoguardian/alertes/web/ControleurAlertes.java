package bf.fasoguardian.alertes.web;

import java.util.List;
import java.util.UUID;

import bf.fasoguardian.alertes.application.Alertes;
import bf.fasoguardian.alertes.application.Alertes.AlerteVue;
import bf.fasoguardian.alertes.application.Escalades;
import bf.fasoguardian.alertes.application.Escalades.Apercu;
import bf.fasoguardian.alertes.application.Escalades.Dossier;
import bf.fasoguardian.alertes.application.Escalades.SignalementVue;
import bf.fasoguardian.alertes.application.Retraits;
import bf.fasoguardian.alertes.application.Retraits.AutorisationVue;
import bf.fasoguardian.alertes.domaine.AutorisationRetrait.Motif;
import bf.fasoguardian.plateforme.erreurs.CodeErreur;
import bf.fasoguardian.plateforme.erreurs.ErreurMetier;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Alertes et autorisation de retrait, côté tuteur (US-ENF-001, US-PAR-008, US-PAR-010, US-PAR-012). */
@RestController
@Tag(name = "Alertes")
@SecurityRequirement(name = "jetonAcces")
@PreAuthorize("hasRole('PARENT')")
class ControleurAlertes {

    private final Alertes alertes;
    private final Retraits retraits;
    private final Escalades escalades;

    ControleurAlertes(Alertes alertes, Retraits retraits, Escalades escalades) {
        this.escalades = escalades;
        this.alertes = alertes;
        this.retraits = retraits;
    }

    record DemandeMotif(@NotBlank @Size(max = 200) String motif) {
    }

    record DemandeEscalade(@Size(max = 6) String codeSecondFacteur) {
    }

    record DemandeRetrait(@NotNull Motif motif, @NotNull Integer dureeMinutes, @Size(max = 6) String codeSecondFacteur) {
    }

    record DemandeProlongation(@NotNull Integer minutes, @Size(max = 6) String codeSecondFacteur) {
    }

    @Operation(summary = "Alertes de mes enfants, les plus récentes d'abord ; « enCours » limite aux alertes non closes")
    @GetMapping("/api/v1/alertes")
    ResponseEntity<List<AlerteVue>> mesAlertes(@AuthenticationPrincipal Jwt jeton,
            @RequestParam(defaultValue = "false") boolean enCours) {
        return sansCache(alertes.mesAlertes(id(jeton), enCours));
    }

    @Operation(summary = "Une alerte et son journal d'acquittement")
    @GetMapping("/api/v1/alertes/{alerteId}")
    ResponseEntity<AlerteVue> alerte(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID alerteId) {
        return sansCache(alertes.alerte(id(jeton), alerteId));
    }

    @Operation(summary = "Prend l'alerte en charge")
    @PostMapping("/api/v1/alertes/{alerteId}/acquittement")
    ResponseEntity<AlerteVue> acquitter(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID alerteId) {
        return sansCache(alertes.acquitter(id(jeton), alerteId));
    }

    @Operation(summary = "Lève une alerte prise en charge ou escaladée ; le motif est journalisé")
    @PostMapping("/api/v1/alertes/{alerteId}/levee")
    ResponseEntity<AlerteVue> lever(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID alerteId,
            @Valid @RequestBody DemandeMotif demande) {
        return sansCache(alertes.lever(id(jeton), alerteId, demande.motif()));
    }

    @Operation(summary = "Classe l'alerte en fausse alerte ; le motif est journalisé")
    @PostMapping("/api/v1/alertes/{alerteId}/fausse-alerte")
    ResponseEntity<AlerteVue> classerFausseAlerte(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID alerteId,
            @Valid @RequestBody DemandeMotif demande) {
        return sansCache(alertes.classerFausseAlerte(id(jeton), alerteId, demande.motif()));
    }

    @Operation(summary = "Aperçu du dossier de signalement avant confirmation (consultation journalisée)")
    @GetMapping("/api/v1/alertes/{alerteId}/signalement/apercu")
    ResponseEntity<Apercu> apercuDuSignalement(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID alerteId) {
        return sansCache(escalades.apercu(id(jeton), alerteId));
    }

    @Operation(summary = "Escalade vers les forces de sécurité (second facteur ESCALADER_FORCES_SECURITE requis) ; "
            + "sans convention active, génère le dossier que le parent remet lui-même")
    @PostMapping("/api/v1/alertes/{alerteId}/escalade")
    ResponseEntity<SignalementVue> escalader(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID alerteId,
            @Valid @RequestBody DemandeEscalade demande) {
        return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(escalades.escalader(id(jeton), alerteId, demande.codeSecondFacteur()));
    }

    @Operation(summary = "Référence et état du dossier de signalement d'une alerte escaladée")
    @GetMapping("/api/v1/alertes/{alerteId}/signalement")
    ResponseEntity<SignalementVue> signalement(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID alerteId) {
        return sansCache(escalades.signalement(id(jeton), alerteId));
    }

    @Operation(summary = "Dossier de signalement en PDF, disponible 30 jours (téléchargement journalisé)")
    @GetMapping(value = "/api/v1/alertes/{alerteId}/signalement/dossier", produces = MediaType.APPLICATION_PDF_VALUE)
    ResponseEntity<byte[]> dossier(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID alerteId) {
        Dossier dossier = escalades.dossier(id(jeton), alerteId);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + dossier.reference() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF).body(dossier.pdf());
    }

    @Operation(summary = "Prend en charge d'un geste toutes les alertes ouvertes de l'enfant")
    @PostMapping("/api/v1/enfants/{enfantId}/alertes/prise-en-charge")
    ResponseEntity<List<AlerteVue>> prendreEnCharge(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID enfantId) {
        return sansCache(alertes.prendreEnCharge(id(jeton), enfantId));
    }

    @Operation(summary = "Le parent ouvre lui-même un signalement, horodaté et pris en charge par lui")
    @PostMapping("/api/v1/enfants/{enfantId}/signalement")
    ResponseEntity<AlerteVue> signaler(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID enfantId) {
        return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(alertes.signaler(id(jeton), enfantId));
    }

    @Operation(summary = "Journal des alertes de l'enfant, non modifiable (consultation journalisée)")
    @GetMapping("/api/v1/enfants/{enfantId}/journal")
    ResponseEntity<List<AlerteVue>> journal(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID enfantId) {
        return sansCache(alertes.journal(id(jeton), enfantId));
    }

    @Operation(summary = "Autorisation de retrait en cours")
    @GetMapping("/api/v1/enfants/{enfantId}/bracelet/retrait")
    ResponseEntity<AutorisationVue> retraitEnCours(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID enfantId) {
        return sansCache(retraits.enCours(id(jeton), enfantId).orElseThrow(
                () -> new ErreurMetier(CodeErreur.RESSOURCE_INTROUVABLE, "Aucun retrait n'est autorisé en ce moment.")));
    }

    @Operation(summary = "Autorise le retrait du bracelet pour 15 min à 12 h (second facteur AUTORISER_RETRAIT requis)")
    @PostMapping("/api/v1/enfants/{enfantId}/bracelet/retrait")
    ResponseEntity<AutorisationVue> autoriserRetrait(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID enfantId,
            @Valid @RequestBody DemandeRetrait demande) {
        return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.CACHE_CONTROL, "no-store").body(retraits
                .accorder(id(jeton), enfantId, demande.motif(), demande.dureeMinutes(), demande.codeSecondFacteur()));
    }

    @Operation(summary = "Prolonge la fenêtre de retrait en cours (second facteur AUTORISER_RETRAIT requis)")
    @PostMapping("/api/v1/enfants/{enfantId}/bracelet/retrait/prolongation")
    ResponseEntity<AutorisationVue> prolongerRetrait(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID enfantId,
            @Valid @RequestBody DemandeProlongation demande) {
        return sansCache(retraits.prolonger(id(jeton), enfantId, demande.minutes(), demande.codeSecondFacteur()));
    }

    @Operation(summary = "Bracelet remis : clôt la fenêtre de retrait, la surveillance reprend")
    @DeleteMapping("/api/v1/enfants/{enfantId}/bracelet/retrait")
    ResponseEntity<Void> terminerRetrait(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID enfantId) {
        retraits.terminer(id(jeton), enfantId);
        return ResponseEntity.noContent().build();
    }

    private static UUID id(Jwt jeton) {
        return UUID.fromString(jeton.getSubject());
    }

    private static <T> ResponseEntity<T> sansCache(T corps) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(corps);
    }
}
