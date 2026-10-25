# Commande de la carte FasoGuardian V1

Dossier généré par `conception/fabriquer.py` depuis la carte contrôlée (DRC sans erreur ni avertissement, aucune
liaison manquante, parité avec le schéma). Ne pas retoucher les fichiers : corriger la source et rejouer la chaîne.

## Fichiers

| Fichier | Usage |
|---|---|
| `gerber-fasoguardian-v1.zip` | À déposer chez le façonnier : 6 couches de cuivre, masques, pâtes, sérigraphies, contour, perçages |
| `nomenclature-jlcpcb.csv`, `placement-jlcpcb.csv` | Assemblage (références LCSC, positions et rotations, deux faces) |
| `fasoguardian-v1.net` | Netlist du schéma, pour le contrôle électrique (test sous pointes) |
| `schema-fasoguardian-v1.pdf`, `montage-face-*.pdf` | Schéma et plans de montage (repères et valeurs) |
| `carte-fasoguardian-v1.step`, `rendu-*.png` | Modèle 3D pour le boîtier, vues de contrôle |

## Options à choisir à la commande (ne pas laisser les valeurs par défaut)

| Option | Valeur | Raison |
|---|---|---|
| Couches | 6 | ADR 0042, point 7 |
| Épaisseur | 1,2 mm | Minimum du façonnier en 6 couches |
| Empilement | Standard à préimprégné 3313 entre les couches 1 et 2 (0,0994 mm, εr 4,1) | Lignes d'antenne de 50 Ω : 0,20 mm, fente de 0,15 mm, référencées à la masse de la couche 2 (`outils/impedance.py`) |
| Contrôle d'impédance | Oui, 50 Ω ± 10 % sur la couche 1 (guide coplanaire) | Lignes RF LTE et GNSS |
| Cuivre | 1 oz sur les faces ; 0,5 oz à l'intérieur (1 oz en option : divise par deux la résistance du plan VBAT) | Chemin du modem calculé à 25 mΩ avec 0,5 oz |
| Finition | ENIG | Plots au pas de 0,4 mm (chargeur en boîtier à billes), contacts de la SIM |
| Vias | 0,3 mm percés à 0,15 mm, 0,2 mm entre perçages | Capacité standard en 6 couches |
| Vias dans les pastilles | Oui : bouchés à la résine et recouverts (POFV) | Vias de plan posés dans les pastilles ; procédé par défaut en 6 couches, à vérifier sur le devis |
| Pistes et isolements minimaux | 0,09 mm / 0,1 mm | Sorties des plots au pas de 0,5 mm |
| Assemblage | Deux faces | Face inférieure : composants de 1,2 mm de haut au plus |

## Avant de commander

Lever les points de la section 6 de `CAHIER.md` (variante du module, boîtier, batterie, stock du microcontrôleur).
