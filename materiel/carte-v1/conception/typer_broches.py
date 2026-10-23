"""Donne à chaque broche des symboles LCSC son type électrique, d'après la fiche du fabricant.

Les symboles importés de LCSC déclarent toutes leurs broches « non spécifiées » : l'ERC ne peut alors rien
vérifier. Ce script les type une fois pour toutes (entrée, sortie, alimentation…), puis KiCad normalise le fichier.

Usage : python typer_broches.py
"""
from __future__ import annotations

import re

import bibliotheques as bib

# Symbole → nom de broche (expression régulière) → type ; la première règle qui correspond s'applique.
TYPES: dict[str, list[tuple[str, str]]] = {
    'A7670E-FASE': [
        (r'^VBAT$', 'power_in'), (r'^GND$', 'power_in'), (r'^VDD_1V8$', 'power_out'), (r'^1V8_GNSS$', 'power_in'),
        (r'^USIM1_VDD$', 'power_out'), (r'^(RF_ANT|GNSS_ANT|BT_ANT|GNSS_VBKP|VBUS|ADC|VBAT_ADC|VDD_AUX)$', 'passive'),
        # PWRKEY et RESET : rappel interne à VBAT, tirés à la masse par un drain ouvert.
        (r'^(PWRKEY|RESET)$', 'passive'),
        (r'^(USB_BOOT|RXD|RTS|DTR|GNSS_RXD|UART3_RXD|UART_LOG_RX|GNSS_PWRCTL)$', 'input'),
        (r'^(TXD|CTS|RI|DCD|STATUS|NETLIGHT|GNSS_TXD|UART3_TXD|UART_LOG_TX|1PPS)$', 'output'),
        (r'^(USIM1_CLK|USIM1_RST)$', 'output'), (r'.*', 'bidirectional')],
    'BQ25180YBGR': [(r'^IN$', 'power_in'), (r'^SYS$', 'power_out'), (r'^BAT$', 'passive'), (r'^GND$', 'power_in'),
                    (r'^SCL$', 'input'), (r'^SDA$', 'bidirectional'), (r'/INT', 'open_collector'), (r'.*', 'passive')],
    'TPS63900DSKR': [(r'^VIN$', 'power_in'), (r'^(EN|SEL)$', 'input'), (r'^VOUT$', 'power_out'), (r'^(GND|EP)$', 'power_in'),
                     (r'.*', 'passive')],
    # Deux plots VOUT : un seul porte la sortie d'alimentation, l'autre lui est relié en interne.
    'TPS22965DSGR': [('#8', 'passive'), (r'^(VIN|VBIAS)$', 'power_in'), (r'^ON$', 'input'), (r'^VOUT$', 'power_out'),
                     (r'^(GND|EP)$', 'power_in'),
                     (r'.*', 'passive')],
    'MAX17048G+T10': [(r'^VDD$', 'power_in'), (r'^(GND|EP)$', 'power_in'), (r'^SCL$', 'input'), (r'^SDA$', 'bidirectional'),
                      (r'ALRT', 'open_collector'), (r'^QSTRT$', 'input'), (r'.*', 'passive')],
    'LIS2DW12TR': [(r'^(VDD|VDDIO|GND)$', 'power_in'), (r'^SCL', 'input'), (r'^SDA', 'bidirectional'), (r'^(CS|SDO/SA0)$', 'input'),
                   (r'^INT', 'output'), (r'^NC$', 'no_connect'), (r'.*', 'passive')],
    'ATECC608B-MAHDA-S': [(r'^(VCC|GND)$', 'power_in'), (r'^SDA$', 'bidirectional'), (r'^SCL$', 'input'), (r'^NC$', 'no_connect'),
                          (r'.*', 'passive')],
    # IO2 et IO3 servent ici de /WP et /HOLD (mode SPI simple) : des entrées.
    'W25Q64JVZPIQ': [(r'^(VCC|GND)$', 'power_in'), (r'CS|CLK|^DI', 'input'), (r'^DO', 'tri_state'), (r'^IO[23]$', 'input'),
                     (r'.*', 'passive')],
    'SN74AXC4T774BQBR': [(r'^(VCCA|VCCB|GND)$', 'power_in'), (r'OE|^DIR', 'input'), (r'^[AB]\d$', 'bidirectional'),
                         (r'.*', 'passive')],
    'NANOSIMTL6PH1.35': [(r'^VCC$', 'power_in'), (r'^(RST|CLK)$', 'input'), (r'^I/O$', 'bidirectional'), (r'.*', 'passive')],
}
# Pièces passives (inductance, connecteurs, quartz, protections, MOSFET, LED, condensateurs) : toutes leurs broches.
for passif in ('DFE252012F-2R2M', 'U.FL-R-SMT-1', 'SM03B-SRSS-TB', 'T520B337M006ATE040', 'FC-13532.7680KA-E', 'ECT818011998',
               'ESDBW5V0A1_C5173252', 'ESDA6V1W5', '2N7002DW-7-F', 'AO3401A', 'AO3400A', 'FC-B1010RGBT-HG'):
    TYPES[passif] = [(r'.*', 'passive')]


def main() -> None:
    chemin = bib.BIBLIO_FG / 'fg.kicad_sym'
    texte = chemin.read_text(encoding='utf-8')
    for symbole, regles in TYPES.items():
        m = re.search(r'\n\s*\(symbol "%s"\s' % re.escape(symbole), texte)
        debut = m.start() + texte[m.start():].index('(')
        definition = bib.bloc(texte, debut)

        morceaux, curseur = [], 0
        for broche in re.finditer(r'\(pin \w+ \w+\s*\(at ', definition):
            corps = bib.bloc(definition, broche.start())
            nom = re.search(r'\(name "([^"]*)"', corps).group(1)
            numero = re.search(r'\(number "([^"]*)"', corps).group(1)
            # « #n » vise une broche par son numéro, sinon le motif porte sur son nom.
            genre = next(t for motif, t in regles if (motif == f'#{numero}' if motif.startswith('#') else re.search(motif, nom)))
            morceaux += [definition[curseur:broche.start()], re.sub(r'^\(pin \w+', f'(pin {genre}', corps)]
            curseur = broche.start() + len(corps)
        nouvelle = ''.join(morceaux) + definition[curseur:]
        texte = texte[:debut] + nouvelle + texte[debut + len(definition):]
    chemin.write_text(texte, encoding='utf-8')
    print(f'{len(TYPES)} symboles typés')


if __name__ == '__main__':
    main()
