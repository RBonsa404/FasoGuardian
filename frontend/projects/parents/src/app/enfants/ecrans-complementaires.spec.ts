import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { Bracelet, ClientBracelet, ClientFamille, ContactUrgence, FicheSante, SuiviMaintenance } from 'api';

import { MaintenanceBracelet } from '../bracelet/maintenance';
import { TEMOIN_INTRODUCTION, introductionVue } from '../commun/introduction';
import { Bienvenue } from '../connexion/bienvenue';
import { ApercuQr } from './apercu-qr';

const BRACELET: Bracelet = {
  numeroSerie: 'FG-2291',
  statut: 'ACTIF',
  versionLogiciel: '2.4.1',
  revisionMaterielle: 'V1',
  garantieJusquAu: '2027-10-01',
  modeEconomie: false,
  intervalleS: 300,
  appaireLe: '2026-10-01T08:00:00Z',
  suiviJusquAu: null,
};

const CONTACTS: ContactUrgence[] = [
  { id: 'c-1', lien: 'Mère', nom: 'Mariam Ouédraogo', telephone: '+22670123456', visibleSurQr: true, rang: 1 },
  { id: 'c-2', lien: 'Oncle', nom: 'Issouf Ouédraogo', telephone: '+22676445508', visibleSurQr: false, rang: 2 },
];

const SANTE: FicheSante = {
  groupeSanguin: 'O+',
  groupeSanguinSurQr: true,
  elements: [
    { type: 'ALLERGIE', libelle: 'Arachides', critique: true },
    { type: 'TRAITEMENT', libelle: 'Suivi orthophonique', critique: false },
  ],
  modifieLe: '2026-10-02T10:00:00Z',
};

const SUIVI: SuiviMaintenance = {
  reference: 'SAV-007731',
  numeroSerie: 'FG-2291',
  statut: 'OUVERT',
  ouvertLe: '2026-10-09T11:19:00Z',
  dernierContact: '2026-10-09T11:04:00Z',
  batterie: 46,
  reseau: '2G',
  prisEnChargeLe: null,
};

const INTROUVABLE = new HttpErrorResponse({ status: 404, error: { status: 404, code: 'RESSOURCE_INTROUVABLE', detail: 'Absent.' } });

function lire(element: Element): string {
  return (element.textContent ?? '').replace(/\s+/g, ' ');
}

function bouton(page: HTMLElement, nom: string): HTMLButtonElement {
  return [...page.querySelectorAll('button')].find((b) => lire(b).includes(nom)) as HTMLButtonElement;
}

async function ouvrir<T>(composant: new (...args: never[]) => T, bracelet: Partial<ClientBracelet>, famille: Partial<ClientFamille> = {}) {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      { provide: ClientBracelet, useValue: bracelet },
      {
        provide: ClientFamille,
        useValue: { contacts: () => of(CONTACTS), sante: () => of(SANTE), enfant: () => of({ id: 'e-1', prenom: 'Awa' }), ...famille },
      },
    ],
  });
  const fixture = TestBed.createComponent(composant as never);
  fixture.componentRef.setInput('id', 'e-1');
  await fixture.whenStable();
  return fixture.nativeElement as HTMLElement;
}

describe('aperçu de la page QR', () => {
  it('ne montre que le numéro, les contacts joignables et les informations marquées critiques', async () => {
    const page = await ouvrir(ApercuQr, { bracelet: () => of(BRACELET) });
    const cadre = page.querySelector('section') as HTMLElement;

    expect(lire(cadre)).toContain('FG-2291');
    expect(lire(cadre)).toContain('Vous avez trouvé un enfant ?');
    expect(lire(cadre)).toContain('Appeler · Mère');
    expect(lire(cadre)).not.toContain('Oncle');
    expect(lire(cadre)).toContain('Allergie');
    expect(lire(cadre)).toContain('Arachides');
    expect(lire(cadre)).toContain('Groupe sanguin');
    expect(lire(cadre)).not.toContain('Suivi orthophonique');
    // Ni nom, ni prénom, ni numéro de téléphone : la page publique n'en porte pas.
    expect(lire(cadre)).not.toContain('Awa');
    expect(lire(cadre)).not.toContain('Ouédraogo');
    expect(lire(cadre)).not.toContain('70123456');
  });

  it('prévient quand aucun contact n’est joignable, et tait le groupe sanguin non autorisé', async () => {
    const page = await ouvrir(
      ApercuQr,
      { bracelet: () => of(BRACELET) },
      { contacts: () => of([CONTACTS[1]]), sante: () => of({ ...SANTE, groupeSanguinSurQr: false }) } as Partial<ClientFamille>,
    );

    expect(lire(page)).toContain("Aucun contact n'est joignable depuis la page");
    expect(lire(page.querySelector('section')!)).not.toContain('Groupe sanguin');
    expect(lire(page.querySelector('section')!)).not.toContain('Appeler');
  });

  it('dit que la page est désactivée pour un bracelet perdu, et qu’elle n’existe pas sans bracelet', async () => {
    const perdu = await ouvrir(ApercuQr, { bracelet: () => of({ ...BRACELET, statut: 'PERDU' } as Bracelet) });
    expect(lire(perdu)).toContain('Ce bracelet est désactivé');
    expect(lire(perdu.querySelector('section')!)).not.toContain('Arachides');

    TestBed.resetTestingModule();
    const sans = await ouvrir(ApercuQr, { bracelet: () => throwError(() => INTROUVABLE) });
    expect(lire(sans)).toContain("sa page QR n'existe pas encore");
    expect(sans.querySelector('section')).toBeNull();
  });
});

describe('suivi de maintenance', () => {
  it('montre le ticket ouvert, ses étapes et la personne à appeler', async () => {
    const page = await ouvrir(MaintenanceBracelet, { maintenance: () => of(SUIVI) });

    expect(lire(page)).toContain('Ticket SAV-007731 · ouvert automatiquement');
    expect(lire(page)).toContain('Le bracelet de Awa ne répond plus');
    expect(lire(page)).toMatch(/Dernier contact \d{2}:\d{2} · batterie 46 % · 2G\./);
    const etapes = [...page.querySelectorAll('ol li')].map(lire);
    expect(etapes).toHaveLength(4);
    expect(etapes[0]).toContain('Bracelet muet détecté');
    expect(etapes[2]).toContain("En attente d'un agent");
    expect(etapes[3]).toContain('En attente de reconnexion');
    const appel = page.querySelector('a[href^="tel:"]') as HTMLAnchorElement;
    expect(appel.getAttribute('href')).toBe('tel:+22670123456');
    expect(lire(appel)).toContain('Appeler Mariam Ouédraogo (Mère)');
  });

  it('dit quand un agent a pris le ticket en charge, sans le nommer', async () => {
    const page = await ouvrir(MaintenanceBracelet, { maintenance: () => of({ ...SUIVI, statut: 'EN_COURS', prisEnChargeLe: '2026-10-09T11:20:00Z' } as SuiviMaintenance) });

    expect(lire(page.querySelectorAll('ol li')[2])).toContain('Pris en charge par un agent');
  });

  it('rassure quand aucun ticket n’est ouvert', async () => {
    const page = await ouvrir(MaintenanceBracelet, { maintenance: () => throwError(() => INTROUVABLE) });

    expect(lire(page)).toContain('Aucun incident en cours');
    expect(page.querySelector('ol')).toBeNull();
  });
});

describe('introduction', () => {
  beforeEach(() => localStorage.removeItem(TEMOIN_INTRODUCTION));

  it('déroule trois volets puis propose de créer un compte, et ne se remontre plus', async () => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const navigation = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(Bienvenue);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    expect(introductionVue()).toBe(false);
    expect(lire(page)).toContain('Sachez où est votre enfant, sans le surveiller');
    bouton(page, 'Suivant').click();
    await fixture.whenStable();
    expect(lire(page)).toContain('Un SOS au poignet');
    bouton(page, 'Suivant').click();
    await fixture.whenStable();
    expect(lire(page)).toContain("Un QR pour qu'on vous prévienne");
    expect(bouton(page, 'Suivant')).toBeUndefined();

    bouton(page, 'Créer mon compte').click();

    expect(navigation).toHaveBeenCalledWith('/inscription/numero');
    expect(introductionVue()).toBe(true);
  });

  it('« Passer » mène à la connexion dès le premier volet', async () => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const navigation = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(Bienvenue);
    await fixture.whenStable();

    bouton(fixture.nativeElement as HTMLElement, 'Passer').click();

    expect(navigation).toHaveBeenCalledWith('/connexion');
    expect(introductionVue()).toBe(true);
  });
});
