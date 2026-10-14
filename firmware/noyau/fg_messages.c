#include "fg_messages.h"

#include <stdio.h>
#include <string.h>

#include "fg_codage.h"

/* Écrit « -1.51970 » à partir de -151970 : cinq décimales, sans arithmétique flottante. */
static int degres(char *dst, size_t capacite, int32_t e5)
{
    uint32_t absolu = e5 < 0 ? (uint32_t)(-(int64_t)e5) : (uint32_t)e5;
    return snprintf(dst, capacite, "%s%lu.%05lu", e5 < 0 ? "-" : "", (unsigned long)(absolu / 100000), (unsigned long)(absolu % 100000));
}

/* Ajoute du texte formaté à la suite ; renvoie false quand le message déborde. */
static bool ajouter(char *dst, size_t capacite, size_t *longueur, int ecrit)
{
    if (ecrit < 0 || *longueur + (size_t)ecrit >= capacite) {
        return false;
    }
    *longueur += (size_t)ecrit;
    (void)dst;
    return true;
}

#define SUITE(...) \
    do { \
        if (!ajouter(dst, capacite, &n, snprintf(dst + n, capacite - n, __VA_ARGS__))) { \
            return -1; \
        } \
    } while (0)

static int position_json(char *dst, size_t capacite, const fg_position_t *position, bool avec_source)
{
    char lat[16];
    char lon[16];
    degres(lat, sizeof lat, position->lat_e5);
    degres(lon, sizeof lon, position->lon_e5);
    size_t n = 0;
    SUITE(",\"lat\":%s,\"lon\":%s,\"acc\":%u", lat, lon, (unsigned)position->acc_m);
    if (avec_source) {
        SUITE(",\"src\":\"%s\"", position->src == FG_SRC_GNSS ? "gnss" : "cell");
    }
    return (int)n;
}

const char *fg_code_evenement(fg_evenement_t evenement)
{
    switch (evenement) {
    case FG_EV_SOS:
        return "sos";
    case FG_EV_STRAP:
        return "strap";
    case FG_EV_SKIN:
        return "skin";
    case FG_EV_FALL:
        return "fall";
    case FG_EV_BATCRIT:
        return "batcrit";
    case FG_EV_CHARGE:
        return "charge";
    case FG_EV_WORN:
        return "worn";
    }
    return "";
}

int fg_msg_telemetrie(char *dst, size_t capacite, const fg_position_t *position, uint32_t sequence, const fg_radio_t *radio)
{
    if (position == NULL || !position->valide) {
        return -1;
    }
    size_t n = 0;
    SUITE("{\"t\":%lu,\"seq\":%lu", (unsigned long)position->t, (unsigned long)sequence);
    int ecrit = position_json(dst + n, capacite - n, position, true);
    if (ecrit < 0) {
        return -1;
    }
    n += (size_t)ecrit;
    if (radio->batterie >= 0) {
        SUITE(",\"bat\":%d", radio->batterie);
    }
    if (radio->rssi_dbm != 0) {
        SUITE(",\"rssi\":%d", radio->rssi_dbm);
    }
    if (radio->reseau != FG_NET_AUCUN) {
        SUITE(",\"net\":\"%s\"", radio->reseau == FG_NET_4G ? "4g" : radio->reseau == FG_NET_3G ? "3g" : "2g");
    }
    /* Le nom de l'opérateur vient du réseau : un guillemet ou une barre oblique casserait le message. */
    if (radio->operateur != NULL && radio->operateur[0] != '\0' && strpbrk(radio->operateur, "\"\\") == NULL) {
        SUITE(",\"op\":\"%.24s\"", radio->operateur);
    }
    SUITE(",\"mv\":%d}", radio->mouvement ? 1 : 0);
    return (int)n;
}

int fg_msg_alerte(char *dst, size_t capacite, uint32_t t, uint32_t sequence, fg_evenement_t evenement, const fg_position_t *position)
{
    size_t n = 0;
    SUITE("{\"t\":%lu,\"seq\":%lu,\"ev\":\"%s\"", (unsigned long)t, (unsigned long)sequence, fg_code_evenement(evenement));
    if (position != NULL && position->valide) {
        int ecrit = position_json(dst + n, capacite - n, position, false);
        if (ecrit < 0) {
            return -1;
        }
        n += (size_t)ecrit;
    }
    SUITE("}");
    return (int)n;
}

int fg_msg_etat(char *dst, size_t capacite, bool en_ligne, const char *version)
{
    size_t n = 0;
    SUITE("{\"online\":%s,\"fw\":\"%.16s\"}", en_ligne ? "true" : "false", version);
    return (int)n;
}

int fg_msg_accuse(char *dst, size_t capacite, const char *id_commande, bool ok)
{
    size_t n = 0;
    SUITE("{\"id\":\"%.36s\",\"ok\":%s}", id_commande, ok ? "true" : "false");
    return (int)n;
}

int fg_sms_alerte(char *dst, size_t capacite, const char *identifiant, fg_evenement_t evenement, const fg_position_t *position,
                  int batterie, uint32_t t, fg_signer_fn signer)
{
    const char *code;
    switch (evenement) {
    case FG_EV_SOS:
        code = "SOS";
        break;
    case FG_EV_STRAP:
        code = "STRAP";
        break;
    case FG_EV_SKIN:
        code = "SKIN";
        break;
    case FG_EV_FALL:
        code = "FALL";
        break;
    case FG_EV_BATCRIT:
        code = "BATCRIT";
        break;
    default:
        return -1;
    }
    size_t n = 0;
    SUITE("FG1|%.16s|ALR|%s|", identifiant, code);
    char source = 'G';
    if (position != NULL && position->valide) {
        char lat[16];
        char lon[16];
        degres(lat, sizeof lat, position->lat_e5);
        degres(lon, sizeof lon, position->lon_e5);
        SUITE("%s,%s", lat, lon);
        source = position->src == FG_SRC_GNSS ? 'G' : 'C';
    }
    SUITE("|%c|%d|%lu|", source, batterie < 0 ? 0 : batterie, (unsigned long)t);
    /* La signature porte sur tout ce qui précède, dernier séparateur compris. */
    uint8_t signature[FG_SIGNATURE_TAILLE];
    if (signer == NULL || !signer((const uint8_t *)dst, n, signature)) {
        return -1;
    }
    int ecrit = fg_base64url_encoder(signature, sizeof signature, dst + n, capacite - n);
    if (ecrit < 0) {
        return -1;
    }
    return (int)n + ecrit;
}
