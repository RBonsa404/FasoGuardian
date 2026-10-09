import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { AgentInterne, ChaineAudit, ClientAgents, ClientConformite, DemandeEffacement, EntreeAudit, FiltreAudit, PageAudit, RoleInterne, Session, TableauConformite } from 'api';

import { Agents } from './agents';
import { Audit } from './audit';
import { Conformite, moisPrecedent } from './conformite';
import { Securite } from './securite';

function entree(partiel: Partial<EntreeAudit> = {}): EntreeAudit {
  return {
    id: 12,
    horodatage: '2026-10-07T15:02:11Z',
    acteurId: '6f1c2a90-0000-4000-8000-000000000001',
    role: 'KYC',
    action: 'DOSSIER_KYC_CONSULTE',
    typeCible: 'DOSSIER_KYC',
    cibleId: 'KYC-000412',
    resultat: 'SUCCES',
    empreinte: 'a91f…3c0e',
    ...partiel,
  };
}

function tableau(partiel: Partial<TableauConformite> = {}): TableauConformite {
  return {
    aipd: { documentee: false, reference: null, valideeLe: null, delegue: null },
    conservation: [
      { donnee: 'Positions', duree: '30 jours', mecanisme: 'Purge quotidienne' },
      { donnee: 'Pièces KYC', duree: 'Durée de la relation + 1 an', mecanisme: 'Purge quotidienne' },
    ],
    demandesAcces: 3,
    demandesEffacement: 1,
    effacementsEnAttente: 1,
    joursDePurge: 9,
    joursEcoules: 9,
    purges: [{ traitement: 'POSITIONS', derniereExecution: '2026-10-09T02:20:00Z', executions: 9, elements: 1240 }],
    ...partiel,
  };
}

function demande(partiel: Partial<DemandeEffacement> = {}): DemandeEffacement {
  return {
    id: 'd-1',
    reference: 'EFF-000012',
    statut: 'RECUE',
    recueLe: '2026-10-01T09:00:00Z',
    echeanceLe: '2099-10-31T09:00:00Z',
    traiteeLe: null,
    traiteeParLeSysteme: false,
    ...partiel,
  };
}

/** Double du client : retient ce que l'écran demande et répond ce que l'essai a réglé. */
class ClientFactice {
  chaine: ChaineAudit = { integre: true, verifieeLe: '2026-10-07T15:02:00Z', entreeAlteree: null };
  entrees: EntreeAudit[] = [entree(), entree({ id: 11, acteurId: null, role: 'SYSTEME', action: 'CHAINE_VERIFIEE', typeCible: 'JOURNAL', cibleId: null })];
  total = 120;
  filtres: { filtre: FiltreAudit; page: number }[] = [];
  verifications = 0;
  etat = tableau();
  liste: DemandeEffacement[] = [demande()];
  executions: string[] = [];

  journal(filtre: FiltreAudit, page: number) {
    this.filtres.push({ filtre, page });
    return of<PageAudit>({ entrees: this.entrees, total: this.total, page, taille: 50, chaine: this.chaine });
  }

  verifierLaChaine() {
    this.verifications++;
    return of(this.chaine);
  }

  tableau() {
    return of(this.etat);
  }

  demandes() {
    return of(this.liste);
  }

  executer(id: string) {
    this.executions.push(id);
    this.liste = [demande({ statut: 'TRAITEE', traiteeLe: '2026-10-09T10:00:00Z' })];
    return of(this.liste[0]);
  }
}

function lire(element: Element): string {
  return (element.textContent ?? '').replace(/\s+/g, ' ');
}

function bouton(page: HTMLElement, nom: string): HTMLButtonElement {
  return [...page.querySelectorAll('button')].find((b) => lire(b).includes(nom)) as HTMLButtonElement;
}

describe('journal d’audit', () => {
  async function monter(client: ClientFactice) {
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: ClientConformite, useValue: client }] });
    const fixture = TestBed.createComponent(Audit);
    await fixture.whenStable();
    return { fixture, page: fixture.nativeElement as HTMLElement };
  }

  it('montre les entrées, l’état de la chaîne et ne désigne les comptes que par leur identifiant', async () => {
    const { page } = await monter(new ClientFactice());

    expect(lire(page)).toContain('Chaîne intègre · vérifiée');
    expect(page.querySelectorAll('[role="row"]')).toHaveLength(3);
    expect(lire(page)).toContain('KYC · 6f1c2a90');
    expect(lire(page)).toContain('DOSSIER_KYC · KYC-000412');
    expect(lire(page)).toContain('Système');
    expect(lire(page)).toContain('a91f…3c0e');
    expect(lire(page)).toContain('1 à 2 sur 120');
  });

  it('signale une rupture de la chaîne', async () => {
    const client = new ClientFactice();
    client.chaine = { integre: false, verifieeLe: '2026-10-07T15:02:00Z', entreeAlteree: 998102 };

    const { page } = await monter(client);

    expect(lire(page)).toContain("Rupture détectée à l'entrée 998102");
    expect(lire(page)).not.toContain('Chaîne intègre');
  });

  it('filtre par action, rôle et résultat, puis parcourt les pages', async () => {
    const client = new ClientFactice();
    const { fixture, page } = await monter(client);
    const action = page.querySelector('input') as HTMLInputElement;
    action.value = 'acces_refuse';
    action.dispatchEvent(new Event('input'));
    const [role, resultat] = [...page.querySelectorAll('select')] as HTMLSelectElement[];
    role.value = 'SAV';
    role.dispatchEvent(new Event('change'));
    resultat.value = 'REFUS';
    resultat.dispatchEvent(new Event('change'));

    bouton(page, 'Filtrer').click();
    await fixture.whenStable();
    bouton(page, 'Plus anciennes').click();
    await fixture.whenStable();

    expect(client.filtres.at(-2)).toEqual({ filtre: { action: 'ACCES_REFUSE', role: 'SAV', resultat: 'REFUS' }, page: 0 });
    expect(client.filtres.at(-1)?.page).toBe(1);
  });

  it('fait vérifier la chaîne à la demande', async () => {
    const client = new ClientFactice();
    const { fixture, page } = await monter(client);

    bouton(page, 'Vérifier la chaîne').click();
    await fixture.whenStable();

    expect(client.verifications).toBe(1);
  });
});

describe('conformité CIL', () => {
  async function monter(client: ClientFactice) {
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: ClientConformite, useValue: client }] });
    const fixture = TestBed.createComponent(Conformite);
    await fixture.whenStable();
    return { fixture, page: fixture.nativeElement as HTMLElement };
  }

  it('dit que la mise en production est bloquée tant que l’AIPD n’est pas documentée', async () => {
    const { page } = await monter(new ClientFactice());

    expect(lire(page)).toContain('Mise en production bloquée');
    expect(lire(page)).toContain("L'analyse d'impact (AIPD) n'est pas documentée.");
    expect(lire(page)).toContain('9 / 9');
    expect(lire(page)).toContain('Durée de la relation + 1 an');
    expect(lire(page)).toContain('1240 supprimés');
  });

  it('montre l’AIPD documentée', async () => {
    const client = new ClientFactice();
    client.etat = tableau({ aipd: { documentee: true, reference: 'AIPD-2026-01', valideeLe: '2026-09-30', delegue: 'dpo@exemple.bf' } });

    const { page } = await monter(client);

    expect(lire(page)).toContain('référence AIPD-2026-01');
    expect(lire(page)).not.toContain('Mise en production bloquée');
  });

  it('exécute une demande d’effacement après confirmation', async () => {
    const client = new ClientFactice();
    const { fixture, page } = await monter(client);
    expect(lire(page)).toContain('EFF-000012');
    expect(lire(page)).toContain('À exécuter');

    bouton(page, 'Exécuter').click();
    await fixture.whenStable();
    expect(client.executions).toEqual([]);
    expect(lire(page)).toContain('Exécuter la demande EFF-000012 ?');

    bouton(page, "Exécuter l'effacement").click();
    await fixture.whenStable();

    expect(client.executions).toEqual(['d-1']);
    expect(lire(page)).toContain('Exécutée le');
    expect(lire(page)).not.toContain('À exécuter');
  });

  it('propose le rapport du dernier mois complet', () => {
    expect(moisPrecedent(new Date(2026, 9, 9))).toEqual({ code: '2026-09', libelle: 'septembre' });
    expect(moisPrecedent(new Date(2027, 0, 3))).toEqual({ code: '2026-12', libelle: 'décembre' });
  });
});

describe('agents et rôles', () => {
  const MOI = '00000000-0000-4000-8000-0000000000aa';

  function agent(partiel: Partial<AgentInterne> = {}): AgentInterne {
    return { id: 'a-1', identifiant: 'awa.kyc', roles: ['KYC'], secondFacteurActif: true, suspendu: false, ...partiel };
  }

  class AgentsFactices {
    liste: AgentInterne[] = [agent(), agent({ id: MOI, identifiant: 'admin', roles: ['ADMIN'] })];
    appels: string[] = [];

    agents() {
      return of(this.liste);
    }

    creer(identifiant: string, _motDePasse: string, roles: readonly RoleInterne[]) {
      this.appels.push(`creer ${identifiant} ${roles.join('+')}`);
      this.liste = [...this.liste, agent({ id: 'a-9', identifiant, roles, secondFacteurActif: false })];
      return of(this.liste.at(-1)!);
    }

    attribuer(id: string, roles: readonly RoleInterne[]) {
      this.appels.push(`roles ${id} ${roles.join('+')}`);
      return of(agent({ roles }));
    }

    suspendre(id: string) {
      this.appels.push(`suspendre ${id}`);
      this.liste = [agent({ suspendu: true }), this.liste[1]];
      return of(this.liste[0]);
    }

    retablir(id: string) {
      this.appels.push(`retablir ${id}`);
      return of(agent());
    }
  }

  async function monter(client: AgentsFactices) {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: ClientAgents, useValue: client }, { provide: Session, useValue: { identifiant: () => MOI } }],
    });
    const fixture = TestBed.createComponent(Agents);
    await fixture.whenStable();
    return { fixture, page: fixture.nativeElement as HTMLElement };
  }

  function cocher(page: HTMLElement, role: string): void {
    const libelle = [...page.querySelectorAll('fg-sheet label')].find((l) => lire(l).trim() === role)!;
    (libelle.querySelector('input') as HTMLInputElement).click();
  }

  it('montre les agents, la matrice, et ne propose aucune action sur son propre compte', async () => {
    const { page } = await monter(new AgentsFactices());

    expect(lire(page)).toContain('awa.kyc');
    expect(lire(page)).toContain('Votre compte');
    expect([...page.querySelectorAll('button')].filter((b) => lire(b).includes('Suspendre'))).toHaveLength(1);
    expect(lire(page)).toContain("Aucun rôle interne ne voit la position ni la fiche santé d'un enfant.");
    // La ligne « position ou fiche santé » ne porte que des « aucun accès ».
    const ligne = [...page.querySelectorAll('[role="row"]')].find((l) => lire(l).includes('Voir la position'))!;
    expect([...ligne.querySelectorAll('[aria-label]')].map((c) => c.getAttribute('aria-label'))).toEqual(Array(5).fill('aucun accès'));
  });

  it('refuse un agent sans rôle, puis le crée', async () => {
    const client = new AgentsFactices();
    const { fixture, page } = await monter(client);
    bouton(page, 'Ajouter un agent').click();
    await fixture.whenStable();
    const [identifiant, motDePasse] = [...page.querySelectorAll('fg-sheet input:not([type="checkbox"])')] as HTMLInputElement[];
    identifiant.value = 'issa.sav';
    identifiant.dispatchEvent(new Event('input'));
    motDePasse.value = 'phrase-de-passe-provisoire';
    motDePasse.dispatchEvent(new Event('input'));

    bouton(page, "Créer l'agent").click();
    await fixture.whenStable();
    expect(lire(page)).toContain('Attribuez au moins un rôle.');
    expect(client.appels).toEqual([]);

    cocher(page, 'Agent SAV');
    await fixture.whenStable();
    bouton(page, "Créer l'agent").click();
    await fixture.whenStable();

    expect(client.appels).toEqual(['creer issa.sav SAV']);
    expect(lire(page)).toContain('Second facteur à activer');
  });

  it('change les rôles d’un agent et le suspend', async () => {
    const client = new AgentsFactices();
    const { fixture, page } = await monter(client);

    bouton(page, 'Rôles').click();
    await fixture.whenStable();
    cocher(page, 'Agent KYC');
    cocher(page, 'Opérateur support');
    await fixture.whenStable();
    bouton(page, 'Enregistrer les rôles').click();
    await fixture.whenStable();
    bouton(page, 'Suspendre').click();
    await fixture.whenStable();

    expect(client.appels).toEqual(['roles a-1 SUPPORT', 'suspendre a-1']);
    expect(lire(page)).toContain('Suspendu');
    expect(bouton(page, 'Rétablir')).toBeDefined();
  });
});

describe('sécurité', () => {
  it('montre les sources bloquées sous pseudonyme et les accès refusés des sept derniers jours', async () => {
    const client = new ClientFactice();
    client.entrees = [];
    const demandes: FiltreAudit[] = [];
    const journal = (filtre: FiltreAudit, page: number) => {
      demandes.push(filtre);
      const entrees =
        filtre.action === 'ENUMERATION_QR'
          ? [entree({ id: 40, acteurId: null, role: 'SYSTEME', action: 'ENUMERATION_QR', typeCible: 'SOURCE', cibleId: '9f2c1a7b33de4455667788', resultat: 'REFUS' })]
          : [entree({ id: 41, role: 'KYC', action: 'ACCES_REFUSE', typeCible: 'ROUTE', cibleId: 'GET /api/v1/console/parc', resultat: 'REFUS' })];
      return of<PageAudit>({ entrees, total: 1, page, taille: 50, chaine: client.chaine });
    };
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: ClientConformite, useValue: { journal } }] });
    const fixture = TestBed.createComponent(Securite);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    expect(lire(page)).toContain('Source 9f2c1a7b33de');
    expect(lire(page)).not.toContain('9f2c1a7b33de4455667788');
    expect(lire(page)).toContain('GET /api/v1/console/parc');
    expect(lire(page)).toContain('KYC · 6f1c2a90');
    expect(demandes.map((d) => d.action)).toEqual(['ENUMERATION_QR', 'ACCES_REFUSE']);
    expect(Date.now() - Date.parse(demandes[0].depuis!)).toBeGreaterThan(6.9 * 86_400_000);
  });
});
