package bf.fasoguardian.identite.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;

import bf.fasoguardian.identite.application.Ports.EmetteurJetons;
import bf.fasoguardian.identite.application.Ports.HacheurMotDePasse;
import bf.fasoguardian.identite.application.Sessions.Session;
import bf.fasoguardian.identite.domaine.CodeEmis;
import bf.fasoguardian.identite.domaine.CodeUsageUnique;
import bf.fasoguardian.identite.domaine.Consentement;
import bf.fasoguardian.identite.domaine.NumeroTelephone;
import bf.fasoguardian.identite.domaine.NumeroTelephone.NumeroInvalideException;
import bf.fasoguardian.identite.domaine.PolitiqueMotDePasse;
import bf.fasoguardian.identite.domaine.Tuteur;
import bf.fasoguardian.identite.domaine.TypeConsentement;
import bf.fasoguardian.identite.infrastructure.DepotCodes;
import bf.fasoguardian.identite.infrastructure.DepotConsentements;
import bf.fasoguardian.identite.infrastructure.DepotUtilisateurs;
import bf.fasoguardian.notifications.ServiceSms;
import bf.fasoguardian.plateforme.chiffrement.CategorieDonnee;
import bf.fasoguardian.plateforme.chiffrement.ServiceChiffrement;
import bf.fasoguardian.plateforme.erreurs.CodeErreur;
import bf.fasoguardian.plateforme.erreurs.ErreurMetier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inscription d'un parent en trois temps (écran 10 du design, US-PAR-001) : numéro, code reçu par SMS,
 * puis mot de passe et consentements. Le compte créé reste « en instruction » jusqu'à la validation KYC.
 */
@Service
public class InscriptionParent {

    static final String FINALITE = "INSCRIPTION";
    static final String VERSION_TEXTES = "2026-10";

    private final DepotUtilisateurs utilisateurs;
    private final DepotCodes codes;
    private final DepotConsentements consentements;
    private final ServiceChiffrement chiffrement;
    private final HacheurMotDePasse hacheur;
    private final EmetteurJetons emetteur;
    private final ServiceSms sms;
    private final Sessions sessions;
    private final Clock horloge;

    InscriptionParent(DepotUtilisateurs utilisateurs, DepotCodes codes, DepotConsentements consentements,
            ServiceChiffrement chiffrement, HacheurMotDePasse hacheur, EmetteurJetons emetteur, ServiceSms sms,
            Sessions sessions, Clock horloge) {
        this.utilisateurs = utilisateurs;
        this.codes = codes;
        this.consentements = consentements;
        this.chiffrement = chiffrement;
        this.hacheur = hacheur;
        this.emetteur = emetteur;
        this.sms = sms;
        this.sessions = sessions;
        this.horloge = horloge;
    }

    /**
     * Envoie un code au numéro indiqué. La réponse ne révèle pas si un compte existe déjà : dans ce cas,
     * le titulaire du numéro reçoit un SMS l'invitant à se connecter.
     */
    @Transactional
    public void demarrer(String saisieTelephone) {
        NumeroTelephone numero = numero(saisieTelephone);
        String cible = chiffrement.empreinte(CategorieDonnee.TELEPHONE, numero.e164());
        Instant maintenant = horloge.instant();

        codes.findFirstByFinaliteAndCibleHashOrderByEmisLeDesc(FINALITE, cible).ifPresent(precedent -> {
            if (!precedent.code().renvoiPossible(maintenant)) {
                throw new ErreurMetier(CodeErreur.TROP_DE_REQUETES,
                        "Un code vient d'être envoyé. Patientez une minute avant d'en demander un autre.");
            }
        });

        if (utilisateurs.findByTelephoneHash(cible).isPresent()) {
            codes.save(new CodeEmis(FINALITE, cible, CodeUsageUnique.emettre("compte-existant", maintenant).code()));
            sms.envoyer(numero.e164(), "FasoGuardian : un compte existe déjà avec ce numéro. Connectez-vous, "
                    + "ou utilisez « Mot de passe oublié ».");
            return;
        }

        CodeUsageUnique.Emission emission = CodeUsageUnique.emettre(CodeEmis.contexte(FINALITE, cible), maintenant);
        codes.save(new CodeEmis(FINALITE, cible, emission.code()));
        sms.envoyer(numero.e164(), "FasoGuardian : votre code est " + emission.codeEnClair()
                + ". Valable 5 minutes. Ne le communiquez à personne.");
    }

    /** Vérifie le code et renvoie la preuve à présenter à la dernière étape. L'échec conserve le compteur d'essais. */
    @Transactional(noRollbackFor = ErreurMetier.class)
    public String verifierCode(String saisieTelephone, String code) {
        NumeroTelephone numero = numero(saisieTelephone);
        String cible = chiffrement.empreinte(CategorieDonnee.TELEPHONE, numero.e164());
        Instant maintenant = horloge.instant();
        CodeEmis emis = codes.findFirstByFinaliteAndCibleHashOrderByEmisLeDesc(FINALITE, cible)
                .orElseThrow(() -> new ErreurMetier(CodeErreur.CODE_INCORRECT, "Code incorrect."));

        switch (emis.verifier(code, maintenant)) {
            case VALIDE -> {
                return emetteur.preuveTelephone(numero.e164(), maintenant);
            }
            case INCORRECT -> throw new ErreurMetier(CodeErreur.CODE_INCORRECT,
                    "Code incorrect. Essais restants : " + emis.code().essaisRestants() + ".");
            case EXPIRE -> throw new ErreurMetier(CodeErreur.CODE_EXPIRE, "Ce code a expiré. Demandez-en un nouveau.");
            default -> throw new ErreurMetier(CodeErreur.CODE_EPUISE, "Trop d'essais. Demandez un nouveau code.");
        }
    }

    @Transactional
    public Session terminer(String preuveTelephone, String motDePasse, Set<TypeConsentement> accordes) {
        NumeroTelephone numero = emetteur.lirePreuveTelephone(preuveTelephone)
                .map(NumeroTelephone::new)
                .orElseThrow(() -> new ErreurMetier(CodeErreur.SESSION_EXPIREE,
                        "La vérification du numéro a expiré. Recommencez l'inscription."));
        Set<TypeConsentement> choix = accordes == null ? Set.of() : accordes;
        for (TypeConsentement type : TypeConsentement.values()) {
            if (type.obligatoire() && !choix.contains(type)) {
                throw new ErreurMetier(CodeErreur.CONSENTEMENT_REQUIS,
                        "Les conditions générales et le traitement des données de l'enfant doivent être acceptés.");
            }
        }
        PolitiqueMotDePasse.verifier(motDePasse, numero).ifPresent(refus -> {
            throw new ErreurMetier(CodeErreur.MOT_DE_PASSE_REFUSE, switch (refus) {
                case TROP_COURT -> "Le mot de passe doit compter au moins 10 caractères.";
                case TROP_LONG -> "Le mot de passe ne peut pas dépasser 128 caractères.";
                case SANS_CHIFFRE -> "Le mot de passe doit contenir au moins un chiffre.";
                case CONTIENT_LE_TELEPHONE -> "Le mot de passe ne doit pas contenir votre numéro de téléphone.";
            });
        });

        String cible = chiffrement.empreinte(CategorieDonnee.TELEPHONE, numero.e164());
        if (utilisateurs.findByTelephoneHash(cible).isPresent()) {
            throw new ErreurMetier(CodeErreur.CONFLIT, "Un compte existe déjà avec ce numéro.");
        }

        Instant maintenant = horloge.instant();
        Tuteur tuteur = new Tuteur(chiffrement.chiffrerTexte(CategorieDonnee.TELEPHONE, numero.e164()), cible, maintenant);
        tuteur.definirMotDePasse(hacheur.hacher(motDePasse), maintenant);
        utilisateurs.save(tuteur);
        for (TypeConsentement type : TypeConsentement.values()) {
            consentements.save(new Consentement(tuteur.id(), type, choix.contains(type), VERSION_TEXTES, maintenant));
        }
        return sessions.ouvrir(tuteur, maintenant);
    }

    private static NumeroTelephone numero(String saisie) {
        try {
            return NumeroTelephone.depuisSaisie(saisie);
        } catch (NumeroInvalideException erreur) {
            throw new ErreurMetier(CodeErreur.TELEPHONE_INVALIDE, "Saisissez un numéro mobile à 8 chiffres.");
        }
    }
}
