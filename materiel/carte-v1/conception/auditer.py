"""Revue de conception de la carte routée : ce que le DRC ne mesure pas.

- découplage : distance de chaque broche d'alimentation de circuit intégré au condensateur le plus proche de son rail ;
- radio : plan de masse continu sous les lignes de 50 Ω, masse coplanaire des deux côtés, coutures de masse, vias
  étrangers voisins ;
- plans : îlots de chaque zone remplie ;
- pastilles thermiques : vias vers la masse, couverture de pâte à braser ;
- bords : distance des composants au contour.

À lancer avec l'interpréteur Python de KiCad :
    "C:\\Users\\rachi\\AppData\\Local\\Programs\\KiCad\\10.0\\bin\\python.exe" auditer.py
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

import pcbnew

sys.path.insert(0, str(Path(__file__).resolve().parent))
from carte import RAILS_DECOUPLES  # noqa: E402

CARTE = Path(__file__).resolve().parent.parent / 'kicad' / 'fasoguardian-v1.kicad_pcb'
RF = ('RF_LTE_MOD', 'RF_LTE_1', 'RF_LTE_2', 'RF_GNSS_MOD', 'RF_GNSS_1')
mm = pcbnew.ToMM


def point(x: float, y: float) -> pcbnew.VECTOR2I:
    return pcbnew.VECTOR2I(pcbnew.FromMM(x), pcbnew.FromMM(y))


def distance(a: pcbnew.VECTOR2I, b: pcbnew.VECTOR2I) -> float:
    return mm((a - b).EuclideanNorm())


def decouplage(carte: pcbnew.BOARD) -> list[tuple]:
    """(distance, broche, rail, condensateur) pour chaque broche d'alimentation de circuit intégré."""
    rails = [r for r in RAILS_DECOUPLES if r != 'GND']
    condensateurs = {}
    for fp in carte.GetFootprints():
        if not fp.GetReference().startswith('C'):
            continue
        nets = {p.GetNetname() for p in fp.Pads()}
        for rail in rails:
            if rail in nets and 'GND' in nets:
                condensateurs.setdefault(rail, []).append(fp)
    lignes = []
    for fp in carte.GetFootprints():
        if not fp.GetReference().startswith('U'):
            continue
        for p in fp.Pads():
            rail = p.GetNetname()
            if rail not in condensateurs:
                continue
            meilleur = min(((distance(p.GetPosition(), q.GetPosition()), c.GetReference())
                            for c in condensateurs[rail] for q in c.Pads() if q.GetNetname() == rail), default=(math.inf, '—'))
            lignes.append((meilleur[0], f'{fp.GetReference()}.{p.GetNumber()}', rail, meilleur[1]))
    return sorted(lignes, reverse=True)


def zone_contient(carte: pcbnew.BOARD, reseau: str, couche: int, p: pcbnew.VECTOR2I) -> bool:
    return any(z.GetNetname() == reseau and z.IsOnLayer(couche) and z.GetFilledPolysList(couche).Contains(p)
               for z in carte.Zones() if not z.GetIsRuleArea())


def radio(carte: pcbnew.BOARD) -> None:
    masse_vias = [t for t in carte.GetTracks() if t.GetClass() == 'PCB_VIA' and t.GetNetname() == 'GND']
    autres_vias = [t for t in carte.GetTracks() if t.GetClass() == 'PCB_VIA' and t.GetNetname() not in ('GND',) + RF]
    for reseau in RF:
        segments = [t for t in carte.GetTracks() if t.GetClass() == 'PCB_TRACK' and t.GetNetname() == reseau]
        if not segments:
            continue
        echantillons = sous_plan = cote_g = cote_d = 0
        coutures = set()
        etranger = math.inf
        for s in segments:
            a, b = s.GetStart(), s.GetEnd()
            longueur = mm((b - a).EuclideanNorm())
            if longueur < 1e-6:
                continue
            ux, uy = mm(b.x - a.x) / longueur, mm(b.y - a.y) / longueur
            ecart = mm(s.GetWidth()) / 2 + 0.15 + 0.12  # bord de la piste, fente de 0,15 mm, puis masse
            n = max(2, int(longueur / 0.2))
            for k in range(n + 1):
                x, y = mm(a.x) + ux * longueur * k / n, mm(a.y) + uy * longueur * k / n
                echantillons += 1
                sous_plan += zone_contient(carte, 'GND', pcbnew.In1_Cu, point(x, y))
                cote_g += zone_contient(carte, 'GND', s.GetLayer(), point(x - uy * ecart, y + ux * ecart))
                cote_d += zone_contient(carte, 'GND', s.GetLayer(), point(x + uy * ecart, y - ux * ecart))
            seg = pcbnew.SEG(a, b)
            for v in masse_vias:
                if mm(seg.Distance(v.GetPosition())) < 1.2:
                    coutures.add(id(v))
            for v in autres_vias:
                etranger = min(etranger, mm(seg.Distance(v.GetPosition())))
        longueur_totale = sum(mm(s.GetLength()) for s in segments)
        print(f'  {reseau:12} {longueur_totale:5.1f} mm : plan In1 dessous {100 * sous_plan / echantillons:5.1f} %, '
              f'masse coplanaire {100 * cote_g / echantillons:5.1f} % / {100 * cote_d / echantillons:5.1f} %, '
              f'{len(coutures)} via(s) de couture à moins de 1,2 mm, via étranger le plus proche {etranger:.2f} mm')


def plans(carte: pcbnew.BOARD) -> None:
    for z in sorted((z for z in carte.Zones() if not z.GetIsRuleArea()), key=lambda z: (z.GetFirstLayer(), z.GetNetname())):
        for couche in z.GetLayerSet().CuStack():
            polys = z.GetFilledPolysList(couche)
            aires = sorted((abs(polys.Outline(k).Area()) / 1e12 for k in range(polys.OutlineCount())), reverse=True)
            if aires:
                print(f'  {z.GetNetname():11} {carte.GetLayerName(couche):11} {len(aires):3} îlot(s) : '
                      + ', '.join(f'{a:.1f}' for a in aires[:6]) + (' …' if len(aires) > 6 else '') + ' mm²')


def pastilles_thermiques(carte: pcbnew.BOARD) -> None:
    vias = [t for t in carte.GetTracks() if t.GetClass() == 'PCB_VIA']
    for fp in carte.GetFootprints():
        for p in fp.Pads():
            l, h = mm(p.GetSize().x), mm(p.GetSize().y)
            if p.GetAttribute() != pcbnew.PAD_ATTRIB_SMD or l * h < 0.9 or not fp.GetReference().startswith('U'):
                continue
            dedans = [v for v in vias if v.GetNetname() == p.GetNetname() and p.HitTest(v.GetPosition())]
            marge = mm(p.GetLocalSolderPasteMargin() or 0)
            ratio = p.GetLocalSolderPasteMarginRatio() or 0.0
            couverture = max(0.0, (l + 2 * marge + l * ratio) * (h + 2 * marge + h * ratio)) / (l * h)
            print(f'  {fp.GetReference()}.{p.GetNumber():3} {p.GetNetname():10} {l:.2f} × {h:.2f} mm : {len(dedans)} via(s), '
                  f'pâte {100 * couverture:.0f} % de la pastille')


def bords(carte: pcbnew.BOARD) -> None:
    contour = carte.GetBoardEdgesBoundingBox()
    lignes = []
    for fp in carte.GetFootprints():
        b = fp.GetCourtyard(fp.GetLayer()).BBox() if fp.GetCourtyard(fp.GetLayer()).OutlineCount() else fp.GetBoundingBox()
        d = min(mm(b.GetLeft() - contour.GetLeft()), mm(contour.GetRight() - b.GetRight()),
                mm(b.GetTop() - contour.GetTop()), mm(contour.GetBottom() - b.GetBottom()))
        lignes.append((d, fp.GetReference(), fp.GetValue()))
    for d, ref, valeur in sorted(lignes)[:12]:
        print(f'  {ref:5} {valeur:24} {d:6.2f} mm du bord')


def main() -> None:
    carte = pcbnew.LoadBoard(str(CARTE))
    pcbnew.ZONE_FILLER(carte).Fill(carte.Zones())
    print('Découplage : broches d\'alimentation de circuit intégré les plus loin de leur condensateur')
    for d, broche, rail, cond in decouplage(carte)[:30]:
        print(f'  {broche:8} {rail:11} {cond:5} {d:5.2f} mm')
    print('Radio : lignes de 50 Ω')
    radio(carte)
    print('Plans : îlots des zones remplies')
    plans(carte)
    print('Pastilles thermiques des circuits intégrés')
    pastilles_thermiques(carte)
    print('Composants les plus proches du bord (enveloppe)')
    bords(carte)


if __name__ == '__main__':
    main()
