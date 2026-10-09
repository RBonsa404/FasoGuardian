# ADR 0009 — Cycle de vie du bracelet : identifiant d'appareil, perte, vol et révocation

Statut : acceptée — 8 octobre 2026

## Contexte

Quatre points du module dispositifs ne sont pas tranchés, ou sont tranchés différemment, par les documents
de référence :

1. FG-DOC-08 (§7) demande un identifiant d'appareil « distinct de l'IMEI » sans dire lequel, et l'ACL du
   broker (FG-DOC-06 §6.4) autorise chaque bracelet sur les seuls sujets `fg/{identifiant}/…`, l'identifiant
   étant le nom commun de son certificat.
2. FG-DOC-05 (US-PAR-014) impose, pour un vol déclaré, la désactivation de la page publique **et** la
   révocation du certificat. Le paquet de design (écran 39, écart 6 du HANDOFF) annonce pour une perte comme
   pour un vol que « le suivi continue 72 h », ce qui suppose que le bracelet se connecte encore.
3. L'écran 39 propose aussi le motif « Cassé » et annonce dans tous les cas que la page QR affichera
   « bracelet désactivé ».
4. Le certificat est émis à l'atelier (FG-DOC-08 §7.2) : le serveur ne détient pas la clé de l'autorité et
   ne peut donc pas signer lui-même une liste de révocation.

## Décision

1. **L'identifiant d'appareil est le numéro gravé** (`FG-2291`). Il est unique, distinct de l'IMEI (chiffré
   et consultable du seul service après-vente, consultation journalisée), déjà public puisqu'il est gravé et
   affiché sur la page QR, et il sert de nom commun au certificat. Les sujets MQTT sont donc
   `fg/FG-2291/telemetry`, etc.
2. **FG-DOC-05 prime sur le design pour le vol** : la déclaration de vol révoque le certificat aussitôt, il
   n'y a pas de suivi. **Pour une perte, le suivi de 72 h du design est retenu** : la page publique est
   désactivée tout de suite, le bracelet reste appairé et peut encore émettre pendant 72 h ; passé ce délai
   (traitement planifié toutes les dix minutes), l'appairage est clos et le certificat révoqué. Pendant le
   suivi, le parent peut déclarer le bracelet retrouvé (tout redevient actif) ou volé (révocation immédiate).
   L'écran 39 décrit la conséquence propre au motif choisi au lieu d'un texte unique.
3. **Un bracelet déclaré cassé garde sa page publique** tant qu'il n'est pas revenu au service après-vente :
   l'enfant peut encore le porter et le QR gravé, passif, continue de le protéger. L'unité passe « En SAV »
   et l'appairage est clos. La page n'est détachée de l'enfant qu'au retour de l'unité, à sa remise en stock
   ou à l'appairage d'un bracelet de remplacement.
4. **Le serveur est la source de la liste des certificats révoqués**, pas son signataire : il enregistre la
   date de révocation, l'expose au service après-vente (`GET /api/v1/console/parc/certificats-revoques`) et
   refusera, dès le module télémétrie, tout message d'un bracelet au certificat révoqué. La production de la
   liste de révocation signée et le rechargement du broker restent une opération de l'atelier, outillée à
   l'étape télémétrie (script d'infrastructure alimenté par cette API).

Autres règles retenues :

- Le code d'appairage compte sept caractères d'un alphabet sans signe ambigu (31 signes, environ 2,7 × 10¹⁰
  combinaisons), ne sert qu'une fois et n'est conservé que sous forme d'empreinte à clé. Cinq essais
  infructueux d'un même parent bloquent l'appairage pendant quinze minutes ; chaque refus est journalisé.
- Le code d'appairage et le jeton du QR ne sont remis qu'une fois, à l'enregistrement du bracelet au parc.
- Un désappairage sans déclaration renvoie l'unité au service après-vente : elle ne peut être appairée de
  nouveau qu'après remise en stock, qui lui donne un nouveau code.
- La déclaration (perte, vol, casse) exige le second facteur `DECLARER_BRACELET`.
- Le service après-vente ne voit jamais l'identité de l'enfant qui porte un bracelet : la fiche d'une unité
  ne montre que les périodes d'appairage.

## Conséquences

- Un bracelet volé cesse d'être localisable dès la déclaration : c'est le prix de l'exigence de FG-DOC-05,
  qui protège l'enfant contre le détournement du bracelet. Le parent qui hésite déclare d'abord une perte.
- Tant que le script de liste de révocation n'est pas livré (étape télémétrie), la révocation est enregistrée
  et exposée mais n'est pas encore appliquée par le broker : aucune donnée réelle ne circule avant cette étape.
- La demande de remplacement de l'écran 37 (30 000 FCFA, ou incluse sous garantie) relève du paiement et du
  suivi après-vente : elle est livrée avec les modules abonnements et console.

## Complément du 9 octobre 2026 — supervision et maintenance

- **Bracelet muet** (US-SAV-001). Chaque minute, la supervision compare le dernier contact de chaque bracelet
  actif et porté à trois fois son intervalle d'émission du moment (5 min, 15 min en mode économie ou pour
  l'offre Essentiel). Au-delà, le parent est informé et un ticket `SAV-…` est ouvert, un seul par bracelet. Il
  se clôt de lui-même quand le bracelet redonne des nouvelles, ou par l'agent qui dit comment il s'est résolu.
  Un bracelet dont l'émission périodique est suspendue (abonnement restreint) n'est pas attendu.
- **Ce que voit le service après-vente.** Le design (écrans 64 et 66) montre le prénom de l'enfant, le nom du
  parent et le quartier. FG-DOC-06 (tableau 17) limite ce rôle à « l'état des bracelets seulement » et prime
  sur le design : la console SAV montre le numéro de série, l'état, le dernier contact, la batterie et le
  réseau, et dit seulement si le bracelet est porté. C'est la plateforme qui prévient le parent.
- **Batterie** (US-SYS-007). Au franchissement de 20 % vers le bas, la plateforme active le mode économie par
  commande signée et avertit le parent, une fois ; à 30 %, elle lève ce mode. L'écart évite d'osciller autour
  du seuil. Un mode économie choisi par le parent n'est jamais levé d'office. Sous 10 %, l'alerte « batterie
  critique » existante prend le relais.
