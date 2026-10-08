package bf.fasoguardian.identite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;

import bf.fasoguardian.Acteurs;
import bf.fasoguardian.Acteurs.Agent;
import bf.fasoguardian.Acteurs.Parent;
import bf.fasoguardian.TestIntegration;
import bf.fasoguardian.notifications.infrastructure.SmsBacASable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Vérification KYC du lien de filiation ou de tutelle (US-PAR-001, REQ-SYS-013 ; cloisonnement REQ-SYS-016). */
@RecordApplicationEvents
class KycIT extends TestIntegration {

    private static final byte[] JPEG = concat(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0},
            "photo de la pièce de MARQUEUR-CNIB".getBytes(StandardCharsets.UTF_8));
    private static final byte[] PDF = "%PDF-1.7 acte de naissance MARQUEUR-ACTE".getBytes(StandardCharsets.UTF_8);

    @Autowired
    MockMvc mvc;

    @Autowired
    SmsBacASable sms;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ApplicationEvents evenements;

    @Test
    void unDossierCompletValideParLAgentActiveLeCompteEtInformeLeParent() throws Exception {
        Acteurs acteurs = new Acteurs(mvc, sms);
        Parent parent = acteurs.parent();
        Agent agent = acteurs.agent("[\"KYC\"]");
        String dossier = deposerDossierComplet(parent);

        mvc.perform(get("/api/v1/console/kyc/dossiers").header("Authorization", "Bearer " + agent.jeton()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elements[?(@.id == '" + dossier + "')].statut").value("DEPOSE"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Ouédraogo"))));
        agentPost(agent, dossier, "prise-en-charge", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EN_INSTRUCTION"))
                .andExpect(jsonPath("$.demandeur.nom").value("Ouédraogo"))
                .andExpect(jsonPath("$.enfant.prenom").value("Awa"));
        agentPost(agent, dossier, "decision", "{\"decision\":\"APPROUVER\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("APPROUVE"));

        mvc.perform(get("/api/v1/moi").header("Authorization", "Bearer " + parent.jeton()))
                .andExpect(jsonPath("$.statut").value("ACTIF"));
        assertThat(acteurs.dernierSms(parent.telephone())).contains("votre compte est activé").doesNotContain("Awa");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM identite.lien_tutelle l JOIN identite.dossier_kyc d ON d.enfant_id = l.enfant_id
                WHERE d.id = ?::uuid AND l.statut = 'ACTIF' AND l.nature = 'PARENT'
                """, Long.class, dossier)).isEqualTo(1);
        assertThat(evenements.stream(DossierKycApprouve.class))
                .anyMatch(evenement -> evenement.dossierId().toString().equals(dossier));
        assertThat(actionsJournalisees(dossier))
                .contains("PRISE_EN_CHARGE_KYC", "DECISION_KYC_APPROUVE");
    }

    @Test
    void unDossierIncompletNEstPasDeposeEtUnComplementDemandeLaisseLeCompteEnInstruction() throws Exception {
        Acteurs acteurs = new Acteurs(mvc, sms);
        Parent parent = acteurs.parent();
        Agent agent = acteurs.agent("[\"KYC\"]");
        String dossier = ouvrirDossier(parent, "EN_LIGNE", "PARENT");
        ajouterPiece(parent, dossier, "PIECE_RECTO", JPEG, "image/jpeg").andExpect(status().isCreated());

        parentPost(parent, dossier, "depot").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DOSSIER_INCOMPLET"));
        ajouterPiece(parent, dossier, "ACTE_NAISSANCE", PDF, "application/pdf").andExpect(status().isCreated());
        parentPost(parent, dossier, "depot").andExpect(status().isOk());
        agentPost(agent, dossier, "prise-en-charge", null).andExpect(status().isOk());

        agentPost(agent, dossier, "decision", "{\"decision\":\"DEMANDER_COMPLEMENT\"}")
                .andExpect(status().isBadRequest());
        agentPost(agent, dossier, "decision",
                "{\"decision\":\"DEMANDER_COMPLEMENT\",\"motif\":\"Le verso de la pièce est illisible.\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("COMPLEMENT_DEMANDE"));

        mvc.perform(get("/api/v1/moi").header("Authorization", "Bearer " + parent.jeton()))
                .andExpect(jsonPath("$.statut").value("EN_INSTRUCTION"));
        mvc.perform(get("/api/v1/kyc/dossiers/courant").header("Authorization", "Bearer " + parent.jeton()))
                .andExpect(jsonPath("$.statut").value("COMPLEMENT_DEMANDE"))
                .andExpect(jsonPath("$.motif").value("Le verso de la pièce est illisible."));
        assertThat(acteurs.dernierSms(parent.telephone())).contains("complément").doesNotContain("illisible");
        assertThat(actionsJournalisees(dossier)).contains("DECISION_KYC_COMPLEMENT_DEMANDE");

        // Le parent complète puis redépose ; l'agent peut alors rejeter avec un motif.
        ajouterPiece(parent, dossier, "PIECE_VERSO", JPEG, "image/jpeg").andExpect(status().isCreated());
        parentPost(parent, dossier, "depot").andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("DEPOSE"));
        agentPost(agent, dossier, "prise-en-charge", null).andExpect(status().isOk());
        agentPost(agent, dossier, "decision", "{\"decision\":\"REJETER\",\"motif\":\"Lien non établi.\"}")
                .andExpect(jsonPath("$.statut").value("REJETE"));
        agentPost(agent, dossier, "decision", "{\"decision\":\"APPROUVER\"}").andExpect(status().isConflict());
    }

    @Test
    void lesPiecesSontChiffreesEnBaseEtLisiblesParLaSeuleEquipeDInstruction() throws Exception {
        Acteurs acteurs = new Acteurs(mvc, sms);
        Parent parent = acteurs.parent();
        Agent kyc = acteurs.agent("[\"KYC\"]");
        Agent support = acteurs.agent("[\"SUPPORT\"]");
        String dossier = deposerDossierComplet(parent);
        String piece = jdbc.queryForObject(
                "SELECT id::text FROM identite.piece_justificative WHERE dossier_id = ?::uuid AND type = 'PIECE_RECTO'",
                String.class, dossier);

        assertThat(jdbc.queryForList("SELECT contenu_chiffre FROM identite.piece_justificative WHERE dossier_id = ?::uuid",
                byte[].class, dossier)).allSatisfy(octets ->
                assertThat(new String(octets, StandardCharsets.ISO_8859_1)).doesNotContain("MARQUEUR"));
        assertThat(jdbc.queryForObject(
                "SELECT encode(identite_chiffree || enfant_chiffre, 'escape') FROM identite.dossier_kyc WHERE id = ?::uuid",
                String.class, dossier)).doesNotContain("Ouédraogo", "Awa", "B12345678");

        String chemin = "/api/v1/console/kyc/dossiers/" + dossier + "/pieces/" + piece;
        mvc.perform(get(chemin).header("Authorization", "Bearer " + kyc.jeton()))
                .andExpect(status().isOk()).andExpect(content().bytes(JPEG))
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get(chemin).header("Authorization", "Bearer " + support.jeton()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCES_REFUSE"));
        mvc.perform(get(chemin).header("Authorization", "Bearer " + parent.jeton())).andExpect(status().isForbidden());
        mvc.perform(get(chemin)).andExpect(status().isUnauthorized());

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit.entree WHERE action = 'CONSULTATION_PIECE_KYC' AND cible_id = ?", Long.class,
                piece)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit.entree
                WHERE action = 'ACCES_REFUSE' AND role = 'SUPPORT' AND cible_id = ?
                """, Long.class, "GET " + chemin)).isEqualTo(1);
    }

    @Test
    void unParentNeVoitNiNeModifieLeDossierDUnAutre() throws Exception {
        Acteurs acteurs = new Acteurs(mvc, sms);
        Parent titulaire = acteurs.parent();
        Parent autre = acteurs.parent();
        String dossier = ouvrirDossier(titulaire, "EN_LIGNE", "PARENT");

        ajouterPiece(autre, dossier, "PIECE_RECTO", JPEG, "image/jpeg").andExpect(status().isNotFound());
        parentPost(autre, dossier, "depot").andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/kyc/dossiers/courant").header("Authorization", "Bearer " + autre.jeton()))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit.entree
                WHERE action = 'ACCES_REFUSE' AND type_cible = 'DOSSIER_KYC' AND cible_id = ? AND resultat = 'REFUS'
                """, Long.class, dossier)).isEqualTo(2);
        ouvrir(titulaire, "EN_LIGNE", "PARENT").andExpect(status().isConflict());
    }

    @Test
    void unFichierDontLeContenuNeCorrespondPasAuFormatAnnonceEstRefuse() throws Exception {
        Parent parent = new Acteurs(mvc, sms).parent();
        String dossier = ouvrirDossier(parent, "EN_LIGNE", "TUTEUR");

        ajouterPiece(parent, dossier, "PIECE_RECTO", "MZ exécutable déguisé".getBytes(StandardCharsets.UTF_8), "image/jpeg")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PIECE_REFUSEE"));
        ajouterPiece(parent, dossier, "PIECE_RECTO", JPEG, "text/html").andExpect(status().isBadRequest());

        // Un tuteur doit joindre le jugement de tutelle : l'acte de naissance ne suffit pas.
        ajouterPiece(parent, dossier, "PIECE_RECTO", JPEG, "image/jpeg").andExpect(status().isCreated());
        ajouterPiece(parent, dossier, "ACTE_NAISSANCE", PDF, "application/pdf").andExpect(status().isCreated());
        parentPost(parent, dossier, "depot").andExpect(jsonPath("$.code").value("DOSSIER_INCOMPLET"));
        ajouterPiece(parent, dossier, "JUGEMENT_TUTELLE", PDF, "application/pdf").andExpect(status().isCreated());
        parentPost(parent, dossier, "depot").andExpect(status().isOk());
    }

    @Test
    void enPointDInscriptionLeDossierSeDeposeSansPieceNumerisee() throws Exception {
        Parent parent = new Acteurs(mvc, sms).parent();
        String dossier = ouvrirDossier(parent, "POINT_INSCRIPTION", "PARENT");

        parentPost(parent, dossier, "depot").andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("DEPOSE"))
                .andExpect(jsonPath("$.reference").value(org.hamcrest.Matchers.startsWith("KYC-")));
    }

    private String deposerDossierComplet(Parent parent) throws Exception {
        String dossier = ouvrirDossier(parent, "EN_LIGNE", "PARENT");
        ajouterPiece(parent, dossier, "PIECE_RECTO", JPEG, "image/jpeg").andExpect(status().isCreated());
        ajouterPiece(parent, dossier, "ACTE_NAISSANCE", PDF, "application/pdf").andExpect(status().isCreated());
        parentPost(parent, dossier, "depot").andExpect(status().isOk());
        return dossier;
    }

    private String ouvrirDossier(Parent parent, String canal, String nature) throws Exception {
        return Acteurs.extraire(ouvrir(parent, canal, nature).andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("BROUILLON"))
                .andReturn().getResponse().getContentAsString(), "\"id\":\"([0-9a-f-]{36})\"");
    }

    private ResultActions ouvrir(Parent parent, String canal, String nature) throws Exception {
        return mvc.perform(post("/api/v1/kyc/dossiers").header("Authorization", "Bearer " + parent.jeton())
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"canal":"%s","natureLien":"%s",
                         "demandeur":{"nom":"Ouédraogo","prenoms":"Mariam","typePiece":"CNIB","numeroPiece":"B12345678"},
                         "enfant":{"prenom":"Awa","nom":"Ouédraogo","dateNaissance":"2018-03-14"}}
                        """.formatted(canal, nature)));
    }

    private ResultActions ajouterPiece(Parent parent, String dossier, String type, byte[] contenu, String mime)
            throws Exception {
        return mvc.perform(multipart("/api/v1/kyc/dossiers/" + dossier + "/pieces")
                .file(new MockMultipartFile("fichier", "piece", mime, contenu)).param("type", type)
                .header("Authorization", "Bearer " + parent.jeton()));
    }

    private ResultActions parentPost(Parent parent, String dossier, String action) throws Exception {
        return mvc.perform(post("/api/v1/kyc/dossiers/" + dossier + "/" + action)
                .header("Authorization", "Bearer " + parent.jeton()));
    }

    private ResultActions agentPost(Agent agent, String dossier, String action, String corps) throws Exception {
        var requete = post("/api/v1/console/kyc/dossiers/" + dossier + "/" + action)
                .header("Authorization", "Bearer " + agent.jeton());
        return mvc.perform(corps == null ? requete : requete.contentType(MediaType.APPLICATION_JSON).content(corps));
    }

    private List<String> actionsJournalisees(String dossier) {
        return jdbc.queryForList("SELECT action FROM audit.entree WHERE type_cible = 'DOSSIER_KYC' AND cible_id = ?",
                String.class, dossier);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] resultat = new byte[a.length + b.length];
        System.arraycopy(a, 0, resultat, 0, a.length);
        System.arraycopy(b, 0, resultat, a.length, b.length);
        return resultat;
    }
}
