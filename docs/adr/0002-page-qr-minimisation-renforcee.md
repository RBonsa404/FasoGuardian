# ADR 0002 — Page publique QR : minimisation renforcée

Statut : acceptée — 7 octobre 2026

## Contexte

Les documents de conception autorisent l'affichage du prénom et de l'initiale du nom sur la page publique :
FG-DOC-04 (REQ-SYS-014), FG-DOC-05 (US-TRS-001, tableau 5), FG-DOC-06 (tableau 17, ligne « Tiers »).
Le brief de développement impose une règle plus stricte : la page n'affiche que le numéro du bracelet,
les informations médicales marquées critiques et des boutons d'appel — jamais de nom, d'initiale, de photo
ni de localisation. Le paquet de design (écrans 51 à 54) suit cette règle.

## Décision

La règle la plus protectrice s'applique. La projection `VuePubliqueEnfant` contient :

- le numéro de série du bracelet ;
- les éléments médicaux marqués critiques ;
- les contacts marqués « visibles sur le QR », sous forme de boutons d'appel, sans numéro affiché
  lorsque la ligne relais est disponible (voir ADR 0005).

Elle ne contient ni prénom, ni initiale, ni photo, ni position, ni identifiant technique.
Tout jeton invalide, inconnu ou désactivé reçoit la même page générique, dans le même délai.

## Conséquences

- Les critères d'acceptation de US-TRS-001 sont testés contre cette règle : un test vérifie l'absence du
  prénom et du nom dans la réponse HTTP.
- La ligne « Contact secondaire : Prénom » du tableau 17 n'est pas concernée.
- À reporter dans FG-DOC-04, FG-DOC-05 et FG-DOC-06 lors de leur prochaine révision.
