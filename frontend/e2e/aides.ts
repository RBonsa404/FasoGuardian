import { createHmac } from 'node:crypto';

import { APIRequestContext, expect } from '@playwright/test';

export const SERVEUR = process.env['FG_E2E_SERVEUR'] ?? 'http://localhost:8080';

const ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';

function depuisBase32(base32: string): Buffer {
  let bits = '';
  for (const caractere of base32) {
    bits += ALPHABET.indexOf(caractere).toString(2).padStart(5, '0');
  }
  const octets = bits.match(/.{8}/g) ?? [];
  return Buffer.from(octets.map((octet) => parseInt(octet, 2)));
}

/** Code TOTP (RFC 6238, SHA-1, 6 chiffres, 30 s) du pas de temps courant. */
export function codeTotp(secretBase32: string, decalagePas = 0): string {
  const pas = Buffer.alloc(8);
  pas.writeBigUInt64BE(BigInt(Math.floor(Date.now() / 30_000) + decalagePas));
  const sceau = createHmac('sha1', depuisBase32(secretBase32)).update(pas).digest();
  const decalage = sceau[sceau.length - 1] & 0x0f;
  return String((sceau.readUInt32BE(decalage) & 0x7fffffff) % 1_000_000).padStart(6, '0');
}

/** Dernier code à six chiffres envoyé à ce numéro par l'adaptateur SMS bac à sable. */
export async function dernierCodeSms(api: APIRequestContext, telephone: string): Promise<string> {
  const messages: { destinataire: string; texte: string }[] = await (await api.get(`${SERVEUR}/api/v1/dev/sms`)).json();
  const code = messages
    .filter((message) => message.destinataire === `+226${telephone}`)
    .map((message) => message.texte.match(/\d{6}/)?.[0])
    .filter(Boolean)
    .pop();
  expect(code, `aucun code SMS pour ${telephone}`).toBeTruthy();
  return code!;
}

/** Connecte un agent en activant son second facteur à la première connexion ; renvoie son jeton d'accès. */
export async function connecterAgent(api: APIRequestContext, identifiant: string, motDePasse: string): Promise<string> {
  const chemin = `${SERVEUR}/api/v1/auth/agents/connexion`;
  const premiere = await api.post(chemin, { data: { identifiant, motDePasse } });
  expect(premiere.status(), 'première connexion : second facteur à activer').toBe(403);
  const { secretTotp } = await premiere.json();
  const session = await api.post(chemin, { data: { identifiant, motDePasse, codeTotp: codeTotp(secretTotp) } });
  expect(session.status()).toBe(200);
  return (await session.json()).jetonAcces;
}

/** Crée un agent KYC par l'administrateur d'amorçage et le connecte. */
export async function agentKyc(api: APIRequestContext): Promise<string> {
  const admin = await connecterAgent(
    api,
    process.env['FG_ADMIN_IDENTIFIANT'] ?? '',
    process.env['FG_ADMIN_MOT_DE_PASSE'] ?? '',
  );
  const identifiant = `agent.kyc.${Date.now()}`;
  const motDePasseProvisoire = `phrase-de-passe-${Date.now()}`;
  const creation = await api.post(`${SERVEUR}/api/v1/admin/agents`, {
    headers: { Authorization: `Bearer ${admin}` },
    data: { identifiant, motDePasseProvisoire, roles: ['KYC'] },
  });
  expect(creation.status()).toBe(201);
  return connecterAgent(api, identifiant, motDePasseProvisoire);
}
