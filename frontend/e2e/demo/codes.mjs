// Codes à six chiffres du moment pour les comptes de démonstration : `sh e2e/pile.sh codes`.
// Ils changent toutes les trente secondes, comme dans une application d'authentification.
import { createHmac } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

const ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';

function code(secret) {
  const bits = [...secret].map((c) => ALPHABET.indexOf(c).toString(2).padStart(5, '0')).join('');
  const cle = Buffer.from((bits.match(/.{8}/g) ?? []).map((o) => parseInt(o, 2)));
  const pas = Buffer.alloc(8);
  pas.writeBigUInt64BE(BigInt(Math.floor(Date.now() / 30_000)));
  const sceau = createHmac('sha1', cle).update(pas).digest();
  const decalage = sceau[sceau.length - 1] & 0x0f;
  return String((sceau.readUInt32BE(decalage) & 0x7fffffff) % 1_000_000).padStart(6, '0');
}

const demo = JSON.parse(readFileSync(join(import.meta.dirname, '..', '.etat', 'demo.json'), 'utf8'));
const restantes = 30 - (Math.floor(Date.now() / 1000) % 30);
console.log(`Codes valables encore ${restantes} s :`);
console.log(`  ${demo.administrateur.identifiant.padEnd(14)} ${code(demo.administrateur.secret)}`);
for (const [identifiant, compte] of Object.entries(demo.agents)) {
  console.log(`  ${identifiant.padEnd(14)} ${code(compte.secret)}   (${compte.roles.join(', ')})`);
}
