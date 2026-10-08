package bf.fasoguardian.identite.application;

import java.util.Set;
import java.util.UUID;

import bf.fasoguardian.identite.domaine.NumeroTelephone;
import bf.fasoguardian.identite.domaine.StatutCompte;
import bf.fasoguardian.identite.domaine.Utilisateur;
import bf.fasoguardian.identite.infrastructure.DepotUtilisateurs;
import bf.fasoguardian.plateforme.chiffrement.CategorieDonnee;
import bf.fasoguardian.plateforme.chiffrement.ServiceChiffrement;
import bf.fasoguardian.plateforme.erreurs.CodeErreur;
import bf.fasoguardian.plateforme.erreurs.ErreurMetier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lecture du compte de l'utilisateur authentifié. Le téléphone n'est restitué que masqué. */
@Service
public class ConsultationCompte {

    private final DepotUtilisateurs utilisateurs;
    private final ServiceChiffrement chiffrement;

    ConsultationCompte(DepotUtilisateurs utilisateurs, ServiceChiffrement chiffrement) {
        this.utilisateurs = utilisateurs;
        this.chiffrement = chiffrement;
    }

    public record Compte(UUID id, StatutCompte statut, String telephoneMasque, Set<String> roles) {
    }

    @Transactional(readOnly = true)
    public Compte de(UUID utilisateurId) {
        Utilisateur utilisateur = utilisateurs.findById(utilisateurId)
                .filter(Utilisateur::peutSAuthentifier)
                .orElseThrow(() -> new ErreurMetier(CodeErreur.NON_AUTHENTIFIE, "Compte introuvable ou clos."));
        String masque = utilisateur.telephoneChiffre() == null ? null
                : new NumeroTelephone(chiffrement.dechiffrerTexte(CategorieDonnee.TELEPHONE, utilisateur.telephoneChiffre()))
                        .masque();
        return new Compte(utilisateur.id(), utilisateur.statut(), masque, utilisateur.roles());
    }
}
