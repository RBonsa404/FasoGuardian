// Résumé des comptes de démonstration, affiché par `sh e2e/pile.sh demo`.
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

const d = JSON.parse(readFileSync(join(import.meta.dirname, '..', '.etat', 'demo.json'), 'utf8'));
console.log(`
Comptes de démonstration (données fictives)
  Console  http://localhost:4202   ${Object.keys(d.agents).join(', ')}   mot de passe : ${d.motDePasseAgents}
           administrateur d'amorçage : ${d.administrateur.identifiant} / ${d.administrateur.motDePasse}
  Parents  http://localhost:4201   ${d.parents.actif} (compte actif, enfant Awa, bracelet ${d.bracelets.appaire})
                                   ${d.parents.enAttente} (dossier ${d.parents.dossierEnAttente} à instruire)
                                   mot de passe : ${d.parents.motDePasse}
  Bracelet en stock ${d.bracelets.enStock}, code d'appairage ${d.bracelets.codeEnStock}
  Page QR publique d'Awa : http://localhost:8080/q/${d.bracelets.jetonQr}
  Codes SMS reçus : http://localhost:8080/api/v1/dev/sms
  Codes à six chiffres des agents : sh e2e/pile.sh codes
`);
