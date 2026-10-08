package bf.fasoguardian.identite.application;

import java.time.Clock;
import java.time.Instant;

import bf.fasoguardian.identite.domaine.CodeEmis;
import bf.fasoguardian.identite.domaine.CodeUsageUnique;
import bf.fasoguardian.identite.infrastructure.DepotCodes;
import bf.fasoguardian.notifications.ServiceSms;
import bf.fasoguardian.plateforme.erreurs.CodeErreur;
import bf.fasoguardian.plateforme.erreurs.ErreurMetier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Émission et vérification des codes à usage unique envoyés par SMS. Un code est lié à sa finalité et à
 * sa cible : émis pour une action, il n'en valide aucune autre. Le compteur d'essais est conservé même si
 * l'opération appelante échoue ensuite (transaction propre à la vérification).
 */
@Service
public class CodesSms {

    private final DepotCodes codes;
    private final ServiceSms sms;
    private final Clock horloge;

    CodesSms(DepotCodes codes, ServiceSms sms, Clock horloge) {
        this.codes = codes;
        this.sms = sms;
        this.horloge = horloge;
    }

    /**
     * @param finalite     code court de la finalité (32 caractères au plus)
     * @param cibleHash    empreinte de ce que le code protège (numéro de téléphone)
     * @param numeroE164   destinataire du SMS
     * @param modeleTexte  texte du SMS, où {@code %s} reçoit le code
     */
    @Transactional
    public void emettre(String finalite, String cibleHash, String numeroE164, String modeleTexte) {
        Instant maintenant = horloge.instant();
        codes.findFirstByFinaliteAndCibleHashOrderByEmisLeDesc(finalite, cibleHash).ifPresent(precedent -> {
            if (!precedent.code().renvoiPossible(maintenant)) {
                throw new ErreurMetier(CodeErreur.TROP_DE_REQUETES,
                        "Un code vient d'être envoyé. Patientez une minute avant d'en demander un autre.");
            }
        });
        CodeUsageUnique.Emission emission = CodeUsageUnique.emettre(CodeEmis.contexte(finalite, cibleHash), maintenant);
        codes.save(new CodeEmis(finalite, cibleHash, emission.code()));
        sms.envoyer(numeroE164, modeleTexte.formatted(emission.codeEnClair()));
    }

    /** Consomme le code ou lève l'erreur correspondante (incorrect, expiré, épuisé). */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ErreurMetier.class)
    public void verifier(String finalite, String cibleHash, String saisie) {
        CodeEmis emis = codes.findFirstByFinaliteAndCibleHashOrderByEmisLeDesc(finalite, cibleHash)
                .orElseThrow(() -> new ErreurMetier(CodeErreur.CODE_INCORRECT, "Code incorrect."));
        switch (emis.verifier(saisie, horloge.instant())) {
            case VALIDE -> {
            }
            case INCORRECT -> throw new ErreurMetier(CodeErreur.CODE_INCORRECT,
                    "Code incorrect. Essais restants : " + emis.code().essaisRestants() + ".");
            case EXPIRE -> throw new ErreurMetier(CodeErreur.CODE_EXPIRE, "Ce code a expiré. Demandez-en un nouveau.");
            default -> throw new ErreurMetier(CodeErreur.CODE_EPUISE, "Trop d'essais. Demandez un nouveau code.");
        }
    }
}
