import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';

import { ClientFamille, ClientPartages, ContactUrgence, FicheEnfant, PartagePosition, VuePartagee } from 'api';

import { Partage, decompte } from './partage';
import { VueContact } from './vue-contact';

const ONCLE: ContactUrgence = { id: 'c-1', lien: 'Oncle', nom: 'Issouf Ouédraogo', telephone: '+22676112208', visibleSurQr: false, rang: 1 };
const TANTE: ContactUrgence = { id: 'c-2', lien: 'Tante', nom: 'Fatou Sawadogo', telephone: '+22670556677', visibleSurQr: true, rang: 2 };
const INTROUVABLE = new HttpErrorResponse({ status: 404 });

function partage(partiel: Partial<PartagePosition> = {}): PartagePosition {
  return {
    id: 'p-1',
    lien: 'Oncle',
    destinataire: '+226 76 •• •• 08',
    debut: new Date().toISOString(),
    fin: new Date(Date.now() + 84 * 60_000 + 10_000).toISOString(),
    ouvertures: 2,
    derniereOuverture: '2026-10-09T16:31:00Z',
    ...partiel,
  };
}

/** Double du client : l'essai règle le partage en cours et relit ce que l'écran a demandé. */
class PartagesFactices {
  courant: PartagePosition | null = null;
  demandes: { contact: string; minutes: number }[] = [];
  revocations = 0;
  vue: Observable<VuePartagee> = throwError(() => INTROUVABLE);

  enCours() {
    return this.courant ? of(this.courant) : throwError(() => INTROUVABLE);
  }

  partager(_enfant: string, contact: string, minutes: number) {
    this.demandes.push({ contact, minutes });
    this.courant = partage({ ouvertures: 0, derniereOuverture: null });
    return of(this.courant);
  }

  revoquer() {
    this.revocations++;
    this.courant = null;
    return of(undefined);
  }

  consulter() {
    return this.vue;
  }
}

function lire(element: Element): string {
  return (element.textContent ?? '').replace(/\s+/g, ' ');
}

function bouton(page: HTMLElement, nom: string): HTMLButtonElement {
  return [...page.querySelectorAll('button')].find((b) => lire(b).includes(nom)) as HTMLButtonElement;
}

describe('partage temporaire de la position', () => {
  async function monter(client: PartagesFactices, contacts: ContactUrgence[] = [ONCLE, TANTE]) {
    const famille = { enfant: () => of({ id: 'e-1', prenom: 'Awa' } as FicheEnfant), contacts: () => of(contacts) };
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: ClientPartages, useValue: client }, { provide: ClientFamille, useValue: famille }],
    });
    const fixture = TestBed.createComponent(Partage);
    fixture.componentRef.setInput('id', 'e-1');
    await fixture.whenStable();
    return { fixture, page: fixture.nativeElement as HTMLElement };
  }

  it('envoie le lien au contact et pour la durée choisis', async () => {
    const client = new PartagesFactices();
    const { fixture, page } = await monter(client);
    expect(lire(page)).toContain('Il voit où est Awa pendant la durée choisie');

    const tante = [...page.querySelectorAll('label')].find((l) => lire(l).includes('Tante'))!;
    (tante.querySelector('input') as HTMLInputElement).click();
    const deuxHeures = [...page.querySelectorAll('label')].find((l) => lire(l).trim() === '2 h')!;
    (deuxHeures.querySelector('input') as HTMLInputElement).click();
    await fixture.whenStable();
    bouton(page, 'Envoyer le lien par SMS').click();
    await fixture.whenStable();

    expect(client.demandes).toEqual([{ contact: 'c-2', minutes: 120 }]);
    expect(lire(page)).toContain('Partage en cours');
    expect(lire(page)).toContain('pas encore');
  });

  it('montre le partage en cours, son décompte et ses ouvertures, et le révoque', async () => {
    const client = new PartagesFactices();
    client.courant = partage();
    const { fixture, page } = await monter(client);

    expect(lire(page)).toContain('Oncle voit la position de Awa jusqu');
    expect(page.querySelector('[role="timer"]')?.textContent).toMatch(/1:2[34]:\d\d/);
    expect(lire(page)).toContain('+226 76 •• •• 08');
    expect(lire(page)).toContain('SMS · lien unique');
    expect(lire(page)).toMatch(/2 fois · dernier \d\d:\d\d/);

    bouton(page, 'Révoquer maintenant').click();
    await fixture.whenStable();

    expect(client.revocations).toBe(1);
    expect(lire(page)).toContain('Partager la position');
  });

  it('sans contact d’urgence, invite à en ajouter un plutôt qu’à saisir un numéro', async () => {
    const { page } = await monter(new PartagesFactices(), []);

    expect(lire(page)).toContain("Ajoutez d'abord un contact d'urgence");
    expect(bouton(page, 'Envoyer le lien par SMS')).toBeUndefined();
    expect(page.querySelector('input[type="tel"]')).toBeNull();
  });

  it('écrit le temps restant en heures, minutes et secondes', () => {
    expect(decompte(5050)).toBe('1:24:10');
    expect(decompte(59)).toBe('0:00:59');
    expect(decompte(-3)).toBe('0:00:00');
  });
});

describe('vue du contact secondaire', () => {
  async function monter(client: PartagesFactices) {
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: ClientPartages, useValue: client }] });
    const fixture = TestBed.createComponent(VueContact);
    fixture.componentRef.setInput('jeton', 'jeton-de-22-caracteres');
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('montre qui partage, pour combien de temps, et la position avec sa précision', async () => {
    const client = new PartagesFactices();
    client.vue = of({
      partagePar: 'Mariam',
      fin: new Date(Date.now() + 84 * 60_000).toISOString(),
      position: { latitude: 12.3714, longitude: -1.5197, precisionM: 20, approximative: false, mesureeLe: new Date(Date.now() - 60_000).toISOString() },
    });

    const page = await monter(client);

    expect(lire(page)).toContain('Position partagée par Mariam');
    expect(lire(page)).toContain('Lien personnel · ne le transférez pas');
    expect(page.querySelector('[role="timer"]')?.textContent).toMatch(/1:2[34]:\d\d/);
    expect(lire(page)).toContain('Position précise ± 20 m');
  });

  it('dit que le bracelet n’a pas donné de position récente', async () => {
    const client = new PartagesFactices();
    client.vue = of({ partagePar: null, fin: new Date(Date.now() + 600_000).toISOString(), position: null });

    const page = await monter(client);

    expect(lire(page)).toContain('Position partagée avec vous');
    expect(lire(page)).toContain("Le bracelet n'a pas donné de position récente.");
  });

  it('lien expiré, révoqué ou inconnu : le même message, sans rien d’autre', async () => {
    const page = await monter(new PartagesFactices());

    expect(lire(page)).toContain('Ce partage est terminé.');
    expect(page.querySelector('app-carte')).toBeNull();
    expect(page.querySelector('[role="timer"]')).toBeNull();
  });
});
