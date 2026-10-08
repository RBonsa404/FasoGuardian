# Tests de bout en bout (Playwright)

Parcours critiques joués dans un vrai navigateur, contre le vrai serveur et une vraie base.

| Fichier | Parcours | User stories |
|---|---|---|
| `inscription-kyc.spec.ts` | Inscription du parent, dépôt du dossier, validation par un agent KYC, compte activé | US-PAR-001 |

## Lancer les tests

Ils exigent une **base vierge** : le premier administrateur active son second facteur pendant le test, et un
secret TOTP déjà activé ne se relit pas.

1. Démarrer PostgreSQL : `docker compose --env-file .env -f infra/docker-compose.yml up -d postgres`
2. Lancer le serveur sous le profil `dev`, avec `FG_SMS_ADAPTATEUR=bac-a-sable` et les variables
   `FG_ADMIN_IDENTIFIANT` / `FG_ADMIN_MOT_DE_PASSE` (voir `.env.example`).
3. Servir l'application : `npx ng serve parents --port 4201 --proxy-config proxy.dev.json`
4. Avec les deux mêmes variables `FG_ADMIN_*` dans l'environnement : `npm run e2e`

`FG_E2E_PARENTS` et `FG_E2E_SERVEUR` changent les adresses par défaut (`http://localhost:4201` et
`http://localhost:8080`). Ces tests ne tournent pas encore dans l'intégration continue.
