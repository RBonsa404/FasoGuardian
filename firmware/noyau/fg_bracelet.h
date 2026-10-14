/*
 * Comportement du bracelet (FG-DOC-08 §7) : rythme des positions, SOS par appui long, détection de retrait,
 * batterie critique, exécution des commandes, tampon hors ligne et repli SMS des alertes.
 * Les tâches (ports/freertos) appellent ces fonctions ; elles ne contiennent aucune règle.
 */
#ifndef FG_BRACELET_H
#define FG_BRACELET_H

#include "fg_commandes.h"
#include "fg_ota.h"
#include "fg_types.h"

/* Positions gardées pendant une coupure ; la plus ancienne cède la place quand le tampon est plein. */
#define FG_TAMPON_TAILLE 64
/* Appui long qui déclenche le SOS. */
#define FG_SOS_APPUI_MS 3000U
/* Sans liaison de données au bout de ce délai, l'alerte part par SMS (FG-DOC-08 §8.3). */
#define FG_REPLI_SMS_S 20U
/* Rappel au porteur avant la fin d'une fenêtre de retrait. */
#define FG_RAPPEL_RETRAIT_S 300U
#define FG_BATTERIE_CRITIQUE 10
/* La batterie doit remonter jusque-là pour qu'une nouvelle alerte « critique » puisse partir. */
#define FG_BATTERIE_RETABLIE 15

typedef struct {
    fg_position_t position;
    uint32_t sequence;
} fg_point_t;

typedef struct {
    fg_point_t points[FG_TAMPON_TAILLE];
    uint16_t premier;
    uint16_t nombre;
} fg_tampon_t;

typedef struct {
    bool en_attente;
    fg_evenement_t evenement;
    uint32_t t;
    uint32_t sequence;
    uint32_t prochain_sms; /* heure à partir de laquelle le SMS de repli part */
} fg_alerte_en_attente_t;

typedef struct {
    char identifiant[FG_ID_TAILLE];
    const char *version;
    fg_verificateur_t verificateur;

    /* Réglages reçus de la plateforme (commande cfg). Intervalle nul : émission périodique suspendue. */
    uint32_t intervalle_s;
    uint32_t intervalle_alerte_s;
    bool economie;
    bool mode_alerte;
    /* Fin de la fenêtre de retrait autorisée, ou 0. */
    uint32_t fin_retrait;
    bool rappel_retrait_fait;

    uint32_t sequence;
    uint32_t prochaine_emission;
    bool localisation_demandee;
    fg_position_t derniere;

    bool bouton_appuye;
    uint32_t appui_depuis_ms;
    bool sos_emis;

    bool boucle_fermee;
    bool porte;
    bool batterie_critique;

    fg_tampon_t tampon;
    fg_alerte_en_attente_t alerte;
    fg_ota_t ota;
    /* Une image a été acceptée : la tâche réseau doit la télécharger depuis ota.url. */
    bool telechargement_demande;
} fg_bracelet_t;

void fg_bracelet_init(fg_bracelet_t *bracelet, const char *identifiant, const char *version);

/* À appeler à la connexion : annonce « en ligne » et la version exécutée. */
void fg_bracelet_connecte(fg_bracelet_t *bracelet);

/* À appeler une fois par seconde. */
void fg_bracelet_tic(fg_bracelet_t *bracelet, uint32_t maintenant);

/* État du bouton SOS, avec une horloge en millisecondes. */
void fg_bracelet_bouton(fg_bracelet_t *bracelet, bool appuye, uint32_t maintenant_ms, uint32_t maintenant);

/*
 * État du fermoir et du contact peau. `immobile` : l'accéléromètre confirme que le bracelet ne bouge plus,
 * condition pour tenir une perte de contact peau pour un retrait.
 */
void fg_bracelet_capteurs(fg_bracelet_t *bracelet, bool boucle_fermee, bool contact_peau, bool immobile, uint32_t maintenant);

void fg_bracelet_chute(fg_bracelet_t *bracelet, uint32_t maintenant);

/* Message reçu sur le sujet des commandes. */
void fg_bracelet_commande(fg_bracelet_t *bracelet, const char *message, uint32_t maintenant);

/* Fin du téléchargement d'une image : la valide et redémarre, ou l'abandonne. Renvoie l'issue. */
fg_ota_issue_t fg_bracelet_image_recue(fg_bracelet_t *bracelet);

uint16_t fg_tampon_nombre(const fg_tampon_t *tampon);

#endif
