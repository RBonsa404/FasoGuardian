# ADR 0034 — Mode alerte depuis la carte : un suivi rapproché de trente minutes

Statut : acceptée — 14 octobre 2026

## Contexte

L'écran 18 (carte plein écran) porte une bascule « Mode alerte · 60 s ». Les documents de conception ne font
passer le bracelet en mode alerte (une position toutes les 60 secondes) que lorsqu'une alerte critique est
ouverte, jusqu'à sa levée (FG-DOC-05, FG-DOC-08 §7). Ce rythme consomme environ 2,5 fois plus que le rythme
ordinaire (17,6 mA contre 7,2 mA, FG-DOC-08 tableau 9) : laissé allumé, il viderait la batterie en un jour.

## Décision

1. **La bascule ouvre un « suivi rapproché » de trente minutes.** Le bracelet reçoit la commande signée de
   mode alerte ; à l'échéance, la plateforme la retire d'elle-même. Le tuteur voit l'heure de fin sous la
   bascule et peut l'arrêter avant.
2. **Le suivi rapproché ne touche jamais une alerte critique.** Pendant une alerte critique, la bascule est
   allumée et verrouillée (« pendant l'alerte en cours ») : l'arrêter ne coupe pas le mode alerte de l'alerte.
   À l'inverse, la levée d'une alerte ne coupe pas un suivi rapproché encore en cours.
3. **Activation, arrêt et échéance sont au journal d'audit.** L'accès est réservé aux tuteurs de l'enfant ;
   sans bracelet associé, la bascule n'a pas d'effet et le dit.

## Point à trancher

- La durée de trente minutes est une valeur de départ, à ajuster avec les mesures d'autonomie du prototype.
