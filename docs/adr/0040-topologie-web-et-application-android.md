# ADR 0040 — Service des applications web en production et application Android

Statut : acceptée — 20 octobre 2026

## Contexte

En production, le proxy ne servait que l'API : le site public, l'application Parents et la console n'étaient
publiés nulle part, et chacun était construit pour la racine d'un domaine, alors que le site renvoie déjà vers
`/app/` pour l'espace parents. L'APK Android attendue à la livraison doit ouvrir l'application Parents à une
adresse stable. Le porteur a retenu une application web de confiance (Trusted Web Activity) ; l'adresse du
serveur de production n'est pas encore décidée.

## Décision

1. **Un domaine public** (`FG_DOMAINE`) sert le site prérendu à la racine, l'application Parents sous `/app/`,
   la page QR sous `/q/` et l'API sous `/api/`. **La console** a son propre nom (`FG_DOMAINE_CONSOLE`), que
   l'exploitant peut réserver au réseau de l'équipe ; ses appels sont refusés sur le domaine public. Tout autre
   nom reçoit 421. Les noms viennent de `.env` : l'adresse définitive se renseigne au déploiement.
2. **L'application Parents est construite sous `/app/`** en production : le service worker, le manifeste et la
   liste des fichiers gardés hors ligne se résolvent dans la portée de l'application, et le service worker ne
   prend jamais en charge les adresses hors de cette portée.
3. **Politiques de contenu par application.** Parents et console : aucun script en ligne (`script-src 'self'`) ;
   le CSS critique en ligne d'Angular est désactivé pour cela. Le site prérendu garde ses deux scripts en ligne
   (relecture des événements, état) : il ne porte aucune donnée personnelle. L'appareil photo n'est permis qu'à
   l'application Parents (photo des pièces, écran 11b). Les fichiers à empreinte se gardent un an, l'entrée, le
   service worker et le manifeste jamais.
4. **Application Android** (`android/`) : une TWA de paquet `bf.fasoguardian.parents` qui ouvre
   `https://<FG_DOMAINE>/app/`. Le domaine et la clé de signature sont des paramètres de construction ; le proxy
   publie `/.well-known/assetlinks.json` avec l'empreinte de la clé (`FG_ANDROID_EMPREINTE`).

## Conséquences

- Une mise en ligne de la PWA met à jour l'application Android sans republier l'APK.
- Il reste au porteur : choisir le domaine (et le nom de la console), puis fournir l'empreinte de sa clé de
  signature ; l'APK se construit alors en une commande (`android/README.md`).
- Une construction d'essai (`FG_DOMAINE=fasoguardian.test`) vérifie le projet sans domaine réel.
