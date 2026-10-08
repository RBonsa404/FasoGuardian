# Logiciel embarqué du bracelet

Logiciel du bracelet FasoGuardian (C, STM32 HAL/LL, FreeRTOS) pour le STM32U575, conforme à FG-DOC-08.
Réalisé à l'étape 5 du plan, après le site vitrine. Les couches matérielles seront abstraites afin que la
logique (modes de fonctionnement, intervalles, SOS, détection de retrait, mémoire tampon, repli SMS signé)
soit testée sur machine hôte.

En attendant, le simulateur (`simulator/`) tient lieu de bracelet pour les tests de la plateforme.
