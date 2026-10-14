#include "fg_commandes.h"

#include <string.h>

#include "../ports/fg_port.h"
#include "fg_codage.h"

void fg_cmd_init(fg_verificateur_t *v, const char *identifiant)
{
    memset(v, 0, sizeof *v);
    strncpy(v->identifiant, identifiant, FG_ID_TAILLE - 1);
}

static bool deja_executee(const fg_verificateur_t *v, const char *id)
{
    for (int i = 0; i < FG_MEMOIRE_COMMANDES; i++) {
        if (v->executees[i][0] != '\0' && strcmp(v->executees[i], id) == 0) {
            return true;
        }
    }
    return false;
}

static fg_code_cmd_t code_de(const char *texte)
{
    static const struct {
        const char *texte;
        fg_code_cmd_t code;
    } CODES[] = {{"alert", FG_CMD_ALERT}, {"cfg", FG_CMD_CFG}, {"rm", FG_CMD_RM}, {"loc", FG_CMD_LOC}, {"ota", FG_CMD_OTA}};
    for (size_t i = 0; i < sizeof CODES / sizeof CODES[0]; i++) {
        if (strcmp(texte, CODES[i].texte) == 0) {
            return CODES[i].code;
        }
    }
    return FG_CMD_INCONNUE;
}

void fg_cmd_verifier(fg_verificateur_t *v, const char *message, uint32_t maintenant, fg_resultat_t *r)
{
    memset(r, 0, sizeof *r);
    r->rejet = FG_REJET_FORMAT;
    r->code = FG_CMD_INCONNUE;
    const char *point = message == NULL ? NULL : strchr(message, '.');
    if (point == NULL || strchr(point + 1, '.') != NULL) {
        return;
    }
    int taille = fg_base64url_decoder(message, (size_t)(point - message), (uint8_t *)r->corps, sizeof r->corps - 1);
    uint8_t signature[FG_SIGNATURE_TAILLE];
    int taille_signature = fg_base64url_decoder(point + 1, strlen(point + 1), signature, sizeof signature);
    if (taille <= 0 || taille_signature != FG_SIGNATURE_TAILLE) {
        r->corps[0] = '\0';
        return;
    }
    r->corps[taille] = '\0';
    /* L'identifiant est lu avant la vérification pour pouvoir signaler le refus ; il n'engage à rien. */
    if (!fg_json_texte(r->corps, "id", r->id, sizeof r->id)) {
        r->id[0] = '\0';
    }
    if (!fg_port_verifier(FG_CLE_PLATEFORME, (const uint8_t *)r->corps, (size_t)taille, signature)) {
        r->rejet = FG_REJET_SIGNATURE;
        return;
    }
    char code[9];
    char destinataire[FG_ID_TAILLE];
    int64_t numero;
    int64_t expiration;
    if (r->id[0] == '\0' || !fg_json_texte(r->corps, "cmd", code, sizeof code) || !fg_json_entier(r->corps, "n", &numero)
        || !fg_json_entier(r->corps, "exp", &expiration) || numero < 0) {
        return;
    }
    if (!fg_json_texte(r->corps, "dev", destinataire, sizeof destinataire) || strcmp(destinataire, v->identifiant) != 0) {
        r->rejet = FG_REJET_DESTINATAIRE;
        return;
    }
    if ((int64_t)maintenant >= expiration) {
        r->rejet = FG_REJET_EXPIREE;
        return;
    }
    if ((uint64_t)numero <= v->dernier_numero) {
        /* La plateforme réémet une commande dont l'accusé s'est perdu : elle n'est pas rejouée, mais accusée. */
        r->rejet = FG_REJET_REJEU;
        r->deja_executee = deja_executee(v, r->id);
        return;
    }
    v->dernier_numero = (uint64_t)numero;
    strncpy(v->executees[v->suivante], r->id, FG_UUID_TAILLE - 1);
    v->executees[v->suivante][FG_UUID_TAILLE - 1] = '\0';
    v->suivante = (uint8_t)((v->suivante + 1) % FG_MEMOIRE_COMMANDES);
    r->code = code_de(code);
    r->rejet = FG_REJET_AUCUN;
}

bool fg_cmd_accuse_positif(const fg_resultat_t *r)
{
    return r->rejet == FG_REJET_AUCUN || r->deja_executee;
}
