# ADR 0004 — Versions figées au démarrage

Statut : acceptée — 7 octobre 2026

## Contexte

FG-DOC-06 (tableau 5) donne des versions cibles ; les versions exactes sont figées dans `pom.xml` et
`package.json` après vérification de leur existence et de leur compatibilité (dépôts Maven Central et npm
consultés le 7 octobre 2026).

## Décision

| Composant | Cible FG-DOC-06 | Version figée | Remarque |
|---|---|---|---|
| Java | 21 LTS minimum | 21 | Passage à 25 LTS possible : aucun blocage identifié, à valider sur l'intégration continue |
| Spring Boot | 4.0.x | 4.0.8 | Dernière 4.0.x ; la branche 4.1 existe mais n'est pas la cible |
| Spring Modulith | 2.0.x | 2.0.8 | |
| Flyway | 11.x | 11.14.1 | Gérée par Spring Boot |
| Hibernate ORM / Spatial | alignés | 7.2.24 | Gérée par Spring Boot |
| springdoc-openapi | 3.x | 3.0.3 | |
| ShedLock | 6.x | **7.10.1** | Écart : la branche 6.x vise Spring 6 ; la 7.x est celle qui prend en charge Spring Framework 7 |
| Testcontainers | stable | 2.0.5 | Gérée par Spring Boot |
| Angular | ≥ 20 | 22.2 | Version active |
| Tailwind CSS | 4.x | 4.3.3 | |
| TypeScript | — | 6.0.3 | Version retenue par Angular 22 |
| Vitest | stable | 5.0.3 | Lanceur de tests par défaut d'Angular 22 |
| PostgreSQL / PostGIS | 17 / 3.5 | image `postgis/postgis:17-3.5` | |
| Mosquitto | 2.x | image `eclipse-mosquitto:2.0` | |

## Conséquences

Toute montée de version passe par une demande de changement tracée (FG-DOC-06 §4.2).
Les dépendances du frontend sont déclarées sans intervalle (`^`, `~`).
