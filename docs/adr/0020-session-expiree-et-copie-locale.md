# ADR 0020 — Session expirée : réauthentification et fiche consultable hors ligne

Statut : acceptée — 9 octobre 2026

## Contexte

US-PAR-019 : avec « un jeton de rafraîchissement invalide, quand le parent agit, une réauthentification
complète est demandée ; la fiche enfant reste consultable hors ligne entre-temps ». Le design (écran 14) montre,
sous « Votre session a expiré », l'identité de l'enfant, son groupe sanguin, une allergie, le numéro du
bracelet et la date de la « copie locale ». Le brief impose Dexie et un service worker.

## Décision

1. **Copie locale minimale.** À chaque ouverture du tableau de bord, l'application garde dans IndexedDB
   (Dexie, chargé à la demande) : prénom, nom, date de naissance, école, quartier, groupe sanguin, éléments
   médicaux que le parent a marqués critiques, numéro du bracelet, date de la copie. Rien d'autre : ni
   position, ni trajet, ni contact, ni élément médical non critique, ni jeton de session.
2. **Elle ne survit pas à la déconnexion.** « Se déconnecter » et la clôture du compte l'effacent ; une
   relecture encore en cours à ce moment-là n'écrit plus rien. L'expiration de la session, elle, ne l'efface
   pas : c'est précisément le cas où elle sert.
3. **Réauthentification.** Quand le rafraîchissement échoue, au chargement ou en cours d'usage, le parent est
   conduit à l'écran 14 s'il existe une copie locale, sinon à la connexion. L'écran propose de se reconnecter
   et d'appeler le 17.
4. **Ouverture sans réseau.** Le service worker garde les fichiers de l'application (page, scripts, styles,
   polices, icônes), listés à la construction dans `precache.json`, et sert la page gardée quand le réseau
   manque. Il ne garde jamais une réponse de l'API : aucune donnée d'enfant ne passe par ce cache. Il est
   enregistré à l'ouverture du tableau de bord ; l'autorisation de notifier reste demandée à part (ADR 0014).

## Risque accepté

Des données de santé d'un mineur sont gardées sur le téléphone du parent, hors du chiffrement applicatif du
serveur. Elles se limitent à ce que le parent a choisi de marquer critique, c'est-à-dire ce que la page
publique du bracelet peut déjà montrer à un tiers, et elles protègent l'enfant si le parent doit renseigner
un secours sans réseau. Le chiffrement du stockage relève du téléphone. À présenter dans l'AIPD.

## Points ouverts

- Le parcours de bout en bout couvre la session expirée en ligne ; l'ouverture réellement hors ligne demande
  de servir l'application construite, ce que la chaîne d'intégration fera à l'étape de durcissement.
- La mise en file des actions faites hors ligne (écran 49, « 2 actions seront envoyées dès le retour du
  réseau ») n'est pas réalisée : hors ligne, l'application est en lecture seule.
