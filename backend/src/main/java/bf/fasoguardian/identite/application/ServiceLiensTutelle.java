package bf.fasoguardian.identite.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import bf.fasoguardian.identite.LiensTutelle;
import bf.fasoguardian.identite.application.InstructionKyc.EnfantDeclare;
import bf.fasoguardian.identite.application.InstructionKyc.IdentiteDeclaree;
import bf.fasoguardian.identite.domaine.DossierKyc;
import bf.fasoguardian.identite.domaine.DossierKyc.Statut;
import bf.fasoguardian.identite.domaine.LienTutelle;
import bf.fasoguardian.identite.infrastructure.DepotDossiersKyc;
import bf.fasoguardian.identite.infrastructure.DepotLiensTutelle;
import bf.fasoguardian.plateforme.chiffrement.CategorieDonnee;
import bf.fasoguardian.plateforme.chiffrement.ServiceChiffrement;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
class ServiceLiensTutelle implements LiensTutelle {

    private final DepotLiensTutelle liens;
    private final DepotDossiersKyc dossiers;
    private final ServiceChiffrement chiffrement;
    private final JsonMapper json;

    ServiceLiensTutelle(DepotLiensTutelle liens, DepotDossiersKyc dossiers, ServiceChiffrement chiffrement, JsonMapper json) {
        this.liens = liens;
        this.dossiers = dossiers;
        this.chiffrement = chiffrement;
        this.json = json;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean estTuteurActif(UUID tuteurId, UUID enfantId) {
        return liens.findByTuteurId(tuteurId).stream()
                .anyMatch(lien -> lien.enfantId().equals(enfantId) && lien.actif());
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> enfantsDe(UUID tuteurId) {
        return liens.findByTuteurId(tuteurId).stream().filter(LienTutelle::actif).map(LienTutelle::enfantId).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> tuteursActifsDe(UUID enfantId) {
        return liens.findByEnfantId(enfantId).stream().filter(LienTutelle::actif).map(LienTutelle::tuteurId).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> prenomDuTuteur(UUID tuteurId) {
        return dossiers.findFirstByDemandeurIdAndStatutOrderByDecideLeDesc(tuteurId, Statut.APPROUVE)
                .map(dossier -> json.readValue(chiffrement.dechiffrer(CategorieDonnee.PIECE_KYC, dossier.identiteChiffree()),
                        IdentiteDeclaree.class).prenoms())
                .map(prenoms -> prenoms.strip().split("\\s+")[0]).filter(prenom -> !prenom.isEmpty());
    }

    @Override
    @Transactional(readOnly = true)
    public EnfantVerifie enfantDuDossier(UUID dossierId) {
        DossierKyc dossier = dossiers.findById(dossierId).filter(d -> d.statut() == Statut.APPROUVE)
                .orElseThrow(() -> new IllegalStateException("Dossier KYC absent ou non approuvé : " + dossierId));
        EnfantDeclare enfant = json.readValue(
                chiffrement.dechiffrer(CategorieDonnee.PIECE_KYC, dossier.enfantChiffre()), EnfantDeclare.class);
        return new EnfantVerifie(dossier.enfantId(), enfant.prenom(), enfant.nom(), enfant.dateNaissance());
    }
}
