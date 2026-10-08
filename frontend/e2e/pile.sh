#!/usr/bin/env sh
# Pile locale des tests de bout en bout : PostgreSQL vierge (Docker), serveur sous le profil dev avec
# l'adaptateur SMS bac à sable, applications Parents (4201) et Console (4202).
# Les secrets de la pile sont tirés au hasard à chaque démarrage et gardés dans e2e/.etat (ignoré par Git).
#
#   sh e2e/pile.sh demarrer   démarre la pile (JAVA_HOME doit pointer sur un JDK 21)
#   sh e2e/pile.sh tester     joue les tests Playwright contre la pile
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

case "${1:-}" in
  demarrer)
    rm -rf "$ETAT" && mkdir -p "$ETAT"
    {
      echo "FG_DB_NAME=fasoguardian"
      echo "FG_DB_USER=fasoguardian"
      echo "FG_DB_PASSWORD=$(openssl rand -hex 16)"
      echo "FG_DB_PORT=55432"
      echo "FG_GRAFANA_ADMIN_PASSWORD=$(openssl rand -hex 16)"
      echo "FG_JWT_SECRET=$(openssl rand -base64 32)"
      echo "FG_ADMIN_IDENTIFIANT=admin.e2e"
      echo "FG_ADMIN_MOT_DE_PASSE=$(openssl rand -hex 12)"
      for cle in EMPREINTE TELEPHONE PIECE_KYC SANTE SECRET_MFA IMEI; do
        echo "FG_CLE_$cle=$(openssl rand -base64 32)"
      done
    } > "$ENV"
    $COMPOSE up -d --wait postgres
    (cd "$RACINE/backend" && ./mvnw -B -q package -DskipTests)
    (
      set -a && . "$ENV" && set +a
      export FG_DB_URL="jdbc:postgresql://localhost:55432/fasoguardian" FG_SMS_ADAPTATEUR=bac-a-sable SPRING_PROFILES_ACTIVE=dev
      nohup "${JAVA_HOME:?JAVA_HOME doit pointer sur un JDK 21}/bin/java" -jar "$RACINE"/backend/target/fasoguardian-backend-*.jar \
        > "$ETAT/serveur.log" 2>&1 &
    )
    (cd "$FRONTEND" && nohup npx ng serve parents --port 4201 --proxy-config proxy.dev.json > "$ETAT/parents.log" 2>&1 &)
    (cd "$FRONTEND" && nohup npx ng serve console --port 4202 --proxy-config proxy.dev.json > "$ETAT/console.log" 2>&1 &)
    attendre http://localhost:8080/api/v1/dev/sms
    attendre http://localhost:4201/
    attendre http://localhost:4202/
    echo "Pile prête : serveur 8080, parents 4201, console 4202"
    ;;
  tester)
    set -a && . "$ENV" && set +a
    shift
    cd "$FRONTEND" && npx playwright test -c e2e/playwright.config.ts "$@"
    ;;
  arreter)
    for port in 8080 4201 4202; do arreter_port "$port"; done
    [ -f "$ENV" ] && $COMPOSE down -v
    rm -rf "$ETAT"
    ;;
  *)
    echo "Usage : sh e2e/pile.sh demarrer | tester [arguments Playwright] | arreter"
    exit 1
    ;;
esac
