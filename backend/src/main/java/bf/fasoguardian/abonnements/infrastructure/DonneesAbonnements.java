package bf.fasoguardian.abonnements.infrastructure;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import bf.fasoguardian.audit.DonneesPersonnelles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Abonnements et reçus pour l'exercice des droits. Les reçus sont des pièces comptables : ils sont conservés,
 * et ne portent qu'un numéro de portefeuille masqué. L'effacement détruit le numéro du portefeuille retenu
 * pour le renouvellement et clôt les demandes de paiement en attente.
 */
@Component
class DonneesAbonnements implements DonneesPersonnelles {

    private final JdbcTemplate jdbc;
    private final Clock horloge;

    DonneesAbonnements(JdbcTemplate jdbc, Clock horloge) {
        this.jdbc = jdbc;
        this.horloge = horloge;
    }

    @Override
    public String rubrique() {
        return "abonnements";
    }

    @Override
    public Map<String, Object> exporter(Personne personne) {
        Map<String, Object> export = new LinkedHashMap<>();
        export.put("abonnements", jdbc.queryForList("SELECT enfant_id::text AS enfant, offre_code AS offre, statut,"
                + " prochaine_echeance::text AS \"prochaineEcheance\", renouvellement_auto AS \"renouvellementAuto\", moyen,"
                + " numero_masque AS \"portefeuille\" FROM abonnements.abonnement WHERE tuteur_id = ? ORDER BY cree_le",
                personne.tuteurId()));
        export.put("recus", jdbc.queryForList("SELECT numero, offre_libelle AS offre, montant_fcfa AS \"montantFcfa\", moyen,"
                + " numero_masque AS \"portefeuille\", periode_debut::text AS \"periodeDebut\", periode_fin::text AS \"periodeFin\","
                + " emise_le::text AS \"emisLe\" FROM abonnements.facture WHERE tuteur_id = ? ORDER BY emise_le", personne.tuteurId()));
        return export;
    }

    @Override
    public long effacer(Personne personne) {
        Timestamp maintenant = Timestamp.from(horloge.instant());
        List<UUID> abonnements = jdbc.queryForList("SELECT id FROM abonnements.abonnement WHERE tuteur_id = ?", UUID.class,
                personne.tuteurId());
        long modifies = 0;
        for (UUID abonnement : abonnements) {
            jdbc.update("UPDATE abonnements.paiement SET statut = 'EXPIRE', conclu_le = ? WHERE abonnement_id = ? AND statut = 'INITIE'",
                    maintenant, abonnement);
            modifies += jdbc.update("UPDATE abonnements.abonnement SET numero_chiffre = NULL, numero_masque = NULL,"
                    + " renouvellement_auto = FALSE, modifie_le = ? WHERE id = ?", maintenant, abonnement);
        }
        return modifies;
    }
}
