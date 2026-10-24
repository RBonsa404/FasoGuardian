# Consigne pour l'outil de synthèse vocale

Texte généré depuis `film/scenario.js`. Coller la **consigne de voix** dans le champ de description (ou de « prompt »)
de l'outil, puis le **texte** réplique par réplique.

## Consigne de voix (à coller telle quelle)

> Voix off d'une publicité pour FasoGuardian, un bracelet de sécurité pour enfants conçu à Ouagadougou. Locutrice ou
> locuteur burkinabè de 30 à 40 ans, français d'Afrique de l'Ouest à l'accent naturel et léger, diction claire. Ton
> chaleureux, rassurant et confiant, comme un parent qui conseille un autre parent : jamais alarmiste, jamais
> commercial ou criard. Débit posé d'environ 2,5 mots par seconde, phrases courtes, respirations audibles entre les
> phrases. Sourire dans la voix sur les passages positifs, gravité retenue sur « en cas de danger ». Les chiffres sont
> écrits en toutes lettres et doivent être dits tels quels. Prononcer « Ouagadougou » « Wa-ga-dou-gou », « SOS » et
> « SMS » lettre par lettre, « Safe Zones » « sèf zônes », « FCFA » « francs CFA ».

## Réglages conseillés

| Réglage | Valeur | Pourquoi |
|---|---|---|
| Stabilité | 45 à 55 % | Assez de variation pour sonner humain, sans dérive d'une réplique à l'autre |
| Similarité ou clarté | 75 % | Garde le timbre choisi sur les 12 répliques |
| Style ou exagération | 15 à 25 % | Chaleur sans emphase publicitaire |
| Vitesse | 1,0 (1,05 si une réplique dépasse son budget) | Le minutage du film suppose 2,5 mots par seconde |
| Format | WAV 48 kHz, ou MP3 320 kb/s | Qualité de montage |

## Texte, réplique par réplique (méthode conseillée)

1. *(00:00,4, 5,4 s au plus, calme, posé, presque murmuré. Une image du quotidien.)*
   Seize heures trente. À Ouagadougou, des milliers d'enfants sortent de l'école.
2. *(00:06,4, 5,9 s au plus, proche, sincère. Légère tension sur « en ce moment », sans dramatiser.)*
   Et pour chaque parent, la même question : où est mon enfant, en ce moment ?
3. *(00:12,9, 7,4 s au plus, la voix s’ouvre, sourire audible. Marquer une courte pause après « FasoGuardian ».)*
   FasoGuardian. Un bracelet simple au poignet de votre enfant, et une application dans votre téléphone.
4. *(00:20,9, 8,4 s au plus, rythme net sur les trois « pas de », puis assurance tranquille.)*
   Pas d'écran, pas de caméra, pas de micro. Un bracelet robuste et étanche, qui tient deux jours et plus.
5. *(00:29,9, 6,9 s au plus, simple, concret, comme on montre l’écran à une amie.)*
   D'un coup d'œil, vous voyez où il est, à quelle heure, et avec quelle précision.
6. *(00:37,4, 8,9 s au plus, énumération chaleureuse (« chez grand-mère » avec un sourire), puis rassurant.)*
   Créez vos Safe Zones : l'école, la maison, chez grand-mère. S'il en sort pendant les horaires prévus, vous êtes prévenu.
7. *(00:46,9, 10,4 s au plus, plus grave sur « en cas de danger », puis la voix se détend : l’aide arrive.)*
   En cas de danger, il appuie trois secondes sur le bouton SOS. Le bracelet vibre pour le rassurer, et vous êtes alerté en quelques secondes.
8. *(00:57,9, 6,4 s au plus, léger, presque malicieux sur la question. Dire « quatre G », « deux G », « S M S ».)*
   Plus de 4G ? Le bracelet passe en 2G, puis envoie ses alertes par SMS.
9. *(01:04,9, 8,9 s au plus, récit, ton de confiance. Appuyer doucement « sans voir ».)*
   S'il se perd, celui qui le trouve scanne le QR code gravé et vous appelle, sans voir son nom ni sa position.
10. *(01:14,4, 5,9 s au plus, ferme et bienveillant, une promesse tenue.)*
   Seuls vous et les proches que vous choisissez voient où il se trouve.
11. *(01:20,9, 9,4 s au plus, clair, articulé, aucune précipitation sur les chiffres.)*
   Le bracelet coûte trente mille francs CFA. L'abonnement commence à mille cinq cents francs par mois, sans engagement, payé par mobile money.
12. *(01:30,9, 8,4 s au plus, fierté tranquille sur « Ouagadougou ». Le slogan en deux temps, pause entre les deux phrases.)*
   FasoGuardian, conçu à Ouagadougou pour les familles du Burkina. Toujours près d'eux. Jamais sur leur dos.

## Texte en une seule prise (outils acceptant les pauses SSML)

Les pauses reproduisent les silences du film entre deux répliques.

```
Seize heures trente. À Ouagadougou, des milliers d'enfants sortent de l'école. <break time="1.6s" />

Et pour chaque parent, la même question : où est mon enfant, en ce moment ? <break time="0.9s" />

FasoGuardian. Un bracelet simple au poignet de votre enfant, et une application dans votre téléphone. <break time="2.0s" />

Pas d'écran, pas de caméra, pas de micro. Un bracelet robuste et étanche, qui tient deux jours et plus. <break time="1.4s" />

D'un coup d'œil, vous voyez où il est, à quelle heure, et avec quelle précision. <break time="1.5s" />

Créez vos Safe Zones : l'école, la maison, chez grand-mère. S'il en sort pendant les horaires prévus, vous êtes prévenu. <break time="1.9s" />

En cas de danger, il appuie trois secondes sur le bouton SOS. Le bracelet vibre pour le rassurer, et vous êtes alerté en quelques secondes. <break time="1.0s" />

Plus de 4G ? Le bracelet passe en 2G, puis envoie ses alertes par SMS. <break time="1.4s" />

S'il se perd, celui qui le trouve scanne le QR code gravé et vous appelle, sans voir son nom ni sa position. <break time="0.7s" />

Seuls vous et les proches que vous choisissez voient où il se trouve. <break time="1.3s" />

Le bracelet coûte trente mille francs CFA. L'abonnement commence à mille cinq cents francs par mois, sans engagement, payé par mobile money. <break time="1.2s" />

FasoGuardian, conçu à Ouagadougou pour les familles du Burkina. Toujours près d'eux. Jamais sur leur dos.
```

## Contrôle après génération

- Chaque réplique tient dans son budget (colonne « Budget » de `voix-off.md`).
- Aucun mot ajouté ni retiré par l'outil : le texte est relu et validé pour ne rien promettre que le produit ne fait pas.
- Écoute au casque puis sur haut-parleur de téléphone (public principal).
