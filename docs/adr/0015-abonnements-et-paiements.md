# ADR 0015 — Abonnements, paiement mobile money et dégradation en cas d'impayé

Statut : acceptée — 9 octobre 2026

## Contexte

US-PAR-015 demande un paiement Orange Money ou Moov Money « confirmé par l'agrégateur », suivi d'un reçu, et
un renouvellement automatique. US-SYS-008 suspend la géolocalisation continue et l'historique étendu au-delà
de quinze jours d'impayé, « après deux rappels », la page QR restant accessible. FG-DOC-06 (§6.7) impose une
notification HTTPS signée, un en-tête `Idempotency-Key`, et pose deux règles : « aucun abonnement n'est
activé sur la seule déclaration du client » et « aucune donnée de carte ou de compte de paiement n'est
stockée ». FG-DOC-11 (§3.3) décrit les offres, FG-DOC-04 la conservation des positions (30 jours par défaut,
90 au plus). L'agrégateur n'est pas choisi.

## Décision

1. **Un abonnement par enfant**, payé par l'un de ses tuteurs. Les offres sont des données (`abonnements.offre`) :
   Essentiel (1 500 FCFA, position toutes les 15 min, 1 Safe Zone, 30 jours), Intermédiaire (2 250, 5 min,
   3 zones, 30 jours), Premium (5 000, 5 min, 3 zones, 90 jours), École (4 000, non souscriptible par une
   famille : elle relève d'une convention d'établissement, US ultérieure).
2. **Seule la notification signée de l'agrégateur active ou prolonge.** La demande de paiement crée un
   `Paiement` à l'état `INITIE` et rien d'autre. La notification est authentifiée par le port
   `AgregateurPaiement` (dans le bac à sable : HMAC-SHA-256 sur l'horodatage et le corps, comparaison en
   temps constant, tolérance de cinq minutes contre le rejeu), puis le montant annoncé est comparé au montant
   demandé ; un écart n'active rien et est journalisé. Une notification reçue deux fois n'a d'effet qu'une
   fois. Une confirmation tardive est honorée même si la plateforme tenait la demande pour expirée : si
   l'argent a été débité, le service est dû.
3. **Idempotence.** `Idempotency-Key` est obligatoire ; la même clé renvoie le paiement déjà demandé sans
   solliciter le portefeuille. Le renouvellement automatique utilise une clé dérivée de l'abonnement et de
   l'échéance : un traitement relancé ne demande pas deux fois.
4. **Échéance.** Un paiement ajoute un mois à l'échéance en cours, ou part du jour du paiement si elle était
   dépassée : payer en avance ne fait rien perdre, payer en retard ne fait pas payer les jours non servis.
5. **Relances** (traitement quotidien à 7 h 30, heure de Ouagadougou, sous verrou) : J-7 rappel d'échéance,
   J renouvellement automatique s'il est activé, J+1 premier rappel (abonnement « en retard », tout
   fonctionne), J+8 second rappel, J+15 restriction. Au plus une étape par passage et par abonnement : un
   traitement resté plusieurs jours sans tourner ne restreint jamais avant les deux rappels.
6. **Ce que la restriction suspend, et rien d'autre.** Le bracelet reçoit une commande signée `cfg` avec un
   intervalle 0 : il n'émet plus de position périodique, seulement à la demande (« Localiser maintenant ») ou
   en mode alerte. L'historique visible est limité à 24 heures ; les positions conservées ne sont pas
   effacées plus tôt et reviennent au paiement. SOS, détection de retrait, alertes, page publique QR et Safe
   Zones déjà tracées ne dépendent pas de l'abonnement.
7. **Les droits sont une API.** `Droits.de(enfant)` donne aux autres modules le nombre de zones, la profondeur
   d'historique, l'intervalle et l'état du suivi continu ; un événement `DroitsModifies` fait reconfigurer le
   bracelet. Les modules ne lisent jamais les tables d'abonnement.
8. **Premium « plusieurs enfants ».** L'abonnement Premium d'un tuteur couvre ses autres enfants : un enfant
   sans abonnement payé, ou restreint, reçoit les droits Premium tant qu'un de ses tuteurs a un abonnement
   Premium à jour.
9. **Conservation des positions.** La purge nocturne garde 30 jours, et 90 jours pour les enfants dont
   l'offre ouvre l'historique étendu ; rien n'est gardé au-delà de 90 jours (FG-DOC-04).
10. **Reçus.** Numérotation continue `FG-R-AAAA-MM-NNNN` : le compteur avance dans la transaction qui émet le
    reçu, donc sans trou. Le reçu PDF ne porte ni le nom de l'enfant ni le numéro complet du portefeuille.
11. **Adaptateur bac à sable**, seul fourni, activé par `FG_PAIEMENTS_ADAPTATEUR=bac-a-sable` : il ne débite
    rien, garde les demandes en mémoire et, si `FG_PAIEMENTS_VALIDATION_APRES` est renseigné, y répond de
    lui-même par le même chemin signé qu'un agrégateur réel (un numéro se terminant par 00 refuse). Sans
    adaptateur configuré, le serveur ne démarre pas.

## Écarts et points à trancher

- **Numéro du portefeuille conservé.** FG-DOC-06 interdit de stocker « une donnée de compte de paiement »,
  mais le renouvellement automatique de US-PAR-015 exige de savoir quel portefeuille solliciter. Le numéro
  est conservé chiffré (AES-GCM, clé de la catégorie téléphone), affiché masqué (`+226 70 •• •• 56`), jamais
  journalisé ; aucun code secret, solde ou jeton de l'opérateur n'est stocké. Si l'agrégateur retenu fournit
  un mandat ou un jeton de prélèvement, il remplacera ce numéro.
- **Renouvellement « automatique ».** Le mobile money burkinabè exige la validation du client sur son
  téléphone à chaque débit : la plateforme envoie la demande à l'échéance, le parent la valide. Sans mandat de
  l'agrégateur, il n'existe pas de débit sans geste.
- **Enfant sans abonnement payé.** Les documents ne disent pas ce qu'il reçoit. `FG_OFFRE_D_ACCUEIL` désigne
  l'offre dont les droits s'appliquent en attendant ; vide (valeur par défaut), ce sont les droits restreints.
  Les environnements de développement et d'essai utilisent `INTERMEDIAIRE`. **À décider avant le pilote** :
  période d'essai, offre d'accueil, ou paiement exigé dès l'appairage.
- **Premium : « alerte prioritaire, support dédié ».** Les alertes partent déjà sans délai pour toutes les
  offres ; le support dédié sera traité avec les demandes de support (US-PAR-017, US-SUP-001).
- **Achat du bracelet.** Le design montre un reçu « Bracelet FG-2291 — 30 000 F » ; aucune user story ne
  couvre la vente en ligne du bracelet, qui n'est donc pas réalisée.
- **Paiement hors ligne.** Le design prévoit la mise en file de la demande hors connexion ; elle viendra avec
  le fonctionnement hors ligne de l'application (US-PAR-019).
- **Choix de l'agrégateur.** Il revient au porteur du projet. L'adaptateur réel implémentera
  `AgregateurPaiement` (dépôt de la demande, authentification et lecture de la notification selon le schéma
  de signature du prestataire) sans toucher au reste du module.

## Conséquences

- Le serveur exige `FG_PAIEMENTS_ADAPTATEUR` et `FG_PAIEMENTS_SECRET_WEBHOOK` (32 caractères au moins).
- Le protocole du bracelet gagne une valeur : `cfg` avec `int` à 0 (voir `docs/protocole-bracelet.md`) ; le
  firmware devra l'honorer.
- Le nombre de Safe Zones et la profondeur de l'historique ne sont plus des réglages du serveur mais des
  droits de l'offre ; une zone créée sous une offre plus large est conservée après un changement d'offre.
