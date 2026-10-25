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
7. **Six couches.** FG-DOC-08 prévoit un circuit de 4 couches. Sur 41 × 36 mm, avec le module de 26 × 26 mm et un
   assemblage double face, le routage automatique plafonne à 88 % en 4 couches, quels que soient les réglages (30
   à 50 liaisons sans piste sur 403). Le porteur a retenu le 6 couches (23 octobre 2026) : signaux et lignes RF /
   masse continue / signaux / signaux / plans d'alimentation (modem, VSYS, 3,3 V) / signaux. L'épaisseur passe à
   1,2 mm, minimum du façonnier en 6 couches ; le préimprégné externe (3313, 0,0994 mm) ne change pas, ni la ligne
   de 50 Ω (0,20 mm, fente 0,15 mm). Surcoût estimé du prototype : 20 à 40 USD pour 5 cartes.
8. **Température.** La thermistance de la batterie n'est lue par le BQ25180 que pendant la charge. Une seconde
   thermistance, sur la carte, assure la surveillance continue demandée par FG-DOC-08 § 5.4.
9. **Couche d'alimentation mixte.** Le plan de masse de la couche 2 (In1) reste plein et interdit aux pistes : il
   sert de référence aux lignes RF de 50 Ω et à l'USB du modem, et FG-DOC-08 § 4.2 l'exige continu. Avec quatre
   couches de signaux seulement, le routage ne se referme pas sur 41 × 36 mm. La couche 5 (In4) devient donc
   mixte : ses plans VSYS et 3,3 V se coulent autour de pistes de signal courtes, que le routeur n'y place qu'à
   défaut (coût triplé). Le plan VBAT_MODEM, sous le modem, reste interdit aux pistes, pour que les pointes de 2 A
   du module ne contournent aucune fente. Une première passe de routage, qui avait aussi tracé des signaux sur la
   masse, a été écartée pour cette raison (24 octobre 2026). Pour rendre la place perdue, les vias passent à
   0,3 mm percés à 0,15 mm (0,5/0,25 mm sur le plan du modem), et les vias de plan se posent dans les pastilles
   quand elles sont assez larges. Ces vias sont bouchés à la résine et recouverts, procédé par défaut du façonnier
   en 6 couches (capacités JLCPCB : perçage 0,15 mm, diamètre au moins égal au perçage plus 0,1 mm, 0,2 mm entre
   perçages). Option à vérifier sur le devis.
10. **Chemin des pointes de courant du modem.** Les pastilles de la batterie sont au bord inférieur, avec les autres
    raccordements filaires. Le modem et son interrupteur sont en haut. Routé en pistes, le chemin batterie → R8 →
    U6 mesurait 140 mΩ : les salves GSM de 2 A y perdaient 280 mV, et le modem serait descendu sous les 3,4 V
    exigés par SIMCom. VBAT_MES, le tronçon de R8 à U6, n'appartenait d'ailleurs à aucune classe de puissance. Le
    courant passe désormais par une bande de plan VBAT dédiée sur In4, interdite aux pistes : 3,6 mm le long du
    bord gauche, puis le long du bord supérieur, puis une languette sous R8. Le chemin critique est pré-routé et
    verrouillé avant le routage automatique : 4 vias dans la pastille de la batterie, 2 dans R8.1, deux pistes de
    0,4 mm de R8.2 aux entrées VIN de U6. `controler_carte.py` calcule la résistance réelle du chemin
    (`resistance.py` : pistes, vias et plans maillés) et la limite à 30 mΩ de cuivre, soit 60 mV sous 2 A. Le
    cuivre interne de 1 oz, en option chez le façonnier, diviserait par deux la part de la bande.

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
