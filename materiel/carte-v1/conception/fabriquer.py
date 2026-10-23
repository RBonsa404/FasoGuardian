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
COUCHES = 'F.Cu,In1.Cu,In2.Cu,B.Cu,F.Paste,B.Paste,F.Silkscreen,B.Silkscreen,F.Mask,B.Mask,Edge.Cuts'
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
