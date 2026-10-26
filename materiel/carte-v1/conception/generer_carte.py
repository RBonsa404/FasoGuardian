"""Étape 3 : génère le circuit imprimé (contour, empilement, règles, placement, plans) depuis la netlist de référence.

À lancer avec l'interpréteur Python de KiCad :
    "C:\\Users\\rachi\\AppData\\Local\\Programs\\KiCad\\10.0\\bin\\python.exe" generer_carte.py

Les coordonnées sont en millimètres, vue de dessus, origine au coin supérieur gauche de la carte. Les composants de
la face inférieure sont placés par leur position vue de dessus, puis retournés.
"""
from __future__ import annotations

import json
import re
import subprocess
import sys
from pathlib import Path

import pcbnew

sys.path.insert(0, str(Path(__file__).resolve().parent))
import bibliotheques as bib  # noqa: E402
from carte import COMPOSANTS  # noqa: E402
from generer_schema import FEUILLE_UUID, NOM, RACINE, symbole_uuid  # noqa: E402

LARGEUR, HAUTEUR, RAYON = 41.0, 36.0, 1.0
# Lignes d'antenne : 50 Ω en guide coplanaire (fente de 0,15 mm) sur le préimprégné 3313 de 0,092 mm de l'empilement
# JLC061211-3313, à cuivre interne de 1 oz (outils/impedance.py).
PISTE_RF = 0.186
X0, Y0 = 100.0, 100.0
ECART = 0.5  # écart entre pièces rangées : la place d'un via ou de deux pistes pour le routage
BORD = 0.6  # distance des pièces rangées au bord de la carte (mm)
TOUT = [(0.2, 0.2), (LARGEUR - 0.2, 0.2), (LARGEUR - 0.2, HAUTEUR - 0.2), (0.2, HAUTEUR - 0.2)]
SORTIE = bib.PROJET / 'kicad' / f'{NOM}.kicad_pcb'


def mm(v: float) -> int:
    return pcbnew.FromMM(v)


def point(x: float, y: float) -> pcbnew.VECTOR2I:
    return pcbnew.VECTOR2I(mm(X0 + x), mm(Y0 + y))


# Étiquettes imprimées à côté des pastilles des liaisons hors carte (câblage dans le boîtier).
# Étiquettes des pastilles de câblage (BZ : buzzer, M : moteur du vibreur, LP : boucle du bracelet, EL : électrode).
ETIQUETTES = {'TP1': '+5V', 'TP2': 'GND', 'TP8': 'BAT+', 'TP9': 'BAT-', 'TP17': 'NTC', 'TP30': 'BZ+', 'TP31': 'BZ-',
              'TP32': 'M+', 'TP33': 'M-', 'TP34': 'LP', 'TP35': 'LP-', 'TP36': 'EL'}

# Placement imposé : (x, y, rotation, face). Le reste est rangé par zones (plus bas).
FIXES = {
    'U2': (27.3, 18.0, 0, 'F'),       # module : plot RF (60) vers le bord supérieur, GNSS (90) vers le bas
    'J5': (6.6, 7.0, 0, 'F'),         # porte-nano-SIM
    'U1': (7.0, 22.4, 0, 'F'),        # microcontrôleur
    'J7': (7.0, 15.4, 180, 'B'),        # Tag-Connect : ses trous tombent entre le porte-SIM et le microcontrôleur
    'J4': (8.6, 33.6, 180, 'F'),      # U.FL GNSS, plot signal vers le haut (loin du bord)
    'J3': (16.6, 2.4, 0, 'F'),        # U.FL LTE
    'J6': (20.6, 2.4, 180, 'F'),      # commutateur de mesure conduite, entrée côté module
    'C10': (31.0, 2.4, 0, 'F'),       # réservoir au-dessus des plots VBAT (55 à 57)
    'C11': (37.6, 2.4, 0, 'F'),
    'SW1': (36.5, 34.0, 0, 'F'),      # bouton SOS latéral : poussoir au bord inférieur, sous la membrane du boîtier
    'D10': (25.0, 33.5, 0, 'F'),      # LED RGB (guide de lumière du boîtier)
    # Chemin des pointes de courant du modem : R8.2 en face des entrées VIN de U6 (broches 1 et 2), R8.1 au-dessus
    # de la languette du plan VBAT (pré-routage de chemin_du_modem, ADR 0042 point 10).
    'R8': (21.625, 6.575, 0, 'B'),
    'U6': (25.882, 6.49, 0, 'B'),
    # Pastilles des fils de la batterie, de la charge et du buzzer, par paires le long du bord inférieur, et protection
    # de l'entrée de charge à côté (les pastilles du vibreur, de la boucle et de l'électrode sont rangées sous le module).
    'TP8': (2.395, 31.395, 0, 'B'), 'TP9': (6.495, 31.395, 0, 'B'), 'TP17': (10.095, 30.895, 0, 'B'),
    'TP1': (16.395, 32.795, 0, 'B'), 'TP2': (20.495, 32.795, 0, 'B'),
    'D15': (25.195, 32.195, 0, 'B'), 'D1': (30.345, 32.195, 0, 'B'),
    'TP30': (34.395, 32.295, 0, 'B'), 'TP31': (37.495, 32.295, 0, 'B'),
    # Pastilles du vibreur, l'une au-dessus de l'autre au bord droit.
    'TP32': (39.0, 20.4, 0, 'B'), 'TP33': (39.0, 23.0, 0, 'B'),
}
# Empreintes posées par leur origine plutôt que par le centre de leur encombrement.
PAR_ORIGINE = ('U2', 'U1', 'J7', 'SW1', 'R8', 'U6')

# Éléments posés au pied de la broche qu'ils servent, avant le rangement par zones : condensateur → (circuit, broche).
# Dans l'ordre de priorité (le plus exigeant d'abord pour chaque circuit). Sur la face de leur zone si la place y est,
# sinon sur l'autre face, sous la broche.
AU_PIED = {
    # Microcontrôleur (face supérieure) : VCAP, puis une capacité par broche VDD, VDDA, VBAT, NRST, quartz.
    'C47': ('U1', '22'), 'C40': ('U1', '24'), 'C41': ('U1', '36'), 'C42': ('U1', '48'), 'C44': ('U1', '9'),
    'C45': ('U1', '9'), 'C46': ('U1', '1'), 'C43': ('U1', '48'), 'C48': ('U1', '7'), 'C64': ('U1', '25'),
    'C65': ('U1', '10'), 'Y1': ('U1', '3'), 'C49': ('Y1', '1'), 'C50': ('Y1', '2'),
    # Module : 10 pF et 33 pF au ras des plots VBAT (SIMCom § 3.1.1), puis 100 nF, 1 µF, TVS ; 1,8 V.
    'C15': ('U2', '56'), 'C14': ('U2', '56'), 'C13': ('U2', '55'), 'C12': ('U2', '57'), 'D2': ('U2', '55'),
    'C17': ('U2', '15'), 'C16': ('U2', '97'),
    # Interrupteur du modem, chargeur, jauge.
    'C7': ('U6', '1'), 'C6': ('U6', '6'),
    'C2': ('U4', 'B2'), 'C3': ('U4', 'C2'), 'C1': ('U4', 'A2'),
    'C8': ('U7', '3'),
    # Mémoire, capteurs, élément sécurisé, translateurs.
    'C63': ('U9', '8'), 'C60': ('U3', '9'), 'C61': ('U3', '10'), 'C62': ('U8', '8'),
    'C70': ('U10', '16'), 'C71': ('U10', '15'), 'C72': ('U11', '16'), 'C73': ('U11', '15'),
}
PAIRES = (('TP35', 'TP34'),)  # second fil → premier fil (boucle du bracelet)
ECART_PIED = 0.15   # écart entre un élément posé au pied et ses voisins (encombrements déjà élargis de 0,2 mm)
RAYON_PIED = 3.0    # distance maximale du plot de l'élément à la broche servie (mm)
HALO = 1.2          # dégagement des circuits intégrés rangés, pour la place de leurs condensateurs (mm)
def candidats(rayon: float, pas: float = 0.05) -> list[tuple[float, float, float]]:
    n = int(rayon / pas)
    return sorted(((i * pas) ** 2 + (j * pas) ** 2, i * pas, j * pas)
                  for i in range(-n, n + 1) for j in range(-n, n + 1) if (i * pas) ** 2 + (j * pas) ** 2 <= rayon ** 2)


CANDIDATS = candidats(RAYON_PIED)
CANDIDATS_PAIRES = candidats(6.0, 0.1)  # second fil d'une paire : jusqu'à 6 mm du premier

# Zones de rangement : (face, x1, y1, x2, y2, références dans l'ordre de rangement).
ZONES = [
    ('B', 27.6, 0.5, 35.0, 4.9, ['C12', 'C13', 'C14', 'C15', 'D2']),               # découplage VBAT du modem
    ('B', 0.6, 0.6, 13.6, 13.0, ['U9', 'Q2', 'C63', 'R64', 'R65', 'R60', 'R61',
                                  'U12', 'R30', 'R31', 'R32', 'C30', 'C31', 'C32', 'C33']),
    ('B', 0.6, 17.6, 13.6, 29.4, ['TP36', 'Y1', 'C49', 'C50', 'C40', 'C41', 'C42', 'C43', 'C44', 'C45', 'C46', 'C47', 'C48', 'R40',
                                   'TP20', 'R62', 'R63', 'TP40', 'TP41', 'TP42', 'TP43', 'TP44', 'TP45']),
    ('B', 0.6, 29.6, 13.6, 35.5, ['TP8', 'TP9', 'TP17', 'R23', 'C25']),
    ('B', 14.6, 5.4, 27.4, 18.0, ['F1', 'C1', 'R8', 'U4', 'C2', 'C3', 'R2', 'R4', 'R5', 'U7', 'C8', 'U6', 'C6', 'R6', 'C7']),
    ('B', 27.6, 5.4, 40.4, 18.0, ['Q1', 'R10', 'R11', 'R12', 'R13', 'R14', 'R15', 'R16', 'R17', 'C16', 'C17', 'R7', 'RT1', 'TP7', 'TP3', 'TP4', 'TP5', 'TP6', 'TP10', 'TP11',
                                   'TP12', 'TP13', 'TP14', 'TP15', 'TP16']),
    ('B', 14.6, 18.2, 27.4, 31.0, ['U10', 'C70', 'C71', 'U11', 'C72', 'C73', 'U3', 'C60', 'C61', 'U8', 'C62', 'TP34']),
    ('B', 27.6, 18.2, 40.4, 31.0, ['Q3', 'Q4', 'R80', 'R81', 'R82', 'R83', 'R84', 'R85', 'R86', 'R87', 'Q5', 'R88', 'R89',
                                   'D11', 'D12', 'R91', 'R92', 'C65', 'D13', 'R93', 'C64', 'D14']),
    ('B', 14.6, 31.0, 40.4, 35.6, ['TP1', 'TP2', 'D1', 'D15', 'TP30', 'TP31']),
]


def charger(c) -> pcbnew.FOOTPRINT:
    bibliotheque, nom = c.empreinte.split(':', 1)
    empreinte = pcbnew.FootprintLoad(str(bib.dossier_empreintes(bibliotheque)), nom)
    if empreinte is None:
        raise RuntimeError(f'empreinte introuvable : {c.empreinte}')
    empreinte.SetReference(c.ref)
    empreinte.SetValue(c.valeur)
    empreinte.SetFPIDAsString(c.empreinte)
    chemin = pcbnew.KIID_PATH(f'/{FEUILLE_UUID[c.feuille]}/{symbole_uuid(c.ref)}')
    empreinte.SetPath(chemin)
    empreinte.SetSheetfile(f'{c.feuille}.kicad_sch')
    # Mêmes champs que le symbole (contrôle de parité schéma ↔ carte).
    for champ, valeur in (('LCSC', c.lcsc), ('Fabricant', c.fabricant), ('Note', c.note)):
        empreinte.SetField(champ, valeur)
        propriete = empreinte.GetField(champ)
        propriete.SetLayer(pcbnew.F_Fab)
        propriete.SetVisible(False)
    # Sérigraphie : une carte aussi dense ne porte que les repères utiles au câblage et au montage (connecteurs,
    # bouton, pastilles des liaisons hors carte) ; tous les repères et valeurs figurent sur le plan de montage (Fab).
    valeur = empreinte.Value()
    valeur.SetLayer(pcbnew.F_Fab)
    valeur.SetVisible(False)
    repere = empreinte.Reference()
    repere.SetTextSize(pcbnew.VECTOR2I(mm(0.8), mm(0.8)))
    repere.SetTextThickness(mm(0.12))
    repere.SetLayer(pcbnew.F_Fab)
    for element in list(empreinte.GraphicalItems()):
        if element.GetLayer() in (pcbnew.F_SilkS, pcbnew.B_SilkS):
            empreinte.Remove(element)
    attributs = empreinte.GetAttributes()
    if c.non_monte:
        attributs |= pcbnew.FP_DNP
    empreinte.SetAttributes(attributs)
    return empreinte


def reseaux_du_schema() -> dict[tuple[str, str], str]:
    """Réseau de chaque broche d'après la netlist exportée par KiCad depuis le schéma : la carte reprend exactement
    les noms du schéma, y compris les « unconnected-… » des broches libres (contrôle de parité)."""
    netlist = bib.PROJET / 'kicad' / 'routage' / 'schema.net'
    netlist.parent.mkdir(exist_ok=True)
    subprocess.run([str(bib.KICAD / 'bin' / 'kicad-cli.exe'), 'sch', 'export', 'netlist', '-o', str(netlist),
                    str(bib.PROJET / 'kicad' / f'{NOM}.kicad_sch')], check=True, capture_output=True)
    texte = netlist.read_text(encoding='utf-8')
    correspondance = {}
    for m in re.finditer(r'\(net\s+\(code "?\d+"?\)\s+\(name "((?:[^"\\]|\\.)*)"\)', texte):
        corps = bib.bloc(texte, m.start())
        nom = m.group(1).replace('\\"', '"')
        for ref, broche in re.findall(r'\(node\s+\(ref "([^"]+)"\)\s+\(pin "([^"]+)"\)', corps):
            correspondance[(ref, broche)] = nom
    return correspondance


def enveloppe(empreinte: pcbnew.FOOTPRINT) -> tuple[float, float, float, float]:
    """Encombrement (mm, repère de la carte) : union de la zone de courtoisie et des plots élargis de 0,2 mm. Les
    zones de courtoisie des empreintes LCSC ne couvrent que le corps du composant, pas ses plots."""
    couche = pcbnew.F_CrtYd if empreinte.GetLayer() == pcbnew.F_Cu else pcbnew.B_CrtYd
    boite = None
    for plot in empreinte.Pads():
        b = plot.GetBoundingBox()
        b.Inflate(mm(0.2))
        if boite is None:
            boite = b
        else:
            boite.Merge(b)
    cour = empreinte.GetCourtyard(couche).BBox()
    if cour.GetWidth() > 0:
        boite.Merge(cour)
    return (pcbnew.ToMM(boite.GetLeft()) - X0, pcbnew.ToMM(boite.GetTop()) - Y0,
            pcbnew.ToMM(boite.GetRight()) - X0, pcbnew.ToMM(boite.GetBottom()) - Y0)


def poser(empreinte: pcbnew.FOOTPRINT, x: float, y: float, rotation: float, face: str) -> None:
    """Pose l'empreinte en (x, y) sur la face voulue : elle est d'abord mise sur sa face (retournée au besoin), puis
    orientée de `rotation` (angle de KiCad, vu de dessus). Le résultat ne dépend pas de la pose précédente."""
    voulue = pcbnew.B_Cu if face == 'B' else pcbnew.F_Cu
    if empreinte.GetLayer() != voulue:
        empreinte.Flip(empreinte.GetPosition(), pcbnew.FLIP_DIRECTION_LEFT_RIGHT)
    empreinte.SetOrientationDegrees(rotation)
    empreinte.SetPosition(point(x, y))


def centrer(empreinte: pcbnew.FOOTPRINT, x: float, y: float) -> None:
    """Déplace l'empreinte pour que le centre de son encombrement tombe en (x, y)."""
    x1, y1, x2, y2 = enveloppe(empreinte)
    empreinte.Move(point(x - (x1 + x2) / 2, y - (y1 + y2) / 2) - point(0, 0))


class Obstacles:
    """Encombrements déjà occupés, par face ; un trou traversant occupe les deux faces."""

    def __init__(self) -> None:
        self.faces: dict[str, list[tuple[float, float, float, float]]] = {'F': [], 'B': []}

    def ajouter(self, empreinte: pcbnew.FOOTPRINT, face: str) -> None:
        self.faces[face].append(enveloppe(empreinte))
        for plot in empreinte.Pads():
            if plot.GetDrillSize().x > 0:
                b = plot.GetBoundingBox()
                trou = (pcbnew.ToMM(b.GetLeft()) - X0 - 0.4, pcbnew.ToMM(b.GetTop()) - Y0 - 0.4,
                        pcbnew.ToMM(b.GetRight()) - X0 + 0.4, pcbnew.ToMM(b.GetBottom()) - Y0 + 0.4)
                self.faces['F' if face == 'B' else 'B'].append(trou)

    def reserver(self, face: str, boite: tuple[float, float, float, float]) -> None:
        """Réserve une surface sans empreinte (couloir d'une piste pré-routée)."""
        self.faces[face].append(boite)

    def libre(self, face: str, boite: tuple[float, float, float, float], ecart: float) -> bool:
        x1, y1, x2, y2 = boite
        return all(x2 + ecart <= a1 or x1 - ecart >= a2 or y2 + ecart <= b1 or y1 - ecart >= b2
                   for a1, b1, a2, b2 in self.faces[face])


def ranger(empreintes: list[pcbnew.FOOTPRINT], face: str, x1: float, y1: float, x2: float, y2: float,
           obstacles: Obstacles, ecart: float = ECART) -> list[str]:
    """Rangement « en bas à gauche d'abord » : chaque pièce, de la plus grande à la plus petite, prend la première
    place libre de la zone, dans l'orientation qui tient, sans toucher les pièces ni les trous déjà posés."""
    pas = 0.1
    x1, y1 = max(x1, BORD), max(y1, BORD)
    x2, y2 = min(x2, LARGEUR - BORD), min(y2, HAUTEUR - BORD)
    hors = []

    def taille(e):
        a, b, c, d = enveloppe(e)
        return (c - a) * (d - b)

    for empreinte in empreintes:
        poser(empreinte, 0, 0, 0, face)
    for empreinte in sorted(empreintes, key=lambda e: -taille(e)):
        place = None
        for rotation in (0, 90):
            poser(empreinte, 0, 0, rotation, face)
            a, b, c, d = enveloppe(empreinte)
            l, h = c - a, d - b
            y = y1
            while place is None and y + h <= y2 + 1e-6:
                x = x1
                while x + l <= x2 + 1e-6:
                    if obstacles.libre(face, (x, y, x + l, y + h), ecart):
                        place = (rotation, x + l / 2, y + h / 2)
                        break
                    x += pas
                y += pas
            if place:
                break
        if place is None:
            hors.append(empreinte.GetReference())
            continue
        rotation, cx, cy = place
        poser(empreinte, 0, 0, rotation, face)
        centrer(empreinte, cx, cy)
        obstacles.ajouter(empreinte, face)
    return hors


def plot(empreinte: pcbnew.FOOTPRINT, numero: str) -> pcbnew.PAD:
    return next(p for p in empreinte.Pads() if p.GetNumber() == numero)


def au_pied(empreinte: pcbnew.FOOTPRINT, broche: pcbnew.PAD, face_preferee: str, obstacles: Obstacles,
            numero: str | None = None, positions: list | None = None) -> bool:
    """Pose l'élément pour que son plot du réseau de `broche` (ou son plot `numero`) en soit le plus près possible
    (RAYON_PIED au plus) : sur la face préférée, ou sur l'autre face s'il y gagne plus de 0,3 mm. Rend False s'il n'y
    a pas de place."""
    reseau = broche.GetNetname()
    tx, ty = xy(broche.GetPosition())
    meilleur = None
    for face in (face_preferee, 'B' if face_preferee == 'F' else 'F'):
        malus = 0.0 if face == face_preferee else 0.3
        # Seuls les obstacles du voisinage comptent (la recherche reste dans RAYON_PIED de la broche).
        positions = positions or CANDIDATS
        portee = positions[-1][0] ** 0.5 + 4.0
        proches = [b for b in obstacles.faces[face]
                   if b[0] < tx + portee and b[2] > tx - portee and b[1] < ty + portee and b[3] > ty - portee]
        voisinage = Obstacles()
        voisinage.faces[face] = proches
        for rotation in (0, 90, 180, 270):
            poser(empreinte, 0, 0, rotation, face)
            pied = next((p for p in empreinte.Pads() if (p.GetNumber() == numero if numero else p.GetNetname() == reseau)), None)
            if pied is None:
                return False
            px, py = xy(pied.GetPosition())
            ex1, ey1, ex2, ey2 = enveloppe(empreinte)
            for d2, dx, dy in positions:
                score = d2 ** 0.5 + malus
                if meilleur is not None and score >= meilleur[0]:
                    break
                cx, cy = tx + dx - px, ty + dy - py
                boite = (ex1 + cx, ey1 + cy, ex2 + cx, ey2 + cy)
                if boite[0] < BORD or boite[1] < BORD or boite[2] > LARGEUR - BORD or boite[3] > HAUTEUR - BORD:
                    continue
                if voisinage.libre(face, boite, ECART_PIED):
                    meilleur = (score, face, rotation, cx, cy)
                    break
    if meilleur is None:
        return False
    _, face, rotation, cx, cy = meilleur
    poser(empreinte, cx, cy, rotation, face)
    obstacles.ajouter(empreinte, face)
    return True


def poser_au_pied(empreintes, refs, places: set, obstacles: Obstacles, faces: dict[str, str]) -> None:
    """Pose au pied de leur broche les éléments de `refs` dont le circuit est déjà placé (jusqu'à stabilité : un
    condensateur de charge peut servir le quartz qui vient d'être posé)."""
    progres = True
    while progres:
        progres = False
        for ref in refs:
            if ref in places or ref not in AU_PIED or AU_PIED[ref][0] not in places:
                continue
            circuit, numero = AU_PIED[ref]
            if au_pied(empreintes[ref], plot(empreintes[circuit], numero), faces[ref], obstacles):
                places.add(ref)
                progres = True


def xy(position: pcbnew.VECTOR2I) -> tuple[float, float]:
    return pcbnew.ToMM(position.x) - X0, pcbnew.ToMM(position.y) - Y0


def en_serie(empreinte: pcbnew.FOOTPRINT, x: float, y: float, cote_module: str, module_a_droite: bool) -> None:
    """Élément série d'une ligne horizontale : son plot `cote_module` est du côté du module."""
    for rotation in (0, 180):
        poser(empreinte, x, y, rotation, 'F')
        autre = '2' if cote_module == '1' else '1'
        if (xy(plot(empreinte, cote_module).GetPosition())[0] > xy(plot(empreinte, autre).GetPosition())[0]) == module_a_droite:
            return


def en_parallele(empreinte: pcbnew.FOOTPRINT, x: float, ligne: float, sens: int) -> None:
    """Élément parallèle : plot 1 posé sur la ligne, plot 2 (masse ou polarisation) écarté du côté `sens`."""
    for rotation in (90, 270):
        poser(empreinte, x, ligne, rotation, 'F')
        dx, dy = xy(plot(empreinte, '1').GetPosition())
        empreinte.Move(point(x - dx, ligne - dy) - point(0, 0))
        if (xy(plot(empreinte, '2').GetPosition())[1] - ligne) * sens > 0:
            return


def piste(carte: pcbnew.BOARD, reseau: pcbnew.NETINFO_ITEM, points: list[tuple[float, float]], largeur: float) -> float:
    """Pistes verrouillées sur la face supérieure, rend la longueur (mm)."""
    longueur = 0.0
    for (xa, ya), (xb, yb) in zip(points, points[1:]):
        if abs(xa - xb) < 1e-6 and abs(ya - yb) < 1e-6:
            continue
        segment = pcbnew.PCB_TRACK(carte)
        segment.SetStart(point(xa, ya))
        segment.SetEnd(point(xb, yb))
        segment.SetWidth(mm(largeur))
        segment.SetLayer(pcbnew.F_Cu)
        segment.SetNet(reseau)
        segment.SetLocked(True)
        carte.Add(segment)
        longueur += ((xa - xb) ** 2 + (ya - yb) ** 2) ** 0.5
    return longueur


def coudes(a: tuple[float, float], b: tuple[float, float], vertical_d_abord: bool = False) -> list[tuple[float, float]]:
    return [a, (a[0], b[1]) if vertical_d_abord else (b[0], a[1]), b]


def lignes_rf(carte, empreintes, reseaux, obstacles: 'Obstacles') -> dict[str, float]:
    """Lignes d'antenne de 50 Ω tracées droites le long du bord (SIMCom § 4.7 : 20 mm au plus, sans via), réseau
    d'adaptation aligné sur la ligne, éléments parallèles posés à cheval sur elle."""
    largeur = PISTE_RF
    e = empreintes
    # LTE : plot 60 du module, π (C20, R20, C21), ESD (D4), commutateur de mesure J6, U.FL J3.
    j6_entree = xy(plot(e['J6'], '1').GetPosition())
    ligne = j6_entree[1]
    # Écarts : 1,41 mm entre un élément série et un parallèle, 0,96 mm entre deux parallèles (zones de courtoisie).
    en_parallele(e['C20'], 26.5, ligne, +1)
    en_serie(e['R20'], 25.09, ligne, '1', True)
    en_parallele(e['C21'], 23.68, ligne, +1)
    en_parallele(e['D4'], 22.72, ligne, +1)
    p60 = xy(plot(e['U2'], '60').GetPosition())
    c20 = xy(plot(e['C20'], '1').GetPosition())
    r20a, r20b = xy(plot(e['R20'], '1').GetPosition()), xy(plot(e['R20'], '2').GetPosition())
    longueurs = {'LTE': 0.0}
    longueurs['LTE'] += piste(carte, reseaux['RF_LTE_MOD'], coudes(p60, c20, True) + [r20a], largeur)
    longueurs['LTE'] += piste(carte, reseaux['RF_LTE_1'], [r20b, xy(plot(e['C21'], '1').GetPosition()),
                                                            xy(plot(e['D4'], '1').GetPosition()), j6_entree], largeur)
    longueurs['LTE'] += piste(carte, reseaux['RF_LTE_2'], coudes(xy(plot(e['J6'], '2').GetPosition()),
                                                                 xy(plot(e['J3'], '1').GetPosition()), True), largeur)
    # GNSS : plot 90 du module, C22 (parallèle), C24 (série, arrête le continu), C23, ESD D5, choc L2, U.FL J4.
    j4 = xy(plot(e['J4'], '1').GetPosition())
    ligne = j4[1]
    p90 = xy(plot(e['U2'], '90').GetPosition())
    en_parallele(e['C22'], p90[0] - 0.7, ligne, +1)
    en_serie(e['C24'], p90[0] - 2.11, ligne, '1', True)
    en_parallele(e['C23'], p90[0] - 3.52, ligne, +1)
    en_parallele(e['D5'], p90[0] - 4.48, ligne, +1)
    en_parallele(e['L2'], p90[0] - 5.44, ligne, +1)
    c22 = xy(plot(e['C22'], '1').GetPosition())
    c24a, c24b = xy(plot(e['C24'], '1').GetPosition()), xy(plot(e['C24'], '2').GetPosition())
    longueurs['GNSS'] = piste(carte, reseaux['RF_GNSS_MOD'], coudes(p90, c22, True) + [c24a], largeur)
    longueurs['GNSS'] += piste(carte, reseaux['RF_GNSS_1'], [c24b, xy(plot(e['C23'], '1').GetPosition()),
                                                             xy(plot(e['D5'], '1').GetPosition()),
                                                             xy(plot(e['L2'], '1').GetPosition()), j4], largeur)
    for ref in ('C20', 'R20', 'C21', 'D4', 'C22', 'C24', 'C23', 'D5', 'L2'):
        obstacles.ajouter(e[ref], 'F')
    return longueurs


def bloc_regulateur(empreintes, obstacles: 'Obstacles', x: float, y: float) -> None:
    """Régulateur 3,3 V posé en bloc (face inférieure) : inductance face aux broches LX1 (9) et LX2 (7), condensateurs
    au pied de VIN (10) et de VOUT (6). Le nœud de commutation reste de quelques millimètres (fiche TI, § 10)."""
    u5, l1, c4, c5 = (empreintes[r] for r in ('U5', 'L1', 'C4', 'C5'))
    poser(u5, x, y, 180, 'B')
    lx1, lx2 = xy(plot(u5, '9').GetPosition()), xy(plot(u5, '7').GetPosition())
    cote = 1 if lx1[0] > x else -1
    for rotation in (90, 270):
        poser(l1, x + cote * 2.95, y, rotation, 'B')
        if (xy(plot(l1, '1').GetPosition())[1] - y) * (lx1[1] - y) > 0:
            break
    vin, vout = xy(plot(u5, '10').GetPosition()), xy(plot(u5, '6').GetPosition())
    poser(c4, x - 0.2 * cote, y + (2.2 if vin[1] > y else -2.2), 180, 'B')
    poser(c5, x - 0.2 * cote, y + (2.2 if vout[1] > y else -2.2), 180, 'B')
    for e in (u5, l1, c4, c5):
        obstacles.ajouter(e, 'B')


def coutures(carte: pcbnew.BOARD, masse: pcbnew.NETINFO_ITEM) -> int:
    """Vias de masse du guide coplanaire (0,3/0,15 mm, verrouillés) : un dans chaque plot de masse des éléments
    d'adaptation, des connecteurs U.FL et du commutateur de mesure, puis tous les 0,8 mm de part et d'autre de chaque
    ligne, à 0,5 mm de son axe, là où la place est libre sur toutes les couches. Rend le nombre de vias posés."""
    from relier_plans import CUIVRE, ISOLEMENT, libre, obstacles
    elements = obstacles(carte, 'GND')
    poses: list[tuple[float, float]] = []

    def poser_couture(x: float, y: float) -> None:
        if not (BORD <= x <= LARGEUR - BORD and BORD <= y <= HAUTEUR - BORD):
            return
        if any((x - a) ** 2 + (y - b) ** 2 < 0.6 ** 2 for a, b in poses):
            return
        if not libre(pcbnew.SHAPE_CIRCLE(point(x, y), mm(0.15)), set(CUIVRE), elements, mm(ISOLEMENT)):
            return
        via = pcbnew.PCB_VIA(carte)
        via.SetPosition(point(x, y))
        via.SetWidth(mm(0.3))
        via.SetDrill(mm(0.15))
        via.SetNet(masse)
        via.SetLocked(True)
        carte.Add(via)
        poses.append((x, y))

    for ref in ('C20', 'C21', 'D4', 'J6', 'J3', 'C22', 'C23', 'D5', 'J4'):
        empreinte = carte.FindFootprintByReference(ref)
        for p in empreinte.Pads():
            if p.GetNetname() == 'GND':
                poser_couture(*xy(p.GetPosition()))
    for s in carte.GetTracks():
        if s.GetClass() != 'PCB_TRACK' or not s.GetNetname().startswith('RF_'):
            continue
        (xa, ya), (xb, yb) = xy(s.GetStart()), xy(s.GetEnd())
        longueur = ((xa - xb) ** 2 + (ya - yb) ** 2) ** 0.5
        if longueur < 0.3:
            continue
        ux, uy = (xb - xa) / longueur, (yb - ya) / longueur
        ecart = pcbnew.ToMM(s.GetWidth()) / 2 + 0.15 + 0.15 + 0.1  # bord de la piste, fente, rayon du via, marge
        for k in range(int(longueur / 0.8) + 1):
            cx, cy = xa + ux * min(k * 0.8, longueur), ya + uy * min(k * 0.8, longueur)
            for signe in (-1, 1):
                poser_couture(cx - uy * ecart * signe, cy + ux * ecart * signe)
    return len(poses)


# Bande du plan VBAT sur In4 : bord gauche (sous la pastille de la batterie), bord supérieur, puis languette jusque
# sous la résistance de mesure R8 (environ 16 carrés de cuivre de 0,5 oz, soit 16 mΩ).
BANDE_VBAT = [(0.2, 0.2), (21.7, 0.2), (21.7, 8.0), (18.6, 8.0), (18.6, 3.0), (3.8, 3.0), (3.8, 33.0), (0.2, 33.0)]


def vias_dans_plot(carte: pcbnew.BOARD, plot: pcbnew.PAD, diametre: float, percage: float, nombre: int,
                   coins: list[tuple[float, float]]) -> int:
    """Pose jusqu'à `nombre` vias verrouillés dans la pastille (centre dedans), libres sur toutes les couches et
    au-dessus du polygone `coins` (le plan qu'ils rejoignent) ; les plus écartés possible. Rend le nombre posé."""
    from relier_plans import CUIVRE, ISOLEMENT, libre, obstacles
    elements = obstacles(carte, plot.GetNetname())
    poly = pcbnew.SHAPE_POLY_SET()
    poly.NewOutline()
    for x, y in coins:
        poly.Append(mm(X0 + x), mm(Y0 + y))
    r = diametre / 2
    cx, cy = xy(plot.GetPosition())
    b = plot.GetBoundingBox()
    demi_l, demi_h = pcbnew.ToMM(b.GetWidth()) / 2, pcbnew.ToMM(b.GetHeight()) / 2
    candidats = []
    for i in range(-20, 21):
        for j in range(-20, 21):
            x, y = cx + i * 0.05, cy + j * 0.05
            if abs(x - cx) > demi_l or abs(y - cy) > demi_h:
                continue
            p = point(x, y)
            if not plot.HitTest(p):
                continue
            if not poly.Contains(p) or not libre(pcbnew.SHAPE_CIRCLE(p, mm(r)), set(CUIVRE), elements, mm(ISOLEMENT)):
                continue
            candidats.append((x, y))
    choisis: list[tuple[float, float]] = []
    while candidats and len(choisis) < nombre:
        # Le plus éloigné des vias déjà choisis (le premier : le plus proche du centre de la pastille).
        loin = max(candidats, key=lambda c: min(((c[0] - a) ** 2 + (c[1] - b_) ** 2 for a, b_ in choisis),
                                              default=-((c[0] - cx) ** 2 + (c[1] - cy) ** 2)))
        if choisis and min(((loin[0] - a) ** 2 + (loin[1] - b_) ** 2) ** 0.5 for a, b_ in choisis) < diametre + 0.25:
            break
        choisis.append(loin)
        candidats.remove(loin)
    for x, y in choisis:
        via = pcbnew.PCB_VIA(carte)
        via.SetPosition(point(x, y))
        via.SetWidth(mm(diametre))
        via.SetDrill(mm(percage))
        via.SetNet(plot.GetNet())
        via.SetLocked(True)
        carte.Add(via)
    return len(choisis)


def chemin_du_modem(carte: pcbnew.BOARD, empreintes, reseaux) -> None:
    """Pré-routage verrouillé du courant fort du modem (pointes de 2 A en GSM) : batterie → plan VBAT d'In4 par vias
    dans les pastilles TP8 et R8.1, puis R8.2 → entrées VIN de l'interrupteur U6 par deux pistes de 0,4 mm. La broche 4
    de U6 (VBIAS, quelques µA) est reliée par polarisation_du_modem ; le condensateur d'entrée reste au routeur."""
    from relier_plans import ISOLEMENT, libre, obstacles
    for ref, numero, nombre in (('TP8', '1', 4), ('R8', '1', 2)):
        poses = vias_dans_plot(carte, plot(empreintes[ref], numero), 0.4, 0.2, nombre, BANDE_VBAT)
        if poses < 2:
            raise SystemExit(f'chemin du modem : {poses} via(s) seulement dans {ref}.{numero}')
    r8, u6 = plot(empreintes['R8'], '2'), empreintes['U6']
    if r8.GetLayer() != plot(u6, '1').GetLayer():
        raise SystemExit('chemin du modem : R8 et U6 doivent être sur la même face')
    couche = pcbnew.B_Cu if r8.IsOnLayer(pcbnew.B_Cu) else pcbnew.F_Cu
    net = reseaux['VBAT_MES']
    elements = obstacles(carte, 'VBAT_MES')
    xr = xy(r8.GetPosition())[0]
    segments = []
    for numero in ('2', '1'):
        xc, yc = xy(plot(u6, numero).GetPosition())
        segments.append(((xr, yc), (xc, yc), 0.4))
    (xa, ya), (xb, yb) = xy(plot(u6, '1').GetPosition()), xy(plot(u6, '2').GetPosition())
    segments.append(((xa, ya), (xb, yb), 0.25))
    for (xa, ya), (xb, yb), largeur in segments:
        forme = pcbnew.SHAPE_SEGMENT(point(xa, ya), point(xb, yb), mm(largeur))
        # Isolement de la classe par défaut (0,127 mm) aux broches voisines (ON, au pas de 0,5 mm).
        if not libre(forme, {couche}, elements, mm(0.13)):
            raise SystemExit(f'chemin du modem : la piste VBAT_MES ({xa:.2f},{ya:.2f})-({xb:.2f},{yb:.2f}) touche un autre réseau')
        segment = pcbnew.PCB_TRACK(carte)
        segment.SetStart(point(xa, ya))
        segment.SetEnd(point(xb, yb))
        segment.SetWidth(mm(largeur))
        segment.SetLayer(couche)
        segment.SetNet(net)
        segment.SetLocked(True)
        carte.Add(segment)


def couloir_de_la_commande(carte: pcbnew.BOARD, empreintes) -> None:
    """Zone interdite aux vias entre R8 et la broche ON (3) de U6 : c'est le seul passage de la commande du modem,
    sous le bord du module. Sans elle, un via de plan posé dans un plot de masse du module ressort sur cette face et
    ferme le passage ; ce plot reste relié à la masse par ses voisins."""
    import relier_plans
    x3, y3 = xy(plot(empreintes['U6'], '3').GetPosition())
    r8 = plot(empreintes['R8'], '2').GetBoundingBox()
    x1 = pcbnew.ToMM(r8.GetRight()) - X0 + 0.05
    x2 = x3 - pcbnew.ToMM(plot(empreintes['U6'], '3').GetBoundingBox().GetWidth()) / 2
    y1, y2 = y3 - 1.4, y3 + 0.5
    regle = pcbnew.ZONE(carte)
    regle.SetIsRuleArea(True)
    regle.SetZoneName('Couloir de la commande du modem')
    regle.SetLayerSet(pcbnew.LSET.AllCuMask())
    regle.SetDoNotAllowTracks(False)
    regle.SetDoNotAllowVias(True)
    regle.SetDoNotAllowPads(False)
    regle.SetDoNotAllowFootprints(False)
    regle.SetDoNotAllowZoneFills(False)
    contour_regle = regle.Outline()
    contour_regle.NewOutline()
    for x, y in ((x1, y1), (x2, y1), (x2, y2), (x1, y2)):
        contour_regle.Append(mm(X0 + x), mm(Y0 + y))
    carte.Add(regle)
    relier_plans._INTERDITS.pop(id(carte), None)  # zones de règles relues au prochain contrôle


def polarisation_du_modem(carte: pcbnew.BOARD, empreintes, reseaux) -> None:
    """VBIAS (broche 4) de l'interrupteur U6 rejoint la bande VBAT d'In4 par une piste et un via verrouillés, posés
    avant le routage. Ses voisines VIN (2) et ON (3) sont sous le bord du module : aucun via ne passe sous ses plots,
    et une liaison de VBIAS vers R8 par la face de U6 ferait le tour de la broche ON, qui ne sortirait plus. La piste
    monte depuis la broche jusqu'au-dessus du module, puis file vers le via, au plus court ; le passage de ON reste
    interdit aux vias (couloir_de_la_commande)."""
    from relier_plans import CUIVRE, ISOLEMENT, bord_libre, libre, obstacles
    couloir_de_la_commande(carte, empreintes)
    vbias = plot(empreintes['U6'], '4')
    couche = pcbnew.B_Cu if vbias.IsOnLayer(pcbnew.B_Cu) else pcbnew.F_Cu
    x4, y4 = xy(vbias.GetPosition())
    zone_recherche = pcbnew.BOX2I(point(x4 - 6.0, y4 - 4.0), pcbnew.VECTOR2I(mm(7.0), mm(5.0)))
    elements = [e for e in obstacles(carte, vbias.GetNetname()) if e[0].BBox().Intersects(zone_recherche)]
    bande = pcbnew.SHAPE_POLY_SET()
    bande.NewOutline()
    for x, y in BANDE_VBAT:
        bande.Append(mm(X0 + x), mm(Y0 + y))
    largeur, rayon, marge = 0.15, 0.15, mm(ISOLEMENT)
    meilleur = None
    for i in range(6, 61):
        for j in range(6, 31):
            xv, yv = x4 - 0.1 * i, y4 - 0.1 * j
            centre, coude = point(xv, yv), point(x4, yv)
            # Au moins 0,3 mm à l'intérieur de la bande : le via touche le plan coulé.
            if not all(bande.Contains(point(xv + dx, yv + dy)) for dx in (-0.3, 0.3) for dy in (-0.3, 0.3)):
                continue
            longueur = (x4 - xv) + (y4 - yv)
            if meilleur is not None and longueur >= meilleur[0]:
                continue
            if not bord_libre(carte, centre) or                     not libre(pcbnew.SHAPE_CIRCLE(centre, mm(rayon)), set(CUIVRE), elements, marge) or                     not libre(pcbnew.SHAPE_SEGMENT(vbias.GetPosition(), coude, mm(largeur)), {couche}, elements, marge) or                     not libre(pcbnew.SHAPE_SEGMENT(coude, centre, mm(largeur)), {couche}, elements, marge):
                continue
            meilleur = (longueur, coude, centre)
    if meilleur is None:
        raise SystemExit('chemin du modem : aucune place pour relier VBIAS de U6 à la bande VBAT')
    _, coude, centre = meilleur
    reseau = reseaux[vbias.GetNetname()]
    for debut_piste, fin_piste in ((vbias.GetPosition(), coude), (coude, centre)):
        piste = pcbnew.PCB_TRACK(carte)
        piste.SetStart(debut_piste)
        piste.SetEnd(fin_piste)
        piste.SetWidth(mm(largeur))
        piste.SetLayer(couche)
        piste.SetNet(reseau)
        piste.SetLocked(True)
        carte.Add(piste)
    via = pcbnew.PCB_VIA(carte)
    via.SetPosition(centre)
    via.SetWidth(mm(2 * rayon))
    via.SetDrill(mm(0.15))
    via.SetNet(reseau)
    via.SetLocked(True)
    carte.Add(via)


def placer_etiquette(carte: pcbnew.BOARD, etiquette: pcbnew.PCB_TEXT, empreinte: pcbnew.FOOTPRINT) -> None:
    """Pose l'étiquette contre sa pastille (au-dessus, en dessous, à gauche, à droite, puis en diagonale ; droite, puis
    tournée d'un quart de tour) : au premier endroit où elle ne recouvre ni cuivre de sa face ni autre sérigraphie, et
    reste dans la carte (la sérigraphie sur du cuivre est rognée par le façonnier). La taille reste de 0,8 mm, minimum
    du façonnier."""
    face = pcbnew.B_Cu if empreinte.GetLayer() == pcbnew.B_Cu else pcbnew.F_Cu
    plots = [q for f in carte.GetFootprints() for q in f.Pads() if q.IsOnLayer(face)]
    textes = [d.GetBoundingBox() for d in carte.GetDrawings() if d.GetLayer() == etiquette.GetLayer() and d is not etiquette]
    bord = carte.GetBoardEdgesBoundingBox()
    b = next(iter(empreinte.Pads())).GetBoundingBox()
    x1, y1 = pcbnew.ToMM(b.GetLeft()) - X0, pcbnew.ToMM(b.GetTop()) - Y0
    x2, y2 = pcbnew.ToMM(b.GetRight()) - X0, pcbnew.ToMM(b.GetBottom()) - Y0
    cx, cy = (x1 + x2) / 2, (y1 + y2) / 2
    etiquette.SetTextSize(pcbnew.VECTOR2I(mm(0.8), mm(0.8)))
    for angle in (0, 90):
        etiquette.SetTextAngleDegrees(angle)
        etiquette.SetPosition(point(cx, cy))
        taille = etiquette.GetBoundingBox()
        w, h = pcbnew.ToMM(taille.GetWidth()) / 2, pcbnew.ToMM(taille.GetHeight()) / 2
        for d in (0.2, 0.4, 0.7, 1.0, 1.4, 1.8, 2.2, 2.6):
            for x, y in ((cx, y1 - d - h), (cx, y2 + d + h), (x1 - d - w, cy), (x2 + d + w, cy),
                         (x1 - d - w, y1 - d - h), (x2 + d + w, y1 - d - h), (x1 - d - w, y2 + d + h),
                         (x2 + d + w, y2 + d + h)):
                etiquette.SetPosition(point(x, y))
                boite = etiquette.GetBoundingBox()
                boite.Inflate(pcbnew.FromMM(0.15))
                if bord.Contains(boite) and not any(boite.Intersects(q.GetBoundingBox()) for q in plots) and                         not any(boite.Intersects(t) for t in textes):
                    return
    raise SystemExit(f'étiquette {etiquette.GetText()} : aucune place libre de cuivre près de {empreinte.GetReference()}')


# Pastille thermique de chaque circuit : vias de masse (bouchés et recouverts) et pâte réduite, pour que le
# composant ne flotte pas sur un excès de brasure (environ 60 % de la pastille couverte).
PASTILLES_THERMIQUES = {'U5': '11', 'U6': '9', 'U7': '9', 'U8': '9', 'U9': '9', 'U10': '17', 'U11': '17'}


def pastilles_thermiques(carte: pcbnew.BOARD, empreintes) -> int:
    poses = 0
    for ref, numero in PASTILLES_THERMIQUES.items():
        p = plot(empreintes[ref], numero)
        l, h = pcbnew.ToMM(p.GetSize().x), pcbnew.ToMM(p.GetSize().y)
        poses += vias_dans_plot(carte, p, 0.3, 0.15, max(1, min(6, round(l * h / 0.45))), TOUT)
        p.SetLocalSolderPasteMarginRatio(-0.1 if l * h < 4 else -0.13)
    return poses


# Repère de broche 1 (circuits, transistors, LED), de cathode (diodes) et de + (condensateurs au tantale).
POLARISES = ('D1', 'D15', 'D11', 'D10', 'C10', 'C11')


def marquer_polarite(carte: pcbnew.BOARD, empreintes) -> list[str]:
    """Point de sérigraphie de 0,35 mm près du plot repère, hors de tout cuivre de la face ; rend les références
    sans place."""
    sans_place = []
    bord = carte.GetBoardEdgesBoundingBox()
    for ref in sorted(r for r in empreintes if r[0] in 'UQ') + list(POLARISES):
        fp = empreintes[ref]
        dessous = fp.GetLayer() == pcbnew.B_Cu
        cuivre = pcbnew.B_Cu if dessous else pcbnew.F_Cu
        numeros = [q.GetNumber() for q in fp.Pads()]
        repere = plot(fp, '1' if '1' in numeros else 'A1')
        plots = [q for f in carte.GetFootprints() for q in f.Pads() if q.IsOnLayer(cuivre)]
        textes = [d for d in carte.GetDrawings() if d.GetLayer() == (pcbnew.B_SilkS if dessous else pcbnew.F_SilkS)]
        (cx, cy), (px, py) = xy(fp.GetPosition()), xy(repere.GetPosition())
        norme = ((px - cx) ** 2 + (py - cy) ** 2) ** 0.5 or 1.0
        ux, uy = (px - cx) / norme, (py - cy) / norme
        demi = max(pcbnew.ToMM(repere.GetSize().x), pcbnew.ToMM(repere.GetSize().y)) / 2
        pose = False
        for d in (demi + 0.35, demi + 0.55, demi + 0.8, demi + 1.1):
            for ax, ay in ((ux, uy), (-uy, ux), (uy, -ux), (ux - uy, uy + ux), (ux + uy, uy - ux)):
                n = (ax * ax + ay * ay) ** 0.5
                x, y = px + ax / n * d, py + ay / n * d
                boite = pcbnew.BOX2I(point(x - 0.33, y - 0.33), pcbnew.VECTOR2I(mm(0.66), mm(0.66)))
                if not bord.Contains(boite) or any(boite.Intersects(q.GetBoundingBox()) for q in plots) or \
                        any(boite.Intersects(t.GetBoundingBox()) for t in textes):
                    continue
                point_repere = pcbnew.PCB_SHAPE(carte)
                point_repere.SetShape(pcbnew.SHAPE_T_CIRCLE)
                point_repere.SetCenter(point(x, y))
                point_repere.SetEnd(point(x + 0.1, y))
                point_repere.SetWidth(mm(0.15))
                point_repere.SetFilled(True)
                point_repere.SetLayer(pcbnew.B_SilkS if dessous else pcbnew.F_SilkS)
                carte.Add(point_repere)
                pose = True
                break
            if pose:
                break
        if not pose:
            sans_place.append(ref)
    return sans_place


def une_couche(couche: int) -> pcbnew.LSET:
    jeu = pcbnew.LSET()
    jeu.AddLayer(couche)
    return jeu


def sans_pistes(carte: pcbnew.BOARD, couches: pcbnew.LSET, coins: list[tuple[float, float]], vias=False) -> None:
    """Zone de règle interdite aux pistes (et aux vias si `vias`) ; les plans y restent coulés."""
    regle = pcbnew.ZONE(carte)
    regle.SetIsRuleArea(True)
    regle.SetLayerSet(couches)
    regle.SetDoNotAllowTracks(True)
    regle.SetDoNotAllowVias(vias)
    regle.SetDoNotAllowPads(False)
    regle.SetDoNotAllowFootprints(False)
    regle.SetDoNotAllowZoneFills(False)
    contour_regle = regle.Outline()
    contour_regle.NewOutline()
    for x, y in coins:
        contour_regle.Append(mm(X0 + x), mm(Y0 + y))
    carte.Add(regle)


def interdits_bord(carte: pcbnew.BOARD) -> None:
    """Bande de 0,5 mm interdite aux pistes et aux vias le long du bord."""
    for x1, y1, x2, y2 in ((0, 0, LARGEUR, 0.5), (0, HAUTEUR - 0.5, LARGEUR, HAUTEUR), (0, 0, 0.5, HAUTEUR),
                           (LARGEUR - 0.5, 0, LARGEUR, HAUTEUR)):
        sans_pistes(carte, pcbnew.LSET.AllCuMask(), [(x1, y1), (x2, y1), (x2, y2), (x1, y2)], vias=True)


def contour(carte: pcbnew.BOARD) -> None:
    r = RAYON
    segments = [((r, 0), (LARGEUR - r, 0)), ((LARGEUR, r), (LARGEUR, HAUTEUR - r)),
                ((LARGEUR - r, HAUTEUR), (r, HAUTEUR)), ((0, HAUTEUR - r), (0, r))]
    for (xa, ya), (xb, yb) in segments:
        trait = pcbnew.PCB_SHAPE(carte, pcbnew.SHAPE_T_SEGMENT)
        trait.SetLayer(pcbnew.Edge_Cuts)
        trait.SetStart(point(xa, ya))
        trait.SetEnd(point(xb, yb))
        trait.SetWidth(mm(0.1))
        carte.Add(trait)
    k = r * (1 - 0.5 ** 0.5)  # point milieu de chaque arc d'angle
    for debut, milieu, fin in (((0, r), (k, k), (r, 0)), ((LARGEUR - r, 0), (LARGEUR - k, k), (LARGEUR, r)),
                               ((LARGEUR, HAUTEUR - r), (LARGEUR - k, HAUTEUR - k), (LARGEUR - r, HAUTEUR)),
                               ((r, HAUTEUR), (k, HAUTEUR - k), (0, HAUTEUR - r))):
        arc = pcbnew.PCB_SHAPE(carte, pcbnew.SHAPE_T_ARC)
        arc.SetLayer(pcbnew.Edge_Cuts)
        arc.SetArcGeometry(point(*debut), point(*milieu), point(*fin))
        arc.SetWidth(mm(0.1))
        carte.Add(arc)


REGLES_DRC = """(version 1)

# Guide coplanaire des lignes d'antenne (outils/impedance.py) : la classe RF tient tout autre réseau à 0,5 mm, mais la
# masse et les autres tronçons RF gardent la fente de 0,15 mm qui fixe l'impédance de 50 ohms.
(rule "Fente du guide coplanaire"
  (condition "(A.NetClass == 'RF' && (B.NetName == 'GND' || B.NetClass == 'RF')) || (B.NetClass == 'RF' && A.NetName == 'GND')")
  (constraint clearance (min 0.15mm)))

# Choc de polarisation de l'antenne GNSS (L2, 0402) : ses deux plots, RF_GNSS_1 et GNSS_BIAS, sont à 0,38 mm.
(rule "Polarisation de l'antenne GNSS"
  (condition "(A.NetClass == 'RF' && B.NetName == 'GNSS_BIAS') || (B.NetClass == 'RF' && A.NetName == 'GNSS_BIAS')")
  (constraint clearance (min 0.15mm)))
"""


def regles(carte: pcbnew.BOARD) -> None:
    """Règles aux capacités standard des façonniers (4 couches) et classes de réseaux."""
    reglage = carte.GetDesignSettings()
    reglage.SetBoardThickness(mm(1.2))  # 6 couches : 1,2 mm au plus fin chez le façonnier
    reglage.m_TrackMinWidth = mm(0.09)  # capacité standard des façonniers en multicouche (3,5 mil)
    reglage.m_MinClearance = mm(0.1)
    # Vias de 0,3 mm percés à 0,15 mm, annulaire 0,075 mm : capacité standard du façonnier en 6 couches (perçage
    # 0,15 mm, diamètre ≥ perçage + 0,1 mm, 0,2 mm entre perçages). Les vias dans les pastilles sont bouchés à la
    # résine et recouverts, procédé par défaut en 6 couches et plus (ADR 0042, point 9).
    reglage.m_ViasMinSize = mm(0.3)
    reglage.m_MinThroughDrill = mm(0.15)
    reglage.m_ViasMinAnnularWidth = mm(0.075)
    reglage.m_HoleToHoleMin = mm(0.2)
    reglage.m_HoleClearance = mm(0.2)
    reglage.m_CopperEdgeClearance = mm(0.3)
    reglage.m_MinResolvedSpokes = 1  # petits plots de masse : un rayon de liaison thermique suffit
    reglage.m_TentViasFront = True   # vias recouverts de vernis (ceux des pastilles sont bouchés et recouverts)
    reglage.m_TentViasBack = True
    reseaux = reglage.m_NetSettings
    defaut = reseaux.GetDefaultNetclass()
    defaut.SetTrackWidth(mm(0.127))
    defaut.SetClearance(mm(0.127))
    defaut.SetViaDiameter(mm(0.3))
    defaut.SetViaDrill(mm(0.15))
    classes = {
        # nom : (piste, isolement, via, perçage, réseaux)
        'Modem': (0.8, 0.15, 0.6, 0.3, ['VBAT_MODEM']),
        'Puissance': (0.4, 0.15, 0.5, 0.25, ['VBAT', 'VSYS', 'VIN', 'VIN_EXT', '3V3', '3V3_REG', 'TPS_LX1', 'TPS_LX2']),
        'Alim_1V8': (0.3, 0.127, 0.3, 0.15, ['1V8', '3V3_FLASH', 'SIM_VDD']),
        # RF : 0,5 mm de tout autre réseau (couloir des lignes de 50 Ω) ; la fente de 0,15 mm vers la masse est une
        # règle du projet (REGLES_DRC).
        'RF': (PISTE_RF, 0.5, 0.3, 0.15, ['RF_LTE_MOD', 'RF_LTE_1', 'RF_LTE_2', 'RF_GNSS_MOD', 'RF_GNSS_1']),
    }
    for nom, (piste, isolement, via, percage, membres) in classes.items():
        classe = pcbnew.NETCLASS(nom)
        classe.SetTrackWidth(mm(piste))
        classe.SetClearance(mm(isolement))
        classe.SetViaDiameter(mm(via))
        classe.SetViaDrill(mm(percage))
        reseaux.SetNetclass(nom, classe)
        for membre in membres:
            reseaux.SetNetclassPatternAssignment(membre, nom)


def zone(carte: pcbnew.BOARD, reseau: pcbnew.NETINFO_ITEM, couche: int, coins: list[tuple[float, float]], priorite=0) -> None:
    surface = pcbnew.ZONE(carte)
    surface.SetLayer(couche)
    surface.SetNet(reseau)
    surface.SetAssignedPriority(priorite)
    surface.SetMinThickness(mm(0.15))
    surface.SetLocalClearance(mm(0.15))  # fente du guide coplanaire RF (outils/impedance.py)
    surface.SetPadConnection(pcbnew.ZONE_CONNECTION_THERMAL)
    surface.SetThermalReliefGap(mm(0.2))
    surface.SetThermalReliefSpokeWidth(mm(0.25))
    contour_zone = surface.Outline()
    contour_zone.NewOutline()
    for x, y in coins:
        contour_zone.Append(mm(X0 + x), mm(Y0 + y))
    carte.Add(surface)


def severites_du_projet() -> None:
    """Écart volontaire aux bibliothèques : les empreintes perdent leur sérigraphie et leurs champs passent sur le
    plan de montage (voir charger). Le DRC le signalerait pour chacune ; l'avertissement est ignoré dans le projet,
    tous les autres contrôles restent actifs. KiCad n'expose pas ce réglage à Python : il est écrit dans le projet."""
    projet = SORTIE.with_suffix('.kicad_pro')
    donnees = json.loads(projet.read_text(encoding='utf-8'))
    donnees['board']['design_settings']['rule_severities']['lib_footprint_mismatch'] = 'ignore'
    projet.write_text(json.dumps(donnees, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')


def main() -> None:
    carte = pcbnew.BOARD()
    # 6 couches : signaux et RF / masse continue / signaux / signaux / alimentations / signaux (ADR 0042).
    carte.SetCopperLayerCount(6)
    carte.SetLayerName(pcbnew.In1_Cu, 'In1.Masse')
    carte.SetLayerName(pcbnew.In2_Cu, 'In2.Signal')
    carte.SetLayerName(pcbnew.In3_Cu, 'In3.Signal')
    carte.SetLayerName(pcbnew.In4_Cu, 'In4.Alim')
    # In1 est un plan : Freerouting n'y route pas (type « power » dans l'export Specctra). In4 est mixte : ses plans
    # d'alimentation sont coulés autour de quelques pistes de signal, sauf sous le modem (ADR 0042, point 9).
    carte.SetLayerType(pcbnew.In1_Cu, pcbnew.LT_POWER)
    carte.SetLayerType(pcbnew.In4_Cu, pcbnew.LT_MIXED)
    regles(carte)
    contour(carte)

    schema = reseaux_du_schema()
    for c in COMPOSANTS:
        for numero, nom in c.broches.items():
            if schema.get((c.ref, numero)) != nom:
                raise SystemExit(f'{c.ref}.{numero} : {nom} dans carte.py, {schema.get((c.ref, numero))} dans le schéma')
    reseaux: dict[str, pcbnew.NETINFO_ITEM] = {}
    for nom in sorted(set(schema.values())):
        reseaux[nom] = pcbnew.NETINFO_ITEM(carte, nom)
        carte.Add(reseaux[nom])

    empreintes = {}
    for c in COMPOSANTS:
        empreinte = charger(c)
        for broche in empreinte.Pads():
            nom = schema.get((c.ref, broche.GetNumber()))
            if nom:
                broche.SetNet(reseaux[nom])
        carte.Add(empreinte)
        empreintes[c.ref] = empreinte

    obstacles = Obstacles()
    for ref, (x, y, rotation, face) in FIXES.items():
        poser(empreintes[ref], 0, 0, rotation, face)
        centrer(empreintes[ref], x, y) if ref not in PAR_ORIGINE else empreintes[ref].SetPosition(point(x, y))
        obstacles.ajouter(empreintes[ref], face)

    # Place des étiquettes des pastilles figées, réservée avant le rangement : au-dessus de la pastille, au-dessous pour
    # le second fil d'une paire verticale (placer_etiquette essaie ces deux places en premier).
    for ref in ETIQUETTES:
        if ref in FIXES:
            b = next(iter(empreintes[ref].Pads())).GetBoundingBox()
            x1, y1 = pcbnew.ToMM(b.GetLeft()) - X0, pcbnew.ToMM(b.GetTop()) - Y0
            x2, y2 = pcbnew.ToMM(b.GetRight()) - X0, pcbnew.ToMM(b.GetBottom()) - Y0
            cx = (x1 + x2) / 2
            dessous = ref == 'TP33'
            obstacles.reserver(FIXES[ref][3], (cx - 1.25, y2 + 0.05, cx + 1.25, y2 + 1.7) if dessous else
                               (cx - 1.25, y1 - 1.7, cx + 1.25, y1 - 0.05))

    longueurs_rf = lignes_rf(carte, empreintes, reseaux, obstacles)
    # Les lignes RF elles-mêmes, et leur couloir de 0,5 mm, sont interdits aux autres pièces de la face supérieure.
    for s in carte.GetTracks():
        if s.GetNetname().startswith('RF_'):
            (xa, ya), (xb, yb) = xy(s.GetStart()), xy(s.GetEnd())
            obstacles.reserver('F', (min(xa, xb) - 0.6, min(ya, yb) - 0.6, max(xa, xb) + 0.6, max(ya, yb) + 0.6))
    bloc_regulateur(empreintes, obstacles, 17.0, 9.0)
    # Couloir des pistes VBAT_MES pré-routées entre R8.2 et les broches VIN de U6.
    (xr, _), broches_vin = xy(plot(empreintes['R8'], '2').GetPosition()), [xy(plot(empreintes['U6'], n).GetPosition()) for n in '12']
    obstacles.reserver('B', (xr, min(y for _, y in broches_vin) - 0.45, max(x for x, _ in broches_vin),
                             max(y for _, y in broches_vin) + 0.45))
    places = set(FIXES) | {'C20', 'R20', 'C21', 'D4', 'C22', 'C24', 'C23', 'D5', 'L2', 'U5', 'L1', 'C4', 'C5'}
    faces = {r: face for face, *_, refs in ZONES for r in refs}
    # Découplage des circuits déjà en place (microcontrôleur, module, interrupteur), puis chaque zone : ses circuits
    # intégrés avec un dégagement, leurs condensateurs à leur pied, puis le reste.
    poser_au_pied(empreintes, list(AU_PIED), places, obstacles, faces)
    hors = []
    for face, x1, y1, x2, y2, refs in ZONES:
        circuits = [r for r in refs if r not in places and r.startswith('U')]
        if circuits:
            hors += ranger([empreintes[r] for r in circuits], face, x1, y1, x2, y2, obstacles, ECART + HALO)
            places |= set(circuits) - set(hors)
            poser_au_pied(empreintes, refs, places, obstacles, faces)
        reste = [r for r in refs if r not in places]
        sortis = ranger([empreintes[r] for r in reste], face, x1, y1, x2, y2, obstacles)
        # Repli : une pièce qui ne tient plus dans sa zone (les condensateurs posés au pied y ont pris leur place)
        # prend la première place libre de sa face, puis de l'autre.
        for ref in sortis:
            if ranger([empreintes[ref]], face, 0, 0, LARGEUR, HAUTEUR, obstacles) and \
                    ranger([empreintes[ref]], 'B' if face == 'F' else 'F', 0, 0, LARGEUR, HAUTEUR, obstacles):
                hors.append(ref)
        places |= set(reste)
    # Second fil de chaque paire (vibreur, boucle du bracelet) à côté du premier : les deux fils arrivent ensemble.
    for ref, voisin in PAIRES:
        if not au_pied(empreintes[ref], plot(empreintes[voisin], '1'), 'B', obstacles, numero='1', positions=CANDIDATS_PAIRES):
            hors.append(ref)
        places.add(ref)
    loin = [r for r in AU_PIED if r in empreintes and r in places and
            min(((p.GetPosition() - plot(empreintes[AU_PIED[r][0]], AU_PIED[r][1]).GetPosition()).EuclideanNorm()
                 for p in empreintes[r].Pads() if p.GetNetname() == plot(empreintes[AU_PIED[r][0]], AU_PIED[r][1]).GetNetname()),
                default=0) > mm(RAYON_PIED + 0.01)]
    if loin:
        print('posés hors de portée de leur broche (rangés dans leur zone) :', ', '.join(loin))
    oublies = sorted(set(empreintes) - places)
    if oublies:
        raise SystemExit(f'composants sans place : {oublies}')

    # Plan de masse plein sur In1 (§ 4.2, ligne 21), interdit aux pistes : il sert de référence aux lignes RF et à
    # l'USB du modem. Les masses des faces externes sont coulées après le routage (router.py) : déclarées avant,
    # elles interdiraient ces faces au routeur.
    zone(carte, reseaux['GND'], pcbnew.In1_Cu, TOUT)
    sans_pistes(carte, une_couche(pcbnew.In1_Cu), TOUT)
    # In4 en trois plans séparés de 0,3 mm : modem (en haut), VSYS (bloc de charge, LED, buzzer, vibreur), 3,3 V (tout
    # le reste : microcontrôleur, capteurs, translateurs, mémoire). Le plan du modem, de l'interrupteur aux plots
    # VBAT (l'équivalent des 3 mm demandés par SIMCom), est prioritaire et interdit aux pistes de signal : ses
    # pointes de 2 A ne doivent pas contourner de fentes.
    modem = [(27.4, 0.6), (40.4, 0.6), (40.4, 12.0), (14.6, 12.0), (14.6, 5.4), (27.4, 5.4)]
    zone(carte, reseaux['VBAT_MODEM'], pcbnew.In4_Cu, modem, 2)
    sans_pistes(carte, une_couche(pcbnew.In4_Cu), modem)
    zone(carte, reseaux['VSYS'], pcbnew.In4_Cu, [(14.3, 12.3), (LARGEUR - 0.2, 12.3), (LARGEUR - 0.2, 31.0), (27.5, 31.0),
                                                 (27.5, 18.0), (14.3, 18.0)], 1)
    zone(carte, reseaux['3V3'], pcbnew.In4_Cu, [(0.2, 0.2), (27.1, 0.2), (27.1, 5.1), (14.0, 5.1), (14.0, 18.3),
                                                (27.2, 18.3), (27.2, 31.3), (LARGEUR - 0.2, 31.3),
                                                (LARGEUR - 0.2, HAUTEUR - 0.2), (0.2, HAUTEUR - 0.2)], 1)
    # Plan VBAT, prioritaire : les plans du modem et du 3,3 V se coulent autour. Interdit aux pistes, comme celui du
    # modem : c'est le chemin des pointes de 2 A, de la batterie à l'interrupteur U6.
    zone(carte, reseaux['VBAT'], pcbnew.In4_Cu, BANDE_VBAT, 3)
    sans_pistes(carte, une_couche(pcbnew.In4_Cu), BANDE_VBAT)
    chemin_du_modem(carte, empreintes, reseaux)
    polarisation_du_modem(carte, empreintes, reseaux)
    vias_thermiques = pastilles_thermiques(carte, empreintes)
    interdits_bord(carte)
    vias_masse = coutures(carte, reseaux['GND'])

    # Étiquettes de câblage, au-dessus de chaque pastille, sur la face de la pastille.
    for ref, texte in ETIQUETTES.items():
        empreinte = empreintes[ref]
        etiquette = pcbnew.PCB_TEXT(carte)
        etiquette.SetText(texte)
        etiquette.SetLayer(pcbnew.B_SilkS if empreinte.GetLayer() == pcbnew.B_Cu else pcbnew.F_SilkS)
        etiquette.SetTextSize(pcbnew.VECTOR2I(mm(0.8), mm(0.8)))
        etiquette.SetTextThickness(mm(0.12))
        etiquette.SetMirrored(empreinte.GetLayer() == pcbnew.B_Cu)
        placer_etiquette(carte, etiquette, empreinte)
        carte.Add(etiquette)
    sans_repere = marquer_polarite(carte, empreintes)
    if sans_repere:
        print('sans place pour le repère de polarité :', ', '.join(sans_repere))
    # Marquage de la carte.
    marque = pcbnew.PCB_TEXT(carte)
    marque.SetText('FG V1.0')
    marque.SetLayer(pcbnew.F_SilkS)
    marque.SetTextSize(pcbnew.VECTOR2I(mm(0.8), mm(0.8)))
    marque.SetTextThickness(mm(0.12))
    marque.SetPosition(point(30.8, 33.6))
    carte.Add(marque)

    pcbnew.ZONE_FILLER(carte).Fill(carte.Zones())
    carte.Save(str(SORTIE))
    severites_du_projet()
    SORTIE.with_suffix('.kicad_dru').write_text(REGLES_DRC, encoding='utf-8')
    print(f'carte enregistrée : {SORTIE} ; {len(empreintes)} empreintes ; lignes RF ' +
          ', '.join(f'{k} {v:.1f} mm' for k, v in longueurs_rf.items()) + f' ; {vias_masse} vias de couture ; '
          f'{vias_thermiques} vias dans les pastilles thermiques')
    if hors:
        print('NE TIENNENT PAS DANS LEUR ZONE :', ', '.join(hors))


if __name__ == '__main__':
    main()
