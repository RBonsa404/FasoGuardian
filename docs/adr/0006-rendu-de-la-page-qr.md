# ADR 0006 — Mode de rendu de la page publique QR

Statut : proposée — à trancher à l'étape « famille et page QR »

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

À prendre avec le module `famille`. L'option 2 est privilégiée à ce stade, parce qu'elle n'ajoute aucun
composant à exploiter pour une équipe de quatre personnes.

## Conséquences

Tant que la décision n'est pas appliquée, le budget de 60 Ko de `public-qr` n'est pas tenu ; la vérification
bloquante en intégration continue ne porte que sur `parents`.
