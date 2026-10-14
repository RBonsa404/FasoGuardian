/* Messages émis par le bracelet (docs/protocole-bracelet.md). Chaque fonction renvoie la longueur écrite, ou -1. */
#ifndef FG_MESSAGES_H
#define FG_MESSAGES_H

#include "fg_types.h"

typedef struct {
    int batterie;        /* pour cent, ou -1 si inconnue */
    int rssi_dbm;        /* 0 si inconnu */
    fg_reseau_t reseau;
    const char *operateur; /* NULL ou vide si inconnu */
    bool mouvement;
} fg_radio_t;

int fg_msg_telemetrie(char *dst, size_t capacite, const fg_position_t *position, uint32_t sequence, const fg_radio_t *radio);

/* `position` peut être NULL ou invalide : l'alerte part sans attendre un point. */
int fg_msg_alerte(char *dst, size_t capacite, uint32_t t, uint32_t sequence, fg_evenement_t evenement, const fg_position_t *position);

int fg_msg_etat(char *dst, size_t capacite, bool en_ligne, const char *version);

int fg_msg_accuse(char *dst, size_t capacite, const char *id_commande, bool ok);

typedef bool (*fg_signer_fn)(const uint8_t *message, size_t taille, uint8_t signature[FG_SIGNATURE_TAILLE]);

/*
 * SMS de repli : « FG1|<id>|ALR|<EV>|<lat>,<lon>|<src>|<bat>|<t>|<signature> ». Seuls SOS, retrait, chute et
 * batterie critique partent par SMS ; pour un autre événement, ou si la signature échoue, renvoie -1.
 */
int fg_sms_alerte(char *dst, size_t capacite, const char *identifiant, fg_evenement_t evenement, const fg_position_t *position,
                  int batterie, uint32_t t, fg_signer_fn signer);

/* Code de l'événement dans un message MQTT (« sos », « strap »…). */
const char *fg_code_evenement(fg_evenement_t evenement);

#endif
