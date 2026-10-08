package bf.fasoguardian.identite.application;

import java.util.UUID;

import bf.fasoguardian.identite.MessagesTuteurs;
import bf.fasoguardian.identite.domaine.Utilisateur;
import bf.fasoguardian.identite.infrastructure.DepotUtilisateurs;
import bf.fasoguardian.notifications.ServiceSms;
import bf.fasoguardian.plateforme.chiffrement.CategorieDonnee;
import bf.fasoguardian.plateforme.chiffrement.ServiceChiffrement;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ServiceMessagesTuteurs implements MessagesTuteurs {

    private final DepotUtilisateurs utilisateurs;
    private final ServiceChiffrement chiffrement;
    private final ServiceSms sms;

    ServiceMessagesTuteurs(DepotUtilisateurs utilisateurs, ServiceChiffrement chiffrement, ServiceSms sms) {
        this.utilisateurs = utilisateurs;
        this.chiffrement = chiffrement;
        this.sms = sms;
    }

    /** Sans effet pour un compte clos ou sans téléphone. */
    @Override
    @Transactional(readOnly = true)
    public void envoyerSms(UUID tuteurId, String texte) {
        utilisateurs.findById(tuteurId).filter(Utilisateur::peutSAuthentifier)
                .filter(utilisateur -> utilisateur.telephoneChiffre() != null)
                .ifPresent(utilisateur -> sms.envoyer(
                        chiffrement.dechiffrerTexte(CategorieDonnee.TELEPHONE, utilisateur.telephoneChiffre()), texte));
    }
}
