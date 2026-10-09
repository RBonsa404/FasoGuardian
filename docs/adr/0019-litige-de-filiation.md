# ADR 0019 — Litige de filiation : mesures conservatoires et décision

Statut : acceptée — 9 octobre 2026

## Contexte

US-KYC-001 : sur un compte actif, un litige de filiation peut être signalé après coup. À l'ouverture de
l'instruction, « les paramètres modifiables du compte sont gelés et la géolocalisation peut être suspendue à
titre conservatoire » ; la décision, rendue sur « décision de justice ou accord écrit », est « appliquée,
journalisée et notifiée aux parties ». Le design (écran 61) ajoute une échéance de sept jours et la mention
« SOS et page QR maintenus ».

## Décision

1. **Un litige porte sur un lien** : ce tuteur, pour cet enfant. L'agent KYC l'ouvre à partir du dossier KYC
   approuvé qui a établi le lien ; un seul litige ouvert par lien.
2. **Le gel est appliqué en un seul endroit**, par un intercepteur placé devant toutes les routes d'un parent :
   tant que le litige est ouvert, toute requête qui modifie quelque chose autour de l'enfant est refusée
   (423, code `COMPTE_GELE`), de même que le changement de numéro et la clôture du compte. Aucun cas d'usage
   n'a à s'en souvenir, et une route ajoutée demain est gelée d'office. La consultation reste possible.
3. **Ce qui protège l'enfant n'est jamais gelé** : prise en charge d'une alerte, signalement d'une
   disparition, paiement de l'abonnement, changement de mot de passe. Le SOS, les alertes et la page publique
   du bracelet ne dépendent pas du litige.
4. **Suspension conservatoire de la géolocalisation**, au choix de l'agent, à l'ouverture ou en cours
   d'instruction : le tuteur contesté ne voit plus la position ni les trajets, ne peut plus demander une
   localisation ni partager la position (423, code `GEOLOCALISATION_SUSPENDUE`). Les autres tuteurs de
   l'enfant ne sont pas touchés.
5. **Décision.** L'agent enregistre « lien maintenu » ou « lien retiré », son fondement (décision de justice
   ou accord écrit) et la référence de la pièce. Lien maintenu : les mesures tombent. Lien retiré : le lien
   de tutelle est suspendu, le tuteur perd l'accès à l'enfant et ses sessions sont fermées ; les données de
   l'enfant restent, pour ses autres tuteurs. Les parties (le tuteur contesté et les autres tuteurs de
   l'enfant) sont notifiées à l'ouverture et à la décision ; chaque étape est au journal d'audit.
6. **Échéance de sept jours**, affichée à l'agent et mise en évidence une fois dépassée. Elle n'a pas d'effet
   automatique : lever un gel sans décision serait pire que le prolonger.

## Écarts et points ouverts

- **Qui signale.** Le signalement arrive hors application (courrier, point d'inscription, appel) : l'agent le
  saisit. Le parent qui conteste n'a pas forcément de compte ; s'il doit devenir tuteur, il passe par un
  dossier KYC ordinaire.
- **Pièces du litige.** La décision de justice ou l'accord écrit n'est pas téléversé dans la plateforme :
  seule sa référence est enregistrée, pour ne pas conserver une pièce judiciaire de plus. À revoir si la CIL
  ou le conseil juridique demande l'archivage.
- **Alertes pendant la suspension.** Une alerte critique reste notifiée au tuteur contesté tant que son lien
  n'est pas retiré, et la fiche de l'alerte porte la position du déclenchement : priver un parent d'un SOS
  sur la foi d'un signalement non encore instruit ferait courir un risque à l'enfant.
- **Enfant sans tuteur.** Si le lien retiré était le seul, l'enfant n'a plus de tuteur actif jusqu'à
  l'approbation d'un nouveau dossier KYC ; ses données sont conservées et son bracelet continue d'émettre.
