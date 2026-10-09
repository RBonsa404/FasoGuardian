# ADR 0012 — Commandes signées de la plateforme vers le bracelet

Statut : acceptée — 9 octobre 2026

## Contexte

FG-DOC-08 mentionne des « commandes signées » (levée du mode alerte, autorisation de retrait, intervalle,
localisation immédiate) et un accusé d'exécution sur `fg/{deviceId}/ack`, sans en fixer le format. FG-DOC-05
(US-SYS-011) exige que le bracelet ignore toute commande non signée par la plateforme et signale la tentative.
Le TLS mutuel et l'ACL du broker protègent déjà le transport ; la signature protège le bracelet si le broker
ou son ACL sont compromis.

## Décision

1. **Signature ECDSA P-256 / SHA-256, au format brut R‖S (64 octets).** C'est ce que l'élément sécurisé
   ATECC608B vérifie nativement. La clé privée de la plateforme vient du coffre de secrets
   (`FG_CLE_COMMANDES`) ; un serveur relié au broker ne démarre pas sans elle. La clé publique est inscrite
   dans le logiciel embarqué.
2. **Message : `base64url(corps) "." base64url(signature)`.** La signature porte sur les octets exacts du
   corps, sans mise en forme canonique à refaire côté bracelet. Le corps est un JSON court :
   `{"id","dev","cmd","n","exp","p"}`.
3. **Quatre contrôles côté bracelet**, tous nécessaires : signature valide ; `dev` égal à son propre
   identifiant (une commande valable pour un autre bracelet ne vaut pas pour lui) ; `exp` non dépassé
   (15 minutes) ; `n` strictement supérieur au dernier numéro accepté (anti-rejeu). Un refus est signalé par
   `{"id":…,"ok":false}`.
4. **Suivi et réémission.** Chaque commande est enregistrée avec son message signé. Sans accusé au bout de
   30 secondes, le même message est republié, à l'identique, jusqu'à l'expiration ; le broker le garde pour un
   bracelet hors ligne (QoS 1, session persistante). Le bracelet reconnaît une commande déjà exécutée à son
   identifiant et l'accuse sans la rejouer.
5. **Qui émet.** Seuls les cas d'usage de la plateforme émettent des commandes : le système en réaction à un
   événement (mode alerte à l'ouverture d'une alerte critique, sortie du mode à la clôture de la dernière),
   ou un tuteur dont le lien avec l'enfant vient d'être vérifié (mode économie, retrait, localisation).
   Chaque émission est journalisée avec son auteur. « Localiser maintenant » est limité à une demande par
   minute et par enfant, pour ménager la batterie.
6. **Découplage des modules.** Le module dispositifs enregistre et signe ; il publie un événement que le
   module telemetrie, seul à tenir la connexion au broker, transmet. Les accusés suivent le chemin inverse.
7. **Tentative étrangère.** Un accusé négatif portant un identifiant que la plateforme n'a jamais émis est
   journalisé (`COMMANDE_ETRANGERE_REJETEE`) et compté : c'est le signal d'une tentative de prise de contrôle.

## Conséquences

- Le numéro `n` est dérivé de l'horloge du serveur (millisecondes) et du dernier numéro émis : il reste
  croissant après une restauration de la base, mais suppose une horloge du serveur qui ne recule pas de façon
  durable.
- Une commande qui n'a pas atteint le bracelet en 15 minutes est abandonnée. Pour le mode alerte, la suivante
  repart à la prochaine ouverture ou clôture d'alerte ; un bracelet resté hors ligne plus longtemps garde son
  dernier mode jusqu'à son retour, ce que le logiciel embarqué devra borner de lui-même.
- Le simulateur applique les mêmes contrôles que le bracelet et sert de référence exécutable au logiciel
  embarqué (`simulator/…/VerificateurCommandes.java` et ses tests).
- La rotation de la clé de la plateforme n'est pas encore prévue : elle demandera un champ de version de clé
  dans le corps et deux clés publiques dans le logiciel embarqué.
