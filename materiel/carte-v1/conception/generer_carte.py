"""Étape 3 : génère le circuit imprimé (contour, empilement, règles, placement, plans) depuis la netlist de référence.

À lancer avec l'interpréteur Python de KiCad :
    "C:\\Users\\rachi\\AppData\\Local\\Programs\\KiCad\\10.0\\bin\\python.exe" generer_carte.py

Les coordonnées sont en millimètres, vue de dessus, origine au coin supérieur gauche de la carte. Les composants de
la face inférieure sont placés par leur position vue de dessus, puis retournés.
"""
from __future__ import annotations

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
X0, Y0 = 100.0, 100.0
BORD = 0.6  # distance des pièces rangées au bord de la carte (mm)
TOUT = [(0.2, 0.2), (LARGEUR - 0.2, 0.2), (LARGEUR - 0.2, HAUTEUR - 0.2), (0.2, HAUTEUR - 0.2)]
SORTIE = bib.PROJET / 'kicad' / f'{NOM}.kicad_pcb'


def mm(v: float) -> int:
    return pcbnew.FromMM(v)


def point(x: float, y: float) -> pcbnew.VECTOR2I:
    return pcbnew.VECTOR2I(mm(X0 + x), mm(Y0 + y))


# Étiquettes imprimées à côté des pastilles des liaisons hors carte (câblage dans le boîtier).
ETIQUETTES = {'TP1': '+5V', 'TP2': 'GND', 'TP8': 'BAT+', 'TP9': 'BAT-', 'TP17': 'NTC', 'TP30': 'BZ+', 'TP31': 'BZ-',
              'TP32': 'VB+', 'TP33': 'VB-', 'TP34': 'LP', 'TP35': 'LP-', 'TP36': 'EL'}

# Placement imposé : (x, y, rotation, face). Le reste est rangé par zones (plus bas).
FIXES = {
    'U2': (27.3, 18.0, 0, 'F'),       # module : plot RF (60) vers le bord supérieur, GNSS (90) vers le bas
    'J5': (6.6, 7.0, 0, 'F'),         # porte-nano-SIM
    'U1': (7.0, 22.4, 0, 'F'),        # microcontrôleur
    'J7': (7.0, 15.4, 0, 'B'),        # Tag-Connect : ses trous tombent entre le porte-SIM et le microcontrôleur
    'J4': (8.6, 33.4, 0, 'F'),        # U.FL GNSS
    'J3': (16.6, 2.4, 0, 'F'),        # U.FL LTE
    'J6': (20.6, 2.4, 0, 'F'),        # commutateur de mesure conduite
    'C10': (31.0, 2.4, 0, 'F'),       # réservoir au-dessus des plots VBAT (55 à 57)
    'C11': (37.6, 2.4, 0, 'F'),
    'SW1': (36.5, 34.0, 0, 'F'),      # bouton SOS latéral : poussoir au bord inférieur, sous la membrane du boîtier
    'L1': (20.0, 33.5, 0, 'F'),
    'D10': (25.0, 33.5, 0, 'F'),      # LED RGB (guide de lumière du boîtier)
}

# Zones de rangement : (face, x1, y1, x2, y2, références dans l'ordre de rangement).
ZONES = [
    ('F', 22.4, 0.4, 28.9, 4.6, ['D4', 'C21', 'R20', 'C20']),                     # adaptation LTE
    ('F', 11.0, 31.4, 18.2, 35.6, ['C22', 'C24', 'C23', 'D5', 'L2', 'R23', 'C25']),  # adaptation et polarisation GNSS
    ('B', 27.6, 0.5, 35.0, 4.9, ['C12', 'C13', 'C14', 'C15', 'D2']),               # découplage VBAT du modem
    ('B', 0.6, 0.6, 13.6, 13.0, ['U9', 'Q2', 'C63', 'R64', 'R65', 'U3', 'C60', 'C61', 'U8', 'C62', 'R60', 'R61',
                                  'U12', 'R30', 'R31', 'R32', 'C30', 'C31', 'C32', 'C33']),
    ('B', 0.6, 17.6, 13.6, 29.4, ['TP36', 'Y1', 'C49', 'C50', 'C40', 'C41', 'C42', 'C43', 'C44', 'C45', 'C46', 'C47', 'C48', 'R40',
                                   'TP20', 'R62', 'R63', 'TP40', 'TP41', 'TP42', 'TP43', 'TP44', 'TP45']),
    ('B', 0.6, 29.6, 13.6, 35.5, ['TP8', 'TP9', 'TP17', 'F1', 'C1']),
    ('B', 14.6, 5.4, 27.4, 18.0, ['D1', 'D15', 'TP7', 'R8', 'U4', 'C2', 'C3', 'U5', 'R2', 'R4', 'C4', 'C5', 'R5', 'U7', 'C8', 'U6', 'C6', 'R6', 'C7',
                                   'R7', 'RT1']),
    ('B', 27.6, 5.4, 40.4, 18.0, ['Q1', 'R10', 'R11', 'R12', 'R13', 'R14', 'R15', 'R16', 'R17', 'C16', 'C17', 'TP3', 'TP4', 'TP5', 'TP6', 'TP10', 'TP11',
                                   'TP12', 'TP13', 'TP14', 'TP15', 'TP16']),
    ('B', 14.6, 18.2, 27.4, 31.0, ['U10', 'C70', 'C71', 'U11', 'C72', 'C73', 'TP33', 'TP34', 'TP35']),
    ('B', 27.6, 18.2, 40.4, 31.0, ['Q3', 'Q4', 'R80', 'R81', 'R82', 'R83', 'R84', 'R85', 'R86', 'R87', 'Q5', 'R88', 'R89',
                                   'D11', 'D12', 'R91', 'R92', 'C65', 'D13', 'R93', 'C64', 'D14']),
    ('B', 14.6, 31.0, 40.4, 35.6, ['TP1', 'TP2', 'TP30', 'TP31', 'TP32']),
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
    empreinte.SetOrientationDegrees(rotation)
    empreinte.SetPosition(point(x, y))
    if face == 'B' and empreinte.GetLayer() == pcbnew.F_Cu:
        empreinte.Flip(empreinte.GetPosition(), pcbnew.FLIP_DIRECTION_LEFT_RIGHT)


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

    def libre(self, face: str, boite: tuple[float, float, float, float], ecart: float) -> bool:
        x1, y1, x2, y2 = boite
        return all(x2 + ecart <= a1 or x1 - ecart >= a2 or y2 + ecart <= b1 or y1 - ecart >= b2
                   for a1, b1, a2, b2 in self.faces[face])


def ranger(empreintes: list[pcbnew.FOOTPRINT], face: str, x1: float, y1: float, x2: float, y2: float,
           obstacles: Obstacles) -> list[str]:
    """Rangement « en bas à gauche d'abord » : chaque pièce, de la plus grande à la plus petite, prend la première
    place libre de la zone, dans l'orientation qui tient, sans toucher les pièces ni les trous déjà posés."""
    ecart, pas = 0.25, 0.1
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


def regles(carte: pcbnew.BOARD) -> None:
    """Règles aux capacités standard des façonniers (4 couches) et classes de réseaux."""
    reglage = carte.GetDesignSettings()
    reglage.SetBoardThickness(mm(1.0))
    reglage.m_TrackMinWidth = mm(0.1)
    reglage.m_MinClearance = mm(0.1)
    reglage.m_ViasMinSize = mm(0.4)
    reglage.m_MinThroughDrill = mm(0.2)
    reglage.m_HoleClearance = mm(0.2)
    reglage.m_CopperEdgeClearance = mm(0.3)
    reseaux = reglage.m_NetSettings
    defaut = reseaux.GetDefaultNetclass()
    defaut.SetTrackWidth(mm(0.127))
    defaut.SetClearance(mm(0.127))
    defaut.SetViaDiameter(mm(0.4))
    defaut.SetViaDrill(mm(0.2))
    classes = {
        # nom : (piste, isolement, via, perçage, réseaux)
        'Modem': (0.8, 0.15, 0.6, 0.3, ['VBAT_MODEM']),
        'Puissance': (0.4, 0.15, 0.5, 0.25, ['VBAT', 'VSYS', 'VIN', 'VIN_EXT', '3V3', '3V3_REG', 'TPS_LX1', 'TPS_LX2']),
        'Alim_1V8': (0.3, 0.127, 0.4, 0.2, ['1V8', '3V3_FLASH', 'SIM_VDD']),
        'RF': (0.2, 0.15, 0.4, 0.2, ['RF_LTE_MOD', 'RF_LTE_1', 'RF_LTE_2', 'RF_GNSS_MOD', 'RF_GNSS_1']),
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
    surface.SetLocalClearance(mm(0.2))
    surface.SetPadConnection(pcbnew.ZONE_CONNECTION_THERMAL)
    surface.SetThermalReliefGap(mm(0.2))
    surface.SetThermalReliefSpokeWidth(mm(0.25))
    contour_zone = surface.Outline()
    contour_zone.NewOutline()
    for x, y in coins:
        contour_zone.Append(mm(X0 + x), mm(Y0 + y))
    carte.Add(surface)


def main() -> None:
    carte = pcbnew.BOARD()
    carte.SetCopperLayerCount(4)
    carte.SetLayerName(pcbnew.In1_Cu, 'In1.Masse')
    carte.SetLayerName(pcbnew.In2_Cu, 'In2.Alim')
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
        for plot in empreinte.Pads():
            nom = schema.get((c.ref, plot.GetNumber()))
            if nom:
                plot.SetNet(reseaux[nom])
        carte.Add(empreinte)
        empreintes[c.ref] = empreinte

    obstacles = Obstacles()
    for ref, (x, y, rotation, face) in FIXES.items():
        poser(empreintes[ref], 0, 0, rotation, face)
        centrer(empreintes[ref], x, y) if ref not in ('U2', 'U1', 'J7', 'SW1') else empreintes[ref].SetPosition(point(x, y))
        obstacles.ajouter(empreintes[ref], face)

    places = set(FIXES)
    hors = []
    for face, x1, y1, x2, y2, refs in ZONES:
        hors += ranger([empreintes[r] for r in refs], face, x1, y1, x2, y2, obstacles)
        places |= set(refs)
    oublies = sorted(set(empreintes) - places)
    if oublies:
        raise SystemExit(f'composants sans place : {oublies}')

    # Plan de masse continu sur la couche 2 (§ 4.2, ligne 21). Les masses des faces externes sont coulées après le
    # routage (router.py) : déclarées avant, elles interdiraient ces faces au routeur.
    zone(carte, reseaux['GND'], pcbnew.In1_Cu, TOUT)
    # Rail du modem sur la couche 3, de l'interrupteur aux plots VBAT : l'équivalent des 3 mm demandés par SIMCom.
    zone(carte, reseaux['VBAT_MODEM'], pcbnew.In2_Cu, [(27.4, 0.6), (40.4, 0.6), (40.4, 12.0), (14.6, 12.0), (14.6, 5.4), (27.4, 5.4)], 1)
    # Rail 3,3 V sur la couche 3 pour le microcontrôleur et ses voisins.
    zone(carte, reseaux['3V3'], pcbnew.In2_Cu, [(0.4, 14.0), (14.2, 14.0), (14.2, 35.6), (0.4, 35.6)], 1)

    # Étiquettes de câblage, au-dessus de chaque pastille, sur la face de la pastille.
    for ref, texte in ETIQUETTES.items():
        empreinte = empreintes[ref]
        etiquette = pcbnew.PCB_TEXT(carte)
        etiquette.SetText(texte)
        etiquette.SetLayer(pcbnew.B_SilkS if empreinte.GetLayer() == pcbnew.B_Cu else pcbnew.F_SilkS)
        etiquette.SetTextSize(pcbnew.VECTOR2I(mm(0.8), mm(0.8)))
        etiquette.SetTextThickness(mm(0.12))
        x1, y1, x2, y2 = enveloppe(empreinte)
        etiquette.SetPosition(point((x1 + x2) / 2, y1 - 0.5))
        etiquette.SetMirrored(empreinte.GetLayer() == pcbnew.B_Cu)
        carte.Add(etiquette)
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
    print(f'carte enregistrée : {SORTIE} ; {len(empreintes)} empreintes')
    if hors:
        print('NE TIENNENT PAS DANS LEUR ZONE :', ', '.join(hors))


if __name__ == '__main__':
    main()
