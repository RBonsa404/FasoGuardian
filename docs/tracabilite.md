# Matrice de traçabilité

User story (FG-DOC-05) → exigence (FG-DOC-03) → code → tests → statut. Tenue à jour à la fin de chaque étape.
Une user story n'est « Terminée » que si les cinq critères de la définition du terminé sont remplis
(critères d'acceptation automatisés, écran fidèle au design, autorisation testée, OpenAPI, matrice à jour).

Statuts : **À faire**, **En cours**, **Terminée**, **À valider en laboratoire** (exigence physique : la partie
logicielle est livrée, la preuve relève d'un essai décrit dans `docs/essais/`).

Bilan au 8 octobre 2026 : 0 user story terminée sur 43, 5 en cours (US-PAR-001, US-PAR-019, US-SYS-006, US-ADM-001, US-ADM-002). Étape 1 « Socle » close.

| User story | Priorité | Exigence | Modules serveur | Applications | Code | Tests | Statut |
|---|---|---|---|---|---|---|---|
| US-PAR-001 | MUST | REQ-MUST-01 | identite | parents, console | `InscriptionParent`, `ControleurAuthentification` | `InscriptionEtSessionsIT`, `ReglesIdentiteTest`, `inscription.spec.ts` | En cours : inscription et session du parent, écrans 10 et 12 ; KYC et écran 11 à faire |
| US-PAR-002 | MUST | REQ-MUST-02 | identite, audit | parents |  |  | À faire |
| US-PAR-003 | MUST | REQ-MUST-03 | identite, famille | parents |  |  | À faire |
| US-PAR-004 | MUST | REQ-MUST-04 | famille | parents |  |  | À faire |
| US-PAR-005 | SHOULD | REQ-SHOULD-01 | identite | parents, console, site |  |  | À faire |
| US-PAR-006 | MUST | REQ-MUST-09 | telemetrie, geolocalisation | parents |  |  | À faire |
| US-PAR-007 | MUST | REQ-MUST-10 | geolocalisation | parents |  |  | À faire |
| US-PAR-008 | MUST | REQ-MUST-13 | geolocalisation, alertes | parents |  |  | À faire |
| US-SYS-001 | MUST | REQ-MUST-14 | telemetrie, notifications | parents, firmware, simulator |  |  | Socle : simulateur minimal (télémétrie MQTT) ; repli SMS à venir |
| US-SYS-002 | MUST | REQ-MUST-15 | telemetrie | parents, firmware, simulator |  |  | Socle : registre de publication des événements persisté |
| US-SYS-003 | MUST | REQ-MUST-16 | telemetrie | firmware, simulator |  |  | À faire |
| US-PAR-009 | SHOULD | REQ-SHOULD-03 | telemetrie, geolocalisation | parents |  |  | À faire |
| US-SEC-001 | SHOULD | REQ-SHOULD-04 | famille | parents |  |  | À faire |
| US-SYS-004 | COULD | REQ-COULD-06 | dispositifs, telemetrie | console, simulator |  |  | À faire |
| US-ENF-001 | MUST | REQ-MUST-11 | telemetrie, geolocalisation, alertes, notifications | parents, firmware |  |  | À faire |
| US-ENF-002 | MUST | REQ-MUST-11 | telemetrie, alertes, notifications | parents, firmware |  |  | À faire |
| US-PAR-010 | MUST | REQ-MUST-12 | alertes | parents, console |  |  | À faire |
| US-SYS-005 | COULD | REQ-COULD-05 | alertes, notifications | parents |  |  | À faire |
| US-FDS-001 | COULD | Complémentaire | alertes | console, parents |  |  | À faire |
| US-PAR-011 | MUST | REQ-MUST-05 | famille | parents |  |  | À faire |
| US-TRS-001 | MUST | REQ-MUST-06 | famille | public-qr, site |  |  | À faire |
| US-PAR-012 | MUST | REQ-MUST-17 | alertes, dispositifs | parents, firmware |  |  | À faire |
| US-ADM-001 | MUST | REQ-MUST-23 | identite, plateforme, audit | console | `AgentsInternes`, `ControleurAgents`, `JournalisationRefus` | `AgentsEtAuditIT` | En cours : comptes d'agents, TOTP obligatoire, cloisonnement et refus journalisés ; écrans 55 à 58 et 69 à faire |
| US-SYS-006 | MUST | REQ-MUST-24 | plateforme, identite, famille | site | `ServiceChiffrement` | `ServiceChiffrementTest`, `InscriptionEtSessionsIT` | En cours : téléphone chiffré ; pièces KYC et santé à venir avec leurs modules |
| US-ADM-002 | MUST | REQ-MUST-25 | audit | console | `ServiceJournalAudit`, V3 (table en ajout seul) | `AgentsEtAuditIT` | En cours : journal chaîné, contrôle quotidien, altération détectée ; consultation (écran 70) à faire |
| US-ADM-003 | MUST | REQ-MUST-26 | audit | console, site |  |  | À faire |
| US-ADM-004 | SHOULD | REQ-SHOULD-02 | plateforme | console |  |  | À faire |
| US-PAR-013 | MUST | REQ-MUST-07 | dispositifs | parents, console, firmware |  |  | À faire |
| US-PAR-014 | MUST | REQ-MUST-08 | dispositifs, famille | parents, console, public-qr |  |  | À faire |
| US-SYS-007 | MUST | REQ-MUST-18 | dispositifs, notifications | parents, firmware, site |  |  | À faire |
| US-SAV-001 | MUST | REQ-MUST-19 | dispositifs, telemetrie | console, parents |  |  | À faire |
| US-SAV-002 | MUST | REQ-MUST-20 | dispositifs | console |  |  | À faire |
| US-PAR-015 | MUST | REQ-MUST-21 | abonnements | parents, site |  |  | À faire |
| US-SYS-008 | MUST | REQ-MUST-22 | abonnements, famille | parents, public-qr |  |  | À faire |
| US-PAR-016 | COULD | REQ-COULD-01 | abonnements, notifications | parents |  |  | À faire |
| US-PAR-017 | COULD | REQ-COULD-02 | identite | parents, console |  |  | À faire |
| US-SUP-001 | COULD | REQ-COULD-03 | identite | console, parents, site |  |  | À faire |
| US-SYS-009 | COULD | REQ-COULD-04 | dispositifs | firmware |  |  | À faire |
| US-SYS-010 | SHOULD | REQ-MUST-06 (sécurité) | famille | public-qr, console |  |  | À faire |
| US-PAR-018 | SHOULD | REQ-MUST-11 (ergonomie) | alertes | parents |  |  | À faire |
| US-SYS-011 | MUST | REQ-MUST-07 (sécurité) | dispositifs | firmware |  |  | À faire |
| US-PAR-019 | SHOULD | REQ-MUST-02 (sécurité) | identite | parents | `Sessions` | `InscriptionEtSessionsIT` | En cours : rotation et révocation côté serveur, réauthentification à l'écran de connexion ; écran 14 et consultation hors ligne à faire |
| US-KYC-001 | SHOULD | REQ-MUST-01 (exception) | identite | console |  |  | À faire |

## Exigences système vérifiées par le socle

| Exigence | Vérification en place |
|---|---|
| REQ-SYS-010 (modules vérifiés à la construction) | `ModulariteTest`, `ArchitectureHexagonaleTest` |
| REQ-SYS-011 (PostgreSQL 17 / PostGIS 3.5) | `SocleIT` sur conteneur `postgis/postgis:17-3.5` |
| REQ-SYS-012 (mTLS et contrôle d'accès par bracelet) | Vérifié manuellement sur Mosquitto (4 scénarios) et par le simulateur ; test automatisé à ajouter avec `telemetrie` |
| REQ-SYS-009 (budget de 250 Ko de la PWA) | `tools/verifier-budgets.mjs parents`, bloquant en intégration continue |
