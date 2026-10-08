# Protocole entre le bracelet et la plateforme

Référence : FG-DOC-08 §8 (topics, format compact, sécurité) et FG-DOC-06 §6.4. Ce document fixe les champs que
ces deux documents laissent ouverts ; le micrologiciel (`firmware/`), le simulateur (`simulator/`) et le
serveur (module `telemetrie`) s'y conforment.

## Transport

- MQTT 3.1.1 sur TLS 1.2 ou supérieur, authentification mutuelle par certificat X.509.
- L'identifiant d'appareil est le numéro gravé du bracelet (`FG-2291`, ADR 0009). C'est le nom commun de son
  certificat et son identifiant de session ; l'ACL du broker ne le laisse publier et s'abonner que sous
  `fg/<identifiant>/…`.
- Tous les messages sont publiés en QoS 1. Le bracelet ouvre une session persistante.
- Le serveur s'abonne par abonnement partagé (`$share/fg-serveur/fg/+/<flux>`) : les messages se répartissent
  entre ses instances.

| Sujet | Sens | Contenu |
|---|---|---|
| `fg/<id>/telemetry` | bracelet → plateforme | position et état radio |
| `fg/<id>/alert` | bracelet → plateforme | événement (SOS, retrait, chute…) |
| `fg/<id>/status` | bracelet → plateforme | en ligne / hors ligne (message retenu et dernière volonté) |
| `fg/<id>/cmd` | plateforme → bracelet | commandes signées (livrées avec le module alertes) |
| `fg/<id>/ack` | bracelet → plateforme | accusé d'exécution d'une commande (idem) |

## Messages

JSON compact au prototype, 512 octets au plus. Un champ inconnu est ignoré ; un message mal formé est écarté
et compté (`fasoguardian_telemetrie_messages_total{resultat="INVALIDE"}`).

### `telemetry`

```json
{"t":1759651200,"seq":4812,"lat":12.3714,"lon":-1.5197,"acc":8,"src":"gnss","bat":76,"rssi":-79,"net":"4g","op":"Orange BF","mv":1}
```

| Champ | Obligatoire | Sens |
|---|---|---|
| `t` | oui | heure de la **mesure**, en secondes Unix. Une position gardée en mémoire pendant une coupure est envoyée avec son heure d'origine (US-SYS-002). |
| `seq` | oui | numéro de séquence croissant du bracelet |
| `lat`, `lon` | oui | degrés décimaux WGS 84 |
| `acc` | non | rayon d'incertitude en mètres |
| `src` | non | `gnss` (défaut), `cell` ou `wifi` |
| `bat` | non | batterie en pourcentage |
| `rssi` | non | puissance reçue en dBm |
| `net` | non | `2g`, `3g` ou `4g` |
| `op` | non | opérateur retenu par la SIM multi-opérateurs (US-SYS-003) |
| `mv` | non | 1 si l'accéléromètre voit un mouvement, 0 sinon |

### `alert`

```json
{"t":1759651200,"seq":4813,"ev":"sos","lat":12.3714,"lon":-1.5197,"acc":15}
```

`ev` vaut `sos`, `strap` (coupure de la boucle), `skin` (perte du contact peau), `fall` (chute), `batcrit`
(batterie critique) ou `charge` (pose sur le chargeur). La position est la dernière connue ; elle peut manquer :
l'alerte part sans attendre un nouveau point (FG-DOC-08 §7.3).

### `status`

```json
{"online":true,"fw":"2.4.1"}
```

Publié en message retenu à la connexion ; le même message avec `"online":false` est la dernière volonté.

## Règles de réception

Un message n'est retenu que si toutes ces conditions sont réunies ; sinon il est écarté sans erreur renvoyée
au bracelet et compté par motif.

1. L'appareil est au parc, au statut « Actif » ou « Perdu » (suivi de 72 h), et appairé à un enfant.
2. Son certificat n'est pas révoqué.
3. L'heure de mesure n'est pas dans le futur (5 minutes de tolérance), ni antérieure de plus de 30 jours, ni
   antérieure au début de l'appairage en cours : ce qu'un bracelet a mesuré avant d'être remis à l'enfant
   n'est pas une donnée de cet enfant.
4. Les coordonnées sont plausibles (`0,0` est refusé : c'est la valeur d'un récepteur sans point).

L'unicité `(bracelet, seq, t)` rend la réception idempotente : un message rejoué par le QoS 1 ou par la vidange
du tampon hors ligne est ignoré (`resultat="DOUBLON"`).

## Conservation

Les positions sont effacées chaque nuit au-delà de 30 jours (FG-DOC-06, tableau 18 ; la durée par offre, 90
jours au plus, arrive avec le module abonnements). Les événements sont conservés avec les alertes.
