plugins {
    id("com.android.application")
}

/*
 * Paramètres de construction, jamais écrits dans le dépôt :
 * - le domaine public de la plateforme (FG_DOMAINE ou -Pfasoguardian.domaine=…), où l'application est ouverte
 *   sous /app/ ; « fasoguardian.test » par défaut, pour une construction d'essai ;
 * - la clé de signature de publication, détenue par le porteur du projet : FG_ANDROID_MAGASIN (fichier .jks),
 *   FG_ANDROID_MAGASIN_MOT_DE_PASSE, FG_ANDROID_ALIAS, FG_ANDROID_CLE_MOT_DE_PASSE. Sans elle, l'APK de
 *   publication sort non signée, à signer ensuite (voir android/README.md).
 */
val domaine: String = (findProperty("fasoguardian.domaine") as String?) ?: System.getenv("FG_DOMAINE") ?: "fasoguardian.test"
val versionDeLApplication: Int = ((findProperty("fasoguardian.version") as String?) ?: System.getenv("FG_ANDROID_VERSION") ?: "1").toInt()
val magasin: String? = System.getenv("FG_ANDROID_MAGASIN")

android {
    namespace = "bf.fasoguardian.parents"
    compileSdk = 36

    defaultConfig {
        applicationId = "bf.fasoguardian.parents"
        minSdk = 23
        targetSdk = 35
        versionCode = versionDeLApplication
        versionName = "1.0.$versionDeLApplication"

        manifestPlaceholders["hote"] = domaine
        manifestPlaceholders["adresse"] = "https://$domaine/app/"
        // Déclaration réciproque du lien avec le site (Digital Asset Links) : le domaine publie l'empreinte de la clé.
        resValue(
            "string",
            "asset_statements",
            "[{\\\"relation\\\": [\\\"delegate_permission/common.handle_all_urls\\\"], \\\"target\\\": {\\\"namespace\\\": \\\"web\\\", \\\"site\\\": \\\"https://$domaine\\\"}}]",
        )
    }

    signingConfigs {
        if (magasin != null) {
            create("publication") {
                storeFile = file(magasin)
                storePassword = System.getenv("FG_ANDROID_MAGASIN_MOT_DE_PASSE")
                keyAlias = System.getenv("FG_ANDROID_ALIAS")
                keyPassword = System.getenv("FG_ANDROID_CLE_MOT_DE_PASSE")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (magasin != null) {
                signingConfig = signingConfigs.getByName("publication")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        resValues = true
    }
}

dependencies {
    implementation("com.google.androidbrowserhelper:androidbrowserhelper:2.7.3")
}
