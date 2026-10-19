# ADR 0036 — Actions de configuration rejouées hors ligne

Statut : acceptée — 19 octobre 2026

## Contexte

FG-DOC-06 (§5.4, tableau 8) demande que les actions de configuration faites sans réseau soient gardées dans une
file locale, rejouées au retour du réseau, « sans perte d'action », avec une clé d'idempotence côté serveur.
L'écran 49 du paquet de design donne l'exemple d'une Safe Zone modifiée hors ligne.

## Décision

1. **Seules les actions sans code SMS sont rejouables** : fiche de l'enfant, contacts
   d'urgence, prise en charge, levée et fausse alerte, réactivation d'une Safe Zone, mode économie,
   renouvellement automatique, notifications lues, message au support. Une action protégée par second facteur
   (modifier une Safe Zone, autoriser un retrait, signaler une disparition…) ne l'est pas : le code expire
   avant le retour du réseau, et le rejouer contournerait la confirmation. L'exemple de l'écran 49 n'est donc
   pas repris tel quel. Les actions liées à l'instant (localiser, partager la position, suivi rapproché) ne
   sont pas rejouées non plus. La fiche médicale non plus : hors ligne, seuls ses éléments critiques sont
   relus sur l'appareil (FG-DOC-06, tableau 8), et l'enregistrer depuis cette copie effacerait les autres.
2. **Idempotence générique côté serveur** (`plateforme/idempotence`, V33) : toute écriture qui porte
   l'en-tête `Idempotency-Key` n'est exécutée qu'une fois par compte ; un renvoi reçoit le même statut, sans
   corps, avec l'en-tête `Idempotent-Replayed`. Seuls le statut et une empreinte SHA-256 de la requête sont
   gardés, 24 heures : aucune donnée d'un enfant ne passe par cette table. Les paiements gardent leur propre
   idempotence, qui rend le paiement d'origine.
3. **File locale dans l'application Parents** (IndexedDB, base distincte de la copie des fiches) : renvoi
   dans l'ordre au retour du réseau ; une action refusée au renvoi est retirée et signalée, une panne ou une
   session expirée la laisse en attente. La file est effacée à la déconnexion, comme la copie locale.

## Conséquences

- Le parent voit en tête d'écran ce qui partira au retour du réseau (écran 49).
- Une clé n'est plus reconnue après 24 heures : une action gardée plus longtemps serait appliquée de nouveau
  si la première tentative avait en fait abouti. Les actions rejouables sont des remplacements d'état ou des
  ajouts sans conséquence grave en double (un contact d'urgence), ce risque est accepté.
