# Essais de charge (Gatling)

Simulations des parcours les plus sollicités, avec les seuils de FG-DOC-06 en assertions : une simulation
échoue si le seuil n'est pas tenu.

| Simulation | Parcours | Seuil |
|---|---|---|
| `PositionSimulation` | connexion d'un parent, puis lecture de la position de l'enfant toutes les 3 s | API position < 500 ms au 95e centile ; moins de 1 % d'échecs |

L'évaluation des Safe Zones (< 50 ms au 95e centile pour 10 000 bracelets) est mesurée par un essai
d'intégration du serveur, `EvaluationZonesChargeIT`, qui tourne avec les autres essais.

## Lancer

Sur la pile locale avec la démonstration (comptes fictifs) :

```bash
sh frontend/e2e/pile.sh demarrer && sh frontend/e2e/pile.sh demo
cd charge && ../backend/mvnw -B gatling:test -Dgatling.simulationClass=bf.fasoguardian.charge.PositionSimulation
```

Propriétés : `-Dbase=` (adresse du serveur, `http://localhost:8080` par défaut), `-Dtelephone=` et
`-DmotDePasse=` (compte de test), `-Dparents=` (parents par seconde au plateau, 5 par défaut), `-Dduree=`
(durée du plateau en secondes, 120 par défaut). Le rapport est écrit dans `charge/target/gatling/`.

Contre un environnement de recette, utiliser un compte de test de cet environnement, jamais un compte réel.

## Mesure de référence

Poste de développement (pile locale complète, une instance du serveur), 5 parents par seconde : 6 384
requêtes sans échec, API position à 26 ms au 95e centile (19 octobre 2026). À refaire sur le serveur du pilote.
