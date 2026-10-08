# ADR 0010 — Évaluation des Safe Zones et signalement des sorties

Statut : acceptée — 8 octobre 2026

## Contexte

FG-DOC-05 (US-PAR-007, US-ENF-001) demande de détecter « la sortie d'une Safe Zone active » et de n'émettre
aucune alerte avant l'expiration du délai de tolérance. FG-DOC-07 (§4.4, §7) précise que `contient()` est
défini par chaque sous-classe, que les zones sont « évaluées par ST_DWithin / ST_Covers », mappées par JPA en
table unique, et que les zones actives sont tenues en cache. FG-DOC-04 exige une évaluation en moins de 50 ms
au 95e centile pour 10 000 bracelets. Quatre points restaient à trancher.

## Décision

1. **Une seule implémentation de l'appartenance, dans le domaine.** `ZoneCirculaire.contient` (distance
   orthodromique) et `ZonePolygonale.contient` (test dans le plan local, exact au mètre près à l'échelle d'un
   quartier) sont appelées en mémoire sur les zones du cache. PostGIS stocke les formes en types géographiques
   indexés (GiST), prêts pour des requêtes spatiales d'ensemble, mais n'évalue pas chaque position : deux
   calculs concurrents du même fait (Java et SQL) finiraient par diverger, et l'évaluation en mémoire tient
   largement l'objectif de 50 ms. C'est un écart assumé à la lettre de FG-DOC-07 (« évaluée par ST_DWithin »).
2. **Accès par SQL direct, sans entité JPA.** Les types géographiques et la colonne JSONB de la plage se
   lisent et s'écrivent en SQL ; l'héritage reste une table unique à colonne discriminante et la colonne
   `version` porte le verrouillage optimiste, vérifié à chaque écriture. Le domaine n'a aucune dépendance.
3. **Une sortie suppose une présence.** Une sortie n'est signalée que si l'enfant a d'abord été vu dans la
   zone depuis le début de la plage en cours, puis mesuré dehors pendant tout le délai de tolérance. Un enfant
   qui n'est pas encore arrivé à l'école à 7 h ne déclenche rien ; l'absence à un lieu attendu serait une
   autre fonction, que les documents ne demandent pas. La sortie est signalée une seule fois ; le retour dans
   la zone est publié (`RetourEnZone`) et réarme le suivi. Hors plage, ou zone suspendue ou modifiée, le suivi
   repart de zéro.
4. **Une position imprécise ne change pas le suivi.** Au-delà de 300 m d'incertitude (position par antenne
   relais), une mesure ne prouve ni la présence ni la sortie : elle est ignorée par la surveillance des zones
   (paramètre `fasoguardian.geolocalisation.precision-maximale-m`). Le parent la voit tout de même sur la
   carte, marquée « approximative » avec son cercle d'incertitude (US-PAR-009).

Autres règles retenues :

- Une position plus ancienne que la dernière évaluée (vidange du tampon hors ligne) ne rejoue pas le suivi.
- Les heures d'une plage sont locales (`fasoguardian.fuseau`, Africa/Ouagadougou) ; une plage qui chevauche
  minuit appartient au jour où elle commence ; début et fin égaux désignent la journée entière.
- Créer, modifier, suspendre et supprimer une zone exigent le second facteur `MODIFIER_SAFE_ZONE`, comme le
  montre le design ; réactiver, qui rétablit la surveillance, ne l'exige pas.
- Le nombre de zones par enfant est borné (3, palier Intermédiaire de FG-DOC-11) en attendant le module
  abonnements, qui le fixera par offre.
- Un code SMS déjà utilisé peut être remplacé aussitôt : deux actions sensibles enchaînées ne font pas
  attendre le parent une minute (le délai de renvoi ne protège que contre l'envoi répété de codes non lus).

## Conséquences

- Un enfant emmené hors de la zone dans un lieu sans GPS, localisé seulement par antenne relais, ne déclenche
  pas d'alerte de zone tant qu'aucune position précise n'arrive ; les alertes de retrait et le SOS restent
  indépendants. Le seuil de 300 m sera réévalué sur les mesures du pilote.
- Avec un intervalle normal de 5 minutes, une sortie est signalée au plus tard un intervalle après la fin du
  délai de tolérance.
- Le fond de carte par défaut est celui d'OpenStreetMap, dont les conditions d'usage ne couvrent pas une
  exploitation commerciale à volume : le choix d'un fournisseur de tuiles reste à faire avant la production.
