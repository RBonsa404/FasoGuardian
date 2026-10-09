package bf.fasoguardian.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;

import bf.fasoguardian.Acteurs;
import bf.fasoguardian.Acteurs.Carte;
import bf.fasoguardian.Acteurs.Parent;
import bf.fasoguardian.Acteurs.ParentAvecEnfant;
import bf.fasoguardian.TestIntegration;
import bf.fasoguardian.audit.application.Conformite;
import bf.fasoguardian.famille.application.PurgesFamille;
import bf.fasoguardian.identite.application.PurgeKyc;
import bf.fasoguardian.notifications.infrastructure.SmsBacASable;
import bf.fasoguardian.telemetrie.application.Ingestion;
import bf.fasoguardian.telemetrie.application.Ingestion.Resultat;
import bf.fasoguardian.telemetrie.application.Positions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Droits d'accès et d'effacement, purges, tableau de bord et journal d'audit (US-ADM-002, US-ADM-003). */
class ConformiteIT extends TestIntegration {

    private static final String DEMANDES = "/api/v1/console/conformite/demandes";

    @Autowired
    MockMvc mvc;

    @Autowired
    SmsBacASable sms;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Ingestion ingestion;

    @Autowired
    Positions positions;

    @Autowired
    Conformite conformite;

    @Autowired
    PurgesFamille purgesFamille;

    @Autowired
    PurgeKyc purgeKyc;

    Acteurs acteurs;

    @BeforeEach
    void preparer() {
        acteurs = new Acteurs(mvc, sms);
    }

    @Test
    void leParentObtientAussitotToutesLesDonneesDetenuesSurLuiEtSesEnfants() throws Exception {
        ParentAvecEnfant famille = familleComplete();
        Parent parent = famille.parent();

        avec(get("/api/v1/moi/donnees"), parent.jeton()).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.matchesPattern(
                        "attachment; filename=\"mes-donnees-ACC-\\d{6}\\.json\"")))
                .andExpect(jsonPath("$.reference").value(org.hamcrest.Matchers.startsWith("ACC-")))
                .andExpect(jsonPath("$.compte.telephone").value("+226" + parent.telephone()))
                .andExpect(jsonPath("$.compte.consentements.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.compte.dossiersDeVerification[0].statut").value("APPROUVE"))
                .andExpect(jsonPath("$.enfants.fiches[0].fiche.prenom").exists())
                .andExpect(jsonPath("$.enfants.fiches[0].sante.groupeSanguin").value("O+"))
                .andExpect(jsonPath("$.enfants.fiches[0].contactsDUrgence[0].telephone").value("+22676112233"))
                .andExpect(jsonPath("$.bracelets['" + famille.enfantId() + "'][0].bracelet").exists())
                .andExpect(jsonPath("$.positions['" + famille.enfantId() + "'].nombreDePositionsConservees").value(1))
                .andExpect(jsonPath("$.positions['" + famille.enfantId() + "'].positions[0].latitude").value(12.37))
                .andExpect(jsonPath("$.safeZones['" + famille.enfantId() + "'][0].nom").value("Maison"))
                .andExpect(jsonPath("$.alertes['" + famille.enfantId() + "'].alertes").isArray())
                .andExpect(jsonPath("$.abonnements.recus").isArray()).andExpect(jsonPath("$.notifications.recues").isArray());

        // La demande est comptée et l'export journalisé ; un autre parent ne reçoit que ses propres données.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit.demande_droit d JOIN audit.entree e ON e.cible_id = d.reference"
                + " WHERE d.type = 'ACCES' AND d.statut = 'TRAITEE' AND e.action = 'DONNEES_EXPORTEES' AND e.acteur_id = d.demandeur_id"
                + " AND d.demandeur_id = (SELECT tuteur_id FROM identite.lien_tutelle WHERE enfant_id = ?::uuid)", Integer.class,
                famille.enfantId())).isEqualTo(1);
        avec(get("/api/v1/moi/donnees"), acteurs.parent().jeton()).andExpect(status().isOk())
                .andExpect(jsonPath("$.enfants.fiches.length()").value(0)).andExpect(jsonPath("$.positions").isEmpty());
        avec(get("/api/v1/moi/donnees"), null).andExpect(status().isUnauthorized());
        avec(get("/api/v1/moi/donnees"), acteurs.jetonAdmin()).andExpect(status().isForbidden());
    }

    @Test
    void laClotureVautDemandeDEffacementQueLAdministrateurExecuteEtQuiEstAccusee() throws Exception {
        ParentAvecEnfant famille = familleComplete();
        Parent parent = famille.parent();
        String tuteurId = jdbc.queryForObject("SELECT tuteur_id::text FROM identite.lien_tutelle WHERE enfant_id = ?::uuid",
                String.class, famille.enfantId());
        String braceletId = jdbc.queryForObject("SELECT bracelet_id::text FROM dispositifs.appairage WHERE enfant_id = ?::uuid",
                String.class, famille.enfantId());

        String reference = clore(parent);

        String admin = acteurs.jetonAdmin();
        String demandeId = jdbc.queryForObject("SELECT id::text FROM audit.demande_droit WHERE reference = ?", String.class, reference);
        avec(get(DEMANDES), admin).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.reference == '" + reference + "')].statut").value("RECUE"));
        // Tant que la demande n'est pas exécutée, les données sont encore là.
        assertThat(compter("famille.enfant", "id", famille.enfantId())).isEqualTo(1);

        avec(post(DEMANDES + "/" + demandeId + "/execution"), acteurs.agent("[\"KYC\"]").jeton()).andExpect(status().isForbidden());
        avec(post(DEMANDES + "/" + demandeId + "/execution"), null).andExpect(status().isUnauthorized());
        avec(post(DEMANDES + "/" + demandeId + "/execution"), admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("TRAITEE")).andExpect(jsonPath("$.traiteeParLeSysteme").value(false));
        avec(post(DEMANDES + "/" + demandeId + "/execution"), admin).andExpect(status().isConflict());

        // Tout ce qui n'a pas à être conservé a disparu, dans chaque module.
        assertThat(compter("famille.enfant", "id", famille.enfantId())).isZero();
        assertThat(compter("famille.fiche_sante", "enfant_id", famille.enfantId())).isZero();
        assertThat(compter("famille.contact_urgence", "enfant_id", famille.enfantId())).isZero();
        assertThat(compter("famille.profil_qr", "enfant_id", famille.enfantId())).isZero();
        assertThat(compter("geolocalisation.safe_zone", "enfant_id", famille.enfantId())).isZero();
        assertThat(compter("telemetrie.position", "bracelet_id", braceletId)).isZero();
        assertThat(compter("telemetrie.etat_bracelet", "bracelet_id", braceletId)).isZero();
        assertThat(compter("identite.lien_tutelle", "tuteur_id", tuteurId)).isZero();
        assertThat(compter("identite.consentement", "utilisateur_id", tuteurId)).isZero();
        assertThat(compter("notifications.notification", "destinataire_id", tuteurId)).isZero();
        assertThat(jdbc.queryForMap("SELECT telephone_chiffre, telephone_hash, mdp_argon2id, statut, (efface_le IS NOT NULL) AS efface"
                + " FROM identite.utilisateur WHERE id = ?::uuid", tuteurId)).containsEntry("telephone_chiffre", null)
                .containsEntry("telephone_hash", null).containsEntry("mdp_argon2id", null).containsEntry("statut", "CLOS")
                .containsEntry("efface", true);
        // Le bracelet est rendu au service après-vente et sa page publique ne désigne plus l'enfant.
        assertThat(jdbc.queryForObject("SELECT statut FROM dispositifs.bracelet WHERE id = ?::uuid", String.class, braceletId))
                .isEqualTo("EN_SAV");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dispositifs.appairage WHERE enfant_id = ?::uuid AND fin IS NULL",
                Integer.class, famille.enfantId())).isZero();
        // Conservés : les pièces KYC un an encore, et les entrées du journal d'audit.
        assertThat(compter("identite.dossier_kyc", "demandeur_id", tuteurId)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT action FROM audit.entree WHERE cible_id = ? ORDER BY id", String.class, reference))
                .containsExactly("EFFACEMENT_DEMANDE", "EFFACEMENT_EXECUTE");

        assertThat(acteurs.dernierSms(parent.telephone())).contains("vos données ont été supprimées").contains(reference);
        // Le numéro est de nouveau libre pour une inscription.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identite.utilisateur WHERE telephone_hash IS NOT NULL AND id = ?::uuid",
                Integer.class, tuteurId)).isZero();
    }

    @Test
    void unEnfantQuiGardeUnAutreTuteurNEstPasEfface() throws Exception {
        ParentAvecEnfant famille = familleComplete();
        Parent second = acteurs.parent();
        jdbc.update("INSERT INTO identite.lien_tutelle (id, tuteur_id, enfant_id, nature, statut, cree_le)"
                + " VALUES (gen_random_uuid(), ?::uuid, ?::uuid, 'TUTEUR', 'ACTIF', now())", idDe(second.jeton()), famille.enfantId());
        assertThat(compter("identite.lien_tutelle", "enfant_id", famille.enfantId())).isEqualTo(2);

        String reference = clore(famille.parent());
        executer(reference);

        assertThat(compter("famille.enfant", "id", famille.enfantId())).isEqualTo(1);
        assertThat(compter("famille.fiche_sante", "enfant_id", famille.enfantId())).isEqualTo(1);
        assertThat(compter("geolocalisation.safe_zone", "enfant_id", famille.enfantId())).isEqualTo(1);
        assertThat(compter("identite.lien_tutelle", "enfant_id", famille.enfantId())).isEqualTo(1);
        // L'autre tuteur garde l'accès à l'enfant.
        avec(get("/api/v1/enfants/" + famille.enfantId()), second.jeton()).andExpect(status().isOk());
    }

    @Test
    void aLApprocheDeLEcheanceLeSystemeExecuteLuiMemeLEffacement() throws Exception {
        ParentAvecEnfant famille = familleComplete();
        String reference = clore(famille.parent());

        conformite.executerLesEffacementsDus();
        assertThat(compter("famille.enfant", "id", famille.enfantId())).as("avant l'approche de l'échéance").isEqualTo(1);

        jdbc.update("UPDATE audit.demande_droit SET recue_le = now() - INTERVAL '26 days' WHERE reference = ?", reference);
        conformite.executerLesEffacementsDus();

        assertThat(compter("famille.enfant", "id", famille.enfantId())).isZero();
        avec(get(DEMANDES), acteurs.jetonAdmin())
                .andExpect(jsonPath("$[?(@.reference == '" + reference + "')].traiteeParLeSysteme").value(true));
        assertThat(jdbc.queryForObject("SELECT role FROM audit.entree WHERE action = 'EFFACEMENT_EXECUTE' AND cible_id = ?",
                String.class, reference)).isEqualTo("SYSTEME");
    }

    @Test
    void lesPurgesAppliquentLesDureesDeConservationEtSontInscritesAuRegistre() throws Exception {
        ParentAvecEnfant famille = familleComplete();
        String tuteurId = jdbc.queryForObject("SELECT tuteur_id::text FROM identite.lien_tutelle WHERE enfant_id = ?::uuid",
                String.class, famille.enfantId());
        jdbc.execute("SELECT famille.creer_partition_consultation_qr((CURRENT_DATE - INTERVAL '13 months')::date)");
        jdbc.execute("SELECT famille.creer_partition_consultation_qr((CURRENT_DATE - INTERVAL '11 months')::date)");
        for (String age : new String[] {"13 months", "11 months"}) {
            jdbc.update("INSERT INTO famille.consultation_qr (id, consulte_le, enfant_id, ip_pseudonymisee, resultat)"
                    + " VALUES (gen_random_uuid(), now() - ?::interval, ?::uuid, 'pseudonyme', 'TROUVE')", age, famille.enfantId());
        }
        for (String age : new String[] {"31 days", "29 days"}) {
            jdbc.update("INSERT INTO famille.signalement_tiers (id, enfant_id, telephone_chiffre, recu_le)"
                    + " VALUES (gen_random_uuid(), ?::uuid, '\\\\x00'::bytea, now() - ?::interval)", famille.enfantId(), age);
        }

        purgesFamille.purger();
        positions.entretenir();

        assertThat(compter("famille.consultation_qr", "enfant_id", famille.enfantId())).isEqualTo(1);
        assertThat(compter("famille.signalement_tiers", "enfant_id", famille.enfantId())).isEqualTo(1);

        // Pièces KYC : gardées un an après la clôture, puis détruites.
        clore(famille.parent());
        purgeKyc.purger();
        assertThat(compter("identite.dossier_kyc", "demandeur_id", tuteurId)).isEqualTo(1);
        jdbc.update("UPDATE identite.utilisateur SET clos_le = now() - INTERVAL '13 months' WHERE id = ?::uuid", tuteurId);
        purgeKyc.purger();
        assertThat(compter("identite.dossier_kyc", "demandeur_id", tuteurId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identite.piece_justificative p WHERE NOT EXISTS ("
                + "SELECT 1 FROM identite.dossier_kyc d WHERE d.id = p.dossier_id)", Integer.class)).isZero();

        String admin = acteurs.jetonAdmin();
        avec(get("/api/v1/console/conformite"), admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.aipd.documentee").value(false))
                .andExpect(jsonPath("$.conservation[0].donnee").value("Positions"))
                .andExpect(jsonPath("$.conservation.length()").value(11))
                .andExpect(jsonPath("$.joursDePurge").value(1))
                .andExpect(jsonPath("$.demandesEffacement").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.purges[?(@.traitement == 'CONSULTATIONS_PAGE_QR')].executions").exists())
                .andExpect(jsonPath("$.purges[?(@.traitement == 'NUMEROS_DES_TIERS')].elements").exists())
                .andExpect(jsonPath("$.purges[?(@.traitement == 'POSITIONS')].executions").exists())
                .andExpect(jsonPath("$.purges[?(@.traitement == 'PIECES_KYC')].executions").exists());
        avec(get("/api/v1/console/conformite"), acteurs.agent("[\"SUPPORT\"]").jeton()).andExpect(status().isForbidden());

        String mois = YearMonth.now(ZoneId.of("Africa/Ouagadougou")).toString();
        byte[] pdf = avec(get("/api/v1/console/conformite/rapport?mois=" + mois), admin).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"rapport-conformite-" + mois + ".pdf\""))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit.entree WHERE action = 'RAPPORT_DE_CONFORMITE_PRODUIT' AND cible_id = ?",
                Integer.class, mois)).isPositive();
        avec(get("/api/v1/console/conformite/rapport?mois=" + mois), acteurs.jetonSav()).andExpect(status().isForbidden());
    }

    @Test
    void lAdministrateurConsulteLeJournalFiltreEtFaitVerifierLaChaine() throws Exception {
        Parent parent = acteurs.parent();
        String admin = acteurs.jetonAdmin();
        avec(get("/api/v1/moi/donnees"), parent.jeton()).andExpect(status().isOk());

        avec(get("/api/v1/console/audit?taille=5"), admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.entrees.length()").value(5)).andExpect(jsonPath("$.taille").value(5))
                .andExpect(jsonPath("$.total").value(org.hamcrest.Matchers.greaterThan(5)))
                .andExpect(jsonPath("$.entrees[0].empreinte").value(org.hamcrest.Matchers.matchesPattern("[0-9a-f]{4}…[0-9a-f]{4}")));
        avec(get("/api/v1/console/audit?action=DONNEES_EXPORTEES&role=PARENT&resultat=SUCCES"), admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.entrees[0].action").value("DONNEES_EXPORTEES"))
                .andExpect(jsonPath("$.entrees[0].typeCible").value("DEMANDE_DROIT"));
        avec(get("/api/v1/console/audit?action=ACTION_INCONNUE"), admin).andExpect(jsonPath("$.total").value(0));
        avec(get("/api/v1/console/audit?depuis=" + Instant.now().plusSeconds(3600)), admin).andExpect(jsonPath("$.total").value(0));

        avec(post("/api/v1/console/audit/verification"), admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.integre").value(true)).andExpect(jsonPath("$.verifieeLe").exists())
                .andExpect(jsonPath("$.entreeAlteree").doesNotExist());
        avec(get("/api/v1/console/audit?taille=1"), admin).andExpect(jsonPath("$.chaine.integre").value(true))
                .andExpect(jsonPath("$.chaine.verifieeLe").exists());

        // La consultation du journal est elle-même journalisée ; les autres rôles n'y ont pas accès.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit.entree WHERE action = 'JOURNAL_CONSULTE'", Integer.class)).isPositive();
        avec(get("/api/v1/console/audit"), acteurs.agent("[\"KYC\"]").jeton()).andExpect(status().isForbidden());
        avec(get("/api/v1/console/audit"), parent.jeton()).andExpect(status().isForbidden());
        avec(get("/api/v1/console/audit"), null).andExpect(status().isUnauthorized());
        avec(post("/api/v1/console/audit/verification"), acteurs.jetonSav()).andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------- aides

    /** Famille dont chaque module détient quelque chose : fiche santé, contact, bracelet, position, zone. */
    private ParentAvecEnfant familleComplete() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        String jeton = famille.parent().jeton();
        String base = "/api/v1/enfants/" + famille.enfantId();
        json(put(base + "/sante"), "{\"groupeSanguin\":\"O+\",\"elements\":[{\"type\":\"ALLERGIE\",\"libelle\":\"Arachide\","
                + "\"critique\":true}]}", jeton).andExpect(status().isOk());
        json(post(base + "/contacts"), "{\"lien\":\"Tante\",\"nom\":\"Fatou Sawadogo\",\"telephone\":\"76 11 22 33\","
                + "\"visibleSurQr\":true}", jeton).andExpect(status().isCreated());
        Carte carte = acteurs.equiper(famille);
        jdbc.update("UPDATE dispositifs.appairage SET debut = now() - INTERVAL '3 days' WHERE enfant_id = ?::uuid", famille.enfantId());
        String mesure = "{\"t\":" + (Instant.now().getEpochSecond() - 600) + ",\"seq\":1,\"lat\":12.37000,\"lon\":-1.52000,"
                + "\"acc\":10,\"src\":\"gnss\",\"bat\":70}";
        assertThat(ingestion.telemetrie(carte.numeroSerie(), mesure.getBytes(StandardCharsets.UTF_8))).isEqualTo(Resultat.ACCEPTE);
        jdbc.update("DELETE FROM identite.code_usage_unique WHERE finalite = '2F_MODIFIER_SAFE_ZONE'");
        json(post("/api/v1/moi/second-facteur"), "{\"action\":\"MODIFIER_SAFE_ZONE\"}", jeton).andExpect(status().isAccepted());
        json(post(base + "/zones"), "{\"forme\":\"CERCLE\",\"nom\":\"Maison\",\"categorie\":\"MAISON\",\"centre\":{\"latitude\":12.37,"
                + "\"longitude\":-1.52},\"rayonM\":200,\"jours\":[1,2,3,4,5,6,7],\"debut\":\"00:00\",\"fin\":\"00:00\",\"toleranceS\":0,"
                + "\"codeSecondFacteur\":\"" + acteurs.dernierCodeDeConfirmation(famille.parent().telephone()) + "\"}", jeton)
                .andExpect(status().isCreated());
        return famille;
    }

    /** Clôt le compte avec son second facteur ; rend la référence de la demande d'effacement. */
    private String clore(Parent parent) throws Exception {
        jdbc.update("DELETE FROM identite.code_usage_unique WHERE finalite = '2F_CLORE_COMPTE'");
        json(post("/api/v1/moi/second-facteur"), "{\"action\":\"CLORE_COMPTE\"}", parent.jeton()).andExpect(status().isAccepted());
        json(post("/api/v1/moi/cloture"), "{\"codeSecondFacteur\":\"" + acteurs.dernierCodeDeConfirmation(parent.telephone()) + "\"}",
                parent.jeton()).andExpect(status().isNoContent());
        return Acteurs.extraire(acteurs.dernierSms(parent.telephone()), "\\((EFF-\\d{6})\\)");
    }

    private void executer(String reference) throws Exception {
        String id = jdbc.queryForObject("SELECT id::text FROM audit.demande_droit WHERE reference = ?", String.class, reference);
        avec(post(DEMANDES + "/" + id + "/execution"), acteurs.jetonAdmin()).andExpect(status().isOk());
    }

    /** Identifiant du compte, lu dans le jeton d'accès. */
    private static String idDe(String jeton) {
        String charge = new String(java.util.Base64.getUrlDecoder().decode(jeton.split("\\.")[1]), StandardCharsets.UTF_8);
        return Acteurs.extraire(charge, "\"sub\":\"([0-9a-f-]{36})\"");
    }

    private int compter(String table, String colonne, String identifiant) {
        Integer nombre = jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + colonne + " = ?::uuid", Integer.class, identifiant);
        return nombre == null ? 0 : nombre;
    }

    private ResultActions avec(MockHttpServletRequestBuilder requete, String jeton) throws Exception {
        return mvc.perform(jeton == null ? requete : requete.header("Authorization", "Bearer " + jeton));
    }

    private ResultActions json(MockHttpServletRequestBuilder requete, String corps, String jeton) throws Exception {
        return avec(requete.contentType(MediaType.APPLICATION_JSON).content(corps), jeton);
    }
}
