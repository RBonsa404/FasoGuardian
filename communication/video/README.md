# Vidéo de présentation

Film publicitaire de 99,5 s (1920 × 1080, 120 i/s, version à 60 i/s pour les réseaux sociaux) destiné aux familles. Le cahier des charges est dans
[BRIEF.md](BRIEF.md).

| Fichier | Rôle |
|---|---|
| `film/scenario.js` | Source unique : plans, durées, texte dit, texte affiché, intentions de jeu |
| `film/film.html` | Page animée ; chaque image est calculée à son instant par `rendre(t)` |
| `film/capturer.mjs`, `film/capturer-bracelet.mjs` | Captures des écrans réels sur la pile de démonstration |
| `film/produire.mjs` | Contrôle du minutage, textes, rendu et encodage |
| `voix-off.md`, `consigne-voix.md`, `sous-titres.fr.srt`, `.vtt`, `.ass` | Générés : ne pas modifier à la main |
| `sortie/` | Films rendus (ignorés par Git) |

## Refaire le film

Sur la pile de démonstration (comptes et bracelet fictifs), depuis la racine du dépôt :

```bash
sh frontend/e2e/pile.sh demarrer && sh frontend/e2e/pile.sh demo
sh frontend/e2e/pile.sh bracelet FG-438075 position
node communication/video/film/capturer.mjs sombre carte zones qr alertes
cp communication/video/film/captures/alertes-sombre.png communication/video/film/captures/alertes-zone-sombre.png
sh frontend/e2e/pile.sh bracelet FG-438075 sos
node communication/video/film/capturer.mjs sombre accueil detail-alerte
node communication/video/film/capturer-bracelet.mjs
node communication/video/film/produire.mjs
```

La position fraîche donne « à l'instant » sur l'accueil et la carte ; le SOS donne l'écran d'alerte critique.
`node communication/video/film/produire.mjs apercu` sort une planche d'une image par plan pour vérifier la mise en
page sans rendre tout le film ; `produire.mjs soustitres` refait seulement les versions sous-titrées. Le rendu à
120 i/s (11 940 images) prend environ 30 minutes avec 4 rendeurs (`--rendeurs=4`) ; `--ips=60` le divise par deux.

## Ajouter la voix

Suivre [consigne-voix.md](consigne-voix.md) pour générer les 12 répliques, puis les poser aux instants de
[voix-off.md](voix-off.md) sur `sortie/fasoguardian-presentation-120ips.mp4`. Les versions sous-titrées
(`sortie/fasoguardian-presentation-sous-titree-120ips.mp4` et `-60ips.mp4`) servent aux réseaux sociaux, où la
lecture démarre sans son ; la plupart plafonnent à 60 i/s.
