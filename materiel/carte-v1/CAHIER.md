# Carte électronique du bracelet — version V1 : cahier de réalisation

Ce cahier reformule la demande du porteur en une consigne de travail vérifiable. Il pilote toute la chaîne de
réalisation (schéma, placement, routage, fabrication) et sert de grille de relecture à chaque étape.

## 1. Consigne reformulée

> Concevoir, sous KiCad, le circuit imprimé complet du bracelet FasoGuardian à l'étape **V1 « Premier circuit »**
> de la feuille de route de FG-DOC-08 (§ 12) : un seul circuit qui réunit toutes les fonctions du banc d'essai V0,
> **4 couches (6 retenues, ADR 0042), finition ENIG, plan de masse continu**, prêt à être fabriqué et assemblé en 5 à 10 exemplaires chez un
> façonnier (JLCPCB, PCBWay ou équivalent). Chaque composant, chaque liaison et chaque règle viennent de FG-DOC-08
> v3.0 (§ 4 à 6, 10) et des fiches des fabricants. Un écart au document n'est jamais fait en silence : il est
> justifié par une fiche technique et consigné (section 5 et ADR 0042). Le livrable comprend le projet KiCad, les
> fichiers de fabrication (Gerber, perçage, nomenclature, placement), les contrôles ERC et DRC sans erreur, et la
> liste de ce qui reste à valider sur la carte réelle.

## 2. Sources

| Source | Usage |
|---|---|
| FG-DOC-08 v3.0, § 4 (nomenclature), § 5 (liaisons, broches, règles), § 6 (énergie), § 10 (mécanique), § 12 (V1) | Référence de conception |
| SIMCom, *A7672X/A7670X Series Hardware Design* V1.03 | Brochage 124 plots, alimentation (pointes 2 A, réservoir ≥ 300 µF), allumage, SIM, GNSS |
| TI, *BQ25180* (SLUSE99C) | Brochage DSBGA-8, limites de la sortie SYS, NTC, adresse I²C 0x6A |
| TI, *TPS63900* (SLVSET3D) | Brochage WSON-10, configuration 3,3 V par résistances, inductance 2,2 µH |
| Bibliothèques KiCad officielles et bibliothèque LCSC (EasyEDA) | Symboles et empreintes ; l'empreinte du module est contrôlée plot par plot contre la table SIMCom |

## 3. Contraintes retenues

- **Format** : carte de **41 × 36 mm** aux angles arrondis, épaisseur 1,2 mm, **assemblage double face** (ADR 0042,
  point 5). L'étude d'encombrement montre que le module (26 × 26 mm), le porte-nano-SIM, les connecteurs d'antenne,
  le bouton latéral et le Tag-Connect ne tiennent pas dans 40 × 34 mm, la place que laisse le boîtier de
  44 × 38 mm visé au § 10 pour le prototype portable V2. La V1 se monte dans un boîtier imprimé en 3D (§ 12). La
  face supérieure porte les pièces hautes ; la face inférieure, contre la batterie, ne porte que des pièces de
  1,35 mm de haut au plus d'après les fiches des fabricants (passifs, petits circuits intégrés, SOT-23 ; ADR 0042,
  point 12) et les pastilles des liaisons hors carte.
- **Empilement** : **6 couches** (signaux et RF / masse continue sans piste / signaux / signaux / alimentations
  mixtes, plans du modem et de la batterie sans piste, ADR 0042 points 9 et 10 / signaux), 1,2 mm, ENIG
  (décision du porteur, ADR 0042 point 7 : le 4 couches plafonnait à 88 % de routage). Empilement JLC061211-3313 à
  cuivre interne de 1 oz (ADR 0042, point 15). Pistes ≥ 0,09 mm et isolements ≥ 0,1 mm : sorties des plots au pas
  de 0,5 mm ; 0,09 mm est la capacité standard du façonnier en multicouche, cuivre interne de 1 oz compris. Vias
  0,3 / 0,15 mm (0,5 / 0,25 mm sur le plan du modem) ; vias de plan dans les pastilles, bouchés à la résine et
  recouverts (gratuit en 6 couches).
- **Courant fort du modem** : chemin batterie → R8 → U6 → module pré-routé et verrouillé, bande de plan VBAT
  dédiée ; au plus 30 mΩ de cuivre (60 mV sous 2 A), mesurés par `controler_carte.py` : 20 mΩ avec le cuivre
  interne de 1 oz.
- **Radio** : lignes d'antenne de 50 Ω en guide coplanaire référencé à la masse de la couche 2 (piste de 0,186 mm,
  fente de 0,15 mm, sur le préimprégné 3313 de 0,092 mm), de 20 mm au plus (SIMCom § 4.7) ; réseau d'adaptation
  en π (éléments série 0 Ω montés, éléments parallèles non montés, valeurs données par l'accord de l'antenne) ; protection ESD de faible capacité ; connecteur de test conduit ECT 818011998
  sur la voie LTE ; connecteurs U.FL vers l'antenne FPC LTE (flanc) et l'antenne GNSS active (dessus du boîtier).
  La batterie est sous la carte (§ 10) : le plan de masse continu fait écran, les antennes partent du bord opposé
  au bloc de charge. L'antenne GNSS active est alimentée par un té de polarisation (SIMCom figure 34) :
  condensateur série de 33 pF, choc bobiné de 68 nH et résistance de 10 Ω depuis VDD_AUX (plot 99), réglée par
  `AT+CVAUXV` et coupée par `AT+CVAUXS=0` : aucun MOSFET de coupure n'est nécessaire.
- **GNSS** : solution « non autonome » de SIMCom (figure 28) : 1V8_GNSS alimenté par VDD_1V8 par une piste courte
  d'au moins 0,3 mm, GNSS_PWRCTL par 10 kΩ vers MK_IN_3 (plot 20), liaison série GNSS ↔ UART3 par deux 1 kΩ ;
  GNSS_VBKP laissé libre (démarrage à chaud logiciel AP-Flash, sans les 1 mA permanents), avec une option non
  montée vers le 3,3 V permanent pour l'essai du démarrage à chaud matériel (VDD_1V8 disparaît module éteint).
- **Énergie** : modem alimenté depuis la batterie par un interrupteur commandé par le microcontrôleur, réservoir
  ≥ 300 µF et découplage 33 pF / 10 pF / 100 nF / 1 µF au pied des plots VBAT, piste VBAT d'au moins 3 mm (fiche
  SIMCom) ; 3,3 V par le TPS63900 pour la logique ; coupures par MOSFET de l'antenne GNSS active, de la mémoire et du
  vibreur ; résistance de mesure du courant sur chaque bloc (§ 4.3).
- **Niveaux** : 1,8 V côté modem, 3,3 V côté microcontrôleur. Deux translateurs à sens fixe SN74AXC4T774 plutôt
  que le TXS0108E cité au § 4.3 : leurs sorties poussent les deux niveaux sans les rappels internes du TXS0108E,
  mal adaptés à des lignes UART à sens fixe, et ils isolent les deux côtés quand le module est éteint. Côté 1,8 V,
  alimentation par VDD_1V8 du module (50 mA au plus) ; aucune ligne logique du module n'est tirée à 3,3 V (2,0 V au
  maximum). PWRKEY et RESET, au potentiel de VBAT, sont tirés à la masse par deux NMOS à drain ouvert, jamais par le
  translateur ; aucun condensateur de plus de 100 nF sur ces plots.
- **Microcontrôleur** : quartz 32,768 kHz pour la RTC (horodatage des positions), VCAP 4,7 µF, BOOT0 rappelé à la
  masse, NRST 100 nF, découplage de chaque VDD et de VDDA.
- **Protection** : ESD/TVS sur le connecteur de charge, le bouton, la SIM et la boucle (§ 4.3).
- **Broches** : affectation de FG-DOC-08 tableau 8, vérifiée contre les fonctions alternatives du STM32U575CIT6
  (symbole KiCad), dont le STM32U585CIT6 retenu reprend le brochage (ADR 0042, point 11) ; les écarts sont à la
  section 5.
- **Basse consommation** : rappel externe de 1 MΩ filtré par 10 nF sur la boucle (PA0) au lieu du rappel interne
  de 40 kΩ (80 µA permanents) ; grilles des MOSFET rappelées à l'état bloqué ; mémoire et thermistance de carte alimentées seulement
  pendant la mesure ou l'écriture.
- **Température** : la thermistance de la batterie est lue par le BQ25180 pendant la charge (JEITA) ; une seconde
  thermistance sur la carte, lue par l'ADC du microcontrôleur, assure la surveillance continue demandée au § 5.4.
- **Mise au point** : pastilles Tag-Connect SWD, points de test des alimentations et des liaisons, accès USB et
  USB_BOOT du module pour sa mise à jour (exigé par SIMCom).

## 4. Chaîne de réalisation et points de contrôle

Chaque étape se termine par une revue : contrôles automatiques, puis relecture de la consigne et des règles
ci-dessus. On ne passe à l'étape suivante qu'une fois tous les écarts levés.

| Étape | Produit | Contrôle de sortie |
|---|---|---|
| 1. Netlist de référence | `conception/carte.py` : composants, valeurs, liaisons, une seule source de vérité | Script de règles : broches du tableau 8, adresses I²C distinctes, aucune broche en double, niveaux 1,8 / 3,3 V, découplages présents |
| 2. Schéma | Feuilles KiCad par bloc | ERC sans erreur ; relecture bloc par bloc contre les fiches |
| 3. Placement | Contour, zones radio, placement des blocs | Règles de proximité (découplages, réservoir, antennes au bord, batterie loin du module) |
| 4. Routage | 6 couches, plans, lignes 50 Ω | DRC sans erreur ni connexion manquante ; relecture des chemins de courant forts |
| 5. Fabrication | Gerber, perçage, nomenclature, placement, PDF du schéma, rendu 3D | Relecture finale contre la consigne ; liste des validations restantes |

## 5. Écarts justifiés au document de conception

| Point de FG-DOC-08 | Constat | Décision |
|---|---|---|
| § 5.4 : modem alimenté par « la sortie système du chargeur » | Le BQ25180 limite SYS à 1,5 A continus et **100 µF** de capacité ; le modem appelle 2 A en pointe et SIMCom exige **≥ 300 µF** | Le modem est alimenté depuis la batterie (formulation du § 4.2, ligne 12) par un interrupteur TPS22965 (4 A, 16 mΩ, montée progressive) commandé par le microcontrôleur et bloqué par défaut ; réservoir de 2 × 330 µF après l'interrupteur ; ADR 0042 |
| Tableau 8 : vibreur sur PB11 | PB11 n'existe pas sur le boîtier LQFP48 du STM32U575CIT6 (broche 22 = VCAP) | Vibreur sur PB9 (TIM4_CH4) |
| Tableau 8 : PWRKEY et RESET sur PB3 et PB4 | Au démarrage, PB3/PB4 sont des broches JTAG ; PB4 (NJTRST) porte un rappel interne vers le haut qui mettrait RESET à l'état actif | PWRKEY sur PC13, RESET sur PH1, commande de l'interrupteur du modem sur PH0 : broches sans rappel au démarrage, grilles rappelées à la masse par 100 kΩ |
| Tableau 8 : coupure de l'antenne GNSS active par MOSFET (PB15) | L'antenne active est alimentée par la sortie auxiliaire VDD_AUX du module, que la commande `AT+CVAUXS=0` coupe (SIMCom § 4.6.2, figure 34) | Té de polarisation depuis VDD_AUX, pas de MOSFET ; PB15 reçoit NETLIGHT par le translateur |
| Tableau 8 : pas de DTR ni de mesure de température | Le sommeil du module (`AT+CSCLK=1`) exige DTR ; la surveillance continue de la température (§ 5.4) exige une mesure hors charge | DTR sur PB4 (via le translateur), thermistance de carte sur PA3 (ADC1_IN8) alimentée par PB3 |
| § 4.3 : translateur TXS0108E | Translateur à rappels internes, mal adapté à des lignes poussées à sens fixe | Deux SN74AXC4T774 à sens fixe |
| § 4.2, lignes 15 et 17 : batterie avec connecteur | Un JST SH (1 A par contact) ne tient pas les pointes de 2 A du modem | Batterie soudée sur pastilles ; entrée de charge protégée (écrêteur 12 V, Schottky, fusible 0,5 A, charge à 300 mA) |
| § 4.2, ligne 16 : nano-SIM au prototype | Conservé : le porte-nano-SIM permet d'essayer les cartes d'Orange, Moov et Telecel ; l'eSIM MFF2 reste pour la série | Porte-nano-SIM 6 contacts sur la face supérieure |

## 6. Points tranchés avant la commande (25 octobre 2026)

| Point | Décision |
|---|---|
| Variante du module | **A7670E-FASE** : LTE Cat-1 et 2G, GNSS et Bluetooth (LCSC C7435296, 380 pièces en stock). Toutes les variantes partagent l'empreinte de 124 plots. |
| Microcontrôleur | **STM32U585CIT6** (LCSC C5271027) : le STM32U575CIT6 est en rupture chez JLCPCB ; même brochage, accélérateurs cryptographiques en plus (ADR 0042, point 11). Stock faible : à réserver avant la commande. |
| Batterie | Li-Po une cellule, 600 à 650 mAh, format 603035 (6,0 × 30 × 35 mm) ; PCM à seuil de surintensité d'au moins 3 A ; cellule tenant 2C en continu et 3C en impulsion ; résistance interne de 150 mΩ au plus ; trois fils soudés sur BAT+, BAT− et NTC (NTC 10 kΩ, B3435) ; certification UN38.3. |
| Boîtier V1 | Imprimé en 3D : cote intérieure 43 × 38 mm, 1,6 mm réservés sous la carte (composants de 1,35 mm au plus et 0,25 mm de mousse), hauteur intérieure d'environ 17,5 mm avec l'antenne GNSS active posée au-dessus du module ; empilement dans `guide/guide-commande-et-mise-en-service.pdf` (§ 13). |
| Options de commande | `fabrication/COMMANDE.md` : empilement JLC061211-3313 à impédance contrôlée et cuivre interne de 1 oz (le chemin du modem passe de 33 à 20 mΩ, sous les 30 mΩ du budget), vias dans les pastilles bouchés et recouverts (gratuit en 6 couches), assemblage standard des deux faces, rails à gauche et à droite, contrôle du placement par l'assembleur. |
| Quantité | 5 cartes assemblées (2 au minimum). |
