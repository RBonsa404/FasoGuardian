# Tests de bout en bout (Playwright)

Parcours critiques joués dans un vrai navigateur, contre le vrai serveur et une vraie base.

| Fichier | Parcours | User stories |
|---|---|---|
| `inscription-kyc.spec.ts` | Inscription du parent, dépôt du dossier, validation par un agent KYC, compte activé | US-PAR-001 |
| `compte.spec.ts` | Mot de passe oublié par code SMS, puis clôture du compte confirmée par second facteur | US-PAR-002 |
| `console-kyc.spec.ts` | Enrôlement TOTP d'un agent, instruction et validation d'un dossier dans la console ; refus opposé à un opérateur support | US-ADM-001, US-PAR-001 |

## Lancer les tests

`e2e/pile.sh` monte une pile locale jetable : PostgreSQL vierge dans Docker, serveur sous le profil `dev`
avec l'adaptateur SMS bac à sable, applications Parents (4201) et Console (4202). Ses secrets sont tirés au
hasard à chaque démarrage et gardés dans `e2e/.etat`, ignoré par Git.

```bash
sh e2e/pile.sh demarrer
```

```bash
sh e2e/pile.sh tester
```

```bash
sh e2e/pile.sh arreter
```

`JAVA_HOME` doit pointer sur un JDK 21. Sous Git Bash, lancez `demarrer` sans rediriger sa sortie vers une
autre commande : les serveurs lancés en arrière-plan la garderaient ouverte.

La base doit être vierge à chaque démarrage : le premier administrateur active son second facteur pendant
les tests, et un secret TOTP déjà activé ne se relit pas. Ces tests ne tournent pas encore dans
l'intégration continue.
