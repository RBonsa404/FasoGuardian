"""Netlist de référence de la carte V1 du bracelet FasoGuardian : la seule source de vérité.

Chaque composant y porte son symbole, son empreinte, sa valeur, sa référence fabricant, sa face d'assemblage et la
liaison de chacune de ses broches (par numéro de broche, contrôlé sur la fiche du fabricant). Le schéma KiCad et le
circuit imprimé sont générés à partir d'ici (`generer_schema.py`, `generer_carte.py`) ; les règles de conception sont
vérifiées par `verifier.py`. Les décisions et écarts au document de conception sont dans `../CAHIER.md`.
"""
from __future__ import annotations

from dataclasses import dataclass, field

# Bibliothèques : « fg » est la bibliothèque du projet (pièces LCSC contrôlées), les autres sont celles de KiCad.
R0402 = 'Resistor_SMD:R_0402_1005Metric'
R0603 = 'Resistor_SMD:R_0603_1608Metric'
C0402 = 'Capacitor_SMD:C_0402_1005Metric'
C0603 = 'Capacitor_SMD:C_0603_1608Metric'
D0402 = 'Diode_SMD:D_0402_1005Metric'
POINT_TEST = 'TestPoint:TestPoint_Pad_D1.0mm'
PASTILLE = 'TestPoint:TestPoint_Pad_1.5x1.5mm'
PASTILLE_CHARGE = 'TestPoint:TestPoint_Pad_2.5x2.5mm'

HAUT, BAS = 'F', 'B'


@dataclass
class Composant:
    ref: str
    symbole: str
    empreinte: str
    valeur: str
    broches: dict[str, str]
    feuille: str
    face: str = BAS
    fabricant: str = ''
    lcsc: str = ''
    non_monte: bool = False
    note: str = ''
    libres: tuple[str, ...] = field(default_factory=tuple)


COMPOSANTS: list[Composant] = []


def ajouter(*args, **kwargs) -> Composant:
    composant = Composant(*args, **kwargs)
    COMPOSANTS.append(composant)
    return composant


def resistance(ref, valeur, a, b, feuille, empreinte=R0402, **kw):
    return ajouter(ref, 'Device:R', empreinte, valeur, {'1': a, '2': b}, feuille, **kw)


def condensateur(ref, valeur, a, b, feuille, empreinte=C0402, **kw):
    return ajouter(ref, 'Device:C', empreinte, valeur, {'1': a, '2': b}, feuille, **kw)


def point_test(ref, reseau, feuille, empreinte=POINT_TEST, valeur=None, **kw):
    return ajouter(ref, 'Connector:TestPoint', empreinte, valeur or reseau, {'1': reseau}, feuille, **kw)


# ---------------------------------------------------------------------------------------------------- alimentation
F = 'alimentation'
# Connecteur magnétique de charge 5 V (§ 4.2, ligne 17) : deux pastilles reliées au connecteur du boîtier.
point_test('TP1', 'VIN_EXT', F, PASTILLE_CHARGE, 'Charge +5 V')
point_test('TP2', 'GND', F, PASTILLE_CHARGE, 'Charge masse')
ajouter('F1', 'Device:Polyfuse', 'Fuse:Fuse_0603_1608Metric', '0,5 A', {'1': 'VIN_EXT', '2': 'VIN_F'}, F,
        fabricant='0603L050YR', lcsc='C207015',
        note="Fusible réarmable : une alimentation branchée à l'envers fait conduire D1 et déclenche F1 ; "
             "courant de charge réglé à 300 mA (0,5 C), sous le maintien de 500 mA du fusible")
ajouter('D1', 'Device:D_Zener', 'Diode_SMD:D_SOD-123F', 'SMF12A', {'1': 'VIN_F', '2': 'GND'}, F, fabricant='SMF12A', lcsc='C123800',
        note='Écrêteur du connecteur de charge (§ 4.3) : 12 V, sous les 25 V admis par le BQ25180')
ajouter('D15', 'Device:D_Schottky', 'Diode_SMD:D_SOD-123', 'B5819W', {'1': 'VIN', '2': 'VIN_F'}, F, fabricant='B5819W SL', lcsc='C8598',
        note="Protection contre l'inversion de polarité du connecteur magnétique")
condensateur('C1', '2,2 µF 25 V', 'VIN', 'GND', F, C0603)
# Chargeur BQ25180 (DSBGA-8) : A1 /INT, A2 IN, B1 SCL, B2 SYS, C1 SDA, C2 BAT, D1 TS/MR, D2 GND.
ajouter('U4', 'fg:BQ25180YBGR', 'fg:DSBGA-8_L1.6-W0.9-R2-C4-P0.40-BL', 'BQ25180YBGR',
        {'A1': 'CHG_INT', 'A2': 'VIN', 'B1': 'I2C_SCL', 'B2': 'VSYS', 'C1': 'I2C_SDA', 'C2': 'VBAT', 'D1': 'BAT_NTC', 'D2': 'GND'},
        F, fabricant='BQ25180YBGR', lcsc='C3682423')
condensateur('C2', '10 µF', 'VSYS', 'GND', F, C0603, note='SYS : 10 µF au pied, 100 µF au plus sur tout le rail (fiche TI)')
condensateur('C3', '1 µF', 'VBAT', 'GND', F)
# Batterie Li-Po 600 mAh avec PCM et NTC 10 kΩ (§ 4.2, ligne 15) : JST SH 3 contacts.
# Batterie soudée sur trois pastilles : un JST SH (1 A par contact) ne tient pas les pointes de 2 A du modem.
point_test('TP8', 'VBAT', F, PASTILLE_CHARGE, 'Batterie +', note="Point d'insertion de l'analyseur de consommation")
point_test('TP9', 'GND', F, PASTILLE_CHARGE, 'Batterie −')
point_test('TP17', 'BAT_NTC', F, PASTILLE, 'NTC batterie')
# Régulateur 3,3 V TPS63900 : SEL haut, VO(2) = 3,3 V par CFG1 = 36,5 kΩ et CFG2 = 0 Ω (sans limite de courant),
# VO(1) = 3,3 V par CFG3 = 16,2 kΩ ; les deux réglages donnent 3,3 V.
ajouter('U5', 'fg:TPS63900DSKR', 'fg:WSON-10_L2.5-W2.5-W0.50-TL-EP', 'TPS63900DSKR',
        {'1': 'VSYS', '2': 'VSYS', '3': 'TPS_CFG1', '4': 'GND', '5': 'TPS_CFG3', '6': '3V3_REG', '7': 'TPS_LX2', '8': 'GND',
         '9': 'TPS_LX1', '10': 'VSYS', '11': 'GND'}, F, fabricant='TPS63900DSKR', lcsc='C1518762')
resistance('R2', '36,5 kΩ 1 %', 'TPS_CFG1', 'GND', F)
resistance('R4', '16,2 kΩ 1 %', 'TPS_CFG3', 'GND', F)
ajouter('L1', 'fg:DFE252012F-2R2M', 'fg:L1008', '2,2 µH', {'1': 'TPS_LX1', '2': 'TPS_LX2'}, F, face=HAUT,
        fabricant='DFE252012F-2R2M', lcsc='C576403')
condensateur('C4', '10 µF', 'VSYS', 'GND', F, C0603)
condensateur('C5', '22 µF', '3V3_REG', 'GND', F, C0603)
resistance('R5', '0 Ω', '3V3_REG', '3V3', F, R0603, note='Coupable : mesure du courant du rail 3,3 V (§ 4.3)')
# Interrupteur du modem TPS22965 (ADR 0042) : VIN, VBIAS depuis la batterie, ON rappelé à l'état bloqué.
resistance('R8', '0 Ω', 'VBAT', 'VBAT_MES', F, 'Resistor_SMD:R_1206_3216Metric',
           note='Coupable : mesure du courant du modem (§ 4.3) ; 1206 pour les pointes de 2 A')
ajouter('U6', 'fg:TPS22965DSGR', 'fg:UDFN-8_L2.0-W2.0-P0.50-TL-EP', 'TPS22965DSGR',
        {'1': 'VBAT_MES', '2': 'VBAT_MES', '3': 'MODEM_EN', '4': 'VBAT_MES', '5': 'GND', '6': 'MODEM_CT', '7': 'VBAT_MODEM',
         '8': 'VBAT_MODEM', '9': 'GND'}, F, fabricant='TPS22965DSGR', lcsc='C122837')
condensateur('C6', '10 nF', 'MODEM_CT', 'GND', F,
             note='Montée en 13 ms environ (fiche TI, tableau 1) : appel de 0,2 A dans le réservoir de 660 µF')
resistance('R6', '100 kΩ', 'MODEM_EN', 'GND', F)
condensateur('C7', '10 µF', 'VBAT_MES', 'GND', F, C0603)
# Jauge MAX17048 : alimentée et mesurée sur la batterie (prise de tension au connecteur).
ajouter('U7', 'fg:MAX17048G+T10', 'fg:TDFN-8_L2.0-W2.0-P0.50-BL-EP1.2', 'MAX17048G+T10',
        {'1': 'GND', '2': 'VBAT', '3': 'VBAT', '4': 'GND', '5': 'GAUGE_ALRT', '6': 'GND', '7': 'I2C_SCL', '8': 'I2C_SDA', '9': 'GND'},
        F, fabricant='MAX17048G+T10', lcsc='C2682616')
condensateur('C8', '100 nF', 'VBAT', 'GND', F)
# Thermistance de carte (surveillance continue, § 5.4) : pont alimenté par PB3 pendant la mesure, lu sur PA3.
resistance('R7', '10 kΩ 1 %', 'NTC_EN', 'NTC_MES', F)
ajouter('RT1', 'Device:Thermistor_NTC', R0402, '10 kΩ B3435', {'1': 'NTC_MES', '2': 'GND'}, F, fabricant='NCP15XH103F03RC',
        lcsc='C77131')
point_test('TP3', 'VBAT', F)
point_test('TP4', 'VSYS', F)
point_test('TP5', '3V3', F)
point_test('TP6', 'VBAT_MODEM', F)
point_test('TP7', 'GND', F)

# ----------------------------------------------------------------------------------------------------------- modem
F = 'modem'
GND_MODULE = ['2', '17', '18', '29', '39', '45', '46', '54', '58', '59', '61', '62', '63', '64', '65', '89', '91', '92', '94',
              *[str(n) for n in range(69, 89)]]
modem = {n: 'GND' for n in GND_MODULE}
modem.update({
    '55': 'VBAT_MODEM', '56': 'VBAT_MODEM', '57': 'VBAT_MODEM',
    '1': 'MOD_PWRKEY', '16': 'MOD_RESET', '6': 'MOD_USB_BOOT',
    '9': 'MOD_TXD', '10': 'MOD_RXD', '7': 'MOD_CTS', '8': 'MOD_RTS', '3': 'MOD_DTR', '4': 'MOD_RI', '66': 'MOD_STATUS',
    '52': 'MOD_NETLIGHT', '15': '1V8', '97': '1V8',
    '98': 'GNSS_PWRCTL', '20': 'MOD_MKIN3', '95': 'GNSS_TXD', '49': 'MOD_U3RXD', '96': 'GNSS_RXD', '50': 'MOD_U3TXD',
    '116': 'GNSS_VBKP', '90': 'RF_GNSS_MOD', '60': 'RF_LTE_MOD', '99': 'MOD_VDD_AUX',
    '30': 'SIM_VDD', '31': 'SIM_DATA_M', '32': 'SIM_CLK_M', '33': 'SIM_RST_M',
    '24': 'MOD_VBUS', '27': 'MOD_DP', '28': 'MOD_DM', '23': 'MOD_LOG_TX', '22': 'MOD_LOG_RX',
})
# Broches non utilisées laissées libres (« If unused, keep it open », SIMCom tableau 6) : audio, clavier, LCD,
# caméra, SPI, I²C, seconde SIM, DCD, ADC, GPIO, 1PPS, VDD_AUX, BT_ANT.
LIBRES_MODULE = tuple(str(n) for n in range(1, 125) if str(n) not in modem)
ajouter('U2', 'fg:A7670E-FASE', 'fg:LGA-124_L24.0-W24.0-P1.0-A7670C-LASS', 'A7670E (variante GNSS)', modem, F, face=HAUT,
        fabricant='A7670E-FASE', lcsc='C7435296', libres=LIBRES_MODULE,
        note='Variante GNSS à confirmer auprès de SIMCom avant commande')
# Réservoir et découplage au pied des plots VBAT (SIMCom § 3.1.1).
ajouter('C10', 'fg:T520B337M006ATE040', 'fg:CAP-SMD_L3.5-W2.8-R-RD', '330 µF 6,3 V', {'1': 'VBAT_MODEM', '2': 'GND'}, F,
        face=HAUT, fabricant='T520B337M006ATE040', lcsc='C696806')
ajouter('C11', 'fg:T520B337M006ATE040', 'fg:CAP-SMD_L3.5-W2.8-R-RD', '330 µF 6,3 V', {'1': 'VBAT_MODEM', '2': 'GND'}, F,
        face=HAUT, fabricant='T520B337M006ATE040', lcsc='C696806')
condensateur('C12', '1 µF', 'VBAT_MODEM', 'GND', F, face=HAUT)
condensateur('C13', '100 nF', 'VBAT_MODEM', 'GND', F, face=HAUT)
condensateur('C14', '33 pF', 'VBAT_MODEM', 'GND', F, face=HAUT)
condensateur('C15', '10 pF', 'VBAT_MODEM', 'GND', F, face=HAUT)
ajouter('D2', 'fg:ESDBW5V0A1_C5173252', 'fg:X1-DFN1006-2_L1.0-W0.6-P0.65-BI', 'ESDBW5V0A1', {'1': 'VBAT_MODEM', '2': 'GND'}, F,
        face=HAUT, fabricant='ESDBW5V0A1', lcsc='C5173252', note='TVS VBAT recommandée par SIMCom (tableau 9)')
# PWRKEY et RESET : NMOS à drain ouvert, grilles rappelées à la masse.
ajouter('Q1', 'fg:2N7002DW-7-F', 'fg:SOT-363_L2.0-W1.3-P0.65-LS2.1-TL', '2N7002DW',
        {'3': 'MOD_PWRKEY', '5': 'PWRKEY_G', '4': 'GND', '6': 'MOD_RESET', '2': 'RESET_G', '1': 'GND'}, F,
        fabricant='2N7002DW-7-F', lcsc='C83571')
resistance('R10', '1 kΩ', 'MCU_PWRKEY', 'PWRKEY_G', F)
resistance('R11', '100 kΩ', 'PWRKEY_G', 'GND', F)
resistance('R12', '1 kΩ', 'MCU_RESET', 'RESET_G', F)
resistance('R13', '100 kΩ', 'RESET_G', 'GND', F)
# GNSS non autonome (SIMCom figure 28).
resistance('R14', '10 kΩ', 'GNSS_PWRCTL', 'MOD_MKIN3', F)
resistance('R15', '1 kΩ', 'GNSS_TXD', 'MOD_U3RXD', F)
resistance('R16', '1 kΩ', 'MOD_U3TXD', 'GNSS_RXD', F)
resistance('R17', '0 Ω', '3V3', 'GNSS_VBKP', F, non_monte=True,
           note='Option : démarrage à chaud matériel sur le 3,3 V permanent (1,4 à 3,6 V admis ; ≈ 1 mA)')
condensateur('C16', '1 µF', '1V8', 'GND', F, face=HAUT, note='Au pied de 1V8_GNSS (plot 97)')
condensateur('C17', '100 nF', '1V8', 'GND', F, face=HAUT, note='Au pied de VDD_1V8 (plot 15)')
for ref, reseau in [('TP10', 'MOD_USB_BOOT'), ('TP11', 'MOD_VBUS'), ('TP12', 'MOD_DP'), ('TP13', 'MOD_DM'),
                    ('TP14', 'MOD_LOG_TX'), ('TP15', 'MOD_LOG_RX'), ('TP16', '1V8')]:
    point_test(ref, reseau, F, note='Mise à jour et mise au point du module (SIMCom § 2.2)')

# ----------------------------------------------------------------------------------------------------------- radio
F = 'radio'
# LTE : π d'adaptation (série 0 Ω monté, parallèles non montés), ESD, commutateur de test conduit, U.FL.
condensateur('C20', 'NM', 'RF_LTE_MOD', 'GND', F, face=HAUT, non_monte=True, note='Adaptation : valeur donnée par l\'accord de l\'antenne')
resistance('R20', '0 Ω', 'RF_LTE_MOD', 'RF_LTE_1', F, face=HAUT)
condensateur('C21', 'NM', 'RF_LTE_1', 'GND', F, face=HAUT, non_monte=True)
ajouter('D4', 'Device:D_TVS', D0402, 'LXES15AAA1-153', {'1': 'RF_LTE_1', '2': 'GND'}, F, face=HAUT, fabricant='LXES15AAA1-153',
        lcsc='C5210978', note='ESD de 0,05 pF : le PESD0402-03 de SIMCom (tableau 40) est en rupture')
ajouter('J6', 'fg:ECT818011998', 'fg:IPEX-SMD_ECT818011998', 'ECT 818011998', {'1': 'RF_LTE_1', '2': 'RF_LTE_2', '3': 'GND', '4': 'GND'},
        F, face=HAUT, fabricant='ECT818011998', lcsc='C437250', note='Commutateur de mesure conduite (SIMCom § 4.7)')
ajouter('J3', 'fg:U.FL-R-SMT-1', 'fg:RF-SMD_FRF05002-JSS103M', 'Antenne LTE', {'1': 'RF_LTE_2', '2': 'GND', '3': 'GND'}, F,
        face=HAUT, fabricant='U.FL-R-SMT-1(80)', lcsc='C88374', note='Antenne FPC LTE 698–2700 MHz le long d\'un flanc')
# GNSS (SIMCom figure 34) : condensateur série qui arrête le continu, puis té de polarisation : l'antenne active est
# alimentée par la sortie auxiliaire du module (VDD_AUX, plot 99), réglée par AT+CVAUXV et coupée par AT+CVAUXS=0.
condensateur('C22', 'NM', 'RF_GNSS_MOD', 'GND', F, face=HAUT, non_monte=True)
condensateur('C24', '33 pF', 'RF_GNSS_MOD', 'RF_GNSS_1', F, face=HAUT, note='Série : arrête le continu vers le module')
ajouter('L2', 'Device:L', 'Inductor_SMD:L_0402_1005Metric', '68 nH', {'1': 'RF_GNSS_1', '2': 'GNSS_BIAS'}, F, face=HAUT,
        fabricant='LQW15AN68NG00D', lcsc='C108965', note='Choc de polarisation (> 56 nH, SIMCom) : bobinée pour sa résonance propre')
resistance('R23', '10 Ω', 'GNSS_BIAS', 'MOD_VDD_AUX', F)
condensateur('C25', '100 nF', 'MOD_VDD_AUX', 'GND', F)
condensateur('C23', 'NM', 'RF_GNSS_1', 'GND', F, face=HAUT, non_monte=True)
ajouter('D5', 'Device:D_TVS', D0402, 'LXES15AAA1-153', {'1': 'RF_GNSS_1', '2': 'GND'}, F, face=HAUT, fabricant='LXES15AAA1-153',
        lcsc='C5210978', note="Tient les 3 V d'alimentation de l'antenne active")
ajouter('J4', 'fg:U.FL-R-SMT-1', 'fg:RF-SMD_FRF05002-JSS103M', 'Antenne GNSS active', {'1': 'RF_GNSS_1', '2': 'GND', '3': 'GND'}, F,
        face=HAUT, fabricant='U.FL-R-SMT-1(80)', lcsc='C88374', note='Patch céramique actif sous le couvercle, sans métal au-dessus')

# ------------------------------------------------------------------------------------------------------------- SIM
F = 'sim'
ajouter('J5', 'fg:NANOSIMTL6PH1.35', 'fg:SIM-SMD_NANO-SIM-TL6P-H1.35', 'Nano-SIM',
        {'1': 'SIM_VDD', '2': 'SIM_RST', '3': 'SIM_CLK', '5': 'GND', '7': 'SIM_DATA', '8': 'GND', '9': 'GND'}, F, face=HAUT,
        fabricant='NANO SIM TL6P H1.35', lcsc='C7529385', libres=('6',), note='VPP (6) non connecté')
resistance('R30', '22 Ω', 'SIM_RST_M', 'SIM_RST', F)
resistance('R31', '22 Ω', 'SIM_CLK_M', 'SIM_CLK', F)
resistance('R32', '22 Ω', 'SIM_DATA_M', 'SIM_DATA', F)
condensateur('C30', '100 nF', 'SIM_VDD', 'GND', F)
condensateur('C31', '33 pF', 'SIM_RST', 'GND', F)
condensateur('C32', '33 pF', 'SIM_CLK', 'GND', F)
condensateur('C33', '33 pF', 'SIM_DATA', 'GND', F)
ajouter('U12', 'fg:ESDA6V1W5', 'fg:SC-70-5_L2.1-W1.3-P0.65-LS2.1-BR', 'ESDA6V1W5',
        {'1': 'SIM_VDD', '2': 'GND', '3': 'SIM_RST', '4': 'SIM_CLK', '5': 'SIM_DATA'}, F, fabricant='ESDA6V1W5', lcsc='C48677',
        note='Protection ESD au pied du porte-SIM (SIMCom § 3.5.1)')

# --------------------------------------------------------------------------------------------------- microcontrôleur
F = 'microcontroleur'
ajouter('U1', 'MCU_ST_STM32U5:STM32U575CITx', 'Package_QFP:LQFP-48_7x7mm_P0.5mm', 'STM32U575CIT6', {
    '1': '3V3', '2': 'MCU_PWRKEY', '3': 'OSC32_IN', '4': 'OSC32_OUT', '5': 'MODEM_EN', '6': 'MCU_RESET', '7': 'NRST',
    '8': 'GND', '9': '3V3', '10': 'LOOP', '11': 'CHG_INT', '12': 'GAUGE_ALRT', '13': 'NTC_MES', '14': 'FLASH_CS',
    '15': 'SPI_SCK', '16': 'SPI_MISO', '17': 'SPI_MOSI', '18': 'LED_R', '19': 'LED_G', '20': 'STATUS_3V3', '21': 'LED_B',
    '22': 'VCAP', '23': 'GND', '24': '3V3', '25': 'TSC_CS', '26': 'TSC_EL', '27': 'BUZZER', '28': 'NETLIGHT_3V3',
    '29': 'SOS', '30': 'MCU_TX', '31': 'MCU_RX', '32': 'MCU_CTS', '33': 'MCU_RTS', '34': 'SWDIO', '35': 'GND', '36': '3V3',
    '37': 'SWCLK', '38': 'ACC_INT1', '39': 'NTC_EN', '40': 'MCU_DTR', '41': 'RI_3V3', '42': 'I2C_SCL', '43': 'I2C_SDA',
    '44': 'BOOT0', '45': 'FLASH_PWR_N', '46': 'VIB', '47': 'GND', '48': '3V3'}, F, face=HAUT, fabricant='STM32U575CIT6',
    lcsc='C5271014')
for ref in ('C40', 'C41', 'C42'):
    condensateur(ref, '100 nF', '3V3', 'GND', F, note='Un par broche VDD')
condensateur('C43', '4,7 µF', '3V3', 'GND', F)
condensateur('C44', '100 nF', '3V3', 'GND', F, note='VDDA')
condensateur('C45', '1 µF', '3V3', 'GND', F, note='VDDA')
condensateur('C46', '100 nF', '3V3', 'GND', F, note='VBAT du microcontrôleur')
condensateur('C47', '4,7 µF', 'VCAP', 'GND', F, note='VCAP : céramique X5R à faible ESR (fiche ST)')
condensateur('C48', '100 nF', 'NRST', 'GND', F)
resistance('R40', '10 kΩ', 'BOOT0', 'GND', F)
ajouter('Y1', 'fg:FC-13532.7680KA-E', 'fg:CRYSTAL-SMD_L3.2-W1.5-FC-135R32.7680KF-A3', '32,768 kHz', {'1': 'OSC32_IN', '2': 'OSC32_OUT'},
        F, fabricant='FC-135 32.7680KA-E', lcsc='C20340322', note='RTC : horodatage des positions en mode Stop')
condensateur('C49', '18 pF', 'OSC32_IN', 'GND', F, note='C = 2 × (CL − Cparasite) pour CL = 12,5 pF')
condensateur('C50', '18 pF', 'OSC32_OUT', 'GND', F)
ajouter('J7', 'Connector:Conn_ARM_SWD_TagConnect_TC2030', 'Connector:Tag-Connect_TC2030-IDC-NL_2x03_P1.27mm_Vertical',
        'SWD Tag-Connect', {'1': '3V3', '2': 'SWDIO', '3': 'NRST', '4': 'SWCLK', '5': 'GND'}, F, libres=('6',),
        note='Programmation sans connecteur (§ 4.3) ; SWO (6) non relié, PB3 sert à la thermistance')
point_test('TP20', 'BOOT0', F)
for ref, reseau in [('TP40', 'I2C_SCL'), ('TP41', 'I2C_SDA'), ('TP42', 'MCU_TX'), ('TP43', 'MCU_RX'), ('TP44', 'MODEM_EN'),
                    ('TP45', 'STATUS_3V3')]:
    point_test(ref, reseau, F, note='Mise au point')

# ------------------------------------------------------------------------------------------------- capteurs et clés
F = 'capteurs'
# LIS2DW12 en I²C : CS au 3,3 V, SDO/SA0 à la masse → adresse 0x18 (tableau 8).
ajouter('U3', 'fg:LIS2DW12TR', 'fg:LGA-12_L2.0-W2.0-P0.50-BL', 'LIS2DW12TR', {
    '1': 'I2C_SCL', '2': '3V3', '3': 'GND', '4': 'I2C_SDA', '6': 'GND', '7': 'GND', '8': 'GND', '9': '3V3', '10': '3V3',
    '12': 'ACC_INT1'}, F, fabricant='LIS2DW12TR', lcsc='C189624', libres=('5', '11'))
condensateur('C60', '100 nF', '3V3', 'GND', F)
condensateur('C61', '1 µF', '3V3', 'GND', F)
# ATECC608B (UDFN-8) : adresse 0x60 (réglage d'usine de la variante MAHDA).
ajouter('U8', 'fg:ATECC608B-MAHDA-S', 'fg:UDFN-8_L3.0-W2.0-P0.50-TL', 'ATECC608B-MAHDA',
        {'4': 'GND', '5': 'I2C_SDA', '6': 'I2C_SCL', '8': '3V3', '9': 'GND'}, F, fabricant='ATECC608B-MAHDA-S', lcsc='C1518773',
        libres=('1', '2', '3', '7'))
condensateur('C62', '100 nF', '3V3', 'GND', F)
resistance('R60', '4,7 kΩ', 'I2C_SCL', '3V3', F)
resistance('R61', '4,7 kΩ', 'I2C_SDA', '3V3', F)
resistance('R62', '10 kΩ', 'CHG_INT', '3V3', F)
resistance('R63', '10 kΩ', 'GAUGE_ALRT', '3V3', F)
# Mémoire W25Q64 : alimentation coupée entre deux écritures (§ 4.2, ligne 13) par un PMOS commandé par PB8.
ajouter('Q2', 'fg:AO3401A', 'fg:SOT-23_L2.9-W1.3-P1.90-LS2.4-BR', 'AO3401A', {'1': 'FLASH_PWR_N', '2': '3V3', '3': '3V3_FLASH'}, F,
        fabricant='AO3401A', lcsc='C15127')
resistance('R64', '100 kΩ', 'FLASH_PWR_N', '3V3', F, note='Mémoire coupée par défaut')
ajouter('U9', 'fg:W25Q64JVZPIQ', 'fg:WSON-8_L6.0-W5.0-P1.27-BL-EP', 'W25Q64JVZPIQ', {
    '1': 'FLASH_CS', '2': 'SPI_MISO', '3': '3V3_FLASH', '4': 'GND', '5': 'SPI_MOSI', '6': 'SPI_SCK', '7': '3V3_FLASH',
    '8': '3V3_FLASH', '9': 'GND'}, F, fabricant='W25Q64JVZPIQ', lcsc='C2940197')
condensateur('C63', '100 nF', '3V3_FLASH', 'GND', F)
resistance('R65', '10 kΩ', 'FLASH_CS', '3V3_FLASH', F)

# --------------------------------------------------------------------------------------------- translateurs de niveau
F = 'niveaux'
# SN74AXC4T774 : DIRx haut = A vers B ; côté A = 1,8 V du module, côté B = 3,3 V du microcontrôleur ; /OE à la masse.
ajouter('U10', 'fg:SN74AXC4T774BQBR', 'fg:DHVQFN-16_L3.5-W2.5-P0.50-BL-EP', 'SN74AXC4T774', {
    '16': '1V8', '15': '3V3', '10': 'GND', '17': 'GND', '9': 'GND',
    '3': 'MOD_RXD', '14': 'MCU_TX', '1': 'GND',
    '4': 'MOD_RTS', '13': 'MCU_RTS', '2': 'GND',
    '5': 'MOD_DTR', '12': 'MCU_DTR', '7': 'GND',
    '6': 'MOD_TXD', '11': 'MCU_RX', '8': '1V8'}, F, fabricant='SN74AXC4T774BQBR', lcsc='C2862903',
    note='Voies 1 à 3 : microcontrôleur vers module ; voie 4 : module vers microcontrôleur')
ajouter('U11', 'fg:SN74AXC4T774BQBR', 'fg:DHVQFN-16_L3.5-W2.5-P0.50-BL-EP', 'SN74AXC4T774', {
    '16': '1V8', '15': '3V3', '10': 'GND', '17': 'GND', '9': 'GND',
    '3': 'MOD_CTS', '14': 'MCU_CTS', '1': '1V8',
    '4': 'MOD_RI', '13': 'RI_3V3', '2': '1V8',
    '5': 'MOD_STATUS', '12': 'STATUS_3V3', '7': '1V8',
    '6': 'MOD_NETLIGHT', '11': 'NETLIGHT_3V3', '8': '1V8'}, F, fabricant='SN74AXC4T774BQBR', lcsc='C2862903',
    note='Quatre voies du module vers le microcontrôleur')
for ref, reseau in (('C70', '1V8'), ('C71', '3V3'), ('C72', '1V8'), ('C73', '3V3')):
    condensateur(ref, '100 nF', reseau, 'GND', F)
# Pas de rappel externe : le SN74AXC4T774 a un rappel interne de 71 kΩ vers la masse sur chaque entrée-sortie, et TI
# n'admet pas de rappel vers le haut de plus de 7 kΩ (fiche § 7.3.9). Module éteint, les lignes lues par le
# microcontrôleur sont à l'état bas : le logiciel ignore RI et coupe l'UART tant que STATUS est bas.

# ------------------------------------------------------------------------------------------- signalisation et capteurs
F = 'interface'
# LED RGB à anode commune (fiche Nationstar : 1 anode, 2 rouge, 3 vert, 4 bleu ; 10 mA au plus).
ajouter('D10', 'fg:FC-B1010RGBT-HG', 'fg:LED-ARRAY-SMD_4P-L1.0-W1.0-TR-RD', 'LED RGB', {'1': 'VSYS', '2': 'LED_RK', '3': 'LED_GK', '4': 'LED_BK'},
        F, face=HAUT, fabricant='FC-B1010RGBT-HG', lcsc='C158099')
resistance('R80', '1 kΩ', 'LED_RK', 'LED_RD', F)
resistance('R81', '330 Ω', 'LED_GK', 'LED_GD', F)
resistance('R82', '330 Ω', 'LED_BK', 'LED_BD', F)
ajouter('Q3', 'fg:2N7002DW-7-F', 'fg:SOT-363_L2.0-W1.3-P0.65-LS2.1-TL', '2N7002DW',
        {'3': 'LED_RD', '5': 'LED_R', '4': 'GND', '6': 'LED_GD', '2': 'LED_G', '1': 'GND'}, F, fabricant='2N7002DW-7-F', lcsc='C83571')
ajouter('Q4', 'fg:2N7002DW-7-F', 'fg:SOT-363_L2.0-W1.3-P0.65-LS2.1-TL', '2N7002DW',
        {'3': 'LED_BD', '5': 'LED_B', '4': 'GND', '6': 'BUZ_N', '2': 'BUZZER', '1': 'GND'}, F, fabricant='2N7002DW-7-F', lcsc='C83571')
for ref, reseau in (('R83', 'LED_R'), ('R84', 'LED_G'), ('R85', 'LED_B'), ('R86', 'BUZZER')):
    resistance(ref, '100 kΩ', reseau, 'GND', F, note='Grille bloquée au démarrage')
# Buzzer piézo (membrane ePTFE du boîtier) : pastilles, résistance de décharge en parallèle.
point_test('TP30', 'VSYS', F, PASTILLE, 'Buzzer +')
point_test('TP31', 'BUZ_N', F, PASTILLE, 'Buzzer −')
resistance('R87', '4,7 kΩ', 'VSYS', 'BUZ_N', F, note='Décharge du piézo')
# Vibreur ERM : NMOS, diode de roue libre.
ajouter('Q5', 'fg:AO3400A', 'fg:SOT-23-3_L2.9-W1.3-P1.90-LS2.4-BR', 'AO3400A', {'1': 'VIB_G', '2': 'GND', '3': 'VIB_N'}, F,
        fabricant='AO3400A', lcsc='C20917')
resistance('R88', '100 Ω', 'VIB', 'VIB_G', F)
resistance('R89', '100 kΩ', 'VIB_G', 'GND', F)
ajouter('D11', 'Device:D_Schottky', 'Diode_SMD:D_SOD-323', 'BAT54WS', {'1': 'VSYS', '2': 'VIB_N'}, F, fabricant='BAT54WS', lcsc='C22629',
        note='Roue libre du moteur')
point_test('TP32', 'VSYS', F, PASTILLE, 'Vibreur +')
point_test('TP33', 'VIB_N', F, PASTILLE, 'Vibreur −')
# Bouton SOS latéral sous la membrane IP67 (appui long ≥ 3 s), protégé.
# Panasonic EVQ-P7A : poussoir latéral sans ergot (le motif Alps SKRT met ses plots à 0,075 mm des trous de ses
# ergots, trop près pour la fabrication) ; 2,2 N, assez ferme pour éviter les appuis involontaires.
ajouter('SW1', 'Switch:SW_Push', 'Button_Switch_SMD:SW_SPST_EVQP7A', 'SOS', {'1': 'SOS', '2': 'GND'}, F,
        face=HAUT, fabricant='EVQP7A01P', lcsc='C79167')
ajouter('D12', 'fg:ESDBW5V0A1_C5173252', 'fg:X1-DFN1006-2_L1.0-W0.6-P0.65-BI', 'ESDBW5V0A1', {'1': 'SOS', '2': 'GND'}, F,
        fabricant='ESDBW5V0A1', lcsc='C5173252')
# Boucle conductrice de la sangle sur PA0 (WKUP1) : rappel externe de 1 MΩ, filtrée.
point_test('TP34', 'LOOP_EXT', F, PASTILLE, 'Boucle A')
point_test('TP35', 'GND', F, PASTILLE, 'Boucle B')
resistance('R91', '1 kΩ', 'LOOP_EXT', 'LOOP', F)
resistance('R92', '1 MΩ', 'LOOP', '3V3', F, note='3,3 µA boucle fermée ; assez bas pour tenir les fuites à chaud')
condensateur('C65', '10 nF', 'LOOP', 'GND', F, note='Filtre les salves radio captées par la sangle')
ajouter('D13', 'fg:ESDBW5V0A1_C5173252', 'fg:X1-DFN1006-2_L1.0-W0.6-P0.65-BI', 'ESDBW5V0A1', {'1': 'LOOP_EXT', '2': 'GND'}, F,
        fabricant='ESDBW5V0A1', lcsc='C5173252')
# Électrode de peau lue par le TSC (groupe 1 : PB12 condensateur d'échantillonnage, PB13 électrode).
point_test('TP36', 'ELEC_EXT', F, PASTILLE, 'Électrode')
resistance('R93', '10 kΩ', 'ELEC_EXT', 'TSC_EL', F)
condensateur('C64', '22 nF X7R', 'TSC_CS', 'GND', F, note="Condensateur d'échantillonnage du TSC")
ajouter('D14', 'Device:D_TVS', D0402, 'LXES15AAA1-153', {'1': 'ELEC_EXT', '2': 'GND'}, F, fabricant='LXES15AAA1-153',
        lcsc='C5210978', note='0,05 pF : ne fausse pas la mesure capacitive de la peau')

# Passifs : références LCSC contrôlées une à une (outils/jlc.py), bibliothèque « de base » de JLCPCB quand elle existe.
PASSIFS = {
    ('100 nF', C0402): ('C1525', 'CL05B104KO5NNNC'), ('1 µF', C0402): ('C52923', 'CL05A105KA5NQNC'),
    ('4,7 µF', C0402): ('C23733', 'CL05A475MP5NRNC'), ('10 pF', C0402): ('C32949', 'CL05C100JB5NNNC'),
    ('18 pF', C0402): ('C1549', '0402CG180J500NT'), ('33 pF', C0402): ('C1562', '0402CG330J500NT'),
    ('2,2 nF', C0402): ('C1531', '0402B222K500NT'), ('22 nF X7R', C0402): ('C1532', '0402B223K500NT'),
    ('10 µF', C0603): ('C19702', 'CL10A106KP8NNNC'), ('22 µF', C0603): ('C59461', 'CL10A226MQ8NRNC'),
    ('2,2 µF 25 V', C0603): ('C57895', 'CL10A225KA8NNNC'),
    ('0 Ω', R0402): ('C17168', '0402WGF0000TCE'), ('0 Ω', R0603): ('C21189', '0603WAF0000T5E'),
    ('1 kΩ', R0402): ('C11702', '0402WGF1001TCE'), ('10 kΩ', R0402): ('C25744', '0402WGF1002TCE'),
    ('10 kΩ 1 %', R0402): ('C25744', '0402WGF1002TCE'), ('100 kΩ', R0402): ('C25741', '0402WGF1003TCE'),
    ('4,7 kΩ', R0402): ('C25900', '0402WGF4701TCE'), ('22 Ω', R0402): ('C25092', '0402WGF220JTCE'),
    ('330 Ω', R0402): ('C25104', '0402WGF3300TCE'), ('100 Ω', R0402): ('C25076', '0402WGF1000TCE'),
    ('36,5 kΩ 1 %', R0402): ('C25887', '0402WGF3652TCE'), ('16,2 kΩ 1 %', R0402): ('C226806', 'AC0402FR-0716K2L'),
    ('1 MΩ', R0402): ('C26083', '0402WGF1004TCE'), ('10 nF', C0402): ('C15195', 'CL05B103KB5NNNC'),
    ('0 Ω', 'Resistor_SMD:R_1206_3216Metric'): ('C17888', '1206W4F0000T5E'), ('10 Ω', R0402): ('C25077', '0402WGF100JTCE'),
}
for _c in COMPOSANTS:
    if not _c.lcsc and (_c.valeur, _c.empreinte) in PASSIFS:
        _c.lcsc, _c.fabricant = PASSIFS[(_c.valeur, _c.empreinte)]

FEUILLES = ['alimentation', 'modem', 'radio', 'sim', 'microcontroleur', 'capteurs', 'niveaux', 'interface']
TITRES = {
    'alimentation': 'Alimentation, charge et jauge',
    'modem': 'Module cellulaire A7670E',
    'radio': 'Antennes LTE et GNSS',
    'sim': 'Carte SIM',
    'microcontroleur': 'Microcontrôleur STM32U575',
    'capteurs': 'Capteurs, élément sécurisé et mémoire',
    'niveaux': 'Translateurs 1,8 V / 3,3 V',
    'interface': 'Signalisation et détection',
}
# Rails d'alimentation, tous découplés.
RAILS_DECOUPLES = ['GND', 'VIN', 'VSYS', 'VBAT', 'VBAT_MES', 'VBAT_MODEM', '3V3', '3V3_REG', '3V3_FLASH', '1V8', 'SIM_VDD', 'VCAP']
# Rails sans sortie d'alimentation déclarée (connecteur, interrupteur passif, 0 Ω de mesure) : un drapeau
# d'alimentation l'indique à l'ERC. Les autres sont produits par une broche « power_out » (SYS du chargeur, VOUT
# des régulateurs et de l'interrupteur, VDD_1V8 et USIM1_VDD du module, VCAP du microcontrôleur).
ALIMENTATIONS = ['GND', 'VIN', 'VBAT', 'VBAT_MES', '3V3', '3V3_FLASH']
# Adresses I²C du tableau 8.
ADRESSES_I2C = {'U3': 0x18, 'U8': 0x60, 'U7': 0x36, 'U4': 0x6A}
