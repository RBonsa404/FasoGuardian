# ADR 0008 — Chiffrement applicatif appelé par les cas d'usage

Statut : acceptée — 8 octobre 2026

## Contexte

FG-DOC-06 (§8.3) et FG-DOC-07 (§7.1) prévoient le chiffrement applicatif « par convertisseurs JPA appelant
ServiceChiffrement ». Un convertisseur est référencé par l'entité (`@Convert`) : le domaine dépendrait alors
d'une classe d'infrastructure et d'un service Spring, ce que les mêmes documents interdisent (« le domaine ne
dépend d'aucun cadre technique ») et que les tests d'architecture vérifient.

## Décision

Les entités portent les valeurs chiffrées (`byte[]`) et leur empreinte de recherche ; ce sont les cas d'usage
de la couche application qui appellent `ServiceChiffrement` pour chiffrer avant écriture et déchiffrer à la
lecture. L'algorithme, les clés par catégorie et les colonnes sont ceux des documents de conception.

## Conséquences

- Aucune valeur sensible ne peut être lue en clair par une simple requête JPA : le déchiffrement est un acte
  explicite, au seul endroit où l'autorisation est contrôlée et l'accès journalisé.
- Un test d'intégration lit la base pour vérifier que les colonnes sensibles sont illisibles.
- Écart de forme par rapport à FG-DOC-06 et FG-DOC-07, à reporter lors de leur prochaine révision.

## Complément — statut BROUILLON du dossier KYC

FG-DOC-07 fait débuter le cycle du dossier à DEPOSE. Un statut BROUILLON le précède : il porte le dossier
pendant que le parent ajoute ses pièces une à une (réseau 2G, reprise possible). Un brouillon n'est jamais
visible des agents.
