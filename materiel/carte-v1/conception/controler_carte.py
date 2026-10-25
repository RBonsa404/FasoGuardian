"""Point de contrôle de l'étape 4 : règles propres au bracelet, mesurées sur le circuit routé.

À lancer avec l'interpréteur Python de KiCad, en complément du DRC (kicad-cli pcb drc --schematic-parity) :
    "C:\\Users\\rachi\\AppData\\Local\\Programs\\KiCad\\10.0\\bin\\python.exe" controler_carte.py
"""
from __future__ import annotations

import sys
from collections import defaultdict
from pathlib import Path

import pcbnew

sys.path.insert(0, str(Path(__file__).resolve().parent))
from resistance import resistance  # noqa: E402

CARTE = Path(__file__).resolve().parent.parent / 'kicad' / 'fasoguardian-v1.kicad_pcb'
# Batterie → résistance de mesure R8 → interrupteur U6 → plots VBAT du module.
CHEMIN_DU_MODEM = (('VBAT', ['TP8.1'], ['R8.1']), ('VBAT_MES', ['R8.2'], ['U6.1', 'U6.2']),
                   ('VBAT_MODEM', ['U6.7', 'U6.8'], ['U2.55', 'U2.56', 'U2.57']))
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
    # 1V8 (VDD_EXT du module, 50 mA au plus) : classe à 0,3 mm, au moins 0,25 mm partout (plus de 0,5 A admissibles),
    # l'élargissement (elargir.py) laissant quelques segments à 0,275 mm entre deux obstacles.
    for nom, minimum in (('1V8', 0.25),):
        trop_fines = [l for l in largeurs.get(nom, []) if l < minimum - 1e-6]
        if trop_fines:
            erreurs.append(f'{nom} : {len(trop_fines)} segment(s) plus fins que {minimum} mm')
    # Chemin des pointes de 2 A du modem en GSM : résistance réelle du cuivre (pistes, vias et plans), et non largeur
    # de chaque segment. Budget : 30 mΩ de cuivre, soit 60 mV sous 2 A ; s'y ajoutent l'interrupteur U6 (16 mΩ typ.,
    # fiche TI) et la batterie. SIMCom demande que VBAT ne descende pas sous 3,4 V pendant une salve.
    pcbnew.ZONE_FILLER(carte).Fill(carte.Zones())
    total = 0.0
    for reseau, sources, puits in CHEMIN_DU_MODEM:
        r = resistance(carte, reseau, sources, puits)
        total += r
        print(f'chemin du modem, {reseau} {",".join(sources)} → {",".join(puits)} : {r * 1000:.1f} mΩ ({r * 2000:.0f} mV sous 2 A)')
    print(f'chemin du modem, cuivre total : {total * 1000:.1f} mΩ ({total * 2000:.0f} mV sous 2 A), '
          f'avec l\'interrupteur : {(total + 0.016) * 2000:.0f} mV')
    if total > 0.030:
        erreurs.append(f'chemin du modem : {total * 1000:.0f} mΩ de cuivre > 30 mΩ')
    for z in carte.Zones():
        if not z.GetIsRuleArea() and z.GetNetname() == 'VBAT' and z.GetFilledPolysList(z.GetFirstLayer()).OutlineCount() != 1:
            erreurs.append('plan VBAT coupé en plusieurs îlots')
    non_routes = carte.GetConnectivity().GetUnconnectedCount(True)
    print(f'connexions non routées : {non_routes}')
    if non_routes:
        erreurs.append(f'{non_routes} connexion(s) non routée(s)')
    for message in erreurs:
        print('ERREUR', message)
    return 1 if erreurs else 0


if __name__ == '__main__':
    sys.exit(main())
