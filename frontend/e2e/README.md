# Tests de bout en bout (Playwright)

Parcours critiques joués dans un vrai navigateur, contre le vrai serveur et une vraie base.

| Fichier | Parcours | User stories |
|---|---|---|
| `inscription-kyc.spec.ts` | Inscription du parent, dépôt du dossier, validation par un agent KYC, compte activé | US-PAR-001 |
| `commandes.spec.ts` | « Localiser maintenant » et mode alerte : commande signée, vérifiée, exécutée et accusée par un bracelet simulé | US-SYS-011, US-ENF-002, US-PAR-006 |
| `compte.spec.ts` | Mot de passe oublié par code SMS, puis clôture du compte confirmée par second facteur | US-PAR-002 |
| `famille.spec.ts` | Fiche enfant, fiche médicale avec élément critique, contact d'urgence visible sur la page QR | US-PAR-003, 004, 011 |
| `alertes.spec.ts` | SOS publié en MQTT par un bracelet fictif, alerte prise en charge puis levée avec motif, journal, retrait autorisé, constaté, prolongé et clos | US-ENF-001, 002, US-PAR-008, 010, 012 |
| `bracelet.spec.ts` | Appairage par code, mode économie, perte confirmée par SMS, bracelet retrouvé, désappairage ; effet sur la page QR | US-PAR-013, 014 |
| `signalement.spec.ts` | Signalement par le parent, aperçu du dossier, escalade confirmée par code SMS, téléchargement du PDF, levée | US-PAR-010 |
| `zones.spec.ts` | Carte sans position, Safe Zone tracée sur la carte, suspendue, réactivée, modifiée et supprimée sous code SMS, trajets | US-PAR-006, 007, 008 |
| `notifications.spec.ts` | Écran d'installation et de notifications ; message poussé au service worker, accusé de livraison envoyé au serveur | US-ENF-001 |
| `page-qr.spec.ts` | Page publique QR sans JavaScript : page générique, une seule requête, moins de 60 Ko | US-TRS-001, US-SYS-010 |
| `console-kyc.spec.ts` | Enrôlement TOTP d'un agent, instruction et validation d'un dossier dans la console ; refus opposé à un opérateur support | US-ADM-001, US-PAR-001 |
| `console-fds.spec.ts` | Signalement retrouvé par sa référence dans l'espace des forces de sécurité, accusé de réception, accusé visible par le parent | US-FDS-001 |
| `console-parametres.spec.ts` | Écran de paramétrage : tarifs, passerelle LoRaWAN enregistrée, vue en ligne sur un signe de vie scellé, puis retirée | US-SYS-004 |
| `grand-ecran.spec.ts` | Espace parent sur un poste de travail : barre latérale, cartes plein cadre (tableau de bord, position, tracé d'une zone, trajets), retour à l'affichage téléphone | US-PAR-006, 007 |

## Lancer les tests

`e2e/pile.sh` monte une pile locale jetable : PostgreSQL vierge dans Docker, serveur sous le profil `dev`
avec l'adaptateur SMS bac à sable, applications Parents (4201) et Console (4202). Ses secrets sont tirés au
hasard à chaque démarrage et gardés dans `e2e/.etat`, ignoré par Git.

```bash
sh e2e/pile.sh demarrer
```

```bash
sh e2e/pile.sh tester
```

```bash
sh e2e/pile.sh arreter
```

`JAVA_HOME` doit pointer sur un JDK 21. Sous Git Bash, lancez `demarrer` sans rediriger sa sortie vers une
autre commande : les serveurs lancés en arrière-plan la garderaient ouverte.

La base doit être vierge à chaque démarrage : le premier administrateur active son second facteur pendant
les tests, et un secret TOTP déjà activé ne se relit pas. Ces tests ne tournent pas encore dans
l'intégration continue.
