# ADR 0042 — Carte électronique V1 du bracelet : écarts au document de conception

Statut : acceptée — 22 octobre 2026

## Contexte

FG-DOC-08 v3.0 (§ 12) prévoit l'étape V1 « Premier circuit » : schéma et routage 4 couches sous KiCad, 5 à 10 cartes
assemblées. La carte est réalisée dans `materiel/carte-v1/` (cahier : `CAHIER.md`). La confrontation du document aux
fiches des fabricants et au boîtier LQFP48 du microcontrôleur fait apparaître des points que le document ne permet
pas de réaliser tels quels.

## Décision

1. **Alimentation du modem.** FG-DOC-08 § 5.4 alimente le module A7670E par la sortie SYS du chargeur. Or le BQ25180
   limite SYS à 1,5 A continus (2,5 A en impulsion de moins de 20 ms) et à 100 µF de capacité, alors que SIMCom
   annonce des pointes de 2 A et exige au moins 300 µF au pied du module (*A7672X/A7670X Hardware Design* V1.03,
   § 3.1.1). Le modem est donc alimenté depuis la batterie, comme le dit aussi FG-DOC-08 § 4.2 (ligne 12), par un
   interrupteur TPS22965 commandé par le microcontrôleur et bloqué par défaut, à montée progressive. SIMCom
   recommande d'ailleurs une alimentation que le microcontrôleur peut couper. Le réservoir de 2 × 330 µF est placé
   après l'interrupteur.
2. **Broches du microcontrôleur** (tableau 8) :
   - PB11 n'existe pas sur le LQFP48 : le vibreur passe sur PB9 ;
   - PB3 et PB4 sont des broches JTAG au démarrage, et PB4 porte un rappel interne : PWRKEY passe sur PC13, RESET sur
     PH1, la commande de l'interrupteur du modem sur PH0 ;
   - DTR, nécessaire au sommeil du module, est ajouté sur PB4 ;
   - la thermistance de carte est ajoutée sur PA3 (lecture) et PB3 (alimentation du pont).
3. **Antenne GNSS active.** SIMCom (figure 34) l'alimente par un té de polarisation externe : condensateur série
   qui arrête le continu vers le module, puis inductance de plus de 56 nH et résistance depuis la sortie auxiliaire
   du module (VDD_AUX, plot 99), réglée par `AT+CVAUXV` et coupée par `AT+CVAUXS=0`. La coupure voulue par
   FG-DOC-08 se fait donc par commande AT : le MOSFET prévu sur PB15 est supprimé, et PB15 reçoit l'état réseau
   (NETLIGHT).
4. **Translateurs.** Deux SN74AXC4T774 à sens fixe remplacent le TXS0108E : sorties poussées, isolement des deux
   côtés quand le module est éteint. Leurs entrées-sorties ont un rappel interne de 71 kΩ vers la masse (TI
   n'admet pas de rappel externe vers le haut de plus de 7 kΩ) : aucun rappel externe n'est ajouté.
5. **Batterie et charge.** La batterie est soudée sur trois pastilles (BAT+, masse, NTC) : un connecteur JST SH,
   donné pour 1 A par contact, ne tient pas les pointes de 2 A du modem. L'entrée de charge reçoit un écrêteur de
   12 V (le BQ25180 tient 25 V), une diode Schottky contre l'inversion de polarité et un fusible réarmable de
   0,5 A ; le courant de charge est réglé à 300 mA (0,5 C).
6. **Format.** La carte V1 mesure 41 × 36 mm, assemblée sur les deux faces. Le module occupe à lui seul 26 × 26 mm.
   Le porte-nano-SIM, les connecteurs d'antenne, le bouton latéral et le connecteur de programmation ne tiennent pas
   dans 40 × 34 mm, la place qui reste dans le boîtier de 44 × 38 mm que FG-DOC-08 § 10 vise pour le prototype
   portable V2. La V1 se monte dans un boîtier imprimé en 3D, comme prévu au § 12. La réduction pour la V2 vient de
   l'eSIM MFF2 de série (12 × 13 mm de porte-SIM en moins) et du raccordement direct des antennes.
7. **Température.** La thermistance de la batterie n'est lue par le BQ25180 que pendant la charge. Une seconde
   thermistance, sur la carte, assure la surveillance continue demandée par FG-DOC-08 § 5.4.

## Conséquences

- Le logiciel embarqué coupe le modem par `AT+CPOF` ou PWRKEY, attend que STATUS passe bas, puis seulement ouvre
  l'interrupteur (couper VBAT directement abîme la mémoire du module, SIMCom § 3.2.2). Il coupe aussi le modem
  vers 3,5 V de batterie : la sous-tension du chargeur ne protège plus cette branche.
- Le courant du modem s'ajoute à la charge de la batterie : le courant de fin de charge du BQ25180 est réglé
  au-dessus du courant de veille du modem, ou le modem dort pendant la charge.
- La batterie doit tenir les pointes : PCM d'au moins 3 A, cellule 3C en impulsion, 150 mΩ au plus.
- Avant de couper la mémoire, le logiciel met PA4 à PA7 en mode analogique : sinon le rappel de /CS et les diodes
  de protection réalimentent la mémoire coupée.
- En mode transport, la jauge et l'interrupteur du modem restent sur la batterie : quelques µA, et non les 15 nA du
  seul BQ25180 annoncés par FG-DOC-08.
- Le portage du noyau (`firmware/ports/`) reprend l'affectation des broches de `materiel/carte-v1/conception/verifier.py`.
