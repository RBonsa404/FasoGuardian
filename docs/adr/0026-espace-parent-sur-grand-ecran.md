# ADR 0026 — Espace parent sur grand écran : la même application, une autre mise en page

Statut : acceptée — 9 octobre 2026

## Contexte

Le paquet de design ne dessine l'espace parent qu'en 360 × 800 ; HANDOFF §« Déclinaisons » dit le système
« fluide » sans dessiner d'autre taille. Sur un ordinateur, chaque écran s'affichait donc comme une colonne
de téléphone au milieu de la fenêtre. Le porteur du projet demande une vraie version web de l'espace parent,
en plus de l'application mobile.

## Décision

1. **Une seule application.** La version web n'est pas un second produit : c'est l'application Parents,
   avec une mise en page qui change à partir de 1024 px de large. Mêmes écrans, mêmes textes, mêmes règles,
   mêmes essais ; en dessous de 1024 px, rien ne change par rapport aux maquettes.
2. **Une coque pour les écrans du parent connecté.** Sur grand écran, une barre latérale donne accès aux
   rubriques (tableau de bord, alertes, enfants, abonnement, aide, paramètres) et porte la déconnexion ;
   les liens équivalents du tableau de bord sont alors masqués. La coque reprend la barre de la console
   (écran 56), seul modèle de navigation « poste de travail » du paquet de design, avec les mêmes jetons.
3. **Les écrans occupent la fenêtre.** Les écrans de lecture et de saisie passent à une colonne plus large,
   alignée sur la barre. Les écrans à carte donnent la fenêtre à la carte : tableau de bord (carte à gauche,
   position, mesures et raccourcis à droite), position de l'enfant (plein cadre), tracé d'une zone
   (formulaire à gauche, carte sur le reste), trajets (jours et bilan à gauche, carte à droite).
4. **Hors session, rien ne bouge.** Connexion, inscription, vérification du contact et session expirée
   gardent la colonne centrée : il n'y a pas de rubrique à proposer à quelqu'un qui n'est pas connecté.
5. **Budget tenu.** La coque est chargée à la demande, avec les écrans connectés ; le chargement initial
   reste sous le budget de 250 Ko.

## Conséquences

- Les contrôles de session se font à l'entrée dans la coque et à chaque changement d'écran, comme avant.
- Le parcours `e2e/grand-ecran.spec.ts` vérifie la navigation, la place donnée aux cartes et le retour à
  l'affichage téléphone. Les autres parcours restent joués en 360 × 800.

## Point à trancher

- Cette mise en page n'a pas de maquette : elle applique le système de design, elle ne le remplace pas. Si un
  dessin « poste de travail » de l'espace parent est produit, il fera référence.
