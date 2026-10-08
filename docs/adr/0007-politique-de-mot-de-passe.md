# ADR 0007 — Politique de mot de passe des parents

Statut : acceptée — 8 octobre 2026

## Contexte

Les FG-DOC imposent Argon2id et l'absence d'expiration périodique, sans fixer de longueur. Le design
(écran 10) annonce « 10 caractères minimum, avec un chiffre ». L'OWASP ASVS 4.0 niveau 2, référence de
vérification, demande 12 caractères au moins et aucune règle de composition (V2.1.1, V2.1.9).

## Décision

La règle du design est appliquée : 10 caractères minimum dont un chiffre, 128 au maximum, refus d'un mot de
passe contenant le numéro de téléphone du compte. Le public visé est peu familier du numérique (US-PAR-005)
et chaque action sensible exige en plus un code à usage unique par SMS.

Les agents internes suivront la règle de l'ASVS (12 caractères) avec TOTP obligatoire.

## Conséquences

Écart assumé par rapport à l'ASVS V2.1.1 pour les comptes parents, à présenter lors de l'audit de sécurité.
Les seuils sont des constantes de `PolitiqueMotDePasse` ; les relever ne demande aucune migration.
