package bf.fasoguardian.telemetrie.application;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import bf.fasoguardian.dispositifs.Bracelets;
import bf.fasoguardian.dispositifs.Bracelets.EnService;
import bf.fasoguardian.telemetrie.domaine.EtatBracelet;
import bf.fasoguardian.telemetrie.infrastructure.DepotTelemetrie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * État technique des bracelets en service pour le parc du service après-vente (écran 64, US-SAV-002) :
 * batterie, réseau et dernier contact, sans l'enfant ni la position. Seuls les messages reçus depuis
 * l'appairage en cours comptent : ceux d'avant concernent un autre porteur.
 */
@Service
public class EtatsDuParc {

    public record EtatTechnique(String numeroSerie, Integer batterie, String reseau, Instant dernierContact) {
    }

    private final Bracelets bracelets;
    private final DepotTelemetrie depot;

    EtatsDuParc(Bracelets bracelets, DepotTelemetrie depot) {
        this.bracelets = bracelets;
        this.depot = depot;
    }

    @Transactional(readOnly = true)
    public List<EtatTechnique> enService() {
        Map<UUID, EtatBracelet> etats = depot.etats();
        return bracelets.enService().stream().map(bracelet -> etatDe(bracelet, etats.get(bracelet.braceletId())))
                .filter(Objects::nonNull).sorted(Comparator.comparing(EtatTechnique::numeroSerie)).toList();
    }

    private static EtatTechnique etatDe(EnService bracelet, EtatBracelet etat) {
        if (etat == null || etat.dernierContact().isBefore(bracelet.appaireDepuis())) {
            return null;
        }
        return new EtatTechnique(bracelet.numeroSerie(), etat.batterie(), etat.reseau(), etat.dernierContact());
    }
}
