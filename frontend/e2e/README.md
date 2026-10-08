# Tests de bout en bout (Playwright)

Parcours critiques joués dans un vrai navigateur, contre le vrai serveur et une vraie base.

| Fichier | Parcours | User stories |
|---|---|---|
| `inscription-kyc.spec.ts` | Inscription du parent, dépôt du dossier, validation par un agent KYC, compte activé | US-PAR-001 |
| `console-kyc.spec.ts` | Enrôlement TOTP d'un agent, instruction et validation d'un dossier dans la console ; refus opposé à un opérateur support | US-ADM-001, US-PAR-001 |

## Lancer les tests

Ils exigent une **base vierge** : le premier administrateur active son second facteur pendant le test, et un
secret TOTP déjà activé ne se relit pas.

1. Démarrer PostgreSQL : `docker compose --env-file .env -f infra/docker-compose.yml up -d postgres`
2. Lancer le serveur sous le profil `dev`, avec `FG_SMS_ADAPTATEUR=bac-a-sable` et les variables
   `FG_ADMIN_IDENTIFIANT` / `FG_ADMIN_MOT_DE_PASSE` (voir `.env.example`).
3. Servir les deux applications : `npx ng serve parents --port 4201 --proxy-config proxy.dev.json` et
   `npx ng serve console --port 4202 --proxy-config proxy.dev.json`
4. Avec les deux mêmes variables `FG_ADMIN_*` dans l'environnement : `npm run e2e`

`FG_E2E_PARENTS`, `FG_E2E_CONSOLE` et `FG_E2E_SERVEUR` changent les adresses par défaut (ports 4201, 4202 et
8080). Le secret TOTP de l'administrateur de test est gardé dans `e2e/.etat` (ignoré par Git) : supprimez ce
dossier quand vous repartez d'une base vierge. Ces tests ne tournent pas encore dans l'intégration continue.
