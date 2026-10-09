package bf.fasoguardian.famille.application;

import java.util.UUID;

import bf.fasoguardian.famille.DossiersEnfants;
import bf.fasoguardian.famille.application.Familles.ProfilEnfant;
import bf.fasoguardian.famille.domaine.Enfant;
import bf.fasoguardian.famille.infrastructure.DepotEnfants;
import bf.fasoguardian.plateforme.chiffrement.CategorieDonnee;
import bf.fasoguardian.plateforme.chiffrement.ServiceChiffrement;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Rassemble ce qui aide à reconnaître et à prendre en charge un enfant signalé disparu. */
@Service
class IdentificationEnfants implements DossiersEnfants {

    private final DepotEnfants enfants;
    private final DossierMedical medical;
    private final ServiceChiffrement chiffrement;
    private final JsonMapper json;

    IdentificationEnfants(DepotEnfants enfants, DossierMedical medical, ServiceChiffrement chiffrement, JsonMapper json) {
        this.enfants = enfants;
        this.medical = medical;
        this.chiffrement = chiffrement;
        this.json = json;
    }

    @Override
    @Transactional(readOnly = true)
    public Identification pourSignalement(UUID enfantId) {
        Enfant enfant = enfants.findById(enfantId).orElseThrow();
        ProfilEnfant profil = enfant.profilChiffre() == null ? new ProfilEnfant(null, null, null, null)
                : json.readValue(chiffrement.dechiffrer(CategorieDonnee.PROFIL_ENFANT, enfant.profilChiffre()), ProfilEnfant.class);
        return new Identification(enfant.prenom(), enfant.nom(), enfant.dateNaissance(), profil.tailleCm(),
                profil.signesDistinctifs(), profil.ecole(), profil.quartier(),
                medical.informationsCritiques(enfantId).stream().map(i -> i.type() + " : " + i.libelle()).toList());
    }
}
