import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { Alerte, ClientAlertes, ClientFamille, ClientNotifications, FicheEnfant, NotificationRecue } from 'api';

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

/** Notifications hors alertes, relues dans le centre. */
const INFORMATIONS: NotificationRecue[] = [
  {
    id: 'n-1',
    modele: 'MISE_A_JOUR_BRACELET',
    titre: 'Mise à jour du bracelet',
    texte: 'une mise à jour (2.4.2) va être installée sur le bracelet FG-2291. Il reste utilisable ; gardez-le chargé.',
    lien: '/enfants/e-1/bracelet',
    creeeLe: new Date(Date.now() - 120_000).toISOString(),
  },
];

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
      providers: [
        provideRouter([]),
        { provide: ClientAlertes, useValue: client },
        { provide: ClientFamille, useValue: { mesEnfants: () => of([AWA, YACOUBA]) } },
        { provide: ClientNotifications, useValue: { recues: () => of(INFORMATIONS) } },
      ],
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

    const groupes = page.querySelectorAll('section:not([aria-labelledby])');
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

    const titres = [...page.querySelectorAll('section:not([aria-labelledby]) h2')].map((titre) => lire(titre).trim());
    expect(titres).toEqual(['Awa', 'Yacouba']);
    expect(lire(page)).not.toContain('Regroupées par ordre de gravité');
  });

  it('garde lisibles les notifications reçues hors alertes, même sans push', async () => {
    const { page } = await monter(new AlertesFactices());

    expect(lire(page)).toContain('Aucune alerte en cours.');
    const information = page.querySelector('section[aria-labelledby="titre-informations"] a') as HTMLAnchorElement;
    expect(lire(information)).toContain('Mise à jour du bracelet');
    expect(lire(information)).toContain('Une mise à jour (2.4.2) va être installée sur le bracelet FG-2291.');
    expect(lire(information)).toContain('il y a 2 min');
    expect(information.getAttribute('href')).toBe('/enfants/e-1/bracelet');
  });
});
