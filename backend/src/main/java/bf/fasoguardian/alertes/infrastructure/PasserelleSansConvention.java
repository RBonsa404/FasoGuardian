package bf.fasoguardian.alertes.infrastructure;

import bf.fasoguardian.alertes.application.PasserelleFds;
import org.springframework.stereotype.Component;

/**
 * État réel du projet : aucune convention n'est signée avec la Police nationale ni la Gendarmerie
 * (FG-DOC-04 §6). Cette passerelle refuse donc toute transmission ; elle sera remplacée par l'adaptateur du
 * protocole convenu lorsque la convention existera.
 */
@Component
class PasserelleSansConvention implements PasserelleFds {

    @Override
    public boolean conventionActive() {
        return false;
    }

    @Override
    public void transmettre(String reference, byte[] dossierPdf) {
        throw new IllegalStateException("Aucune convention active avec les forces de sécurité");
    }
}
