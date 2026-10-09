package bf.fasoguardian.identite.application;

import java.util.Optional;
import java.util.UUID;

import bf.fasoguardian.identite.domaine.Utilisateur;
import bf.fasoguardian.identite.infrastructure.DepotUtilisateurs;
import bf.fasoguardian.notifications.Annuaire;
import bf.fasoguardian.plateforme.chiffrement.CategorieDonnee;
import bf.fasoguardian.plateforme.chiffrement.ServiceChiffrement;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Fournit au module notifications le numéro d'un compte, déchiffré à la demande. */
@Service
class AnnuaireDesComptes implements Annuaire {

    private final DepotUtilisateurs utilisateurs;
    private final ServiceChiffrement chiffrement;

    AnnuaireDesComptes(DepotUtilisateurs utilisateurs, ServiceChiffrement chiffrement) {
        this.utilisateurs = utilisateurs;
        this.chiffrement = chiffrement;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> telephoneE164(UUID utilisateurId) {
        return utilisateurs.findById(utilisateurId).filter(Utilisateur::peutSAuthentifier)
                .filter(utilisateur -> utilisateur.telephoneChiffre() != null)
                .map(utilisateur -> chiffrement.dechiffrerTexte(CategorieDonnee.TELEPHONE, utilisateur.telephoneChiffre()));
    }
}
