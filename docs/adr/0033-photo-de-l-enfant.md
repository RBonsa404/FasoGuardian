# ADR 0033 — Photo de l'enfant : chiffrée en base, pour ses tuteurs et le dossier de signalement seulement

Statut : acceptée — 14 octobre 2026

## Contexte

US-PAR-004 (FG-DOC-05) demande que le parent saisisse « identité, date de naissance, photo et contacts ».
US-PAR-010 et FG-DOC-04 veulent la photo dans le dossier de signalement remis aux forces de sécurité ;
l'ADR 0013 en avait acté l'absence faute de photo. FG-DOC-04 et US-TRS-001 excluent toute photo haute
résolution de la page publique du QR, et l'ADR 0002 n'y montre aucune photo. FG-DOC-06 chiffre au niveau
applicatif les données sensibles par catégorie de clé. La colonne `photo_ref` prévue dès V6 pour un
stockage externe n'a jamais servi.

## Décision

1. **Stockage.** Une photo par enfant, JPEG ou PNG de 2 Mo au plus, reconnue à sa signature et non à son
   seul type déclaré, chiffrée en AES-256-GCM avec la clé du profil de l'enfant (`FG_CLE_PROFIL_ENFANT`),
   dans la table `famille.photo_enfant` (V30, qui retire `photo_ref`). L'application l'allège à 400 Ko
   environ avant l'envoi, comme les pièces KYC.
2. **Accès.** Elle n'est servie qu'aux tuteurs actifs, par l'API authentifiée et sans cache ; l'application
   l'affiche depuis une adresse locale (`blob:`), jamais depuis une URL publique. Ajout, remplacement et
   retrait sont tracés dans l'historique de la fiche (« photo modifiée »).
3. **Dossier de signalement.** La photo est placée en tête de la rubrique « Enfant » du PDF ; sans photo, le
   dossier le dit. Une image illisible n'empêche pas le dossier.
4. **Jamais sur la page QR**, ni dans l'export d'un autre module : la page publique ne change pas.
5. **Droits des personnes.** L'export de l'enfant la rend telle que confiée (base 64) ; l'effacement la
   supprime avec la fiche ; le rapport de conformité affiche sa durée de conservation (celle du compte,
   retirable à tout moment).

## Conséquences

- Routes `PUT`, `GET` et `DELETE /api/v1/enfants/{id}/photo` ; `FicheEnfant.photoModifieeLe`.
- La fiche (écran 31) et la liste des enfants (écran 17) montrent la photo, sinon l'initiale.
- L'écart « pas de photo » de l'ADR 0013 est levé.
