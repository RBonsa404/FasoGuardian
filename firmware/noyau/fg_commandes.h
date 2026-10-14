/*
 * Vérification d'une commande reçue de la plateforme (docs/protocole-bracelet.md, US-SYS-011). Une commande
 * n'est exécutée que si sa signature est celle de la plateforme, qu'elle est adressée à ce bracelet, qu'elle
 * n'a pas expiré et que son numéro n'a jamais été vu.
 */
#ifndef FG_COMMANDES_H
#define FG_COMMANDES_H

#include "fg_types.h"

/* Nombre d'identifiants de commandes exécutées gardés pour reconnaître une réémission. */
#define FG_MEMOIRE_COMMANDES 16

typedef enum { FG_CMD_ALERT, FG_CMD_CFG, FG_CMD_RM, FG_CMD_LOC, FG_CMD_OTA, FG_CMD_INCONNUE } fg_code_cmd_t;

typedef enum {
    FG_REJET_AUCUN,
    FG_REJET_FORMAT,
    FG_REJET_SIGNATURE,
    FG_REJET_DESTINATAIRE,
    FG_REJET_EXPIREE,
    FG_REJET_REJEU
} fg_rejet_t;

typedef struct {
    char identifiant[FG_ID_TAILLE];
    uint64_t dernier_numero;
    char executees[FG_MEMOIRE_COMMANDES][FG_UUID_TAILLE];
    uint8_t suivante;
} fg_verificateur_t;

typedef struct {
    fg_rejet_t rejet;
    /* Commande authentique déjà exécutée : elle est accusée de nouveau, sans être rejouée. */
    bool deja_executee;
    /* Identifiant lu dans le message, même refusé, pour le signaler à la plateforme ; vide s'il est illisible. */
    char id[FG_UUID_TAILLE];
    fg_code_cmd_t code;
    /* Corps JSON de la commande, pour en lire les paramètres. */
    char corps[FG_MESSAGE_MAX];
} fg_resultat_t;

void fg_cmd_init(fg_verificateur_t *verificateur, const char *identifiant);

/* `message` est le texte « base64url(corps).base64url(signature) » ; `maintenant` en secondes Unix. */
void fg_cmd_verifier(fg_verificateur_t *verificateur, const char *message, uint32_t maintenant, fg_resultat_t *resultat);

/* Valeur de « ok » dans l'accusé : vrai pour une commande exécutée, maintenant ou auparavant. */
bool fg_cmd_accuse_positif(const fg_resultat_t *resultat);

#endif
