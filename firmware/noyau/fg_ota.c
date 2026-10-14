#include "fg_ota.h"

#include <stdio.h>
#include <string.h>

#include "../ports/fg_port.h"

static fg_ota_issue_t abandonner(fg_ota_t *ota, fg_ota_issue_t issue)
{
    if (ota->en_cours) {
        fg_port_ota_abandonner();
    }
    ota->en_cours = false;
    return issue;
}

fg_ota_issue_t fg_ota_commencer(fg_ota_t *ota, const char *corps)
{
    memset(ota, 0, sizeof *ota);
    char sha[65];
    char signature_texte[100];
    int64_t taille;
    if (!fg_json_texte(corps, "v", ota->version, sizeof ota->version) || !fg_json_texte(corps, "url", ota->url, sizeof ota->url)
        || !fg_json_entier(corps, "size", &taille) || !fg_json_texte(corps, "sha", sha, sizeof sha)
        || !fg_json_texte(corps, "sig", signature_texte, sizeof signature_texte)) {
        return FG_OTA_MANIFESTE_REFUSE;
    }
    if (taille <= 0 || (uint64_t)taille > FG_OTA_TAILLE_MAX || !fg_hex_vers_octets(sha, ota->attendue, FG_SHA256_TAILLE)) {
        return FG_OTA_MANIFESTE_REFUSE;
    }
    /* L'empreinte est signée en minuscules : c'est ainsi que la plateforme et le poste de publication l'écrivent. */
    for (char *c = sha; *c != '\0'; c++) {
        if (*c >= 'A' && *c <= 'F') {
            *c = (char)(*c - 'A' + 'a');
        }
    }
    char manifeste[128];
    int longueur = snprintf(manifeste, sizeof manifeste, "FG-OTA|%s|%lu|%s", ota->version, (unsigned long)taille, sha);
    uint8_t signature[FG_SIGNATURE_TAILLE];
    if (longueur <= 0 || (size_t)longueur >= sizeof manifeste
        || fg_base64url_decoder(signature_texte, strlen(signature_texte), signature, sizeof signature) != FG_SIGNATURE_TAILLE
        || !fg_port_verifier(FG_CLE_PUBLICATION, (const uint8_t *)manifeste, (size_t)longueur, signature)) {
        return FG_OTA_MANIFESTE_REFUSE;
    }
    ota->taille = (uint32_t)taille;
    if (!fg_port_ota_ouvrir(ota->taille)) {
        return FG_OTA_STOCKAGE;
    }
    fg_sha256_debut(&ota->calcul);
    ota->en_cours = true;
    return FG_OTA_OK;
}

fg_ota_issue_t fg_ota_recevoir(fg_ota_t *ota, const uint8_t *donnees, size_t taille)
{
    if (!ota->en_cours) {
        return FG_OTA_STOCKAGE;
    }
    if (taille > ota->taille - ota->recu) {
        return abandonner(ota, FG_OTA_TAILLE);
    }
    if (!fg_port_ota_ecrire(donnees, taille)) {
        return abandonner(ota, FG_OTA_STOCKAGE);
    }
    fg_sha256_ajouter(&ota->calcul, donnees, taille);
    ota->recu += (uint32_t)taille;
    return FG_OTA_OK;
}

fg_ota_issue_t fg_ota_terminer(fg_ota_t *ota)
{
    if (!ota->en_cours) {
        return FG_OTA_STOCKAGE;
    }
    if (ota->recu != ota->taille) {
        return abandonner(ota, FG_OTA_TAILLE);
    }
    uint8_t empreinte[FG_SHA256_TAILLE];
    fg_sha256_fin(&ota->calcul, empreinte);
    if (memcmp(empreinte, ota->attendue, FG_SHA256_TAILLE) != 0) {
        return abandonner(ota, FG_OTA_EMPREINTE);
    }
    if (!fg_port_ota_valider()) {
        return abandonner(ota, FG_OTA_STOCKAGE);
    }
    ota->en_cours = false;
    return FG_OTA_OK;
}
