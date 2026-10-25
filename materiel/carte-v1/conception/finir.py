"""Finition du routage : amène la carte à zéro liaison manquante et zéro erreur DRC.

Chaque tour part du rapport DRC complet de kicad-cli (`kicad/drc.json`) :
1. nettoyage : les tronçons et vias orphelins (track_dangling, via_dangling) sont supprimés, ainsi que les pistes et
   vias non verrouillés en infraction (largeur, isolement, anneau, distance entre perçages) et tout via posé dans une
   zone interdite, même verrouillé : ils redeviennent des liaisons manquantes ; relier_plans.py rebranche ensuite aux
   plans les plots qui y ont perdu leur via ;
2. routage : recherche de chemin A* sur une grille de 0,05 mm à travers les couches de signaux (F.Cu, In2, In3, B.Cu)
   et la couche mixte In4 (à coût triplé, hors de la zone interdite du plan du modem), avec passages de couche par via.
   Les plots, les vias de plan, les lignes RF (verrouillés) et les zones interdites sont des obstacles absolus ; les
   pistes et vias posés par le routeur sont des obstacles coûteux. Chaque liaison est d'abord cherchée sans rien
   arracher, de la largeur la plus confortable à la plus fine ; à défaut seulement, elle traverse ce cuivre, qui est
   arraché et re-routé au tour suivant. Pour que deux réseaux ne s'arrachent pas indéfiniment la même place, chaque
   case disputée coûte un peu plus à chaque tour (négociation de congestion, à la manière de PathFinder) : l'un des
   deux finit par passer ailleurs. L'isolement exigé est le plus grand des deux classes en présence ; les trous non
   métallisés exigent 0,2 mm. Un plan coupé en îlots est recousu de via à via par une autre couche.
Le meilleur état atteint (liaisons manquantes plus erreurs) est gardé.

Quand deux réseaux se disputent un passage que la topologie leur impose de partager (ils se croisent forcément sur une
face), l'ordre de passage décide. `finir.py permuter` le change, par transaction : la liaison restante passe en
arrachant ce qui la gêne, puis ce qui a été arraché est re-routé sans plus rien arracher ; le résultat n'est gardé
que s'il fait baisser le compte, sinon la carte revient à l'état d'avant. Quand tout un quartier est saturé,
`finir.py fenetre [marge]` y retire tout le cuivre non verrouillé (tous réseaux), dans une fenêtre élargie de `marge`
mm autour de chaque liaison restante, et refait le quartier entier par les tours ordinaires ; même règle de
transaction.

À lancer avec l'interpréteur Python de KiCad :
    "C:\\Users\\rachi\\AppData\\Local\\Programs\\KiCad\\10.0\\bin\\python.exe" finir.py [tours]
    "C:\\Users\\rachi\\AppData\\Local\\Programs\\KiCad\\10.0\\bin\\python.exe" finir.py permuter
    "C:\\Users\\rachi\\AppData\\Local\\Programs\\KiCad\\10.0\\bin\\python.exe" finir.py fenetre [marge]
"""
from __future__ import annotations

import heapq
import json
import math
import re
import subprocess
import sys
from pathlib import Path

import numpy as np
import pcbnew

ICI = Path(__file__).resolve().parent
CARTE = ICI.parent / 'kicad' / 'fasoguardian-v1.kicad_pcb'
RAPPORT = ICI.parent / 'kicad' / 'drc.json'
KICAD_CLI = Path(sys.executable).parent / 'kicad-cli.exe'
PAS = 0.05                                   # résolution de la grille (mm)
MARGE = 0.02                                 # marge sur les isolements : écart possible entre la grille et le tracé (mm)
BORD = 0.55                                  # pistes et vias tenus à cette distance du bord
VIA_RAYON, VIA_PERCAGE = 0.15, 0.15          # via de 0,3 mm, perçage 0,15 mm (capacité du façonnier en 6 couches)
ANNEAU_MIN, PISTE_MIN = 0.075, 0.09          # règles de la carte (generer_carte.regles)
COUT_VIA = 2.5                               # coût d'un via, en mm de piste
HISTOIRE = 2.0                               # surcoût d'une case, en mm, à chaque tour où elle a été disputée
TROU = 0.2                                   # isolement d'un trou non métallisé (règle de la carte)
ENTRE_TROUS = 0.2                            # distance minimale entre deux perçages (règle de la carte)
ISOLEMENTS: dict[str, float] = {}            # isolement de la classe de chaque réseau, lu sur la carte
PUISSANCE = {'3V3', 'VBAT', 'VSYS', 'VIN', 'VBAT_MODEM', 'VBAT_MES', '1V8', '3V3_REG', 'TPS_LX1', 'TPS_LX2', 'GND'}
# Charge USB à 300 mA au plus, rails de 1,8 V : quelques dizaines de mA. (VBAT_MES porte le courant du modem.)
FAIBLE_COURANT = {'VIN', 'VIN_EXT', '1V8', 'SIM_VDD', '3V3_FLASH'}
SIGNAUX = {'F.Cu': pcbnew.F_Cu, 'In2.Signal': pcbnew.In2_Cu, 'In3.Signal': pcbnew.In3_Cu, 'In4.Alim': pcbnew.In4_Cu,
           'B.Cu': pcbnew.B_Cu}
COUCHES = list(SIGNAUX.values())
COUT_COUCHE = {pcbnew.In4_Cu: 3.0}           # In4 porte les plans d'alimentation : n'y passer qu'à défaut
TOUTES = [pcbnew.F_Cu, pcbnew.In1_Cu, pcbnew.In2_Cu, pcbnew.In3_Cu, pcbnew.In4_Cu, pcbnew.B_Cu]
VOISINS = [(1, 0, 1.0), (-1, 0, 1.0), (0, 1, 1.0), (0, -1, 1.0), (1, 1, 1.414), (1, -1, 1.414), (-1, 1, 1.414),
           (-1, -1, 1.414)]
# Défauts du DRC corrigés en arrachant le cuivre non verrouillé en cause.
ORPHELINS = {'track_dangling', 'via_dangling'}
INTERDITS = {'items_not_allowed'}           # cuivre dans une zone interdite : retiré même verrouillé
INFRACTIONS = {'clearance', 'track_width', 'annular_width', 'hole_to_hole', 'hole_clearance', 'shorting_items',
               'tracks_crossing', 'copper_edge_clearance', 'items_not_allowed', 'holes_co_located'}


def largeurs(reseau: str) -> tuple[float, ...]:
    """Largeurs essayées, de la plus confortable à la plus fine admise (0,09 mm au minimum sur la carte). Les rails à
    faible courant peuvent se rétrécir à 0,127 mm (environ 0,4 A) pour sortir d'entre les billes d'un boîtier au pas
    de 0,4 mm ; jamais le rail du modem ni ceux du régulateur."""
    if reseau in FAIBLE_COURANT:
        return (0.25, 0.15, 0.127)
    return (0.25, 0.15) if reseau in PUISSANCE else (0.127, 0.1)


def isolement(reseau: str) -> float:
    """Isolement de la classe du réseau, tel que la carte le définit (classes de generer_carte.regles)."""
    return ISOLEMENTS.get(reseau, 0.127) + MARGE


def apprendre_classes(carte: pcbnew.BOARD) -> None:
    reglages = carte.GetDesignSettings().m_NetSettings
    for nom in carte.GetNetsByName().keys():
        nom = str(nom)
        if nom:
            ISOLEMENTS[nom] = pcbnew.ToMM(reglages.GetEffectiveNetClass(nom).GetClearance())


def dans_polygone(X: np.ndarray, Y: np.ndarray, points) -> np.ndarray:
    """Cases de la grille à l'intérieur du polygone (règle pair-impair)."""
    dedans = np.zeros(X.shape, dtype=bool)
    for (x1, y1), (x2, y2) in zip(points, points[1:] + points[:1]):
        if y1 == y2:
            continue
        dedans ^= ((y1 > Y) != (y2 > Y)) & (X < (x2 - x1) * (Y - y1) / (y2 - y1) + x1)
    return dedans


def dilater(masque: np.ndarray, rayon: float) -> np.ndarray:
    """Élargit un masque de `rayon` mm (décalages de la grille, disque approché)."""
    n = int(math.ceil(rayon / PAS))
    res = masque.copy()
    for di in range(-n, n + 1):
        for dj in range(-n, n + 1):
            if di * di + dj * dj > n * n or (di == 0 and dj == 0):
                continue
            res[max(0, di):res.shape[0] + min(0, di), max(0, dj):res.shape[1] + min(0, dj)] |= \
                masque[max(0, -di):masque.shape[0] + min(0, -di), max(0, -dj):masque.shape[1] + min(0, -dj)]
    return res


class Element:
    """Cuivre d'un réseau : distance à chaque case d'une fenêtre ; `objet` est la piste ou le via arrachable."""

    def __init__(self, reseau, couches, fenetre, distance, objet=None, trou=False):
        self.reseau, self.couches, self.fenetre, self.distance, self.objet = reseau, set(couches), fenetre, distance, objet
        self.trou = trou  # via ou plot percé : un autre perçage, même du réseau, doit rester à ENTRE_TROUS


class Grille:
    def __init__(self, carte: pcbnew.BOARD):
        boite = carte.GetBoardEdgesBoundingBox()
        self.x0, self.y0 = pcbnew.ToMM(boite.GetLeft()), pcbnew.ToMM(boite.GetTop())
        self.nx = int(pcbnew.ToMM(boite.GetWidth()) / PAS) + 1
        self.ny = int(pcbnew.ToMM(boite.GetHeight()) / PAS) + 1
        self.X, self.Y = np.meshgrid(self.x0 + np.arange(self.nx) * PAS, self.y0 + np.arange(self.ny) * PAS)
        self.elements: list[Element] = []
        self.distance_bord = np.minimum.reduce([self.X - self.x0, self.x0 + (self.nx - 1) * PAS - self.X,
                                                self.Y - self.y0, self.y0 + (self.ny - 1) * PAS - self.Y])
        # Zones de règle interdites aux pistes, par couche, et aux vias (toutes couches).
        self.interdit = {c: np.zeros((self.ny, self.nx), dtype=bool) for c in TOUTES}
        self.interdit_via = np.zeros((self.ny, self.nx), dtype=bool)
        self.cache: dict = {}

    def regle(self, zone: pcbnew.ZONE) -> None:
        contour = zone.Outline().COutline(0)
        points = [(pcbnew.ToMM(contour.CPoint(k).x), pcbnew.ToMM(contour.CPoint(k).y)) for k in range(contour.PointCount())]
        dedans = dans_polygone(self.X, self.Y, points)
        if zone.GetDoNotAllowTracks():
            for c in TOUTES:
                if zone.IsOnLayer(c):
                    self.interdit[c] |= dedans
        if zone.GetDoNotAllowVias():
            self.interdit_via |= dedans

    def fenetre(self, x1, y1, x2, y2):
        return (max(0, int((y1 - self.y0) / PAS) - 1), min(self.ny, int((y2 - self.y0) / PAS) + 2),
                max(0, int((x1 - self.x0) / PAS) - 1), min(self.nx, int((x2 - self.x0) / PAS) + 2))

    def rectangle(self, reseau, couches, cx, cy, dx, dy, objet=None, rayon=0.0, trou=False):
        f = self.fenetre(cx - dx - 1, cy - dy - 1, cx + dx + 1, cy + dy + 1)
        X, Y = self.X[f[0]:f[1], f[2]:f[3]], self.Y[f[0]:f[1], f[2]:f[3]]
        d = np.hypot(np.maximum(np.abs(X - cx) - dx, 0), np.maximum(np.abs(Y - cy) - dy, 0)) - rayon
        self.elements.append(Element(reseau, couches, f, np.maximum(d, 0), objet, trou))

    def segment(self, reseau, couches, xa, ya, xb, yb, demi, objet=None):
        f = self.fenetre(min(xa, xb) - demi - 1, min(ya, yb) - demi - 1, max(xa, xb) + demi + 1, max(ya, yb) + demi + 1)
        X, Y = self.X[f[0]:f[1], f[2]:f[3]], self.Y[f[0]:f[1], f[2]:f[3]]
        vx, vy = xb - xa, yb - ya
        u = np.clip(((X - xa) * vx + (Y - ya) * vy) / (vx * vx + vy * vy or 1e-12), 0, 1)
        d = np.hypot(X - (xa + u * vx), Y - (ya + u * vy)) - demi
        self.elements.append(Element(reseau, couches, f, np.maximum(d, 0), objet))

    def masques(self, reseau: str, couche: int, demi: float, via=False) -> tuple[np.ndarray, np.ndarray]:
        """Cases interdites (cuivre fixe, zones de règle) et cases coûteuses (cuivre arrachable)."""
        dur = np.zeros((self.ny, self.nx), dtype=bool)
        mou = np.zeros((self.ny, self.nx), dtype=bool)
        propre = isolement(reseau)
        for e in self.elements:
            if e.reseau == reseau:
                # Même réseau : seul compte l'écart entre perçages, pour un via.
                if via and e.trou and couche == pcbnew.F_Cu:
                    i1, i2, j1, j2 = e.fenetre
                    dur[i1:i2, j1:j2] |= e.distance < ENTRE_TROUS + MARGE
                continue
            if couche not in e.couches:
                continue
            i1, i2, j1, j2 = e.fenetre
            touche = e.distance < max(propre, isolement(e.reseau)) + demi
            (mou if e.objet is not None else dur)[i1:i2, j1:j2] |= touche
        dur |= self.distance_bord < BORD + demi
        cle = (via, couche, round(demi, 3))
        if cle not in self.cache:
            self.cache[cle] = dilater(self.interdit_via if via else self.interdit[couche], demi)
        dur |= self.cache[cle]
        return dur, mou


def charger(carte: pcbnew.BOARD) -> Grille:
    apprendre_classes(carte)
    g = Grille(carte)
    mm = pcbnew.ToMM
    for zone in list(carte.Zones()) + [z for fp in carte.GetFootprints() for z in fp.Zones()]:
        if zone.GetIsRuleArea():
            g.regle(zone)
    for plot in carte.GetPads():
        couches = TOUTES if plot.GetDrillSize().x > 0 else [c for c in TOUTES if plot.IsOnLayer(c)]
        b = plot.GetBoundingBox()
        # Un trou non métallisé (sans réseau) exige TROU d'isolement : l'écart avec l'isolement ordinaire le grossit.
        grossi = TROU - isolement('') + MARGE if plot.GetDrillSize().x > 0 and not plot.GetNetname() else 0.0
        g.rectangle(plot.GetNetname(), couches, mm(b.GetCenter().x), mm(b.GetCenter().y), mm(b.GetWidth()) / 2,
                    mm(b.GetHeight()) / 2, rayon=grossi, trou=plot.GetDrillSize().x > 0)
    for piste in carte.GetTracks():
        objet = None if piste.IsLocked() else piste
        if piste.GetClass() == 'PCB_VIA':
            p = piste.GetPosition()
            g.rectangle(piste.GetNetname(), TOUTES, mm(p.x), mm(p.y), 0, 0, objet, mm(piste.GetWidth(pcbnew.F_Cu)) / 2,
                        trou=True)
        else:
            a, b = piste.GetStart(), piste.GetEnd()
            g.segment(piste.GetNetname(), [piste.GetLayer()], mm(a.x), mm(a.y), mm(b.x), mm(b.y), mm(piste.GetWidth()) / 2,
                      objet)
    return g


def chercher(g: Grille, reseau, depart, arrivee, couches_depart, couches_arrivee, w, penalite, histoire=None):
    """A* sur (couche, ligne, colonne) ; rend la liste des cases du chemin, ou None. Sans pénalité (None), le cuivre
    arrachable est infranchissable."""
    demi = w / 2
    libre, cher = {}, {}
    for c in COUCHES:
        dur, mou = g.masques(reseau, c, demi)
        if penalite is None:
            dur |= mou
        libre[c], cher[c] = ~dur, mou
    via_dur = np.zeros((g.ny, g.nx), dtype=bool)
    via_mou = np.zeros((g.ny, g.nx), dtype=bool)
    for c in TOUTES:
        dur, mou = g.masques(reseau, c, VIA_RAYON, via=True)
        via_dur |= dur | (mou if penalite is None else False)
        via_mou |= mou
    case = lambda x, y: (int(round((y - g.y0) / PAS)), int(round((x - g.x0) / PAS)))
    (si, sj), (ti, tj) = case(*depart), case(*arrivee)
    for (i, j), couches in (((si, sj), couches_depart), ((ti, tj), couches_arrivee)):
        for c in couches:
            if c in libre:
                libre[c][max(0, i - 2):i + 3, max(0, j - 2):j + 3] = True
    buts = {(c, ti, tj) for c in couches_arrivee if c in libre}
    debut = [(c, si, sj) for c in couches_depart if c in libre]
    if not debut or not buts:
        return None
    h = lambda i, j: math.hypot(i - ti, j - tj) * PAS
    ouverts = [(h(si, sj), 0.0, etat) for etat in debut]
    heapq.heapify(ouverts)
    couts = {etat: 0.0 for etat in debut}
    parents = {}
    explores = 0
    while ouverts:
        _, cout, (c, i, j) = heapq.heappop(ouverts)
        if (c, i, j) in buts:
            chemin = [(c, i, j)]
            while chemin[-1] in parents:
                chemin.append(parents[chemin[-1]])
            return chemin[::-1]
        if cout > couts.get((c, i, j), 1e18) + 1e-9:
            continue
        explores += 1
        if explores > 3_000_000:
            return None
        facteur = COUT_COUCHE.get(c, 1.0)
        suivants = []
        for di, dj, k in VOISINS:
            ni, nj = i + di, j + dj
            if 0 <= ni < g.ny and 0 <= nj < g.nx and libre[c][ni, nj]:
                surcout = histoire[c][ni, nj] if histoire is not None else 0.0
                suivants.append(((c, ni, nj), k * PAS * facteur + surcout +
                                 (penalite * PAS if penalite and cher[c][ni, nj] else 0.0)))
        if not via_dur[i, j]:
            for autre in COUCHES:
                if autre != c and libre[autre][i, j]:
                    suivants.append(((autre, i, j), COUT_VIA + (penalite if penalite and via_mou[i, j] else 0.0)))
        for etat, pas in suivants:
            nouveau = cout + pas
            if nouveau < couts.get(etat, 1e18):
                couts[etat] = nouveau
                parents[etat] = (c, i, j)
                heapq.heappush(ouverts, (nouveau + h(etat[1], etat[2]), nouveau, etat))
    return None


def arracher(carte: pcbnew.BOARD, g: Grille, reseau: str, chemin, w: float) -> int:
    """Retire le cuivre arrachable des autres réseaux que le chemin traverse."""
    demi = w / 2
    a_retirer = set()
    for e in g.elements:
        if e.objet is None or e.reseau == reseau:
            continue
        i1, i2, j1, j2 = e.fenetre
        seuil = max(isolement(reseau), isolement(e.reseau))
        for k, (c, i, j) in enumerate(chemin):
            if not (i1 <= i < i2 and j1 <= j < j2):
                continue
            via_ici = k + 1 < len(chemin) and chemin[k + 1][0] != c
            if (c in e.couches and e.distance[i - i1, j - j1] < seuil + demi) or \
                    (via_ici and e.distance[i - i1, j - j1] < seuil + VIA_RAYON):
                a_retirer.add(id(e))
                break
    retires = [e for e in g.elements if id(e) in a_retirer]
    for e in retires:
        carte.Delete(e.objet)
    g.elements = [e for e in g.elements if id(e) not in a_retirer]
    return len(retires)


def poser(carte: pcbnew.BOARD, g: Grille, reseau: str, chemin, depart, arrivee, w: float) -> None:
    net = carte.FindNet(reseau)
    point = lambda i, j: (g.x0 + j * PAS, g.y0 + i * PAS)
    sommets = [chemin[0]]
    for k in range(1, len(chemin) - 1):
        a, b, c = chemin[k - 1], chemin[k], chemin[k + 1]
        if b[0] != a[0] or c[0] != b[0] or (b[1] - a[1], b[2] - a[2]) != (c[1] - b[1], c[2] - b[2]):
            sommets.append(b)
    sommets.append(chemin[-1])
    coords = [point(i, j) for _, i, j in sommets]
    coords[0], coords[-1] = depart, arrivee
    for (ca, _, _), (cb, _, _), pa, pb in zip(sommets, sommets[1:], coords, coords[1:]):
        if ca != cb:
            via = pcbnew.PCB_VIA(carte)
            via.SetPosition(pcbnew.VECTOR2I(pcbnew.FromMM(pa[0]), pcbnew.FromMM(pa[1])))
            via.SetWidth(pcbnew.FromMM(2 * VIA_RAYON))
            via.SetDrill(pcbnew.FromMM(VIA_PERCAGE))
            via.SetNet(net)
            carte.Add(via)
            g.rectangle(reseau, TOUTES, pa[0], pa[1], 0, 0, via, VIA_RAYON, trou=True)
            continue
        if math.dist(pa, pb) < 1e-6:
            continue
        piste = pcbnew.PCB_TRACK(carte)
        piste.SetStart(pcbnew.VECTOR2I(pcbnew.FromMM(pa[0]), pcbnew.FromMM(pa[1])))
        piste.SetEnd(pcbnew.VECTOR2I(pcbnew.FromMM(pb[0]), pcbnew.FromMM(pb[1])))
        piste.SetWidth(pcbnew.FromMM(w))
        piste.SetLayer(ca)
        piste.SetNet(net)
        carte.Add(piste)
        g.segment(reseau, [ca], pa[0], pa[1], pb[0], pb[1], w / 2, piste)


def tracer(g: Grille, reseau, depart, arrivee, couches_depart, couches_arrivee, penalite, histoire):
    """Toutes les largeurs sans rien arracher d'abord, puis (si `penalite`) en traversant le cuivre arrachable ;
    rend (chemin, largeur)."""
    for mode in (None, penalite) if penalite else (None,):
        for w in largeurs(reseau):
            chemin = chercher(g, reseau, depart, arrivee, couches_depart, couches_arrivee, w, mode, histoire)
            if chemin is not None:
                return chemin, w
    return None, None


def recoudre(carte: pcbnew.BOARD, reseau: str):
    """Deux vias du réseau, chacun dans un îlot différent de son plan, au plus près l'un de l'autre ; ou None."""
    vias = [t for t in carte.GetTracks() if t.GetClass() == 'PCB_VIA' and t.GetNetname() == reseau]
    for zone in carte.Zones():
        if zone.GetIsRuleArea() or zone.GetNetname() != reseau:
            continue
        polys = zone.GetFilledPolysList(zone.GetFirstLayer())
        ilots = [[v for v in vias if polys.Outline(k).PointInside(v.GetPosition())] for k in range(polys.OutlineCount())]
        ilots = sorted([i for i in ilots if i], key=len, reverse=True)
        if len(ilots) < 2:
            continue
        # Le plus petit îlot est rattaché à un autre, par la paire de vias la plus proche.
        petit = ilots[-1]
        a, b = min(((a, b) for a in petit for i in ilots[:-1] for b in i),
                   key=lambda p: (p[0].GetPosition() - p[1].GetPosition()).EuclideanNorm())
        point = lambda v: (pcbnew.ToMM(v.GetPosition().x), pcbnew.ToMM(v.GetPosition().y))
        return point(a), point(b)
    return None


def extremites(carte: pcbnew.BOARD, liaison: dict):
    """(réseau, départ, arrivée, couches du départ, couches de l'arrivée) d'une liaison manquante du DRC, ou None."""
    a, b = liaison['items'][0], liaison['items'][1]
    reseau = re.search(r'\[([^\]]+)\]', a['description']).group(1)
    depart, arrivee = (a['pos']['x'], a['pos']['y']), (b['pos']['x'], b['pos']['y'])
    couches_a, couches_b = couches_de(a['description']), couches_de(b['description'])
    if a['description'].startswith('Zone') and b['description'].startswith('Zone'):
        paire = recoudre(carte, reseau)
        if paire is None:
            return None
        (depart, arrivee), couches_a, couches_b = paire, COUCHES, COUCHES
    return reseau, depart, arrivee, couches_a, couches_b


def couches_de(description: str) -> list[int]:
    if description.startswith('Via'):
        return COUCHES
    m = re.search(r' on ([A-Za-z0-9.]+)', description)
    nom = m.group(1) if m else 'F.Cu'
    return [SIGNAUX[nom]] if nom in SIGNAUX else COUCHES


def drc() -> dict:
    subprocess.run([str(KICAD_CLI), 'pcb', 'drc', '--format', 'json', '--severity-all', '-o', str(RAPPORT), str(CARTE)],
                   check=True, capture_output=True)
    return json.loads(RAPPORT.read_text(encoding='utf-8'))


def erreurs(rapport: dict) -> list:
    return [v for v in rapport.get('violations', []) if v['type'] in INFRACTIONS]


def conformer(carte: pcbnew.BOARD) -> int:
    """Supprime les pistes en double et de longueur nulle ; ramène largeurs et anneaux au minimum des règles."""
    n = 0
    vus = set()
    for t in list(carte.GetTracks()):
        if t.IsLocked() or t.GetClass() != 'PCB_TRACK':
            continue
        a, b = (t.GetStart().x, t.GetStart().y), (t.GetEnd().x, t.GetEnd().y)
        cle = (t.GetNetname(), t.GetLayer(), min(a, b), max(a, b))
        if a == b or cle in vus:
            carte.Delete(t)
            n += 1
        else:
            vus.add(cle)
    for t in carte.GetTracks():
        if t.IsLocked():
            continue
        if t.GetClass() == 'PCB_VIA':
            if pcbnew.ToMM(t.GetWidth(pcbnew.F_Cu) - t.GetDrillValue()) / 2 < ANNEAU_MIN - 1e-6:
                t.SetWidth(t.GetDrillValue() + pcbnew.FromMM(2 * ANNEAU_MIN))
                n += 1
        elif pcbnew.ToMM(t.GetWidth()) < PISTE_MIN - 1e-6:
            t.SetWidth(pcbnew.FromMM(PISTE_MIN))
            n += 1
    return n


def nettoyer() -> tuple[int, int]:
    """Supprime les orphelins (jusqu'à stabilité) et le cuivre non verrouillé en infraction ; rend les deux comptes."""
    orphelins = infractions = 0
    for passe in range(6):
        rapport = drc()
        cibles = {it['uuid'] for v in rapport.get('violations', []) if v['type'] in ORPHELINS for it in v['items']}
        forces = set()
        if passe == 0:
            cibles |= {it['uuid'] for v in erreurs(rapport) for it in v['items']}
            forces = {it['uuid'] for v in erreurs(rapport) if v['type'] in INTERDITS for it in v['items']}
        carte = pcbnew.LoadBoard(str(CARTE))
        retires = 0
        for uuid in cibles:
            objet = carte.ResolveItem(pcbnew.KIID(uuid))
            if objet is None or objet.GetClass() not in ('PCB_TRACK', 'PCB_VIA', 'PCB_ARC'):
                continue
            objet = objet.Cast()
            if objet.IsLocked() and uuid not in forces:
                continue
            carte.Delete(objet)
            retires += 1
        if passe == 0:
            infractions = retires
        else:
            orphelins += retires
        if not retires:
            break
        carte.BuildConnectivity()
        pcbnew.ZONE_FILLER(carte).Fill(carte.Zones())
        carte.Save(str(CARTE))
    return orphelins, infractions


def main(tours: int | None = None) -> None:
    tours = tours or (int(sys.argv[1]) if len(sys.argv) > 1 else 10)
    sauvegarde = CARTE.with_suffix('.meilleure.kicad_pcb')
    carte = pcbnew.LoadBoard(str(CARTE))
    if conformer(carte):
        carte.Save(str(CARTE))
    meilleur = None
    histoire = None
    for tour in range(1, tours + 1):
        carte = pcbnew.LoadBoard(str(CARTE))
        if conformer(carte):
            carte.BuildConnectivity()
            carte.Save(str(CARTE))
        rapport = drc()
        manquantes, defauts = rapport.get('unconnected_items', []), erreurs(rapport)
        score = len(manquantes) + len(defauts)
        print(f'tour {tour} : {len(manquantes)} liaison(s) manquante(s), {len(defauts)} erreur(s)', flush=True)
        if meilleur is None or score < meilleur:
            meilleur = score
            sauvegarde.write_bytes(CARTE.read_bytes())
        if not score:
            break
        orphelins, infractions = nettoyer()
        subprocess.run([sys.executable, str(ICI / 'relier_plans.py')], check=True, capture_output=True)
        manquantes = drc().get('unconnected_items', [])
        carte = pcbnew.LoadBoard(str(CARTE))
        g = charger(carte)
        if histoire is None:
            histoire = {c: np.zeros((g.ny, g.nx)) for c in COUCHES}
        # Traverser du cuivre arrachable coûte cher (40 mm de détour par case au premier tour, davantage ensuite) :
        # on n'arrache qu'en dernier recours.
        penalite = 800.0 * tour
        traces, arraches = 0, 0
        for liaison in manquantes:
            fins = extremites(carte, liaison)
            if fins is None:
                continue
            reseau, depart, arrivee, couches_a, couches_b = fins
            chemin, w = tracer(g, reseau, depart, arrivee, couches_a, couches_b, penalite, histoire)
            if chemin is None:
                continue
            retires = arracher(carte, g, reseau, chemin, w)
            if retires:
                # Ce chemin a dû chasser d'autres pistes : ses cases deviennent plus chères pour tout le monde.
                for c, i, j in chemin:
                    histoire[c][i, j] += HISTOIRE
            arraches += retires
            poser(carte, g, reseau, chemin, depart, arrivee, w)
            traces += 1
        carte.BuildConnectivity()
        pcbnew.ZONE_FILLER(carte).Fill(carte.Zones())
        carte.Save(str(CARTE))
        print(f'   {orphelins} orphelin(s) et {infractions} élément(s) en infraction retirés ; {traces} tracée(s), '
              f'{arraches} élément(s) arraché(s) à re-router', flush=True)
    rapport = drc()
    if len(rapport.get('unconnected_items', [])) + len(erreurs(rapport)) > meilleur:
        CARTE.write_bytes(sauvegarde.read_bytes())  # on garde le meilleur état atteint
        rapport = drc()
    print(f'reste : {len(rapport.get("unconnected_items", []))} liaison(s) manquante(s), {len(erreurs(rapport))} erreur(s)')


def score(rapport: dict) -> int:
    return len(rapport.get('unconnected_items', [])) + len(erreurs(rapport))


def router_sans_arracher(manquantes) -> int:
    """Trace les liaisons qui passent sans toucher au cuivre existant ; rend leur nombre."""
    carte = pcbnew.LoadBoard(str(CARTE))
    g = charger(carte)
    traces = 0
    for liaison in manquantes:
        fins = extremites(carte, liaison)
        if fins is None:
            continue
        reseau, depart, arrivee, couches_a, couches_b = fins
        chemin, w = tracer(g, reseau, depart, arrivee, couches_a, couches_b, None, None)
        if chemin is not None:
            poser(carte, g, reseau, chemin, depart, arrivee, w)
            traces += 1
    carte.BuildConnectivity()
    pcbnew.ZONE_FILLER(carte).Fill(carte.Zones())
    carte.Save(str(CARTE))
    return traces


def permuter() -> None:
    """Pour chaque liaison restante, essaie de passer en premier (voir l'en-tête) ; garde ce qui fait baisser le compte."""
    rapport = drc()
    actuel = score(rapport)
    print(f'avant permutation : {len(rapport.get("unconnected_items", []))} liaison(s), {len(erreurs(rapport))} erreur(s)')
    for liaison in list(rapport.get('unconnected_items', [])):
        # Plus fine d'abord : elle chasse le moins de cuivre possible.
        for largeur in sorted(largeurs(re.search(r'\[([^\]]+)\]', liaison['items'][0]['description']).group(1))):
            etat = CARTE.read_bytes()
            carte = pcbnew.LoadBoard(str(CARTE))
            g = charger(carte)
            fins = extremites(carte, liaison)
            if fins is None:
                break
            reseau, depart, arrivee, couches_a, couches_b = fins
            chemin = chercher(g, reseau, depart, arrivee, couches_a, couches_b, largeur, 40.0)
            if chemin is None:
                continue
            chasses = arracher(carte, g, reseau, chemin, largeur)
            poser(carte, g, reseau, chemin, depart, arrivee, largeur)
            carte.BuildConnectivity()
            pcbnew.ZONE_FILLER(carte).Fill(carte.Zones())
            carte.Save(str(CARTE))
            # Ce qui a été chassé repasse, sans plus rien arracher (trois essais, nettoyage compris).
            for _ in range(3):
                nettoyer()
                subprocess.run([sys.executable, str(ICI / 'relier_plans.py')], check=True, capture_output=True)
                manquantes = drc().get('unconnected_items', [])
                if not manquantes or not router_sans_arracher(manquantes):
                    break
            nouveau = drc()
            if score(nouveau) < actuel:
                actuel = score(nouveau)
                print(f'  {reseau} ({largeur} mm) passe en premier, {chasses} élément(s) déplacé(s) : '
                      f'{len(nouveau.get("unconnected_items", []))} liaison(s), {len(erreurs(nouveau))} erreur(s)', flush=True)
                break
            CARTE.write_bytes(etat)  # la permutation n'aide pas : retour à l'état d'avant
            print(f'  {reseau} ({largeur} mm) : sans gain, annulé', flush=True)
        if not actuel:
            break
    rapport = drc()
    print(f'reste : {len(rapport.get("unconnected_items", []))} liaison(s) manquante(s), {len(erreurs(rapport))} erreur(s)')


def fenetre(marge: float, tours: int = 8) -> None:
    """Refait les quartiers des liaisons restantes (voir l'en-tête)."""
    rapport = drc()
    avant = score(rapport)
    print(f'avant : {len(rapport.get("unconnected_items", []))} liaison(s), {len(erreurs(rapport))} erreur(s)', flush=True)
    if not avant:
        return
    etat = CARTE.read_bytes()
    carte = pcbnew.LoadBoard(str(CARTE))
    retires = 0
    for liaison in rapport.get('unconnected_items', []):
        xs = [it['pos']['x'] for it in liaison['items']]
        ys = [it['pos']['y'] for it in liaison['items']]
        coin = pcbnew.VECTOR2I(pcbnew.FromMM(min(xs) - marge), pcbnew.FromMM(min(ys) - marge))
        taille = pcbnew.VECTOR2I(pcbnew.FromMM(max(xs) - min(xs) + 2 * marge), pcbnew.FromMM(max(ys) - min(ys) + 2 * marge))
        boite = pcbnew.BOX2I(coin, taille)
        for piste in list(carte.GetTracks()):
            if not piste.IsLocked() and piste.HitTest(boite, False):
                carte.Delete(piste)
                retires += 1
    carte.BuildConnectivity()
    pcbnew.ZONE_FILLER(carte).Fill(carte.Zones())
    carte.Save(str(CARTE))
    print(f'{retires} élément(s) retirés dans les fenêtres', flush=True)
    main(tours)
    if score(drc()) >= avant:
        CARTE.write_bytes(etat)
        print("sans gain : carte remise dans son état d'avant")
    rapport = drc()
    print(f'reste : {len(rapport.get("unconnected_items", []))} liaison(s) manquante(s), {len(erreurs(rapport))} erreur(s)')


if __name__ == '__main__':
    if len(sys.argv) > 1 and sys.argv[1] == 'permuter':
        permuter()
    elif len(sys.argv) > 1 and sys.argv[1] == 'fenetre':
        fenetre(float(sys.argv[2]) if len(sys.argv) > 2 else 1.0)
    else:
        main()
