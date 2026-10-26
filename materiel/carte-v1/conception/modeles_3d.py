"""Modèles 3D de la carte : recopie ceux de la bibliothèque du projet, puis contrôle la hauteur des composants.

Une empreinte posée sur la carte garde le modèle 3D qu'elle avait au placement ; une correction faite ensuite dans
bibliotheque/fg.pretty (chemin relatif au projet, hauteur ramenée à la fiche du fabricant) est recopiée ici, sans
toucher à la géométrie ni au routage. La hauteur d'un composant est la cote maximale de la fiche de son fabricant
quand elle est relevée (FICHES), sinon le point le plus haut de son modèle 3D. La face inférieure, contre la batterie,
n'admet que 1,35 mm (CAHIER § 3) ; la pièce la plus haute de la face supérieure entre dans la cote du boîtier.
Usage (interpréteur Python de KiCad) : python.exe modeles_3d.py [carte.kicad_pcb]
"""
from __future__ import annotations

import os
import re
import sys
from pathlib import Path

import pcbnew

ICI = Path(__file__).resolve().parent
CARTE = ICI.parent / 'kicad' / 'fasoguardian-v1.kicad_pcb'
BIBLIOTHEQUE = ICI.parent / 'bibliotheque' / 'fg.pretty'
# 1,6 mm réservés entre la carte et la batterie : 1,35 mm de composants et 0,25 mm de mousse isolante (ADR 0042, point 12).
LIMITE_DESSOUS = 1.35
# Hauteur maximale relevée sur la fiche du fabricant (mm), par référence LCSC : pièces de la face inférieure de plus
# de 1 mm, dont le modèle 3D générique ne donne que la hauteur nominale.
FICHES = {
    'C123800': 1.35,  # SMF12A de MDD, boîtier SOD-123FL
    'C8598': 1.25,    # B5819W de CJ, boîtier SOD-123
    'C20917': 1.25,   # AO3400A d'AOS, boîtier SOT-23 (plan PO-00001)
    'C15127': 1.25,   # AO3401A d'AOS, même boîtier
    'C576403': 1.2,   # DFE252012F de Murata
    'C83571': 1.1,    # 2N7002DW de Diodes, boîtier SOT-363 (A1 + A2)
    'C48677': 1.1,    # ESDA6V1W5 de ST, boîtier SOT-323-5
}
# KiCad 10 ne livre pas de modèle pour la diode 0402 : on prend celui de la résistance 0402, de même boîtier.
SUBSTITUTS = {'${KICAD10_3DMODEL_DIR}/Diode_SMD.3dshapes/D_0402_1005Metric.step':
              '${KICAD10_3DMODEL_DIR}/Resistor_SMD.3dshapes/R_0402_1005Metric.step'}
DIXIEME_DE_POUCE = 2.54  # unité des modèles VRML de KiCad
VARIABLES = {'KIPRJMOD': str(ICI.parent / 'kicad'),
             'KICAD10_3DMODEL_DIR': r'C:\Users\rachi\AppData\Local\Programs\KiCad\10.0\share\kicad\3dmodels'}
POINTS_VRML = re.compile(r'point\s*\[(.*?)\]', re.S)
POINTS_STEP = re.compile(r"CARTESIAN_POINT\('[^']*',\(([^)]*)\)\)")
NOMBRE = re.compile(r'-?\d+(?:\.\d*)?(?:[eE][-+]?\d+)?')


def synchroniser(carte: pcbnew.BOARD) -> list[str]:
    """Remplace les modèles 3D des empreintes de la bibliothèque du projet par ceux de la bibliothèque et les modèles
    absents de KiCad par leur substitut ; rend la liste des repères modifiés."""
    modifies = []
    for fp in carte.GetFootprints():
        modeles = list(fp.Models())
        if any(m.m_Filename in SUBSTITUTS for m in modeles):
            fp.Models().clear()
            for modele in modeles:
                modele.m_Filename = SUBSTITUTS.get(modele.m_Filename, modele.m_Filename)
                fp.Models().push_back(modele)
            modifies.append(fp.GetReference())
        if str(fp.GetFPID().GetLibNickname()) != 'fg':
            continue
        source = pcbnew.FootprintLoad(str(BIBLIOTHEQUE), str(fp.GetFPID().GetLibItemName()))
        voulus = [(m.m_Filename, m.m_Scale.x, m.m_Scale.y, m.m_Scale.z, m.m_Offset.z) for m in source.Models()]
        actuels = [(m.m_Filename, m.m_Scale.x, m.m_Scale.y, m.m_Scale.z, m.m_Offset.z) for m in fp.Models()]
        if voulus == actuels:
            continue
        fp.Models().clear()
        for modele in source.Models():
            fp.Models().push_back(modele)
        modifies.append(fp.GetReference())
    return modifies


def hauteur(modele) -> float | None:
    """Point le plus haut du modèle, en mm : le VRML que montre la vue 3D s'il existe, sinon le STEP (seul format
    de la bibliothèque standard de KiCad 10)."""
    chemin = modele.m_Filename
    for nom, valeur in VARIABLES.items():
        chemin = chemin.replace('${' + nom + '}', valeur)
    chemin = Path(os.path.normpath(chemin))
    z, unite = [], 1.0
    if chemin.with_suffix('.wrl').exists():
        for bloc in POINTS_VRML.findall(chemin.with_suffix('.wrl').read_text(encoding='utf-8', errors='ignore')):
            z += [float(v) for v in NOMBRE.findall(bloc)][2::3]
        unite = DIXIEME_DE_POUCE
    elif chemin.with_suffix('.step').exists():
        for point in POINTS_STEP.findall(chemin.with_suffix('.step').read_text(encoding='utf-8', errors='ignore')):
            coordonnees = [float(v) for v in NOMBRE.findall(point)]
            if len(coordonnees) == 3:
                z.append(coordonnees[2])
    return max(z) * unite * modele.m_Scale.z + modele.m_Offset.z if z else None


def main() -> int:
    chemin = Path(sys.argv[1]) if len(sys.argv) > 1 else CARTE
    carte = pcbnew.LoadBoard(str(chemin))
    modifies = synchroniser(carte)
    if modifies:
        carte.Save(str(chemin))
        print(f'modèles 3D mis à jour : {", ".join(sorted(modifies))}')
    mesures, sans_modele, erreurs = [], [], []
    for fp in carte.GetFootprints():
        if fp.GetAttributes() & pcbnew.FP_EXCLUDE_FROM_BOM:
            continue
        h = FICHES.get(fp.GetFieldText('LCSC')) or max((v for v in (hauteur(m) for m in fp.Models()) if v is not None),
                                                       default=None)
        if h is None:
            sans_modele.append(fp.GetReference())
            continue
        face = 'inférieure' if fp.IsFlipped() else 'supérieure'
        mesures.append((face, h, fp.GetReference()))
        if fp.IsFlipped() and h > LIMITE_DESSOUS + 1e-3:
            erreurs.append(f'{fp.GetReference()} ({fp.GetValue()}) : {h:.2f} mm en face inférieure')
    for face in ('supérieure', 'inférieure'):
        hautes = sorted((m for m in mesures if m[0] == face), key=lambda m: -m[1])[:6]
        print(f'face {face} : ' + ', '.join(f'{ref} {h:.2f} mm' for _, h, ref in hautes))
    if sans_modele:
        print(f'sans modèle 3D : {", ".join(sorted(sans_modele))}')
    for e in erreurs:
        print('ERREUR', e)
    return 1 if erreurs else 0


if __name__ == '__main__':
    sys.exit(main())
