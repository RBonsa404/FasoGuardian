# ADR 0028 — Thème clair : suivre le système, laisser le choix

Statut : acceptée — 9 octobre 2026

## Contexte

Le paquet de design dessine chaque écran en sombre et en clair, et la définition du « terminé » exige les
deux. Les jetons du thème clair étaient livrés (`[data-theme="clair"]`), mais aucune application ne posait
cet attribut : tout s'affichait en sombre, quel que soit le réglage de l'appareil. Le design ne dit pas
comment le thème se choisit.

## Décision

1. **Par défaut, l'apparence du système.** Un téléphone réglé en clair affiche l'application en clair, sans
   rien demander ; elle suit un changement de réglage sans rechargement.
2. **La personne peut imposer son choix** : « Système », « Sombre » ou « Clair », dans les paramètres de
   l'espace parent et dans la barre latérale de la console. Le choix est gardé sur l'appareil (clé
   `fg.theme`, qui ne dit rien de la personne) et s'applique dès l'ouverture suivante.
3. **Pas d'éclair de l'autre thème.** Un script d'une dizaine de lignes, servi par l'application elle-même
   (`theme.js`, compatible avec la politique de sécurité de contenu), pose l'attribut avant le premier
   affichage ; le service `Theme` de la bibliothèque `ui` prend ensuite le relais avec la même règle.
4. **Ce qui ne passe pas par un jeton** change avec la variante `clair:` : le logo fait pour un fond clair
   remplace celui du fond sombre. Les couleurs en dur restent interdites.

## Conséquences

- Le parcours `e2e/theme.spec.ts` vérifie le suivi du système, le choix, sa persistance, et produit les
  captures en clair des écrans principaux des deux applications.
- La page publique QR, sans JavaScript, suit déjà le système par `prefers-color-scheme`.
