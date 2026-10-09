package bf.fasoguardian.dispositifs.web;

import java.util.List;
import java.util.UUID;

import bf.fasoguardian.dispositifs.application.Parc;
import bf.fasoguardian.dispositifs.application.Parc.BraceletParc;
import bf.fasoguardian.dispositifs.application.Parc.CarteActivation;
import bf.fasoguardian.dispositifs.application.Parc.CertificatRevoque;
import bf.fasoguardian.dispositifs.application.Parc.Enregistrement;
import bf.fasoguardian.dispositifs.application.Parc.FicheBracelet;
import bf.fasoguardian.dispositifs.domaine.StatutBracelet;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Parc de bracelets de la console (US-SAV-002), réservé au service après-vente. */
@RestController
@RequestMapping("/api/v1/console/parc")
@Tag(name = "Parc de bracelets")
@SecurityRequirement(name = "jetonAcces")
@PreAuthorize("hasRole('SAV')")
class ControleurParc {

    private final Parc parc;

    ControleurParc(Parc parc) {
        this.parc = parc;
    }

    record DemandeEnregistrement(@NotBlank @Size(max = 16) String numeroSerie, @NotBlank @Size(max = 20) String imei,
            @NotBlank @Size(max = 16) String revisionMaterielle, @NotBlank @Size(max = 16) String versionLogiciel,
            @NotBlank @Size(max = 100) String empreinteCertificat, @Size(max = 400) String clePublique) {
    }

    record DemandeRemiseEnStock(@Size(max = 100) String empreinteCertificat) {
    }

    @Operation(summary = "Enregistre un bracelet préparé à l'atelier ; renvoie une seule fois le code d'appairage et le jeton du QR")
    @PostMapping
    ResponseEntity<CarteActivation> enregistrer(@AuthenticationPrincipal Jwt jeton,
            @Valid @RequestBody DemandeEnregistrement demande) {
        return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(parc.enregistrer(id(jeton), new Enregistrement(demande.numeroSerie(), demande.imei(),
                        demande.revisionMaterielle(), demande.versionLogiciel(), demande.empreinteCertificat(), demande.clePublique())));
    }

    @Operation(summary = "Vue consolidée du parc, filtrable par statut")
    @GetMapping
    ResponseEntity<List<BraceletParc>> parc(@RequestParam(required = false) StatutBracelet statut) {
        return sansCache(parc.parc(statut));
    }

    @Operation(summary = "Certificats révoqués, source de la liste de révocation du broker")
    @GetMapping("/certificats-revoques")
    ResponseEntity<List<CertificatRevoque>> certificatsRevoques() {
        return sansCache(parc.certificatsRevoques());
    }

    @Operation(summary = "Fiche d'un bracelet et historique de ses appairages (consultation journalisée)")
    @GetMapping("/{numeroSerie}")
    ResponseEntity<FicheBracelet> fiche(@AuthenticationPrincipal Jwt jeton, @PathVariable String numeroSerie) {
        return sansCache(parc.fiche(id(jeton), numeroSerie));
    }

    @Operation(summary = "Unité retournée : passe « En SAV » et n'est plus active pour l'enfant")
    @PostMapping("/{numeroSerie}/retour")
    ResponseEntity<BraceletParc> retourner(@AuthenticationPrincipal Jwt jeton, @PathVariable String numeroSerie) {
        return sansCache(parc.retourner(id(jeton), numeroSerie));
    }

    @Operation(summary = "Remet en stock une unité reconditionnée ; renvoie son nouveau code d'appairage")
    @PostMapping("/{numeroSerie}/remise-en-stock")
    ResponseEntity<CarteActivation> remettreEnStock(@AuthenticationPrincipal Jwt jeton, @PathVariable String numeroSerie,
            @Valid @RequestBody(required = false) DemandeRemiseEnStock demande) {
        return sansCache(parc.remettreEnStock(id(jeton), numeroSerie, demande == null ? null : demande.empreinteCertificat()));
    }

    @Operation(summary = "Retire définitivement une unité du parc ; son certificat est révoqué")
    @PostMapping("/{numeroSerie}/reforme")
    ResponseEntity<BraceletParc> reformer(@AuthenticationPrincipal Jwt jeton, @PathVariable String numeroSerie) {
        return sansCache(parc.reformer(id(jeton), numeroSerie));
    }

    private static UUID id(Jwt jeton) {
        return UUID.fromString(jeton.getSubject());
    }

    private static <T> ResponseEntity<T> sansCache(T corps) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(corps);
    }
}
