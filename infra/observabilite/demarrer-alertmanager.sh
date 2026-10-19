#!/bin/sh
# Complète le gabarit d'Alertmanager avec les variables FG_ALERTES_* puis lance Alertmanager. Les valeurs ne sont
# écrites que dans le système de fichiers du conteneur, jamais dans le dépôt. Le remplacement est littéral (awk),
# et « \ » et « " » sont échappés pour rester dans une chaîne YAML entre guillemets : un mot de passe peut
# contenir n'importe quel caractère.
set -eu
config=/tmp/alertmanager.yml
awk -v noms="FG_ALERTES_SMTP_HOTE FG_ALERTES_SMTP_UTILISATEUR FG_ALERTES_SMTP_MOT_DE_PASSE FG_ALERTES_DE FG_ALERTES_A" '
  BEGIN {
    n = split(noms, liste, " ")
    for (i = 1; i <= n; i++) {
      v = ENVIRON[liste[i]]
      gsub(/\\/, "\\\\", v)
      gsub(/"/, "\\\"", v)
      valeurs[i] = v
    }
  }
  {
    ligne = $0
    for (i = 1; i <= n; i++) {
      marque = "__" liste[i] "__"
      while ((p = index(ligne, marque)) > 0) {
        ligne = substr(ligne, 1, p - 1) valeurs[i] substr(ligne, p + length(marque))
      }
    }
    print ligne
  }' /etc/alertmanager/gabarit.yml > "$config"
exec /bin/alertmanager --config.file="$config" --storage.path=/alertmanager
