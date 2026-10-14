#include "fg_codage.h"

#include <string.h>

static const char ALPHABET[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

int fg_base64url_encoder(const uint8_t *src, size_t taille, char *dst, size_t capacite)
{
    size_t besoin = (taille * 4 + 2) / 3;
    if (capacite < besoin + 1) {
        return -1;
    }
    size_t ecrit = 0;
    for (size_t i = 0; i < taille; i += 3) {
        uint32_t groupe = (uint32_t)src[i] << 16;
        if (i + 1 < taille) {
            groupe |= (uint32_t)src[i + 1] << 8;
        }
        if (i + 2 < taille) {
            groupe |= src[i + 2];
        }
        dst[ecrit++] = ALPHABET[(groupe >> 18) & 0x3F];
        dst[ecrit++] = ALPHABET[(groupe >> 12) & 0x3F];
        if (i + 1 < taille) {
            dst[ecrit++] = ALPHABET[(groupe >> 6) & 0x3F];
        }
        if (i + 2 < taille) {
            dst[ecrit++] = ALPHABET[groupe & 0x3F];
        }
    }
    dst[ecrit] = '\0';
    return (int)ecrit;
}

static int valeur_base64(char c)
{
    if (c >= 'A' && c <= 'Z') {
        return c - 'A';
    }
    if (c >= 'a' && c <= 'z') {
        return c - 'a' + 26;
    }
    if (c >= '0' && c <= '9') {
        return c - '0' + 52;
    }
    if (c == '-') {
        return 62;
    }
    if (c == '_') {
        return 63;
    }
    return -1;
}

int fg_base64url_decoder(const char *src, size_t longueur, uint8_t *dst, size_t capacite)
{
    /* Un reste d'un seul caractère ne code aucun octet : le texte est tronqué. */
    if (longueur % 4 == 1) {
        return -1;
    }
    size_t ecrit = 0;
    uint32_t tampon = 0;
    int bits = 0;
    for (size_t i = 0; i < longueur; i++) {
        int valeur = valeur_base64(src[i]);
        if (valeur < 0) {
            return -1;
        }
        tampon = (tampon << 6) | (uint32_t)valeur;
        bits += 6;
        if (bits >= 8) {
            bits -= 8;
            if (ecrit >= capacite) {
                return -1;
            }
            dst[ecrit++] = (uint8_t)((tampon >> bits) & 0xFF);
        }
    }
    return (int)ecrit;
}

/* ------------------------------------------------------------------ SHA-256 (FIPS 180-4) */

static const uint32_t K[64] = {
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
    0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
    0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2};

static uint32_t rotation(uint32_t x, unsigned n)
{
    return (x >> n) | (x << (32 - n));
}

static void transformer(fg_sha256_t *c, const uint8_t bloc[64])
{
    uint32_t w[64];
    for (int i = 0; i < 16; i++) {
        w[i] = ((uint32_t)bloc[i * 4] << 24) | ((uint32_t)bloc[i * 4 + 1] << 16) | ((uint32_t)bloc[i * 4 + 2] << 8) | bloc[i * 4 + 3];
    }
    for (int i = 16; i < 64; i++) {
        uint32_t s0 = rotation(w[i - 15], 7) ^ rotation(w[i - 15], 18) ^ (w[i - 15] >> 3);
        uint32_t s1 = rotation(w[i - 2], 17) ^ rotation(w[i - 2], 19) ^ (w[i - 2] >> 10);
        w[i] = w[i - 16] + s0 + w[i - 7] + s1;
    }
    uint32_t a = c->etat[0], b = c->etat[1], d = c->etat[3], e = c->etat[4], f = c->etat[5], g = c->etat[6], h = c->etat[7];
    uint32_t cc = c->etat[2];
    for (int i = 0; i < 64; i++) {
        uint32_t s1 = rotation(e, 6) ^ rotation(e, 11) ^ rotation(e, 25);
        uint32_t choix = (e & f) ^ (~e & g);
        uint32_t t1 = h + s1 + choix + K[i] + w[i];
        uint32_t s0 = rotation(a, 2) ^ rotation(a, 13) ^ rotation(a, 22);
        uint32_t majorite = (a & b) ^ (a & cc) ^ (b & cc);
        uint32_t t2 = s0 + majorite;
        h = g;
        g = f;
        f = e;
        e = d + t1;
        d = cc;
        cc = b;
        b = a;
        a = t1 + t2;
    }
    c->etat[0] += a;
    c->etat[1] += b;
    c->etat[2] += cc;
    c->etat[3] += d;
    c->etat[4] += e;
    c->etat[5] += f;
    c->etat[6] += g;
    c->etat[7] += h;
}

void fg_sha256_debut(fg_sha256_t *c)
{
    static const uint32_t DEPART[8] = {0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19};
    memcpy(c->etat, DEPART, sizeof DEPART);
    c->octets = 0;
    c->rempli = 0;
}

void fg_sha256_ajouter(fg_sha256_t *c, const uint8_t *donnees, size_t taille)
{
    c->octets += taille;
    while (taille > 0) {
        size_t place = 64 - c->rempli;
        size_t pris = taille < place ? taille : place;
        memcpy(c->bloc + c->rempli, donnees, pris);
        c->rempli += pris;
        donnees += pris;
        taille -= pris;
        if (c->rempli == 64) {
            transformer(c, c->bloc);
            c->rempli = 0;
        }
    }
}

void fg_sha256_fin(fg_sha256_t *c, uint8_t empreinte[FG_SHA256_TAILLE])
{
    uint64_t bits = c->octets * 8;
    uint8_t bourrage = 0x80;
    fg_sha256_ajouter(c, &bourrage, 1);
    uint8_t zero = 0;
    while (c->rempli != 56) {
        fg_sha256_ajouter(c, &zero, 1);
    }
    uint8_t longueur[8];
    for (int i = 0; i < 8; i++) {
        longueur[i] = (uint8_t)(bits >> (56 - 8 * i));
    }
    fg_sha256_ajouter(c, longueur, 8);
    for (int i = 0; i < 8; i++) {
        empreinte[i * 4] = (uint8_t)(c->etat[i] >> 24);
        empreinte[i * 4 + 1] = (uint8_t)(c->etat[i] >> 16);
        empreinte[i * 4 + 2] = (uint8_t)(c->etat[i] >> 8);
        empreinte[i * 4 + 3] = (uint8_t)c->etat[i];
    }
}

static int valeur_hex(char c)
{
    if (c >= '0' && c <= '9') {
        return c - '0';
    }
    if (c >= 'a' && c <= 'f') {
        return c - 'a' + 10;
    }
    if (c >= 'A' && c <= 'F') {
        return c - 'A' + 10;
    }
    return -1;
}

bool fg_hex_vers_octets(const char *hex, uint8_t *dst, size_t taille)
{
    if (strlen(hex) != taille * 2) {
        return false;
    }
    for (size_t i = 0; i < taille; i++) {
        int haut = valeur_hex(hex[i * 2]);
        int bas = valeur_hex(hex[i * 2 + 1]);
        if (haut < 0 || bas < 0) {
            return false;
        }
        dst[i] = (uint8_t)((haut << 4) | bas);
    }
    return true;
}

/* ------------------------------------------------------------------ JSON */

/* Position de la valeur du champ, juste après « "nom": », ou NULL. */
static const char *valeur_du_champ(const char *json, const char *nom)
{
    size_t longueur = strlen(nom);
    for (const char *p = json; (p = strchr(p, '"')) != NULL; p++) {
        if (strncmp(p + 1, nom, longueur) == 0 && p[1 + longueur] == '"' && p[2 + longueur] == ':') {
            return p + 3 + longueur;
        }
    }
    return NULL;
}

bool fg_json_texte(const char *json, const char *nom, char *dst, size_t capacite)
{
    const char *valeur = valeur_du_champ(json, nom);
    if (valeur == NULL || *valeur != '"') {
        return false;
    }
    valeur++;
    size_t i = 0;
    while (valeur[i] != '"') {
        /* Une chaîne tronquée, trop longue ou échappée n'est pas attendue de la plateforme : elle est refusée. */
        if (valeur[i] == '\0' || valeur[i] == '\\' || i + 1 >= capacite) {
            return false;
        }
        dst[i] = valeur[i];
        i++;
    }
    dst[i] = '\0';
    return true;
}

bool fg_json_entier(const char *json, const char *nom, int64_t *resultat)
{
    const char *valeur = valeur_du_champ(json, nom);
    if (valeur == NULL) {
        return false;
    }
    bool negatif = *valeur == '-';
    if (negatif) {
        valeur++;
    }
    if (*valeur < '0' || *valeur > '9') {
        return false;
    }
    int64_t nombre = 0;
    int chiffres = 0;
    while (*valeur >= '0' && *valeur <= '9') {
        if (++chiffres > 18) {
            return false;
        }
        nombre = nombre * 10 + (*valeur - '0');
        valeur++;
    }
    *resultat = negatif ? -nombre : nombre;
    return true;
}
