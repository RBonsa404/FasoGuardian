/*
 * Types communs du logiciel embarqué FasoGuardian (FG-DOC-08, docs/protocole-bracelet.md).
 * Le noyau n'alloue rien dynamiquement et ne dépend d'aucun matériel : tout passe par ports/fg_port.h.
 */
#ifndef FG_TYPES_H
#define FG_TYPES_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

/* Identifiant gravé du bracelet (« FG-2291 »), zéro final compris. */
#define FG_ID_TAILLE 17
/* Identifiant d'une commande (UUID en texte), zéro final compris. */
#define FG_UUID_TAILLE 37
/* Taille maximale d'un message du protocole. */
#define FG_MESSAGE_MAX 512
/* Signature ECDSA P-256 au format brut R||S. */
#define FG_SIGNATURE_TAILLE 64
#define FG_SHA256_TAILLE 32

typedef enum {
    FG_EV_SOS,
    FG_EV_STRAP,   /* coupure de la boucle */
    FG_EV_SKIN,    /* perte du contact peau */
    FG_EV_FALL,
    FG_EV_BATCRIT,
    FG_EV_CHARGE,  /* pose sur le chargeur */
    FG_EV_WORN     /* contact peau rétabli */
} fg_evenement_t;

typedef enum { FG_SRC_GNSS, FG_SRC_CELL } fg_source_t;

typedef enum { FG_NET_AUCUN, FG_NET_2G, FG_NET_3G, FG_NET_4G } fg_reseau_t;

/* Position en degrés multipliés par 100 000 : pas de calcul flottant sur le microcontrôleur. */
typedef struct {
    bool valide;
    uint32_t t;      /* heure de la mesure, secondes Unix */
    int32_t lat_e5;
    int32_t lon_e5;
    uint16_t acc_m;  /* rayon d'incertitude */
    fg_source_t src;
} fg_position_t;

#endif
