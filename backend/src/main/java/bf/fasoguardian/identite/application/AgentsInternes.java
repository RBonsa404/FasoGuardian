package bf.fasoguardian.identite.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

import bf.fasoguardian.audit.JournalAudit;
import bf.fasoguardian.audit.JournalAudit.Resultat;
import bf.fasoguardian.identite.application.Ports.HacheurMotDePasse;
import bf.fasoguardian.identite.application.Sessions.Session;
import bf.fasoguardian.identite.domaine.AgentInterne;
import bf.fasoguardian.identite.domaine.PolitiqueMotDePasse;
import bf.fasoguardian.identite.domaine.RoleInterne;
import bf.fasoguardian.identite.domaine.Totp;
import bf.fasoguardian.identite.infrastructure.DepotUtilisateurs;
import bf.fasoguardian.plateforme.chiffrement.CategorieDonnee;
import bf.fasoguardian.plateforme.chiffrement.ServiceChiffrement;
import bf.fasoguardian.plateforme.erreurs.CodeErreur;
import bf.fasoguardian.plateforme.erreurs.ErreurMetier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Comptes des agents internes (US-ADM-001) : création par un administrateur, connexion par identifiant,
 * mot de passe et code TOTP. Le second facteur s'active à la première connexion, avant tout accès.
 */
@Service
public class AgentsInternes {

    private static final String EMETTEUR_TOTP = "FasoGuardian";

    private final DepotUtilisateurs utilisateurs;
    private final HacheurMotDePasse hacheur;
    private final ServiceChiffrement chiffrement;
    private final Sessions sessions;
    private final JournalAudit journal;
    private final Clock horloge;

    AgentsInternes(DepotUtilisateurs utilisateurs, HacheurMotDePasse hacheur, ServiceChiffrement chiffrement,
            Sessions sessions, JournalAudit journal, Clock horloge) {
        this.utilisateurs = utilisateurs;
        this.hacheur = hacheur;
        this.chiffrement = chiffrement;
        this.sessions = sessions;
        this.journal = journal;
        this.horloge = horloge;
    }

    public record Agent(UUID id, String identifiant, Set<RoleInterne> roles, boolean secondFacteurActif) {
    }

    /** L'échec n'annule pas la transaction : compteur d'échecs et secret d'enrôlement sont conservés. */
    @Transactional(noRollbackFor = ErreurMetier.class)
    public Session connecter(String identifiant, String motDePasse, String codeTotp) {
        Instant maintenant = horloge.instant();
        AgentInterne agent = identifiant == null ? null
                : utilisateurs.findAgentByIdentifiant(identifiant.trim().toLowerCase()).orElse(null);
        if (agent == null || !agent.peutSAuthentifier()) {
            hacheur.hacher(motDePasse == null ? "" : motDePasse);
            throw identifiantsInvalides();
        }
        if (agent.estVerrouille(maintenant)) {
            throw new ErreurMetier(CodeErreur.COMPTE_VERROUILLE, "Trop de tentatives. Réessayez dans quelques minutes.");
        }
        if (motDePasse == null || !hacheur.correspond(motDePasse, agent.mdpArgon2id())) {
            agent.enregistrerEchecDeConnexion(maintenant);
            throw identifiantsInvalides();
        }

        boolean sansCode = codeTotp == null || codeTotp.isBlank();
        if (!agent.totpActif() && sansCode) {
            if (agent.totpSecretChiffre() == null) {
                agent.enrolerTotp(chiffrement.chiffrer(CategorieDonnee.SECRET_MFA, Totp.nouveauSecret()));
            }
            String secret = Totp.enBase32(chiffrement.dechiffrer(CategorieDonnee.SECRET_MFA, agent.totpSecretChiffre()));
            throw new ErreurMetier(CodeErreur.TOTP_A_ACTIVER,
                    "Activez le second facteur dans votre application d'authentification, puis saisissez le code.",
                    Map.of("secretTotp", secret, "uriTotp", "otpauth://totp/" + EMETTEUR_TOTP + ":"
                            + agent.identifiant() + "?secret=" + secret + "&issuer=" + EMETTEUR_TOTP));
        }
        if (sansCode) {
            throw new ErreurMetier(CodeErreur.CODE_TOTP_REQUIS, "Saisissez le code de votre application d'authentification.");
        }
        if (agent.totpSecretChiffre() == null) {
            throw identifiantsInvalides();
        }
        OptionalLong pas = Totp.verifier(chiffrement.dechiffrer(CategorieDonnee.SECRET_MFA, agent.totpSecretChiffre()),
                codeTotp, maintenant, agent.totpDernierPas());
        if (pas.isEmpty()) {
            agent.enregistrerEchecDeConnexion(maintenant);
            throw identifiantsInvalides();
        }
        agent.totpAccepte(pas.getAsLong());
        agent.enregistrerConnexionReussie();
        journal.consigner(agent.id(), String.join(",", agent.roles().stream().sorted().toList()),
                "CONNEXION_AGENT", "AGENT", agent.id().toString(), Resultat.SUCCES);
        return sessions.ouvrir(agent, maintenant);
    }

    @Transactional
    public Agent creer(UUID administrateurId, String identifiant, String motDePasseProvisoire, Set<RoleInterne> roles) {
        String normalise = identifiant == null ? "" : identifiant.trim().toLowerCase();
        if (!normalise.matches("[a-z0-9][a-z0-9._-]{2,63}")) {
            throw new ErreurMetier(CodeErreur.REQUETE_INVALIDE,
                    "L'identifiant compte 3 à 64 caractères : lettres minuscules, chiffres, point, tiret.");
        }
        if (roles == null || roles.isEmpty()) {
            throw new ErreurMetier(CodeErreur.REQUETE_INVALIDE, "Attribuez au moins un rôle à l'agent.");
        }
        PolitiqueMotDePasse.verifierAgent(motDePasseProvisoire).ifPresent(refus -> {
            throw new ErreurMetier(CodeErreur.MOT_DE_PASSE_REFUSE,
                    "Le mot de passe d'un agent compte 12 à 128 caractères.");
        });
        if (utilisateurs.findAgentByIdentifiant(normalise).isPresent()) {
            throw new ErreurMetier(CodeErreur.CONFLIT, "Cet identifiant est déjà attribué.");
        }
        Instant maintenant = horloge.instant();
        AgentInterne agent = new AgentInterne(normalise, roles, maintenant);
        agent.definirMotDePasse(hacheur.hacher(motDePasseProvisoire), maintenant);
        utilisateurs.save(agent);
        journal.consigner(administrateurId, administrateurId == null ? "SYSTEME" : RoleInterne.ADMIN.name(),
                "AGENT_CREE", "AGENT", agent.id().toString(), Resultat.SUCCES);
        return vue(agent);
    }

    @Transactional(readOnly = true)
    public List<Agent> lister() {
        return utilisateurs.findAllAgents().stream().map(AgentsInternes::vue).toList();
    }

    @Transactional(readOnly = true)
    public boolean aucunAgent() {
        return utilisateurs.findAllAgents().isEmpty();
    }

    private static Agent vue(AgentInterne agent) {
        return new Agent(agent.id(), agent.identifiant(), agent.rolesInternes(), agent.totpActif());
    }

    private static ErreurMetier identifiantsInvalides() {
        return new ErreurMetier(CodeErreur.IDENTIFIANTS_INVALIDES, "Identifiant, mot de passe ou code incorrect.");
    }
}
