# Contribuer à FasoGuardian

## Branches et commits

- `main` est protégée : toute modification passe par une demande de fusion relue par un autre membre.
- Branches : `feat/…`, `fix/…`, `chore/…`.
- Commits au format [Conventional Commits](https://www.conventionalcommits.org/fr/), en français, petits et
  cohérents : `feat(alertes): escalade vers les forces de sécurité`.
- Un tag par étape terminée du plan de réalisation.

## Définition du « terminé » d'une user story

1. Tous ses critères d'acceptation (Étant donné / Quand / Alors) sont couverts par des tests automatisés verts.
2. L'écran correspondant est fidèle au design, en thème sombre et clair, avec tous ses états.
3. L'autorisation est testée (accès permis et refusé) et les accès sensibles sont journalisés.
4. L'API est documentée dans l'OpenAPI.
5. `docs/tracabilite.md` est à jour.

## Règles de code

- Serveur : un paquet par module (`bf.fasoguardian.<module>`), organisé en `web`, `application`, `domaine`,
  `infrastructure`. Aucun accès aux classes internes d'un autre module ; le domaine ne dépend d'aucun cadre
  technique. Ces règles sont vérifiées à chaque construction.
- Vocabulaire métier en français sans accents dans le code (`Enfant`, `SafeZone`, `AutorisationRetrait`) ;
  éléments techniques en anglais (`Repository`, `Controller`).
- Schéma de base : uniquement par migration Flyway, un schéma PostgreSQL par module.
- Interface : aucune couleur ni taille codée en dur, uniquement les jetons de `projects/ui/src/styles`.
  L'ambre est réservé aux alertes.
- Aucun secret dans le dépôt ; données de démonstration fictives.
- Journaux techniques sans donnée personnelle : identifiants techniques uniquement.

## Avant de pousser

```bash
cd backend && ./mvnw verify
```

```bash
cd frontend && npm test && npm run build && npm run budgets -- parents
```

```bash
cd simulator && ../backend/mvnw -f pom.xml verify
```

## Contradictions entre documents

Ordre de priorité : FG-DOC-04 > FG-DOC-06 > FG-DOC-05 > FG-DOC-07 > FG-DOC-08 > design. Toute contradiction
est signalée et tranchée dans un ADR (`docs/adr/`), jamais ignorée.
