"""Étape 5 : dossier de fabrication (Gerber, perçage, nomenclature et placement au format JLCPCB, PDF, rendu 3D).

Usage : python fabriquer.py — à lancer après le routage et les contrôles (DRC sans erreur).
"""
from __future__ import annotations

import csv
import json
import re
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
ROTATIONS = bib.PROJET / 'outils' / 'rotations_jlc.json'
COUCHES = 'F.Cu,In1.Cu,In2.Cu,In3.Cu,In4.Cu,B.Cu,F.Paste,B.Paste,F.Silkscreen,B.Silkscreen,F.Mask,B.Mask,Edge.Cuts'
# Pièces qui ne s'achètent pas : pastilles de test et de câblage, empreinte de programmation (pastilles nues).
SANS_PIECE = ('Connector:TestPoint', 'Connector:Conn_ARM_SWD_TagConnect_TC2030')


def cli(*arguments: str) -> None:
    subprocess.run([str(KICAD_CLI), *arguments], check=True, capture_output=True, text=True)


def ordre(repere: str) -> tuple[str, int]:
    """Clé de tri naturel des repères : C2 avant C10."""
    lettres, chiffres = re.fullmatch(r'([A-Z]+)(\d+)', repere).groups()
    return lettres, int(chiffres)


def nomenclature() -> Path:
    """Nomenclature au format JLCPCB (Comment, Designator, Footprint, LCSC), une ligne par référence LCSC : la
    machine charge un chargeur par référence. Quand les pièces d'une même référence portent des valeurs différentes
    au schéma (10 kΩ et 10 kΩ 1 %), le commentaire est la référence du fabricant."""
    groupes: dict[str, list] = defaultdict(list)
    for c in COMPOSANTS:
        if c.non_monte or c.symbole in SANS_PIECE:
            continue
        groupes[c.lcsc].append(c)
    chemin = SORTIE / 'nomenclature-jlcpcb.csv'
    with chemin.open('w', newline='', encoding='utf-8') as f:
        ecrit = csv.writer(f)
        ecrit.writerow(['Comment', 'Designator', 'Footprint', 'LCSC Part #', 'Manufacturer Part', 'Quantity'])
        for lcsc, pieces in sorted(groupes.items(), key=lambda g: min(ordre(c.ref) for c in g[1])):
            if len({(c.empreinte, c.fabricant) for c in pieces}) > 1:
                raise SystemExit(f'{lcsc} : empreintes ou références fabricant différentes ({", ".join(c.ref for c in pieces)})')
            valeurs = {c.valeur for c in pieces}
            commentaire = valeurs.pop() if len(valeurs) == 1 else pieces[0].fabricant
            refs = sorted((c.ref for c in pieces), key=ordre)
            ecrit.writerow([commentaire, ','.join(refs), pieces[0].empreinte.split(':', 1)[1], lcsc,
                            pieces[0].fabricant, len(refs)])
    return chemin


def empreintes_lcsc() -> None:
    """Refuse le placement si une empreinte diffère, en orientation ou en centre, de l'empreinte LCSC d'après
    laquelle la machine de JLCPCB pose la pièce (comparaison plot par plot de `outils/rotations_jlc.py`)."""
    ecarts = [cle for cle, e in json.loads(ROTATIONS.read_text(encoding='utf-8')).items()
              if e['rotation'] or abs(e['dx']) > 0.05 or abs(e['dy']) > 0.05]
    if ecarts:
        raise SystemExit(f'empreintes à rapprocher de LCSC avant le placement : {", ".join(ecarts)}')


def placement() -> Path:
    """Fichier de placement JLCPCB (Designator, Mid X, Mid Y, Layer, Rotation) depuis l'export de KiCad.

    Positions dans le repère des Gerber, pour les deux faces. Rotation : celle de KiCad en face supérieure ; en face
    inférieure, JLCPCB lit l'angle sur la carte retournée de gauche à droite sans retourner l'empreinte, soit
    180° − l'angle de KiCad (convention du greffon kicad-jlcpcb-tools et du modèle JLCPCB de KiBot). Sans cette
    conversion, une pièce de la face inférieure orientée à 0° serait posée à 180°."""
    empreintes_lcsc()
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
            dessus = ligne['Side'] == 'top'
            rotation = (float(ligne['Rot']) if dessus else 180 - float(ligne['Rot'])) % 360
            ecrit.writerow([ligne['Ref'], f"{float(ligne['PosX']):.4f}mm", f"{float(ligne['PosY']):.4f}mm",
                            'Top' if dessus else 'Bottom', f'{rotation:.0f}'])
    brut.unlink()
    return chemin


COMMANDE = """# Commande de la carte FasoGuardian V1

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
