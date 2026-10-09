package bf.fasoguardian.dispositifs.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import bf.fasoguardian.abonnements.Droits;
import bf.fasoguardian.abonnements.Droits.DroitsEnfant;
import bf.fasoguardian.dispositifs.Bracelets;
import bf.fasoguardian.dispositifs.domaine.Appairage;
import bf.fasoguardian.dispositifs.domaine.Bracelet;
import bf.fasoguardian.dispositifs.domaine.StatutBracelet;
import bf.fasoguardian.dispositifs.infrastructure.DepotAppairages;
import bf.fasoguardian.dispositifs.infrastructure.DepotBracelets;
import bf.fasoguardian.dispositifs.infrastructure.DepotConfigurations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class RegistreBracelets implements Bracelets {

    private final DepotBracelets bracelets;
    private final DepotAppairages appairages;
    private final DepotConfigurations configurations;
    private final Droits droits;

    RegistreBracelets(DepotBracelets bracelets, DepotAppairages appairages, DepotConfigurations configurations, Droits droits) {
        this.bracelets = bracelets;
        this.appairages = appairages;
        this.configurations = configurations;
        this.droits = droits;
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
    public List<EnService> enService() {
        return bracelets.findByStatutOrderByNumeroSerie(StatutBracelet.ACTIF).stream().filter(bracelet -> !bracelet.certificatRevoque())
                .flatMap(bracelet -> appairages.findByBraceletIdAndFinIsNull(bracelet.id()).stream().map(appairage -> {
                    DroitsEnfant ouverts = droits.de(appairage.enfantId());
                    return new EnService(bracelet.id(), bracelet.numeroSerie(), appairage.enfantId(), appairage.debut(),
                            configurations.findById(bracelet.id()).orElseThrow().intervalleS(ouverts.intervalleS(), ouverts.suiviContinu()));
                })).toList();
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
