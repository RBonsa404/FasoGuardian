"""Reporte sur la carte routée la valeur et les champs de chaque composant de carte.py, sans toucher à la géométrie.

Sert quand une référence change sans changer d'empreinte (STM32U575CIT6 → STM32U585CIT6, même boîtier) : la carte
reprend la valeur et les champs LCSC, Fabricant et Note du schéma, ce que vérifie la parité du DRC. Refuse le report
si l'empreinte d'un composant a changé : il faut alors rejouer le placement.
À lancer avec l'interpréteur Python de KiCad :
    "C:\\Users\\rachi\\AppData\\Local\\Programs\\KiCad\\10.0\\bin\\python.exe" reporter_champs.py
"""
from __future__ import annotations

import sys
from pathlib import Path

import pcbnew

from carte import COMPOSANTS

CARTE = Path(__file__).resolve().parent.parent / 'kicad' / 'fasoguardian-v1.kicad_pcb'


def main() -> int:
    carte = pcbnew.LoadBoard(str(CARTE))
    empreintes = {fp.GetReference(): fp for fp in carte.GetFootprints()}
    modifies = []
    for c in COMPOSANTS:
        fp = empreintes.get(c.ref)
        if fp is None:
            raise SystemExit(f'{c.ref} absent de la carte : rejouer le placement')
        if fp.GetFPIDAsString() != c.empreinte:
            raise SystemExit(f'{c.ref} : empreinte {fp.GetFPIDAsString()} sur la carte, {c.empreinte} au schéma')
        avant = (fp.GetValue(), *(fp.GetFieldText(champ) for champ in ('LCSC', 'Fabricant', 'Note')))
        if avant == (c.valeur, c.lcsc, c.fabricant, c.note):
            continue
        fp.SetValue(c.valeur)
        for champ, valeur in (('LCSC', c.lcsc), ('Fabricant', c.fabricant), ('Note', c.note)):
            fp.SetField(champ, valeur)
        modifies.append(c.ref)
    if modifies:
        carte.Save(str(CARTE))
    print(f'champs reportés : {", ".join(modifies) or "aucun"}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
