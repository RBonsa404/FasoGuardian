# Logiciel embarqué du bracelet

Logiciel du bracelet FasoGuardian (C, STM32 HAL/LL, FreeRTOS) pour le STM32U575, conforme à FG-DOC-08 et à
`docs/protocole-bracelet.md`. Architecture et périmètre : ADR 0030.

| Dossier | Contenu |
|---|---|
| `noyau/` | Règles du bracelet, sans matériel : rythme des positions, SOS, retrait, batterie, tampon hors ligne, repli SMS signé, vérification des commandes et des images de mise à jour |
| `ports/fg_port.h` | Frontière avec le matériel ; le port STM32U575 sera écrit avec le prototype |
| `tests/` | Essais du noyau sur machine hôte et port d'essai |
| `outils/` | Image Docker pour lancer les essais sans compilateur C installé |

## Essais

```sh
make essais
```

Sans compilateur C sur le poste :

```sh
docker build -t fasoguardian-firmware-hote firmware/outils
docker run --rm -v "$PWD/firmware:/src" -w /src fasoguardian-firmware-hote make essais
```

En attendant le prototype, le simulateur (`simulator/`) tient lieu de bracelet pour les essais de la
plateforme ; il émet les mêmes messages que le noyau.
