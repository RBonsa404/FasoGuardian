/* Codages utilitaires : base64url sans remplissage, SHA-256, lecture de champs JSON, hexadécimal. */
#ifndef FG_CODAGE_H
#define FG_CODAGE_H

#include "fg_types.h"

/* Encode en base64url sans remplissage. Renvoie la longueur écrite (hors zéro final), ou -1 si dst est trop petit. */
int fg_base64url_encoder(const uint8_t *src, size_t taille, char *dst, size_t capacite);

/* Décode du base64url sans remplissage. Renvoie le nombre d'octets, ou -1 (caractère invalide, dst trop petit). */
int fg_base64url_decoder(const char *src, size_t longueur, uint8_t *dst, size_t capacite);

typedef struct {
    uint32_t etat[8];
    uint64_t octets;
    uint8_t bloc[64];
    size_t rempli;
} fg_sha256_t;

void fg_sha256_debut(fg_sha256_t *contexte);
void fg_sha256_ajouter(fg_sha256_t *contexte, const uint8_t *donnees, size_t taille);
void fg_sha256_fin(fg_sha256_t *contexte, uint8_t empreinte[FG_SHA256_TAILLE]);

/* Convertit 64 chiffres hexadécimaux en 32 octets. Renvoie false si le texte n'en est pas. */
bool fg_hex_vers_octets(const char *hex, uint8_t *dst, size_t taille);

/*
 * Lecture d'un champ dans un objet JSON compact produit par la plateforme. Le noyau ne lit que des champs de
 * premier niveau ou de l'objet « p », aux noms distincts : une recherche de « "nom": » suffit.
 */
bool fg_json_texte(const char *json, const char *nom, char *dst, size_t capacite);
bool fg_json_entier(const char *json, const char *nom, int64_t *valeur);

#endif
