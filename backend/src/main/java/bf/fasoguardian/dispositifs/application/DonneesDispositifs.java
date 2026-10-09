package bf.fasoguardian.dispositifs.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import bf.fasoguardian.audit.DonneesPersonnelles;
import org.springframework.stereotype.Component;

/**
 * Bracelets portés par les enfants pour l'exercice des droits. À l'effacement, le bracelet en service est
 * désappairé ; l'historique des appairages reste au parc, rattaché à un identifiant d'enfant qui ne désigne
 * plus personne.
 */
@Component
class DonneesDispositifs implements DonneesPersonnelles {

    private final Appairages appairages;
    private final RegistreBracelets registre;

    DonneesDispositifs(Appairages appairages, RegistreBracelets registre) {
        this.appairages = appairages;
        this.registre = registre;
    }

    @Override
    public String rubrique() {
        return "bracelets";
    }

    /** Après la télémétrie, qui efface d'après les périodes d'appairage. */
    @Override
    public int ordre() {
        return 20;
    }

    @Override
    public Map<String, Object> exporter(Personne personne) {
        Map<String, Object> export = new LinkedHashMap<>();
        for (UUID enfant : personne.enfants()) {
            export.put(enfant.toString(), registre.periodesDe(enfant).stream().map(periode -> {
                Map<String, Object> appairage = new LinkedHashMap<>();
                appairage.put("bracelet", periode.numeroSerie());
                appairage.put("debut", periode.debut().toString());
                appairage.put("fin", periode.fin() == null ? null : periode.fin().toString());
                return appairage;
            }).toList());
        }
        return export;
    }

    @Override
    public long effacer(Personne personne) {
        return personne.enfants().stream().filter(appairages::desappairerPourEffacement).count();
    }
}
