# ADR 0029 — Site vitrine : rendu côté serveur, bracelet 3D à la demande, rien de promis en trop

Statut : acceptée — 9 octobre 2026

## Contexte

Le paquet de design décrit un site public de huit écrans (accueil, bracelet, fonctionnement, offres,
établissements, sécurité et données, points relais, aide et pages légales) et une scène 3D du bracelet.
Plusieurs textes de la maquette décrivent des fonctions que le service n'a pas, ou des éléments qui
n'existent pas encore.

## Décision

1. **Pages statiques rendues à la construction.** Les douze routes sont prérendues : le contenu est lisible
   sans JavaScript et s'affiche vite sur un réseau lent. Le site n'appelle aucune interface de la plateforme.
2. **Le bracelet 3D est construit par du code**, porté du fichier « Bracelet 3D » du paquet de design : mêmes
   cotes, mêmes pièces, mêmes noms de nœuds. Il n'y a donc pas de fichier de modèle à télécharger.
   three.js n'est chargé qu'à l'entrée de la scène dans la fenêtre ; une illustration fixe tient la place
   avant cela, et définitivement si l'appareil demande l'économie de données, a peu de mémoire, n'a pas de
   WebGL ou réduit les animations. Sur la page du bracelet, le défilement fait tourner le modèle et ouvre la
   vue éclatée ; les pièces sont aussi décrites en texte.
3. **Le site dit ce que le service fait.** Les offres affichées sont celles du catalogue de la plateforme.
   Sont retirés ou reformulés, par rapport à la maquette :
   - « Safe Zones illimitées », « plusieurs enfants », « alerte prioritaire », « support dédié » (Premium) :
     le catalogue ne les contient pas ; l'abonnement est par enfant et Premium apporte 90 jours d'historique ;
   - « appel masqué » depuis la page QR : la page compose le numéro du contact choisi par le parent ;
   - la passerelle LoRaWAN de l'offre École : présentée comme une étude, pas comme une prestation (ADR 0024) ;
   - « Commander » : le site ne vend rien, le bouton mène aux points d'inscription ;
   - sangle gratuite la première année, paiement en point relais : non prévus par la plateforme.
4. **Aucune donnée recueillie.** Le formulaire de contact de la maquette n'est pas réalisé : il collecterait
   le nom et le numéro de personnes sans compte, sans base ni durée de conservation définies. La page de
   contact oriente vers l'application (support), les points relais et l'offre École.
5. **Thème** : comme les applications, le site suit le système et laisse le choix (ADR 0028).

## Points à trancher, et contenus à fournir par le porteur

- **Mentions légales** : adresse du siège, immatriculations, directeur de la publication, hébergeur.
- **Coordonnées du support** (téléphone, courriel) et, si un formulaire est voulu, sa base légale.
- **Points relais** : la liste publiée est indicative (quartiers réels, enseignes fictives) et le dit.
- **Textes légaux** : rédigés d'après le fonctionnement réel, marqués « projet » jusqu'à validation juridique.
- **Illustrations et photographies** : absentes du paquet de design ; le site emploie les pictogrammes du
  système à leur place.
- **Adresse de l'application des parents** depuis le site (`/app/` par défaut) : dépend du déploiement.
