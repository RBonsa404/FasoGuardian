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
| `fg/<id>/cmd` | plateforme → bracelet | commande signée |
| `fg/<id>/ack` | bracelet → plateforme | accusé d'exécution d'une commande, ou signalement d'un refus |

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
(batterie critique), `charge` (pose sur le chargeur) ou `worn` (contact peau rétabli : le bracelet est remis). La position est la dernière connue ; elle peut manquer :
l'alerte part sans attendre un nouveau point (FG-DOC-08 §7.3).

Pendant une fenêtre de retrait autorisée par le parent, `strap` et `skin` sont seulement notés ; hors fenêtre,
ils ouvrent une alerte critique. `worn` referme la fenêtre en cours. Posé sur son chargeur, le bracelet émet
`charge` et non `skin` (ADR 0011).

### `status`

```json
{"online":true,"fw":"2.4.1"}
```

Publié en message retenu à la connexion ; le même message avec `"online":false` est la dernière volonté.

### `cmd` — commande signée (ADR 0012)

Le message est du texte : `base64url(corps) "." base64url(signature)`, sans remplissage. La signature est une
ECDSA P-256 / SHA-256 au format brut R‖S (64 octets) calculée sur les octets du corps. Le corps décodé :

```json
{"id":"3f0c2b9e-4f55-4c0e-9d3a-0e8a1b2c3d4e","dev":"FG-2291","cmd":"alert","n":1791540000123,"exp":1791540900,"p":{"on":1}}
```

| Champ | Sens |
|---|---|
| `id` | identifiant de la commande, repris dans l'accusé |
| `dev` | bracelet destinataire |
| `cmd` | `alert`, `cfg`, `rm` ou `loc` |
| `n` | numéro strictement croissant par bracelet |
| `exp` | expiration, en secondes Unix (15 minutes après l'émission) |
| `p` | paramètres de la commande |

| `cmd` | Paramètres | Effet attendu |
|---|---|---|
| `alert` | `on` : 1 ou 0 | entre en mode alerte (une position toutes les 60 s) ou en sort |
| `cfg` | `int`, `alr` : intervalles en secondes ; `eco` : 1 ou 0 | applique les intervalles et le mode économie |
| `rm` | `until` : fin de la fenêtre en secondes Unix, 0 pour la refermer | autorise le retrait sans alerte jusqu'à cette heure |
| `loc` | aucun | mesure et publie une position tout de suite |

Le bracelet n'exécute une commande que si **tous** ces contrôles passent : signature de la plateforme valide,
`dev` égal à son identifiant, `exp` non dépassé, `n` supérieur au dernier numéro accepté. Une commande déjà
exécutée (même `id`) est accusée de nouveau sans être rejouée.

### `ack`

```json
{"id":"3f0c2b9e-4f55-4c0e-9d3a-0e8a1b2c3d4e","ok":true}
```

`"ok":false` signale une commande refusée. Sans accusé au bout de 30 secondes, la plateforme republie le même
message jusqu'à son expiration.

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
