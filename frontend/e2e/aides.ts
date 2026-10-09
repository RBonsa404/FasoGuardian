import { ChildProcess, execFileSync, spawn } from 'node:child_process';
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

export const MOT_DE_PASSE_PARENT = 'soleil-de-ouaga-2026';

/** Inscrit un parent par l'API ; renvoie son numéro et son jeton d'accès. */
export async function parentInscrit(api: APIRequestContext, prefixe = '73'): Promise<{ telephone: string; jetonAcces: string }> {
  const telephone = `${prefixe}${String(Date.now()).slice(-6)}`;
  const auth = `${SERVEUR}/api/v1/auth/inscription`;
  expect((await api.post(`${auth}/numero`, { data: { telephone } })).status()).toBe(202);
  const code = await dernierCodeSms(api, telephone);
  const { preuve } = await (await api.post(`${auth}/code`, { data: { telephone, code } })).json();
  const { jetonAcces } = await (
    await api.post(`${auth}/terminer`, {
      data: { preuve, motDePasse: MOT_DE_PASSE_PARENT, consentements: ['CONDITIONS_GENERALES', 'DONNEES_ENFANT'] },
    })
  ).json();
  return { telephone, jetonAcces };
}

/** Inscrit un parent et dépose pour lui un dossier KYC complet, par l'API ; renvoie la référence du dossier. */
export async function parentAvecDossierDepose(api: APIRequestContext): Promise<{ telephone: string; reference: string }> {
  const { telephone, jetonAcces } = await parentInscrit(api, '72');
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

/**
 * Parent dont le dossier KYC vient d'être approuvé par un agent : la fiche de son enfant (Yacouba) existe.
 * `reference` est celle du dossier KYC approuvé.
 */
export async function parentAvecEnfant(api: APIRequestContext): Promise<{ telephone: string; reference: string }> {
  const { telephone, reference } = await parentAvecDossierDepose(api);
  const entetes = { Authorization: `Bearer ${await agentKyc(api)}` };
  const file = await (await api.get(`${SERVEUR}/api/v1/console/kyc/dossiers?size=100`, { headers: entetes })).json();
  const dossier = file.elements.find((element: { reference: string }) => element.reference === reference);
  expect(dossier, `dossier ${reference} dans la file`).toBeTruthy();
  const base = `${SERVEUR}/api/v1/console/kyc/dossiers/${dossier.id}`;
  expect((await api.post(`${base}/prise-en-charge`, { headers: entetes })).status()).toBe(200);
  expect((await api.post(`${base}/decision`, { headers: entetes, data: { decision: 'APPROUVER' } })).status()).toBe(200);
  return { telephone, reference };
}

export interface CarteActivation {
  numeroSerie: string;
  codeAppairage: string;
  jetonQr: string;
}

/** IMEI fictif de 15 chiffres à clé de Luhn valide. */
function imeiFictif(): string {
  const chiffres = [3, 5, ...Array.from({ length: 12 }, () => Math.floor(Math.random() * 10))];
  const somme = chiffres.reduce((total, chiffre, index) => {
    const double = index % 2 === 1 ? chiffre * 2 : chiffre;
    return total + (double > 9 ? double - 9 : double);
  }, 0);
  return chiffres.join('') + ((10 - (somme % 10)) % 10);
}

/** Enregistre au parc un bracelet neuf par l'API du service après-vente ; renvoie sa carte d'activation. */
export async function braceletAuParc(api: APIRequestContext): Promise<CarteActivation> {
  const agent = await creerAgent(api, ['SAV']);
  const jeton = await connecterAgent(api, agent.identifiant, agent.motDePasse);
  const reponse = await api.post(`${SERVEUR}/api/v1/console/parc`, {
    headers: { Authorization: `Bearer ${jeton}` },
    data: {
      numeroSerie: `FG-${1000 + Math.floor(Math.random() * 9000)}`,
      imei: imeiFictif(),
      revisionMaterielle: 'V1',
      versionLogiciel: '2.4.1',
      empreinteCertificat: Array.from({ length: 64 }, () => Math.floor(Math.random() * 16).toString(16)).join(''),
    },
  });
  expect(reponse.status()).toBe(201);
  return reponse.json();
}

/**
 * Publie un message comme le ferait le bracelet : en MQTT, TLS mutuel, sous son propre certificat de
 * développement (émis à la demande). Le message traverse le broker et son ACL avant d'atteindre le serveur.
 */
export function braceletEmet(numeroSerie: string, flux: 'telemetry' | 'alert' | 'status', message: Record<string, unknown>): void {
  const racine = join(__dirname, '..', '..');
  execFileSync('sh', [join(racine, 'infra', 'generer-certificats-dev.sh'), numeroSerie], { stdio: 'ignore' });
  const certificats = '/mosquitto/certs';
  execFileSync(
    'docker',
    // Identifiant de session distinct de celui du bracelet simulé : le broker n'admet qu'une session par identifiant.
    ['exec', 'fasoguardian-mosquitto-1', 'mosquitto_pub', '-h', 'localhost', '-p', '8883', '-q', '1', '-i', `${numeroSerie}-essai`,
      '--cafile', `${certificats}/ca.crt`, '--cert', `${certificats}/bracelet-${numeroSerie}.crt`, '--key', `${certificats}/bracelet-${numeroSerie}.key`,
      '-t', `fg/${numeroSerie}/${flux}`, '-m', JSON.stringify(message)],
    { stdio: 'ignore' },
  );
}

export interface BraceletSimule {
  /** Tout ce que le simulateur a écrit depuis son démarrage. */
  sortie(): string;
  arreter(): void;
}

/**
 * Lance le simulateur pour un bracelet : il se connecte au broker sous son certificat, émet une position, puis
 * vérifie avec la clé publique de la plateforme, accuse et applique les commandes signées qu'il reçoit.
 */
export function simulerBracelet(numeroSerie: string): BraceletSimule {
  const racine = join(__dirname, '..', '..');
  const jar = join(racine, 'simulator', 'target', 'fasoguardian-simulateur-0.1.0-SNAPSHOT.jar');
  if (!existsSync(jar)) {
    throw new Error('Simulateur absent : cd simulator && ../backend/mvnw -f pom.xml package');
  }
  execFileSync('sh', [join(racine, 'infra', 'generer-certificats-dev.sh'), numeroSerie], { stdio: 'ignore' });
  const java = process.env['JAVA_HOME'] ? join(process.env['JAVA_HOME'], 'bin', 'java') : 'java';
  const processus: ChildProcess = spawn(java, [
    // La sortie du simulateur est lue en UTF-8, quel que soit l'encodage par défaut du poste.
    '-Dstdout.encoding=UTF-8', '-Dstderr.encoding=UTF-8',
    '-jar', jar, `--certificats=${join(racine, 'infra', 'certs')}`, `--bracelets=${numeroSerie}`, '--intervalle=PT10M',
    `--cle-plateforme=${join(__dirname, '.etat', 'commandes-publique.pem')}`,
  ]);
  let sortie = '';
  processus.stdout?.on('data', (morceau: Buffer) => (sortie += morceau.toString()));
  processus.stderr?.on('data', (morceau: Buffer) => (sortie += morceau.toString()));
  return { sortie: () => sortie, arreter: () => void processus.kill() };
}
