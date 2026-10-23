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
JAVA = Path(r'C:\Program Files\Java\jdk-21.0.12\bin\java.exe')
FREEROUTING = Path(r'C:\Users\rachi\outils-pcb\freerouting-2.1.0.jar')


def main() -> None:
    passes = sys.argv[1] if len(sys.argv) > 1 else '30'
    TRAVAIL.mkdir(exist_ok=True)
    dsn, ses = TRAVAIL / 'carte.dsn', TRAVAIL / 'carte.ses'
    carte = pcbnew.LoadBoard(str(CARTE))
    if not pcbnew.ExportSpecctraDSN(carte, str(dsn)):
        raise SystemExit('export Specctra impossible')
    # Éventail (fanout) : chaque plot CMS sort vers un via, ce qui relie les plots de masse au plan de la couche 2 ;
    # l'optimiseur, très long sur une carte aussi dense, est laissé de côté (le DRC juge le résultat).
    commande = [str(JAVA), '-jar', str(FREEROUTING), '-de', str(dsn), '-do', str(ses), '-mp', passes, '-mt', '4',
                '--gui.enabled=false', '--router.fanout.enabled=true', '--router.optimizer.enabled=false']
    print(' '.join(commande), flush=True)
    with (TRAVAIL / 'freerouting.log').open('w', encoding='utf-8') as journal:
        subprocess.run(commande, stdout=journal, stderr=subprocess.STDOUT, text=True, timeout=5400)
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
