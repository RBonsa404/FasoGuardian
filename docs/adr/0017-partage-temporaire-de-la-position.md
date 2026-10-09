# ADR 0017 — Partage temporaire de la position avec un contact d'urgence

Statut : acceptée — 9 octobre 2026

## Contexte

US-SEC-001 : le parent partage temporairement la position de l'enfant avec le contact secondaire, qui « voit la
position pendant la durée définie uniquement » en ouvrant « le lien reçu par SMS » ; le parent peut révoquer, et
« l'accès est interrompu immédiatement ». Le design le montre aux écrans 35 (parent) et 50 (contact, « app
légère » à l'adresse `/p/:token`). Le contact n'a pas de compte.

## Décision

1. **Le partage ne se fait qu'avec un contact d'urgence déjà enregistré** pour l'enfant : le parent ne saisit
   pas un numéro à la volée, ce qui évite l'envoi d'un lien de position à un numéro mal tapé.
2. **Un lien personnel, non devinable.** Le jeton du lien est tiré au hasard (128 bits) ; la base n'en garde
   que l'empreinte SHA-256, et le numéro du contact chiffré. Le lien part par le SMS seul.
3. **Un seul partage à la fois par enfant** : en ouvrir un nouveau révoque le précédent. Durée de 15 minutes à
   12 heures ; l'écran propose 30 min, 1 h, 2 h et 4 h.
4. **Ce que voit le contact, et rien d'autre** : le prénom du parent qui partage, le temps restant, la dernière
   position de moins de deux heures avec sa précision. Ni nom ni prénom de l'enfant, ni historique, ni Safe
   Zones, ni lien vers l'application. Le SMS non plus ne nomme pas l'enfant.
5. **Fin d'accès.** L'échéance et la révocation sont contrôlées à chaque lecture par le serveur ; jeton
   inconnu, partage échu ou révoqué donnent la même réponse, « Ce partage est terminé ». L'écran du contact
   efface la position à l'échéance sans attendre la prochaine interrogation.
6. **Traçabilité.** L'ouverture d'un partage, sa révocation et chaque consultation par le contact sont au
   journal d'audit ; le parent voit le nombre d'ouvertures et l'heure de la dernière.
7. **Conservation.** Les partages terminés sont effacés au bout de trente jours (ils portent le numéro d'un
   tiers) ; la purge est inscrite au registre. L'effacement d'un compte les supprime aussitôt.
8. **La vue du contact est une route publique de l'application Parents**, chargée à la demande, et non une
   seconde application : même hébergement, mêmes composants de carte, rien à installer.

## Écarts et points ouverts

- **Pas de second facteur.** FG-DOC-06 (§8.1) ne range pas le partage parmi les actions sensibles ; il reste
  borné dans le temps, révocable et journalisé. À rediscuter si la CIL le demande.
- **Lieu en clair.** La maquette affiche « Gounghin · près du marché ». La plateforme n'a pas de service de
  géocodage inverse : la carte et les coordonnées tiennent lieu d'adresse. À reprendre avec le fournisseur de
  tuiles retenu pour la production.
- **Adresse du lien.** Elle vient de `FG_URL_PARENTS`, à renseigner en production.
