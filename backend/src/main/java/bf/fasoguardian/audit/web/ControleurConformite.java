package bf.fasoguardian.audit.web;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import bf.fasoguardian.audit.JournalAudit.Resultat;
import bf.fasoguardian.audit.application.Conformite;
import bf.fasoguardian.audit.application.Conformite.DemandeVue;
import bf.fasoguardian.audit.application.Conformite.Export;
import bf.fasoguardian.audit.application.Conformite.Rapport;
import bf.fasoguardian.audit.application.Conformite.Tableau;
import bf.fasoguardian.audit.application.ConsultationJournal;
import bf.fasoguardian.audit.application.ConsultationJournal.Chaine;
import bf.fasoguardian.audit.application.ConsultationJournal.Filtre;
import bf.fasoguardian.audit.application.ConsultationJournal.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Journal d'audit et conformité, côté administrateur (US-ADM-002, US-ADM-003) ; droit d'accès, côté parent. */
@RestController
@Tag(name = "Audit et conformité")
@SecurityRequirement(name = "jetonAcces")
class ControleurConformite {

    private static final String ADMIN = "hasRole('ADMIN')";

    private final Conformite conformite;
    private final ConsultationJournal consultation;

    ControleurConformite(Conformite conformite, ConsultationJournal consultation) {
        this.conformite = conformite;
        this.consultation = consultation;
    }

    @Operation(summary = "Journal d'audit, entrées les plus récentes d'abord (consultation journalisée)")
    @PreAuthorize(ADMIN)
    @GetMapping("/api/v1/console/audit")
    ResponseEntity<Page> journal(@AuthenticationPrincipal Jwt jeton, @RequestParam(required = false) String action,
            @RequestParam(required = false) String role, @RequestParam(required = false) Resultat resultat,
            @RequestParam(required = false) UUID acteur,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant depuis,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant jusqua,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int taille) {
        return sansCache(consultation.entrees(id(jeton), new Filtre(action, role, resultat, acteur, depuis, jusqua), page, taille));
    }

    @Operation(summary = "Vérifie la chaîne d'empreintes du journal ; le résultat est consigné")
    @PreAuthorize(ADMIN)
    @PostMapping("/api/v1/console/audit/verification")
    ResponseEntity<Chaine> verifier(@AuthenticationPrincipal Jwt jeton) {
        return sansCache(consultation.verifier(id(jeton)));
    }

    @Operation(summary = "Tableau de bord de conformité : AIPD, durées de conservation, purges, demandes")
    @PreAuthorize(ADMIN)
    @GetMapping("/api/v1/console/conformite")
    ResponseEntity<Tableau> tableau() {
        return sansCache(conformite.tableau());
    }

    @Operation(summary = "Demandes d'effacement, les plus récentes d'abord")
    @PreAuthorize(ADMIN)
    @GetMapping("/api/v1/console/conformite/demandes")
    ResponseEntity<List<DemandeVue>> demandes() {
        return sansCache(conformite.demandes());
    }

    @Operation(summary = "Exécute une demande d'effacement : données supprimées, accusé envoyé au demandeur")
    @PreAuthorize(ADMIN)
    @PostMapping("/api/v1/console/conformite/demandes/{demandeId}/execution")
    ResponseEntity<DemandeVue> executer(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID demandeId) {
        return sansCache(conformite.traiter(id(jeton), demandeId));
    }

    @Operation(summary = "Rapport mensuel de conformité en PDF (mois au format AAAA-MM)")
    @PreAuthorize(ADMIN)
    @GetMapping(value = "/api/v1/console/conformite/rapport", produces = MediaType.APPLICATION_PDF_VALUE)
    ResponseEntity<byte[]> rapport(@AuthenticationPrincipal Jwt jeton, @RequestParam YearMonth mois) {
        Rapport rapport = conformite.rapport(id(jeton), mois);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + rapport.nom() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF).body(rapport.pdf());
    }

    @Operation(summary = "Droit d'accès : toutes les données détenues sur moi et mes enfants (export journalisé)")
    @PreAuthorize("hasRole('PARENT')")
    @GetMapping("/api/v1/moi/donnees")
    ResponseEntity<Map<String, Object>> mesDonnees(@AuthenticationPrincipal Jwt jeton) {
        Export export = conformite.acceder(id(jeton));
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"mes-donnees-" + export.reference() + ".json\"")
                .body(export.contenu());
    }

    private static UUID id(Jwt jeton) {
        return UUID.fromString(jeton.getSubject());
    }

    private static <T> ResponseEntity<T> sansCache(T corps) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(corps);
    }
}
