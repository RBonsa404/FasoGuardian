# ADR 0011 — Alertes : déclenchement, notification, résolution et autorisation de retrait

Statut : acceptée — 9 octobre 2026

## Contexte

FG-DOC-07 (§5) fixe la machine à états de l'alerte : `OUVERTE → ACQUITTEE → ESCALADEE → LEVEE`, et
`FAUSSE_ALERTE` depuis les deux premiers états. FG-DOC-05 demande que le parent soit notifié « par push, puis
par SMS en l'absence d'accusé sous 60 s » pour une sortie de zone, « par push et par SMS » pour un SOS, et
décrit la fenêtre de retrait (US-PAR-012). Le paquet de design ajoute des comportements que les documents ne
décrivent pas : alertes « levées automatiquement », rappel cinq minutes avant la fin d'un retrait,
regroupement des alertes simultanées. Le canal Web Push n'existe pas encore (module notifications).

## Décision

1. **Gravité.** SOS, retrait non autorisé et signalement par le parent sont *critiques* ; sortie de zone,
   batterie critique et chute sont *importantes*. Cette distinction suit le design (écrans 24 à 26) et règle
   la notification.
2. **Notification.** Une alerte critique est envoyée aussitôt par SMS à tous les tuteurs de l'enfant. Une
   alerte importante n'est doublée par SMS que si personne ne l'a prise en charge au bout de 60 secondes. Tant
   que le Web Push n'est pas livré, l'alerte importante n'apparaît donc, pendant cette première minute, que
   dans l'application ouverte (relue toutes les 30 secondes). Les SMS ne portent ni position, ni nom de lieu,
   ni donnée de santé.
3. **Une alerte par cause.** Un événement répété (second appui sur le SOS, nouvelle position hors zone) ne
   crée pas de nouvelle alerte tant que la précédente, de même nature et de même zone, n'est pas close.
4. **Résolution par le système.** Une transition `resoudre()` s'ajoute à la machine à états : depuis
   `OUVERTE` ou `ACQUITTEE`, le système clôt une alerte dont la cause a disparu (retour dans la zone,
   bracelet mis en charge), avec une action `RESOLUTION` au journal. Elle ne s'applique ni aux alertes
   critiques, que seul un tuteur peut clore, ni à une alerte escaladée.
5. **Journal d'acquittement.** Chaque transition du domaine *rend* l'action à enregistrer : il n'existe pas de
   changement d'état sans ligne de journal. La table est en ajout seul (déclencheurs refusant modification,
   suppression et troncature). Elle ne révèle jamais l'identité d'un autre tuteur : l'auteur d'une action est
   « vous », « un autre tuteur » ou le système.
6. **Retrait.** La coupure de la boucle comme la perte du contact peau sont couvertes par une fenêtre
   autorisée (FG-DOC-08 §7.4) : elles y sont seulement notées. Hors fenêtre, elles ouvrent une alerte
   critique, y compris pour une urgence médicale, que le parent indique en levant l'alerte. Le bracelet
   signale la reprise du contact peau (`worn`, ajouté au protocole), ce qui referme la fenêtre. Cinq minutes
   avant la fin, un rappel part si le bracelet n'a pas été remis ; à l'échéance, un bracelet toujours retiré
   ouvre l'alerte. La prolongation exige le second facteur, comme l'autorisation ; la durée totale reste
   bornée à 12 heures.
7. **Regroupement (US-PAR-018).** Les alertes restent des agrégats distincts, tracés séparément ; elles sont
   regroupées par enfant à l'affichage, classées par gravité, et une seule action les prend toutes en charge.
8. **Transaction au point d'entrée des transports.** `ReceptionMessages.recevoir` ouvre la transaction : les
   événements de domaine ne sont remis aux autres modules qu'à sa validation. Un essai de bout en bout par le
   broker a montré qu'un appel interne hors transaction enregistrait l'événement sans jamais le transmettre.

## Conséquences

- Avant le Web Push, une sortie de zone peut n'atteindre un parent qui n'a pas l'application ouverte qu'au
  bout d'une minute environ. C'est le délai de repli voulu par FG-DOC-05, mais sans son premier canal.
- La prise en charge se fait par un bouton ; le geste « glisser pour acquitter » du design (ADR 0005) reste à
  ajouter, le bouton en étant l'équivalent accessible.
- Le mode alerte du bracelet (une position toutes les 60 secondes), « Localiser maintenant » et la
  confirmation du retrait par le bracelet (LED) supposent des commandes signées vers le bracelet : elles ne
  sont pas encore émises.
- L'escalade vers les forces de sécurité (transition `escalader()`, dossier de signalement) est modélisée
  dans le domaine mais n'est pas encore exposée.
- Le bracelet posé sur son chargeur hors fenêtre doit émettre `charge` et non `skin` : c'est au logiciel
  embarqué de les distinguer (FG-DOC-08 §7.4).
