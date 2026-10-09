import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { ClientAuthentification, ClientFds, ConstatSignalement } from 'api';

import { SignalementFds } from './signalement';

const CONSTAT: ConstatSignalement = {
  reference: 'FG-SIG-000412',
  nature: 'Disparition signalée par un tuteur',
  etabliLe: '2026-10-09T14:41:00Z',
  accuseLe: null,
  dossierConsultable: false,
};

/** Double du client : retient ce que l'agent demande. */
class FdsFactice {
  recherches: string[] = [];
  accuses: string[] = [];
  refus = false;

  constat(reference: string) {
    this.recherches.push(reference);
    if (this.refus) {
      return throwError(() => new HttpErrorResponse({ status: 403 }));
    }
    return reference === CONSTAT.reference
      ? of(CONSTAT)
      : throwError(() => new HttpErrorResponse({ status: 404, error: { status: 404, code: 'RESSOURCE_INTROUVABLE', detail: 'Aucun signalement ne porte cette référence.' } }));
  }

  accuser(reference: string) {
    this.accuses.push(reference);
    return of({ ...CONSTAT, accuseLe: '2026-10-09T14:52:00Z' });
  }
}

function lire(element: Element): string {
  return (element.textContent ?? '').replace(/\s+/g, ' ');
}

function bouton(page: HTMLElement, nom: string): HTMLButtonElement {
  return [...page.querySelectorAll('button')].find((b) => lire(b).includes(nom)) as HTMLButtonElement;
}

async function ouvrir(client: FdsFactice) {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      { provide: ClientFds, useValue: client },
      { provide: ClientAuthentification, useValue: { moi: () => of({ identifiant: 'k.sawadogo' }) } },
    ],
  });
  const fixture = TestBed.createComponent(SignalementFds);
  await fixture.whenStable();
  return fixture;
}

async function chercher(fixture: Awaited<ReturnType<typeof ouvrir>>, reference: string) {
  const page = fixture.nativeElement as HTMLElement;
  const champ = page.querySelector('fg-input input') as HTMLInputElement;
  champ.value = reference;
  champ.dispatchEvent(new Event('input'));
  bouton(page, 'Retrouver').click();
  await fixture.whenStable();
}

describe('espace des forces de sécurité', () => {
  it("n'affiche rien sans référence, puis le signalement désigné, et accuse réception au nom de l'agent", async () => {
    const client = new FdsFactice();
    const fixture = await ouvrir(client);
    const page = fixture.nativeElement as HTMLElement;

    expect(page.querySelector('section')).toBeNull();
    bouton(page, 'Retrouver').click();
    await fixture.whenStable();
    expect(lire(page)).toContain('Saisissez la référence du signalement.');
    expect(client.recherches).toEqual([]);

    await chercher(fixture, ' FG-SIG-000412 ');
    expect(client.recherches).toEqual(['FG-SIG-000412']);
    expect(lire(page)).toContain('Disparition signalée par un tuteur');
    expect(lire(page)).toContain('Remis en main propre par le parent');
    expect(lire(page)).toContain('Accès limité à 30 jours · chaque consultation est tracée.');
    // Remis par le parent : le dossier n'est pas proposé ici.
    expect(bouton(page, 'Ouvrir le dossier')).toBeUndefined();

    const accuser = bouton(page, 'Accuser réception');
    expect(lire(accuser)).toContain('agent k.sawadogo');
    accuser.click();
    await fixture.whenStable();

    expect(client.accuses).toEqual(['FG-SIG-000412']);
    expect(lire(page)).toContain('Réception accusée le');
    expect(lire(page)).toContain('Les parents en ont été informés.');
    expect(bouton(page, 'Accuser réception')).toBeUndefined();
  });

  it('dit qu’aucun signalement ne porte une référence inconnue', async () => {
    const fixture = await ouvrir(new FdsFactice());

    await chercher(fixture, 'FG-SIG-999999');

    const page = fixture.nativeElement as HTMLElement;
    expect(lire(page)).toContain('Aucun signalement ne porte cette référence.');
    expect(page.querySelector('section')).toBeNull();
  });

  it('renvoie à l’écran de refus quand le serveur refuse', async () => {
    const client = new FdsFactice();
    client.refus = true;
    const fixture = await ouvrir(client);
    const navigation = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    await chercher(fixture, 'FG-SIG-000412');

    expect(navigation).toHaveBeenCalledWith(['/refuse'], { queryParams: { role: 'FDS' } });
  });
});
