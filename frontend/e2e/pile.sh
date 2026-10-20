#!/usr/bin/env sh
# Pile locale des tests de bout en bout : PostgreSQL vierge (Docker), serveur sous le profil dev avec
# l'adaptateur SMS bac à sable, broker Mosquitto en TLS mutuel (8883), applications Parents (4201) et Console (4202), site vitrine (4203).
# Les secrets de la pile sont tirés au hasard à chaque démarrage et gardés dans e2e/.etat (ignoré par Git).
#
#   sh e2e/pile.sh demarrer   démarre la pile (JAVA_HOME doit pointer sur un JDK 21)
#   sh e2e/pile.sh tester     joue les tests Playwright contre la pile
#   sh e2e/pile.sh serveur    reconstruit et relance le seul serveur (base conservée)
#   sh e2e/pile.sh demo       crée les comptes de démonstration (pile fraîche ; docs/tester-en-local.md)
#   sh e2e/pile.sh codes      affiche les codes à six chiffres du moment des agents de démonstration
#   sh e2e/pile.sh bracelet FG-XXXX position|sos|retrait|remis|chute|batterie
#                             fait émettre au bracelet un message, comme le bracelet réel (MQTT, TLS mutuel)
#   sh e2e/pile.sh simulateur FG-XXXX
#                             lance un bracelet simulé qui émet sa position et répond aux commandes signées
#   sh e2e/pile.sh arreter    arrête la pile et supprime la base
set -eu

FRONTEND="$(cd "$(dirname "$0")/.." && pwd)"
RACINE="$(cd "$FRONTEND/.." && pwd)"
ETAT="$FRONTEND/e2e/.etat"
ENV="$ETAT/pile.env"
COMPOSE="docker compose --env-file $ENV -f $RACINE/infra/docker-compose.yml"

attendre() { # url
  i=0
  until [ "$(curl -s -o /dev/null -w '%{http_code}' "$1")" = "200" ]; do
    i=$((i + 1))
    [ "$i" -gt 90 ] && { echo "Délai dépassé pour $1 (journaux dans $ETAT)"; exit 1; }
    sleep 2
  done
}

arreter_port() { # port
  if command -v taskkill >/dev/null 2>&1; then
    pid=$(netstat -ano | grep ":$1 .*LISTENING" | awk '{print $5}' | head -1)
    [ -n "$pid" ] && taskkill //PID "$pid" //F >/dev/null 2>&1 || true
  else
    pid=$(lsof -ti "tcp:$1" 2>/dev/null || true)
    [ -n "$pid" ] && kill $pid 2>/dev/null || true
  fi
}

lancer_serveur() {
  (cd "$RACINE/backend" && ./mvnw -B -q package -DskipTests)
  (
    set -a && . "$ENV" && set +a
    export FG_DB_URL="jdbc:postgresql://localhost:55432/fasoguardian" FG_SMS_ADAPTATEUR=bac-a-sable SPRING_PROFILES_ACTIVE=dev
    export FG_MQTT_URL="ssl://localhost:${FG_MQTT_PORT:-8883}" FG_MQTT_AUTORITE="$RACINE/infra/certs/ca.crt" \
      FG_MQTT_CERTIFICAT="$RACINE/infra/certs/serveur.crt" FG_MQTT_CLE="$RACINE/infra/certs/serveur.key"
    nohup "${JAVA_HOME:?JAVA_HOME doit pointer sur un JDK 21}/bin/java" -jar "$RACINE"/backend/target/fasoguardian-backend-*.jar \
      > "$ETAT/serveur.log" 2>&1 &
  )
}

case "${1:-}" in
  serveur)
    # Reconstruit et relance le seul serveur, en conservant la base et les clients en cours.
    arreter_port 8080
    lancer_serveur
    attendre http://localhost:8080/api/v1/dev/sms
    echo "Serveur relancé"
    ;;
  demarrer)
    rm -rf "$ETAT" && mkdir -p "$ETAT"
    {
      echo "FG_DB_NAME=fasoguardian"
      echo "FG_DB_USER=fasoguardian"
      echo "FG_DB_PASSWORD=$(openssl rand -hex 16)"
      echo "FG_DB_PORT=55432"
      echo "FG_GRAFANA_ADMIN_PASSWORD=$(openssl rand -hex 16)"
      # Alertes d'exploitation : la pile d'essai ne lance pas Alertmanager, ces valeurs ne servent qu'à Compose.
      # Noms de la plateforme : la pile d'essai ne lance pas le proxy, ces valeurs ne servent qu'à Compose.
      echo "FG_DOMAINE=fasoguardian.test"
      echo "FG_DOMAINE_CONSOLE=console.fasoguardian.test"
      echo "FG_ALERTES_SMTP_HOTE=smtp.essais.test:587"
      echo "FG_ALERTES_SMTP_UTILISATEUR=essais"
      echo "FG_ALERTES_SMTP_MOT_DE_PASSE=$(openssl rand -hex 12)"
      echo "FG_ALERTES_DE=alertes@fasoguardian.test"
      echo "FG_ALERTES_A=exploitation@fasoguardian.test"
      echo "FG_JWT_SECRET=$(openssl rand -base64 32)"
      echo "FG_ADMIN_IDENTIFIANT=admin.e2e"
      echo "FG_ADMIN_MOT_DE_PASSE=$(openssl rand -hex 12)"
      for cle in EMPREINTE TELEPHONE PIECE_KYC SANTE SECRET_MFA IMEI PROFIL_ENFANT SIGNALEMENT; do
        echo "FG_CLE_$cle=$(openssl rand -base64 32)"
      done
      # Clé de signature des commandes : privée pour le serveur, publique pour les bracelets simulés.
      openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out "$ETAT/commandes-privee.pem" 2>/dev/null
      openssl pkey -in "$ETAT/commandes-privee.pem" -pubout -out "$ETAT/commandes-publique.pem" 2>/dev/null
      echo "FG_CLE_COMMANDES=$(openssl pkcs8 -topk8 -nocrypt -in "$ETAT/commandes-privee.pem" -outform DER | openssl base64 -A)"
      # Clé de publication du logiciel embarqué : la privée reste dans e2e/.etat, où les tests signent leurs images.
      openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out "$ETAT/ota-privee.pem" 2>/dev/null
      openssl pkey -in "$ETAT/ota-privee.pem" -pubout -out "$ETAT/ota-publique.pem" 2>/dev/null
      echo "FG_OTA_CLE_PUBLIQUE=$(openssl pkey -in "$ETAT/ota-privee.pem" -pubout -outform DER | openssl base64 -A)"
      rm -f "$ETAT/commandes-privee.pem"
      # Clés du serveur d'application pour les notifications push (VAPID) : privée en PKCS#8, publique en point brut.
      openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out "$ETAT/push-privee.pem" 2>/dev/null
      echo "FG_PUSH_CLE_PRIVEE=$(openssl pkcs8 -topk8 -nocrypt -in "$ETAT/push-privee.pem" -outform DER | openssl base64 -A)"
      echo "FG_PUSH_CLE_PUBLIQUE=$(openssl pkey -in "$ETAT/push-privee.pem" -pubout -outform DER | tail -c 65 | openssl base64 -A | tr '+/' '-_' | tr -d '=')"
      echo "FG_PUSH_SUJET=mailto:essais@fasoguardian.test"
      # Paiements : bac à sable qui répond de lui-même au bout de trois secondes (numéro en 00 : refus).
      echo "FG_PAIEMENTS_ADAPTATEUR=bac-a-sable"
      echo "FG_PAIEMENTS_SECRET_WEBHOOK=$(openssl rand -hex 32)"
      echo "FG_PAIEMENTS_VALIDATION_APRES=PT3S"
      echo "FG_OFFRE_D_ACCUEIL=INTERMEDIAIRE"
      # Sceau du serveur de réseau LoRaWAN simulé par les tests.
      echo "FG_LORAWAN_SECRET=$(openssl rand -hex 32)"
      rm -f "$ETAT/push-privee.pem"
    } > "$ENV"
    # Certificats de développement du broker, du serveur et des bracelets simulés (jamais versionnés).
    [ -f "$RACINE/infra/certs/ca.crt" ] || sh "$RACINE/infra/generer-certificats-dev.sh"
    # Une pile laissée par une session précédente (poste redémarré) garderait l'ancienne base et son mot de passe.
    for port in 8080 4201 4202 4203; do arreter_port "$port"; done
    $COMPOSE down -v
    $COMPOSE up -d --wait postgres
    $COMPOSE up -d mosquitto
    lancer_serveur
    (cd "$FRONTEND" && nohup npx ng serve parents --port 4201 --proxy-config proxy.dev.json > "$ETAT/parents.log" 2>&1 &)
    (cd "$FRONTEND" && nohup npx ng serve console --port 4202 --proxy-config proxy.dev.json > "$ETAT/console.log" 2>&1 &)
    (cd "$FRONTEND" && nohup npx ng serve site --port 4203 > "$ETAT/site.log" 2>&1 &)
    attendre http://localhost:8080/api/v1/dev/sms
    attendre http://localhost:4201/
    attendre http://localhost:4202/
    attendre http://localhost:4203/
    echo "Pile prête : serveur 8080, parents 4201, console 4202, site 4203"
    ;;
  tester)
    set -a && . "$ENV" && set +a
    shift
    cd "$FRONTEND" && npx playwright test -c e2e/playwright.config.ts "$@"
    ;;
  demo)
    set -a && . "$ENV" && set +a
    cd "$FRONTEND" && npx playwright test -c e2e/demo/demo.config.ts
    node "$FRONTEND/e2e/demo/resume.mjs"
    ;;
  codes)
    node "$FRONTEND/e2e/demo/codes.mjs"
    ;;
  bracelet)
    numero="${2:?numéro du bracelet, par exemple FG-1234}"
    sh "$RACINE/infra/generer-certificats-dev.sh" "$numero" >/dev/null
    t=$(date +%s)
    # Position autour du centre de Ouagadougou ; la séquence croît avec l'heure pour ne jamais être rejouée.
    point="\"lat\":12.3714,\"lon\":-1.5197,\"acc\":12"
    case "${3:-position}" in
      position) flux=telemetry; message="{\"t\":$t,\"seq\":$t,$point,\"src\":\"gnss\",\"bat\":78,\"rssi\":-79,\"net\":\"4g\",\"op\":\"Orange BF\"}" ;;
      sos) flux=alert; message="{\"t\":$t,\"seq\":$t,\"ev\":\"sos\",$point}" ;;
      retrait) flux=alert; message="{\"t\":$t,\"seq\":$t,\"ev\":\"strap\",$point}" ;;
      remis) flux=alert; message="{\"t\":$t,\"seq\":$t,\"ev\":\"worn\",$point}" ;;
      chute) flux=alert; message="{\"t\":$t,\"seq\":$t,\"ev\":\"fall\",$point}" ;;
      batterie) flux=alert; message="{\"t\":$t,\"seq\":$t,\"ev\":\"batcrit\",$point,\"bat\":8}" ;;
      *) echo "Événement inconnu : position, sos, retrait, remis, chute ou batterie"; exit 1 ;;
    esac
    c=/mosquitto/certs
    MSYS_NO_PATHCONV=1 docker exec fasoguardian-mosquitto-1 mosquitto_pub -h localhost -p 8883 -q 1 -i "$numero-manuel"       --cafile "$c/ca.crt" --cert "$c/bracelet-$numero.crt" --key "$c/bracelet-$numero.key" -t "fg/$numero/$flux" -m "$message"
    echo "$numero : $flux envoyé"
    ;;
  simulateur)
    numero="${2:?numéro du bracelet, par exemple FG-1234}"
    jar="$RACINE/simulator/target/fasoguardian-simulateur-0.1.0-SNAPSHOT.jar"
    [ -f "$jar" ] || (cd "$RACINE/simulator" && ../backend/mvnw -B -q -f pom.xml package -DskipTests)
    sh "$RACINE/infra/generer-certificats-dev.sh" "$numero" >/dev/null
    echo "Bracelet simulé $numero : une position par minute, commandes vérifiées et accusées (Ctrl+C pour l'arrêter)"
    "${JAVA_HOME:?JAVA_HOME doit pointer sur un JDK 21}/bin/java" -jar "$jar" --certificats="$RACINE/infra/certs" --bracelets="$numero"       --intervalle=PT1M --cle-plateforme="$ETAT/commandes-publique.pem" --cle-ota="$ETAT/ota-publique.pem"
    ;;
  arreter)
    for port in 8080 4201 4202 4203; do arreter_port "$port"; done
    [ -f "$ENV" ] && $COMPOSE down -v
    rm -rf "$ETAT"
    ;;
  *)
    echo "Usage : sh e2e/pile.sh demarrer | demo | codes | bracelet FG-XXXX évènement | simulateur FG-XXXX | serveur | tester [arguments Playwright] | arreter"
    exit 1
    ;;
esac
