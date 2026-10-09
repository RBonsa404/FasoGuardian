# ADR 0024 — Passerelles LoRaWAN en zone pilote : la plateforme est prête, le bracelet ne l'est pas

Statut : acceptée — 9 octobre 2026

## Contexte

US-SYS-004 (COULD, REQ-COULD-06) : « étant donné un bracelet dans le rayon d'une passerelle, quand l'enfant
est dans l'enceinte, alors sa présence est confirmée sans réseau cellulaire ». FG-DOC-02 §3.5 et FG-DOC-04
tiennent LoRaWAN pour une « piste documentée mais non engagée », et le bracelet de FG-DOC-08 n'embarque
**aucune radio LoRa** : LoRaWAN y figure parmi les évolutions. L'écran 74 de la console montre pourtant une
ligne « Passerelles LoRaWAN · 3 écoles · 3 en ligne ».

## Décision

1. **Un registre des passerelles**, tenu par l'administrateur à l'écran de paramétrage : identifiant matériel
   (EUI), établissement, centre et rayon de l'enceinte couverte. Une passerelle est dite en ligne si elle a
   donné signe de vie depuis moins d'un quart d'heure. Installation et retrait sont au journal d'audit.
2. **Un point d'entrée pour le serveur de réseau LoRaWAN** (`POST /api/v1/public/lorawan/trames`). C'est lui
   qui authentifie les trames des bracelets, selon la norme ; la plateforme l'authentifie par un sceau
   HMAC sur chaque appel, comme la passerelle SMS. Sans secret configuré (`FG_LORAWAN_SECRET`), le canal est
   fermé.
3. **Une trame entendue vaut présence.** Elle est enregistrée comme une position de source `LORA` : le
   centre de l'enceinte, avec son rayon pour précision. Elle suit ensuite le chemin de toute position
   (carte du parent, Safe Zones), sans que le bracelet ait joint le réseau cellulaire. Le parent lit
   « Présence relevée par la passerelle d'un établissement partenaire », et non une position GPS.
4. **Aucune donnée nouvelle.** La trame ne porte que l'identifiant du bracelet, un compteur et une heure.
   Une trame rejouée, datée du futur, d'une passerelle inconnue ou retirée, ou d'un bracelet non appairé,
   n'enregistre rien ; la réponse est la même dans tous les cas.

## Ce qui n'est pas fait, et pourquoi

- **Le critère d'acceptation ne peut pas être validé sur le matériel actuel.** Sans radio LoRa dans le
  bracelet, aucune trame réelle ne peut être émise. La chaîne est essayée de bout en bout avec un serveur
  de réseau simulé ; elle le sera sur le terrain quand une révision du bracelet embarquera la radio.
- **Le format d'appel est le nôtre.** Le serveur de réseau retenu (ChirpStack, The Things Stack ou autre)
  aura son propre format de notification : il faudra un adaptateur d'une page, ou une règle de transformation
  côté serveur de réseau.
- **Écran 74.** La maquette liste aussi une offre « École » et le prix du bracelet. La plateforme ne vend ni
  l'une ni l'autre : l'écran montre les trois offres réellement souscrites par les familles. Le gabarit du
  SMS se lit à l'écran mais ne s'y modifie pas : un texte d'alerte modifiable depuis la console pourrait
  recevoir une donnée de santé ou une position, ce que FG-DOC-06 interdit.

## Points à trancher

- Engager ou non la piste LoRaWAN (radio dans le bracelet, passerelles, serveur de réseau) : décision
  d'investissement, hors du logiciel.
