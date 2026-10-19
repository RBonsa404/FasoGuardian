package bf.fasoguardian.telemetrie.web;

import java.util.List;

import bf.fasoguardian.telemetrie.application.EtatsDuParc;
import bf.fasoguardian.telemetrie.application.EtatsDuParc.EtatTechnique;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** État technique des bracelets en service, complément de la vue du parc (US-SAV-002), réservé au service après-vente. */
@RestController
@Tag(name = "Parc de bracelets")
@SecurityRequirement(name = "jetonAcces")
@PreAuthorize("hasRole('SAV')")
class ControleurEtatsParc {

    private final EtatsDuParc etats;

    ControleurEtatsParc(EtatsDuParc etats) {
        this.etats = etats;
    }

    @Operation(summary = "Batterie, réseau et dernier contact des bracelets en service, depuis leur appairage en cours")
    @GetMapping("/api/v1/console/parc/etats")
    ResponseEntity<List<EtatTechnique>> enService() {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(etats.enService());
    }
}
