package bf.fasoguardian.dispositifs.application;

import java.util.List;
import java.util.UUID;

import bf.fasoguardian.plateforme.console.Decomptes;
import bf.fasoguardian.plateforme.console.IndicateursConsole;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Tableau de bord du service après-vente (écran 57) : bracelets muets, tickets sans agent, unités au SAV et en stock. */
@Component
class IndicateursSav implements IndicateursConsole {

    private final Decomptes decomptes;

    IndicateursSav(Decomptes decomptes) {
        this.decomptes = decomptes;
    }

    @Override
    public String role() {
        return "SAV";
    }

    @Override
    @Transactional(readOnly = true)
    public List<Indicateur> pour(UUID agentId) {
        long muets = decomptes.compter("SELECT count(*) FROM dispositifs.ticket_maintenance WHERE statut <> 'RESOLU'");
        long sansAgent = decomptes.compter("SELECT count(*) FROM dispositifs.ticket_maintenance WHERE statut = 'OUVERT'");
        long auSav = decomptes.compter("SELECT count(*) FROM dispositifs.bracelet WHERE statut = 'EN_SAV'");
        long enStock = decomptes.compter("SELECT count(*) FROM dispositifs.bracelet WHERE statut = 'EN_STOCK' AND certificat_revoque_le IS NULL");
        return List.of(new Indicateur("sav.muets", muets, null, false),
                new Indicateur("sav.sansAgent", sansAgent, null, sansAgent > 0),
                new Indicateur("sav.auSav", auSav, null, false),
                new Indicateur("sav.enStock", enStock, null, enStock == 0));
    }
}
