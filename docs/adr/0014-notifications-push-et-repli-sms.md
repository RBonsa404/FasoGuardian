# ADR 0014 — Notifications : Web Push, repli SMS et choix du canal

Statut : acceptée — 9 octobre 2026

## Contexte

FG-DOC-05 demande une notification push, « puis un SMS en l'absence d'accusé sous 60 s » (US-ENF-001), et
« push et SMS » pour un SOS (US-ENF-002). FG-DOC-07 (§4.4) prévoit une `Notification`, une interface
`CanalNotification` et ses implémentations Web Push et SMS ; FG-DOC-06 (§6.6) interdit toute donnée de santé
ou coordonnée dans un message. Jusqu'ici, chaque module envoyait ses SMS lui-même, en passant par le module
identite pour obtenir le numéro.

## Décision

1. **Un seul point d'entrée.** Les modules appellent `Notifications.notifier(destinataire, urgence, message)` ;
   le module notifications choisit le canal. Le numéro de téléphone lui est fourni par le port `Annuaire`,
   implémenté par identite : aucun autre module ne le manipule, et il n'est jamais conservé avec une
   notification.
2. **Règle de choix du canal.**
   - *Critique* (SOS, retrait, signalement) : push et SMS, tout de suite.
   - *Importante* (sortie de zone, batterie critique, chute, rappel de fin de retrait, bracelet scanné) :
     push, puis SMS au bout de 60 secondes sans accusé.
   - *Information* (bracelet associé, mode économie…) : push seulement.
   - **Sans abonnement push, ou si le push échoue, le SMS part aussitôt, quelle que soit l'urgence** : il est
     alors le seul canal, attendre 60 secondes n'aurait pas de sens.
3. **Accusé.** Une notification est accusée quand le navigateur en confirme la réception (le service worker
   appelle une adresse publique portant l'identifiant de la notification, aléatoire et transmis chiffré), ou
   quand son objet est traité (l'alerte est prise en charge ou close). L'un ou l'autre annule le repli SMS.
4. **Web Push sans bibliothèque tierce.** Le chiffrement du contenu (RFC 8291, aes128gcm) et le jeton du
   serveur d'application (RFC 8292, ES256) sont écrits avec les seules API cryptographiques de Java, une
   centaine de lignes vérifiées par un test qui joue le navigateur avec sa propre implémentation du
   déchiffrement. Le service de push ne voit jamais le contenu.
5. **Adresses de livraison limitées.** L'adresse d'un abonnement vient du navigateur : le serveur ne la suit
   que si elle est en HTTPS et désigne un service de push connu (Google, Mozilla, Apple, Microsoft), sans
   redirection. Sans cette liste, l'abonnement serait un moyen de faire appeler une adresse interne par le
   serveur.
6. **Envoi après validation.** Une notification est acheminée après la validation de la transaction qui l'a
   créée : on ne prévient pas d'un fait qui n'a pas eu lieu, et aucun appel réseau ne retient une transaction.
7. **Contenu.** Un titre, une phrase et un chemin de l'application ; ni position, ni nom de lieu, ni donnée
   de santé. Les notifications sont effacées au bout de 90 jours.
8. **Service worker dédié.** L'application Parents enregistre un service worker minimal (`sw.js`), et
   seulement quand le parent active les notifications. La mise en cache hors ligne (US-PAR-019) s'y ajoutera.

## Conséquences

- Le repli SMS des alertes ne dépend plus de la seule prise en charge de l'alerte : un parent qui a reçu la
  notification sur son téléphone ne reçoit pas de SMS en plus, ce qui réduit le coût des SMS.
- Un parent abonné dont le téléphone est hors réseau reçoit le SMS au bout de 60 secondes : c'est le délai
  voulu par FG-DOC-05, pas un défaut.
- L'affichage réel des notifications et la livraison par les services de push ne sont pas vérifiables par les
  tests automatisés ; le protocole `docs/essais/notifications-push.md` les couvre.
- Sur iPhone, les notifications push n'existent que pour l'application ajoutée à l'écran d'accueil.
- Les clés du serveur d'application (`FG_PUSH_*`) sont facultatives : sans elles, tout passe par SMS.
  Leur rotation invalide tous les abonnements, que les navigateurs devront renouveler.
