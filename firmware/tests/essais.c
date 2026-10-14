/* Essais du noyau sur machine hôte. Chaque essai part d'un port remis à zéro. Sortie non nulle au premier échec. */
#include <stdio.h>
#include <string.h>

#include "../noyau/fg_bracelet.h"
#include "../noyau/fg_codage.h"
#include "../noyau/fg_messages.h"
#include "port_hote.h"

static int echecs;
static int verifications;

#define VRAI(condition) \
    do { \
        verifications++; \
        if (!(condition)) { \
            echecs++; \
            printf("  ECHEC %s:%d : %s\n", __FILE__, __LINE__, #condition); \
        } \
    } while (0)

#define TEXTE(obtenu, attendu) \
    do { \
        verifications++; \
        const char *o_ = (obtenu); \
        if (o_ == NULL || strcmp(o_, (attendu)) != 0) { \
            echecs++; \
            printf("  ECHEC %s:%d\n    attendu : %s\n    obtenu  : %s\n", __FILE__, __LINE__, (attendu), o_ == NULL ? "(rien)" : o_); \
        } \
    } while (0)

#define CONTIENT(texte, morceau) VRAI((texte) != NULL && strstr((texte), (morceau)) != NULL)

#define ESSAI(nom) \
    static void nom(void); \
    static void lancer_##nom(void) \
    { \
        hote_raz(); \
        printf("- %s\n", #nom); \
        nom(); \
    } \
    static void nom(void)

static const char ID_1[] = "3f0c2b9e-4f55-4c0e-9d3a-0e8a1b2c3d4e";
static const char ID_2[] = "7a1d9c20-1b2c-4d3e-8f4a-5b6c7d8e9f00";

/* Fabrique le message « base64url(corps).base64url(signature) » d'une commande. */
static const char *commande(const char *id, const char *dev, const char *cmd, unsigned long long n, unsigned long exp, const char *p)
{
    static char message[900];
    char corps[400];
    int taille = snprintf(corps, sizeof corps, "{\"id\":\"%s\",\"dev\":\"%s\",\"cmd\":\"%s\",\"n\":%llu,\"exp\":%lu,\"p\":%s}", id, dev, cmd, n, exp, p);
    int ecrit = fg_base64url_encoder((const uint8_t *)corps, (size_t)taille, message, sizeof message);
    message[ecrit++] = '.';
    uint8_t signature[FG_SIGNATURE_TAILLE] = {0};
    fg_base64url_encoder(signature, sizeof signature, message + ecrit, sizeof message - (size_t)ecrit);
    return message;
}

static void secondes(fg_bracelet_t *b, uint32_t duree)
{
    for (uint32_t i = 0; i < duree; i++) {
        hote.heure++;
        fg_bracelet_tic(b, hote.heure);
    }
}

/* ------------------------------------------------------------------ codages */

ESSAI(base64url_sans_remplissage)
{
    char texte[32];
    VRAI(fg_base64url_encoder((const uint8_t *)"\xfb\xff\xfe", 3, texte, sizeof texte) == 4);
    TEXTE(texte, "-__-");
    VRAI(fg_base64url_encoder((const uint8_t *)"ab", 2, texte, sizeof texte) == 3);
    TEXTE(texte, "YWI");
    uint8_t octets[8];
    VRAI(fg_base64url_decoder("YWI", 3, octets, sizeof octets) == 2 && octets[0] == 'a' && octets[1] == 'b');
    VRAI(fg_base64url_decoder("YW=I", 4, octets, sizeof octets) == -1);
    VRAI(fg_base64url_decoder("Y", 1, octets, sizeof octets) == -1);
    VRAI(fg_base64url_encoder((const uint8_t *)"abc", 3, texte, 4) == -1);
}

ESSAI(sha256_vecteurs_de_reference)
{
    static const char *MESSAGES[] = {"abc", "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"};
    static const char *ATTENDUS[] = {"ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                                     "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1"};
    for (int i = 0; i < 2; i++) {
        fg_sha256_t calcul;
        uint8_t empreinte[32];
        uint8_t attendue[32];
        fg_sha256_debut(&calcul);
        /* En deux morceaux, comme une image reçue par blocs. */
        size_t moitie = strlen(MESSAGES[i]) / 2;
        fg_sha256_ajouter(&calcul, (const uint8_t *)MESSAGES[i], moitie);
        fg_sha256_ajouter(&calcul, (const uint8_t *)MESSAGES[i] + moitie, strlen(MESSAGES[i]) - moitie);
        fg_sha256_fin(&calcul, empreinte);
        VRAI(fg_hex_vers_octets(ATTENDUS[i], attendue, 32));
        VRAI(memcmp(empreinte, attendue, 32) == 0);
    }
}

ESSAI(lecture_des_champs_json)
{
    const char *json = "{\"id\":\"abc\",\"dev\":\"FG-2291\",\"n\":1791540000123,\"p\":{\"on\":1,\"until\":0,\"v\":\"2.4.2\"}}";
    char texte[16];
    int64_t nombre;
    VRAI(fg_json_texte(json, "dev", texte, sizeof texte));
    TEXTE(texte, "FG-2291");
    VRAI(fg_json_texte(json, "v", texte, sizeof texte));
    TEXTE(texte, "2.4.2");
    VRAI(fg_json_entier(json, "n", &nombre) && nombre == 1791540000123LL);
    VRAI(fg_json_entier(json, "until", &nombre) && nombre == 0);
    VRAI(!fg_json_entier(json, "absent", &nombre));
    VRAI(!fg_json_texte(json, "n", texte, sizeof texte));
    VRAI(!fg_json_texte(json, "dev", texte, 4));
}

/* ------------------------------------------------------------------ messages */

ESSAI(messages_conformes_au_protocole)
{
    char message[FG_MESSAGE_MAX];
    fg_position_t position = {true, 1759651200, 1237140, -151970, 8, FG_SRC_GNSS};
    fg_radio_t etat = {76, -79, FG_NET_4G, "Orange BF", true};
    VRAI(fg_msg_telemetrie(message, sizeof message, &position, 4812, &etat) > 0);
    TEXTE(message, "{\"t\":1759651200,\"seq\":4812,\"lat\":12.37140,\"lon\":-1.51970,\"acc\":8,\"src\":\"gnss\",\"bat\":76,\"rssi\":-79,\"net\":\"4g\",\"op\":\"Orange BF\",\"mv\":1}");

    /* Une latitude sous un degré garde son zéro et son signe. */
    fg_position_t sud = {true, 10, -5, 99999, 30, FG_SRC_CELL};
    fg_radio_t muet = {-1, 0, FG_NET_AUCUN, NULL, false};
    VRAI(fg_msg_telemetrie(message, sizeof message, &sud, 1, &muet) > 0);
    TEXTE(message, "{\"t\":10,\"seq\":1,\"lat\":-0.00005,\"lon\":0.99999,\"acc\":30,\"src\":\"cell\",\"mv\":0}");

    VRAI(fg_msg_alerte(message, sizeof message, 1759651200, 4813, FG_EV_SOS, &position) > 0);
    TEXTE(message, "{\"t\":1759651200,\"seq\":4813,\"ev\":\"sos\",\"lat\":12.37140,\"lon\":-1.51970,\"acc\":8}");
    VRAI(fg_msg_alerte(message, sizeof message, 5, 6, FG_EV_STRAP, NULL) > 0);
    TEXTE(message, "{\"t\":5,\"seq\":6,\"ev\":\"strap\"}");

    VRAI(fg_msg_etat(message, sizeof message, true, "2.4.1") > 0);
    TEXTE(message, "{\"online\":true,\"fw\":\"2.4.1\"}");
    VRAI(fg_msg_accuse(message, sizeof message, ID_1, false) > 0);
    TEXTE(message, "{\"id\":\"3f0c2b9e-4f55-4c0e-9d3a-0e8a1b2c3d4e\",\"ok\":false}");
    /* Un message qui ne tient pas est refusé, jamais tronqué. */
    VRAI(fg_msg_telemetrie(message, 40, &position, 1, &etat) == -1);
    /* Un opérateur au nom piégé n'entre pas dans le message. */
    fg_radio_t piege = {76, -79, FG_NET_2G, "Op\"x", false};
    VRAI(fg_msg_telemetrie(message, sizeof message, &position, 1, &piege) > 0 && strstr(message, "\"op\"") == NULL);
}

ESSAI(sms_de_repli_signe_sur_tout_ce_qui_precede)
{
    char sms[200];
    fg_position_t position = {true, 1759651100, 1237140, -151970, 8, FG_SRC_GNSS};
    int longueur = fg_sms_alerte(sms, sizeof sms, "FG-2291", FG_EV_SOS, &position, 76, 1759651200, fg_port_signer);
    VRAI(longueur > 0 && longueur <= 160);
    const char *debut = "FG1|FG-2291|ALR|SOS|12.37140,-1.51970|G|76|1759651200|";
    VRAI(strncmp(sms, debut, strlen(debut)) == 0);
    /* 64 octets de signature : 86 caractères en base64url sans remplissage. */
    VRAI(strlen(sms) == strlen(debut) + 86);
    uint8_t attendue[FG_SIGNATURE_TAILLE];
    uint8_t lue[FG_SIGNATURE_TAILLE];
    fg_port_signer((const uint8_t *)debut, strlen(debut), attendue);
    VRAI(fg_base64url_decoder(sms + strlen(debut), 86, lue, sizeof lue) == FG_SIGNATURE_TAILLE && memcmp(lue, attendue, sizeof lue) == 0);

    /* Sans position connue, le champ reste vide. */
    VRAI(fg_sms_alerte(sms, sizeof sms, "FG-2291", FG_EV_BATCRIT, NULL, 7, 1759651200, fg_port_signer) > 0);
    VRAI(strncmp(sms, "FG1|FG-2291|ALR|BATCRIT||G|7|1759651200|", 40) == 0);
    /* Les événements qui ne sont pas des alertes ne partent pas par SMS. */
    VRAI(fg_sms_alerte(sms, sizeof sms, "FG-2291", FG_EV_WORN, &position, 76, 1, fg_port_signer) == -1);
    VRAI(fg_sms_alerte(sms, sizeof sms, "FG-2291", FG_EV_CHARGE, &position, 76, 1, fg_port_signer) == -1);
}

/* ------------------------------------------------------------------ commandes */

ESSAI(une_commande_n_est_executee_que_si_tous_les_controles_passent)
{
    fg_verificateur_t v;
    static fg_resultat_t r;
    fg_cmd_init(&v, "FG-2291");
    uint32_t maintenant = hote.heure;

    fg_cmd_verifier(&v, commande(ID_1, "FG-2291", "alert", 1000, maintenant + 900, "{\"on\":1}"), maintenant, &r);
    VRAI(r.rejet == FG_REJET_AUCUN && r.code == FG_CMD_ALERT && fg_cmd_accuse_positif(&r));
    TEXTE(r.id, ID_1);

    /* Réémission de la même commande : accusée de nouveau, pas rejouée. */
    fg_cmd_verifier(&v, commande(ID_1, "FG-2291", "alert", 1000, maintenant + 900, "{\"on\":1}"), maintenant, &r);
    VRAI(r.rejet == FG_REJET_REJEU && r.deja_executee && fg_cmd_accuse_positif(&r));

    /* Numéro déjà dépassé sous un autre identifiant : rejeu pur, refusé. */
    fg_cmd_verifier(&v, commande(ID_2, "FG-2291", "loc", 999, maintenant + 900, "{}"), maintenant, &r);
    VRAI(r.rejet == FG_REJET_REJEU && !r.deja_executee && !fg_cmd_accuse_positif(&r));

    fg_cmd_verifier(&v, commande(ID_2, "FG-0001", "loc", 2000, maintenant + 900, "{}"), maintenant, &r);
    VRAI(r.rejet == FG_REJET_DESTINATAIRE);
    fg_cmd_verifier(&v, commande(ID_2, "FG-2291", "loc", 2000, maintenant, "{}"), maintenant, &r);
    VRAI(r.rejet == FG_REJET_EXPIREE);

    hote.signature_plateforme_valide = false;
    fg_cmd_verifier(&v, commande(ID_2, "FG-2291", "loc", 2000, maintenant + 900, "{}"), maintenant, &r);
    VRAI(r.rejet == FG_REJET_SIGNATURE && !fg_cmd_accuse_positif(&r));
    TEXTE(r.id, ID_2);
    hote.signature_plateforme_valide = true;

    fg_cmd_verifier(&v, "pas une commande", maintenant, &r);
    VRAI(r.rejet == FG_REJET_FORMAT && r.id[0] == '\0');
    fg_cmd_verifier(&v, "YWJj.YWJj", maintenant, &r);
    VRAI(r.rejet == FG_REJET_FORMAT);

    /* Les refus n'ont pas avancé le compteur : la commande légitime passe toujours. */
    fg_cmd_verifier(&v, commande(ID_2, "FG-2291", "loc", 2000, maintenant + 900, "{}"), maintenant, &r);
    VRAI(r.rejet == FG_REJET_AUCUN && r.code == FG_CMD_LOC);
}

/* ------------------------------------------------------------------ comportement */

ESSAI(positions_au_rythme_regle_par_la_plateforme)
{
    fg_bracelet_t b;
    fg_bracelet_init(&b, "FG-2291", "2.4.1");
    fg_bracelet_connecte(&b);
    TEXTE(hote_dernier("status"), "{\"online\":true,\"fw\":\"2.4.1\"}");

    secondes(&b, 1);
    VRAI(hote_nombre("telemetry") == 1);
    secondes(&b, 299);
    VRAI(hote_nombre("telemetry") == 1);
    secondes(&b, 1);
    VRAI(hote_nombre("telemetry") == 2);

    /* Intervalle nul : émission suspendue ; « loc » obtient tout de même une position. */
    fg_bracelet_commande(&b, commande(ID_1, "FG-2291", "cfg", 1, hote.heure + 900, "{\"int\":0,\"alr\":60,\"eco\":0}"), hote.heure);
    CONTIENT(hote_dernier("ack"), "\"ok\":true");
    secondes(&b, 1200);
    VRAI(hote_nombre("telemetry") == 2);
    fg_bracelet_commande(&b, commande(ID_2, "FG-2291", "loc", 2, hote.heure + 900, "{}"), hote.heure);
    secondes(&b, 1);
    VRAI(hote_nombre("telemetry") == 3);

    /* Une configuration incohérente est refusée et ne change rien. */
    fg_bracelet_commande(&b, commande("11111111-1111-4111-8111-111111111111", "FG-2291", "cfg", 3, hote.heure + 900, "{\"int\":300,\"alr\":1}"), hote.heure);
    CONTIENT(hote_dernier("ack"), "\"ok\":false");
    VRAI(b.intervalle_s == 0);
}

ESSAI(sos_apres_trois_secondes_d_appui_une_seule_fois)
{
    fg_bracelet_t b;
    fg_bracelet_init(&b, "FG-2291", "2.4.1");
    fg_bracelet_bouton(&b, true, 10000, hote.heure);
    fg_bracelet_bouton(&b, true, 12900, hote.heure);
    VRAI(hote_nombre("alert") == 0);
    /* Appui relâché avant trois secondes : rien, et le compte repart de zéro. */
    fg_bracelet_bouton(&b, false, 12950, hote.heure);
    fg_bracelet_bouton(&b, true, 13000, hote.heure);
    fg_bracelet_bouton(&b, true, 15999, hote.heure);
    VRAI(hote_nombre("alert") == 0);
    fg_bracelet_bouton(&b, true, 16000, hote.heure);
    VRAI(hote_nombre("alert") == 1);
    CONTIENT(hote_dernier("alert"), "\"ev\":\"sos\"");
    VRAI(hote.nombre_signaux == 1 && hote.signaux[0] == FG_SIGNAL_SOS_CONFIRME);
    /* Bouton toujours tenu : pas de second SOS. */
    fg_bracelet_bouton(&b, true, 30000, hote.heure);
    VRAI(hote_nombre("alert") == 1);
    /* En alerte, une position toutes les soixante secondes, sans attendre la plateforme. */
    VRAI(b.mode_alerte);
    secondes(&b, 1);
    int avant = hote_nombre("telemetry");
    secondes(&b, 60);
    VRAI(hote_nombre("telemetry") == avant + 1);
    fg_bracelet_commande(&b, commande(ID_1, "FG-2291", "alert", 5, hote.heure + 900, "{\"on\":0}"), hote.heure);
    VRAI(!b.mode_alerte);
}

ESSAI(hors_reseau_les_positions_attendent_avec_leur_heure_d_origine)
{
    fg_bracelet_t b;
    fg_bracelet_init(&b, "FG-2291", "2.4.1");
    fg_bracelet_commande(&b, commande(ID_1, "FG-2291", "cfg", 1, hote.heure + 900, "{\"int\":60,\"alr\":60,\"eco\":0}"), hote.heure);
    hote.donnees = false;
    uint32_t debut = hote.heure;
    secondes(&b, 180);
    VRAI(hote_nombre("telemetry") == 0);
    VRAI(fg_tampon_nombre(&b.tampon) == 3);
    hote.donnees = true;
    secondes(&b, 1);
    VRAI(fg_tampon_nombre(&b.tampon) == 0);
    VRAI(hote_nombre("telemetry") == 3);
    /* La plus ancienne d'abord, à l'heure de sa mesure et non à celle de l'envoi. */
    char attendu[32];
    snprintf(attendu, sizeof attendu, "{\"t\":%lu,\"seq\":1,", (unsigned long)(debut + 60));
    int premier = -1;
    for (int i = 0; i < hote.nombre_publies && premier < 0; i++) {
        if (strcmp(hote.flux[i], "telemetry") == 0) {
            premier = i;
        }
    }
    VRAI(premier >= 0 && strncmp(hote.publies[premier], attendu, strlen(attendu)) == 0);

    /* Tampon plein : les plus anciennes cèdent la place, le nombre reste borné. */
    hote.donnees = false;
    secondes(&b, 60 * (FG_TAMPON_TAILLE + 10));
    VRAI(fg_tampon_nombre(&b.tampon) == FG_TAMPON_TAILLE);
}

ESSAI(sans_donnees_l_alerte_part_par_sms_au_bout_de_vingt_secondes)
{
    fg_bracelet_t b;
    fg_bracelet_init(&b, "FG-2291", "2.4.1");
    secondes(&b, 1);
    hote.donnees = false;
    fg_bracelet_bouton(&b, true, 0, hote.heure);
    fg_bracelet_bouton(&b, true, 3000, hote.heure);
    uint32_t instant = hote.heure;
    secondes(&b, 19);
    VRAI(hote.nombre_sms == 0);
    secondes(&b, 1);
    VRAI(hote.nombre_sms == 1);
    char debut[64];
    snprintf(debut, sizeof debut, "FG1|FG-2291|ALR|SOS|12.37140,-1.51970|G|80|%lu|", (unsigned long)instant);
    VRAI(strncmp(hote.sms[0], debut, strlen(debut)) == 0);
    /* Le SMS vaut l'alerte : au retour du réseau, elle n'est pas émise une seconde fois. */
    hote.donnees = true;
    secondes(&b, 5);
    VRAI(hote_nombre("alert") == 0 && hote.nombre_sms == 1);
}

ESSAI(si_le_reseau_revient_avant_vingt_secondes_pas_de_sms)
{
    fg_bracelet_t b;
    fg_bracelet_init(&b, "FG-2291", "2.4.1");
    hote.donnees = false;
    fg_bracelet_chute(&b, hote.heure);
    secondes(&b, 10);
    hote.donnees = true;
    secondes(&b, 30);
    VRAI(hote.nombre_sms == 0 && hote_nombre("alert") == 1);
    CONTIENT(hote_dernier("alert"), "\"ev\":\"fall\"");

    /* SMS impossible : l'alerte reste en attente et le SMS est retenté. */
    hote.donnees = false;
    hote.sms_reussit = false;
    fg_bracelet_chute(&b, hote.heure);
    secondes(&b, 45);
    VRAI(hote.nombre_sms == 0 && b.alerte.en_attente);
    hote.sms_reussit = true;
    secondes(&b, 20);
    VRAI(hote.nombre_sms == 1 && !b.alerte.en_attente);
}

ESSAI(retrait_fenetre_autorisee_rappel_et_alerte_a_l_echeance)
{
    fg_bracelet_t b;
    fg_bracelet_init(&b, "FG-2291", "2.4.1");
    char fenetre[40];
    snprintf(fenetre, sizeof fenetre, "{\"until\":%lu}", (unsigned long)(hote.heure + 1800));
    fg_bracelet_commande(&b, commande(ID_1, "FG-2291", "rm", 1, hote.heure + 900, fenetre), hote.heure);
    VRAI(hote.signaux[0] == FG_SIGNAL_RETRAIT_AUTORISE);

    /* Le fermoir s'ouvre : l'événement part toujours, la plateforme sait qu'il est autorisé. */
    fg_bracelet_capteurs(&b, false, false, true, hote.heure);
    CONTIENT(hote_dernier("alert"), "\"ev\":\"strap\"");
    VRAI(hote_nombre("alert") == 1);

    secondes(&b, 1499);
    VRAI(hote.nombre_signaux == 1);
    secondes(&b, 1);
    VRAI(hote.nombre_signaux == 2 && hote.signaux[1] == FG_SIGNAL_FIN_DE_RETRAIT);
    /* Échéance, bracelet toujours ôté : l'événement est redit, hors fenêtre cette fois. */
    secondes(&b, 300);
    VRAI(hote_nombre("alert") == 2);
    CONTIENT(hote_dernier("alert"), "\"ev\":\"strap\"");

    /* Remis : « worn », et rien de plus à l'échéance d'une fenêtre suivante. */
    fg_bracelet_capteurs(&b, true, true, false, hote.heure);
    CONTIENT(hote_dernier("alert"), "\"ev\":\"worn\"");
    VRAI(b.fin_retrait == 0);
}

ESSAI(contact_peau_perdu_confirme_par_l_accelerometre_ou_pose_sur_le_chargeur)
{
    fg_bracelet_t b;
    fg_bracelet_init(&b, "FG-2291", "2.4.1");
    /* Contact perdu mais bracelet en mouvement : sangle qui flotte, pas un retrait. */
    fg_bracelet_capteurs(&b, true, false, false, hote.heure);
    VRAI(hote_nombre("alert") == 0);
    fg_bracelet_capteurs(&b, true, false, true, hote.heure);
    CONTIENT(hote_dernier("alert"), "\"ev\":\"skin\"");
    fg_bracelet_capteurs(&b, true, true, false, hote.heure);
    CONTIENT(hote_dernier("alert"), "\"ev\":\"worn\"");

    hote.en_charge = true;
    fg_bracelet_capteurs(&b, true, false, false, hote.heure);
    CONTIENT(hote_dernier("alert"), "\"ev\":\"charge\"");
    VRAI(hote_nombre("alert") == 3);
}

ESSAI(batterie_critique_signalee_une_fois_puis_rearmee)
{
    fg_bracelet_t b;
    fg_bracelet_init(&b, "FG-2291", "2.4.1");
    secondes(&b, 5);
    VRAI(hote_nombre("alert") == 0);
    hote.batterie = 9;
    secondes(&b, 30);
    VRAI(hote_nombre("alert") == 1);
    CONTIENT(hote_dernier("alert"), "\"ev\":\"batcrit\"");
    /* Remontée insuffisante puis rechute : pas de seconde alerte. */
    hote.batterie = 12;
    secondes(&b, 5);
    hote.batterie = 8;
    secondes(&b, 5);
    VRAI(hote_nombre("alert") == 1);
    hote.batterie = 60;
    secondes(&b, 5);
    hote.batterie = 5;
    secondes(&b, 5);
    VRAI(hote_nombre("alert") == 2);
}

ESSAI(une_commande_refusee_est_signalee_et_n_a_aucun_effet)
{
    fg_bracelet_t b;
    fg_bracelet_init(&b, "FG-2291", "2.4.1");
    hote.signature_plateforme_valide = false;
    fg_bracelet_commande(&b, commande(ID_1, "FG-2291", "alert", 1, hote.heure + 900, "{\"on\":1}"), hote.heure);
    TEXTE(hote_dernier("ack"), "{\"id\":\"3f0c2b9e-4f55-4c0e-9d3a-0e8a1b2c3d4e\",\"ok\":false}");
    VRAI(!b.mode_alerte);
    /* Illisible : rien à accuser. */
    fg_bracelet_commande(&b, "n'importe quoi", hote.heure);
    VRAI(hote_nombre("ack") == 1);
}

/* ------------------------------------------------------------------ mise à jour */

static const char IMAGE[] = "image de demonstration du logiciel embarque, version 2.4.2";

static const char *demande_ota(const char *id, unsigned long long n, unsigned long taille, const char *sha)
{
    char p[300];
    char signature[100];
    uint8_t brute[FG_SIGNATURE_TAILLE] = {7};
    fg_base64url_encoder(brute, sizeof brute, signature, sizeof signature);
    snprintf(p, sizeof p, "{\"v\":\"2.4.2\",\"url\":\"https://maj.fasoguardian.test/fg-2.4.2.bin\",\"size\":%lu,\"sha\":\"%s\",\"sig\":\"%s\"}", taille, sha,
             signature);
    return commande(id, "FG-2291", "ota", n, hote.heure + 900, p);
}

static void empreinte_hex(const char *donnees, char hex[65])
{
    fg_sha256_t calcul;
    uint8_t empreinte[32];
    fg_sha256_debut(&calcul);
    fg_sha256_ajouter(&calcul, (const uint8_t *)donnees, strlen(donnees));
    fg_sha256_fin(&calcul, empreinte);
    for (int i = 0; i < 32; i++) {
        snprintf(hex + i * 2, 3, "%02X", empreinte[i]);
    }
}

ESSAI(image_installee_seulement_si_manifeste_signe_et_empreinte_conforme)
{
    fg_bracelet_t b;
    char sha[65];
    empreinte_hex(IMAGE, sha);
    fg_bracelet_init(&b, "FG-2291", "2.4.1");

    /* Manifeste que la clé de publication n'a pas signé : refus, rien n'est ouvert. */
    hote.signature_publication_valide = false;
    fg_bracelet_commande(&b, demande_ota(ID_1, 1, sizeof IMAGE - 1, sha), hote.heure);
    CONTIENT(hote_dernier("ack"), "\"ok\":false");
    VRAI(!hote.ota_ouverte && !b.telechargement_demande);

    hote.signature_publication_valide = true;
    fg_bracelet_commande(&b, demande_ota(ID_2, 2, sizeof IMAGE - 1, sha), hote.heure);
    CONTIENT(hote_dernier("ack"), "\"ok\":true");
    VRAI(hote.ota_ouverte && b.telechargement_demande);
    TEXTE(b.ota.url, "https://maj.fasoguardian.test/fg-2.4.2.bin");

    /* L'image arrive par blocs. */
    VRAI(fg_ota_recevoir(&b.ota, (const uint8_t *)IMAGE, 20) == FG_OTA_OK);
    VRAI(fg_ota_recevoir(&b.ota, (const uint8_t *)IMAGE + 20, sizeof IMAGE - 1 - 20) == FG_OTA_OK);
    VRAI(fg_bracelet_image_recue(&b) == FG_OTA_OK);
    VRAI(hote.ota_validee && !hote.ota_abandonnee && hote.image_taille == sizeof IMAGE - 1);
}

ESSAI(image_alteree_tronquee_ou_trop_longue_abandonnee)
{
    fg_ota_t ota;
    char sha[65];
    char corps[400];
    char signature[100];
    uint8_t brute[FG_SIGNATURE_TAILLE] = {7};
    fg_base64url_encoder(brute, sizeof brute, signature, sizeof signature);
    empreinte_hex(IMAGE, sha);
    snprintf(corps, sizeof corps, "{\"v\":\"2.4.2\",\"url\":\"https://x.test/a.bin\",\"size\":%lu,\"sha\":\"%s\",\"sig\":\"%s\"}", (unsigned long)(sizeof IMAGE - 1), sha,
             signature);

    /* Un octet change : l'empreinte ne correspond plus. */
    char alteree[sizeof IMAGE];
    memcpy(alteree, IMAGE, sizeof IMAGE);
    alteree[3] ^= 1;
    VRAI(fg_ota_commencer(&ota, corps) == FG_OTA_OK);
    VRAI(fg_ota_recevoir(&ota, (const uint8_t *)alteree, sizeof IMAGE - 1) == FG_OTA_OK);
    VRAI(fg_ota_terminer(&ota) == FG_OTA_EMPREINTE);
    VRAI(hote.ota_abandonnee && !hote.ota_validee);

    hote_raz();
    VRAI(fg_ota_commencer(&ota, corps) == FG_OTA_OK);
    VRAI(fg_ota_recevoir(&ota, (const uint8_t *)IMAGE, 10) == FG_OTA_OK);
    VRAI(fg_ota_terminer(&ota) == FG_OTA_TAILLE);

    hote_raz();
    VRAI(fg_ota_commencer(&ota, corps) == FG_OTA_OK);
    VRAI(fg_ota_recevoir(&ota, (const uint8_t *)IMAGE, sizeof IMAGE - 1) == FG_OTA_OK);
    VRAI(fg_ota_recevoir(&ota, (const uint8_t *)"x", 1) == FG_OTA_TAILLE);
    VRAI(hote.ota_abandonnee);

    /* Manifeste incomplet ou taille hors bornes. */
    VRAI(fg_ota_commencer(&ota, "{\"v\":\"2.4.2\",\"size\":10}") == FG_OTA_MANIFESTE_REFUSE);
    snprintf(corps, sizeof corps, "{\"v\":\"2.4.2\",\"url\":\"https://x.test/a.bin\",\"size\":99999999,\"sha\":\"%s\",\"sig\":\"%s\"}", sha, signature);
    VRAI(fg_ota_commencer(&ota, corps) == FG_OTA_MANIFESTE_REFUSE);
}

int main(void)
{
    lancer_base64url_sans_remplissage();
    lancer_sha256_vecteurs_de_reference();
    lancer_lecture_des_champs_json();
    lancer_messages_conformes_au_protocole();
    lancer_sms_de_repli_signe_sur_tout_ce_qui_precede();
    lancer_une_commande_n_est_executee_que_si_tous_les_controles_passent();
    lancer_positions_au_rythme_regle_par_la_plateforme();
    lancer_sos_apres_trois_secondes_d_appui_une_seule_fois();
    lancer_hors_reseau_les_positions_attendent_avec_leur_heure_d_origine();
    lancer_sans_donnees_l_alerte_part_par_sms_au_bout_de_vingt_secondes();
    lancer_si_le_reseau_revient_avant_vingt_secondes_pas_de_sms();
    lancer_retrait_fenetre_autorisee_rappel_et_alerte_a_l_echeance();
    lancer_contact_peau_perdu_confirme_par_l_accelerometre_ou_pose_sur_le_chargeur();
    lancer_batterie_critique_signalee_une_fois_puis_rearmee();
    lancer_une_commande_refusee_est_signalee_et_n_a_aucun_effet();
    lancer_image_installee_seulement_si_manifeste_signe_et_empreinte_conforme();
    lancer_image_alteree_tronquee_ou_trop_longue_abandonnee();
    printf("%d verification(s), %d echec(s)\n", verifications, echecs);
    return echecs == 0 ? 0 : 1;
}
