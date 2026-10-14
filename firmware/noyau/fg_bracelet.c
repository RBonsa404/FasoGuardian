#include "fg_bracelet.h"

#include <string.h>

#include "../ports/fg_port.h"
#include "fg_messages.h"

/* Positions du tampon vidées par seconde au retour du réseau : assez pour rattraper, sans saturer la liaison. */
#define VIDANGE_PAR_TIC 8

/* ------------------------------------------------------------------ tampon hors ligne */

static void tampon_ajouter(fg_tampon_t *tampon, const fg_position_t *position, uint32_t sequence)
{
    uint16_t place;
    if (tampon->nombre == FG_TAMPON_TAILLE) {
        /* Plein : la plus ancienne position cède la place, les plus récentes servent davantage aux parents. */
        place = tampon->premier;
        tampon->premier = (uint16_t)((tampon->premier + 1) % FG_TAMPON_TAILLE);
    } else {
        place = (uint16_t)((tampon->premier + tampon->nombre) % FG_TAMPON_TAILLE);
        tampon->nombre++;
    }
    tampon->points[place].position = *position;
    tampon->points[place].sequence = sequence;
}

uint16_t fg_tampon_nombre(const fg_tampon_t *tampon)
{
    return tampon->nombre;
}

/* ------------------------------------------------------------------ émissions */

static void radio(fg_radio_t *etat)
{
    etat->batterie = fg_port_batterie();
    etat->rssi_dbm = fg_port_rssi_dbm();
    etat->reseau = fg_port_reseau();
    etat->operateur = fg_port_operateur();
    etat->mouvement = fg_port_mouvement();
}

static bool publier_position(const fg_position_t *position, uint32_t sequence)
{
    char message[FG_MESSAGE_MAX];
    fg_radio_t etat;
    radio(&etat);
    return fg_msg_telemetrie(message, sizeof message, position, sequence, &etat) > 0 && fg_port_publier("telemetry", message, false);
}

/* Mesure une position et l'envoie ; sans réseau, elle attend dans le tampon avec son heure d'origine. */
static void emettre_position(fg_bracelet_t *b)
{
    fg_position_t position;
    memset(&position, 0, sizeof position);
    fg_port_mesurer_position(&position);
    if (!position.valide) {
        return;
    }
    b->derniere = position;
    uint32_t sequence = ++b->sequence;
    if (!fg_port_donnees_disponibles() || !publier_position(&position, sequence)) {
        tampon_ajouter(&b->tampon, &position, sequence);
    }
}

static void vider_tampon(fg_bracelet_t *b)
{
    for (int i = 0; i < VIDANGE_PAR_TIC && b->tampon.nombre > 0; i++) {
        const fg_point_t *point = &b->tampon.points[b->tampon.premier];
        if (!publier_position(&point->position, point->sequence)) {
            return;
        }
        b->tampon.premier = (uint16_t)((b->tampon.premier + 1) % FG_TAMPON_TAILLE);
        b->tampon.nombre--;
    }
}

static bool publier_alerte(const fg_bracelet_t *b, fg_evenement_t evenement, uint32_t t, uint32_t sequence)
{
    char message[FG_MESSAGE_MAX];
    return fg_msg_alerte(message, sizeof message, t, sequence, evenement, &b->derniere) > 0 && fg_port_publier("alert", message, false);
}

/*
 * Un événement part tout de suite par la liaison de données. Sans elle, il attend : au bout de vingt secondes
 * il part par SMS signé, si c'est une alerte que le SMS sait porter.
 */
static void emettre_alerte(fg_bracelet_t *b, fg_evenement_t evenement, uint32_t maintenant)
{
    uint32_t sequence = ++b->sequence;
    if (fg_port_donnees_disponibles() && publier_alerte(b, evenement, maintenant, sequence)) {
        return;
    }
    /* Une seule alerte attend à la fois : un SOS ne cède la place à rien, le reste cède au plus récent. */
    if (b->alerte.en_attente && b->alerte.evenement == FG_EV_SOS && evenement != FG_EV_SOS) {
        return;
    }
    b->alerte.en_attente = true;
    b->alerte.evenement = evenement;
    b->alerte.t = maintenant;
    b->alerte.sequence = sequence;
    b->alerte.prochain_sms = maintenant + FG_REPLI_SMS_S;
}

static void suivre_alerte_en_attente(fg_bracelet_t *b, uint32_t maintenant)
{
    if (!b->alerte.en_attente) {
        return;
    }
    if (fg_port_donnees_disponibles() && publier_alerte(b, b->alerte.evenement, b->alerte.t, b->alerte.sequence)) {
        b->alerte.en_attente = false;
        return;
    }
    if (maintenant < b->alerte.prochain_sms) {
        return;
    }
    char sms[200];
    int longueur = fg_sms_alerte(sms, sizeof sms, b->identifiant, b->alerte.evenement, &b->derniere, fg_port_batterie(), b->alerte.t,
                                 fg_port_signer);
    if (longueur < 0) {
        /* Événement que le SMS ne porte pas (pose sur le chargeur, bracelet remis) : il attend le réseau. */
        b->alerte.prochain_sms = UINT32_MAX;
        return;
    }
    if (fg_port_envoyer_sms(sms)) {
        /* Le SMS vaut l'alerte : la renvoyer ensuite par MQTT en créerait une seconde. */
        b->alerte.en_attente = false;
    } else {
        b->alerte.prochain_sms = maintenant + FG_REPLI_SMS_S;
    }
}

/* ------------------------------------------------------------------ cycle de vie */

void fg_bracelet_init(fg_bracelet_t *b, const char *identifiant, const char *version)
{
    memset(b, 0, sizeof *b);
    strncpy(b->identifiant, identifiant, FG_ID_TAILLE - 1);
    b->version = version;
    fg_cmd_init(&b->verificateur, identifiant);
    b->intervalle_s = 300;
    b->intervalle_alerte_s = 60;
    b->boucle_fermee = true;
    b->porte = true;
}

void fg_bracelet_connecte(fg_bracelet_t *b)
{
    char message[64];
    if (fg_msg_etat(message, sizeof message, true, b->version) > 0) {
        fg_port_publier("status", message, true);
    }
}

static uint32_t intervalle_courant(const fg_bracelet_t *b)
{
    return b->mode_alerte ? b->intervalle_alerte_s : b->intervalle_s;
}

static void suivre_retrait(fg_bracelet_t *b, uint32_t maintenant)
{
    if (b->fin_retrait == 0) {
        return;
    }
    if (!b->rappel_retrait_fait && maintenant + FG_RAPPEL_RETRAIT_S >= b->fin_retrait && maintenant < b->fin_retrait) {
        b->rappel_retrait_fait = true;
        fg_port_signaler(FG_SIGNAL_FIN_DE_RETRAIT);
    }
    if (maintenant >= b->fin_retrait) {
        b->fin_retrait = 0;
        /* Fenêtre échue et bracelet toujours ôté : l'événement est redit, il vaut désormais alerte. */
        if (!b->boucle_fermee) {
            emettre_alerte(b, FG_EV_STRAP, maintenant);
        } else if (!b->porte) {
            emettre_alerte(b, FG_EV_SKIN, maintenant);
        }
    }
}

static void suivre_batterie(fg_bracelet_t *b, uint32_t maintenant)
{
    int niveau = fg_port_batterie();
    if (niveau < 0) {
        return;
    }
    if (!b->batterie_critique && niveau < FG_BATTERIE_CRITIQUE) {
        b->batterie_critique = true;
        fg_port_signaler(FG_SIGNAL_BATTERIE_CRITIQUE);
        emettre_alerte(b, FG_EV_BATCRIT, maintenant);
    } else if (b->batterie_critique && niveau >= FG_BATTERIE_RETABLIE) {
        b->batterie_critique = false;
    }
}

void fg_bracelet_tic(fg_bracelet_t *b, uint32_t maintenant)
{
    suivre_alerte_en_attente(b, maintenant);
    suivre_retrait(b, maintenant);
    suivre_batterie(b, maintenant);
    if (fg_port_donnees_disponibles()) {
        vider_tampon(b);
    }
    uint32_t intervalle = intervalle_courant(b);
    /* Suspendu par la plateforme (abonnement restreint), le bracelet n'émet plus qu'en alerte ou sur demande. */
    bool echeance = intervalle > 0 && maintenant >= b->prochaine_emission;
    if (b->localisation_demandee || echeance) {
        b->localisation_demandee = false;
        if (intervalle > 0) {
            b->prochaine_emission = maintenant + intervalle;
        }
        emettre_position(b);
    }
}

/* ------------------------------------------------------------------ capteurs */

void fg_bracelet_bouton(fg_bracelet_t *b, bool appuye, uint32_t maintenant_ms, uint32_t maintenant)
{
    if (!appuye) {
        b->bouton_appuye = false;
        b->sos_emis = false;
        return;
    }
    if (!b->bouton_appuye) {
        b->bouton_appuye = true;
        b->appui_depuis_ms = maintenant_ms;
        return;
    }
    /* La soustraction non signée reste juste quand le compteur de millisecondes repasse par zéro. */
    if (!b->sos_emis && (uint32_t)(maintenant_ms - b->appui_depuis_ms) >= FG_SOS_APPUI_MS) {
        b->sos_emis = true;
        fg_port_signaler(FG_SIGNAL_SOS_CONFIRME);
        /* Sans attendre l'ordre de la plateforme : en alerte, une position toutes les soixante secondes. */
        b->mode_alerte = true;
        b->prochaine_emission = maintenant;
        emettre_alerte(b, FG_EV_SOS, maintenant);
    }
}

void fg_bracelet_capteurs(fg_bracelet_t *b, bool boucle_fermee, bool contact_peau, bool immobile, uint32_t maintenant)
{
    if (b->boucle_fermee && !boucle_fermee) {
        b->boucle_fermee = false;
        emettre_alerte(b, FG_EV_STRAP, maintenant);
    } else if (!b->boucle_fermee && boucle_fermee) {
        b->boucle_fermee = true;
    }
    if (b->porte && !contact_peau) {
        if (!boucle_fermee) {
            /* Fermoir ouvert : la perte du contact peau en est la suite, « strap » l'a déjà dit. */
            b->porte = false;
        } else if (fg_port_en_charge()) {
            /* Posé sur son chargeur : ce n'est pas un retrait à signaler comme tel (ADR 0011). */
            b->porte = false;
            emettre_alerte(b, FG_EV_CHARGE, maintenant);
        } else if (immobile) {
            b->porte = false;
            emettre_alerte(b, FG_EV_SKIN, maintenant);
        }
    } else if (!b->porte && contact_peau && boucle_fermee) {
        b->porte = true;
        /* Bracelet remis : la fenêtre de retrait n'a plus d'objet. */
        b->fin_retrait = 0;
        emettre_alerte(b, FG_EV_WORN, maintenant);
    }
}

void fg_bracelet_chute(fg_bracelet_t *b, uint32_t maintenant)
{
    emettre_alerte(b, FG_EV_FALL, maintenant);
}

/* ------------------------------------------------------------------ commandes */

static bool appliquer(fg_bracelet_t *b, const fg_resultat_t *r, uint32_t maintenant)
{
    int64_t valeur;
    switch (r->code) {
    case FG_CMD_ALERT:
        if (!fg_json_entier(r->corps, "on", &valeur)) {
            return false;
        }
        b->mode_alerte = valeur != 0;
        b->prochaine_emission = maintenant;
        return true;
    case FG_CMD_CFG: {
        int64_t intervalle;
        int64_t alerte;
        if (!fg_json_entier(r->corps, "int", &intervalle) || !fg_json_entier(r->corps, "alr", &alerte) || intervalle < 0 || alerte < 10
            || intervalle > 86400 || alerte > 3600) {
            return false;
        }
        b->intervalle_s = (uint32_t)intervalle;
        b->intervalle_alerte_s = (uint32_t)alerte;
        b->economie = fg_json_entier(r->corps, "eco", &valeur) && valeur != 0;
        b->prochaine_emission = maintenant + b->intervalle_s;
        return true;
    }
    case FG_CMD_RM:
        if (!fg_json_entier(r->corps, "until", &valeur) || valeur < 0) {
            return false;
        }
        b->fin_retrait = (uint32_t)valeur;
        b->rappel_retrait_fait = false;
        if (valeur > 0) {
            fg_port_signaler(FG_SIGNAL_RETRAIT_AUTORISE);
        }
        return true;
    case FG_CMD_LOC:
        b->localisation_demandee = true;
        return true;
    case FG_CMD_OTA:
        if (fg_ota_commencer(&b->ota, r->corps) != FG_OTA_OK) {
            return false;
        }
        b->telechargement_demande = true;
        return true;
    case FG_CMD_INCONNUE:
        break;
    }
    return false;
}

void fg_bracelet_commande(fg_bracelet_t *b, const char *message, uint32_t maintenant)
{
    /* Résultat hors pile : il porte le corps de la commande, trop gros pour la pile d'une tâche. */
    static fg_resultat_t resultat;
    fg_cmd_verifier(&b->verificateur, message, maintenant, &resultat);
    bool ok = fg_cmd_accuse_positif(&resultat);
    if (resultat.rejet == FG_REJET_AUCUN) {
        ok = appliquer(b, &resultat, maintenant);
    }
    /* Sans identifiant lisible, il n'y a rien à accuser : la plateforme réémettra puis laissera expirer. */
    if (resultat.id[0] != '\0') {
        char accuse[80];
        if (fg_msg_accuse(accuse, sizeof accuse, resultat.id, ok) > 0) {
            fg_port_publier("ack", accuse, false);
        }
    }
}

fg_ota_issue_t fg_bracelet_image_recue(fg_bracelet_t *b)
{
    b->telechargement_demande = false;
    return fg_ota_terminer(&b->ota);
}
