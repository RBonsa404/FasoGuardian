# Application Android (Trusted Web Activity)

L'application Android ouvre l'application Parents, servie sous `https://<domaine>/app/`, en plein écran et sans
barre d'adresse (ADR 0040). Tout le code reste celui de la PWA : une mise en ligne de la PWA met l'application
à jour sans republier l'APK. Les notifications, le mode hors ligne et l'appareil photo sont ceux de la PWA.

## Ce qu'il faut

- JDK 21 et le SDK Android (plateforme 36, outils 36.0.0), par exemple ceux d'Android Studio ;
  `ANDROID_HOME` pointe sur le SDK.
- Le domaine public de la plateforme, servi en HTTPS par le proxy (`infra/`), avec l'application Parents.
- La clé de signature de publication, détenue par le porteur du projet. Elle ne va jamais dans le dépôt.

## Construire

```bash
cd android
FG_DOMAINE=exemple.bf ./gradlew assembleRelease
```

Sans clé, l'APK sort non signée : `app/build/outputs/apk/release/app-release-unsigned.apk`. Pour la signer à la
construction, définir aussi `FG_ANDROID_MAGASIN` (chemin du fichier `.jks`), `FG_ANDROID_MAGASIN_MOT_DE_PASSE`,
`FG_ANDROID_ALIAS` et `FG_ANDROID_CLE_MOT_DE_PASSE`. Pour signer une APK déjà construite :

```bash
"$ANDROID_HOME/build-tools/36.0.0/apksigner" sign --ks fasoguardian.jks --out fasoguardian.apk app/build/outputs/apk/release/app-release-unsigned.apk
```

Chaque nouvelle APK publiée prend un numéro de version plus grand : `FG_ANDROID_VERSION=2`, puis 3, etc.

## Lier l'application au domaine

Android n'ouvre l'application en plein écran que si le domaine déclare l'application. Le proxy publie
`https://<domaine>/.well-known/assetlinks.json` à partir de deux variables de `.env` :

- `FG_ANDROID_PAQUET` : `bf.fasoguardian.parents` (valeur par défaut) ;
- `FG_ANDROID_EMPREINTE` : empreinte SHA-256 du certificat de signature, lue par
  `keytool -list -v -keystore fasoguardian.jks -alias <alias>` (ligne « SHA256 », au format `AA:BB:…`).

Si l'application est distribuée par Google Play avec la signature d'application de Play, l'empreinte à publier
est celle que la console Play affiche pour la clé de signature d'application.

Sans cette déclaration, l'application s'ouvre quand même, mais dans un onglet de navigateur avec une barre
d'adresse : c'est le signe que l'empreinte publiée ne correspond pas à la clé.
