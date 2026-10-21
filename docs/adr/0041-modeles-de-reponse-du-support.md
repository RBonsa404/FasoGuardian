# ADR 0041 — Modèles de réponse du support

Statut : acceptée — 21 octobre 2026

## Contexte

L'écran 62 montre, dans le champ de réponse d'un ticket, l'indication « Répondre… (modèle : /sangle) » : l'opérateur
insère un texte préparé en tapant son raccourci. L'ADR 0021 avait laissé ces modèles hors réalisation. Aucun
document de conception ne les décrit davantage.

## Décision

1. **Modèles partagés.** Les modèles sont communs à tous les opérateurs support, qui seuls les lisent, les créent,
   les modifient et les suppriment ; chaque geste est journalisé (`MODELE_REPONSE_CREE`, `_MODIFIE`, `_SUPPRIME`,
   cible : le raccourci).
2. **Raccourci.** Lettres minuscules sans accent, chiffres et tirets, unique ; la barre oblique saisie avec lui
   n'est pas conservée.
3. **Insertion relue.** Taper `/` puis le début d'un raccourci dans la réponse propose les modèles correspondants ;
   le choix remplace le raccourci par le texte du modèle, où `{prénom}` devient le prénom du parent quand il est
   connu. Rien n'est envoyé sans que l'opérateur ait relu le texte et choisi l'une des deux actions d'envoi.
4. **Texte simple.** Comme les articles d'aide (ADR 0021, point 4), un modèle ne porte aucun balisage. Il ne
   contient aucune donnée d'un parent : il n'entre ni dans l'export ni dans l'effacement des données
   personnelles. Supprimer un modèle ne touche aucune réponse déjà envoyée, qui en a recopié le texte.

## Conséquences

- Migration V36 (`identite.modele_reponse`), API `/api/v1/console/support/modeles`.
- La gestion des modèles se fait depuis l'écran des tickets, sans écran nouveau dans la barre latérale.
