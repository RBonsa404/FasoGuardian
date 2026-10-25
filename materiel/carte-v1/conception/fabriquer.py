"""Étape 5 : dossier de fabrication (Gerber, perçage, nomenclature et placement au format JLCPCB, PDF, rendu 3D).

Usage : python fabriquer.py — à lancer après le routage et les contrôles (DRC sans erreur).
"""
from __future__ import annotations

import csv
import shutil
import subprocess
import zipfile
from collections import defaultdict
from pathlib import Path

import bibliotheques as bib
from carte import COMPOSANTS

KICAD_CLI = bib.KICAD / 'bin' / 'kicad-cli.exe'
PROJET = bib.PROJET / 'kicad'
CARTE = PROJET / 'fasoguardian-v1.kicad_pcb'
SCHEMA = PROJET / 'fasoguardian-v1.kicad_sch'
SORTIE = bib.PROJET / 'fabrication'
COUCHES = 'F.Cu,In1.Cu,In2.Cu,In3.Cu,In4.Cu,B.Cu,F.Paste,B.Paste,F.Silkscreen,B.Silkscreen,F.Mask,B.Mask,Edge.Cuts'
# Pièces qui ne s'achètent pas : pastilles de test et de câblage, empreinte de programmation (pastilles nues).
SANS_PIECE = ('Connector:TestPoint', 'Connector:Conn_ARM_SWD_TagConnect_TC2030')


def cli(*arguments: str) -> None:
    subprocess.run([str(KICAD_CLI), *arguments], check=True, capture_output=True, text=True)


def nomenclature() -> Path:
    """Nomenclature au format JLCPCB (Comment, Designator, Footprint, LCSC), regroupée par référence."""
    groupes: dict[tuple, list[str]] = defaultdict(list)
    for c in COMPOSANTS:
        if c.non_monte or c.symbole in SANS_PIECE:
            continue
        groupes[(c.valeur, c.empreinte.split(':', 1)[1], c.lcsc, c.fabricant)].append(c.ref)
    chemin = SORTIE / 'nomenclature-jlcpcb.csv'
    with chemin.open('w', newline='', encoding='utf-8') as f:
        ecrit = csv.writer(f)
        ecrit.writerow(['Comment', 'Designator', 'Footprint', 'LCSC Part #', 'Manufacturer Part', 'Quantity'])
        for (valeur, empreinte, lcsc, fabricant), refs in sorted(groupes.items(), key=lambda g: g[1][0]):
            ecrit.writerow([valeur, ','.join(sorted(refs)), empreinte, lcsc, fabricant, len(refs)])
    return chemin


def placement() -> Path:
    """Fichier de placement JLCPCB (Designator, Mid X, Mid Y, Layer, Rotation) depuis l'export de KiCad."""
    brut = SORTIE / 'placement-kicad.csv'
    cli('pcb', 'export', 'pos', '--format', 'csv', '--units', 'mm', '--side', 'both', '--use-drill-file-origin',
        '--exclude-dnp', '-o', str(brut), str(CARTE))
    sans_piece = {c.ref for c in COMPOSANTS if c.symbole in SANS_PIECE}
    chemin = SORTIE / 'placement-jlcpcb.csv'
    with brut.open(encoding='utf-8') as source, chemin.open('w', newline='', encoding='utf-8') as cible:
        ecrit = csv.writer(cible)
        ecrit.writerow(['Designator', 'Mid X', 'Mid Y', 'Layer', 'Rotation'])
        for ligne in csv.DictReader(source):
            if ligne['Ref'] in sans_piece:
                continue
            face = 'Top' if ligne['Side'] == 'top' else 'Bottom'
            ecrit.writerow([ligne['Ref'], f"{float(ligne['PosX']):.4f}mm", f"{float(ligne['PosY']):.4f}mm", face,
                            f"{float(ligne['Rot']):.0f}"])
    brut.unlink()
    return chemin


COMMANDE = """# Commande de la carte FasoGuardian V1

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
"""


def main() -> None:
    if SORTIE.exists():
        for element in SORTIE.iterdir():
            shutil.rmtree(element) if element.is_dir() else element.unlink()
    SORTIE.mkdir(exist_ok=True)
    gerber = SORTIE / 'gerber'
    gerber.mkdir()
    cli('pcb', 'export', 'gerbers', '--layers', COUCHES, '--subtract-soldermask', '--use-drill-file-origin',
        '-o', str(gerber), str(CARTE))
    cli('pcb', 'export', 'drill', '--format', 'excellon', '--excellon-separate-th', '--generate-map', '--map-format', 'pdf',
        '--drill-origin', 'plot', '-o', str(gerber) + '\\', str(CARTE))
    with zipfile.ZipFile(SORTIE / 'gerber-fasoguardian-v1.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
        for fichier in sorted(gerber.iterdir()):
            archive.write(fichier, fichier.name)
    nomenclature()
    placement()
    cli('sch', 'export', 'netlist', '-o', str(SORTIE / 'fasoguardian-v1.net'), str(SCHEMA))
    (SORTIE / 'COMMANDE.md').write_text(COMMANDE, encoding='utf-8')
    cli('sch', 'export', 'pdf', '-o', str(SORTIE / 'schema-fasoguardian-v1.pdf'), str(SCHEMA))
    cli('pcb', 'export', 'pdf', '--layers', 'F.Fab,Edge.Cuts', '--include-border-title', '-o',
        str(SORTIE / 'montage-face-superieure.pdf'), str(CARTE))
    cli('pcb', 'export', 'pdf', '--layers', 'B.Fab,Edge.Cuts', '--mirror', '--include-border-title', '-o',
        str(SORTIE / 'montage-face-inferieure.pdf'), str(CARTE))
    cli('pcb', 'export', 'step', '--subst-models', '-o', str(SORTIE / 'carte-fasoguardian-v1.step'), str(CARTE))
    for face in ('top', 'bottom'):
        cli('pcb', 'render', '--side', face, '--width', '1600', '--height', '1400', '--quality', 'high',
            '-o', str(SORTIE / f'rendu-{"dessus" if face == "top" else "dessous"}.png'), str(CARTE))
    print('dossier de fabrication :', SORTIE)
    for fichier in sorted(SORTIE.iterdir()):
        print('  ', fichier.name)


if __name__ == '__main__':
    main()
