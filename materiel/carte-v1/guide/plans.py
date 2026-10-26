"""Plans de câblage et de mesure du guide : une vue de chaque face de la carte, pastilles repérées.

La face inférieure est dessinée vue de dessous (retournée de gauche à droite), comme on la voit en soudant. Chaque
pastille de câblage, point de mesure ou connecteur porte un repère et une étiquette courte, posée au plus près sans
chevaucher les autres ; les libellés complets sont dans les tableaux du guide.
À lancer avec l'interpréteur Python de KiCad :
    "C:\\Users\\rachi\\AppData\\Local\\Programs\\KiCad\\10.0\\bin\\python.exe" guide/plans.py
"""
from __future__ import annotations

import math
import sys
from html import escape
from pathlib import Path

import pcbnew

ICI = Path(__file__).resolve().parent
CARTE = ICI.parent / 'kicad' / 'fasoguardian-v1.kicad_pcb'
MARGE, RAYON, POLICE = 4.5, 1.0, 0.95
# Repère : (catégorie, étiquette). Câblage : éléments soudés hors carte ; mesure : tensions et liaisons à contrôler.
REPERES = {
    'TP1': ('cablage', '+5V'), 'TP2': ('cablage', 'GND'), 'TP8': ('cablage', 'BAT+'), 'TP9': ('cablage', 'BAT−'),
    'TP17': ('cablage', 'NTC'), 'TP30': ('cablage', 'BZ+'), 'TP31': ('cablage', 'BZ−'), 'TP32': ('cablage', 'M+'),
    'TP33': ('cablage', 'M−'), 'TP34': ('cablage', 'LP'), 'TP35': ('cablage', 'LP−'), 'TP36': ('cablage', 'EL'),
    'J7': ('connecteur', 'J7 SWD'), 'J3': ('connecteur', 'J3 LTE'), 'J4': ('connecteur', 'J4 GNSS'),
    'J5': ('connecteur', 'J5 SIM'), 'J6': ('connecteur', 'J6'), 'SW1': ('connecteur', 'SW1 SOS'),
    'U1': ('connecteur', 'U1 MCU'), 'U2': ('connecteur', 'U2 module'),
    'TP3': ('mesure', 'TP3 VBAT'), 'TP4': ('mesure', 'TP4 VSYS'), 'TP5': ('mesure', 'TP5 3V3'),
    'TP6': ('mesure', 'TP6 VBAT_MOD'), 'TP7': ('mesure', 'TP7 GND'), 'TP16': ('mesure', 'TP16 1V8'),
    'TP10': ('mesure', 'TP10'), 'TP11': ('mesure', 'TP11'), 'TP12': ('mesure', 'TP12'), 'TP13': ('mesure', 'TP13'),
    'TP14': ('mesure', 'TP14'), 'TP15': ('mesure', 'TP15'), 'TP20': ('mesure', 'TP20'), 'TP40': ('mesure', 'TP40'),
    'TP41': ('mesure', 'TP41'), 'TP42': ('mesure', 'TP42'), 'TP43': ('mesure', 'TP43'), 'TP44': ('mesure', 'TP44'),
    'TP45': ('mesure', 'TP45'),
}
COULEURS = {'cablage': '#c2410c', 'mesure': '#1d4ed8', 'connecteur': '#15803d'}
ORDRE = {'cablage': 0, 'connecteur': 1, 'mesure': 2}


def mm(v: int) -> float:
    return pcbnew.ToMM(v)


def chevauche(a: tuple, b: tuple) -> bool:
    return a[0] < b[2] and b[0] < a[2] and a[1] < b[3] and b[1] < a[3]


def plan(carte: pcbnew.BOARD, dessous: bool) -> str:
    bord = carte.GetBoardEdgesBoundingBox()
    x0, y0, largeur, hauteur = mm(bord.GetLeft()), mm(bord.GetTop()), mm(bord.GetWidth()), mm(bord.GetHeight())
    face = pcbnew.B_Cu if dessous else pcbnew.F_Cu
    total_l, total_h = largeur + 2 * MARGE, hauteur + 2 * MARGE

    def vue(x: float, y: float) -> tuple[float, float]:
        x, y = x - x0, y - y0
        return (largeur - x if dessous else x) + MARGE, y + MARGE

    fond = [f'<rect x="{MARGE}" y="{MARGE}" width="{largeur:.2f}" height="{hauteur:.2f}" rx="{RAYON}" '
            f'fill="#1f5135" stroke="#0b2a1a" stroke-width="0.15"/>']
    reperes = []
    for fp in carte.GetFootprints():
        for pad in fp.Pads():
            if pad.GetAttribute() == pcbnew.PAD_ATTRIB_NPTH:
                cx, cy = vue(mm(pad.GetPosition().x), mm(pad.GetPosition().y))
                fond.append(f'<circle cx="{cx:.3f}" cy="{cy:.3f}" r="{mm(pad.GetDrillSize().x) / 2:.3f}" fill="#0b2a1a"/>')
            elif pad.IsOnLayer(face):
                boite = pad.GetBoundingBox()
                (xa, ya), (xb, yb) = vue(mm(boite.GetLeft()), mm(boite.GetTop())), vue(mm(boite.GetRight()), mm(boite.GetBottom()))
                fond.append(f'<rect x="{min(xa, xb):.3f}" y="{min(ya, yb):.3f}" width="{abs(xb - xa):.3f}" '
                            f'height="{abs(yb - ya):.3f}" fill="#c9a227"/>')
        if fp.IsFlipped() == dessous and fp.GetReference() in REPERES:
            reperes.append((fp.GetReference(), *vue(mm(fp.GetPosition().x), mm(fp.GetPosition().y))))
    reperes.sort(key=lambda r: ORDRE[REPERES[r[0]][0]])
    # Étiquettes : première place libre autour du repère (distance croissante, huit directions), dans le dessin.
    marques = [(x - 0.55, y - 0.55, x + 0.55, y + 0.55) for _, x, y in reperes]
    occupees, dessins = [], []
    for ref, x, y in reperes:
        categorie, texte = REPERES[ref]
        l, h = 0.56 * POLICE * len(texte) + 0.5, POLICE * 1.3
        choisie = None
        for d in (0.9, 1.5, 2.2, 3.0, 4.0, 5.2):
            for angle in (0, 180, 90, 270, 45, 135, 225, 315, 22, 158, 202, 338):
                cx, cy = x + d * math.cos(math.radians(angle)), y - d * math.sin(math.radians(angle))
                gauche = cx - l / 2 + (l / 2 if angle in (0, 22, 338, 45, 315) else -l / 2 if angle in (180, 158, 202, 135, 225) else 0)
                boite = (gauche, cy - h / 2, gauche + l, cy + h / 2)
                if boite[0] < 0.2 or boite[1] < 0.2 or boite[2] > total_l - 0.2 or boite[3] > total_h - 0.2:
                    continue
                if any(chevauche(boite, b) for b in occupees) or \
                        any(chevauche(boite, m) for m, r in zip(marques, reperes) if r[0] != ref):
                    continue
                choisie = boite
                break
            if choisie:
                break
        if choisie is None:
            raise SystemExit(f'pas de place pour l\'étiquette de {ref}')
        occupees.append(choisie)
        couleur = COULEURS[categorie]
        bx, by = (choisie[0] + choisie[2]) / 2, (choisie[1] + choisie[3]) / 2
        px = min(max(x, choisie[0]), choisie[2])
        py = min(max(y, choisie[1]), choisie[3])
        if math.dist((x, y), (px, py)) > 0.6:
            dessins.append(f'<line x1="{x:.2f}" y1="{y:.2f}" x2="{px:.2f}" y2="{py:.2f}" stroke="{couleur}" '
                           f'stroke-width="0.12"/>')
        dessins.append(f'<circle cx="{x:.2f}" cy="{y:.2f}" r="0.5" fill="none" stroke="{couleur}" stroke-width="0.22"/>')
        dessins.append(f'<rect x="{choisie[0]:.2f}" y="{choisie[1]:.2f}" width="{choisie[2] - choisie[0]:.2f}" '
                       f'height="{h:.2f}" rx="0.25" fill="#ffffff" fill-opacity="0.92" stroke="{couleur}" '
                       f'stroke-width="0.1"/>')
        dessins.append(f'<text x="{bx:.2f}" y="{by + POLICE * 0.36:.2f}" text-anchor="middle" fill="{couleur}" '
                       f'font-weight="600">{escape(texte)}</text>')
    titre = 'Face inférieure, vue de dessous (côté batterie)' if dessous else 'Face supérieure, vue de dessus'
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {total_l:.2f} {total_h:.2f}" '
            f'font-family="Segoe UI, Arial, sans-serif" font-size="{POLICE}">'
            f'<title>{titre}</title>{"".join(fond)}{"".join(dessins)}</svg>\n')


def main() -> None:
    carte = pcbnew.LoadBoard(str(sys.argv[1]) if len(sys.argv) > 1 else str(CARTE))
    for nom, dessous in (('plan-dessus.svg', False), ('plan-dessous.svg', True)):
        (ICI / nom).write_text(plan(carte, dessous), encoding='utf-8')
        print('plan écrit :', ICI / nom)


if __name__ == '__main__':
    main()
