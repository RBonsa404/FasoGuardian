package bf.fasoguardian.dispositifs.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import bf.fasoguardian.dispositifs.Bracelets;
import bf.fasoguardian.dispositifs.domaine.Appairage;
import bf.fasoguardian.dispositifs.domaine.Bracelet;
import bf.fasoguardian.dispositifs.domaine.StatutBracelet;
import bf.fasoguardian.dispositifs.infrastructure.DepotAppairages;
import bf.fasoguardian.dispositifs.infrastructure.DepotBracelets;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class RegistreBracelets implements Bracelets {

    private final DepotBracelets bracelets;
    private final DepotAppairages appairages;

    RegistreBracelets(DepotBracelets bracelets, DepotAppairages appairages) {
        this.bracelets = bracelets;
        this.appairages = appairages;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BraceletConnu> parNumeroSerie(String numeroSerie) {
        return bracelets.findByNumeroSerie(numeroSerie).map(bracelet -> connu(bracelet,
                appairages.findByBraceletIdAndFinIsNull(bracelet.id()).orElse(null)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BraceletConnu> deLEnfant(UUID enfantId) {
        return appairages.findByEnfantIdAndFinIsNull(enfantId).flatMap(
                appairage -> bracelets.findById(appairage.braceletId()).map(bracelet -> connu(bracelet, appairage)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Periode> periodesDe(UUID enfantId) {
        return appairages.findByEnfantIdOrderByDebut(enfantId).stream().map(appairage -> new Periode(appairage.braceletId(),
                bracelets.findById(appairage.braceletId()).orElseThrow().numeroSerie(), appairage.debut(), appairage.fin())).toList();
    }

    /** Un bracelet perdu émet encore pendant les 72 h de suivi ; un certificat révoqué ferme tout. */
    private static BraceletConnu connu(Bracelet bracelet, Appairage appairage) {
        boolean enService = bracelet.statut() == StatutBracelet.ACTIF || bracelet.statut() == StatutBracelet.PERDU;
        return new BraceletConnu(bracelet.id(), bracelet.numeroSerie(), appairage == null ? null : appairage.enfantId(),
                appairage == null ? null : appairage.debut(), enService && !bracelet.certificatRevoque());
    }
}
