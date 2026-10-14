/*
 * Mise à jour du logiciel embarqué (US-PAR-013, ADR 0027). Une image n'est acceptée que si son manifeste
 * « FG-OTA|version|taille|sha256 » est signé par la clé de publication, puis si ce qui a été téléchargé a
 * bien la taille et l'empreinte annoncées. La commande de la plateforme ne fait que transporter le manifeste.
 */
#ifndef FG_OTA_H
#define FG_OTA_H

#include "fg_codage.h"
#include "fg_types.h"

/* Borne haute de la taille d'une image, comme côté plateforme. */
#define FG_OTA_TAILLE_MAX (16UL * 1024UL * 1024UL)
#define FG_OTA_URL_MAX 301

typedef enum {
    FG_OTA_OK,
    FG_OTA_MANIFESTE_REFUSE, /* champ manquant, taille hors bornes ou signature invalide */
    FG_OTA_STOCKAGE,         /* la banque inactive ne s'ouvre pas ou ne s'écrit pas */
    FG_OTA_TAILLE,           /* plus, ou moins, d'octets que le manifeste n'en annonce */
    FG_OTA_EMPREINTE         /* l'image reçue n'est pas celle qui a été signée */
} fg_ota_issue_t;

typedef struct {
    bool en_cours;
    char version[17];
    char url[FG_OTA_URL_MAX];
    uint32_t taille;
    uint32_t recu;
    uint8_t attendue[FG_SHA256_TAILLE];
    fg_sha256_t calcul;
} fg_ota_t;

/* Lit et vérifie le manifeste porté par le corps d'une commande « ota », puis ouvre la banque inactive. */
fg_ota_issue_t fg_ota_commencer(fg_ota_t *ota, const char *corps_commande);

/* Bloc téléchargé : écrit dans la banque inactive et ajouté à l'empreinte. */
fg_ota_issue_t fg_ota_recevoir(fg_ota_t *ota, const uint8_t *donnees, size_t taille);

/* Fin du téléchargement : la banque n'est validée que si la taille et l'empreinte sont celles du manifeste. */
fg_ota_issue_t fg_ota_terminer(fg_ota_t *ota);

#endif
