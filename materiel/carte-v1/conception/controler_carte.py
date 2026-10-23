"""Point de contrôle de l'étape 4 : règles propres au bracelet, mesurées sur le circuit routé.

À lancer avec l'interpréteur Python de KiCad, en complément du DRC (kicad-cli pcb drc --schematic-parity) :
    "C:\\Users\\rachi\\AppData\\Local\\Programs\\KiCad\\10.0\\bin\\python.exe" controler_carte.py
"""
from __future__ import annotations

import sys
from collections import defaultdict
from pathlib import Path

import pcbnew

CARTE = Path(__file__).resolve().parent.parent / 'kicad' / 'fasoguardian-v1.kicad_pcb'
RF = {'RF_LTE_MOD': 'LTE', 'RF_LTE_1': 'LTE', 'RF_LTE_2': 'LTE', 'RF_GNSS_MOD': 'GNSS', 'RF_GNSS_1': 'GNSS'}


def main() -> int:
    carte = pcbnew.LoadBoard(str(CARTE))
    erreurs = []
    longueurs: dict[str, float] = defaultdict(float)
    vias_rf: dict[str, int] = defaultdict(int)
    largeurs: dict[str, list[float]] = defaultdict(list)
    for piste in carte.GetTracks():
        nom = piste.GetNetname()
        if piste.GetClass() == 'PCB_VIA':
            if nom in RF:
                vias_rf[RF[nom]] += 1
            continue
        if nom in RF:
            longueurs[RF[nom]] += pcbnew.ToMM(piste.GetLength())
        largeurs[nom].append(pcbnew.ToMM(piste.GetWidth()))
    for voie, longueur in sorted(longueurs.items()):
        print(f'ligne RF {voie} : {longueur:.1f} mm, {vias_rf[voie]} via(s)')
        if longueur > 20:
            erreurs.append(f'ligne RF {voie} de {longueur:.1f} mm > 20 mm (SIMCom § 4.7)')
    for nom, minimum in (('VBAT_MODEM', 0.8), ('1V8', 0.3), ('VBAT', 0.4), ('VSYS', 0.4)):
        trop_fines = [l for l in largeurs.get(nom, []) if l < minimum - 1e-6]
        if trop_fines:
            erreurs.append(f'{nom} : {len(trop_fines)} segment(s) plus fins que {minimum} mm')
    non_routes = carte.GetConnectivity().GetUnconnectedCount(True)
    print(f'connexions non routées : {non_routes}')
    if non_routes:
        erreurs.append(f'{non_routes} connexion(s) non routée(s)')
    for message in erreurs:
        print('ERREUR', message)
    return 1 if erreurs else 0


if __name__ == '__main__':
    sys.exit(main())
