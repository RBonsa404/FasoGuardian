"""Résistance du cuivre entre deux groupes de plots d'un réseau, calculée sur le circuit routé.

Le cuivre du réseau est résolu comme un circuit de résistances (loi des nœuds), par gradient conjugué :
- piste : ρ·L / (largeur · épaisseur) ;
- via : ρ·h / (π · perçage · placage), réparti entre les couches qu'il traverse ;
- plan (zone remplie) : maillage de PAS mm, chaque lien entre deux mailles voisines valant ρ / épaisseur (un carré) ;
- plot : relié à tout le cuivre du réseau qu'il touche sur ses couches.
Sert au contrôle du chemin des pointes de courant du modem (controler_carte.py).

À lancer avec l'interpréteur Python de KiCad :
    "C:\\Users\\rachi\\AppData\\Local\\Programs\\KiCad\\10.0\\bin\\python.exe" resistance.py RESEAU REF.PLOT[,REF.PLOT] REF.PLOT[,…] [carte]
"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
import pcbnew

CARTE = Path(__file__).resolve().parent.parent / 'kicad' / 'fasoguardian-v1.kicad_pcb'
RHO = 1.72e-8                                    # résistivité du cuivre (Ω·m)
EPAISSEUR_EXT, EPAISSEUR_INT = 35e-6, 30e-6      # 1 oz sur les faces et à l'intérieur (JLC061211-3313 : 0,03 mm)
PLACAGE = 18e-6                                  # cuivre déposé dans un perçage de via (m)
HAUTEUR = 1.2e-3                                 # épaisseur de la carte (m)
PAS = 0.2                                        # maille des plans (mm)
COUCHES = (pcbnew.F_Cu, pcbnew.In1_Cu, pcbnew.In2_Cu, pcbnew.In3_Cu, pcbnew.In4_Cu, pcbnew.B_Cu)
FORT = 1e4                                       # conductance d'un contact franc (S)


def epaisseur(couche: int) -> float:
    return EPAISSEUR_EXT if couche in (pcbnew.F_Cu, pcbnew.B_Cu) else EPAISSEUR_INT


class Circuit:
    def __init__(self):
        self.noeuds: dict = {}
        self.branches: list[tuple[int, int, float]] = []

    def noeud(self, cle) -> int:
        return self.noeuds.setdefault(cle, len(self.noeuds))

    def relier(self, a: int, b: int, g: float) -> None:
        if a != b:
            self.branches.append((a, b, g))

    def resistance(self, sources: list[int], puits: list[int]) -> float:
        n = len(self.noeuds)
        a = np.array([b[0] for b in self.branches])
        b = np.array([b[1] for b in self.branches])
        g = np.array([b[2] for b in self.branches])
        diag = np.zeros(n)
        np.add.at(diag, a, g)
        np.add.at(diag, b, g)
        # Les puits sont reliés à la masse par un contact franc ; 1 A est injecté, réparti sur les sources.
        masse = np.zeros(n)
        masse[puits] = FORT
        diag = diag + masse + 1e-12

        def produit(v):
            r = diag * v
            np.add.at(r, a, -g * v[b])
            np.add.at(r, b, -g * v[a])
            return r

        courant = np.zeros(n)
        courant[sources] = 1.0 / len(sources)
        v = np.zeros(n)
        r = courant - produit(v)
        z = r / diag
        p = z.copy()
        rz = r @ z
        for _ in range(200_000):
            ap = produit(p)
            alpha = rz / (p @ ap)
            v += alpha * p
            r -= alpha * ap
            if np.linalg.norm(r) < 1e-12:
                break
            z = r / diag
            rz, rz_ancien = r @ z, rz
            p = z + (rz / rz_ancien) * p
        else:
            return float('inf')
        if not np.all(np.isfinite(v)):
            return float('inf')
        return float(np.mean(v[sources]) - 1.0 / (FORT * len(puits)))


def construire(carte: pcbnew.BOARD, reseau: str) -> tuple[Circuit, dict[str, list[int]]]:
    c = Circuit()
    um = lambda v: round(v / 1000)  # au micromètre près
    pistes = [t for t in carte.GetTracks() if t.GetNetname() == reseau]
    segments = [t for t in pistes if t.GetClass() != 'PCB_VIA']
    points_par_couche: dict[int, list] = {k: [] for k in COUCHES}
    for t in segments:
        for bout in (t.GetStart(), t.GetEnd()):
            points_par_couche[t.GetLayer()].append((bout, c.noeud(('p', um(bout.x), um(bout.y), t.GetLayer()))))
    for v in pistes:
        if v.GetClass() == 'PCB_VIA':
            for couche in COUCHES:
                p = v.GetPosition()
                points_par_couche[couche].append((p, c.noeud(('p', um(p.x), um(p.y), couche))))
    # Chaque piste est découpée aux points de son réseau posés sur son axe (jonctions en T), puis chaque tronçon vaut
    # ρ·L / (largeur · épaisseur).
    for t in segments:
        a, b = t.GetStart(), t.GetEnd()
        longueur = pcbnew.ToMM(t.GetLength())
        seg = pcbnew.SEG(a, b)
        jalons = {}
        for p, n in points_par_couche[t.GetLayer()]:
            if seg.Distance(p) <= 1000:
                u = pcbnew.ToMM((p - a).EuclideanNorm()) if longueur > 0 else 0.0
                jalons[n] = min(max(u, 0.0), longueur)
        ordre = sorted(jalons.items(), key=lambda e: e[1])
        for (n1, u1), (n2, u2) in zip(ordre, ordre[1:]):
            troncon = max(u2 - u1, 1e-4) * 1e-3
            c.relier(n1, n2, pcbnew.ToMM(t.GetWidth()) * 1e-3 * epaisseur(t.GetLayer()) / (RHO * troncon))
    for v in pistes:
        if v.GetClass() != 'PCB_VIA':
            continue
        p = v.GetPosition()
        g_total = np.pi * pcbnew.ToMM(v.GetDrillValue()) * 1e-3 * PLACAGE / (RHO * HAUTEUR)
        precedent = None
        for couche in COUCHES:
            n = c.noeud(('p', um(p.x), um(p.y), couche))
            if precedent is not None:
                c.relier(precedent, n, g_total * (len(COUCHES) - 1))
            precedent = n
    # Plans : maillage des surfaces remplies du réseau.
    for z in carte.Zones():
        if z.GetIsRuleArea() or z.GetNetname() != reseau:
            continue
        for couche in COUCHES:
            if not z.IsOnLayer(couche):
                continue
            polys = z.GetFilledPolysList(couche)
            if polys.OutlineCount() == 0:
                continue
            boite = polys.BBox()
            x0, y0 = pcbnew.ToMM(boite.GetLeft()), pcbnew.ToMM(boite.GetTop())
            nx, ny = int(pcbnew.ToMM(boite.GetWidth()) / PAS) + 1, int(pcbnew.ToMM(boite.GetHeight()) / PAS) + 1
            dedans = np.zeros((ny, nx), dtype=bool)
            for i in range(ny):
                for j in range(nx):
                    dedans[i, j] = polys.Contains(pcbnew.VECTOR2I(pcbnew.FromMM(x0 + j * PAS), pcbnew.FromMM(y0 + i * PAS)))
            g_carre = epaisseur(couche) / RHO
            maille = lambda i, j: c.noeud(('z', id(z), couche, i, j))
            for i in range(ny):
                for j in range(nx):
                    if not dedans[i, j]:
                        continue
                    if j + 1 < nx and dedans[i, j + 1]:
                        c.relier(maille(i, j), maille(i, j + 1), g_carre)
                    if i + 1 < ny and dedans[i + 1, j]:
                        c.relier(maille(i, j), maille(i + 1, j), g_carre)
            for p, n in points_par_couche[couche]:
                j, i = round((pcbnew.ToMM(p.x) - x0) / PAS), round((pcbnew.ToMM(p.y) - y0) / PAS)
                if 0 <= i < ny and 0 <= j < nx and dedans[i, j]:
                    c.relier(n, maille(i, j), FORT)
    # Plots : reliés au cuivre du réseau qui les touche.
    plots: dict[str, list[int]] = {}
    for plot in carte.GetPads():
        if plot.GetNetname() != reseau:
            continue
        nom = f'{plot.GetParentFootprint().GetReference()}.{plot.GetNumber()}'
        centre = c.noeud(('plot', nom, id(plot)))
        plots.setdefault(nom, []).append(centre)
        for couche in COUCHES:
            if not plot.IsOnLayer(couche):
                continue
            for p, n in points_par_couche[couche]:
                if plot.HitTest(p, 2000):
                    c.relier(centre, n, FORT)
    return c, plots


def resistance(carte: pcbnew.BOARD, reseau: str, sources: list[str], puits: list[str]) -> float:
    """Résistance (Ω) entre deux groupes de plots « REF.NUMERO » du réseau ; inf s'ils ne sont pas reliés."""
    c, plots = construire(carte, reseau)
    manquants = [p for p in sources + puits if p not in plots]
    if manquants:
        raise SystemExit(f'plot(s) inconnu(s) sur {reseau} : {", ".join(manquants)}')
    return c.resistance([n for p in sources for n in plots[p]], [n for p in puits for n in plots[p]])


if __name__ == '__main__':
    carte = pcbnew.LoadBoard(sys.argv[4] if len(sys.argv) > 4 else str(CARTE))
    pcbnew.ZONE_FILLER(carte).Fill(carte.Zones())
    r = resistance(carte, sys.argv[1], sys.argv[2].split(','), sys.argv[3].split(','))
    print(f'{sys.argv[1]} {sys.argv[2]} → {sys.argv[3]} : {r * 1000:.1f} mΩ, soit {r * 2000:.0f} mV sous 2 A')
