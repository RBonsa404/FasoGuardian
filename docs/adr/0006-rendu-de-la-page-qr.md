# ADR 0006 — Mode de rendu de la page publique QR

Statut : acceptée — 8 octobre 2026

## Contexte

La page QR doit peser 60 Ko au plus, se charger en moins de 3 s en 2G et fonctionner sans JavaScript, tout
en affichant des données propres à chaque bracelet. FG-DOC-06 la décrit comme « pré-rendue à la
construction » et servie en fichiers statiques, ce qui est incompatible avec un contenu par bracelet sans
JavaScript. Mesure du 7 octobre 2026 : l'application Angular générée par défaut transfère 78 Ko compressés,
dont 67 Ko de bundle JavaScript.

## Options

1. Rendu côté serveur par Angular (Node.js) à chaque requête, bundle client retiré de la page, amélioration
   progressive par un script autonome de moins de 3 Ko (proposition du paquet de design).
   Un processus Node.js supplémentaire est à exploiter.
2. Rendu de la page par le serveur Spring Boot à partir d'un gabarit HTML produit par la construction de
   `public-qr`. Aucun composant supplémentaire en production ; le gabarit reste écrit avec Angular et Tailwind.

## Décision

Option 2. La page est écrite avec Angular et Tailwind dans `frontend/projects/public-qr`, à partir des jetons
du design. Sa construction la pré-rend une fois ; `tools/gabarit-qr.mjs` en tire un gabarit HTML autonome
(feuille de style en ligne, aucun script, aucune ressource externe), versionné dans
`backend/src/main/resources/gabarits/page-qr.html`. Le serveur Spring Boot le remplit à chaque scan
(`GabaritPageQr`) : balises `fg-etat`, `fg-si`, `fg-pour` et marqueurs `[[nom]]`, toute valeur étant échappée.

L'envoi d'un message à la famille est un formulaire HTML classique (`POST /q/{jeton}/prevenir`).

## Conséquences

- Aucun processus Node.js en production ; la page fonctionne sans JavaScript et ne déclenche qu'une requête.
- Poids mesuré le 8 octobre 2026 : 9 Ko compressés pour un budget de 60 Ko. L'intégration continue échoue si
  le gabarit versionné n'est pas à jour, contient un script ou dépasse le budget.
- L'application `public-qr` n'est jamais exécutée dans un navigateur : son paquet JavaScript n'est pas déployé.
- Toute modification de la page impose de relancer `npm run gabarit:qr` et de committer le gabarit.
- Écart de forme avec FG-DOC-06 (§4.1), qui décrit la page comme un fichier statique : elle est rendue par le
  serveur d'application, condition pour afficher des données propres au bracelet sans JavaScript.
