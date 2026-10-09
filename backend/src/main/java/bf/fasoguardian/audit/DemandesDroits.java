package bf.fasoguardian.audit;

import java.util.UUID;

/** Enregistrement d'une demande d'effacement par le module qui la reçoit (clôture de compte). */
public interface DemandesDroits {

    /**
     * Enregistre la demande ; elle sera exécutée dans les trente jours (FG-DOC-04).
     *
     * @return la référence à rappeler au demandeur
     */
    String enregistrerEffacement(UUID tuteurId);
}
