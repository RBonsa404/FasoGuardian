# ADR 0032 — Second tuteur : invitation confirmée par SMS, vérification KYC de l'invité, mêmes droits

Statut : acceptée — 14 octobre 2026

## Contexte

US-PAR-003 (FG-DOC-05) : « quand il invite un second tuteur (après vérification KYC de celui-ci), alors ce
dernier obtient un accès individuel ». FG-DOC-06 §8.1 range « l'ajout d'un tuteur » parmi les actions
sensibles soumises à un code SMS ; §8.2 n'ouvre un enfant qu'aux tuteurs rattachés par un lien KYC validé ;
le tableau 17 donne au « parent / tuteur » les mêmes accès à ses enfants, et au contact secondaire le seul
prénom de l'enfant et le partage temporaire.

L'écran 34 du design montre deux tuteurs (« Tuteur principal », « Second tuteur ») et un contact secondaire,
avec des pastilles de droits différentes (« Tout, Facturation, Escalade » pour l'un, « Position, Alertes,
Retrait » pour l'autre), et un bouton « Inviter un second tuteur — le tuteur invité passe sa propre
vérification KYC ».

## Décision

1. **Invitation.** Un tuteur actif de l'enfant saisit le numéro de l'autre adulte puis confirme par le code
   SMS de l'action `AJOUTER_TUTEUR`. La demande est contrôlée avant que le code soit consommé (numéro valide,
   pas déjà tuteur de l'enfant, place libre, pas d'invitation en cours). L'invité reçoit un SMS qui donne le
   prénom vérifié de qui l'invite, jamais celui de l'enfant. L'invitation vaut sept jours.
2. **Vérification de l'invité.** Connecté avec ce numéro, l'invité voit l'invitation au début de son parcours
   de vérification et y répond : son dossier KYC vise l'enfant déjà suivi, dont l'identité vérifiée est
   reprise du dossier qui a créé la fiche ; l'invité ne la voit pas. Il fournit ses propres pièces et le
   justificatif de son propre lien. L'agent KYC voit qu'il s'agit d'un second tuteur. L'approbation crée le
   lien de tutelle, comme pour tout dossier ; l'invitant en est prévenu par SMS. Un rejet clôt l'invitation.
3. **Retrait avant décision.** Tant que l'invité n'est pas approuvé, tout tuteur de l'enfant peut annuler
   l'invitation ; l'agent ne peut alors plus que rejeter le dossier.
4. **Deux tuteurs au plus** par enfant, liens suspendus compris : c'est ce que disent la user story (« un
   autre adulte responsable ») et l'écran (« second tuteur ») ; aucune exigence n'en demande davantage.
5. **Mêmes droits.** Les deux tuteurs ont les mêmes droits sur l'enfant (FG-DOC-06, tableau 17, qui prime
   sur le design) : position, alertes, retrait, escalade, abonnement. Les pastilles de l'écran le disent ;
   celles de la maquette, qui retirent l'escalade au second tuteur, ne sont pas reprises : un adulte vérifié
   doit pouvoir signaler la disparition de l'enfant. « Tuteur principal » désigne celui dont la vérification
   a créé la fiche ; ce n'est pas un droit de plus. La différenciation demandée par US-PAR-003 est celle du
   contact secondaire (contact d'urgence) : il n'a pas de compte, il est prévenu par SMS en cascade et peut
   être joignable depuis la page QR.
6. **Retrait d'un tuteur actif.** Il n'est pas offert à l'autre tuteur : deux titulaires de l'autorité
   parentale ont des droits égaux, et un désaccord entre eux relève du signalement de filiation, instruit
   par l'équipe KYC avec ses mesures conservatoires (US-KYC-001). Le gel d'un litige s'applique aussi à
   l'invitation.
7. **Données.** Le numéro de l'invité n'est gardé, chiffré avec son empreinte, que tant que l'invitation est
   ouverte ; une invitation close est supprimée 30 jours plus tard (purge quotidienne inscrite au registre,
   durée affichée dans le rapport de conformité). L'export des données de l'invitant donne l'état de ses
   invitations, sans le numéro du tiers ; l'effacement de l'un ou de l'autre supprime les invitations.

## Conséquences

- Nouvelles routes : `GET /api/v1/enfants/{id}/tuteurs`, `POST` et `DELETE /api/v1/enfants/{id}/tuteurs/invitation`,
  `GET /api/v1/moi/invitations`, `POST /api/v1/kyc/dossiers/invitation` ; migration V29.
- L'écran 34 devient « Tuteurs et contacts » ; l'entrée de la fiche enfant suit ce nom.
- Si le porteur veut un jour plus de deux tuteurs, ou des droits réduits pour le second, c'est une décision de
  produit qui modifie FG-DOC-06 : la limite est une constante et les droits passent tous par `LiensTutelle`.
