# ADR 0030 — Logiciel embarqué : un noyau portable essayé sur machine hôte, la couche matérielle avec le prototype

Statut : acceptée — 14 octobre 2026

## Contexte

FG-DOC-08 §7 décrit le logiciel du bracelet : C, bibliothèques ST (HAL/LL), FreeRTOS, STM32U575, module
cellulaire A7670E, élément sécurisé ATECC608B, jauge MAX17048, chargeur BQ25180, accéléromètre LIS2DW12,
mémoire W25Q64. Le prototype V0 n'existe pas encore : l'affectation des broches est « indicative, à valider
dans STM32CubeMX », et le choix du module cellulaire comme du microcontrôleur est soumis à la revue avec le
mentor matériel (§13.2). Écrire dès maintenant des pilotes pour une carte qui n'existe pas produirait du
code qu'aucun essai ne peut valider.

Les règles du bracelet, en revanche, sont connues et décident de la sûreté du service : SOS par appui de 3 s,
retrait confirmé par l'accéléromètre, fenêtre de retrait, mode alerte à 60 s, tampon hors ligne, repli SMS
signé au bout de 20 s, vérification des commandes signées et des images de mise à jour.

## Décision

1. **Un noyau sans matériel** (`firmware/noyau`) porte toutes ces règles, en C11, sans allocation dynamique
   ni calcul flottant. Il ne parle au matériel qu'à travers `ports/fg_port.h` (heure, élément sécurisé,
   liaison MQTT, SMS, position, batterie, accéléromètre, signaux au porteur, banque de mise à jour).
2. **Essais sur machine hôte** (`make essais`) avec un port d'essai qui rend ce que l'essai règle et retient
   ce que le noyau demande. Toute alerte du compilateur est une erreur et un comportement indéfini arrête
   l'essai. Ils tournent dans la CI ; `firmware/outils/Dockerfile` les rend possibles sur un poste sans
   compilateur C.
3. **Messages identiques à ceux du simulateur** : mêmes champs, même SMS signé, même vérification des
   commandes (`docs/protocole-bracelet.md`). La plateforme ne distingue pas un bracelet réel d'un bracelet
   simulé.
4. **Comportements précisés** en écrivant le noyau :
   - fermoir ouvert : la perte du contact peau qui suit n'est pas signalée une seconde fois (`strap` suffit) ;
   - une seule alerte attend le réseau à la fois ; un SOS en attente n'est remplacé par rien ;
   - un SMS envoyé vaut l'alerte : elle n'est pas republiée par MQTT au retour du réseau, ce qui créerait
     une seconde alerte côté plateforme ;
   - le SOS met le bracelet en mode alerte sans attendre la commande de la plateforme ;
   - une commande déjà exécutée et réémise (accusé perdu) est accusée de nouveau sans être rejouée.
5. **Reste à faire avec le prototype**, dans `firmware/ports/stm32u5` : projet STM32CubeMX (broches validées),
   tâches FreeRTOS qui appellent le noyau, pilote AT du A7670E (enregistrement 4G puis 2G, MQTT/TLS natif,
   SMS, GNSS), pilotes I²C/SPI des capteurs, passage en mode Stop, chien de garde, amorce sécurisée avec
   double banque et retour arrière (TrustZone), clés dans l'ATECC608B.
   **Écart à FG-DOC-08 :** le tampon hors ligne du noyau (64 positions) est en mémoire vive ; FG-DOC-08 le
   veut en mémoire flash. La persistance sur la W25Q64 relève de ce port : il recopiera le tampon en flash
   avant tout passage en veille profonde ou redémarrage volontaire, et le relira au démarrage.

## Conséquences

- Les critères d'acceptation propres au bracelet (US-ENF-002 : appui de 3 s, appui bref ignoré, mode alerte ;
  US-SYS-001 : SMS après 20 s ; US-SYS-002 : aucune perte ; US-PAR-012, US-PAR-013, US-SYS-008, US-SYS-011)
  sont couverts par des essais automatisés du noyau. Leur validation sur le bracelet réel passe par le plan
  d'essais de FG-DOC-08 §12.2.
- Le changement de microcontrôleur ou de module cellulaire que pourrait recommander le mentor ne touche que
  le port.
