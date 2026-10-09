package bf.fasoguardian.identite.infrastructure;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import bf.fasoguardian.audit.DonneesPersonnelles;
import bf.fasoguardian.notifications.ServiceSms;
import bf.fasoguardian.plateforme.chiffrement.CategorieDonnee;
import bf.fasoguardian.plateforme.chiffrement.ServiceChiffrement;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Compte, consentements et dossier KYC d'un tuteur pour l'exercice des droits. À l'effacement, le compte ne
 * garde ni numéro ni mot de passe et l'accusé part par SMS ; les pièces KYC sont conservées un an après la
 * clôture puis détruites par {@code PurgeKyc} (FG-DOC-06, tableau 18).
 */
@Component
class DonneesIdentite implements DonneesPersonnelles {

    private final JdbcTemplate jdbc;
    private final ServiceChiffrement chiffrement;
    private final ServiceSms sms;
    private final Clock horloge;

    DonneesIdentite(JdbcTemplate jdbc, ServiceChiffrement chiffrement, ServiceSms sms, Clock horloge) {
        this.jdbc = jdbc;
        this.chiffrement = chiffrement;
        this.sms = sms;
        this.horloge = horloge;
    }

    @Override
    public String rubrique() {
        return "compte";
    }

    /** Le compte part en dernier : c'est lui qui porte le numéro auquel l'accusé est envoyé. */
    @Override
    public int ordre() {
        return 100;
    }

    @Override
    public Map<String, Object> exporter(Personne personne) {
        Map<String, Object> export = new LinkedHashMap<>();
        jdbc.query("SELECT telephone_chiffre, statut, cree_le FROM identite.utilisateur WHERE id = ?", ligne -> {
            byte[] telephone = ligne.getBytes("telephone_chiffre");
            export.put("telephone", telephone == null ? null : chiffrement.dechiffrerTexte(CategorieDonnee.TELEPHONE, telephone));
            export.put("statut", ligne.getString("statut"));
            export.put("creeLe", ligne.getTimestamp("cree_le").toInstant().toString());
        }, personne.tuteurId());
        export.put("consentements", jdbc.queryForList("SELECT type, accorde, version_texte AS \"versionDuTexte\","
                + " enregistre_le::text AS \"enregistreLe\" FROM identite.consentement WHERE utilisateur_id = ? ORDER BY enregistre_le",
                personne.tuteurId()));
        export.put("dossiersDeVerification", jdbc.queryForList("SELECT d.reference, d.statut, d.nature_lien AS \"natureDuLien\","
                + " d.depose_le::text AS \"deposeLe\", d.decide_le::text AS \"decideLe\","
                + " (SELECT count(*) FROM identite.piece_justificative p WHERE p.dossier_id = d.id) AS \"pieces\""
                + " FROM identite.dossier_kyc d WHERE d.demandeur_id = ? ORDER BY d.cree_le", personne.tuteurId()));
        return export;
    }

    @Override
    public long effacer(Personne personne) {
        List<Map<String, Object>> comptes = jdbc.queryForList("SELECT telephone_chiffre, telephone_hash FROM identite.utilisateur"
                + " WHERE id = ? AND type = 'TUTEUR' AND efface_le IS NULL", personne.tuteurId());
        if (comptes.isEmpty()) {
            return 0;
        }
        String numero = chiffrement.dechiffrerTexte(CategorieDonnee.TELEPHONE, (byte[]) comptes.get(0).get("telephone_chiffre"));
        Timestamp maintenant = Timestamp.from(horloge.instant());
        long supprimes = jdbc.update("DELETE FROM identite.lien_tutelle WHERE tuteur_id = ?", personne.tuteurId());
        supprimes += jdbc.update("DELETE FROM identite.jeton_rafraichissement WHERE utilisateur_id = ?", personne.tuteurId());
        supprimes += jdbc.update("DELETE FROM identite.consentement WHERE utilisateur_id = ?", personne.tuteurId());
        supprimes += jdbc.update("DELETE FROM identite.code_usage_unique WHERE cible_hash = ?", comptes.get(0).get("telephone_hash"));
        supprimes += jdbc.update("UPDATE identite.utilisateur SET efface_le = ?, telephone_chiffre = NULL, telephone_hash = NULL,"
                + " mdp_argon2id = NULL, statut = 'CLOS', clos_le = coalesce(clos_le, ?) WHERE id = ?", maintenant, maintenant,
                personne.tuteurId());
        sms.envoyer(numero, "FasoGuardian : vos données ont été supprimées (demande " + personne.reference()
                + "). Les pièces de vérification d'identité seront détruites un an après la clôture du compte.");
        return supprimes;
    }
}
