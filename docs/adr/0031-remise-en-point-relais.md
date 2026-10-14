# ADR 0031 — Bracelet muet : du diagnostic à l'échange en point relais, sans que le SAV connaisse la famille

Statut : acceptée — 14 octobre 2026

## Contexte

L'écran 67 du design (prototype) déroule le traitement d'un bracelet muet en cinq étapes : détection,
diagnostic à distance, contact du parent, remise en point relais, clôture. La maquette montre le prénom de
l'enfant, le nom du parent, le quartier du dernier contact, un bouton « Appeler le parent », la panne d'une
antenne signalée par l'opérateur et un « ping SMS de secours ».

FG-DOC-06 (tableau 17) limite le rôle SAV à « l'état des bracelets seulement » ; ADR 0009 en a tiré la règle :
le service après-vente ne voit jamais l'enfant ni sa famille. FG-DOC-05 donne au SAV le remplacement et
l'assistance en point relais (US-PAR-013, US-PAR-014, US-SAV-001), et veut que l'autorisation de retrait
reste émise depuis le compte du parent (US-PAR-012).

## Décision

1. **Diagnostic** : l'agent qui a pris le ticket fait envoyer au bracelet une localisation immédiate, par
   commande signée. L'état de la commande (émise, accusée, refusée, expirée) dit si le bracelet reçoit encore
   ses commandes. L'écran indique aussi combien d'autres bracelets sont muets au même moment, ce qui oriente
   vers le réseau. Il n'y a ni « ping SMS » (le bracelet n'a pas de canal SMS entrant défini) ni information
   d'opérateur sur les antennes : la plateforme n'en reçoit pas.
2. **Rendez-vous** : l'agent fixe un point relais (50 caractères au plus, pour tenir dans une notification) et
   une date dans les trente jours. La plateforme le transmet au parent par notification, doublée d'un SMS si
   elle n'est pas lue ; l'écran 40 du parent l'affiche. Le bouton « Appeler le parent » n'est pas réalisé :
   l'agent ne connaît pas le parent.
3. **Remise** : l'agent réserve pour le ticket une unité « En stock » (une seule réservation en cours par
   unité). Le parent l'associe lui-même avec le code de sa carte d'activation, depuis l'écran 40. Cet
   appairage vaut échange : l'ancien appairage est clos (motif « remplacement »), l'ancienne unité passe
   « En SAV », la page QR suit le nouveau bracelet et le ticket se clôt (« Échange en point relais »). Sans
   réservation, un enfant déjà équipé ne peut toujours pas recevoir un second bracelet (ADR 0009).
4. **Rendez-vous facultatif** : un parent venu de lui-même au point relais est servi directement.
5. Si le bracelet redonne des nouvelles avant le rendez-vous, le ticket se clôt comme avant ; le parent est
   averti que le rendez-vous n'est plus nécessaire. L'agent peut toujours clore autrement (recharge, retour à
   l'atelier, sans suite).

Chaque étape est journalisée (`TICKET_DIAGNOSTIC`, `TICKET_RENDEZ_VOUS`, `TICKET_REMPLACEMENT_RESERVE`,
`BRACELET_ECHANGE`, `TICKET_RESOLU`). Migration V28.

## Conséquences

- Le parcours de l'écran 67 est réel de bout en bout, avec les données que le rôle SAV a le droit de voir.
- La gratuité sous garantie affichée par la maquette (« 0 FCFA ») n'est pas promise : la demande de
  remplacement payante hors garantie (écran 37) reste à traiter avec le module abonnements.
