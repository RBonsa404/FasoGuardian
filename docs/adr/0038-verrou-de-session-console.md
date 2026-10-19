# ADR 0038 — Verrou de session de la console

Statut : acceptée — 19 octobre 2026

## Contexte

L'écran 55 montre une « session verrouillée » après 30 minutes d'inactivité, que l'agent déverrouille avec son
seul code TOTP. FG-DOC-06 fixe l'expiration d'une session d'agent à 30 minutes d'inactivité.

## Décision

1. Après 30 minutes sans geste de l'agent, ou quand le serveur laisse expirer la session, la console masque
   l'écran en cours (fenêtre modale, reste de la page inerte) et la session est close côté serveur, pour
   qu'aucun rafraîchissement silencieux du jeton ne la rouvre.
2. Le déverrouillage demande le mot de passe **et** le code TOTP : un code seul ferait de la reprise une
   authentification à un facteur. L'agent reprend sur l'écran où il était.
