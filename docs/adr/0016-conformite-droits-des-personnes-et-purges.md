# ADR 0016 — Conformité : garde-fou AIPD, droits d'accès et d'effacement, purges

Statut : acceptée — 9 octobre 2026

## Contexte

US-ADM-003 demande trois choses : la mise en production avec données réelles est bloquée tant que l'AIPD n'est
pas documentée ; les positions échues sont purgées sans intervention ; une demande d'effacement, une fois
traitée, supprime les données et délivre un accusé. FG-DOC-04 fixe le délai à trente jours ; FG-DOC-06
(tableau 18) donne les durées de conservation, « soumises à validation de la CIL ». US-ADM-002 demande un
journal non modifiable dont la rupture est « détectée et signalée ». Le délégué à la protection des données
n'a pas de compte : il travaille à partir des rapports de l'administrateur.

## Décision

1. **Garde-fou AIPD.** Sous le profil `prod`, le serveur refuse de démarrer tant que `FG_AIPD_REFERENCE`,
   `FG_AIPD_VALIDEE_LE` (date passée) et `FG_DELEGUE_PROTECTION_DONNEES` ne sont pas renseignés. Dans les
   autres profils il démarre, et la console affiche « Mise en production bloquée ». Le blocage est technique,
   pas une consigne : on ne peut pas l'oublier.
2. **Droit d'accès, servi aussitôt.** Le parent télécharge depuis ses réglages un fichier JSON de tout ce que
   la plateforme détient sur lui et ses enfants. Chaque export est une demande enregistrée (`ACC-…`), comptée
   au tableau de bord et journalisée. Aucun agent n'intervient : le délai est nul et personne d'autre ne voit
   les données.
3. **Droit à l'effacement.** La clôture du compte, confirmée par code SMS, vaut demande d'effacement
   (`EFF-…`, rappelée dans le SMS). L'administrateur l'exécute depuis la console ; à défaut, le système
   l'exécute lui-même au vingt-cinquième jour, pour que le délai légal de trente jours ne soit jamais dépassé.
   L'accusé part par SMS au moment de l'effacement, avant la destruction du numéro.
4. **Chaque module efface ce qu'il détient.** Le port `DonneesPersonnelles` (exporter, effacer) est implémenté
   par identite, famille, dispositifs, telemetrie, geolocalisation, alertes, notifications et abonnements ;
   le module audit les appelle sans connaître leurs tables. Un enfant qui garde un autre tuteur n'est pas
   effacé : seul le lien du demandeur disparaît.
5. **Ce qui est supprimé, ce qui est conservé.**

   | Donnée | À l'effacement | Raison |
   |---|---|---|
   | Numéro, mot de passe, sessions, consentements, liens de tutelle | supprimés ; le numéro redevient libre | — |
   | Fiche de l'enfant, fiche santé, contacts, page QR, consultations | supprimés | — |
   | Positions, événements et état du bracelet sur les périodes d'appairage | supprimés | — |
   | Safe Zones et franchissements | supprimés | — |
   | Notifications et abonnements push | supprimés | — |
   | Bracelet | désappairé, rendu au service après-vente | l'historique du parc reste, sans personne derrière l'identifiant |
   | Dossier de signalement (PDF) | détruit | il porte l'identité de l'enfant |
   | Alertes et journal d'acquittement | conservés 5 ans | finalité probatoire (tableau 18) ; rattachés à un identifiant qui ne désigne plus personne |
   | Pièces et dossier KYC | conservés un an après la clôture, puis purgés | tableau 18 |
   | Reçus de paiement | conservés ; numéro du portefeuille de renouvellement détruit | pièces comptables, numéro masqué seulement |
   | Journal d'audit, journal des révisions de santé | conservés | en ajout seul ; identifiants techniques et décomptes uniquement |

6. **Purges et registre.** Chaque purge planifiée s'inscrit au registre (`audit.execution_purge`) : positions
   (30 jours ; 24 heures pour l'offre Essentiel, 90 jours pour l'offre Premium), pièces KYC, consultations de
   la page QR (12 mois), numéros des tiers (30 jours), dossiers de signalement (30 jours), notifications
   (90 jours). Le tableau de bord en tire « purges exécutées : n jours sur n », le rapport mensuel le détail.
7. **Rapport mensuel en PDF**, sans aucune donnée personnelle : état de l'AIPD, durées, purges, demandes
   reçues et exécutées (dont hors délai), intégrité du journal. Sa production est journalisée.
8. **Journal d'audit consultable.** L'administrateur le parcourt et le filtre ; cette consultation est
   elle-même journalisée. Chaque contrôle de la chaîne, quotidien ou demandé depuis la console, consigne son
   résultat (`CHAINE_VERIFIEE` ou `CHAINE_ROMPUE`) : l'écran affiche l'état du dernier contrôle.

## Écarts et points à trancher

- **Offre Essentiel : 24 heures d'historique.** FG-DOC-11 ne lui attribue aucun historique ; l'écran 71 du
  design indique « 24 h / 30 j ». L'offre Essentiel montre et conserve donc 24 heures de positions.
- **Purge des alertes à 5 ans.** Le journal d'acquittement est protégé contre toute suppression par un
  déclencheur de base. La purge à cinq ans n'est pas automatisée : aucune alerte n'aura cet âge avant 2031, et
  la procédure (archivage puis suppression sous contrôle) est à définir avec la CIL.
- **Latence corrigée.** Les partitions mensuelles du journal des consultations de la page QR n'étaient créées
  que pour trois mois ; la purge quotidienne prépare désormais les suivantes.
- **Durées « soumises à validation de la CIL ».** Elles sont celles du tableau 18 ; toute modification
  demandée par la CIL se fait dans les purges et dans la table présentée par le rapport.
- **Demandes hors application.** Une demande d'accès ou d'effacement reçue par courrier ou téléphone n'a pas
  d'écran de saisie : l'administrateur invite la personne à utiliser l'application, ou le support la
  traitera avec les demandes de support (US-PAR-017).

## Conséquences

- Trois variables d'environnement nouvelles, obligatoires en production.
- Un compte effacé laisse une ligne technique sans numéro ni mot de passe, pour l'intégrité des références.
- Tout nouveau module qui conserve des données personnelles doit fournir un `DonneesPersonnelles` et inscrire
  ses purges au registre.
