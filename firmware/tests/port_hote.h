/*
 * Port d'essai : réalise ports/fg_port.h sur la machine hôte. Il ne simule aucun matériel ; il rend ce que
 * l'essai a réglé et retient ce que le noyau a demandé, pour que les essais portent sur les règles du noyau.
 */
#ifndef PORT_HOTE_H
#define PORT_HOTE_H

#include "../ports/fg_port.h"

#define HOTE_MESSAGES 64

typedef struct {
    uint32_t heure;
    bool donnees;
    bool publication_reussit;
    bool signature_plateforme_valide;
    bool signature_publication_valide;
    bool sms_reussit;
    int batterie;
    bool en_charge;
    bool mouvement;
    fg_reseau_t reseau;
    fg_position_t position;

    char flux[HOTE_MESSAGES][12];
    char publies[HOTE_MESSAGES][FG_MESSAGE_MAX];
    int nombre_publies;
    char sms[8][200];
    int nombre_sms;
    fg_signal_t signaux[16];
    int nombre_signaux;

    uint8_t image[4096];
    size_t image_taille;
    bool ota_ouverte;
    bool ota_validee;
    bool ota_abandonnee;
} hote_t;

extern hote_t hote;

/* Remet le port dans son état de départ : réseau présent, signatures valides, batterie à 80 %. */
void hote_raz(void);

/* Dernier message publié sur un flux, ou NULL. */
const char *hote_dernier(const char *flux);
int hote_nombre(const char *flux);

#endif
