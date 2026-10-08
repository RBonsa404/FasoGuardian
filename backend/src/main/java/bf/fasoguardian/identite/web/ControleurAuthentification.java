package bf.fasoguardian.identite.web;

import java.time.Duration;
import java.util.Set;

import bf.fasoguardian.identite.application.InscriptionParent;
import bf.fasoguardian.identite.application.Sessions;
import bf.fasoguardian.identite.application.Sessions.Session;
import bf.fasoguardian.identite.domaine.TypeConsentement;
import bf.fasoguardian.plateforme.erreurs.CodeErreur;
import bf.fasoguardian.plateforme.erreurs.ErreurMetier;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inscription et sessions des parents. Le jeton de rafraîchissement ne voyage que dans un cookie
 * HttpOnly, Secure, SameSite=Strict limité à ce chemin ; les points qui le lisent exigent en plus
 * l'en-tête {@code X-FG-Requete}, qu'un formulaire d'un autre site ne peut pas poser.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentification")
class ControleurAuthentification {

    static final String COOKIE = "fg_rafraichissement";
    static final String CHEMIN_COOKIE = "/api/v1/auth";
    static final String ENTETE_ANTI_CSRF = "X-FG-Requete";

    private final InscriptionParent inscription;
    private final Sessions sessions;

    ControleurAuthentification(InscriptionParent inscription, Sessions sessions) {
        this.inscription = inscription;
        this.sessions = sessions;
    }

    record DemandeNumero(@NotBlank @Size(max = 32) String telephone) {
    }

    record DemandeCode(@NotBlank @Size(max = 32) String telephone, @NotBlank @Size(max = 12) String code) {
    }

    record PreuveTelephone(String preuve) {
    }

    record DemandeFinInscription(@NotBlank @Size(max = 2048) String preuve, @NotBlank @Size(max = 256) String motDePasse,
            Set<TypeConsentement> consentements) {
    }

    record DemandeConnexion(@NotBlank @Size(max = 32) String telephone, @NotBlank @Size(max = 256) String motDePasse) {
    }

    record JetonAccesDto(String jetonAcces, long expireDansSecondes) {
    }

    @Operation(summary = "Inscription, étape 1 : envoie un code par SMS au numéro indiqué")
    @PostMapping("/inscription/numero")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void numero(@Valid @RequestBody DemandeNumero demande) {
        inscription.demarrer(demande.telephone());
    }

    @Operation(summary = "Inscription, étape 2 : vérifie le code reçu par SMS")
    @PostMapping("/inscription/code")
    PreuveTelephone code(@Valid @RequestBody DemandeCode demande) {
        return new PreuveTelephone(inscription.verifierCode(demande.telephone(), demande.code()));
    }

    @Operation(summary = "Inscription, étape 3 : mot de passe et consentements ; crée le compte et ouvre la session")
    @PostMapping("/inscription/terminer")
    ResponseEntity<JetonAccesDto> terminer(@Valid @RequestBody DemandeFinInscription demande) {
        return reponse(HttpStatus.CREATED,
                inscription.terminer(demande.preuve(), demande.motDePasse(), demande.consentements()));
    }

    @Operation(summary = "Connexion par numéro de téléphone et mot de passe")
    @PostMapping("/connexion")
    ResponseEntity<JetonAccesDto> connexion(@Valid @RequestBody DemandeConnexion demande) {
        return reponse(HttpStatus.OK, sessions.connecter(demande.telephone(), demande.motDePasse()));
    }

    @Operation(summary = "Échange le cookie de rafraîchissement contre un nouveau jeton d'accès (rotation)")
    @PostMapping("/rafraichir")
    ResponseEntity<JetonAccesDto> rafraichir(@CookieValue(name = COOKIE, required = false) String jeton,
            @RequestHeader(name = ENTETE_ANTI_CSRF, required = false) String entete) {
        exigerEntete(entete);
        return reponse(HttpStatus.OK, sessions.rafraichir(jeton));
    }

    @Operation(summary = "Ferme la session et révoque ses jetons de rafraîchissement")
    @PostMapping("/deconnexion")
    ResponseEntity<Void> deconnexion(@CookieValue(name = COOKIE, required = false) String jeton,
            @RequestHeader(name = ENTETE_ANTI_CSRF, required = false) String entete) {
        exigerEntete(entete);
        sessions.fermer(jeton);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
                .build();
    }

    private static void exigerEntete(String entete) {
        if (entete == null || entete.isBlank()) {
            throw new ErreurMetier(CodeErreur.ACCES_REFUSE, "En-tête " + ENTETE_ANTI_CSRF + " requis.");
        }
    }

    static ResponseEntity<JetonAccesDto> reponse(HttpStatus statut, Session session) {
        Duration duree = Duration.between(java.time.Instant.now(), session.rafraichissementExpireLe());
        return ResponseEntity.status(statut)
                .header(HttpHeaders.SET_COOKIE, cookie(session.jetonRafraichissement(), duree).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new JetonAccesDto(session.jetonAcces(), session.expireDansSecondes()));
    }

    private static ResponseCookie cookie(String valeur, Duration duree) {
        return ResponseCookie.from(COOKIE, valeur)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(CHEMIN_COOKIE)
                .maxAge(duree.isNegative() ? Duration.ZERO : duree)
                .build();
    }
}
