#!/usr/bin/env sh
# Génère une autorité de certification de DÉVELOPPEMENT et les certificats du broker, du proxy,
# du serveur d'application et de bracelets simulés. Rien de ce qui est produit ici ne doit servir
# hors d'un poste de développement : le dossier infra/certs est ignoré par Git.
# Usage : infra/generer-certificats-dev.sh [identifiant-bracelet ...]
set -eu

DOSSIER="$(cd "$(dirname "$0")" && pwd)/certs"
JOURS=365
mkdir -p "$DOSSIER"
cd "$DOSSIER"

# Évite la conversion de chemins de Git Bash sur les sujets X.509 (« /CN=... »).
export MSYS_NO_PATHCONV=1

if [ ! -f ca.key ]; then
  openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out ca.key
  openssl req -x509 -new -key ca.key -sha256 -days "$JOURS" -subj "/O=FasoGuardian DEV/CN=AC de developpement" -out ca.crt
fi

emettre() { # nom-de-fichier, nom commun, noms alternatifs (facultatif)
  [ -f "$1.crt" ] && return 0
  # Clé au format PKCS#8, lisible aussi bien par Mosquitto et Nginx que par Java.
  openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out "$1.key"
  openssl req -new -key "$1.key" -subj "/O=FasoGuardian DEV/CN=$2" -out "$1.csr"
  if [ -n "${3:-}" ]; then
    printf 'subjectAltName=%s\n' "$3" > "$1.ext"
    openssl x509 -req -in "$1.csr" -CA ca.crt -CAkey ca.key -CAcreateserial -sha256 -days "$JOURS" -extfile "$1.ext" -out "$1.crt" 2>/dev/null
    rm -f "$1.ext"
  else
    openssl x509 -req -in "$1.csr" -CA ca.crt -CAkey ca.key -CAcreateserial -sha256 -days "$JOURS" -out "$1.crt" 2>/dev/null
  fi
  rm -f "$1.csr"
}

emettre broker mosquitto "DNS:mosquitto,DNS:localhost,IP:127.0.0.1"
emettre proxy localhost "DNS:localhost,IP:127.0.0.1"
emettre serveur serveur-fasoguardian
for bracelet in "${@:-FG-DEV-0001 FG-DEV-0002}"; do
  for id in $bracelet; do
    emettre "bracelet-$id" "$id"
  done
done

chmod 644 ./*.key 2>/dev/null || true
echo "Certificats de développement disponibles dans $DOSSIER"
