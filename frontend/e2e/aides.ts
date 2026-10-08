import { createHmac } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';

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

const ETAT_ADMIN = join(__dirname, '.etat', 'admin.json');

/**
 * Jeton de l'administrateur d'amorçage. Son second facteur ne s'active qu'une fois par base : le secret
 * et le dernier jeton sont conservés dans e2e/.etat (ignoré par Git) pour les tests suivants.
 */
async function jetonAdmin(api: APIRequestContext): Promise<string> {
  const identifiant = process.env['FG_ADMIN_IDENTIFIANT'] ?? '';
  const motDePasse = process.env['FG_ADMIN_MOT_DE_PASSE'] ?? '';
  const chemin = `${SERVEUR}/api/v1/auth/agents/connexion`;
  const etat: { secret?: string; jeton?: string; expire?: number } = existsSync(ETAT_ADMIN)
    ? JSON.parse(readFileSync(ETAT_ADMIN, 'utf8'))
    : {};
  if (etat.jeton && etat.expire && etat.expire > Date.now() + 30_000) {
    return etat.jeton;
  }
  if (!etat.secret) {
    const premiere = await api.post(chemin, { data: { identifiant, motDePasse } });
    expect(premiere.status(), 'administrateur : base vierge attendue (voir e2e/README.md)').toBe(403);
    etat.secret = (await premiere.json()).secretTotp;
  }
  const session = await api.post(chemin, { data: { identifiant, motDePasse, codeTotp: codeTotp(etat.secret!) } });
  expect(session.status(), "connexion de l'administrateur").toBe(200);
  const corps = await session.json();
  etat.jeton = corps.jetonAcces;
  etat.expire = Date.now() + corps.expireDansSecondes * 1000;
  mkdirSync(dirname(ETAT_ADMIN), { recursive: true });
  writeFileSync(ETAT_ADMIN, JSON.stringify(etat));
  return etat.jeton!;
}

export interface AgentCree {
  readonly identifiant: string;
  readonly motDePasse: string;
}

/** Crée un agent aux rôles donnés, sans le connecter : il activera son second facteur à sa première connexion. */
export async function creerAgent(api: APIRequestContext, roles: string[]): Promise<AgentCree> {
  const identifiant = `agent.${roles[0].toLowerCase()}.${Date.now()}`;
  const motDePasse = `phrase-de-passe-${Date.now()}`;
  const creation = await api.post(`${SERVEUR}/api/v1/admin/agents`, {
    headers: { Authorization: `Bearer ${await jetonAdmin(api)}` },
    data: { identifiant, motDePasseProvisoire: motDePasse, roles },
  });
  expect(creation.status()).toBe(201);
  return { identifiant, motDePasse };
}

/** Crée un agent KYC et le connecte par l'API. */
export async function agentKyc(api: APIRequestContext): Promise<string> {
  const agent = await creerAgent(api, ['KYC']);
  return connecterAgent(api, agent.identifiant, agent.motDePasse);
}

/** Image PNG valide d'un pixel. */
export const PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==',
  'base64',
);

/** Inscrit un parent et dépose pour lui un dossier KYC complet, par l'API ; renvoie la référence du dossier. */
export async function parentAvecDossierDepose(api: APIRequestContext): Promise<{ telephone: string; reference: string }> {
  const telephone = `72${String(Date.now()).slice(-6)}`;
  const auth = `${SERVEUR}/api/v1/auth/inscription`;
  expect((await api.post(`${auth}/numero`, { data: { telephone } })).status()).toBe(202);
  const code = await dernierCodeSms(api, telephone);
  const { preuve } = await (await api.post(`${auth}/code`, { data: { telephone, code } })).json();
  const { jetonAcces } = await (
    await api.post(`${auth}/terminer`, {
      data: { preuve, motDePasse: 'soleil-de-ouaga-2026', consentements: ['CONDITIONS_GENERALES', 'DONNEES_ENFANT'] },
    })
  ).json();
  const entetes = { Authorization: `Bearer ${jetonAcces}` };
  const kyc = `${SERVEUR}/api/v1/kyc/dossiers`;
  const dossier = await (
    await api.post(kyc, {
      headers: entetes,
      data: {
        canal: 'EN_LIGNE',
        natureLien: 'PARENT',
        demandeur: { nom: 'Zongo', prenoms: 'Aminata', typePiece: 'CNIB', numeroPiece: 'B76543210' },
        enfant: { prenom: 'Yacouba', nom: 'Zongo', dateNaissance: '2017-05-02' },
      },
    })
  ).json();
  const pieces: [string, string, string, Buffer][] = [
    ['PIECE_RECTO', 'recto.png', 'image/png', PNG],
    ['ACTE_NAISSANCE', 'acte.pdf', 'application/pdf', Buffer.from('%PDF-1.7 acte de naissance de test')],
  ];
  for (const [type, name, mimeType, buffer] of pieces) {
    const envoi = await api.post(`${kyc}/${dossier.id}/pieces`, {
      headers: entetes,
      multipart: { type, fichier: { name, mimeType, buffer } },
    });
    expect(envoi.status()).toBe(201);
  }
  expect((await api.post(`${kyc}/${dossier.id}/depot`, { headers: entetes })).status()).toBe(200);
  return { telephone, reference: dossier.reference };
}
