import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { Alerte, ClientAlertes, ClientFamille, FicheEnfant } from 'api';

import { CentreAlertes } from './centre';

function alerte(partiel: Partial<Alerte>): Alerte {
  return {
    id: 'a-0',
    enfantId: 'e-1',
    type: 'SOS',
    gravite: 'CRITIQUE',
    statut: 'OUVERTE',
    ouverteLe: new Date(Date.now() - 60_000).toISOString(),
    closeLe: null,
    libelle: null,
    latitude: null,
    longitude: null,
    actions: [],
    ...partiel,
  };
}

const AWA = { id: 'e-1', prenom: 'Awa' } as FicheEnfant;
const YACOUBA = { id: 'e-2', prenom: 'Yacouba' } as FicheEnfant;

/** Double du client : rend les alertes réglées par l'essai et retient les prises en charge. */
class AlertesFactices {
  liste: Alerte[] = [];
  prises: string[] = [];

  mesAlertes() {
    return of(this.liste);
  }

  prendreEnCharge(enfantId: string) {
    this.prises.push(enfantId);
    this.liste = this.liste.map((a) => (a.enfantId === enfantId && a.statut === 'OUVERTE' ? { ...a, statut: 'ACQUITTEE' as const } : a));
    return of(this.liste);
  }
}

function lire(element: Element): string {
  return (element.textContent ?? '').replace(/\s+/g, ' ');
}

describe('alertes simultanées (US-PAR-018)', () => {
  async function monter(client: AlertesFactices) {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: ClientAlertes, useValue: client }, { provide: ClientFamille, useValue: { mesEnfants: () => of([AWA, YACOUBA]) } }],
    });
    const fixture = TestBed.createComponent(CentreAlertes);
    await fixture.whenStable();
    return { fixture, page: fixture.nativeElement as HTMLElement };
  }

  it('regroupe un SOS et une sortie de zone du même enfant en une seule alerte, le SOS en tête', async () => {
    const client = new AlertesFactices();
    // La sortie de zone est arrivée la première : l'ordre d'affichage suit la gravité, pas l'heure.
    client.liste = [
      alerte({ id: 'a-1', type: 'SORTIE_ZONE', gravite: 'IMPORTANTE', libelle: 'École Les Manguiers', ouverteLe: new Date(Date.now() - 120_000).toISOString() }),
      alerte({ id: 'a-2', type: 'SOS', gravite: 'CRITIQUE' }),
    ];

    const { page } = await monter(client);

    const groupes = page.querySelectorAll('section');
    expect(groupes).toHaveLength(1);
    expect(lire(groupes[0].querySelector('h2')!)).toContain('2 alertes · Awa');
    expect(lire(groupes[0])).toContain('Regroupées par ordre de gravité');
    const lignes = [...groupes[0].querySelectorAll('ol li')].map((ligne) => lire(ligne));
    expect(lignes).toHaveLength(2);
    expect(lignes[0]).toContain('SOS');
    expect(lignes[1]).toContain('École Les Manguiers');
    expect(lire(groupes[0])).toContain('Une seule prise en charge suffit.');
  });

  it('une seule prise en charge vaut pour tout le groupe', async () => {
    const client = new AlertesFactices();
    client.liste = [alerte({ id: 'a-1', type: 'SORTIE_ZONE', gravite: 'IMPORTANTE', libelle: 'Maison' }), alerte({ id: 'a-2' })];
    const { fixture, page } = await monter(client);
    const boutons = [...page.querySelectorAll('section button')];
    expect(boutons).toHaveLength(1);
    expect(lire(boutons[0])).toContain('Tout prendre en charge');

    (boutons[0] as HTMLButtonElement).click();
    await fixture.whenStable();

    expect(client.prises).toEqual(['e-1']);
    expect(page.querySelectorAll('section button')).toHaveLength(0);
    // Les deux alertes restent visibles et distinctes : chacune garde sa trace.
    expect(page.querySelectorAll('section ol li')).toHaveLength(2);
  });

  it('ne mêle pas les alertes de deux enfants', async () => {
    const client = new AlertesFactices();
    client.liste = [alerte({ id: 'a-1' }), alerte({ id: 'a-2', enfantId: 'e-2', type: 'CHUTE', gravite: 'IMPORTANTE' })];

    const { page } = await monter(client);

    const titres = [...page.querySelectorAll('section h2')].map((titre) => lire(titre).trim());
    expect(titres).toEqual(['Awa', 'Yacouba']);
    expect(lire(page)).not.toContain('Regroupées par ordre de gravité');
  });
});
