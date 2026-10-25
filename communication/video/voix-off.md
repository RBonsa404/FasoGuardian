# Voix off — film de présentation FasoGuardian

Texte généré depuis `film/scenario.js` (ne pas modifier à la main : changer le scénario puis relancer
`node communication/video/film/produire.mjs textes`). Durée du film : 105,5 s. Débit visé : 2,5 mots par
seconde, soit une voix posée de publicité. Chaque réplique commence 0,4 s après le début de son plan.

## Réplique par réplique

| # | Plan | Entrée | Sortie | Voix | Budget |
|---|---|---|---|---|---|
| 1 | Ouverture | 00:00,4 | 00:06,0 | Seize heures trente. À Ouagadougou, des milliers d'enfants sortent de l'école. | 11 mots en 5,4 s au plus |
| 2 | La question | 00:06,4 | 00:12,5 | Et pour chaque parent, la même question : où est mon enfant, en ce moment ? | 14 mots en 5,9 s au plus |
| 3 | Révélation | 00:12,9 | 00:20,5 | FasoGuardian. Un bracelet simple au poignet de votre enfant, et une application dans votre téléphone. | 15 mots en 7,4 s au plus |
| 4 | Le bracelet | 00:20,9 | 00:29,5 | Pas d'écran, pas de caméra, pas de micro. Un bracelet robuste et étanche, qui tient deux jours et plus. | 19 mots en 8,4 s au plus |
| 5 | La position | 00:29,9 | 00:37,0 | D'un coup d'œil, vous voyez où il est, à quelle heure, et avec quelle précision. | 15 mots en 6,9 s au plus |
| 6 | Les Safe Zones | 00:37,4 | 00:46,5 | Créez vos Safe Zones : l'école, la maison, chez grand-mère. S'il en sort pendant les horaires prévus, vous êtes prévenu. | 19 mots en 8,9 s au plus |
| 7 | Le SOS | 00:46,9 | 00:57,5 | En cas de danger, il appuie trois secondes sur le bouton SOS. Le bracelet vibre pour le rassurer, et vous êtes alerté en quelques secondes. | 25 mots en 10,4 s au plus |
| 8 | Le réseau | 00:57,9 | 01:04,5 | Plus de 4G ? Le bracelet passe en 2G, puis envoie ses alertes par SMS. | 14 mots en 6,4 s au plus |
| 9 | La page QR | 01:04,9 | 01:14,0 | S'il se perd, celui qui le trouve scanne le QR code gravé et vous appelle, sans voir son nom ni sa position. | 22 mots en 8,9 s au plus |
| 10 | La confidentialité | 01:14,4 | 01:20,5 | Seuls vous et les proches que vous choisissez voient où il se trouve. | 13 mots en 5,9 s au plus |
| 11 | Les offres | 01:20,9 | 01:30,5 | Le bracelet coûte trente mille francs CFA. L'abonnement commence à mille cinq cents francs par mois, sans engagement, payé par mobile money. | 22 mots en 9,4 s au plus |
| 12 | Signature | 01:30,9 | 01:39,5 | FasoGuardian, conçu à Ouagadougou pour les familles du Burkina. Toujours près d'eux. Jamais sur leur dos. | 16 mots en 8,4 s au plus |

Le plan 13, générique (01:39,5 à 01:45,5), est sans voix : la
musique seule le porte.

## Intentions de jeu

1. **Ouverture** (00:00,0) — Calme, posé, presque murmuré. Une image du quotidien.
2. **La question** (00:06,0) — Proche, sincère. Légère tension sur « en ce moment », sans dramatiser.
3. **Révélation** (00:12,5) — La voix s’ouvre, sourire audible. Marquer une courte pause après « FasoGuardian ».
4. **Le bracelet** (00:20,5) — Rythme net sur les trois « pas de », puis assurance tranquille.
5. **La position** (00:29,5) — Simple, concret, comme on montre l’écran à une amie.
6. **Les Safe Zones** (00:37,0) — Énumération chaleureuse (« chez grand-mère » avec un sourire), puis rassurant.
7. **Le SOS** (00:46,5) — Plus grave sur « en cas de danger », puis la voix se détend : l’aide arrive.
8. **Le réseau** (00:57,5) — Léger, presque malicieux sur la question. Dire « quatre G », « deux G », « S M S ».
9. **La page QR** (01:04,5) — Récit, ton de confiance. Appuyer doucement « sans voir ».
10. **La confidentialité** (01:14,0) — Ferme et bienveillant, une promesse tenue.
11. **Les offres** (01:20,5) — Clair, articulé, aucune précipitation sur les chiffres.
12. **Signature** (01:30,5) — Fierté tranquille sur « Ouagadougou ». Le slogan en deux temps, pause entre les deux phrases.
13. **Générique** (01:39,5) — Pas de voix : la musique seule porte le générique.

## Prononciation

| Écrit | Dire |
|---|---|
| FasoGuardian | « Fa-so-gar-dianne », accent sur « Fa-so » |
| Ouagadougou | « Wa-ga-dou-gou », quatre syllabes égales |
| Safe Zones | « Sèf zônes » |
| SOS | « S-O-S », lettre par lettre |
| QR code | « Q-R code » (« ku-èr code ») |
| 4G, 2G, SMS | « quatre-G », « deux-G », « S-M-S » |
| FCFA | « francs CFA » (« cé-èf-a ») |

## Montage

1. Générer une piste par réplique (12 fichiers) plutôt qu'une prise unique : chacune se pose à son « Entrée »
   ci-dessus, ce qui garde la synchronisation même si l'outil parle un peu plus vite ou plus lentement.
2. Si une réplique dépasse son budget, la régénérer avec une vitesse de 1,05 à 1,1 plutôt que de la couper.
3. Musique : nappe acoustique sans parole (kora ou balafon discret, puis pulsation légère à partir du plan 3), à −18 dB
   sous la voix, remontée à −10 dB sur la signature (plan 12). Vérifier la licence de diffusion commerciale.
4. Mixage : voix à −14 LUFS intégrés pour les réseaux sociaux, crête à −1 dBTP.
5. Les sous-titres `sous-titres.fr.srt` sont calés sur ce minutage : réimporter le SRT si une réplique est décalée.
