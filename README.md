# FasoGuardian

Système intégré de sécurisation, d'identification et de géolocalisation des enfants au Burkina Faso :
un bracelet connecté, une plateforme serveur et des applications web pour les parents, les agents internes
et toute personne qui trouve un enfant. Projet du Collectif Dedsec (Ouagadougou).

## État d'avancement

Étape 1 « Socle » close le 8 octobre 2026 ; étape 2 en cours (module `identite` : inscription et sessions).
voir [docs/tracabilite.md](docs/tracabilite.md) pour le statut de chacune des 43 user stories.

## Organisation du dépôt

| Dossier | Contenu |
|---|---|
| `backend/` | Serveur Spring Boot 4 en monolithe modulaire : neuf modules métier et un socle technique |
| `frontend/` | Espace de travail Angular 22 : `site`, `parents`, `public-qr`, `console`, bibliothèques `ui` et `api` |
| `simulator/` | Simulateur de bracelets (MQTT en TLS mutuel) |
| `firmware/` | Logiciel embarqué du bracelet (à venir) |
| `infra/` | Docker Compose, Mosquitto, Nginx, Prometheus, Grafana, Loki |
| `docs/` | ADR, protocoles d'essai, matrice de traçabilité |

## Prérequis

- JDK 21 (la variable `JAVA_HOME` doit pointer dessus), Node.js 24, Docker avec Compose, OpenSSL.
- Maven n'a pas besoin d'être installé : le dépôt fournit `backend/mvnw`.

## Démarrage

Préparation, une seule fois : créer le fichier `.env` à partir de `.env.example`, y renseigner les mots de
passe, puis générer les certificats de développement.

```bash
cp .env.example .env
```

```bash
sh infra/generer-certificats-dev.sh
```

Démarrage de tout l'environnement (base, broker, serveur, proxy, observabilité) :

```bash
docker compose --env-file .env -f infra/docker-compose.yml up --build
```

| Service | Adresse |
|---|---|
| API par le proxy (certificat de développement auto-signé) | https://localhost:8443/api/v1/ |
| Broker MQTT (TLS mutuel) | ssl://localhost:8883 |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 |

Applications clientes en développement :

```bash
cd frontend && npm ci && npx ng serve parents
```

Simulateur de bracelets :

```bash
cd simulator && ../backend/mvnw -f pom.xml package && java -jar target/fasoguardian-simulateur-0.1.0-SNAPSHOT.jar --certificats=../infra/certs --bracelets=FG-DEV-0001,FG-DEV-0002 --intervalle=PT10S --duree=PT1M
```

## Tests

```bash
cd backend && ./mvnw verify
```

```bash
cd frontend && npm test && npm run build && npm run budgets -- parents
```

Les tests d'intégration du serveur démarrent PostgreSQL / PostGIS avec Testcontainers : Docker doit être lancé.

## Architecture

Client-serveur à trois niveaux : applications Angular servies en fichiers statiques, serveur Spring Boot
derrière Nginx, PostgreSQL / PostGIS. Les bracelets communiquent par MQTT en TLS avec authentification
mutuelle, avec repli par SMS signé. Le serveur est découpé en neuf modules (`identite`, `famille`,
`dispositifs`, `telemetrie`, `geolocalisation`, `alertes`, `notifications`, `abonnements`, `audit`) dont les
frontières sont vérifiées à chaque construction.

## Documentation

- Documents de conception FG-DOC-01 à FG-DOC-11 : diffusion restreinte, conservés hors du dépôt
- [Décisions d'architecture](docs/adr/README.md)
- [Matrice de traçabilité](docs/tracabilite.md)
- [Protocoles d'essai](docs/essais/README.md)
- [Guide de contribution](CONTRIBUTING.md)

## Données personnelles

Le projet traite des données d'identité, de santé et de géolocalisation de mineurs. Les données de
démonstration sont fictives. La mise en production avec des données réelles est subordonnée à l'analyse
d'impact (AIPD), à la désignation d'un délégué à la protection des données et aux formalités auprès de la CIL.

## Licence

À définir par le Collectif Dedsec.
