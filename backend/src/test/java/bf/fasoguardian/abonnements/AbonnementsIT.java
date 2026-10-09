package bf.fasoguardian.abonnements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import bf.fasoguardian.Acteurs;
import bf.fasoguardian.Acteurs.Carte;
import bf.fasoguardian.Acteurs.Parent;
import bf.fasoguardian.Acteurs.ParentAvecEnfant;
import bf.fasoguardian.TestIntegration;
import bf.fasoguardian.abonnements.application.Relances;
import bf.fasoguardian.abonnements.infrastructure.AgregateurBacASable;
import bf.fasoguardian.abonnements.infrastructure.AgregateurBacASable.DemandeRecue;
import bf.fasoguardian.abonnements.infrastructure.AgregateurBacASable.NotificationSignee;
import bf.fasoguardian.notifications.infrastructure.SmsBacASable;
import bf.fasoguardian.telemetrie.application.Positions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Abonnement, paiement mobile money, reçus, relances et restriction pour impayé (US-PAR-015, US-PAR-016, US-SYS-008). */
class AbonnementsIT extends TestIntegration {

    private static final ZoneId OUAGADOUGOU = ZoneId.of("Africa/Ouagadougou");
    private static final String NOTIFICATION = "/api/v1/public/paiements/notification";

    @Autowired
    MockMvc mvc;

    @Autowired
    SmsBacASable sms;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AgregateurBacASable agregateur;

    @Autowired
    Relances relances;

    @Autowired
    Positions positions;

    Acteurs acteurs;

    @BeforeEach
    void preparer() {
        acteurs = new Acteurs(mvc, sms);
    }

    @Test
    void lesOffresSouscriptiblesSontCellesDuCatalogueSansLOffreEcole() throws Exception {
        Parent parent = acteurs.parent();

        avec(get("/api/v1/offres"), parent.jeton()).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].code").value("ESSENTIEL")).andExpect(jsonPath("$[0].prixFcfa").value(1500))
                .andExpect(jsonPath("$[0].zonesMaximum").value(1)).andExpect(jsonPath("$[0].intervalleS").value(900))
                .andExpect(jsonPath("$[1].code").value("INTERMEDIAIRE")).andExpect(jsonPath("$[1].prixFcfa").value(2250))
                .andExpect(jsonPath("$[2].code").value("PREMIUM")).andExpect(jsonPath("$[2].historiqueJours").value(90));
        avec(get("/api/v1/offres"), null).andExpect(status().isUnauthorized());
    }

    @Test
    void lAbonnementNEstActiveQuALaConfirmationSigneeDeLOperateurEtUnRecuEstEmis() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Parent parent = famille.parent();
        String base = "/api/v1/enfants/" + famille.enfantId() + "/abonnement";

        avec(get(base), parent.jeton()).andExpect(status().isOk()).andExpect(jsonPath("$.statut").doesNotExist())
                .andExpect(jsonPath("$.droits.offre").value("INTERMEDIAIRE"));

        // L'en-tête d'idempotence est obligatoire.
        json(post(base + "/paiements"), paiement("INTERMEDIAIRE", "70 11 22 33", true), parent.jeton())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"));

        String cle = cle();
        String reponse = payer(famille, "INTERMEDIAIRE", "70 11 22 33", true, cle).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.statut").value("INITIE")).andExpect(jsonPath("$.montantFcfa").value(2250))
                .andExpect(jsonPath("$.recu").doesNotExist()).andReturn().getResponse().getContentAsString();
        String paiementId = Acteurs.extraire(reponse, "\"id\":\"([0-9a-f-]{36})\"");

        // La demande seule n'active rien.
        avec(get(base), parent.jeton()).andExpect(jsonPath("$.statut").doesNotExist())
                .andExpect(jsonPath("$.paiementEnCours.id").value(paiementId));

        // Rejouée avec la même clé, elle ne sollicite pas le portefeuille une seconde fois.
        payer(famille, "INTERMEDIAIRE", "70 11 22 33", true, cle).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(paiementId));
        assertThat(paiements(famille)).hasSize(1);
        // Une autre demande pendant l'attente est refusée.
        payer(famille, "PREMIUM", "70 11 22 33", true, cle()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAIEMENT_EN_COURS"));

        DemandeRecue demande = demandeDe(paiementId);
        NotificationSignee confirmation = agregateur.notification(demande.reference(), true, 2250, null);
        notifier(confirmation).andExpect(status().isNoContent());

        LocalDate aujourdhui = LocalDate.now(OUAGADOUGOU);
        avec(get(base), parent.jeton()).andExpect(jsonPath("$.statut").value("ACTIF"))
                .andExpect(jsonPath("$.offre").value("INTERMEDIAIRE")).andExpect(jsonPath("$.prixFcfa").value(2250))
                .andExpect(jsonPath("$.prochaineEcheance").value(aujourdhui.plusMonths(1).toString()))
                .andExpect(jsonPath("$.renouvellementAuto").value(true)).andExpect(jsonPath("$.moyen").value("ORANGE_MONEY"))
                .andExpect(jsonPath("$.numeroMasque").value("+226 70 •• •• 33")).andExpect(jsonPath("$.paiementEnCours").doesNotExist());
        String recu = Acteurs.extraire(avec(get("/api/v1/paiements/" + paiementId), parent.jeton())
                .andExpect(jsonPath("$.statut").value("CONFIRME")).andReturn().getResponse().getContentAsString(),
                "\"recu\":\"(FG-R-\\d{4}-\\d{2}-\\d{4,})\"");

        // La même notification reçue deux fois ne prolonge pas deux fois.
        notifier(confirmation).andExpect(status().isNoContent());
        avec(get(base), parent.jeton()).andExpect(jsonPath("$.prochaineEcheance").value(aujourdhui.plusMonths(1).toString()));

        avec(get("/api/v1/recus"), parent.jeton()).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].numero").value(recu)).andExpect(jsonPath("$[0].montantFcfa").value(2250))
                .andExpect(jsonPath("$[0].offre").value("Intermédiaire")).andExpect(jsonPath("$[0].periodeDebut").value(aujourdhui.toString()));
        byte[] pdf = avec(get("/api/v1/recus/" + recu + "/pdf"), parent.jeton()).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF)).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        acteurs.attendreSms(parent.telephone(), "paiement de 2250 FCFA reçu", recu);

        // Le numéro du portefeuille n'est conservé que chiffré.
        assertThat(jdbc.queryForObject("SELECT encode(numero_chiffre, 'escape') FROM abonnements.abonnement WHERE enfant_id = ?::uuid",
                String.class, famille.enfantId())).doesNotContain("70112233");
        assertThat(jdbc.queryForList("SELECT action FROM audit.entree WHERE type_cible = 'ABONNEMENT' AND cible_id = ("
                + "SELECT id::text FROM abonnements.abonnement WHERE enfant_id = ?::uuid) ORDER BY id", String.class,
                famille.enfantId())).containsExactly("PAIEMENT_DEMANDE", "PAIEMENT_CONFIRME");
    }

    @Test
    void uneNotificationNonSigneeFalsifieeOuDUnMontantDifferentNActiveRien() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        String base = "/api/v1/enfants/" + famille.enfantId() + "/abonnement";
        String paiementId = Acteurs.extraire(payer(famille, "ESSENTIEL", "71 44 55 66", false, cle())
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString(), "\"id\":\"([0-9a-f-]{36})\"");
        DemandeRecue demande = demandeDe(paiementId);
        NotificationSignee valide = agregateur.notification(demande.reference(), true, 1500, null);

        // Sans signature, avec une signature d'un autre corps, avec un corps modifié, avec une date ancienne.
        mvc.perform(post(NOTIFICATION).contentType(MediaType.APPLICATION_JSON).content(valide.corps())).andExpect(status().isForbidden());
        NotificationSignee autre = agregateur.notification("BAS-AUTRE", true, 1500, null);
        mvc.perform(post(NOTIFICATION).contentType(MediaType.APPLICATION_JSON).content(valide.corps())
                .header("X-FG-Signature", autre.signature())).andExpect(status().isForbidden());
        byte[] modifie = new String(valide.corps(), StandardCharsets.UTF_8).replace("ECHEC", "SUCCES").replace("1500", "1")
                .getBytes(StandardCharsets.UTF_8);
        mvc.perform(post(NOTIFICATION).contentType(MediaType.APPLICATION_JSON).content(modifie)
                .header("X-FG-Signature", valide.signature())).andExpect(status().isForbidden());
        String ancienne = valide.signature().replaceFirst("t=\\d+", "t=" + (System.currentTimeMillis() / 1000 - 3600));
        mvc.perform(post(NOTIFICATION).contentType(MediaType.APPLICATION_JSON).content(valide.corps())
                .header("X-FG-Signature", ancienne)).andExpect(status().isForbidden());
        avec(get(base), famille.parent().jeton()).andExpect(jsonPath("$.statut").doesNotExist());

        // Signée, mais pour un montant qui n'est pas celui demandé : l'abonnement n'est pas servi.
        notifier(agregateur.notification(demande.reference(), true, 15, null)).andExpect(status().isNoContent());
        avec(get(base), famille.parent().jeton()).andExpect(jsonPath("$.statut").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit.entree WHERE action = 'PAIEMENT_MONTANT_INCOHERENT' AND cible_id = ?",
                Integer.class, paiementId)).isEqualTo(1);

        notifier(valide).andExpect(status().isNoContent());
        avec(get(base), famille.parent().jeton()).andExpect(jsonPath("$.statut").value("ACTIF"))
                .andExpect(jsonPath("$.offre").value("ESSENTIEL"));
    }

    @Test
    void unPaiementRefuseParLOperateurLaisseLeParentReessayer() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        String paiementId = Acteurs.extraire(payer(famille, "PREMIUM", "76 00 11 22", false, cle()).andReturn().getResponse()
                .getContentAsString(), "\"id\":\"([0-9a-f-]{36})\"");

        notifier(agregateur.notification(demandeDe(paiementId).reference(), false, 5000, "Solde insuffisant"))
                .andExpect(status().isNoContent());

        avec(get("/api/v1/paiements/" + paiementId), famille.parent().jeton()).andExpect(jsonPath("$.statut").value("ECHOUE"))
                .andExpect(jsonPath("$.motifEchec").value("Solde insuffisant"));
        avec(get("/api/v1/enfants/" + famille.enfantId() + "/abonnement"), famille.parent().jeton())
                .andExpect(jsonPath("$.statut").doesNotExist());
        payer(famille, "PREMIUM", "76 00 11 22", false, cle()).andExpect(status().isAccepted());
    }

    @Test
    void seulUnTuteurDeLEnfantVoitEtPaieSonAbonnement() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Parent intrus = acteurs.parent();
        String base = "/api/v1/enfants/" + famille.enfantId() + "/abonnement";
        String paiementId = Acteurs.extraire(payer(famille, "ESSENTIEL", "70 11 22 33", false, cle()).andReturn().getResponse()
                .getContentAsString(), "\"id\":\"([0-9a-f-]{36})\"");
        notifier(agregateur.notification(demandeDe(paiementId).reference(), true, 1500, null));
        String recu = Acteurs.extraire(avec(get("/api/v1/recus"), famille.parent().jeton()).andReturn().getResponse()
                .getContentAsString(), "\"numero\":\"([^\"]+)\"");

        avec(get(base), intrus.jeton()).andExpect(status().isNotFound());
        json(post(base + "/paiements").header("Idempotency-Key", cle()), paiement("ESSENTIEL", "70 11 22 33", false), intrus.jeton())
                .andExpect(status().isNotFound());
        avec(get("/api/v1/paiements/" + paiementId), intrus.jeton()).andExpect(status().isNotFound());
        avec(get("/api/v1/recus"), intrus.jeton()).andExpect(jsonPath("$.length()").value(0));
        avec(get("/api/v1/recus/" + recu + "/pdf"), intrus.jeton()).andExpect(status().isNotFound());
        avec(get(base), null).andExpect(status().isUnauthorized());
        avec(get(base), acteurs.jetonSav()).andExpect(status().isForbidden());
    }

    @Test
    void lOffreRegleLeNombreDeZonesLHistoriqueEtLIntervalleDuBracelet() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Carte carte = acteurs.equiper(famille);
        String jeton = famille.parent().jeton();
        avec(get("/api/v1/enfants/" + famille.enfantId() + "/zones"), jeton).andExpect(jsonPath("$.maximum").value(3));

        activer(famille, "ESSENTIEL", 1500, false);

        avec(get("/api/v1/enfants/" + famille.enfantId() + "/zones"), jeton).andExpect(jsonPath("$.maximum").value(1));
        avec(get("/api/v1/enfants/" + famille.enfantId() + "/abonnement"), jeton).andExpect(jsonPath("$.droits.zonesMaximum").value(1))
                .andExpect(jsonPath("$.droits.intervalleS").value(900)).andExpect(jsonPath("$.droits.suiviContinu").value(true));
        // Le bracelet reçoit, signée, la configuration de l'offre : une position toutes les 15 minutes.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(configurations(carte)).containsExactly(900));
        avec(get("/api/v1/enfants/" + famille.enfantId() + "/bracelet"), jeton).andExpect(jsonPath("$.intervalleS").value(900));

        activer(famille, "PREMIUM", 5000, false);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(configurations(carte)).containsExactly(900, 300));
        avec(get("/api/v1/enfants/" + famille.enfantId() + "/trajets?jour=" + LocalDate.now(OUAGADOUGOU)), jeton)
                .andExpect(jsonPath("$.joursConserves").value(90));
    }

    @Test
    void unImpayeEstRelanceDeuxFoisPuisRestreintSansToucherAuSosNiALaPageQr() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Carte carte = acteurs.equiper(famille);
        Parent parent = famille.parent();
        String base = "/api/v1/enfants/" + famille.enfantId() + "/abonnement";
        activer(famille, "INTERMEDIAIRE", 2250, false);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(configurations(carte)).containsExactly(300));
        LocalDate echeance = LocalDate.now(OUAGADOUGOU).plusMonths(1);

        // Sept jours avant : rappel d'échéance, une seule fois.
        relances.traiter(echeance.minusDays(7));
        acteurs.attendreSms(parent.telephone(), "arrive à échéance");
        sms.vider();
        relances.traiter(echeance.minusDays(6));
        relances.traiter(echeance);
        avec(get(base), parent.jeton()).andExpect(jsonPath("$.statut").value("ACTIF"));

        relances.traiter(echeance.plusDays(1));
        acteurs.attendreSms(parent.telephone(), "n'a pas été renouvelé", "Le SOS et la page QR restent actifs");
        avec(get(base), parent.jeton()).andExpect(jsonPath("$.statut").value("EN_RETARD"))
                .andExpect(jsonPath("$.restrictionLe").value(echeance.plusDays(15).toString()))
                .andExpect(jsonPath("$.droits.suiviContinu").value(true));

        for (int jour = 2; jour <= 14; jour++) {
            relances.traiter(echeance.plusDays(jour));
        }
        acteurs.attendreSms(parent.telephone(), "dernier rappel");
        avec(get(base), parent.jeton()).andExpect(jsonPath("$.statut").value("EN_RETARD"));
        assertThat(configurations(carte)).containsExactly(300);

        relances.traiter(echeance.plusDays(15));
        acteurs.attendreSms(parent.telephone(), "abonnement impayé", "historique est limité à 24 h");
        avec(get(base), parent.jeton()).andExpect(jsonPath("$.statut").value("RESTREINT"))
                .andExpect(jsonPath("$.droits.suiviContinu").value(false)).andExpect(jsonPath("$.droits.historiqueJours").value(1));
        // Le bracelet cesse d'émettre périodiquement : la position n'est plus donnée qu'à la demande.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(configurations(carte)).containsExactly(300, 0));
        avec(get("/api/v1/enfants/" + famille.enfantId() + "/trajets?jour=" + LocalDate.now(OUAGADOUGOU)), parent.jeton())
                .andExpect(jsonPath("$.joursConserves").value(1));
        avec(post("/api/v1/enfants/" + famille.enfantId() + "/bracelet/localisation"), parent.jeton()).andExpect(status().is2xxSuccessful());
        // La page publique du bracelet reste servie.
        mvc.perform(get("/q/" + carte.jetonQr())).andExpect(status().isOk());

        // Le paiement rétablit tout, à partir du jour du paiement. Les relances ont été jouées en avance sur le
        // calendrier : l'échéance est ramenée dans le passé, là où elle serait réellement.
        jdbc.update("UPDATE abonnements.abonnement SET prochaine_echeance = current_date - 20 WHERE enfant_id = ?::uuid",
                famille.enfantId());
        activer(famille, "INTERMEDIAIRE", 2250, false);
        avec(get(base), parent.jeton()).andExpect(jsonPath("$.statut").value("ACTIF"))
                .andExpect(jsonPath("$.droits.suiviContinu").value(true))
                .andExpect(jsonPath("$.prochaineEcheance").value(LocalDate.now(OUAGADOUGOU).plusMonths(1).toString()));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(configurations(carte)).containsExactly(300, 0, 300));
    }

    @Test
    void leRenouvellementAutomatiqueSolliciteLePortefeuilleRetenuUneSeuleFois() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Parent parent = famille.parent();
        String base = "/api/v1/enfants/" + famille.enfantId() + "/abonnement";
        activer(famille, "INTERMEDIAIRE", 2250, true);
        LocalDate echeance = LocalDate.now(OUAGADOUGOU).plusMonths(1);

        relances.traiter(echeance);
        relances.traiter(echeance);

        List<String> demandes = paiements(famille);
        assertThat(demandes).hasSize(2);
        String renouvellement = Acteurs.extraire(avec(get(base), parent.jeton()).andExpect(jsonPath("$.paiementEnCours.statut").value("INITIE"))
                .andReturn().getResponse().getContentAsString(), "\"paiementEnCours\":\\{\"id\":\"([0-9a-f-]{36})\"");
        notifier(agregateur.notification(demandeDe(renouvellement).reference(), true, 2250, null)).andExpect(status().isNoContent());

        avec(get(base), parent.jeton()).andExpect(jsonPath("$.statut").value("ACTIF"))
                .andExpect(jsonPath("$.prochaineEcheance").value(echeance.plusMonths(1).toString()));
        avec(get("/api/v1/recus"), parent.jeton()).andExpect(jsonPath("$.length()").value(2));

        // Le parent peut y renoncer.
        json(put(base + "/renouvellement"), "{\"automatique\":false}", parent.jeton()).andExpect(status().isOk())
                .andExpect(jsonPath("$.renouvellementAuto").value(false));
        relances.traiter(echeance.plusMonths(1));
        assertThat(paiements(famille)).hasSize(2);
    }

    @Test
    void lAbonnementPremiumDUnTuteurCouvreSesAutresEnfantsEtConserveLHistorique90Jours() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        ParentAvecEnfant autre = acteurs.parentAvecEnfant();
        Carte premium = acteurs.equiper(famille);
        Carte ordinaire = acteurs.equiper(autre);
        // Le second enfant de la famille : un enfant dont le parent Premium devient aussi tuteur.
        ParentAvecEnfant cadet = acteurs.parentAvecEnfant();
        jdbc.update("INSERT INTO identite.lien_tutelle (id, tuteur_id, enfant_id, nature, statut, cree_le) SELECT gen_random_uuid(),"
                + " tuteur_id, ?::uuid, 'PARENT', 'ACTIF', now() FROM identite.lien_tutelle WHERE enfant_id = ?::uuid",
                cadet.enfantId(), famille.enfantId());
        activer(cadet, "ESSENTIEL", 1500, false);
        jdbc.update("UPDATE abonnements.abonnement SET statut = 'RESTREINT', etape_relance = 5, prochaine_echeance = current_date - 20"
                + " WHERE enfant_id = ?::uuid", cadet.enfantId());
        String abonnementDuCadet = "/api/v1/enfants/" + cadet.enfantId() + "/abonnement";
        avec(get(abonnementDuCadet), famille.parent().jeton()).andExpect(jsonPath("$.droits.suiviContinu").value(false));

        activer(famille, "PREMIUM", 5000, false);

        // Le cadet, restreint pour son propre abonnement, retrouve tout par le Premium de son tuteur.
        avec(get(abonnementDuCadet), famille.parent().jeton()).andExpect(jsonPath("$.droits.offre").value("PREMIUM"))
                .andExpect(jsonPath("$.droits.suiviContinu").value(true)).andExpect(jsonPath("$.droits.historiqueJours").value(90));
        avec(get("/api/v1/enfants/" + famille.enfantId() + "/trajets?jour=" + LocalDate.now(OUAGADOUGOU)), famille.parent().jeton())
                .andExpect(jsonPath("$.joursConserves").value(90));
        // Un enfant sans tuteur Premium n'en profite pas.
        avec(get("/api/v1/enfants/" + autre.enfantId() + "/abonnement"), autre.parent().jeton())
                .andExpect(jsonPath("$.droits.offre").value("INTERMEDIAIRE"));

        // Conservation : 90 jours pour l'enfant Premium, 30 jours pour les autres.
        jdbc.execute("SELECT telemetrie.creer_partition('position', current_date - 40)");
        jdbc.execute("SELECT telemetrie.creer_partition('position', current_date - 95)");
        for (Carte carte : List.of(premium, ordinaire)) {
            for (int jours : new int[] {40, 95}) {
                jdbc.update("INSERT INTO telemetrie.position (id, mesuree_le, bracelet_id, point, precision_m, source, sequence, recue_le)"
                        + " SELECT gen_random_uuid(), now() - make_interval(days => ?), id,"
                        + " ST_SetSRID(ST_MakePoint(-1.5, 12.3), 4326)::geography, 10, 'GNSS', ?, now() FROM dispositifs.bracelet"
                        + " WHERE numero_serie = ?", jours, jours, carte.numeroSerie());
            }
        }

        positions.entretenir();

        assertThat(sequencesConservees(premium)).containsExactly(40L);
        assertThat(sequencesConservees(ordinaire)).isEmpty();
    }

    private List<Long> sequencesConservees(Carte carte) {
        return jdbc.queryForList("SELECT p.sequence FROM telemetrie.position p JOIN dispositifs.bracelet b ON b.id = p.bracelet_id"
                + " WHERE b.numero_serie = ? ORDER BY p.sequence", Long.class, carte.numeroSerie());
    }

    @Test
    void lesRecusSontNumerotesSansTrou() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        long avant = jdbc.queryForObject("SELECT dernier FROM abonnements.compteur_recus", Long.class);

        activer(famille, "ESSENTIEL", 1500, false);
        activer(famille, "ESSENTIEL", 1500, false);

        assertThat(jdbc.queryForObject("SELECT dernier FROM abonnements.compteur_recus", Long.class)).isEqualTo(avant + 2);
        assertThat(jdbc.queryForList("SELECT right(numero, 4)::bigint FROM abonnements.facture WHERE tuteur_id = ("
                + "SELECT tuteur_id FROM abonnements.abonnement WHERE enfant_id = ?::uuid) ORDER BY emise_le", Long.class,
                famille.enfantId())).containsExactly(avant + 1, avant + 2);
    }

    // -------------------------------------------------------------------- aides

    /** Paie et fait confirmer par l'opérateur simulé. */
    private void activer(ParentAvecEnfant famille, String offre, int montant, boolean renouvellementAuto) throws Exception {
        String paiementId = Acteurs.extraire(payer(famille, offre, "70 11 22 33", renouvellementAuto, cle())
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString(), "\"id\":\"([0-9a-f-]{36})\"");
        notifier(agregateur.notification(demandeDe(paiementId).reference(), true, montant, null)).andExpect(status().isNoContent());
    }

    private ResultActions payer(ParentAvecEnfant famille, String offre, String numero, boolean auto, String cle) throws Exception {
        return json(post("/api/v1/enfants/" + famille.enfantId() + "/abonnement/paiements").header("Idempotency-Key", cle),
                paiement(offre, numero, auto), famille.parent().jeton());
    }

    private static String paiement(String offre, String numero, boolean auto) {
        return "{\"offre\":\"" + offre + "\",\"moyen\":\"ORANGE_MONEY\",\"numero\":\"" + numero + "\",\"renouvellementAuto\":" + auto + "}";
    }

    private ResultActions notifier(NotificationSignee notification) throws Exception {
        return mvc.perform(post(NOTIFICATION).contentType(MediaType.APPLICATION_JSON).content(notification.corps())
                .header("X-FG-Signature", notification.signature()));
    }

    /** La demande déposée chez l'agrégateur pour ce paiement. */
    private DemandeRecue demandeDe(String paiementId) {
        String reference = jdbc.queryForObject("SELECT reference_operateur FROM abonnements.paiement WHERE id = ?::uuid",
                String.class, paiementId);
        return agregateur.enAttente().stream().filter(d -> d.reference().equals(reference)).findFirst().orElseThrow();
    }

    private List<String> paiements(ParentAvecEnfant famille) {
        return jdbc.queryForList("SELECT p.id::text FROM abonnements.paiement p JOIN abonnements.abonnement a ON a.id = p.abonnement_id"
                + " WHERE a.enfant_id = ?::uuid ORDER BY p.initie_le", String.class, famille.enfantId());
    }

    /** Intervalles portés par les commandes de configuration émises vers le bracelet, dans l'ordre. */
    private List<Integer> configurations(Carte carte) {
        return jdbc.queryForList("SELECT c.message FROM dispositifs.commande c JOIN dispositifs.bracelet b ON b.id = c.bracelet_id"
                + " WHERE b.numero_serie = ? AND c.type = 'CONFIGURATION' ORDER BY c.sequence", String.class, carte.numeroSerie())
                .stream().map(message -> new String(Base64.getUrlDecoder().decode(message.split("\\.")[0]), StandardCharsets.UTF_8))
                .map(corps -> Integer.valueOf(Acteurs.extraire(corps, "\"int\":(\\d+)"))).toList();
    }

    private static String cle() {
        return "essai-" + UUID.randomUUID();
    }

    private ResultActions avec(MockHttpServletRequestBuilder requete, String jeton) throws Exception {
        return mvc.perform(jeton == null ? requete : requete.header("Authorization", "Bearer " + jeton));
    }

    private ResultActions json(MockHttpServletRequestBuilder requete, String corps, String jeton) throws Exception {
        return avec(requete.contentType(MediaType.APPLICATION_JSON).content(corps), jeton);
    }
}
