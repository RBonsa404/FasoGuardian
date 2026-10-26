# Carte électronique V1 du bracelet

Circuit imprimé de l'étape V1 « Premier circuit » de FG-DOC-08 (§ 12) : 6 couches, 1,2 mm, ENIG, 41 × 36 mm, assemblage
double face. Les décisions et les écarts au document de conception sont dans [CAHIER.md](CAHIER.md) et
[l'ADR 0042](../../docs/adr/0042-carte-v1-du-bracelet.md).

## Une seule source de vérité

Tout part de [conception/carte.py](conception/carte.py). Pour chaque composant, il décrit :
- son symbole et son empreinte ;
- sa valeur, sa référence fabricant et LCSC ;
- sa face d'assemblage ;
- la liaison de chacune de ses broches, par numéro contrôlé sur la fiche du fabricant.

Le schéma, le circuit imprimé et le dossier de fabrication sont générés à partir de ce fichier. On ne les retouche
pas à la main : on corrige `carte.py`, puis on rejoue la chaîne.

## Chaîne de réalisation et points de contrôle

| Étape | Commande | Contrôle |
|---|---|---|
| 1. Netlist | `python conception/verifier.py` | Règles du bracelet : broches du tableau 8 et fonctions alternatives, domaines 1,8 / 3,3 V, PWRKEY et RESET, adresses I²C, réservoir ≥ 300 µF, ≤ 100 µF sur SYS, découplage de chaque rail, hauteur en face inférieure |
| 2. Schéma | `python conception/generer_schema.py` puis `kicad-cli sch erc` | ERC sans erreur ni avertissement, broches typées d'après les fiches (`typer_broches.py`) |
| 3. Placement | `KICAD_PY conception/generer_carte.py` | DRC avec parité schéma ↔ carte ; chemin du courant fort du modem pré-routé et verrouillé |
| 4. Routage | `KICAD_PY conception/relier_plans.py avant`, `KICAD_PY conception/router.py 60` (Freerouting), `KICAD_PY conception/relier_plans.py`, `KICAD_PY conception/finir.py 14` (puis `finir.py fenetre 1.0` si un quartier reste saturé), `KICAD_PY conception/elargir.py` | DRC sans erreur ni avertissement, aucune liaison manquante ; `KICAD_PY conception/controler_carte.py` : lignes RF ≤ 20 mm et sans via, chemin du modem ≤ 30 mΩ de cuivre (`resistance.py`), rail 1V8 ≥ 0,25 mm, plan VBAT d'un seul tenant ; revue `KICAD_PY conception/auditer.py` (découplage au pied des broches, masse sous les lignes RF et coutures, îlots des plans, vias des pastilles thermiques, distance au bord) |
| 5. Fabrication | `python outils/rotations_jlc.py` (après tout changement d'empreinte), `KICAD_PY conception/modeles_3d.py`, puis `python conception/fabriquer.py` | Empreintes identiques à celles de LCSC en orientation et en centre (sinon `fabriquer.py` refuse le placement) ; modèles 3D recopiés de la bibliothèque, hauteur de la face inférieure ≤ 1,35 mm d'après les fiches des fabricants ; Gerber, perçage, netlist, nomenclature (une ligne par référence LCSC) et placement à la convention JLCPCB (rotation de la face inférieure : 180° − angle KiCad), PDF, STEP, rendus ; options de commande dans `fabrication/COMMANDE.md` |
| 6. Commande | `python outils/stock_jlc.py 5` | Stock et coût des pièces pour 5 cartes |
| 7. Guide | `KICAD_PY guide/plans.py`, puis `node guide/imprimer.mjs` | Plans de câblage et de mesure, guide PDF de commande et de mise en service |

`KICAD_PY` est l'interpréteur Python de KiCad :
`C:\Users\rachi\AppData\Local\Programs\KiCad\10.0\bin\python.exe`.
Outils utilisés :
- KiCad 10.0.6 ;
- Freerouting 2.5.0 (Java 25, appelé par son chemin ; le projet garde le JDK 21) ;
- `easyeda2kicad` pour importer les pièces LCSC dans `bibliotheque/`. Chaque pièce importée est contrôlée contre la
  fiche de son fabricant : l'empreinte du module A7670E l'a été plot par plot contre la table SIMCom.

Deux revues indépendantes ont été menées, l'une sur le cahier, l'autre sur le schéma. Leurs conclusions sont reprises
dans `CAHIER.md` et dans l'ADR 0042.

## Contenu

- `conception/` : netlist de référence, générateurs, contrôles.
- `bibliotheque/` : symboles, empreintes et modèles 3D des pièces LCSC.
- `kicad/` : projet KiCad généré (schéma, carte, rapports ERC et DRC).
- `fabrication/` : dossier à envoyer au façonnier.
- `guide/` : guide de commande et de mise en service (`guide-commande-et-mise-en-service.pdf`) et ses sources.
- `outils/` : recherche des références chez JLCPCB, stock et coût, comparaison des empreintes avec LCSC, calcul
  d'impédance.
