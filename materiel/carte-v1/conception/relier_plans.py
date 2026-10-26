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
# Isolement minimal (le plus grand des classes ordinaires, avec une marge) ; chaque paire de réseaux prend l'isolement
# que le DRC lui applique (isolement_paire).
VIA, PERCAGE, PISTE, ISOLEMENT = 0.3, 0.15, 0.2, 0.16
TROU = 0.2  # isolement d'un perçage (règle de la carte)
VIA_MODEM = (0.5, 0.25)  # pointes de 2 A du module : vias plus gros sur le plan du modem
VERROUILLER = False


def mm(v: float) -> int:
    return pcbnew.FromMM(v)


def isolement_paire(carte: pcbnew.BOARD, a: str, b: str) -> float:
    """Isolement (mm) que le DRC exige entre deux réseaux : le plus grand des deux classes, sauf la fente du guide
    coplanaire, de 0,15 mm entre une ligne RF et la masse ou une autre ligne RF (règles du projet,
    kicad/fasoguardian-v1.kicad_dru)."""
    classes = carte.GetDesignSettings().m_NetSettings
    classe = lambda n: classes.GetEffectiveNetClass(n).GetName() if n else ''
    isol = lambda n: pcbnew.ToMM(classes.GetEffectiveNetClass(n).GetClearance()) if n else TROU
    rf_a, rf_b = classe(a) == 'RF', classe(b) == 'RF'
    if (rf_a and (rf_b or b in ('GND', 'GNSS_BIAS'))) or (rf_b and a in ('GND', 'GNSS_BIAS')):
        return 0.15
    return max(isol(a), isol(b))


def obstacles(carte: pcbnew.BOARD, reseau: str):
    """Cuivre des autres réseaux et perçages de tous les réseaux : (forme, couche, isolement exigé) ; les vias et
    trous traversent toutes les couches. Un perçage du même réseau compte aussi : la distance entre deux perçages
    (TROU) vaut quel que soit leur réseau, et un via de plan ne doit pas tomber sur un via thermique."""
    elements = []
    marge = {}
    isol = lambda autre: marge.setdefault(autre, mm(max(ISOLEMENT, isolement_paire(carte, reseau, autre) + 0.01)))
    for plot in carte.GetPads():
        if plot.GetNetname() != reseau:
            for couche in CUIVRE:
                if plot.IsOnLayer(couche):
                    elements.append((plot.GetEffectiveShape(couche), couche, isol(plot.GetNetname())))
        if plot.GetDrillSize().x > 0:
            for couche in CUIVRE:
                elements.append((plot.GetEffectiveHoleShape(), couche, mm(TROU + 0.01)))
    for piste in carte.GetTracks():
        if piste.GetClass() == 'PCB_VIA':
            for couche in CUIVRE:
                elements.append((piste.GetEffectiveHoleShape(), couche, mm(TROU + 0.01)))
        if piste.GetNetname() == reseau:
            continue
        if piste.GetClass() == 'PCB_VIA':
            for couche in CUIVRE:
                elements.append((piste.GetEffectiveShape(couche), couche, isol(piste.GetNetname())))
        else:
            elements.append((piste.GetEffectiveShape(), piste.GetLayer(), isol(piste.GetNetname())))
    return elements


def libre(forme, couches, elements, marge: int) -> bool:
    """Aucun élément des couches données à moins de son isolement exigé (au moins `marge`)."""
    return not any(c in couches and f.Collide(forme, max(marge, m)) for f, c, m in elements)


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
    elements.append((via.GetEffectiveShape(pcbnew.F_Cu), pcbnew.F_Cu, mm(TROU)))


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


def degager_et_relier(carte: pcbnew.BOARD, plot: pcbnew.PAD) -> bool:
    """Après le routage, quand aucune place n'est libre : via dans la pastille, en retirant les pistes et vias non
    verrouillés des autres réseaux qui le gênent (finir.py les refait) ; la liaison au plan passe avant le signal.
    Rend False si la pastille ne peut pas le recevoir (trop petite, hors du plan, zone interdite, plot ou cuivre
    verrouillé dans le passage)."""
    reseau = plot.GetNetname()
    diametre, percage = VIA_MODEM if reseau == 'VBAT_MODEM' else (VIA, PERCAGE)
    centre = plot.GetPosition()
    if min(plot.GetSize().x, plot.GetSize().y) < mm(diametre) or not bord_libre(carte, centre) or             not sur_le_plan(carte, reseau, centre):
        return False
    cercle = pcbnew.SHAPE_CIRCLE(centre, mm(diametre / 2))
    marge = lambda autre: mm(max(ISOLEMENT, isolement_paire(carte, reseau, autre) + 0.01))
    for p in carte.GetPads():
        if p.GetNetname() != reseau and any(p.IsOnLayer(c) and p.GetEffectiveShape(c).Collide(cercle, marge(p.GetNetname()))
                                            for c in CUIVRE):
            return False
    genants = []
    for t in carte.GetTracks():
        if t.GetClass() == 'PCB_VIA':
            touche = t.GetEffectiveHoleShape().Collide(cercle, mm(TROU + 0.01)) or                 (t.GetNetname() != reseau and t.GetEffectiveShape(pcbnew.F_Cu).Collide(cercle, marge(t.GetNetname())))
        else:
            touche = t.GetNetname() != reseau and t.GetEffectiveShape().Collide(cercle, marge(t.GetNetname()))
        if touche:
            if t.IsLocked():
                return False
            genants.append(t)
    for t in genants:
        carte.Delete(t)
    poser_via(carte, plot, centre, diametre, percage, [])
    return True


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
    poses, echecs, degages = 0, [], []
    for reseau in PLANS:
        elements = None
        for plot in [p for p in carte.GetPads() if p.GetNetname() == reseau and p.GetDrillSize().x == 0]:
            # Déjà relié : le cuivre qui le touche, de proche en proche, atteint le plan du réseau (un îlot de
            # remplissage ou une piste isolée ne suffisent pas).
            if any(item.GetClass() == 'ZONE' and item.IsOnLayer(PLANS[reseau])
                   for item in connexions.GetConnectedItems(plot)):
                continue
            elements = elements or obstacles(carte, reseau)
            nom = f'{plot.GetParentFootprint().GetReference()}.{plot.GetNumber()}'
            if relier(carte, plot, elements):
                poses += 1
            elif not VERROUILLER and degager_et_relier(carte, plot):
                degages.append(nom)
                elements = None
            else:
                echecs.append(nom)
        connexions.RecalculateRatsnest()
    pcbnew.ZONE_FILLER(carte).Fill(carte.Zones())
    carte.Save(str(CARTE))
    print(f'{poses} vias posés vers les plans ; sans place : {", ".join(echecs) or "aucun"}')
    if degages:
        print(f'vias posés dans la pastille en dégageant le routage : {", ".join(degages)}')
    print(f'connexions non routées : {carte.GetConnectivity().GetUnconnectedCount(True)}')


if __name__ == '__main__':
    main()
