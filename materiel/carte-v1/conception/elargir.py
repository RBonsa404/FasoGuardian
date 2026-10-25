"""Finition des pistes d'alimentation : chaque segment plus fin que la largeur de sa classe est élargi au plus près
de cette largeur, là où l'isolement aux autres réseaux le permet (le routage a pu les rétrécir pour passer).

Les formes sont contrôlées par le moteur géométrique de KiCad ; le DRC de la chaîne valide ensuite le résultat.
À lancer avec l'interpréteur Python de KiCad, après finir.py :
    "C:\\Users\\rachi\\AppData\\Local\\Programs\\KiCad\\10.0\\bin\\python.exe" elargir.py
"""
from __future__ import annotations

from pathlib import Path

import pcbnew

CARTE = Path(__file__).resolve().parent.parent / 'kicad' / 'fasoguardian-v1.kicad_pcb'
MARGE = 0.01      # mm au-delà de l'isolement exigé
PAS = 0.025       # pas de réduction de la largeur essayée (mm)


def main() -> None:
    carte = pcbnew.LoadBoard(str(CARTE))
    reglages = carte.GetDesignSettings()
    classes = reglages.m_NetSettings
    defaut = pcbnew.ToMM(classes.GetDefaultNetclass().GetTrackWidth())
    largeur_de = lambda net: pcbnew.ToMM(classes.GetEffectiveNetClass(net).GetTrackWidth())
    isolement_de = lambda net: pcbnew.ToMM(classes.GetEffectiveNetClass(net).GetClearance())
    bord = carte.GetBoardEdgesBoundingBox()
    marge_bord = pcbnew.ToMM(reglages.m_CopperEdgeClearance) + 0.5  # bande interdite de 0,5 mm le long du bord
    # Cuivre de chaque couche, avec son réseau et son isolement.
    cuivre: dict[int, list] = {}
    for plot in carte.GetPads():
        for couche in plot.GetLayerSet().CuStack():
            cuivre.setdefault(couche, []).append((plot, plot.GetNetname()))
    for t in carte.GetTracks():
        couches = [t.GetLayer()] if t.GetClass() != 'PCB_VIA' else list(pcbnew.LSET.AllCuMask().CuStack())
        for couche in couches:
            cuivre.setdefault(couche, []).append((t, t.GetNetname()))
    interdits = [z for z in list(carte.Zones()) + [z for fp in carte.GetFootprints() for z in fp.Zones()]
                 if z.GetIsRuleArea() and z.GetDoNotAllowTracks()]
    elargis = 0
    for t in carte.GetTracks():
        # Les pistes verrouillées (lignes RF de 50 Ω, chemin du modem) gardent leur géométrie.
        if t.GetClass() != 'PCB_TRACK' or t.IsLocked():
            continue
        net = t.GetNetname()
        cible = largeur_de(net)
        actuelle = pcbnew.ToMM(t.GetWidth())
        if cible <= defaut + 1e-6 or actuelle >= cible - 1e-6:
            continue
        propre = isolement_de(net)
        voisins = [(o, n) for o, n in cuivre.get(t.GetLayer(), []) if n != net and o is not t]
        w = cible
        while w > actuelle + 1e-6:
            forme = pcbnew.SHAPE_SEGMENT(t.GetStart(), t.GetEnd(), pcbnew.FromMM(w))
            boite = forme.BBox()
            dans_bord = all(bord.GetLeft() + pcbnew.FromMM(marge_bord) <= v <= bord.GetRight() - pcbnew.FromMM(marge_bord)
                            for v in (boite.GetLeft(), boite.GetRight())) and \
                all(bord.GetTop() + pcbnew.FromMM(marge_bord) <= v <= bord.GetBottom() - pcbnew.FromMM(marge_bord)
                    for v in (boite.GetTop(), boite.GetBottom()))
            libre = dans_bord and not any(
                o.GetEffectiveShape(t.GetLayer()).Collide(forme, pcbnew.FromMM(max(propre, isolement_de(n) if n else 0.2) + MARGE))
                for o, n in voisins) and not any(
                z.IsOnLayer(t.GetLayer()) and z.Outline().Collide(forme.GetSeg(), pcbnew.FromMM(w / 2)) for z in interdits)
            if libre:
                t.SetWidth(pcbnew.FromMM(w))
                elargis += 1
                break
            w = round(w - PAS, 3)
    pcbnew.ZONE_FILLER(carte).Fill(carte.Zones())
    carte.Save(str(CARTE))
    print(f'{elargis} segment(s) élargi(s)')


if __name__ == '__main__':
    main()
