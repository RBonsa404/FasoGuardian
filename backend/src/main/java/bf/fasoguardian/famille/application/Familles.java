package bf.fasoguardian.famille.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import bf.fasoguardian.audit.JournalAudit;
import bf.fasoguardian.audit.JournalAudit.Resultat;
import bf.fasoguardian.famille.AccesEnfant;
import bf.fasoguardian.famille.domaine.Enfant;
import bf.fasoguardian.famille.domaine.RevisionEnfant;
import bf.fasoguardian.famille.infrastructure.DepotEnfants;
import bf.fasoguardian.famille.infrastructure.DepotRevisionsEnfant;
import bf.fasoguardian.identite.DossierKycApprouve;
import bf.fasoguardian.identite.LiensTutelle;
import bf.fasoguardian.identite.LiensTutelle.EnfantVerifie;
import bf.fasoguardian.plateforme.erreurs.CodeErreur;
import bf.fasoguardian.plateforme.erreurs.ErreurMetier;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fiches enfants (US-PAR-004). La fiche naît de l'approbation du dossier KYC, sous l'identifiant qui y
 * figure ; le parent n'y accède qu'au travers d'un lien de tutelle actif.
 */
@Service
public class Familles implements AccesEnfant {

    public record FicheEnfant(UUID id, String prenom, String nom, LocalDate dateNaissance, Instant modifieLe) {
    }

    public record Revision(String champ, Instant modifieLe) {
    }

    private final DepotEnfants enfants;
    private final DepotRevisionsEnfant revisions;
    private final LiensTutelle liens;
    private final JournalAudit journal;
    private final Clock horloge;

    Familles(DepotEnfants enfants, DepotRevisionsEnfant revisions, LiensTutelle liens, JournalAudit journal, Clock horloge) {
        this.enfants = enfants;
        this.revisions = revisions;
        this.liens = liens;
        this.journal = journal;
        this.horloge = horloge;
    }

    /** Crée la fiche de l'enfant dès que le lien est vérifié. Rejouable sans effet : la fiche n'est créée qu'une fois. */
    @ApplicationModuleListener
    void surDossierApprouve(DossierKycApprouve evenement) {
        if (enfants.existsById(evenement.enfantId())) {
            return;
        }
        EnfantVerifie verifie = liens.enfantDuDossier(evenement.dossierId());
        enfants.save(new Enfant(verifie.enfantId(), verifie.prenom(), verifie.nom(), verifie.dateNaissance(),
                horloge.instant()));
    }

    @Override
    @Transactional(readOnly = true)
    public void exigerTuteur(UUID tuteurId, UUID enfantId) {
        if (!liens.estTuteurActif(tuteurId, enfantId)) {
            refuser(tuteurId, enfantId);
        }
    }

    @Transactional(readOnly = true)
    public List<FicheEnfant> enfantsDe(UUID tuteurId) {
        return enfants.findAllById(liens.enfantsDe(tuteurId)).stream().map(Familles::vue).toList();
    }

    @Transactional(readOnly = true)
    public FicheEnfant fiche(UUID tuteurId, UUID enfantId) {
        exigerTuteur(tuteurId, enfantId);
        return vue(enfants.findById(enfantId).orElseThrow(Familles::introuvable));
    }

    /** Toute modification est horodatée et tracée dans l'historique (US-PAR-004). */
    @Transactional
    public FicheEnfant modifier(UUID tuteurId, UUID enfantId, String prenom, String nom) {
        exigerTuteur(tuteurId, enfantId);
        Enfant enfant = enfants.findById(enfantId).orElseThrow(Familles::introuvable);
        Instant maintenant = horloge.instant();
        for (String champ : enfant.mettreAJour(prenom, nom, maintenant)) {
            revisions.save(new RevisionEnfant(enfantId, tuteurId, champ, maintenant));
        }
        return vue(enfant);
    }

    @Transactional(readOnly = true)
    public List<Revision> historique(UUID tuteurId, UUID enfantId) {
        exigerTuteur(tuteurId, enfantId);
        return revisions.findByEnfantIdOrderByModifieLeDesc(enfantId).stream()
                .map(revision -> new Revision(revision.champ(), revision.modifieLe())).toList();
    }

    private void refuser(UUID tuteurId, UUID enfantId) {
        journal.consigner(tuteurId, "PARENT", "ACCES_REFUSE", "ENFANT", enfantId.toString(), Resultat.REFUS);
        throw introuvable();
    }

    private static FicheEnfant vue(Enfant enfant) {
        return new FicheEnfant(enfant.id(), enfant.prenom(), enfant.nom(), enfant.dateNaissance(), enfant.modifieLe());
    }

    private static ErreurMetier introuvable() {
        return new ErreurMetier(CodeErreur.RESSOURCE_INTROUVABLE, "Enfant introuvable.");
    }
}
