#include "port_hote.h"

#include <string.h>

hote_t hote;

void hote_raz(void)
{
    memset(&hote, 0, sizeof hote);
    hote.heure = 1791540000;
    hote.donnees = true;
    hote.publication_reussit = true;
    hote.signature_plateforme_valide = true;
    hote.signature_publication_valide = true;
    hote.sms_reussit = true;
    hote.batterie = 80;
    hote.reseau = FG_NET_4G;
    hote.position.valide = true;
    hote.position.lat_e5 = 1237140;
    hote.position.lon_e5 = -151970;
    hote.position.acc_m = 8;
    hote.position.src = FG_SRC_GNSS;
}

const char *hote_dernier(const char *flux)
{
    for (int i = hote.nombre_publies - 1; i >= 0; i--) {
        if (strcmp(hote.flux[i], flux) == 0) {
            return hote.publies[i];
        }
    }
    return NULL;
}

int hote_nombre(const char *flux)
{
    int nombre = 0;
    for (int i = 0; i < hote.nombre_publies; i++) {
        if (strcmp(hote.flux[i], flux) == 0) {
            nombre++;
        }
    }
    return nombre;
}

uint32_t fg_port_heure(void)
{
    return hote.heure;
}

bool fg_port_verifier(fg_cle_t cle, const uint8_t *message, size_t taille, const uint8_t signature[FG_SIGNATURE_TAILLE])
{
    (void)message;
    (void)taille;
    (void)signature;
    return cle == FG_CLE_PLATEFORME ? hote.signature_plateforme_valide : hote.signature_publication_valide;
}

bool fg_port_signer(const uint8_t *message, size_t taille, uint8_t signature[FG_SIGNATURE_TAILLE])
{
    /* Signature d'essai : elle dépend du message, pour qu'un essai voie qu'elle porte bien sur le texte attendu. */
    uint8_t somme = 0;
    for (size_t i = 0; i < taille; i++) {
        somme = (uint8_t)(somme + message[i]);
    }
    memset(signature, somme, FG_SIGNATURE_TAILLE);
    return true;
}

bool fg_port_donnees_disponibles(void)
{
    return hote.donnees;
}

bool fg_port_publier(const char *flux, const char *message, bool retenu)
{
    (void)retenu;
    if (!hote.donnees || !hote.publication_reussit || hote.nombre_publies >= HOTE_MESSAGES) {
        return false;
    }
    strncpy(hote.flux[hote.nombre_publies], flux, sizeof hote.flux[0] - 1);
    strncpy(hote.publies[hote.nombre_publies], message, FG_MESSAGE_MAX - 1);
    hote.nombre_publies++;
    return true;
}

bool fg_port_envoyer_sms(const char *texte)
{
    if (!hote.sms_reussit || hote.nombre_sms >= 8) {
        return false;
    }
    strncpy(hote.sms[hote.nombre_sms++], texte, sizeof hote.sms[0] - 1);
    return true;
}

fg_reseau_t fg_port_reseau(void)
{
    return hote.reseau;
}

int fg_port_rssi_dbm(void)
{
    return -79;
}

const char *fg_port_operateur(void)
{
    return "Orange BF";
}

void fg_port_mesurer_position(fg_position_t *position)
{
    *position = hote.position;
    position->t = hote.heure;
}

int fg_port_batterie(void)
{
    return hote.batterie;
}

bool fg_port_en_charge(void)
{
    return hote.en_charge;
}

bool fg_port_mouvement(void)
{
    return hote.mouvement;
}

void fg_port_signaler(fg_signal_t signal)
{
    if (hote.nombre_signaux < 16) {
        hote.signaux[hote.nombre_signaux++] = signal;
    }
}

bool fg_port_ota_ouvrir(uint32_t taille)
{
    if (taille > sizeof hote.image) {
        return false;
    }
    hote.ota_ouverte = true;
    hote.image_taille = 0;
    return true;
}

bool fg_port_ota_ecrire(const uint8_t *donnees, size_t taille)
{
    if (hote.image_taille + taille > sizeof hote.image) {
        return false;
    }
    memcpy(hote.image + hote.image_taille, donnees, taille);
    hote.image_taille += taille;
    return true;
}

bool fg_port_ota_valider(void)
{
    hote.ota_validee = true;
    return true;
}

void fg_port_ota_abandonner(void)
{
    hote.ota_abandonnee = true;
}
