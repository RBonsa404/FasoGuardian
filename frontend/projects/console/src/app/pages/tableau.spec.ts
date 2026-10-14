import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { ClientTableau, GroupeIndicateurs } from 'api';

import { Tableau } from './tableau';

function lire(element: Element): string {
  return (element.textContent ?? '').replace(/\s+/g, ' ');
}

async function monter(groupes: GroupeIndicateurs[]) {
  TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: ClientTableau, useValue: { indicateurs: () => of(groupes) } }] });
  const fixture = TestBed.createComponent(Tableau);
  await fixture.whenStable();
  return fixture.nativeElement as HTMLElement;
}

describe('tableau de bord', () => {
  it('montre les indicateurs de la file KYC, avec leurs repères', async () => {
    const page = await monter([
      {
        role: 'KYC',
        indicateurs: [
          { code: 'kyc.enFile', valeur: 38, repere: null, alerte: false },
          { code: 'kyc.prioritaires', valeur: 6, repere: 36, alerte: true },
          { code: 'kyc.delaiMedian', valeur: 26, repere: 48, alerte: false },
          { code: 'kyc.mesDecisions', valeur: 14, repere: null, alerte: false },
        ],
      },
    ]);

    const cartes = [...page.querySelectorAll('dl > div')].map(lire);
    expect(cartes[0]).toContain('En file');
    expect(cartes[0]).toContain('38');
    expect(cartes[1]).toContain('> 36 h');
    expect(cartes[1]).toContain('à traiter en priorité');
    expect(cartes[2]).toContain('26 h');
    expect(cartes[2]).toContain('objectif 48 h');
    expect(cartes[3]).toContain('Mes décisions · jour');
    expect(page.querySelectorAll('dl > div')[1].className).toContain('border-attention');
    expect(lire(page)).toContain('File KYC');
  });

  it('un délai sans décision sur la période s’affiche comme non mesuré', async () => {
    const page = await monter([{ role: 'KYC', indicateurs: [{ code: 'kyc.delaiMedian', valeur: null, repere: 48, alerte: false }] }]);

    expect(lire(page.querySelector('dl > div')!)).toContain('—');
  });

  it('présente un groupe par rôle, avec les accès à ses écrans', async () => {
    const page = await monter([
      { role: 'SUPPORT', indicateurs: [{ code: 'support.ouvertes', valeur: 3, repere: null, alerte: false }] },
      { role: 'SAV', indicateurs: [{ code: 'sav.enStock', valeur: 0, repere: null, alerte: true }] },
    ]);

    expect([...page.querySelectorAll('h2')].map(lire)).toEqual(['Support', 'Service après-vente']);
    expect(lire(page)).toContain('stock épuisé');
    expect([...page.querySelectorAll('a')].map((a) => a.getAttribute('href'))).toEqual(['/support/tickets', '/support/faq', '/sav/muets', '/sav/parc', '/sav/ota']);
  });

  it('dit quand aucun écran n’est disponible pour le rôle', async () => {
    const page = await monter([]);

    expect(lire(page)).toContain('pas encore disponibles');
  });
});
