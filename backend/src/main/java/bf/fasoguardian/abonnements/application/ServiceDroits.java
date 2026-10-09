package bf.fasoguardian.abonnements.application;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;

import bf.fasoguardian.abonnements.Droits;
import bf.fasoguardian.abonnements.domaine.Abonnement;
import bf.fasoguardian.abonnements.domaine.Abonnement.Statut;
import bf.fasoguardian.abonnements.domaine.Offre;
import bf.fasoguardian.abonnements.infrastructure.DepotAbonnements;
import bf.fasoguardian.abonnements.infrastructure.DepotOffres;
import bf.fasoguardian.identite.LiensTutelle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Droits ouverts par l'abonnement (US-SYS-008). Un abonnement restreint pour impayé garde ses Safe Zones mais
 * perd la géolocalisation continue et ne montre plus que 24 heures d'historique. Un enfant sans abonnement
 * payé est couvert par l'abonnement Premium d'un de ses tuteurs s'il en existe un (« plusieurs enfants »,
 * FG-DOC-11 §3.3) ; à défaut il reçoit les droits de l'offre d'accueil si elle est configurée, sinon les
 * droits restreints (ADR 0015).
 */
@Service
class ServiceDroits implements Droits {

    /** Historique visible quand les fonctions avancées sont suspendues. */
    static final int HISTORIQUE_RESTREINT_JOURS = 1;
    private static final String SANS_OFFRE = "AUCUNE";
    /** Offre dont l'abonnement couvre aussi les autres enfants du tuteur qui la paie. */
    static final String OFFRE_FAMILIALE = "PREMIUM";
    private static final List<Statut> SERVIS = List.of(Statut.ACTIF, Statut.EN_RETARD);
    private static final List<Statut> PAYES = List.of(Statut.ACTIF, Statut.EN_RETARD, Statut.RESTREINT);

    private final DepotAbonnements abonnements;
    private final DepotOffres offres;
    private final LiensTutelle liens;
    private final String offreDAccueil;

    ServiceDroits(DepotAbonnements abonnements, DepotOffres offres, LiensTutelle liens,
            @Value("${fasoguardian.abonnements.offre-d-accueil:}") String offreDAccueil) {
        this.abonnements = abonnements;
        this.offres = offres;
        this.liens = liens;
        this.offreDAccueil = offreDAccueil == null ? "" : offreDAccueil.strip();
    }

    @Override
    @Transactional(readOnly = true)
    public DroitsEnfant de(UUID enfantId) {
        Optional<Abonnement> abonnement = abonnements.findByEnfantId(enfantId).filter(a -> a.statut() != Statut.EN_ATTENTE);
        if (abonnement.isEmpty() || abonnement.get().statut() == Statut.RESTREINT) {
            List<UUID> tuteurs = liens.tuteursActifsDe(enfantId);
            if (!tuteurs.isEmpty() && abonnements.existsByTuteurIdInAndOffreCodeAndStatutIn(tuteurs, OFFRE_FAMILIALE, SERVIS)) {
                return pleins(offres.findById(OFFRE_FAMILIALE).orElseThrow());
            }
        }
        if (abonnement.isEmpty()) {
            return offreDAccueil.isEmpty() ? restreints(null) : pleins(offres.findById(offreDAccueil).orElseThrow(
                    () -> new IllegalStateException("Offre d'accueil inconnue (fasoguardian.abonnements.offre-d-accueil)")));
        }
        Offre offre = offres.findById(abonnement.get().offreCode()).orElseThrow();
        return abonnement.get().statut() == Statut.RESTREINT ? restreints(offre) : pleins(offre);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Integer> joursDeConservation() {
        Map<UUID, Integer> parEnfant = new HashMap<>();
        for (Abonnement abonnement : abonnements.findByStatutIn(PAYES)) {
            int jours = offres.findById(abonnement.offreCode()).orElseThrow().historiqueJours();
            parEnfant.merge(abonnement.enfantId(), jours, Math::max);
            if (OFFRE_FAMILIALE.equals(abonnement.offreCode()) && abonnement.statut() != Statut.RESTREINT) {
                liens.enfantsDe(abonnement.tuteurId()).forEach(enfant -> parEnfant.merge(enfant, jours, Math::max));
            }
        }
        return parEnfant;
    }

    private static DroitsEnfant pleins(Offre offre) {
        return new DroitsEnfant(offre.code(), offre.zonesMaximum(), offre.historiqueJours(), offre.intervalleS(), true);
    }

    private static DroitsEnfant restreints(Offre offre) {
        return new DroitsEnfant(offre == null ? SANS_OFFRE : offre.code(), offre == null ? 1 : offre.zonesMaximum(),
                HISTORIQUE_RESTREINT_JOURS, 0, false);
    }
}
