package bf.fasoguardian.abonnements.web;

import java.util.List;
import java.util.UUID;

import bf.fasoguardian.abonnements.application.ReceptionPaiements;
import bf.fasoguardian.abonnements.application.Souscriptions;
import bf.fasoguardian.abonnements.application.Souscriptions.AbonnementVue;
import bf.fasoguardian.abonnements.application.Souscriptions.OffreVue;
import bf.fasoguardian.abonnements.application.Souscriptions.PaiementVue;
import bf.fasoguardian.abonnements.application.Souscriptions.RecuPdf;
import bf.fasoguardian.abonnements.application.Souscriptions.RecuVue;
import bf.fasoguardian.abonnements.application.Souscriptions.SaisiePaiement;
import bf.fasoguardian.abonnements.domaine.Moyen;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Abonnement, paiement par mobile money et reçus (US-PAR-015) ; notifications de l'agrégateur. */
@RestController
@Tag(name = "Abonnements")
class ControleurAbonnements {

    private static final String PARENT = "hasRole('PARENT')";

    private final Souscriptions souscriptions;
    private final ReceptionPaiements reception;

    ControleurAbonnements(Souscriptions souscriptions, ReceptionPaiements reception) {
        this.souscriptions = souscriptions;
        this.reception = reception;
    }

    record DemandePaiement(@NotBlank @Size(max = 16) String offre, @NotNull Moyen moyen, @NotBlank @Size(max = 24) String numero,
            boolean renouvellementAuto) {
    }

    record DemandeRenouvellement(@NotNull Boolean automatique) {
    }

    @Operation(summary = "Offres qu'une famille peut souscrire ; l'administrateur les lit à l'écran de paramétrage")
    @SecurityRequirement(name = "jetonAcces")
    @PreAuthorize("hasAnyRole('PARENT', 'ADMIN')")
    @GetMapping("/api/v1/offres")
    List<OffreVue> offres() {
        return souscriptions.offres();
    }

    @Operation(summary = "Abonnement de l'enfant et droits qu'il ouvre")
    @SecurityRequirement(name = "jetonAcces")
    @PreAuthorize(PARENT)
    @GetMapping("/api/v1/enfants/{enfantId}/abonnement")
    ResponseEntity<AbonnementVue> abonnement(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID enfantId) {
        return sansCache(souscriptions.abonnement(id(jeton), enfantId));
    }

    @Operation(summary = "Demande un paiement mobile money ; l'en-tête Idempotency-Key est obligatoire et rend l'appel "
            + "rejouable. L'abonnement n'est activé qu'à la confirmation de l'opérateur")
    @SecurityRequirement(name = "jetonAcces")
    @PreAuthorize(PARENT)
    @PostMapping("/api/v1/enfants/{enfantId}/abonnement/paiements")
    @ResponseStatus(HttpStatus.ACCEPTED)
    PaiementVue payer(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID enfantId,
            @RequestHeader(name = "Idempotency-Key", required = false) String cleIdempotence,
            @Valid @RequestBody DemandePaiement demande) {
        return souscriptions.payer(id(jeton), enfantId,
                new SaisiePaiement(demande.offre(), demande.moyen(), demande.numero(), demande.renouvellementAuto()), cleIdempotence);
    }

    @Operation(summary = "État d'un paiement, à interroger jusqu'à la réponse de l'opérateur")
    @SecurityRequirement(name = "jetonAcces")
    @PreAuthorize(PARENT)
    @GetMapping("/api/v1/paiements/{paiementId}")
    ResponseEntity<PaiementVue> paiement(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID paiementId) {
        return sansCache(souscriptions.paiement(id(jeton), paiementId));
    }

    @Operation(summary = "Active ou désactive le renouvellement automatique")
    @SecurityRequirement(name = "jetonAcces")
    @PreAuthorize(PARENT)
    @PutMapping("/api/v1/enfants/{enfantId}/abonnement/renouvellement")
    ResponseEntity<AbonnementVue> choisirRenouvellement(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID enfantId,
            @Valid @RequestBody DemandeRenouvellement demande) {
        return sansCache(souscriptions.choisirRenouvellement(id(jeton), enfantId, demande.automatique()));
    }

    @Operation(summary = "Mes reçus, les plus récents d'abord")
    @SecurityRequirement(name = "jetonAcces")
    @PreAuthorize(PARENT)
    @GetMapping("/api/v1/recus")
    ResponseEntity<List<RecuVue>> recus(@AuthenticationPrincipal Jwt jeton) {
        return sansCache(souscriptions.recus(id(jeton)));
    }

    @Operation(summary = "Un reçu en PDF")
    @SecurityRequirement(name = "jetonAcces")
    @PreAuthorize(PARENT)
    @GetMapping(value = "/api/v1/recus/{numero}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    ResponseEntity<byte[]> recuPdf(@AuthenticationPrincipal Jwt jeton, @PathVariable String numero) {
        RecuPdf recu = souscriptions.recuPdf(id(jeton), numero);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + recu.numero() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF).body(recu.contenu());
    }

    /**
     * Appelé par l'agrégateur, qui n'a pas de session : la signature du corps tient lieu d'authentification.
     * Le corps est lu octet pour octet, car c'est sur lui que porte la signature.
     */
    @Operation(summary = "Notification signée de l'agrégateur de paiement")
    @PostMapping("/api/v1/public/paiements/notification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void notification(@RequestHeader(name = "X-FG-Signature", required = false) String signature, @RequestBody byte[] corps) {
        reception.recevoir(signature, corps);
    }

    private static UUID id(Jwt jeton) {
        return UUID.fromString(jeton.getSubject());
    }

    private static <T> ResponseEntity<T> sansCache(T corps) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(corps);
    }
}
