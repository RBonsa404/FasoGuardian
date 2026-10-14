import { writeFileSync } from 'node:fs';
import { join } from 'node:path';

import { APIRequestContext, expect, test } from '@playwright/test';

import { MOT_DE_PASSE_PARENT, PNG, SERVEUR, braceletAuParc, braceletEmet, codeTotp, dernierCodeSms, journeeDeTrajet } from '../aides';

/**
 * Peuple une pile locale fraîche de comptes de démonstration, pour essayer l'application à la main
 * (docs/tester-en-local.md). Toutes les données sont fictives. Les secrets TOTP des agents sont écrits dans
 * e2e/.etat/demo.json, ignoré par Git ; `sh e2e/pile.sh codes` en donne les codes du moment.
 */

const ETAT = join(__dirname, '..', '.etat');
export const MOT_DE_PASSE_AGENT = 'demo-fasoguardian-2026';

const AGENTS: { identifiant: string; roles: string[] }[] = [
  { identifiant: 'demo.kyc', roles: ['KYC'] },
  { identifiant: 'demo.support', roles: ['SUPPORT'] },
  { identifiant: 'demo.sav', roles: ['SAV'] },
  { identifiant: 'demo.fds', roles: ['FDS'] },
  { identifiant: 'demo.admin', roles: ['ADMIN'] },
];

/** Numéros fictifs des parents de démonstration (format burkinabè, préfixe +226 ajouté par l'application). */
const PARENT_ACTIF = '70000001';
const PARENT_EN_ATTENTE = '70000002';
const SECOND_TUTEUR = '70000003';

async function jetonAdministrateur(api: APIRequestContext): Promise<{ jeton: string; secret: string }> {
  const identifiant = process.env['FG_ADMIN_IDENTIFIANT'] ?? '';
  const motDePasse = process.env['FG_ADMIN_MOT_DE_PASSE'] ?? '';
  const chemin = `${SERVEUR}/api/v1/auth/agents/connexion`;
  const premiere = await api.post(chemin, { data: { identifiant, motDePasse } });
  expect(premiere.status(), 'pile fraîche attendue : lancez « sh e2e/pile.sh demarrer » avant « demo »').toBe(403);
  const secret: string = (await premiere.json()).secretTotp;
  const session = await api.post(chemin, { data: { identifiant, motDePasse, codeTotp: codeTotp(secret) } });
  expect(session.status()).toBe(200);
  const corps = await session.json();
  // Les aides des tests (braceletAuParc) reprennent ce secret : le second facteur ne s'active qu'une fois.
  writeFileSync(join(ETAT, 'admin.json'), JSON.stringify({ secret, jeton: corps.jetonAcces, expire: Date.now() + corps.expireDansSecondes * 1000 }));
  return { jeton: corps.jetonAcces, secret };
}

/** Crée l'agent et active son second facteur ; renvoie son secret TOTP et un jeton. */
async function agent(api: APIRequestContext, admin: string, identifiant: string, roles: string[]): Promise<{ secret: string; jeton: string }> {
  const creation = await api.post(`${SERVEUR}/api/v1/admin/agents`, {
    headers: { Authorization: `Bearer ${admin}` },
    data: { identifiant, motDePasseProvisoire: MOT_DE_PASSE_AGENT, roles },
  });
  expect(creation.status(), `création de ${identifiant}`).toBe(201);
  const chemin = `${SERVEUR}/api/v1/auth/agents/connexion`;
  const premiere = await api.post(chemin, { data: { identifiant, motDePasse: MOT_DE_PASSE_AGENT } });
  const secret: string = (await premiere.json()).secretTotp;
  const session = await api.post(chemin, { data: { identifiant, motDePasse: MOT_DE_PASSE_AGENT, codeTotp: codeTotp(secret) } });
  expect(session.status()).toBe(200);
  return { secret, jeton: (await session.json()).jetonAcces };
}

async function inscrire(api: APIRequestContext, telephone: string): Promise<string> {
  const auth = `${SERVEUR}/api/v1/auth/inscription`;
  expect((await api.post(`${auth}/numero`, { data: { telephone } })).status()).toBe(202);
  const code = await dernierCodeSms(api, telephone);
  const { preuve } = await (await api.post(`${auth}/code`, { data: { telephone, code } })).json();
  const reponse = await api.post(`${auth}/terminer`, {
    data: { preuve, motDePasse: MOT_DE_PASSE_PARENT, consentements: ['CONDITIONS_GENERALES', 'DONNEES_ENFANT'] },
  });
  expect(reponse.ok(), `inscription de ${telephone}`).toBe(true);
  return (await reponse.json()).jetonAcces;
}

async function deposerDossier(api: APIRequestContext, jeton: string, demandeur: Record<string, string>, enfant: Record<string, string>): Promise<string> {
  const entetes = { Authorization: `Bearer ${jeton}` };
  const kyc = `${SERVEUR}/api/v1/kyc/dossiers`;
  const dossier = await (await api.post(kyc, { headers: entetes, data: { canal: 'EN_LIGNE', natureLien: 'PARENT', demandeur, enfant } })).json();
  for (const [type, name, mimeType, buffer] of [
    ['PIECE_RECTO', 'recto.png', 'image/png', PNG],
    ['ACTE_NAISSANCE', 'acte.pdf', 'application/pdf', Buffer.from('%PDF-1.7 acte de naissance fictif')],
  ] as [string, string, string, Buffer][]) {
    expect((await api.post(`${kyc}/${dossier.id}/pieces`, { headers: entetes, multipart: { type, fichier: { name, mimeType, buffer } } })).status()).toBe(201);
  }
  expect((await api.post(`${kyc}/${dossier.id}/depot`, { headers: entetes })).status()).toBe(200);
  return dossier.reference;
}

test('peuplement de démonstration', async ({ request }) => {
  test.setTimeout(180_000);
  const admin = await jetonAdministrateur(request);
  const comptes: Record<string, { secret: string; roles: string[] }> = {};
  const jetons: Record<string, string> = {};
  for (const { identifiant, roles } of AGENTS) {
    const cree = await agent(request, admin.jeton, identifiant, roles);
    comptes[identifiant] = { secret: cree.secret, roles };
    jetons[identifiant] = cree.jeton;
  }

  // Parent au compte actif : Awa, dont le bracelet est appairé et a transmis une position à Ouagadougou.
  const jetonActif = await inscrire(request, PARENT_ACTIF);
  const reference = await deposerDossier(request, jetonActif,
    { nom: 'Ouédraogo', prenoms: 'Mariam', typePiece: 'CNIB', numeroPiece: 'B00000001' },
    { prenom: 'Awa', nom: 'Ouédraogo', dateNaissance: '2018-03-14' });
  const kyc = { Authorization: `Bearer ${jetons['demo.kyc']}` };
  const file = await (await request.get(`${SERVEUR}/api/v1/console/kyc/dossiers?size=100`, { headers: kyc })).json();
  const dossier = file.elements.find((e: { reference: string }) => e.reference === reference);
  expect((await request.post(`${SERVEUR}/api/v1/console/kyc/dossiers/${dossier.id}/prise-en-charge`, { headers: kyc })).status()).toBe(200);
  expect((await request.post(`${SERVEUR}/api/v1/console/kyc/dossiers/${dossier.id}/decision`, { headers: kyc, data: { decision: 'APPROUVER' } })).status()).toBe(200);

  const session = await (await request.post(`${SERVEUR}/api/v1/auth/connexion`, { data: { telephone: PARENT_ACTIF, motDePasse: MOT_DE_PASSE_PARENT } })).json();
  const parent = { Authorization: `Bearer ${session.jetonAcces}` };
  const enfants = await (await request.get(`${SERVEUR}/api/v1/enfants`, { headers: parent })).json();
  const bracelet = await braceletAuParc(request);
  expect((await request.post(`${SERVEUR}/api/v1/enfants/${enfants[0].id}/bracelet/appairage`, { headers: parent, data: { code: bracelet.codeAppairage } })).status()).toBe(200);
  // Une position antérieure à l'appairage serait écartée : elle est datée de quelques secondes plus tard.
  braceletEmet(bracelet.numeroSerie, 'telemetry', { t: Math.floor(Date.now() / 1000) + 5, seq: 1, lat: 12.3714, lon: -1.5197, acc: 12, src: 'gnss', bat: 78, rssi: -79, net: '4g' });

  // Fiche d'Awa remplie (profil, santé, contacts), pour voir les écrans 31, 32, 34 et la page QR dans leur état ordinaire.
  const awa = `${SERVEUR}/api/v1/enfants/${enfants[0].id}`;
  expect((await request.patch(awa, {
    headers: parent,
    data: { profil: { ecole: 'Les Manguiers · CE2', quartier: 'Ouaga 2000', tailleCm: 128, signesDistinctifs: 'Petite cicatrice au menton' } },
  })).status()).toBe(200);
  expect((await request.put(`${awa}/sante`, {
    headers: parent,
    data: {
      groupeSanguin: 'O+', groupeSanguinSurQr: true,
      elements: [
        { type: 'ALLERGIE', libelle: 'Arachides (sévère)', critique: true },
        { type: 'PATHOLOGIE', libelle: 'Asthme · inhalateur dans le sac', critique: true },
        { type: 'TRAITEMENT', libelle: 'Ventoline si crise', critique: false },
      ],
    },
  })).status()).toBe(200);
  for (const contact of [
    { lien: 'Tante', nom: 'Fatimata Sawadogo', telephone: '76087721', visibleSurQr: true },
  ]) {
    expect((await request.post(`${awa}/contacts`, { headers: parent, data: contact })).status()).toBe(201);
  }

  // Safe Zones d'Awa (écrans 21 à 23) : l'école, où se trouve sa position, la maison et chez sa grand-mère.
  const zones: Record<string, unknown>[] = [
    { forme: 'CERCLE', nom: 'École Les Manguiers', categorie: 'ECOLE', centre: { latitude: 12.3714, longitude: -1.5197 }, rayonM: 220,
      sommets: null, jours: [1, 2, 3, 4, 5], debut: '07:00', fin: '17:30', toleranceS: 300 },
    { forme: 'POLYGONE', nom: 'Maison', categorie: 'MAISON', centre: null, rayonM: null,
      sommets: [{ latitude: 12.3305, longitude: -1.5125 }, { latitude: 12.3305, longitude: -1.5095 }, { latitude: 12.3280, longitude: -1.5095 },
        { latitude: 12.3280, longitude: -1.5125 }],
      jours: [1, 2, 3, 4, 5, 6, 7], debut: '00:00', fin: '00:00', toleranceS: 300 },
    { forme: 'CERCLE', nom: 'Chez grand-mère', categorie: 'FAMILLE', centre: { latitude: 12.3790, longitude: -1.4790 }, rayonM: 150,
      sommets: null, jours: [6, 7], debut: '09:00', fin: '19:00', toleranceS: 600 },
  ];
  for (const zone of zones) {
    expect((await request.post(`${SERVEUR}/api/v1/moi/second-facteur`, { headers: parent, data: { action: 'MODIFIER_SAFE_ZONE' } })).status()).toBe(202);
    const codeZone = await dernierCodeSms(request, PARENT_ACTIF);
    expect((await request.post(`${awa}/zones`, { headers: parent, data: { ...zone, codeSecondFacteur: codeZone } })).status()).toBe(201);
  }

  // Journée d'Awa (écran 19) : départ de la maison, arrêt en route, arrivée à l'école.
  journeeDeTrajet(bracelet.numeroSerie, { latitude: 12.3292, longitude: -1.5110 }, { latitude: 12.3714, longitude: -1.5197 });

  // Second tuteur d'Awa (écran 34) : invité par Mariam, code SMS à l'appui, puis vérifié par l'équipe KYC.
  const jetonSecond = await inscrire(request, SECOND_TUTEUR);
  expect((await request.post(`${SERVEUR}/api/v1/moi/second-facteur`, { headers: parent, data: { action: 'AJOUTER_TUTEUR' } })).status()).toBe(202);
  const codeInvitation = await dernierCodeSms(request, PARENT_ACTIF);
  expect((await request.post(`${awa}/tuteurs/invitation`, {
    headers: parent, data: { telephone: SECOND_TUTEUR, codeSecondFacteur: codeInvitation },
  })).status()).toBe(201);
  const second = { Authorization: `Bearer ${jetonSecond}` };
  const [invitation] = await (await request.get(`${SERVEUR}/api/v1/moi/invitations`, { headers: second })).json();
  const dossierSecond = await (await request.post(`${SERVEUR}/api/v1/kyc/dossiers/invitation`, {
    headers: second,
    data: { invitationId: invitation.id, canal: 'POINT_INSCRIPTION', natureLien: 'PARENT',
      demandeur: { nom: 'Ouédraogo', prenoms: 'Ibrahim', typePiece: 'CNIB', numeroPiece: 'B00000003' } },
  })).json();
  expect((await request.post(`${SERVEUR}/api/v1/kyc/dossiers/${dossierSecond.id}/depot`, { headers: second })).status()).toBe(200);
  expect((await request.post(`${SERVEUR}/api/v1/console/kyc/dossiers/${dossierSecond.id}/prise-en-charge`, { headers: kyc })).status()).toBe(200);
  expect((await request.post(`${SERVEUR}/api/v1/console/kyc/dossiers/${dossierSecond.id}/decision`, { headers: kyc, data: { decision: 'APPROUVER' } })).status()).toBe(200);

  // Unité en stock, pour essayer l'appairage ou l'échange en point relais.
  const reserve = await braceletAuParc(request);

  // Parent dont le dossier attend l'équipe KYC : de quoi instruire un dossier dans la console.
  const jetonAttente = await inscrire(request, PARENT_EN_ATTENTE);
  const enAttente = await deposerDossier(request, jetonAttente,
    { nom: 'Sawadogo', prenoms: 'Ibrahim', typePiece: 'CNIB', numeroPiece: 'B00000002' },
    { prenom: 'Moussa', nom: 'Sawadogo', dateNaissance: '2015-09-01' });

  writeFileSync(join(ETAT, 'demo.json'), JSON.stringify({
    administrateur: { identifiant: process.env['FG_ADMIN_IDENTIFIANT'], motDePasse: process.env['FG_ADMIN_MOT_DE_PASSE'], secret: admin.secret },
    agents: comptes,
    motDePasseAgents: MOT_DE_PASSE_AGENT,
    parents: { actif: PARENT_ACTIF, secondTuteur: SECOND_TUTEUR, enAttente: PARENT_EN_ATTENTE, motDePasse: MOT_DE_PASSE_PARENT, dossierEnAttente: enAttente },
    bracelets: { appaire: bracelet.numeroSerie, jetonQr: bracelet.jetonQr, enStock: reserve.numeroSerie, codeEnStock: reserve.codeAppairage, jetonQrEnStock: reserve.jetonQr },
  }, null, 2));
});
