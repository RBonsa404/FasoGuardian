# Tester FasoGuardian en local

Ce guide monte toute la plateforme sur votre poste, crée des comptes de démonstration et décrit, écran par
écran, ce que vous pouvez essayer. Toutes les données sont fictives. Rien ne part vers l'extérieur : les SMS,
les paiements et les notifications passent par des « bacs à sable » que vous pouvez lire.

## 1. Prérequis (une seule fois)

| Outil | Pourquoi | Vérifier |
|---|---|---|
| Docker Desktop, **lancé** | base PostgreSQL et broker MQTT des bracelets | `docker info` |
| JDK 21 | serveur et bracelet simulé | `"/c/Program Files/Java/jdk-21.0.12/bin/java" -version` |
| Node.js 24 | applications web | `node -v` |
| Git Bash | les commandes ci-dessous | — |
| Connexion Internet | fonds de carte OpenStreetMap | — |

Dépendances des applications, une fois : dans `frontend`, `npm ci`.

Toutes les commandes se tapent dans **Git Bash**, depuis le dossier `frontend` du dépôt :

```bash
cd /c/Users/rachi/Desktop/FasoGuardian/Code/fasoguardian/frontend
export JAVA_HOME="/c/Program Files/Java/jdk-21.0.12"; export PATH="$JAVA_HOME/bin:$PATH"
```

(À refaire dans chaque nouvelle fenêtre Git Bash.)

## 2. Démarrer et créer les comptes

```bash
sh e2e/pile.sh demarrer
```

Comptez trois à cinq minutes. La pile part toujours d'une base **vide** ; à la fin s'affiche
`Pile prête : serveur 8080, parents 4201, console 4202, site 4203`.

```bash
sh e2e/pile.sh demo
```

crée les comptes de démonstration et affiche leur récapitulatif. À relancer après chaque `demarrer`
(jamais deux fois sur la même base : les comptes existent déjà).

| Adresse | Contenu |
|---|---|
| http://localhost:4201 | application **Parents** (à essayer en taille téléphone : F12, mode appareil, 360 × 800 ; en grand écran, c'est la version web) |
| http://localhost:4202 | **console** des agents |
| http://localhost:4203 | **site vitrine** |
| http://localhost:8080/swagger-ui.html | documentation de l'API |
| http://localhost:8080/api/v1/dev/sms | **les SMS « envoyés »**, dont les codes à six chiffres |
| http://localhost:8080/q/… | page publique d'un QR (lien donné par `demo`) |

## 3. Les comptes

**Parents** — mot de passe `soleil-de-ouaga-2026`

| Numéro | État |
|---|---|
| `70 00 00 01` | compte actif ; enfant **Awa**, bracelet appairé avec une position à Ouagadougou |
| `70 00 00 02` | compte en attente ; son dossier KYC est à instruire dans la console |

**Agents de la console** — mot de passe `demo-fasoguardian-2026`

| Identifiant | Rôle |
|---|---|
| `demo.kyc` | instruction des dossiers |
| `demo.support` | demandes des parents, base de connaissances |
| `demo.sav` | parc de bracelets, bracelets muets, mises à jour |
| `demo.fds` | forces de sécurité |
| `demo.admin` | agents, journal d'audit, conformité, supervision, sécurité, paramètres |

La console demande ensuite un **code à six chiffres** (second facteur). Pour l'obtenir :

```bash
sh e2e/pile.sh codes
```

Les codes changent toutes les trente secondes. Vous pouvez aussi ajouter les comptes à Google Authenticator :
les secrets sont dans `e2e/.etat/demo.json` (champ `secret`).

**Codes reçus par SMS** (inscription, mot de passe oublié, confirmation d'une action sensible) : ouvrez
http://localhost:8080/api/v1/dev/sms, le dernier message du numéro contient le code.

**Bracelet en stock** : son numéro et son **code d'appairage** sont affichés par `demo`.

## 4. Faire parler un bracelet

Il n'y a pas encore de bracelet physique : deux commandes jouent son rôle, par le même canal que le vrai
(MQTT chiffré, certificat propre au bracelet). `FG-XXXX` est le numéro du bracelet d'Awa, affiché par `demo`.

```bash
sh e2e/pile.sh bracelet FG-XXXX position
```

Évènements possibles : `position`, `sos`, `retrait` (fermoir ouvert), `remis`, `chute`, `batterie` (batterie
critique).

Pour un bracelet qui émet sa position chaque minute et **répond aux commandes** (« Localiser maintenant »,
mode alerte, autorisation de retrait, mise à jour), dans une seconde fenêtre Git Bash :

```bash
sh e2e/pile.sh simulateur FG-XXXX
```

Ctrl+C l'arrête. Sans lui, « Localiser maintenant » est bien envoyé mais rien ne répond.

## 5. Ce que vous pouvez essayer

### Application Parents (`70 00 00 01`)

1. **Tableau de bord** : carte, carte d'état (position, batterie, réseau), actions rapides, dernière alerte ;
   le bouton « Awa » en haut à gauche change d'enfant ; la barre d'onglets en bas.
2. **SOS** : `sh e2e/pile.sh bracelet FG-XXXX sos`. Une alerte critique s'ouvre (cloche, onglet Alertes) :
   « J'ai pris en charge », puis « Lever l'alerte » avec un motif. Le journal de l'alerte est scellé.
3. **Retrait non autorisé** : `… retrait`. Puis, dans Awa → Autoriser un retrait, choisissez une durée,
   confirmez avec le code SMS ; un nouveau `… retrait` n'ouvre plus d'alerte ; `… remis` referme la fenêtre.
4. **Batterie critique, chute** : `… batterie`, `… chute`.
5. **Carte** (onglet ou « Agrandir ») : mode alerte, « Localiser maintenant » (simulateur lancé).
6. **Safe Zones** : en créer une en touchant la carte (cercle) ou en polygone, la suspendre, la modifier (code SMS),
   la supprimer. Une position hors de la zone (modifiez `lat`/`lon` dans `pile.sh` ou déplacez la zone)
   déclenche une sortie de zone.
7. **Trajets** : apparaissent après plusieurs positions (`… position` à quelques minutes d'écart, ou le
   simulateur).
8. **Journal des alertes** (« Tout voir ») : filtres par type.
9. **Fiche enfant**, **fiche médicale**, **contacts d'urgence**, **aperçu de la page QR** ; ouvrez ensuite la
   vraie page publique (lien `…/q/…` donné par `demo`) : un tiers n'y voit ni nom, ni photo, ni position.
10. **Partager la position** temporairement avec un contact.
11. **Mon bracelet** : mode économie, déclarer perdu puis retrouvé (code SMS), désappairer ; associer le
    bracelet en stock avec son code d'appairage.
12. **Abonnement** : changer d'offre et payer en mobile money (bac à sable : le paiement est accepté au bout
    de trois secondes ; un numéro de paiement qui finit par `00` est refusé), puis les **reçus** en PDF.
13. **Aide** : rechercher un article, écrire au support, suivre la réponse.
14. **Réglages** : thème clair ou sombre, mot de passe, numéro de téléphone, téléchargement de vos données,
    déconnexion, clôture du compte (définitive : gardez-la pour la fin).
15. **Hors ligne** : coupez le réseau (F12 → Network → Offline) ; la fiche de l'enfant reste consultable.
16. **Grand écran** : élargissez la fenêtre au-delà de 1024 px, une barre latérale remplace les onglets.

### Nouvelle inscription

http://localhost:4201/inscription : un numéro libre (par exemple `76 12 34 56`), le code lu dans les SMS,
un mot de passe, les consentements, puis le dossier KYC (n'importe quelle image et n'importe quel PDF).
Le compte reste « en instruction » jusqu'à la décision d'un agent KYC.

### Console (http://localhost:4202)

- **`demo.kyc`** : tableau de bord, file KYC ; ouvrez le dossier du parent `70 00 00 02` (référence affichée
  par `demo`), consultez les pièces (consultation journalisée), validez, demandez un complément ou rejetez ;
  litiges de filiation.
- **`demo.support`** : tickets des parents (répondez à celui créé à l'étape 13), base de connaissances.
- **`demo.sav`** : parc (fiche d'un bracelet, retour au SAV, remise en stock), bracelets muets (voir
  ci-dessous), campagnes de mise à jour.
- **`demo.fds`** : un parent signale une disparition (onglet Alertes → Signaler, confirmation par code SMS,
  escalade) ; la référence `FG-SIG-…` du dossier se saisit dans l'espace des forces de sécurité, qui en
  accuse réception.
- **`demo.admin`** : agents et rôles (création, suspension), journal d'audit et vérification de la chaîne,
  conformité (demandes d'accès et d'effacement, purges), supervision, sécurité, paramètres.
- Partout : **Ctrl K** pour rechercher un écran ou une référence (FG-…, KYC-…, SAV-…, SUP-…), bouton
  d'apparence en haut à droite. Un agent n'accède qu'aux écrans de son rôle : essayez une adresse d'un autre
  rôle, l'accès est refusé et journalisé.

**Bracelet muet** : un bracelet qui ne donne plus de nouvelles depuis trois intervalles (quinze minutes)
ouvre un ticket SAV. Laissez le bracelet d'Awa sans `position` pendant un quart d'heure (simulateur arrêté) :
le parent est prévenu, le ticket apparaît chez `demo.sav`, qui peut le prendre en charge, lancer le diagnostic,
proposer un rendez-vous en point relais et réserver le bracelet en stock ; le parent l'associe depuis l'écran
de maintenance et le ticket se clôt. Une `position` avant cela clôt le ticket d'elle-même.

## 6. Arrêter, recommencer

```bash
sh e2e/pile.sh arreter
```

arrête tout et efface la base. Pour repartir de zéro : `demarrer`, puis `demo`.

## 7. Les tests automatiques

| Quoi | Commande |
|---|---|
| Serveur (unitaires, architecture, intégration ; Docker lancé) | dans `backend` : `./mvnw verify` |
| Applications | dans `frontend` : `npx ng test parents --watch=false`, `npx ng test console --watch=false` |
| Parcours complets dans un navigateur (pile démarrée, **base vierge** : avant `demo`) | `sh e2e/pile.sh tester` |
| Logiciel du bracelet | dans `firmware` : `make essais` (ou par Docker, voir `firmware/README.md`) |

## 8. Ce qui n'est pas réel en local

- Les **SMS**, les **paiements** et les **notifications** sont des bacs à sable : rien n'est envoyé.
- Les **notifications push** du navigateur ne s'activent qu'en HTTPS ou sur `localhost` dans Chrome ; les
  alertes restent de toute façon visibles dans l'application et doublées par SMS (bac à sable).
- Le **bracelet** est simulé ; le logiciel du vrai bracelet n'est essayé que par ses tests (`make essais`).
