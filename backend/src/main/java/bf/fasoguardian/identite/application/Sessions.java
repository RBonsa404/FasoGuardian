package bf.fasoguardian.identite.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import bf.fasoguardian.identite.application.Ports.EmetteurJetons;
import bf.fasoguardian.identite.application.Ports.HacheurMotDePasse;
import bf.fasoguardian.identite.application.Ports.JetonAcces;
import bf.fasoguardian.identite.domaine.JetonRafraichissement;
import bf.fasoguardian.identite.domaine.NumeroTelephone;
import bf.fasoguardian.identite.domaine.NumeroTelephone.NumeroInvalideException;
import bf.fasoguardian.identite.domaine.Utilisateur;
import bf.fasoguardian.identite.infrastructure.DepotJetonsRafraichissement;
import bf.fasoguardian.identite.infrastructure.DepotUtilisateurs;
import bf.fasoguardian.plateforme.chiffrement.CategorieDonnee;
import bf.fasoguardian.plateforme.chiffrement.ServiceChiffrement;
import bf.fasoguardian.plateforme.erreurs.CodeErreur;
import bf.fasoguardian.plateforme.erreurs.ErreurMetier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ouverture, rafraîchissement et fermeture des sessions (ADR-07 de FG-DOC-06 ; US-PAR-019). */
@Service
public class Sessions {

    private final DepotUtilisateurs utilisateurs;
    private final DepotJetonsRafraichissement jetons;
    private final HacheurMotDePasse hacheur;
    private final EmetteurJetons emetteur;
    private final ServiceChiffrement chiffrement;
    private final Clock horloge;
    private final SecureRandom alea = new SecureRandom();

    Sessions(DepotUtilisateurs utilisateurs, DepotJetonsRafraichissement jetons, HacheurMotDePasse hacheur,
            EmetteurJetons emetteur, ServiceChiffrement chiffrement, Clock horloge) {
        this.utilisateurs = utilisateurs;
        this.jetons = jetons;
        this.hacheur = hacheur;
        this.emetteur = emetteur;
        this.chiffrement = chiffrement;
        this.horloge = horloge;
    }

    public record Session(String jetonAcces, long expireDansSecondes, String jetonRafraichissement,
            Instant rafraichissementExpireLe) {
    }

    /**
     * Connexion par téléphone et mot de passe. La réponse est identique que le compte existe ou non ;
     * les échecs sont comptés et le compte verrouillé quinze minutes après cinq échecs.
     * L'échec n'annule pas la transaction : le compteur doit être conservé.
     */
    @Transactional(noRollbackFor = ErreurMetier.class)
    public Session connecter(String saisieTelephone, String motDePasse) {
        Instant maintenant = horloge.instant();
        Utilisateur utilisateur = trouver(saisieTelephone);
        if (utilisateur == null || !utilisateur.peutSAuthentifier()) {
            // Travail de hachage équivalent : le délai de réponse ne révèle pas l'existence du compte.
            hacheur.hacher(motDePasse == null ? "" : motDePasse);
            throw new ErreurMetier(CodeErreur.IDENTIFIANTS_INVALIDES, "Numéro ou mot de passe incorrect.");
        }
        if (utilisateur.estVerrouille(maintenant)) {
            throw new ErreurMetier(CodeErreur.COMPTE_VERROUILLE,
                    "Trop de tentatives. Réessayez dans quelques minutes ou réinitialisez votre mot de passe.");
        }
        if (motDePasse == null || !hacheur.correspond(motDePasse, utilisateur.mdpArgon2id())) {
            utilisateur.enregistrerEchecDeConnexion(maintenant);
            throw new ErreurMetier(CodeErreur.IDENTIFIANTS_INVALIDES, "Numéro ou mot de passe incorrect.");
        }
        utilisateur.enregistrerConnexionReussie();
        return ouvrir(utilisateur, maintenant);
    }

    /** Ouvre une session pour un compte tout juste authentifié par un autre moyen (fin d'inscription). */
    @Transactional
    public Session ouvrir(Utilisateur utilisateur, Instant maintenant) {
        String opaque = nouveauJetonOpaque();
        JetonRafraichissement jeton = jetons.save(
                JetonRafraichissement.nouvelleFamille(utilisateur.id(), empreinte(opaque), maintenant));
        return session(utilisateur, opaque, jeton, maintenant);
    }

    /**
     * Échange un jeton de rafraîchissement contre une nouvelle session. Un jeton inconnu, expiré, ou déjà
     * échangé impose une réauthentification complète ; la réutilisation révoque toute la famille.
     */
    @Transactional(noRollbackFor = ErreurMetier.class)
    public Session rafraichir(String opaque) {
        Instant maintenant = horloge.instant();
        JetonRafraichissement courant = opaque == null ? null : jetons.findByEmpreinte(empreinte(opaque)).orElse(null);
        if (courant == null) {
            throw sessionExpiree();
        }
        if (courant.dejaUtiliseOuRevoque()) {
            jetons.findByFamille(courant.famille()).forEach(jeton -> jeton.revoquer(maintenant));
            throw sessionExpiree();
        }
        Utilisateur utilisateur = utilisateurs.findById(courant.utilisateurId()).orElse(null);
        if (courant.expire(maintenant) || utilisateur == null || !utilisateur.peutSAuthentifier()) {
            courant.revoquer(maintenant);
            throw sessionExpiree();
        }
        String suivant = nouveauJetonOpaque();
        JetonRafraichissement successeur = jetons.save(courant.successeur(empreinte(suivant), maintenant));
        return session(utilisateur, suivant, successeur, maintenant);
    }

    @Transactional
    public void fermer(String opaque) {
        if (opaque == null) {
            return;
        }
        Instant maintenant = horloge.instant();
        jetons.findByEmpreinte(empreinte(opaque)).ifPresent(courant ->
                jetons.findByFamille(courant.famille()).forEach(jeton -> jeton.revoquer(maintenant)));
    }

    private Session session(Utilisateur utilisateur, String opaque, JetonRafraichissement jeton, Instant maintenant) {
        JetonAcces acces = emetteur.acces(utilisateur.id(), utilisateur.roles(), maintenant);
        return new Session(acces.valeur(), acces.expireDansSecondes(), opaque, jeton.expireLe());
    }

    private Utilisateur trouver(String saisieTelephone) {
        try {
            NumeroTelephone numero = NumeroTelephone.depuisSaisie(saisieTelephone);
            return utilisateurs.findByTelephoneHash(chiffrement.empreinte(CategorieDonnee.TELEPHONE, numero.e164()))
                    .orElse(null);
        } catch (NumeroInvalideException erreur) {
            return null;
        }
    }

    private String nouveauJetonOpaque() {
        byte[] octets = new byte[32];
        alea.nextBytes(octets);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(octets);
    }

    private static String empreinte(String opaque) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(opaque.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException erreur) {
            throw new IllegalStateException(erreur);
        }
    }

    private static ErreurMetier sessionExpiree() {
        return new ErreurMetier(CodeErreur.SESSION_EXPIREE, "Votre session a expiré. Reconnectez-vous.");
    }
}
