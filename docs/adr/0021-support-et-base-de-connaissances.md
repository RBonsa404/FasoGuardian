# ADR 0021 — Support : demandes suivies dans le compte et base de connaissances

Statut : acceptée — 9 octobre 2026

## Contexte

US-PAR-017 : le parent suit ses demandes de support depuis son compte, « il voit le statut et les réponses ».
US-SUP-001 : l'opérateur support publie des réponses aux questions fréquentes, consultables par les parents
une fois publiées. FG-DOC-06 (tableau 17) donne à l'opérateur support « les coordonnées des parents, jamais
les pièces ni les positions ». Le design : écrans 46 et 47 (parent), 62 et 63 (console).

## Décision

1. **L'échange reste dans le compte.** Une demande a un objet, un fil de messages et un statut : en cours,
   en attente du parent, résolue. La réponse du support n'est jamais envoyée par SMS : le parent reçoit
   seulement l'avis qu'il y en a une, avec la référence, et la lit après s'être authentifié. Un message du
   parent rouvre une demande en attente ou résolue.
2. **Ce que voit l'opérateur** : l'objet, le fil, le prénom du parent et son numéro, pour pouvoir le
   rappeler. Rien sur l'enfant, le bracelet, la position ou la santé : s'il a besoin d'un diagnostic du
   bracelet, il passe la main au service après-vente. Chaque ouverture d'une demande est journalisée.
3. **Base de connaissances.** Un article appartient à une catégorie, reste en brouillon tant qu'il n'est pas
   publié, et peut être retiré ; l'effet est immédiat. Les parents le trouvent par catégorie ou par mots, et
   chaque lecture est comptée, ce qui fait remonter les articles utiles (« les plus lus »).
4. **Texte simple.** Le contenu d'un article et d'un message est du texte, affiché tel quel avec ses retours
   à la ligne : aucun balisage n'est interprété, donc aucun contenu actif ne peut être injecté par un article
   ou par un message.
5. **Rattachement.** Le support n'a pas de module à lui dans le découpage de FG-DOC-06 : il vit dans le
   module identite, qui porte déjà les comptes, les rôles et la relation avec les parents.
6. **Conservation.** Les demandes résolues sont effacées douze mois après leur résolution ; elles figurent
   dans l'export des données du parent et disparaissent avec son compte.

## Écarts du design

- L'éditeur de l'écran 63 montre une barre de mise en forme (gras, italique, listes, lien). Elle n'est pas
  reprise : voir le point 4. Les étapes se numérotent à la main.
- L'écran 62 affiche le bracelet et l'offre du parent, et des modèles de réponse (« /sangle »). Le bracelet
  et l'offre sortent du périmètre du rôle support ; les modèles de réponse ne sont pas réalisés.
- Le message n'est pas chiffré au niveau applicatif : le parent est invité, à la saisie, à n'y mettre ni mot
  de passe ni code. Il est protégé comme le reste de la base (volume chiffré, accès cloisonnés, journal).
