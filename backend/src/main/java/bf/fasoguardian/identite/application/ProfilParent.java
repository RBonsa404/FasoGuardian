package bf.fasoguardian.identite.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import bf.fasoguardian.audit.JournalAudit;
import bf.fasoguardian.audit.JournalAudit.Resultat;
import bf.fasoguardian.identite.SecondFacteur;
import bf.fasoguardian.identite.application.Ports.HacheurMotDePasse;
import bf.fasoguardian.identite.domaine.NumeroTelephone;
import bf.fasoguardian.identite.domaine.NumeroTelephone.NumeroInvalideException;
import bf.fasoguardian.identite.domaine.PolitiqueMotDePasse;
import bf.fasoguardian.identite.domaine.Tuteur;
import bf.fasoguardian.identite.domaine.Utilisateur;
import bf.fasoguardian.identite.infrastructure.DepotUtilisateurs;
import bf.fasoguardian.notifications.ServiceSms;
import bf.fasoguardian.plateforme.chiffrement.CategorieDonnee;
import bf.fasoguardian.plateforme.chiffrement.ServiceChiffrement;
import bf.fasoguardian.plateforme.erreurs.CodeErreur;
import bf.fasoguardian.plateforme.erreurs.ErreurMetier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Profil du parent (US-PAR-002) : récupération d'accès, changement de mot de passe et de téléphone,
 * second facteur des actions sensibles et clôture du compte. Les vérifications précèdent toujours les
 * écritures : un refus ne laisse aucune modification partielle.
 */
@Service
public class ProfilParent implements SecondFacteur {

    static final String FINALITE_REINITIALISATION = "REINITIALISATION";
    static final String FINALITE_NOUVEAU_TELEPHONE = "NOUVEAU_TELEPHONE";

    private final DepotUtilisateurs utilisateurs;
    private final CodesSms codes;
    private final Sessions sessions;
    private final HacheurMotDePasse hacheur;
    private final ServiceChiffrement chiffrement;
    private final ServiceSms sms;
    private final JournalAudit journal;
    private final Clock horloge;

    ProfilParent(DepotUtilisateurs utilisateurs, CodesSms codes, Sessions sessions, HacheurMotDePasse hacheur,
            ServiceChiffrement chiffrement, ServiceSms sms, JournalAudit journal, Clock horloge) {
        this.utilisateurs = utilisateurs;
        this.codes = codes;
        this.sessions = sessions;
        this.hacheur = hacheur;
        this.chiffrement = chiffrement;
        this.sms = sms;
        this.journal = journal;
        this.horloge = horloge;
    }

    // ------------------------------------------------------ mot de passe oublié

    /** Même réponse que le compte existe ou non ; le code ne part que vers un compte existant et non clos. */
    @Transactional
    public void demanderReinitialisation(String saisieTelephone) {
        NumeroTelephone numero = numero(saisieTelephone);
        String cible = empreinte(numero);
        if (utilisateurs.findByTelephoneHash(cible).filter(Utilisateur::peutSAuthentifier).isPresent()) {
            codes.emettre(FINALITE_REINITIALISATION, cible, numero.e164(),
                    "FasoGuardian : %s est votre code pour changer de mot de passe. Valable 5 minutes. Ne le communiquez à personne.");
        }
    }

    /** Remplace le mot de passe, lève le verrouillage et ferme toutes les sessions ouvertes. */
    @Transactional
    public void reinitialiser(String saisieTelephone, String code, String nouveauMotDePasse) {
        NumeroTelephone numero = numero(saisieTelephone);
        exigerMotDePasseAcceptable(nouveauMotDePasse, numero);
        String cible = empreinte(numero);
        codes.verifier(FINALITE_REINITIALISATION, cible, code);
        Utilisateur utilisateur = utilisateurs.findByTelephoneHash(cible).filter(Utilisateur::peutSAuthentifier)
                .orElseThrow(() -> new ErreurMetier(CodeErreur.CODE_INCORRECT, "Code incorrect."));
        Instant maintenant = horloge.instant();
        utilisateur.definirMotDePasse(hacheur.hacher(nouveauMotDePasse), maintenant);
        sessions.fermerToutes(utilisateur.id(), maintenant);
        journal.consigner(utilisateur.id(), Tuteur.ROLE, "MOT_DE_PASSE_REINITIALISE", "COMPTE", utilisateur.id().toString(),
                Resultat.SUCCES);
    }

    // ------------------------------------------------------------- coordonnées

    @Transactional
    public void changerMotDePasse(UUID tuteurId, String actuel, String nouveau) {
        Tuteur tuteur = tuteur(tuteurId);
        exigerMotDePasseActuel(tuteur, actuel);
        exigerMotDePasseAcceptable(nouveau, telephone(tuteur));
        tuteur.definirMotDePasse(hacheur.hacher(nouveau), horloge.instant());
        journal.consigner(tuteurId, Tuteur.ROLE, "MOT_DE_PASSE_CHANGE", "COMPTE", tuteurId.toString(), Resultat.SUCCES);
    }

    /** Envoie un code au nouveau numéro, pour prouver que le parent le détient. */
    @Transactional
    public void demanderChangementTelephone(UUID tuteurId, String saisieNouveau) {
        tuteur(tuteurId);
        NumeroTelephone nouveau = numero(saisieNouveau);
        exigerNumeroLibre(nouveau);
        codes.emettre(FINALITE_NOUVEAU_TELEPHONE, empreinte(nouveau), nouveau.e164(),
                "FasoGuardian : %s est votre code pour confirmer ce nouveau numéro. Valable 5 minutes.");
    }

    /** Le changement exige le mot de passe actuel et le code reçu sur le nouveau numéro ; il est horodaté. */
    @Transactional
    public void changerTelephone(UUID tuteurId, String saisieNouveau, String code, String motDePasseActuel) {
        Tuteur tuteur = tuteur(tuteurId);
        NumeroTelephone nouveau = numero(saisieNouveau);
        exigerMotDePasseActuel(tuteur, motDePasseActuel);
        exigerNumeroLibre(nouveau);
        NumeroTelephone ancien = telephone(tuteur);
        codes.verifier(FINALITE_NOUVEAU_TELEPHONE, empreinte(nouveau), code);
        tuteur.changerTelephone(chiffrement.chiffrerTexte(CategorieDonnee.TELEPHONE, nouveau.e164()), empreinte(nouveau),
                horloge.instant());
        journal.consigner(tuteurId, Tuteur.ROLE, "TELEPHONE_CHANGE", "COMPTE", tuteurId.toString(), Resultat.SUCCES);
        sms.envoyer(ancien.e164(), "FasoGuardian : le numéro de votre compte vient d'être modifié. "
                + "Si vous n'êtes pas à l'origine de ce changement, contactez le support.");
    }

    // ----------------------------------------------------------- second facteur

    @Override
    @Transactional
    public void envoyerCode(UUID tuteurId, ActionSensible action) {
        Tuteur tuteur = tuteur(tuteurId);
        NumeroTelephone numero = telephone(tuteur);
        codes.emettre(finalite(action), empreinte(numero), numero.e164(),
                "FasoGuardian : %s est votre code de confirmation. Valable 5 minutes. Ne le communiquez à personne.");
    }

    @Override
    @Transactional(readOnly = true)
    public void exiger(UUID tuteurId, ActionSensible action, String code) {
        if (code == null || code.isBlank()) {
            throw new ErreurMetier(CodeErreur.SECOND_FACTEUR_REQUIS,
                    "Cette action demande une confirmation par code SMS.");
        }
        codes.verifier(finalite(action), empreinte(telephone(tuteur(tuteurId))), code);
    }

    // ------------------------------------------------------------------ clôture

    /**
     * Clôt le compte après second facteur : il ne peut plus s'authentifier, ses sessions sont fermées et
     * un accusé est envoyé. La purge des données sous 30 jours est exécutée par le module audit.
     */
    @Transactional
    public void clore(UUID tuteurId, String codeSecondFacteur) {
        Tuteur tuteur = tuteur(tuteurId);
        NumeroTelephone numero = telephone(tuteur);
        exiger(tuteurId, ActionSensible.CLORE_COMPTE, codeSecondFacteur);
        Instant maintenant = horloge.instant();
        tuteur.clore(maintenant);
        sessions.fermerToutes(tuteurId, maintenant);
        journal.consigner(tuteurId, Tuteur.ROLE, "COMPTE_CLOS", "COMPTE", tuteurId.toString(), Resultat.SUCCES);
        sms.envoyer(numero.e164(), "FasoGuardian : votre demande de clôture est enregistrée. "
                + "Vos données seront supprimées sous 30 jours.");
    }

    // -------------------------------------------------------------------- aides

    private Tuteur tuteur(UUID tuteurId) {
        return utilisateurs.findById(tuteurId).filter(Utilisateur::peutSAuthentifier)
                .filter(Tuteur.class::isInstance).map(Tuteur.class::cast)
                .orElseThrow(() -> new ErreurMetier(CodeErreur.NON_AUTHENTIFIE, "Compte introuvable ou clos."));
    }

    private NumeroTelephone telephone(Tuteur tuteur) {
        return new NumeroTelephone(chiffrement.dechiffrerTexte(CategorieDonnee.TELEPHONE, tuteur.telephoneChiffre()));
    }

    private String empreinte(NumeroTelephone numero) {
        return chiffrement.empreinte(CategorieDonnee.TELEPHONE, numero.e164());
    }

    private void exigerNumeroLibre(NumeroTelephone numero) {
        if (utilisateurs.findByTelephoneHash(empreinte(numero)).isPresent()) {
            throw new ErreurMetier(CodeErreur.CONFLIT, "Ce numéro est déjà utilisé par un compte.");
        }
    }

    private void exigerMotDePasseActuel(Tuteur tuteur, String motDePasse) {
        if (motDePasse == null || !hacheur.correspond(motDePasse, tuteur.mdpArgon2id())) {
            throw new ErreurMetier(CodeErreur.IDENTIFIANTS_INVALIDES, "Mot de passe actuel incorrect.");
        }
    }

    private static void exigerMotDePasseAcceptable(String motDePasse, NumeroTelephone numero) {
        Optional<PolitiqueMotDePasse.Refus> refus = PolitiqueMotDePasse.verifier(motDePasse, numero);
        if (refus.isPresent()) {
            throw new ErreurMetier(CodeErreur.MOT_DE_PASSE_REFUSE, switch (refus.get()) {
                case TROP_COURT -> "Le mot de passe doit compter au moins 10 caractères.";
                case TROP_LONG -> "Le mot de passe ne peut pas dépasser 128 caractères.";
                case SANS_CHIFFRE -> "Le mot de passe doit contenir au moins un chiffre.";
                case CONTIENT_LE_TELEPHONE -> "Le mot de passe ne doit pas contenir votre numéro de téléphone.";
            });
        }
    }

    private static String finalite(ActionSensible action) {
        return "2F_" + action.name();
    }

    private static NumeroTelephone numero(String saisie) {
        try {
            return NumeroTelephone.depuisSaisie(saisie);
        } catch (NumeroInvalideException erreur) {
            throw new ErreurMetier(CodeErreur.TELEPHONE_INVALIDE, "Saisissez un numéro mobile à 8 chiffres.");
        }
    }
}
