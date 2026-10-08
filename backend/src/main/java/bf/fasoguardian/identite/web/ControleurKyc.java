package bf.fasoguardian.identite.web;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import bf.fasoguardian.identite.application.InstructionKyc;
import bf.fasoguardian.identite.application.InstructionKyc.ContenuPiece;
import bf.fasoguardian.identite.application.InstructionKyc.Decision;
import bf.fasoguardian.identite.application.InstructionKyc.DossierFile;
import bf.fasoguardian.identite.application.InstructionKyc.DossierInstruction;
import bf.fasoguardian.identite.application.InstructionKyc.DossierParent;
import bf.fasoguardian.identite.application.InstructionKyc.EnfantDeclare;
import bf.fasoguardian.identite.application.InstructionKyc.IdentiteDeclaree;
import bf.fasoguardian.identite.application.InstructionKyc.PieceVue;
import bf.fasoguardian.identite.domaine.DossierKyc.Canal;
import bf.fasoguardian.identite.domaine.DossierKyc.NatureLien;
import bf.fasoguardian.identite.domaine.DossierKyc.Statut;
import bf.fasoguardian.identite.domaine.DossierKyc.TypePiece;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Dossier KYC : dépôt par le parent, instruction par l'agent KYC (US-PAR-001). */
@RestController
@Tag(name = "Vérification KYC")
@SecurityRequirement(name = "jetonAcces")
class ControleurKyc {

    private static final String PARENT = "hasRole('PARENT')";
    private static final String AGENT_KYC = "hasRole('KYC')";

    private final InstructionKyc kyc;

    ControleurKyc(InstructionKyc kyc) {
        this.kyc = kyc;
    }

    record IdentiteDto(@NotBlank @Size(max = 80) String nom, @NotBlank @Size(max = 120) String prenoms,
            @NotBlank @Size(max = 24) String typePiece, @NotBlank @Size(max = 32) String numeroPiece) {
    }

    record EnfantDto(@NotBlank @Size(max = 80) String prenom, @NotBlank @Size(max = 80) String nom,
            @NotNull LocalDate dateNaissance) {
    }

    record DemandeDossier(@NotNull Canal canal, @NotNull NatureLien natureLien, @NotNull @Valid IdentiteDto demandeur,
            @NotNull @Valid EnfantDto enfant) {
    }

    record DemandeDecision(@NotNull Decision decision, @Size(max = 500) String motif) {
    }

    record PageDto<T>(List<T> elements, int page, int taille, long total) {
    }

    // ---------------------------------------------------------------- parent

    @Operation(summary = "Ouvre le dossier de vérification du parent")
    @PreAuthorize(PARENT)
    @PostMapping("/api/v1/kyc/dossiers")
    @ResponseStatus(HttpStatus.CREATED)
    DossierParent ouvrir(@AuthenticationPrincipal Jwt jeton, @Valid @RequestBody DemandeDossier demande) {
        IdentiteDto d = demande.demandeur();
        EnfantDto e = demande.enfant();
        return kyc.ouvrir(id(jeton), demande.canal(), demande.natureLien(),
                new IdentiteDeclaree(d.nom().trim(), d.prenoms().trim(), d.typePiece().trim(), d.numeroPiece().trim()),
                new EnfantDeclare(e.prenom().trim(), e.nom().trim(), e.dateNaissance()));
    }

    @Operation(summary = "Dossier le plus récent du parent, ou 204 s'il n'en a pas")
    @PreAuthorize(PARENT)
    @GetMapping("/api/v1/kyc/dossiers/courant")
    ResponseEntity<DossierParent> courant(@AuthenticationPrincipal Jwt jeton) {
        return kyc.courant(id(jeton)).map(dossier -> sansCache().body(dossier))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "Ajoute ou remplace une pièce (JPEG, PNG ou PDF, 5 Mo au plus)")
    @PreAuthorize(PARENT)
    @PostMapping(path = "/api/v1/kyc/dossiers/{dossierId}/pieces", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    PieceVue ajouterPiece(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID dossierId,
            @RequestParam TypePiece type, @RequestParam MultipartFile fichier) throws IOException {
        return kyc.ajouterPiece(id(jeton), dossierId, type, fichier.getBytes(), fichier.getContentType());
    }

    @Operation(summary = "Dépose le dossier pour instruction")
    @PreAuthorize(PARENT)
    @PostMapping("/api/v1/kyc/dossiers/{dossierId}/depot")
    DossierParent deposer(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID dossierId) {
        return kyc.deposer(id(jeton), dossierId);
    }

    // ----------------------------------------------------------------- agent

    @Operation(summary = "File d'instruction : dossiers déposés ou en instruction, sans donnée d'identité")
    @PreAuthorize(AGENT_KYC)
    @GetMapping("/api/v1/console/kyc/dossiers")
    ResponseEntity<PageDto<DossierFile>> file(@AuthenticationPrincipal Jwt jeton,
            @RequestParam(required = false) Set<Statut> statut,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var resultat = kyc.file(id(jeton), statut, page, size);
        return sansCache().body(new PageDto<>(resultat.getContent(), resultat.getNumber(), resultat.getSize(),
                resultat.getTotalElements()));
    }

    @Operation(summary = "Dossier complet pour instruction (consultation journalisée)")
    @PreAuthorize(AGENT_KYC)
    @GetMapping("/api/v1/console/kyc/dossiers/{dossierId}")
    ResponseEntity<DossierInstruction> consulter(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID dossierId) {
        return sansCache().body(kyc.consulter(id(jeton), dossierId));
    }

    @Operation(summary = "L'agent prend le dossier en charge")
    @PreAuthorize(AGENT_KYC)
    @PostMapping("/api/v1/console/kyc/dossiers/{dossierId}/prise-en-charge")
    ResponseEntity<DossierInstruction> prendreEnCharge(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID dossierId) {
        return sansCache().body(kyc.prendreEnCharge(id(jeton), dossierId));
    }

    @Operation(summary = "Contenu déchiffré d'une pièce (consultation journalisée)")
    @PreAuthorize(AGENT_KYC)
    @GetMapping("/api/v1/console/kyc/dossiers/{dossierId}/pieces/{pieceId}")
    ResponseEntity<byte[]> piece(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID dossierId,
            @PathVariable UUID pieceId) {
        ContenuPiece piece = kyc.lirePiece(id(jeton), dossierId, pieceId);
        return sansCache().contentType(MediaType.parseMediaType(piece.typeMime()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .body(piece.octets());
    }

    @Operation(summary = "Décision : approbation, rejet ou demande de complément (motif obligatoire hors approbation)")
    @PreAuthorize(AGENT_KYC)
    @PostMapping("/api/v1/console/kyc/dossiers/{dossierId}/decision")
    ResponseEntity<DossierInstruction> decider(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID dossierId,
            @Valid @RequestBody DemandeDecision demande) {
        return sansCache().body(kyc.decider(id(jeton), dossierId, demande.decision(), demande.motif()));
    }

    private static UUID id(Jwt jeton) {
        return UUID.fromString(jeton.getSubject());
    }

    /** Aucune mise en cache des données sensibles, ni par le navigateur ni par un intermédiaire. */
    private static ResponseEntity.BodyBuilder sansCache() {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store");
    }
}
