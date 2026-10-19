# ADR 0035 — Point d'inscription : où se présenter, sans réservation de créneau

Statut : acceptée — 19 octobre 2026

## Contexte

L'écran 11d du paquet de design (« Choisissez un rendez-vous ») fait réserver au parent un créneau horaire
dans un point d'inscription. Les documents de conception ne prévoient pas de prise de rendez-vous : la
vérification se fait « en présentiel dans un point d'inscription » (FG-DOC-04), et le critère d'acceptation
de US-PAR-001 dit qu'un agent accompagne le parent « quand il s'y présente » (FG-DOC-05). Aucun document ne
décrit l'agenda des agents, la capacité d'un point ni ses horaires ; le site public les annonce « publiés à
l'ouverture de chaque point ». Réserver un créneau que personne ne gère serait une promesse non tenue.

## Décision

1. **Pas de réservation de créneau.** Quand un dossier est déposé « en point d'inscription », l'écran de suivi
   dit où se présenter : la référence du dossier, les originaux à montrer (pièce d'identité, plus l'acte de
   naissance ou le jugement de tutelle) et la liste des points qui font l'inscription.
2. **Une seule liste des points**, dans la bibliothèque `ui` (`POINTS_RELAIS`, `POINTS_INSCRIPTION`), partagée
   par le site public et l'espace parents. Le choix du mode (écran 11) cite ces mêmes quartiers.
3. Le pied du récapitulatif ne promet pas de délai : aucun délai d'instruction n'est engagé dans les documents.

## Point à trancher

- Si le pilote ouvre une prise de rendez-vous (agenda des agents par point), il faudra un module de planning
  côté console ; l'écran 11d de la maquette pourra alors être repris tel quel.
