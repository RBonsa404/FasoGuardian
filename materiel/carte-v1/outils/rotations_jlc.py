"""Écart d'orientation et de centre entre nos empreintes et celles de LCSC, que la machine de JLCPCB utilise.

Pour chaque pièce assemblée (référence LCSC), l'empreinte LCSC est téléchargée par easyeda2kicad, puis comparée
plot par plot à l'empreinte de la carte : on cherche la rotation (0, 90, 180 ou 270°) et le décalage qui superposent
les plots de même numéro. Le résultat alimente le fichier de placement (fabriquer.py) : un écart non nul, s'il n'était
pas compensé, ferait monter la pièce tournée.

Usage : python outils/rotations_jlc.py — écrit outils/rotations_jlc.json et affiche le tableau.
"""
from __future__ import annotations

import json
import math
import re
import subprocess
import sys
import time
from pathlib import Path

ICI = Path(__file__).resolve().parent
sys.path.insert(0, str(ICI.parent / 'conception'))
from carte import COMPOSANTS  # noqa: E402

KICAD = Path(r'C:\Users\rachi\AppData\Local\Programs\KiCad\10.0\share\kicad\footprints')
PROJET = ICI.parent / 'bibliotheque' / 'fg.pretty'
CACHE = ICI / '.cache-lcsc'
SORTIE = ICI / 'rotations_jlc.json'
SANS_PIECE = ('Connector:TestPoint', 'Connector:Conn_ARM_SWD_TagConnect_TC2030')
PLOT = re.compile(r'\(pad\s+"?([^"\s)]*)"?\s+\w+\s+\w+.*?\(at\s+([-\d.]+)\s+([-\d.]+)(?:\s+([-\d.]+))?\)', re.S)


def plots(fichier: Path) -> dict[str, tuple[float, float]]:
    """Centre de chaque plot numéroté (le premier s'il y en a plusieurs de même numéro)."""
    resultat = {}
    for numero, x, y, _ in PLOT.findall(fichier.read_text(encoding='utf-8')):
        if numero and numero not in resultat:
            resultat[numero] = (float(x), float(y))
    return resultat


def empreinte_lcsc(code: str) -> Path | None:
    dossier = CACHE / code
    if not list(dossier.glob('*.pretty/*.kicad_mod')):
        dossier.mkdir(parents=True, exist_ok=True)
        time.sleep(8)  # l'API d'EasyEDA refuse les requêtes trop rapprochées (HTTP 403)
        subprocess.run([sys.executable, '-m', 'easyeda2kicad', '--footprint', '--lcsc_id', code,
                        '--output', str(dossier / 'lcsc')], capture_output=True, text=True, timeout=180)
    fichiers = list(dossier.glob('*.pretty/*.kicad_mod'))
    return fichiers[0] if fichiers else None


def tourner(p: tuple[float, float], angle: float) -> tuple[float, float]:
    """Rotation de KiCad (sens trigonométrique à l'écran, axe y vers le bas)."""
    a = math.radians(angle)
    return p[0] * math.cos(a) + p[1] * math.sin(a), -p[0] * math.sin(a) + p[1] * math.cos(a)


def ecart(nos: dict, leurs: dict) -> tuple[float, float, float, float] | None:
    """(rotation, décalage x, décalage y, résidu) qui amène l'empreinte LCSC sur la nôtre : nos = R(rotation)·leurs + t."""
    communs = sorted(set(nos) & set(leurs))
    if len(communs) < 2:
        return None
    meilleur = None
    for angle in (0, 90, 180, 270):
        tournes = [tourner(leurs[n], angle) for n in communs]
        tx = sum(nos[n][0] - p[0] for n, p in zip(communs, tournes)) / len(communs)
        ty = sum(nos[n][1] - p[1] for n, p in zip(communs, tournes)) / len(communs)
        residu = max(math.dist(nos[n], (p[0] + tx, p[1] + ty)) for n, p in zip(communs, tournes))
        if meilleur is None or residu < meilleur[3]:
            meilleur = (angle, tx, ty, residu)
    return meilleur


def main() -> None:
    vus = {}
    for c in COMPOSANTS:
        if c.non_monte or c.symbole in SANS_PIECE or not c.lcsc:
            continue
        vus.setdefault((c.empreinte, c.lcsc), []).append(c.ref)
    resultats = {}
    print(f'{"empreinte":<52} {"LCSC":>9} {"rot.":>5} {"dx":>6} {"dy":>6} {"résidu":>7}  repères')
    for (empreinte, code), refs in sorted(vus.items()):
        bibliotheque, nom = empreinte.split(':', 1)
        nous = (PROJET if bibliotheque == 'fg' else KICAD / f'{bibliotheque}.pretty') / f'{nom}.kicad_mod'
        leur = empreinte_lcsc(code)
        if leur is None or not nous.exists():
            print(f'{empreinte:<52} {code:>9}   empreinte LCSC introuvable')
            continue
        e = ecart(plots(nous), plots(leur))
        if e is None:
            print(f'{empreinte:<52} {code:>9}   numéros de plots sans correspondance')
            continue
        angle, dx, dy, residu = e
        resultats[empreinte + '|' + code] = {'rotation': angle, 'dx': round(dx, 3), 'dy': round(dy, 3),
                                              'residu': round(residu, 3), 'refs': refs}
        alerte = '  <-- à vérifier' if residu > 0.15 else ''
        print(f'{empreinte:<52} {code:>9} {angle:>5} {dx:>6.2f} {dy:>6.2f} {residu:>7.3f}  {",".join(refs[:6])}'
              f'{"…" if len(refs) > 6 else ""}{alerte}')
    SORTIE.write_text(json.dumps(resultats, indent=1, ensure_ascii=False) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()
