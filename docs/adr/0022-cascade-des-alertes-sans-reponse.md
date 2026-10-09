# ADR 0022 — Cascade : solliciter un tiers quand une alerte reste sans réponse

Statut : acceptée — 9 octobre 2026

## Contexte

US-SYS-005 (COULD) : « étant donné une alerte sans accusé, quand le délai expire, alors le contact secondaire
est appelé, puis le point de contact institutionnel à l'expiration du délai suivant », afin qu'aucune alerte
ne reste sans réponse. FG-DOC-06 réserve au parent, sous second facteur, l'escalade vers les forces de
sécurité, et interdit toute donnée de santé ou coordonnée dans un message.

## Décision

1. **Seules les alertes critiques** (SOS, retrait non autorisé, signalement) entrent dans la cascade, et
   seulement tant qu'aucun tuteur ne les a prises en charge : une alerte acquittée n'est jamais portée à la
   connaissance d'un tiers.
2. **Premier délai (5 minutes, réglable)** : les contacts d'urgence de l'enfant, ceux que le parent a désignés,
   reçoivent un SMS. **Second délai (10 minutes de plus, réglable)** : le point de contact institutionnel
   convenu, s'il est configuré, en reçoit un. Chaque étape n'a lieu qu'une fois par alerte.
3. **Des messages qui ne révèlent rien.** Le contact d'urgence apprend qu'une alerte concerne l'enfant dont il
   est le contact et que les parents n'ont pas répondu ; il lui est demandé de les joindre ou de rejoindre
   l'enfant. L'institution ne reçoit qu'une référence d'alerte et la consigne d'appliquer la procédure
   convenue. Ni nom, ni position, ni donnée de santé ne partent par SMS : la transmission d'un dossier reste
   la décision du parent (ADR 0013).
4. **Chaque étape est au journal de l'alerte**, que le parent voit (« Sans réponse de votre part · 2 contacts
   d'urgence prévenus par SMS »), et au journal d'audit. Sans contact d'urgence enregistré, le journal le dit.

## Écarts et points à trancher

- **« Appelé ».** La user story parle d'un appel. La plateforme n'a pas de prestataire de téléphonie vocale :
  la sollicitation part par SMS, par le même port que les autres. Un appel vocal automatique demandera un
  prestataire et un adaptateur de plus ; le déroulé de la cascade n'en sera pas changé.
- **Point de contact institutionnel.** Il n'existe pas tant qu'aucune convention n'est signée :
  `FG_CASCADE_CONTACT_INSTITUTIONNEL` est vide par défaut et la cascade s'arrête alors aux contacts
  d'urgence. Qui il est, ce qu'il reçoit et ce qu'il doit faire relève de cette convention.
- **Délais.** Cinq puis dix minutes sont des valeurs de départ, à ajuster pendant le pilote.
