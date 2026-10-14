package bf.fasoguardian.identite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bf.fasoguardian.Acteurs;
import bf.fasoguardian.Acteurs.Parent;
import bf.fasoguardian.Acteurs.ParentAvecEnfant;
import bf.fasoguardian.TestIntegration;
import bf.fasoguardian.identite.application.InvitationsTuteur;
import bf.fasoguardian.notifications.infrastructure.SmsBacASable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Second tuteur (US-PAR-003) : invitation confirmée par SMS, vérification KYC de l'invité, accès individuel. */
class SecondTuteurIT extends TestIntegration {

    @Autowired
    MockMvc mvc;

    @Autowired
    SmsBacASable sms;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    InvitationsTuteur invitations;

    @Test
    void lInviteVerifieObtientUnAccesIndividuelALEnfant() throws Exception {
        Acteurs acteurs = new Acteurs(mvc, sms);
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        String mariam = famille.parent().jeton();
        String tuteurs = "/api/v1/enfants/" + famille.enfantId() + "/tuteurs";
        Parent ibrahim = acteurs.parent();

        appel(get(tuteurs), mariam).andExpect(status().isOk())
                .andExpect(jsonPath("$.tuteurs.length()").value(1)).andExpect(jsonPath("$.tuteurs[0].prenom").value("Mariam"))
                .andExpect(jsonPath("$.tuteurs[0].nom").value("Ouédraogo"))
                .andExpect(jsonPath("$.tuteurs[0].principal").value(true)).andExpect(jsonPath("$.tuteurs[0].vous").value(true))
                .andExpect(jsonPath("$.invitation").isEmpty()).andExpect(jsonPath("$.peutInviter").value(true));

        // Action sensible : sans code SMS, rien n'est envoyé
        json(post(tuteurs + "/invitation"), "{\"telephone\":\"" + ibrahim.telephone() + "\"}", mariam)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SECOND_FACTEUR_REQUIS"));
        inviter(acteurs, famille, ibrahim.telephone()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE")).andExpect(jsonPath("$.telephone").value("+226" + ibrahim.telephone()));
        assertThat(acteurs.dernierSms(ibrahim.telephone())).contains("Mariam vous invite comme second tuteur").doesNotContain("Awa");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit.entree WHERE action = 'INVITATION_TUTEUR' AND cible_id = ?",
                Long.class, famille.enfantId())).isEqualTo(1);
        // Le numéro n'est gardé que chiffré
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identite.invitation_tuteur WHERE enfant_id = ?::uuid"
                + " AND encode(telephone_chiffre, 'escape') LIKE ?", Long.class, famille.enfantId(), "%" + ibrahim.telephone() + "%")).isZero();
        appel(get(tuteurs), mariam).andExpect(jsonPath("$.invitation.statut").value("EN_ATTENTE"))
                .andExpect(jsonPath("$.peutInviter").value(false));

        // L'invité voit l'invitation, sans rien de l'enfant, et n'y accède pas avant sa vérification
        String invitation = Acteurs.extraire(appel(get("/api/v1/moi/invitations"), ibrahim.jeton()).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].invitant").value("Mariam")).andReturn().getResponse().getContentAsString(),
                "\"id\":\"([0-9a-f-]{36})\"");
        appel(get("/api/v1/enfants/" + famille.enfantId()), ibrahim.jeton()).andExpect(status().isNotFound());
        String dossier = ouvrirSurInvitation(ibrahim, invitation).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String dossierId = Acteurs.extraire(dossier, "\"id\":\"([0-9a-f-]{36})\"");
        appel(post("/api/v1/kyc/dossiers/" + dossierId + "/depot"), ibrahim.jeton()).andExpect(status().isOk());
        appel(get("/api/v1/moi/invitations"), ibrahim.jeton()).andExpect(jsonPath("$.length()").value(0));
        appel(get(tuteurs), mariam).andExpect(jsonPath("$.invitation.statut").value("EN_VERIFICATION"));

        // L'agent voit qu'il s'agit d'un second tuteur et l'enfant déjà suivi
        String kyc = acteurs.agent("[\"KYC\"]").jeton();
        String base = "/api/v1/console/kyc/dossiers/" + dossierId;
        appel(post(base + "/prise-en-charge"), kyc).andExpect(status().isOk());
        appel(get(base), kyc).andExpect(jsonPath("$.secondTuteur").value(true)).andExpect(jsonPath("$.enfant.prenom").value("Awa"));
        json(post(base + "/decision"), "{\"decision\":\"APPROUVER\"}", kyc).andExpect(status().isOk());

        appel(get("/api/v1/enfants/" + famille.enfantId()), ibrahim.jeton()).andExpect(status().isOk())
                .andExpect(jsonPath("$.prenom").value("Awa"));
        acteurs.attendreSms(famille.parent().telephone(), "second tuteur que vous avez invité est vérifié");
        appel(get(tuteurs), mariam).andExpect(jsonPath("$.tuteurs.length()").value(2))
                .andExpect(jsonPath("$.tuteurs[1].principal").value(false)).andExpect(jsonPath("$.tuteurs[1].vous").value(false))
                .andExpect(jsonPath("$.tuteurs[1].telephone").value("+226" + ibrahim.telephone()))
                .andExpect(jsonPath("$.invitation").isEmpty()).andExpect(jsonPath("$.peutInviter").value(false));
        appel(get(tuteurs), ibrahim.jeton()).andExpect(jsonPath("$.tuteurs[1].vous").value(true));
        // Le numéro de l'invité ne reste pas une fois l'invitation close
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identite.invitation_tuteur WHERE enfant_id = ?::uuid"
                + " AND statut = 'ACCEPTEE' AND telephone_chiffre IS NULL", Long.class, famille.enfantId())).isEqualTo(1);

        // Deux tuteurs au plus : refus avant même de demander le code
        json(post(tuteurs + "/invitation"), "{\"telephone\":\"" + acteurs.parent().telephone() + "\"}", mariam)
                .andExpect(status().isConflict());
    }

    @Test
    void unTiersNeVoitNiLesTuteursNiLInvitationEtLesRefusSontJournalises() throws Exception {
        Acteurs acteurs = new Acteurs(mvc, sms);
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        String tuteurs = "/api/v1/enfants/" + famille.enfantId() + "/tuteurs";
        Parent etranger = acteurs.parent();
        Parent invite = acteurs.parent();

        appel(get(tuteurs), etranger.jeton()).andExpect(status().isNotFound());
        json(post(tuteurs + "/invitation"), "{\"telephone\":\"" + invite.telephone() + "\",\"codeSecondFacteur\":\"123456\"}",
                etranger.jeton()).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit.entree WHERE action = 'ACCES_REFUSE' AND acteur_id = ?::uuid"
                + " AND cible_id = ?", Long.class, sujet(etranger), famille.enfantId()))
                .isEqualTo(2);
        // Le numéro d'un tuteur de l'enfant ne s'invite pas
        json(post(tuteurs + "/invitation"), "{\"telephone\":\"" + famille.parent().telephone() + "\"}", famille.parent().jeton())
                .andExpect(status().isConflict());

        inviter(acteurs, famille, invite.telephone()).andExpect(status().isCreated());
        String invitation = Acteurs.extraire(appel(get("/api/v1/moi/invitations"), invite.jeton())
                .andReturn().getResponse().getContentAsString(), "\"id\":\"([0-9a-f-]{36})\"");
        // Une invitation adressée à un autre numéro ne sert à personne d'autre
        appel(get("/api/v1/moi/invitations"), etranger.jeton()).andExpect(jsonPath("$.length()").value(0));
        ouvrirSurInvitation(etranger, invitation).andExpect(status().isNotFound());
    }

    @Test
    void uneInvitationAnnuleeNePeutPlusEtreApprouveeEtUneInvitationEchueExpire() throws Exception {
        Acteurs acteurs = new Acteurs(mvc, sms);
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        String tuteurs = "/api/v1/enfants/" + famille.enfantId() + "/tuteurs";
        Parent invite = acteurs.parent();

        inviter(acteurs, famille, invite.telephone()).andExpect(status().isCreated());
        String invitation = Acteurs.extraire(appel(get("/api/v1/moi/invitations"), invite.jeton())
                .andReturn().getResponse().getContentAsString(), "\"id\":\"([0-9a-f-]{36})\"");
        String dossierId = Acteurs.extraire(ouvrirSurInvitation(invite, invitation).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "\"id\":\"([0-9a-f-]{36})\"");
        appel(post("/api/v1/kyc/dossiers/" + dossierId + "/depot"), invite.jeton()).andExpect(status().isOk());

        // Le tuteur se ravise avant la décision : l'agent ne peut plus que rejeter
        appel(delete(tuteurs + "/invitation"), famille.parent().jeton()).andExpect(status().isNoContent());
        String kyc = acteurs.agent("[\"KYC\"]").jeton();
        String base = "/api/v1/console/kyc/dossiers/" + dossierId;
        appel(post(base + "/prise-en-charge"), kyc).andExpect(status().isOk());
        json(post(base + "/decision"), "{\"decision\":\"APPROUVER\"}", kyc).andExpect(status().isConflict());
        json(post(base + "/decision"), "{\"decision\":\"REJETER\",\"motif\":\"Invitation retirée par le tuteur.\"}", kyc)
                .andExpect(status().isOk());
        appel(get("/api/v1/enfants/" + famille.enfantId()), invite.jeton()).andExpect(status().isNotFound());

        // Une nouvelle invitation, laissée sans suite au-delà de sept jours, expire et perd le numéro
        Parent autre = acteurs.parent();
        inviter(acteurs, famille, autre.telephone()).andExpect(status().isCreated());
        jdbc.update("UPDATE identite.invitation_tuteur SET expire_le = now() - INTERVAL '1 minute'"
                + " WHERE enfant_id = ?::uuid AND statut = 'EN_ATTENTE'", famille.enfantId());
        appel(get("/api/v1/moi/invitations"), autre.jeton()).andExpect(jsonPath("$.length()").value(0));
        appel(get(tuteurs), famille.parent().jeton()).andExpect(jsonPath("$.invitation").isEmpty())
                .andExpect(jsonPath("$.peutInviter").value(true));
        invitations.purger();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identite.invitation_tuteur WHERE enfant_id = ?::uuid"
                + " AND statut = 'EXPIREE' AND telephone_hash IS NULL", Long.class, famille.enfantId())).isEqualTo(1);
        // Une invitation échue n'empêche pas d'en envoyer une autre
        inviter(acteurs, famille, autre.telephone()).andExpect(status().isCreated());
    }

    @Test
    void leContratOpenApiDecritLesTuteursEtLInvitation() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/enfants/{enfantId}/tuteurs'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/enfants/{enfantId}/tuteurs/invitation'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/enfants/{enfantId}/tuteurs/invitation'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/v1/moi/invitations'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/kyc/dossiers/invitation'].post").exists())
                .andExpect(jsonPath("$.components.schemas.TuteursEnfant.properties.peutInviter").exists());
    }

    private ResultActions inviter(Acteurs acteurs, ParentAvecEnfant famille, String telephone) throws Exception {
        String jeton = famille.parent().jeton();
        json(post("/api/v1/moi/second-facteur"), "{\"action\":\"AJOUTER_TUTEUR\"}", jeton).andExpect(status().isAccepted());
        String code = acteurs.dernierCodeDeConfirmation(famille.parent().telephone());
        return json(post("/api/v1/enfants/" + famille.enfantId() + "/tuteurs/invitation"),
                "{\"telephone\":\"" + telephone + "\",\"codeSecondFacteur\":\"" + code + "\"}", jeton);
    }

    private ResultActions ouvrirSurInvitation(Parent invite, String invitation) throws Exception {
        return json(post("/api/v1/kyc/dossiers/invitation"), """
                {"invitationId":"%s","canal":"POINT_INSCRIPTION","natureLien":"PARENT",
                 "demandeur":{"nom":"Ouédraogo","prenoms":"Ibrahim","typePiece":"CNIB","numeroPiece":"B87654321"}}
                """.formatted(invitation), invite.jeton());
    }

    /** Identifiant du compte, lu dans le jeton d'accès. */
    private static String sujet(Parent parent) {
        String charge = new String(java.util.Base64.getUrlDecoder().decode(parent.jeton().split("\\.")[1]));
        return Acteurs.extraire(charge, "\"sub\":\"([0-9a-f-]{36})\"");
    }

    private ResultActions appel(MockHttpServletRequestBuilder requete, String jeton) throws Exception {
        return mvc.perform(requete.header("Authorization", "Bearer " + jeton));
    }

    private ResultActions json(MockHttpServletRequestBuilder requete, String corps, String jeton) throws Exception {
        return appel(requete.contentType(MediaType.APPLICATION_JSON).content(corps), jeton);
    }
}
