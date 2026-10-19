# ADR 0005 — Écarts et ajouts du paquet de design

Statut : acceptée en partie — 7 octobre 2026

## Contexte

Le paquet de design (HANDOFF.md, §10) signale sept écarts ou ajouts par rapport aux FG-DOC. Le design est
la source la moins prioritaire : chaque point est tranché ici.

## Décision

| N° | Écart du design | Décision |
|---|---|---|
| 1 | Couleur « attention » violette pour les avertissements non critiques | Acceptée : elle permet de réserver l'ambre aux alertes (FG-DOC-06 §5.3). Jetons `attention`, `attention-soft`, `attention-text` |
| 2 | Appels de la page QR par une ligne relais masquée | Acceptée sur le principe, **en attente du prestataire de téléphonie**. Un port `LigneRelais` est prévu avec un adaptateur bac à sable ; tant qu'aucun prestataire n'est retenu, le bouton d'appel compose le numéro du contact marqué visible sur le QR, comme le prévoient les FG-DOC |
| 3 | Notification du parent à chaque scan du QR | Acceptée : heure et quartier approximatif déduit de l'adresse IP pseudonymisée, sans identité du visiteur |
| 4 | Glisser pour acquitter, doublé d'un bouton | Acceptée |
| 5 | Code d'appairage distinct du QR | Déjà exigé par FG-DOC-05 (US-PAR-014) |
| 6 | Suivi 72 h après une déclaration de perte | Acceptée sous réserve de l'AIPD : le suivi prolongé d'un bracelet déclaré perdu est un traitement à documenter |
| 7 | Délai de réponse constant sur un QR invalide | Acceptée : complète US-SYS-010 |

## Conséquences

Les points 2 et 6 dépendent d'éléments externes (prestataire, AIPD) et sont signalés dans la matrice de
traçabilité.

## Complément du 9 octobre 2026 — écrans d'administration de la console

- **Écran 69, « + Agent (code TOTP admin) ».** Le design demande un code TOTP à chaque création d'agent et dit
  que « toute modification exige le second facteur ». Tous les agents se connectent déjà avec un second
  facteur obligatoire, et la session d'un agent est courte (jeton de dix minutes, session de trente). La
  création, le changement de rôles et la suspension ne redemandent donc pas de code : ils sont réservés au
  rôle administrateur, journalisés, et un administrateur ne peut pas agir sur son propre compte. Redemander
  un code à chaque action se heurterait de plus à la protection contre le rejeu (un code ne sert qu'une fois
  par tranche de trente secondes).
- **Écran 69, colonne « CIL ».** La matrice du design a une colonne « CIL ». Le délégué à la protection des
  données n'a pas de compte (FG-DOC-05) : la colonne n'existe pas, ses rapports sont produits par
  l'administrateur.
- **Écran 69, matrice.** Elle présente les permissions réellement appliquées par le serveur, en lecture : le
  cloisonnement est dans le code et vérifié par les tests d'autorisation, il ne se règle pas à l'écran.
- **Écran 73, sources bloquées.** Le design montre des adresses IP partiellement masquées et un nombre
  d'essais. La plateforme ne conserve jamais l'adresse d'une source, seulement un pseudonyme (FG-DOC-06,
  tableau 18) : l'écran montre le début de ce pseudonyme et la date du blocage. Le seuil est celui de
  US-SYS-010 (plus de 20 jetons invalides en une minute), non les « 10 min » de la maquette.
- **Écrans 64 et 66, identité de l'enfant.** Voir ADR 0009 : le rôle SAV ne voit que l'état des bracelets.
- **Écrans 64 et 65, parc et fiche.** Mise en page maître-détail reprise : cinq indicateurs (pertes et vols
  comptés ensemble, unités réformées appelées « Retirés »), liste et fiche du bracelet choisi côte à côte. La
  colonne « Titulaire · zone » devient « Porté » (ADR 0009). Batterie, réseau et dernier contact ne sont connus
  que d'un bracelet en service et seulement depuis son appairage en cours : ailleurs, un tiret. La fiche ne
  montre ni la SIM, ni le coloris, ni les cycles de charge, ni le rendu du bracelet : le parc ne les enregistre
  pas. « Mise en service » est le premier appairage ; les interventions sont les appairages et leurs fins.
- **Écran 16, tableau de bord.** La mise en page de la maquette est reprise (carte en tête, choix de l'enfant
  et cloche posés dessus, carte d'état, quatre actions rapides, dernière alerte, barre d'onglets Accueil, Carte,
  Alertes, Réglages). Trois écarts : l'autonomie restante (« ≈ 41 h ») n'est pas affichée, la plateforme ne
  l'estime pas ; le bandeau orange d'une alerte en cours est conservé sous la carte d'état, une alerte ne
  devant jamais tenir dans le seul point de la cloche ; les accès à la carte, aux Safe Zones, aux trajets et
  au bracelet restent en liste sous la dernière alerte, la maquette ne disant pas d'où on les ouvre. Mes
  enfants, l'abonnement, l'aide et la déconnexion passent dans l'onglet Réglages (écran 48).
- **Écran 56, recherche Ctrl K.** La maquette invite à « rechercher un dossier, un parent, un FG-XXXX ». La
  recherche trouve les écrans de l'agent et reconnaît les références de ses seuls rôles (FG-…, SAV-…, KYC-…,
  SUP-…, FG-SIG-…), puis ouvre l'écran qui les cherche avec ses propres contrôles et sa journalisation. Elle
  ne cherche pas une personne par son nom : ce serait un accès transversal aux données personnelles que
  FG-DOC-06 (tableau 17) ne donne à aucun rôle. L'invite ne cite que ce qui fonctionne pour l'agent. La cloche
  de notifications de la barre du haut n'est pas reprise : la console n'envoie pas de notifications aux agents.
- **Écran 31, fiche enfant.** L'en-tête, les lignes et l'historique suivent la maquette ; l'historique nomme
  l'auteur par son prénom vérifié par le KYC (« (tuteur) » pour un autre tuteur) et le champ modifié, jamais
  la valeur. Trois écarts : « Né(e) » faute de sexe déclaré dans le dossier ; la ligne « Bracelet » donne le
  numéro sans la couleur de la sangle, que la plateforme ne connaît pas ; les rubriques de l'enfant (carte,
  Safe Zones, trajets, journal, bracelet, fiche médicale, contacts, page QR) restent en liste au-dessus de
  « Modifier la fiche », la maquette ne disant pas d'où on les ouvre. Le cercle montre la photo de l'enfant
  (ADR 0033), sinon son initiale ; la photo se choisit dans la feuille « Modifier la fiche ».
- **Écran 17, mes enfants.** Chaque carte reprend la maquette (photo dans un anneau à la couleur de l'état,
  « Prénom · âge », état, heure, fraîcheur, réseau, batterie). L'état dit ce que la plateforme sait : dans
  quelle Safe Zone est l'enfant, s'il en est sorti, s'il se déplace, sinon « Hors des Safe Zones » ; la
  maquette nomme un quartier (« Gounghin ») que la plateforme ne déduit pas d'une position, faute de
  géocodage inverse, et un retrait de bracelet que la liste ne relit pas. « + Ajouter un enfant » ouvre une
  nouvelle vérification de lien, propre à cet enfant.
- **Écran 34, tuteurs et contacts.** Voir ADR 0032. Les tuteurs sont nommés comme leur dossier KYC les a
  vérifiés ; leurs pastilles de droits sont les mêmes (position, alertes, retrait, escalade), le tableau 17
  de FG-DOC-06 ne distinguant pas le second tuteur. Les contacts secondaires sont groupés sous un titre
  « Contacts d'urgence » avec leurs règles (cinq au plus, SMS en cascade, page QR) ; leurs pastilles sont
  « SMS en cascade » et, s'il y a lieu, « Page QR », ce que la plateforme fait réellement (la maquette dit
  « Appel en cascade », or la cascade envoie des SMS). Le bouton « Inviter un second tuteur » n'apparaît que
  tant qu'il reste une place et qu'aucune invitation n'est en cours ; une invitation en cours a sa carte,
  avec son échéance ou la vérification en cours, et peut être annulée.
- **Écran 11, réponse à une invitation.** Le design ne montre pas l'arrivée d'un second tuteur invité. La
  première étape de la vérification lui présente l'invitation reçue (« Mariam vous invite comme second
  tuteur »), choisie d'emblée, avec la possibilité d'inscrire plutôt un autre enfant ; l'étape du lien ne lui
  demande alors pas l'identité de l'enfant, et le récapitulatif indique « Second tuteur, sur invitation de… ».
  Mêmes cartes de choix, mêmes composants que le reste du parcours.
- **Écrans 21 et 22, Safe Zones.** Liste et édition reprennent la maquette (pastilles carrées, état
  « Active » ou « Hors plage horaire », forme et horaires). Le quartier de la zone (« Ouaga 2000 ») n'est pas
  affiché : la plateforme ne déduit pas de nom de quartier d'une position (pas de géocodage inverse).
  L'édition propose cinq types de lieu et le nom libre, que la maquette réduit à quatre étiquettes.
- **Écrans 24 et 27, centre des alertes.** Un seul écran tient les deux maquettes : en tête, un bloc ambre
  par enfant dont des alertes attendent une prise en charge (« 2 alertes · Awa », classées par gravité,
  numérotées, « Tout prendre en charge ») ; dessous, le fil par jour (« Aujourd'hui », « Hier ») où alertes
  et informations se lisent ensemble. Le point bleu marque une information non lue ou une alerte qui attend
  quelqu'un ; « Tout marquer lu » ne vaut que pour les informations, une alerte se prend en charge. La lecture
  est tenue par le serveur (V31), pour être la même sur tous les appareils du parent. Le bouton « Signaler
  une disparition » reste en bas de l'écran : la maquette ne dit pas d'où l'ouvrir hors du tableau de bord.
  « Voir en direct » de la maquette 27 est rendu par l'écran de l'alerte, qui montre la carte.
- **Écran 32, fiche médicale.** La lecture suit la maquette : une carte par information, sa bascule
  « Critique / Privé » enregistrée aussitôt (une révision par bascule, comme tout enregistrement), la dernière
  révision avec le prénom de son auteur et le journal des révisions sur demande. Deux ajouts : « Modifier les
  informations » ouvre le formulaire d'ajout, de correction et de retrait, que la maquette ne montre pas ; le
  groupe sanguin vient après les autres informations. La maquette range le médecin traitant parmi les
  informations : la fiche n'a pas de type « Médecin », il se saisit sous « Autre ».
