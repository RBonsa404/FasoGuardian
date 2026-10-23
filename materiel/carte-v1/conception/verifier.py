"""Point de contrôle de l'étape 1 : règles de conception vérifiées sur la netlist de référence.

Usage : python verifier.py — sort en erreur si une règle n'est pas tenue.
"""
from __future__ import annotations

import re
import sys
from collections import defaultdict

import bibliotheques as bib
from carte import ADRESSES_I2C, ALIMENTATIONS, COMPOSANTS, RAILS_DECOUPLES

# Affectation des broches du STM32U575CIT6 : FG-DOC-08 tableau 8, avec les écarts de CAHIER.md § 5.
TABLEAU_8 = {
    'PA9': 'MCU_TX', 'PA10': 'MCU_RX', 'PA11': 'MCU_CTS', 'PA12': 'MCU_RTS',
    'PB5': 'RI_3V3', 'PB2': 'STATUS_3V3', 'PB6': 'I2C_SCL', 'PB7': 'I2C_SDA', 'PA15': 'ACC_INT1', 'PA1': 'CHG_INT',
    'PA2': 'GAUGE_ALRT', 'PA0': 'LOOP', 'PB12': 'TSC_CS', 'PB13': 'TSC_EL', 'PA8': 'SOS',
    'PA5': 'SPI_SCK', 'PA6': 'SPI_MISO', 'PA7': 'SPI_MOSI', 'PA4': 'FLASH_CS', 'PB8': 'FLASH_PWR_N',
    'PB0': 'LED_R', 'PB1': 'LED_G', 'PB10': 'LED_B', 'PB14': 'BUZZER', 'PA13': 'SWDIO', 'PA14': 'SWCLK',
    # Écarts justifiés (CAHIER.md § 5)
    'PB9': 'VIB', 'PC13': 'MCU_PWRKEY', 'PH1': 'MCU_RESET', 'PH0': 'MODEM_EN', 'PB4': 'MCU_DTR', 'PA3': 'NTC_MES',
    'PB3': 'NTC_EN', 'PB15': 'NETLIGHT_3V3',
}
# Fonctions alternatives exigées (fiche ST, symbole KiCad).
ALTERNATIVES = {
    'PA9': 'USART1_TX', 'PA10': 'USART1_RX', 'PA11': 'USART1_CTS', 'PA12': 'USART1_RTS', 'PB6': 'I2C1_SCL',
    'PB7': 'I2C1_SDA', 'PA5': 'SPI1_SCK', 'PA6': 'SPI1_MISO', 'PA7': 'SPI1_MOSI', 'PB12': 'TSC_G1_IO1',
    'PB13': 'TSC_G1_IO2', 'PB0': 'TIM3_CH3', 'PB1': 'TIM3_CH4', 'PB10': 'TIM2_CH3', 'PB14': 'TIM15_CH1',
    'PB9': 'TIM4_CH4', 'PA3': 'ADC1_IN8',
}

erreurs: list[str] = []
avertissements: list[str] = []


def erreur(message: str) -> None:
    erreurs.append(message)


par_ref = {c.ref: c for c in COMPOSANTS}
reseaux: dict[str, list[tuple[str, str]]] = defaultdict(list)

# 1. Symboles, empreintes et broches.
references = [c.ref for c in COMPOSANTS]
for ref in {r for r in references if references.count(r) > 1}:
    erreur(f'référence en double : {ref}')
for c in COMPOSANTS:
    try:
        broches = bib.broches_symbole(c.symbole)
        plots = bib.plots_empreinte(c.empreinte)
    except (KeyError, ValueError) as e:
        erreur(f'{c.ref} : {e}')
        continue
    for numero in c.broches:
        if numero not in broches:
            erreur(f'{c.ref} : broche {numero} absente du symbole {c.symbole}')
    for numero in broches:
        if numero not in c.broches and numero not in c.libres:
            erreur(f'{c.ref} : broche {numero} ({broches[numero]["nom"]}) ni reliée ni déclarée libre')
    for numero in plots:
        if numero not in broches and numero not in c.libres:
            erreur(f'{c.ref} : plot {numero} de {c.empreinte} sans broche')
    for numero in broches:
        if numero not in plots:
            erreur(f'{c.ref} : broche {numero} sans plot dans {c.empreinte}')
    for numero, reseau in c.broches.items():
        reseaux[reseau].append((c.ref, numero))

# 2. Réseaux à une seule extrémité (hors points de test volontaires).
for reseau, extremites in reseaux.items():
    if len(extremites) < 2:
        erreur(f'réseau {reseau} relié à une seule broche : {extremites}')

# 3. Tableau 8 et fonctions alternatives.
u1 = par_ref['U1']
noms_u1 = {b['nom']: n for n, b in bib.broches_symbole(u1.symbole).items()}
definition_u1 = bib.symbole(u1.symbole)
for port, reseau in TABLEAU_8.items():
    numero = noms_u1.get(port)
    if numero is None:
        erreur(f'{port} absente du boîtier LQFP48')
    elif u1.broches.get(numero) != reseau:
        erreur(f'{port} (broche {numero}) reliée à {u1.broches.get(numero)} au lieu de {reseau}')
for port, fonction in ALTERNATIVES.items():
    m = re.search(r'\(pin \w+ \w+\s*\(at [^)]*\)[^()]*(\([^()]*\)[^()]*)*?\(name "%s"' % port, definition_u1)
    debut = definition_u1.find(f'(name "{port}"')
    fin = definition_u1.find('(pin ', debut)
    if fonction not in definition_u1[debut:fin if fin > 0 else None]:
        erreur(f'{port} ne propose pas {fonction}')

# 4. Domaines de tension : aucune broche logique du module (1,8 V, 2,0 V au plus) sur un réseau du microcontrôleur.
u2 = par_ref['U2']
broches_u2 = bib.broches_symbole(u2.symbole)
for numero, reseau in u2.broches.items():
    nom = broches_u2[numero]['nom']
    if reseau in ALIMENTATIONS or nom in ('PWRKEY', 'RESET') or reseau.startswith(('RF_', 'SIM_', 'GNSS_', 'MOD_U3', 'MOD_MKIN3')):
        continue
    for ref, n in reseaux[reseau]:
        if ref == 'U1' or (par_ref[ref].symbole == 'Device:R' and '3V3' in par_ref[ref].broches.values()):
            erreur(f'U2.{numero} ({nom}, 1,8 V) relié au domaine 3,3 V par {ref}.{n}')
# PWRKEY et RESET : seulement le module et un drain de NMOS.
for reseau in ('MOD_PWRKEY', 'MOD_RESET'):
    if sorted(r for r, _ in reseaux[reseau]) != ['Q1', 'U2']:
        erreur(f'{reseau} doit relier seulement U2 et le drain de Q1 : {reseaux[reseau]}')
    for ref, _ in reseaux[reseau]:
        if par_ref[ref].symbole == 'Device:C':
            erreur(f'condensateur sur {reseau} (100 nF au plus, SIMCom § 3.2.1)')

# 5. Bus I²C : adresses distinctes, rappels présents.
if len(set(ADRESSES_I2C.values())) != len(ADRESSES_I2C):
    erreur(f'adresses I²C en conflit : {ADRESSES_I2C}')
for ligne in ('I2C_SCL', 'I2C_SDA'):
    if not any(par_ref[r].symbole == 'Device:R' and '3V3' in par_ref[r].broches.values() for r, _ in reseaux[ligne]):
        erreur(f'{ligne} sans résistance de rappel')


# 6. Capacités par rail.
def capacite(valeur: str) -> float:
    m = re.match(r'([\d,]+)\s*(pF|nF|µF)', valeur)
    if not m:
        return 0.0
    return float(m.group(1).replace(',', '.')) * {'pF': 1e-12, 'nF': 1e-9, 'µF': 1e-6}[m.group(2)]


def total(reseau: str) -> float:
    return sum(capacite(par_ref[r].valeur) for r, _ in reseaux[reseau]
               if par_ref[r].symbole in ('Device:C',) or 'T520' in par_ref[r].symbole if not par_ref[r].non_monte)


if total('VBAT_MODEM') < 300e-6:
    erreur(f'réservoir du modem {total("VBAT_MODEM") * 1e6:.0f} µF < 300 µF (SIMCom § 3.1.1)')
if total('VSYS') > 100e-6:
    erreur(f'capacité sur SYS {total("VSYS") * 1e6:.0f} µF > 100 µF (BQ25180)')
for valeur in ('33 pF', '10 pF', '100 nF', '1 µF'):
    if not any(par_ref[r].valeur == valeur for r, _ in reseaux['VBAT_MODEM']):
        erreur(f'découplage {valeur} absent au pied des plots VBAT du modem')
# Chaque circuit intégré a au moins un condensateur sur chacun de ses rails.
for c in COMPOSANTS:
    if not c.ref.startswith('U'):
        continue
    for reseau in {r for r in c.broches.values() if r in RAILS_DECOUPLES and r != 'GND'}:
        if not any(par_ref[r].symbole == 'Device:C' or 'T520' in par_ref[r].symbole for r, _ in reseaux[reseau]):
            erreur(f'{c.ref} : aucun condensateur sur {reseau}')

# 7. Faces : en face inférieure, pièces de moins de 1 mm de haut seulement.
HAUTES = ('fg:A7670E', 'fg:T520', 'fg:NANOSIM', 'fg:U.FL', 'fg:SM03B', 'fg:ECT8', 'Switch:', 'fg:DFE25', 'MCU_ST', 'fg:FC-B1010')
for c in COMPOSANTS:
    if c.face == 'B' and c.symbole.startswith(HAUTES):
        erreur(f'{c.ref} ({c.symbole}) trop haut pour la face inférieure')

print(f'{len(COMPOSANTS)} composants, {len(reseaux)} réseaux, {sum(len(v) for v in reseaux.values())} broches reliées')
print(f'réservoir modem {total("VBAT_MODEM") * 1e6:.0f} µF, capacité SYS {total("VSYS") * 1e6:.1f} µF')
for message in avertissements:
    print('AVERTISSEMENT', message)
for message in erreurs:
    print('ERREUR', message)
sys.exit(1 if erreurs else 0)
