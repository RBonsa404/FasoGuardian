# ADR 0037 — États transverses de l'application Parents (écran 49)

Statut : acceptée — 19 octobre 2026

## Contexte

L'écran 49 du paquet de design montre cinq bandeaux : hors ligne, réseau lent (2G), actions en attente,
erreur serveur et maintenance. Trois de ses textes promettent ce que le système ne fait pas tel quel.

## Décision

1. **Hors ligne** et **actions en attente** sont repris (ADR 0036, FG-DOC-06 tableau 8) : le bandeau dit de
   quand datent les données gardées sur le téléphone et ce qui partira au retour du réseau.
2. **Réseau lent** : quand le navigateur signale une connexion 2G ou le mode économie de données, les photos
   des enfants ne sont pas chargées et le bandeau le dit. La « carte simplifiée » de la maquette n'est pas
   reprise : la carte n'a pas de mode allégé, le cache borné des tuiles limite déjà la consommation.
3. **Erreur serveur** : la maquette affirme que « les alertes SOS continuent d'arriver par SMS ». Le SMS de
   secours passe lui aussi par la plateforme (FG-DOC-04) : une panne du serveur peut l'interrompre. Le
   bandeau invite à réessayer et rappelle les numéros d'urgence (17 Police, 18 Pompiers) déjà publiés sur le
   site.
4. **Maintenance** : aucun document ne prévoit de fenêtres de maintenance annoncées, et la plateforme n'en
   planifie pas. Le bandeau n'est pas réalisé.

## Point à trancher

- Si l'exploitation annonce des maintenances, un paramètre de la console (date, durée) pourra alimenter le
  bandeau ; il faudra alors vérifier que le SOS et la page QR restent bien servis pendant la fenêtre.
