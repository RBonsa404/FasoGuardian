"""Finition après routage : relie aux plans les plots d'alimentation que le routeur a laissés isolés.

Freerouting tient un plot pour relié dès qu'un plan du même réseau existe ; il ne pose pas le via. Ce script pose,
pour chaque plot encore isolé, un via vers le plan : dans la pastille quand elle est assez large (via bouché et
recouvert, procédé par défaut du façonnier en 6 couches : aucune piste ni place prise autour), sinon au pied du plot
avec la courte piste qui l'y relie, à un endroit vérifié libre sur toutes les couches. Les plans internes passent à
0,1 mm d'isolement pour rester d'un seul tenant.

À lancer avec l'interpréteur Python de KiCad, deux fois :
    relier_plans.py avant : juste après le placement, un via verrouillé par plot de masse, de 3,3 V et du modem, posé
                            tant que la place est libre (le routeur passe ensuite autour) ;
    relier_plans.py       : après le routage, pour les plots restés isolés.
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

import pcbnew

CARTE = Path(__file__).resolve().parent.parent / 'kicad' / 'fasoguardian-v1.kicad_pcb'
PLANS = {'3V3': pcbnew.In4_Cu, 'VBAT_MODEM': pcbnew.In4_Cu, 'VSYS': pcbnew.In4_Cu, 'VBAT': pcbnew.In4_Cu,
         'GND': pcbnew.In1_Cu}
CUIVRE = (pcbnew.F_Cu, pcbnew.In1_Cu, pcbnew.In2_Cu, pcbnew.In3_Cu, pcbnew.In4_Cu, pcbnew.B_Cu)
# Isolement : le plus grand des classes de réseaux (puissance et RF : 0,15 mm), avec une marge.
VIA, PERCAGE, PISTE, ISOLEMENT = 0.3, 0.15, 0.2, 0.16
VIA_MODEM = (0.5, 0.25)  # pointes de 2 A du module : vias plus gros sur le plan du modem
VERROUILLER = False


def mm(v: float) -> int:
    return pcbnew.FromMM(v)


def obstacles(carte: pcbnew.BOARD, reseau: str):
    """Cuivre des autres réseaux : (forme, couches) ; les vias et trous traversent toutes les couches."""
    elements = []
    for plot in carte.GetPads():
        if plot.GetNetname() != reseau:
            for couche in CUIVRE:
                if plot.IsOnLayer(couche):
                    elements.append((plot.GetEffectiveShape(couche), couche))
            if plot.GetDrillSize().x > 0:
                for couche in CUIVRE:
                    elements.append((plot.GetEffectiveHoleShape(), couche))
    for piste in carte.GetTracks():
        if piste.GetNetname() == reseau:
            continue
        if piste.GetClass() == 'PCB_VIA':
            for couche in CUIVRE:
                elements.append((piste.GetEffectiveShape(couche), couche))
        else:
            elements.append((piste.GetEffectiveShape(), piste.GetLayer()))
    return elements


def libre(forme, couches, elements, marge: int) -> bool:
    return not any(c in couches and f.Collide(forme, marge) for f, c in elements)


def bord_libre(carte: pcbnew.BOARD, centre: pcbnew.VECTOR2I) -> bool:
    """Loin du bord, et hors des zones interdites aux vias de la carte et des empreintes (connecteurs, antennes)."""
    boite = carte.GetBoardEdgesBoundingBox()
    m = mm(0.8)
    if not (boite.GetLeft() + m < centre.x < boite.GetRight() - m and boite.GetTop() + m < centre.y < boite.GetBottom() - m):
        return False
    return not any(z.GetDoNotAllowVias() and z.Outline().Contains(centre) for z in interdits(carte))


_INTERDITS: dict = {}


def interdits(carte: pcbnew.BOARD) -> list:
    if id(carte) not in _INTERDITS:
        zones = list(carte.Zones()) + [z for fp in carte.GetFootprints() for z in fp.Zones()]
        _INTERDITS[id(carte)] = [z for z in zones if z.GetIsRuleArea()]
    return _INTERDITS[id(carte)]


def sur_le_plan(carte: pcbnew.BOARD, reseau: str, centre: pcbnew.VECTOR2I) -> bool:
    """Le via tombe sur du cuivre coulé du plan de son réseau (et non dans le plan d'un autre rail)."""
    couche = PLANS[reseau]
    rayon = (VIA_MODEM[0] if reseau == 'VBAT_MODEM' else VIA) / 2
    return any(z.GetNetname() == reseau and z.GetFirstLayer() == couche and z.HitTestFilledArea(couche, centre, mm(rayon))
               for z in carte.Zones() if not z.GetIsRuleArea())


def poser_via(carte: pcbnew.BOARD, plot: pcbnew.PAD, centre: pcbnew.VECTOR2I, diametre: float, percage: float, elements):
    via = pcbnew.PCB_VIA(carte)
    via.SetPosition(centre)
    via.SetWidth(mm(diametre))
    via.SetDrill(mm(percage))
    via.SetNet(plot.GetNet())
    via.SetLocked(VERROUILLER)
    carte.Add(via)
    elements.append((via.GetEffectiveShape(pcbnew.F_Cu), pcbnew.F_Cu))


def relier(carte: pcbnew.BOARD, plot: pcbnew.PAD, elements) -> bool:
    """Pose un via dans la pastille, ou au pied du plot avec sa piste ; rend False si aucune place n'est libre."""
    couche = pcbnew.F_Cu if plot.IsOnLayer(pcbnew.F_Cu) else pcbnew.B_Cu
    centre = plot.GetPosition()
    taille = max(plot.GetSize().x, plot.GetSize().y) / 2
    toutes = set(CUIVRE)
    diametre, percage = VIA_MODEM if plot.GetNetname() == 'VBAT_MODEM' else (VIA, PERCAGE)
    # Via dans la pastille : il faut qu'elle le contienne et qu'il soit libre sur les autres couches.
    if min(plot.GetSize().x, plot.GetSize().y) >= mm(diametre) and bord_libre(carte, centre) and \
            sur_le_plan(carte, plot.GetNetname(), centre) and \
            libre(pcbnew.SHAPE_CIRCLE(centre, mm(diametre / 2)), toutes, elements, mm(ISOLEMENT)):
        poser_via(carte, plot, centre, diametre, percage, elements)
        return True
    for distance in [taille + mm(0.45 + 0.25 * i) for i in range(9)]:
        for k in range(24):
            angle = 2 * math.pi * k / 24
            via_centre = pcbnew.VECTOR2I(int(centre.x + distance * math.cos(angle)), int(centre.y + distance * math.sin(angle)))
            if not bord_libre(carte, via_centre) or not sur_le_plan(carte, plot.GetNetname(), via_centre):
                continue
            cercle = pcbnew.SHAPE_CIRCLE(via_centre, mm(diametre / 2))
            segment = pcbnew.SHAPE_SEGMENT(centre, via_centre, mm(PISTE))
            if not libre(cercle, toutes, elements, mm(ISOLEMENT)) or not libre(segment, {couche}, elements, mm(ISOLEMENT)):
                continue
            poser_via(carte, plot, via_centre, diametre, percage, elements)
            piste = pcbnew.PCB_TRACK(carte)
            piste.SetStart(centre)
            piste.SetEnd(via_centre)
            piste.SetWidth(mm(PISTE))
            piste.SetLayer(couche)
            piste.SetNet(plot.GetNet())
            piste.SetLocked(VERROUILLER)
            carte.Add(piste)
            return True
    return False


def main() -> None:
    global VERROUILLER
    VERROUILLER = len(sys.argv) > 1 and sys.argv[1] == 'avant'
    carte = pcbnew.LoadBoard(str(CARTE))
    for surface in carte.Zones():
        if not surface.GetIsRuleArea() and surface.GetFirstLayer() in (pcbnew.In1_Cu, pcbnew.In4_Cu):
            surface.SetLocalClearance(mm(0.1))
            surface.SetMinThickness(mm(0.1))
    pcbnew.ZONE_FILLER(carte).Fill(carte.Zones())
    connexions = carte.GetConnectivity()
    poses, echecs = 0, []
    for reseau in PLANS:
        elements = None
        for plot in [p for p in carte.GetPads() if p.GetNetname() == reseau and p.GetDrillSize().x == 0]:
            # Déjà relié : le plot touche un via ou une surface du réseau (piste, via ou plan).
            if any(item.GetClass() in ('PCB_VIA', 'ZONE') or (item.GetClass() == 'PCB_TRACK' and item.GetNetname() == reseau)
                   for item in connexions.GetConnectedItems(plot)):
                continue
            elements = elements or obstacles(carte, reseau)
            if relier(carte, plot, elements):
                poses += 1
            else:
                echecs.append(f'{plot.GetParentFootprint().GetReference()}.{plot.GetNumber()}')
        connexions.RecalculateRatsnest()
    pcbnew.ZONE_FILLER(carte).Fill(carte.Zones())
    carte.Save(str(CARTE))
    print(f'{poses} vias posés vers les plans ; sans place : {", ".join(echecs) or "aucun"}')
    print(f'connexions non routées : {carte.GetConnectivity().GetUnconnectedCount(True)}')


if __name__ == '__main__':
    main()
