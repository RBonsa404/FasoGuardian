import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { ClientConsole, ClientTableau, DossierKycFile } from 'api';

import { FileKyc } from './file';

const HEURE = 3_600_000;

function dossier(partiel: Partial<DossierKycFile>): DossierKycFile {
  return {
    id: 'd-' + Math.random(),
    reference: 'KYC-20418',
    statut: 'DEPOSE',
    canal: 'EN_LIGNE',
    natureLien: 'PARENT',
    demandeur: 'Ouédraogo Mariam',
    enfantPrenom: 'Awa',
    enfantAge: 8,
    deposeLe: new Date(Date.now() - 2 * HEURE).toISOString(),
    prisEnCharge: false,
    prisEnChargeParMoi: false,
    secondTuteur: false,
    complementRecu: false,
    ...partiel,
  };
}

describe('file KYC (écran 59)', () => {
  async function monter() {
    const elements = [
      dossier({ reference: 'KYC-20391', deposeLe: new Date(Date.now() - 41 * HEURE).toISOString() }),
      dossier({ reference: 'KYC-20402', canal: 'POINT_INSCRIPTION' }),
      dossier({ reference: 'KYC-20433', complementRecu: true }),
    ];
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: ClientConsole, useValue: { fileKyc: () => of({ elements, page: 0, taille: 100, total: 3 }) } },
        {
          provide: ClientTableau,
          useValue: {
            indicateurs: () =>
              of([{ role: 'KYC', indicateurs: [{ code: 'kyc.delaiMedian', valeur: 26, repere: 48, alerte: false }, { code: 'kyc.mesDecisions', valeur: 14, repere: null, alerte: false }] }]),
          },
        },
      ],
    });
    const fixture = TestBed.createComponent(FileKyc);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    const references = () => Array.from(page.querySelectorAll('a[role=row] span.font-mono'), (cellule) => cellule.textContent?.trim());
    const filtrer = async (libelle: RegExp) => {
      (Array.from(page.querySelectorAll('[role=group] button')).find((b) => libelle.test(b.textContent ?? '')) as HTMLButtonElement).click();
      await fixture.whenStable();
    };
    return { page, references, filtrer };
  }

  it('reprend les indicateurs du tableau de bord : délai médian et décisions du jour', async () => {
    const { page } = await monter();
    expect(page.textContent).toContain('Délai médian');
    expect(page.textContent).toContain('26 h');
    expect(page.textContent).toContain('Mes décisions · jour');
  });

  it('filtre les dossiers prioritaires, par canal et ceux qui reviennent d’un complément', async () => {
    const { page, references, filtrer } = await monter();
    expect(page.querySelector('[role=group]')?.textContent).toContain('Tous · 3');
    expect(references()).toEqual(['KYC-20391', 'KYC-20402', 'KYC-20433']);

    await filtrer(/> 36 h · 1/);
    expect(references()).toEqual(['KYC-20391']);
    await filtrer(/Point d'inscription/);
    expect(references()).toEqual(['KYC-20402']);
    await filtrer(/Complément reçu · 1/);
    expect(references()).toEqual(['KYC-20433']);
    expect(page.textContent).toContain('Complément reçu');
  });
});
