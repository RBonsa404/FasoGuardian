# Vidéo de présentation FasoGuardian — cahier des charges

Demande du porteur, reformulée pour être exécutable et vérifiable.

## 1. Demande reformulée (le « prompt » de production)

> Réaliser un film publicitaire d'environ 90 secondes, en 1920 × 1080, destiné aux **familles** du Burkina Faso
> (et, en second plan, aux écoles privées). Le film raconte une expérience : ce que le parent **voit, fait et
> ressent** avec le bracelet et l'application. Il ne montre jamais la mécanique interne (serveurs, agents,
> console, vérification d'identité côté équipe). Il dit d'où vient le produit (conçu à Ouagadougou), pourquoi il
> existe (retrouver vite un enfant, faute de solution adaptée au pays), ce que l'on peut faire (position, Safe
> Zones, SOS, page QR, alertes par SMS quand la 4G manque), combien cela coûte et où l'obtenir. Chaque affirmation
> est vraie aujourd'hui dans le produit (règle de l'ADR 0029 : aucune promesse que le système ne tient pas).
> Les écrans montrés sont ceux de l'application réelle, capturés sur la pile de démonstration, avec des
> personnages fictifs. La voix est produite ailleurs par le porteur : la vidéo est livrée sans voix, avec un
> texte de voix off minuté, des sous-titres SRT et VTT calés sur les plans, et une consigne prête à donner à
> l'outil de synthèse vocale.

## 2. Public et ton

- Cible principale : parents urbains (Ouagadougou, Bobo-Dioulasso), smartphone Android d'entrée de gamme,
  abonnés au mobile money. Cible secondaire : directions d'écoles privées.
- Ton : chaleureux, rassurant, concret. On parle au parent (« vous »), on ne dramatise pas la disparition :
  la peur est évoquée en une phrase, puis le film montre le soulagement.
- Langue : français simple, phrases courtes (une idée par phrase), chiffres dits en toutes lettres dans la voix.

## 3. Ce que le film montre — et ce qu'il ne montre pas

| Montré (expérience) | Écarté (coulisses) |
|---|---|
| Le bracelet au poignet, le bouton SOS (appui 3 s) | Console des agents, vérification des pièces |
| La carte de l'enfant, l'heure et la précision de la position | Serveurs, chiffrement expliqué techniquement |
| Les Safe Zones (école, maison) et l'alerte de sortie | Journal d'audit, supervision |
| L'alerte SOS reçue par le parent | Canal vers les forces de sécurité en détail |
| La page QR : un passant appelle la famille sans voir la position | Nomenclature, carte électronique |
| Le repli 2G puis SMS | Protocole MQTT, OTA |
| Prix, offres, mobile money, points relais | Business plan, marges |

La vie privée est dite comme un bénéfice pour le parent (« personne d'autre ne voit la position de votre
enfant »), pas comme une architecture.

## 4. Faits autorisés (source : `frontend/projects/site/src/app/contenu.ts` et FG-DOC-11)

- Bracelet 30 000 FCFA ; offres dès 1 500 FCFA par mois, sans engagement ; paiement par mobile money.
- SOS : appui long de 3 secondes ; en alerte, position toutes les 60 secondes.
- Position toutes les 5 ou 15 minutes selon l'offre ; heure et précision toujours affichées.
- Safe Zones avec plages horaires et délai de tolérance.
- Repli 4G → 2G → SMS.
- Fermoir qui détecte le retrait ; étanche IP67 ; autonomie typique 48 h et plus ; charge magnétique.
- Ni écran, ni caméra, ni micro, ni biométrie.
- Page QR gravée : appel des contacts choisis, sans nom ni position.
- Conçu à Ouagadougou par le Collectif Dedsec ; générique de fin : réalisation du Collectif Dedsec, son fondateur
  Abdoul Rachid BONSA et son contact (demande du porteur du 24 octobre 2026).

Interdits : chiffres non publiés (nombre de familles, temps d'intervention), promesse de retrouver l'enfant
« à coup sûr », citation de partenaires non signés.

## 5. Livrables

| Fichier | Contenu |
|---|---|
| `sortie/fasoguardian-presentation-120ips.mp4` | Film 1080p, 120 i/s, H.264, sans voix ni sous-titres incrustés |
| `sortie/fasoguardian-presentation-sous-titree-120ips.mp4` | Même film, sous-titres incrustés |
| `sortie/fasoguardian-presentation-sous-titree-60ips.mp4` | Sous-titré, 60 i/s : plafond de la plupart des réseaux sociaux |
| `voix-off.md` | Texte minuté plan par plan, intentions de jeu, guide de prononciation |
| `consigne-voix.md` | Consigne prête à coller dans l'outil de synthèse vocale |
| `sous-titres.fr.srt`, `.vtt`, `.ass` | Sous-titres générés depuis la même source que le film (ASS : style de l'incrustation) |
| `film/` | Sources : page HTML animée, scénario (`scenario.js`), captures, script de rendu |

## 6. Contraintes de fabrication

- Une seule source de vérité pour le minutage : `film/scenario.js`. Le film, la voix off et les sous-titres
  en sont tous dérivés, ce qui garantit leur synchronisation.
- Rendu déterministe image par image (chaque image est calculée à son instant, sans dépendre de la vitesse de la
  machine), encodé par ffmpeg sans fichiers intermédiaires (disque C: presque plein).
- Débit de lecture de la voix : environ 2,5 mots par seconde ; chaque plan dure au moins le temps de dire sa
  phrase, plus une respiration.

## 7. Critères d'acceptation

1. Le film dure entre 80 et 100 secondes hors générique (6 s, sans voix) et chaque sous-titre tient sur deux
   lignes de 42 caractères au plus.
2. Chaque phrase de la voix off correspond à un fait de la section 4.
3. Aucun écran de la console, aucun terme technique interne n'apparaît.
4. Les sous-titres et le film proviennent du même `scenario.js`.
