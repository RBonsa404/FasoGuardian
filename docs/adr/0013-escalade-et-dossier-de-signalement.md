# ADR 0013 — Escalade vers les forces de sécurité et dossier de signalement

Statut : acceptée — 9 octobre 2026

## Contexte

FG-DOC-05 (US-PAR-010) : en cas de disparition confirmée avec second facteur, « identité, photo, informations
médicales pertinentes et trajet récent sont transmis selon le protocole et journalisés » ; « en l'absence de
convention avec les forces de sécurité, un dossier de signalement est généré pour remise par le parent ».
FG-DOC-04 (§5, REQ-SYS-022) : la convention doit être signée et un point de contact désigné avant toute
activation en production. Aucune convention n'existe aujourd'hui. Le design (écran 29) annonce que le dossier
« reste consultable 30 jours ».

## Décision

1. **La passerelle existe, et refuse.** `PasserelleFds` est le port de la couche anticorruption ; son seul
   adaptateur, `PasserelleSansConvention`, reflète l'état réel du projet : il ne transmet rien. Ce n'est pas un
   bac à sable. L'escalade produit donc toujours, aujourd'hui, un dossier à remettre par le parent
   (`REMISE_PAR_LE_PARENT`) ; le canal `PASSERELLE` est prévu pour l'adaptateur du protocole convenu.
2. **Conditions de l'escalade.** Seule une alerte prise en charge (`ACQUITTEE`) peut être escaladée, par un
   tuteur de l'enfant, avec le second facteur `ESCALADER_FORCES_SECURITE`. L'état est vérifié avant le code,
   pour ne pas en consommer un inutilement. Le parent voit d'abord un aperçu de ce que contiendra le dossier.
3. **Contenu du dossier (PDF, une à deux pages).** Identité et âge, taille, signes distinctifs, école,
   quartier, numéro gravé du bracelet, informations médicales *marquées critiques* par le parent, nature et
   journal de l'alerte, dernière position et positions des deux dernières heures (quarante au plus dans le
   tableau). Les « informations médicales pertinentes » sont entendues comme celles que le parent a déjà
   choisi d'exposer à un tiers en cas d'urgence ; le reste de la fiche médicale n'y figure pas.
4. **Pas de photo.** La photo de l'enfant n'existe pas encore dans la plateforme (US-PAR-004, à faire) : le
   dossier ne la contient pas. Ce manque est un écart assumé à US-PAR-010, à combler avec la photo.
5. **Conservation.** Le dossier est chiffré (catégorie `SIGNALEMENT`, clé `FG_CLE_SIGNALEMENT`),
   téléchargeable par les tuteurs pendant 30 jours, puis effacé chaque nuit. Sa référence (`FG-SIG-000412`) et
   son empreinte SHA-256 restent : elles prouvent ce qui a été remis sans conserver les données.
6. **Journalisation.** L'aperçu, l'escalade, la génération, chaque téléchargement et l'effacement sont
   inscrits au journal d'audit ; l'escalade l'est aussi au journal d'acquittement de l'alerte.
7. **Bibliothèque.** Le PDF est produit avec OpenPDF 1.3 (LGPL 2.1 / MPL 2.0), utilisée comme dépendance non
   modifiée, ce qui est compatible avec la licence MIT du projet.

## Conséquences

- Le dossier ne porte pas les coordonnées du tuteur : il est remis en main propre. Elles devront y figurer
  quand la passerelle transmettra le dossier sans le parent.
- L'accusé de réception par les forces de sécurité (US-FDS-001, COULD) attend la convention ; la colonne
  `accuse_le` est prête.
- Une alerte escaladée ne peut plus être classée en fausse alerte : elle se lève, avec un motif.
- L'escalade ne déclenche aucune notification vers l'extérieur. Tant que la convention n'existe pas, rien ne
  doit laisser croire au parent que la Police a été prévenue : l'écran le dit explicitement.
