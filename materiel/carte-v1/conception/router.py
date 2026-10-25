"""Étape 4 : routage automatique par Freerouting, puis remplissage des plans.

À lancer avec l'interpréteur Python de KiCad, après generer_carte.py :
    "C:\\Users\\rachi\\AppData\\Local\\Programs\\KiCad\\10.0\\bin\\python.exe" router.py [passes]
"""
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

import pcbnew

sys.path.insert(0, str(Path(__file__).resolve().parent))
from generer_carte import TOUT, zone  # noqa: E402

ICI = Path(__file__).resolve().parent
CARTE = ICI.parent / 'kicad' / 'fasoguardian-v1.kicad_pcb'
TRAVAIL = ICI.parent / 'kicad' / 'routage'
# Freerouting 2.5 exige Java 25 ; il est appelé par son chemin, sans toucher au Java du projet (JDK 21).
JAVA = Path(r'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot\bin\java.exe')
FREEROUTING = Path(r'C:\Users\rachi\outils-pcb\freerouting-2.5.0.jar')


def main() -> None:
    passes = sys.argv[1] if len(sys.argv) > 1 else '30'
    TRAVAIL.mkdir(exist_ok=True)
    dsn, ses = TRAVAIL / 'carte.dsn', TRAVAIL / 'carte.ses'
    carte = pcbnew.LoadBoard(str(CARTE))
    # Freerouting traite une zone interdite aux pistes comme un obstacle complet, vias compris : la règle du plan de
    # masse (toute la couche In1) lui interdirait tout via. L'export se fait donc depuis une copie sans ces règles ;
    # In1, de type « power », reste fermée aux pistes, et finir.py re-route ce qui serait tombé dans la zone du modem.
    copie = pcbnew.LoadBoard(str(CARTE))
    for regle in [z for z in copie.Zones() if z.GetIsRuleArea() and z.GetDoNotAllowTracks() and not z.GetDoNotAllowVias()]:
        copie.Delete(regle)
    if not pcbnew.ExportSpecctraDSN(copie, str(dsn)):
        raise SystemExit('export Specctra impossible')
    # Éventail (fanout) : chaque plot CMS sort vers un via, ce qui relie les plots de masse au plan de la couche 2 ;
    # l'optimiseur, très long sur une carte aussi dense, est laissé de côté (le DRC juge le résultat).
    commande = [str(JAVA), '-jar', str(FREEROUTING), '-de', str(dsn), '-do', str(ses), '-mp', passes, '-mt', '4',
                '--gui.enabled=false', '--router.fanout.enabled=true', '--router.optimizer.enabled=false',
                f'--router.max_passes={passes}', '--router.automatic_neckdown=false']
    print(' '.join(commande), flush=True)
    with (TRAVAIL / 'freerouting.log').open('w', encoding='utf-8') as journal:
        subprocess.run(commande, stdout=journal, stderr=subprocess.STDOUT, text=True, timeout=10800)
    if not ses.exists():
        raise SystemExit('Freerouting n\'a pas produit de session : voir routage/freerouting.log')
    if not pcbnew.ImportSpecctraSES(carte, str(ses)):
        raise SystemExit('import de la session impossible')
    # Masse coulée sur les faces externes, reliée au plan de la couche 2.
    masse = carte.FindNet('GND')
    for couche in (pcbnew.F_Cu, pcbnew.B_Cu):
        zone(carte, masse, couche, TOUT)
    pcbnew.ZONE_FILLER(carte).Fill(carte.Zones())
    carte.Save(str(CARTE))
    pistes = [t for t in carte.GetTracks() if t.GetClass() == 'PCB_TRACK']
    vias = [t for t in carte.GetTracks() if t.GetClass() == 'PCB_VIA']
    print(f'routage importé : {len(pistes)} segments, {len(vias)} vias')


if __name__ == '__main__':
    main()
