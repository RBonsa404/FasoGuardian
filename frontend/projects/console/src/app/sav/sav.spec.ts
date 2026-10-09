import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { BraceletParc, CarteActivationParc, ClientSav, FicheBraceletParc, ResolutionTicket, TicketMaintenance } from 'api';

import { FicheBracelet } from './fiche-bracelet';
import { depuis } from './libelles';
import { Muets } from './muets';
import { Parc } from './parc';

function bracelet(partiel: Partial<BraceletParc> = {}): BraceletParc {
  return {
    numeroSerie: 'FG-3307',
    statut: 'ACTIF',
    revisionMaterielle: 'B',
    versionLogiciel: '1.4.2',
    certificatRevoque: false,
    appaire: true,
    garantieJusquAu: '2027-09-01',
    modifieLe: '2026-10-07T11:04:00Z',
    ...partiel,
  };
}

function ticket(partiel: Partial<TicketMaintenance> = {}): TicketMaintenance {
  return {
    id: 't-1',
    reference: 'SAV-007731',
    numeroSerie: 'FG-3307',
    motif: 'MUET',
    statut: 'OUVERT',
    ouvertLe: new Date(Date.now() - 18 * 60_000).toISOString(),
    dernierContact: '2026-10-07T11:04:00Z',
    batterie: 46,
    reseau: '2G',
    prisEnCharge: false,
    prisEnChargeParMoi: false,
    resolution: null,
    note: null,
    resoluLe: null,
    ...partiel,
  };
}

/** Double du client : répond ce que l'essai a réglé et retient les mouvements demandés. */
class ClientFactice {
  unites: BraceletParc[] = [bracelet(), bracelet({ numeroSerie: 'FG-1001', statut: 'EN_STOCK', appaire: false }), bracelet({ numeroSerie: 'FG-2002', statut: 'EN_SAV', appaire: false })];
  ficheCourante: FicheBraceletParc = {
    bracelet: bracelet({ numeroSerie: 'FG-2002', statut: 'EN_SAV', appaire: false }),
    imeiMasque: '35•••••••••1234',
    empreinteCertificat: 'ab12cd34',
    appairages: [{ debut: '2026-09-01T08:00:00Z', fin: '2026-10-01T09:00:00Z', motifFin: 'PANNE' }],
  };
  mouvements: string[] = [];
  liste: TicketMaintenance[] = [ticket()];
  resolutions: { id: string; resolution: ResolutionTicket; note: string }[] = [];

  parc() {
    return of(this.unites);
  }

  fiche() {
    return of(this.ficheCourante);
  }

  retourner(numero: string) {
    this.mouvements.push(`retour ${numero}`);
    return of(bracelet({ statut: 'EN_SAV' }));
  }

  remettreEnStock(numero: string) {
    this.mouvements.push(`remise ${numero}`);
    this.ficheCourante = { ...this.ficheCourante, bracelet: { ...this.ficheCourante.bracelet, statut: 'EN_STOCK' } };
    return of<CarteActivationParc>({ numeroSerie: numero, codeAppairage: 'NU7D-AU5', jetonQr: 'jeton-de-test' });
  }

  reformer(numero: string) {
    this.mouvements.push(`reforme ${numero}`);
    return of(bracelet({ statut: 'REFORME' }));
  }

  tickets() {
    return of(this.liste);
  }

  prendreEnCharge(id: string) {
    this.mouvements.push(`prise ${id}`);
    this.liste = [ticket({ statut: 'EN_COURS', prisEnCharge: true, prisEnChargeParMoi: true })];
    return of(this.liste[0]);
  }

  resoudre(id: string, resolution: ResolutionTicket, note: string) {
    this.resolutions.push({ id, resolution, note });
    this.liste = [];
    return of(ticket({ statut: 'RESOLU', resolution, note }));
  }
}

function lire(element: Element): string {
  return (element.textContent ?? '').replace(/\s+/g, ' ');
}

function bouton(page: HTMLElement, nom: string): HTMLButtonElement {
  return [...page.querySelectorAll('button')].find((b) => lire(b).includes(nom)) as HTMLButtonElement;
}

function preparer(client: ClientFactice) {
  TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: ClientSav, useValue: client }] });
}

describe('parc de bracelets', () => {
  it('compte les unités par état et filtre la vue d’un appui', async () => {
    preparer(new ClientFactice());
    const fixture = TestBed.createComponent(Parc);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    expect(page.querySelectorAll('a[role="row"]')).toHaveLength(3);
    expect(lire(bouton(page, 'En SAV'))).toContain('1');
    // Le service après-vente sait qu'un bracelet est porté, pas par qui.
    expect(lire(page)).toContain('Par un enfant');

    bouton(page, 'En SAV').click();
    await fixture.whenStable();
    expect([...page.querySelectorAll('a[role="row"]')].map((ligne) => lire(ligne))).toHaveLength(1);
    expect(lire(page.querySelector('a[role="row"]')!)).toContain('FG-2002');

    bouton(page, 'En SAV').click();
    await fixture.whenStable();
    expect(page.querySelectorAll('a[role="row"]')).toHaveLength(3);
  });
});

describe('fiche d’un bracelet', () => {
  it('remet en stock une unité en SAV après confirmation et montre une fois sa carte d’activation', async () => {
    const client = new ClientFactice();
    preparer(client);
    const fixture = TestBed.createComponent(FicheBracelet);
    fixture.componentRef.setInput('numero', 'FG-2002');
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    expect(lire(page)).toContain('35•••••••••1234');
    expect(lire(page)).toContain('Panne');
    // En SAV : ni « retour » (déjà fait), ni action sans confirmation.
    expect(bouton(page, 'Retour au SAV')).toBeUndefined();

    bouton(page, 'Remettre en stock').click();
    await fixture.whenStable();
    expect(client.mouvements).toEqual([]);
    expect(lire(page)).toContain("Il ne sera affiché qu'une fois.");

    [...page.querySelectorAll('fg-sheet button')].find((b) => lire(b).includes('Remettre en stock'))!.dispatchEvent(new Event('click'));
    await fixture.whenStable();

    expect(client.mouvements).toEqual(['remise FG-2002']);
    expect(page.querySelector('[data-code-appairage]')?.textContent).toBe('NU7D-AU5');
    expect(lire(page)).toContain('En stock');
  });
});

describe('bracelets muets', () => {
  async function monter(client: ClientFactice) {
    preparer(client);
    const fixture = TestBed.createComponent(Muets);
    await fixture.whenStable();
    return { fixture, page: fixture.nativeElement as HTMLElement };
  }

  it('montre l’état du bracelet à l’ouverture du ticket, sans rien sur l’enfant', async () => {
    const { page } = await monter(new ClientFactice());

    expect(lire(page)).toContain('18 min');
    expect(lire(page)).toContain('SAV-007731');
    expect(lire(page)).toContain('batterie 46 % · 2G');
    expect(lire(page)).toContain('Non attribué');
  });

  it('l’agent prend le ticket en charge puis le clôt en disant comment', async () => {
    const client = new ClientFactice();
    const { fixture, page } = await monter(client);

    bouton(page, 'Prendre en charge').click();
    await fixture.whenStable();
    expect(client.mouvements).toEqual(['prise t-1']);
    expect(lire(page)).toContain('À vous');

    bouton(page, 'Résoudre').click();
    await fixture.whenStable();
    const echange = [...page.querySelectorAll('label')].find((libelle) => lire(libelle).includes('Échange en point relais'))!;
    (echange.querySelector('input') as HTMLInputElement).click();
    const note = page.querySelector('textarea') as HTMLTextAreaElement;
    note.value = ' Module remplacé ';
    note.dispatchEvent(new Event('input'));
    bouton(page, 'Clore le ticket').click();
    await fixture.whenStable();

    expect(client.resolutions).toEqual([{ id: 't-1', resolution: 'ECHANGE', note: 'Module remplacé' }]);
    expect(lire(page)).toContain('Tous les bracelets en service donnent des nouvelles.');
  });

  it('écrit les durées en minutes puis en heures', () => {
    const maintenant = Date.parse('2026-10-09T12:00:00Z');
    expect(depuis('2026-10-09T11:42:00Z', maintenant)).toBe('18 min');
    expect(depuis('2026-10-09T07:00:00Z', maintenant)).toBe('5 h');
  });
});
