/*
 * Frontière entre le noyau et le matériel. Le noyau n'appelle que ces fonctions ; chaque carte en fournit une
 * réalisation (ports/stm32u5 pour le bracelet, tests/port_hote.c pour les essais sur machine hôte).
 */
#ifndef FG_PORT_H
#define FG_PORT_H

#include "../noyau/fg_types.h"

/* Heure Unix en secondes, recalée par le réseau ; 0 tant qu'elle n'est pas connue. */
uint32_t fg_port_heure(void);

/*
 * Élément sécurisé (ATECC608B). Les clés privées n'en sortent jamais : le noyau lui demande de signer ou de
 * vérifier. `cle` désigne la clé publique à employer pour une vérification.
 */
typedef enum {
    FG_CLE_PLATEFORME,  /* signe les commandes */
    FG_CLE_PUBLICATION  /* signe les images du logiciel embarqué */
} fg_cle_t;

bool fg_port_verifier(fg_cle_t cle, const uint8_t *message, size_t taille, const uint8_t signature[FG_SIGNATURE_TAILLE]);
bool fg_port_signer(const uint8_t *message, size_t taille, uint8_t signature[FG_SIGNATURE_TAILLE]);

/* Liaison de données (MQTT sur TLS mutuel). `flux` vaut "telemetry", "alert", "status" ou "ack". */
bool fg_port_donnees_disponibles(void);
bool fg_port_publier(const char *flux, const char *message, bool retenu);

/* SMS de repli vers la passerelle FasoGuardian. */
bool fg_port_envoyer_sms(const char *texte);

/* Dernier état radio connu. */
fg_reseau_t fg_port_reseau(void);
int fg_port_rssi_dbm(void);
/* Opérateur retenu par la SIM multi-opérateurs, ou chaîne vide. */
const char *fg_port_operateur(void);

/* Mesure une position ; `position->valide` reste faux si ni le GNSS ni la cellule n'en donnent. */
void fg_port_mesurer_position(fg_position_t *position);

/* Batterie en pour cent, et présence sur le chargeur. */
int fg_port_batterie(void);
bool fg_port_en_charge(void);
/* L'accéléromètre a vu un mouvement depuis le dernier appel. */
bool fg_port_mouvement(void);

/* Signaux au porteur (HANDOFF, écran 77). */
typedef enum { FG_SIGNAL_SOS_CONFIRME, FG_SIGNAL_RETRAIT_AUTORISE, FG_SIGNAL_FIN_DE_RETRAIT, FG_SIGNAL_BATTERIE_CRITIQUE } fg_signal_t;
void fg_port_signaler(fg_signal_t signal);

/* Mise à jour : écriture de l'image dans la banque inactive, puis bascule au prochain démarrage. */
bool fg_port_ota_ouvrir(uint32_t taille);
bool fg_port_ota_ecrire(const uint8_t *donnees, size_t taille);
bool fg_port_ota_valider(void);
void fg_port_ota_abandonner(void);

#endif
