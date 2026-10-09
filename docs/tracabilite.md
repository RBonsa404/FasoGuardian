# Matrice de traçabilité

User story (FG-DOC-05) → exigence (FG-DOC-03) → code → tests → statut. Tenue à jour à la fin de chaque étape.
Une user story n'est « Terminée » que si les cinq critères de la définition du terminé sont remplis
(critères d'acceptation automatisés, écran fidèle au design, autorisation testée, OpenAPI, matrice à jour).

Statuts : **À faire**, **En cours**, **Terminée**, **À valider en laboratoire** (exigence physique : la partie
logicielle est livrée, la preuve relève d'un essai décrit dans `docs/essais/`).

Bilan au 9 octobre 2026 : 0 user story terminée sur 43, 27 en cours (US-PAR-001 à 014, 018, 019, US-ENF-001, 002, US-TRS-001, US-SYS-001 à 003, 006, 010, US-ADM-001, 002, US-SAV-002). Étape 1 « Socle » close.

| User story | Priorité | Exigence | Modules serveur | Applications | Code | Tests | Statut |
|---|---|---|---|---|---|---|---|
| US-PAR-001 | MUST | REQ-MUST-01 | identite | parents, console | `InscriptionParent`, `InstructionKyc`, `Verification`, console `FileKyc` et `InstructionKyc` | `InscriptionEtSessionsIT`, `KycIT`, `inscription.spec.ts`, `verification.spec.ts`, e2e `inscription-kyc.spec.ts` et `console-kyc.spec.ts` | En cours : critères d'acceptation couverts de bout en bout, parent et agent ; contrôle visuel complet des écrans 10, 11, 59 et 60 à faire |
| US-PAR-002 | MUST | REQ-MUST-02 | identite | parents | `ProfilParent`, `CodesSms`, `ControleurProfil`, `MotDePasseOublie`, `Reglages`, `SecondFacteur` | `ProfilIT`, e2e `compte.spec.ts` | En cours : coordonnées modifiées et horodatées, réinitialisation par code, clôture avec second facteur et accusé ; purge effective des données sous 30 jours à faire (module audit) |
| US-PAR-003 | MUST | REQ-MUST-03 | identite, famille | parents, public-qr | `ServiceLiensTutelle`, `AccesEnfant`, `DossierMedical` (contacts), `PagePubliqueQr`, `Contacts` | `FamilleIT`, `PagePubliqueQrIT`, e2e `famille.spec.ts` | En cours : contacts chiffrés, contact visible joignable depuis la page publique, écran 34 ; invitation d'un second tuteur à faire |
| US-PAR-004 | MUST | REQ-MUST-04 | famille | parents | `Familles`, `ControleurFamille`, `FicheEnfantEcran`, `Enfants` | `FamilleIT`, e2e `famille.spec.ts` | En cours : fiche créée à l'approbation du KYC, corrections horodatées et historisées, écrans 17 et 31 ; photo de l'enfant à faire |
| US-PAR-005 | SHOULD | REQ-SHOULD-01 | identite | parents, console | `Verification` (choix « en point d'inscription ») | `KycIT`, `verification.spec.ts` | En cours : parcours accompagné sans photo ; prise de rendez-vous et saisie par l'agent à faire ; critère « sans aide dans la majorité des cas » à valider au pilote |
| US-PAR-006 | MUST | REQ-MUST-09 | telemetrie, geolocalisation | parents | `Ingestion`, `Positions`, `ControleurPosition`, `DepotTelemetrie`, `CarteEnfant`, `Carte` | `TelemetrieIT`, `apercu-enfant.spec.ts`, e2e `zones.spec.ts` | En cours : dernière position avec horodatage et précision sur le tableau de bord (écran 16) et la carte (écran 18), rafraîchie chaque minute, accès refusé et journalisé pour un compte non rattaché ; « Localiser maintenant », état « bracelet muet », fraîcheur de 15 minutes de l'offre Essentiel et rafraîchissement à 60 s en mode alerte à faire |
| US-PAR-007 | MUST | REQ-MUST-10 | geolocalisation | parents | `SafeZones`, `SafeZone`, `ZoneCirculaire`, `ZonePolygonale`, `PlageHoraire`, `ControleurZones`, `Zones`, `EditionZone` | `ReglesZonesTest`, `GeolocalisationIT`, e2e `zones.spec.ts` | En cours : les trois critères sont couverts (zone active dans sa plage, suspension réactivable, tolérance), écrans 21 à 23 (ADR 0010) ; nombre de zones par offre à faire avec les abonnements |
| US-PAR-008 | MUST | REQ-MUST-13 | geolocalisation, alertes | parents | `Positions.trajet`, `Trajets`, `Alertes.journal`, `JournalAlertes` | `GeolocalisationIT`, `AlertesIT`, e2e `zones.spec.ts` et `alertes.spec.ts` | En cours : les deux critères sont couverts (trajet sur 30 jours ; journal horodaté et non modifiable, écrans 19 et 20) ; conservation de 90 jours par offre à faire |
| US-SYS-001 | MUST | REQ-MUST-14 | telemetrie, notifications | parents, firmware, simulator | `AbonneMqtt`, `ConfigurationMqtt`, simulateur | `AbonneMqttIT`, essai Compose avec le simulateur | En cours : réception MQTT en TLS mutuel ; repli par SMS signé à faire avec le module alertes |
| US-SYS-002 | MUST | REQ-MUST-15 | telemetrie | parents, firmware, simulator | `Ingestion` (idempotence, heure de mesure d'origine), `PositionRecue` | `TelemetrieIT` | En cours : côté serveur, messages rejoués ignorés et positions tamponnées enregistrées à leur heure d'origine ; tampon du bracelet (firmware) et de l'application (PWA hors ligne) à faire |
| US-SYS-003 | MUST | REQ-MUST-16 | telemetrie | firmware, simulator | `Mesure` (réseau, opérateur), `EtatBracelet`, `MonBracelet` | `TelemetrieIT` | En cours : réseau et opérateur retenus affichés au parent ; la bascule elle-même relève du firmware |
| US-PAR-009 | SHOULD | REQ-SHOULD-03 | telemetrie, geolocalisation | parents | `ApercuEnfant`, `CarteEnfant` (précise / approximative, cercle d'incertitude) | `TelemetrieIT`, `apercu-enfant.spec.ts` | En cours : les deux critères sont couverts sur le tableau de bord ; contrôle visuel du cercle d'incertitude avec une position approximative réelle à faire |
| US-SEC-001 | SHOULD | REQ-SHOULD-04 | famille | parents |  |  | À faire |
| US-SYS-004 | COULD | REQ-COULD-06 | dispositifs, telemetrie | console, simulator |  |  | À faire |
| US-ENF-001 | MUST | REQ-MUST-11 | telemetrie, geolocalisation, alertes, notifications | parents, firmware | `Surveillance`, `SuiviZone`, `Declenchement`, `OuvertureAlertes`, `Alerte`, `CentreAlertes`, `EcranAlerte` | `ReglesZonesTest`, `ReglesAlertesTest`, `GeolocalisationIT`, `AlertesIT`, e2e `alertes.spec.ts` | En cours : sortie de zone, retrait non autorisé et batterie critique ouvrent une alerte notifiée au parent, écrans 24 à 26 (ADR 0010, 0011) ; notification push à faire (module notifications), le SMS de repli à 60 s est en place |
| US-ENF-002 | MUST | REQ-MUST-11 | telemetrie, alertes, notifications | parents, firmware | `Declenchement`, `OuvertureAlertes`, `EcranAlerte` | `AlertesIT`, e2e `alertes.spec.ts` (SOS publié en MQTT) | En cours : SOS reçu, alerte critique ouverte avec la dernière position, SMS immédiat, écran 25 ; push, mode alerte du bracelet (commande signée) et critères propres au bracelet (appui de 3 s, vibration) à faire |
| US-PAR-010 | MUST | REQ-MUST-12 | alertes | parents, console | `Alertes` (signalement, levée, fausse alerte), `Alerte.escalader`, `CentreAlertes` | `ReglesAlertesTest`, `AlertesIT`, e2e `alertes.spec.ts` | En cours : signalement horodaté par le parent, levée et fausse alerte avec motif journalisé ; escalade sous second facteur, dossier de signalement et passerelle vers les forces de sécurité à faire |
| US-SYS-005 | COULD | REQ-COULD-05 | alertes, notifications | parents |  |  | À faire |
| US-FDS-001 | COULD | Complémentaire | alertes | console, parents |  |  | À faire |
| US-PAR-011 | MUST | REQ-MUST-05 | famille | parents, public-qr | `DossierMedical`, `PagePubliqueQr`, `Medical` | `FamilleIT`, `PagePubliqueQrIT`, e2e `famille.spec.ts` | En cours : les deux critères sont couverts (élément critique projeté sur la page publique, journal de révision), écran 32 ; contrôle visuel de l'écran à faire |
| US-TRS-001 | MUST | REQ-MUST-06 | famille | public-qr | `PagePubliqueQr`, `ControleurPageQr`, `GabaritPageQr`, `public-qr/app.html`, `tools/gabarit-qr.mjs` | `PagePubliqueQrIT`, `public-qr/app.spec.ts`, e2e `page-qr.spec.ts` | En cours : page minimale sans nom ni position, page générique, 9 Ko sans script, consultation journalisée ; rattachement du jeton à l'appairage (dispositifs) et mesure en 2G réelle à faire |
| US-PAR-012 | MUST | REQ-MUST-17 | alertes, dispositifs | parents, firmware | `Retraits`, `AutorisationRetrait`, `Retrait` | `ReglesAlertesTest`, `AlertesIT`, e2e `alertes.spec.ts` | En cours : fenêtre de 15 min à 12 h sous second facteur, retrait noté sans alerte, rappel puis alerte à l'échéance, écran 38 ; confirmation par le bracelet (commande signée) à faire |
| US-ADM-001 | MUST | REQ-MUST-23 | identite, plateforme, audit | console | `AgentsInternes`, `ControleurAgents`, `JournalisationRefus`, console `Connexion`, `Structure`, `Refuse` | `AgentsEtAuditIT`, `KycIT`, e2e `console-kyc.spec.ts` | En cours : agents, TOTP obligatoire, cloisonnement, refus journalisés, écrans 55, 56 et 58 ; gestion des agents (écran 69), verrouillage après inactivité et cloisonnement des positions à faire |
| US-SYS-006 | MUST | REQ-MUST-24 | plateforme, identite, famille | site | `ServiceChiffrement`, `InstructionKyc` | `ServiceChiffrementTest`, `InscriptionEtSessionsIT`, `KycIT` | En cours : téléphone, identités et pièces KYC chiffrés ; santé à venir avec son module |
| US-ADM-002 | MUST | REQ-MUST-25 | audit | console | `ServiceJournalAudit`, V3 (table en ajout seul) | `AgentsEtAuditIT`, `KycIT` | En cours : journal chaîné, contrôle quotidien, altération détectée, consultations KYC journalisées ; consultation du journal (écran 70) à faire |
| US-ADM-003 | MUST | REQ-MUST-26 | audit | console, site |  |  | À faire |
| US-ADM-004 | SHOULD | REQ-SHOULD-02 | plateforme | console |  |  | À faire |
| US-PAR-013 | MUST | REQ-MUST-07 | dispositifs | parents, console, firmware | `FabriqueBracelet`, `ConfigurationBracelet`, `Appairages` (activation, mode économie), `MonBracelet` | `ReglesBraceletTest`, `DispositifsIT`, e2e `bracelet.spec.ts` | En cours : bracelet actif dès l'appairage, configuration par défaut de la révision, mode économie journalisé et notifié, écran 37 ; mise à jour signée du logiciel embarqué, envoi de la configuration au bracelet (télémétrie) et aperçu 3D à faire |
| US-PAR-014 | MUST | REQ-MUST-08 | dispositifs, famille | parents, console, public-qr | `Appairages`, `CodeAppairage`, `Bracelet`, `ProfilsQr`, `ControleurBracelet`, `AssocierBracelet`, `PerteBracelet` | `ReglesBraceletTest`, `DispositifsIT`, `code-appairage.spec.ts`, e2e `bracelet.spec.ts` | En cours : les deux critères sont couverts (code distinct du QR ; vol → page désactivée et certificat révoqué), perte avec suivi 72 h, désappairage, écrans 36 et 39 (ADR 0009) ; application de la révocation par le broker et demande de remplacement à faire |
| US-SYS-007 | MUST | REQ-MUST-18 | dispositifs, notifications | parents, firmware, site |  |  | À faire |
| US-SAV-001 | MUST | REQ-MUST-19 | dispositifs, telemetrie | console, parents |  |  | À faire |
| US-SAV-002 | MUST | REQ-MUST-20 | dispositifs | console | `Parc`, `ControleurParc` | `DispositifsIT` | En cours : API du parc (enregistrement, vue par statut, retour « En SAV », remise en stock, réforme, fiche journalisée) ; écrans 64 et 65 de la console à faire |
| US-PAR-015 | MUST | REQ-MUST-21 | abonnements | parents, site |  |  | À faire |
| US-SYS-008 | MUST | REQ-MUST-22 | abonnements, famille | parents, public-qr |  |  | À faire |
| US-PAR-016 | COULD | REQ-COULD-01 | abonnements, notifications | parents |  |  | À faire |
| US-PAR-017 | COULD | REQ-COULD-02 | identite | parents, console |  |  | À faire |
| US-SUP-001 | COULD | REQ-COULD-03 | identite | console, parents, site |  |  | À faire |
| US-SYS-009 | COULD | REQ-COULD-04 | dispositifs | firmware |  |  | À faire |
| US-SYS-010 | SHOULD | REQ-MUST-06 (sécurité) | famille, audit | public-qr, console | `PagePubliqueQr` (compteurs par source) | `PagePubliqueQrIT` | En cours : blocage après 20 jetons invalides par minute, délai constant, entrée d'audit ; alerte visible de l'administrateur (écran 73) à faire |
| US-PAR-018 | SHOULD | REQ-MUST-11 (ergonomie) | alertes | parents | `CentreAlertes` (regroupement par enfant et par gravité), `Alertes.prendreEnCharge` | `AlertesIT` | En cours : alertes regroupées, SOS en tête, une seule prise en charge ; test d'interface du regroupement à écrire |
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
