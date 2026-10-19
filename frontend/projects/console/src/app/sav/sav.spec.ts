import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { BraceletParc, CarteActivationParc, ClientSav, FicheBraceletParc, ResolutionTicket, TicketMaintenance } from 'api';

import { FicheBracelet } from './fiche-bracelet';
import { contact, depuis } from './libelles';
import { Muets, etapeDe } from './muets';
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
    diagnosticLe: null,
    diagnostic: null,
    pointRelais: null,
    rendezVousLe: null,
    remplacement: null,
    autresMuets: 2,
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

  etatsParc() {
    return of([{ numeroSerie: 'FG-3307', batterie: 46, reseau: '2G', dernierContact: new Date(Date.now() - 2 * 60_000).toISOString() }]);
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

  diagnostiquer(id: string) {
    this.mouvements.push(`diagnostic ${id}`);
    this.liste = [{ ...this.liste[0], diagnosticLe: '2026-10-07T11:40:00Z', diagnostic: 'EXPIREE' }];
    return of(this.liste[0]);
  }

  fixerRendezVous(id: string, pointRelais: string, le: string) {
    this.mouvements.push(`rendez-vous ${id} ${pointRelais} ${le.slice(0, 10)}`);
    this.liste = [{ ...this.liste[0], pointRelais, rendezVousLe: le }];
    return of(this.liste[0]);
  }

  reserverRemplacement(id: string, numero: string) {
    this.mouvements.push(`remplacement ${id} ${numero}`);
    this.liste = [{ ...this.liste[0], remplacement: numero.toUpperCase() }];
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

function saisir(page: HTMLElement, libelle: string, valeur: string) {
  const champ = [...page.querySelectorAll('fg-input')].find((c) => lire(c).includes(libelle))!.querySelector('input') as HTMLInputElement;
  champ.value = valeur;
  champ.dispatchEvent(new Event('input'));
}

function preparer(client: ClientFactice) {
  TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: ClientSav, useValue: client }] });
}

describe('parc de bracelets', () => {
  it('compte les unités par groupe d’états, montre l’état technique et filtre la vue d’un appui', async () => {
    const client = new ClientFactice();
    client.unites.push(bracelet({ numeroSerie: 'FG-4004', statut: 'PERDU' }), bracelet({ numeroSerie: 'FG-5005', statut: 'VOLE', appaire: false }));
    preparer(client);
    const fixture = TestBed.createComponent(Parc);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    expect(page.querySelectorAll('a[role="row"]')).toHaveLength(5);
    expect(lire(bouton(page, 'En SAV'))).toContain('1');
    // Pertes et vols sont comptés ensemble, comme sur la maquette.
    expect(lire(bouton(page, 'Perdus ou volés'))).toContain('2');
    // Le service après-vente sait qu'un bracelet est porté, pas par qui ; il voit sa batterie et son réseau.
    const porte = lire(page.querySelector('a[href="/sav/parc/FG-3307"]')!);
    expect(porte).toContain('Par un enfant');
    expect(porte).toContain('46 %');
    expect(porte).toContain('2G');
    expect(porte).toContain('il y a 2 min');

    bouton(page, 'En SAV').click();
    await fixture.whenStable();
    expect([...page.querySelectorAll('a[role="row"]')].map((ligne) => lire(ligne))).toHaveLength(1);
    expect(lire(page.querySelector('a[role="row"]')!)).toContain('FG-2002');

    bouton(page, 'En SAV').click();
    await fixture.whenStable();
    expect(page.querySelectorAll('a[role="row"]')).toHaveLength(5);
    expect(lire(page)).toContain('Choisissez un bracelet pour voir sa fiche.');
  });

  it('ouvre la fiche du bracelet désigné par l’adresse à côté de la liste', async () => {
    preparer(new ClientFactice());
    const fixture = TestBed.createComponent(Parc);
    fixture.componentRef.setInput('numero', 'FG-2002');
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    expect(page.querySelector('a[aria-current="true"]')?.getAttribute('href')).toBe('/sav/parc/FG-2002');
    expect(lire(page.querySelector('aside')!)).toContain('35•••••••••1234');
  });

  it('dit le dernier contact en minutes dans l’heure, puis à l’heure, puis au jour', () => {
    const maintenant = Date.parse('2026-10-07T15:00:00');
    expect(contact(null, maintenant)).toBe('—');
    expect(contact(new Date(maintenant - 9 * 60_000).toISOString(), maintenant)).toBe('il y a 9 min');
    expect(contact(new Date('2026-10-07T11:04:00').toISOString(), maintenant)).toBe('11:04');
    expect(contact(new Date('2026-10-02T11:04:00').toISOString(), maintenant)).toBe('2 oct.');
  });
});

describe('fiche d’un bracelet', () => {
  it('remet en stock une unité en SAV après confirmation et montre une fois sa carte d’activation', async () => {
    const client = new ClientFactice();
    preparer(client);
    const fixture = TestBed.createComponent(FicheBracelet);
    fixture.componentRef.setInput('numero', 'FG-2002');
    const modifie = vi.fn();
    fixture.componentInstance.modifie.subscribe(modifie);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    expect(lire(page)).toContain('35•••••••••1234');
    expect(lire(page)).toContain("1 oct. 2026 · fin d'appairage · Panne");
    expect(lire(page)).toContain('1 sept. 2026 · appairage');
    // Cycle de vie : l'unité est au SAV, après avoir été en stock, remise et active.
    expect(lire(page.querySelector('[aria-current="step"]')!)).toContain('En SAV');
    // En SAV : ni « retour » (déjà fait), ni action sans confirmation.
    expect(bouton(page, 'Retour au SAV')).toBeUndefined();

    bouton(page, 'Remettre en stock').click();
    await fixture.whenStable();
    expect(client.mouvements).toEqual([]);
    expect(lire(page)).toContain("Il ne sera affiché qu'une fois.");

    [...page.querySelectorAll('fg-sheet button')].find((b) => lire(b).includes('Remettre en stock'))!.dispatchEvent(new Event('click'));
    await fixture.whenStable();

    expect(client.mouvements).toEqual(['remise FG-2002']);
    expect(modifie).toHaveBeenCalledTimes(1);
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

    bouton(page, 'Clore autrement').click();
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

  it('mène le ticket du diagnostic à la remise en point relais, sans rien montrer de la famille', async () => {
    const client = new ClientFactice();
    client.liste = [ticket({ statut: 'EN_COURS', prisEnCharge: true, prisEnChargeParMoi: true })];
    const { fixture, page } = await monter(client);

    expect(lire(page)).toContain('Diagnostic à distance');
    expect(lire(page)).toContain('Autres bracelets muets');
    bouton(page, 'Lancer le diagnostic').click();
    await fixture.whenStable();
    expect(lire(page)).toContain('Rendez-vous en point relais');
    expect(lire(page)).toContain('Sans réponse');
    expect(lire(page)).not.toContain('Appeler le parent');

    // Rendez-vous incomplet : rien n'est envoyé.
    bouton(page, 'Proposer le rendez-vous').click();
    await fixture.whenStable();
    expect(lire(page)).toContain('Indiquez le point relais');
    saisir(page, 'Point relais', 'Dassasgho');
    saisir(page, 'Rendez-vous', '2026-10-17T10:00');
    bouton(page, 'Proposer le rendez-vous').click();
    await fixture.whenStable();
    expect(client.mouvements).toEqual(['diagnostic t-1', 'rendez-vous t-1 Dassasgho 2026-10-17']);
    expect(lire(page)).toContain('Remise en point relais');

    saisir(page, 'Unité remise', 'fg-5120');
    bouton(page, 'Réserver l’unité'.replace('’', "'")).click();
    await fixture.whenStable();
    expect(client.mouvements.at(-1)).toBe('remplacement t-1 fg-5120');
    expect(lire(page)).toContain('FG-5120');
    expect(lire(page)).toContain('Le parent l’associe lui-même'.replace('’', "'"));
  });

  it('situe le ticket dans son parcours', () => {
    expect(etapeDe(ticket())).toBe(0);
    expect(etapeDe(ticket({ statut: 'EN_COURS' }))).toBe(1);
    expect(etapeDe(ticket({ statut: 'EN_COURS', diagnosticLe: 'x' }))).toBe(2);
    expect(etapeDe(ticket({ statut: 'EN_COURS', diagnosticLe: 'x' }), true)).toBe(3);
    expect(etapeDe(ticket({ statut: 'EN_COURS', diagnosticLe: 'x', rendezVousLe: 'y' }))).toBe(3);
    expect(etapeDe(ticket({ statut: 'RESOLU' }))).toBe(4);
  });

  it('écrit les durées en minutes puis en heures', () => {
    const maintenant = Date.parse('2026-10-09T12:00:00Z');
    expect(depuis('2026-10-09T11:42:00Z', maintenant)).toBe('18 min');
    expect(depuis('2026-10-09T07:00:00Z', maintenant)).toBe('5 h');
  });
});
