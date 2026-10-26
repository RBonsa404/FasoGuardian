# Commande de la carte FasoGuardian V1

Dossier généré par `conception/fabriquer.py` depuis la carte contrôlée (DRC sans erreur ni avertissement, aucune
liaison manquante, parité avec le schéma). Ne pas retoucher les fichiers : corriger la source et rejouer la chaîne.
Le pas à pas de la commande et de la mise en service est dans `../guide/guide-commande-et-mise-en-service.pdf`.

## Fichiers

| Fichier | Usage |
|---|---|
| `gerber-fasoguardian-v1.zip` | À déposer chez le façonnier : 6 couches de cuivre, masques, pâtes, sérigraphies, contour, perçages |
| `nomenclature-jlcpcb.csv` | Nomenclature d'assemblage, une ligne par référence LCSC |
| `placement-jlcpcb.csv` | Position et rotation de chaque pièce, deux faces, à la convention de JLCPCB |
| `fasoguardian-v1.net` | Netlist du schéma |
| `schema-fasoguardian-v1.pdf`, `montage-face-*.pdf` | Schéma et plans de montage (repères et valeurs) |
| `carte-fasoguardian-v1.step`, `rendu-*.png` | Modèle 3D pour le boîtier, vues de contrôle |

## Circuit imprimé : options à choisir (ne pas laisser les valeurs par défaut)

| Option du site | Valeur | Raison |
|---|---|---|
| Base Material | FR-4 | |
| Layers | 6 | ADR 0042, point 7 |
| PCB Qty | 5 | |
| Delivery Format | Single PCB | JLCPCB ajoute lui-même les rails d'assemblage (voir l'assemblage) |
| PCB Thickness | 1.2 mm | Minimum du façonnier en 6 couches |
| Impedance Control | Yes, empilement JLC061211-3313 | Préimprégné 3313 de 0,092 mm (εr 4,1) entre L1 et L2 : lignes d'antenne de 50 Ω en guide coplanaire, piste de 0,186 mm, fente de 0,15 mm (`outils/impedance.py`) |
| Via Covering | Epoxy Filled & Capped | Vias de plan dans les pastilles (gratuit en 6 couches) |
| Min via hole size/diameter | 0.15 mm / 0.3 mm | Vias 0,3 / 0,15 mm ; 0,5 / 0,25 mm sur le plan du modem |
| Surface Finish | ENIG | Plots au pas de 0,4 mm (chargeur en boîtier à billes), contacts de la SIM |
| Outer / Inner Copper Weight | 1 oz / 1 oz | Chemin du modem à 20 mΩ, sous le budget de 30 mΩ (`controler_carte.py`) ; 33 mΩ avec 0,5 oz |
| Mark on PCB | Remove Mark | Aucun numéro de commande imprimé sur la carte |
| Confirm Production file | Yes | JLCPCB fait valider ses Gerber de production avant de fabriquer |

## Assemblage (PCBA) : options à choisir

| Option du site | Valeur | Raison |
|---|---|---|
| PCBA Type | Standard | Seule formule qui assemble les deux faces |
| Assembly Side | Both Sides | Composants sur les deux faces |
| PCBA Qty | 5 | 2 au minimum |
| Edge Rails / Fiducials | Added by JLCPCB, rails à gauche et à droite seulement | Carte de moins de 70 × 70 mm ; le bouton SW1 dépasse du bord inférieur de 0,3 mm |
| Confirm Parts Placement | Yes | Un technicien contrôle l'orientation de chaque pièce avant la production |
| Fichiers | `nomenclature-jlcpcb.csv` (BOM), `placement-jlcpcb.csv` (CPL) | Chaque ligne doit trouver sa pièce, sans substitution |

La radiographie des pièces à soudures cachées (module LGA, chargeur à billes, boîtiers QFN et DFN) est imposée et
facturée par JLCPCB. Les pastilles de test (TP), les pastilles Tag-Connect (J7) et les pièces non montées (R17,
C20 à C23) ne reçoivent rien : elles sont absentes de la nomenclature et du placement.

Remarque à coller dans le champ de commentaire de la commande :

```
Edge rails on the LEFT and RIGHT sides only: SW1 overhangs the bottom board edge by 0.3 mm.
Controlled impedance: 50 ohm coplanar waveguide on L1 (0.186 mm track, 0.15 mm gap), referenced to L2, stackup JLC061211-3313 (1 oz inner copper).
Via-in-pad: epoxy filled and capped.
Bottom side rotations in the CPL follow the JLCPCB convention (180 deg minus the KiCad angle).
Please check pin 1 of every IC against its silkscreen dot, on both sides.
Test points (TP*) and J7 (Tag-Connect pads) are bare pads: nothing to place.
```

## Avant de commander

- `python outils/stock_jlc.py 5` : aucune ligne en stock insuffisant. Les pièces au stock faible (microcontrôleur
  STM32U585CIT6) se réservent dans la bibliothèque personnelle de JLCPCB avant la commande.
- Dans la visionneuse de JLCPCB, vérifier face par face que le point 1 de chaque circuit intégré tombe sur le point
  de sérigraphie, et que les deux rails sont sur les bords gauche et droit.
