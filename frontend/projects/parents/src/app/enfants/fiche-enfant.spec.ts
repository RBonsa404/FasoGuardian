import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { Bracelet, ClientBracelet, ClientFamille, FicheEnfant, RevisionEnfant } from 'api';

import { FicheEnfantEcran } from './enfants';

const AWA: FicheEnfant = {
  id: 'e-1',
  prenom: 'Awa',
  nom: 'Ouédraogo',
  dateNaissance: '2018-03-14',
  profil: { ecole: 'Les Manguiers · CE2', quartier: 'Ouaga 2000', tailleCm: 128, signesDistinctifs: 'Petite cicatrice au menton' },
  modifieLe: '2026-10-02T10:00:00Z',
  photoModifieeLe: null,
};

const ANNEE = new Date().getFullYear();

const HISTORIQUE: RevisionEnfant[] = [
  { champ: 'signesDistinctifs', modifieLe: `${ANNEE}-10-02T10:00:00Z`, auteur: 'Mariam', parVous: true },
  { champ: 'ecole', modifieLe: `${ANNEE}-09-15T10:00:00Z`, auteur: 'Ibrahim', parVous: false },
  { champ: 'profil', modifieLe: '2024-09-01T10:00:00Z', auteur: '', parVous: false },
];

const INTROUVABLE = new HttpErrorResponse({ status: 404, error: { status: 404, code: 'RESSOURCE_INTROUVABLE', detail: 'Absent.' } });

function lire(element: Element): string {
  return (element.textContent ?? '').replace(/\s+/g, ' ').trim();
}

/** Chaque ligne de la fiche, libellé et valeur séparés par une espace. */
function lignes(page: HTMLElement): string[] {
  return [...page.querySelectorAll('dl > div')].map((ligne) => `${lire(ligne.querySelector('dt')!)} ${lire(ligne.querySelector('dd')!)}`);
}

async function ouvrir(bracelet: Partial<ClientBracelet>, famille: Partial<ClientFamille> = {}) {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      { provide: ClientBracelet, useValue: bracelet },
      { provide: ClientFamille, useValue: { enfant: () => of(AWA), historique: () => of(HISTORIQUE), ...famille } },
    ],
  });
  const fixture = TestBed.createComponent(FicheEnfantEcran);
  fixture.componentRef.setInput('id', 'e-1');
  await fixture.whenStable();
  return fixture.nativeElement as HTMLElement;
}

describe('fiche enfant', () => {
  it('présente le profil, la taille en mètres et le bracelet associé', async () => {
    const page = await ouvrir({ bracelet: () => of({ numeroSerie: 'FG-2291' } as Bracelet) });

    expect(lire(page.querySelector('h1')!)).toBe('Awa Ouédraogo');
    expect(lignes(page)).toEqual([
      'École Les Manguiers · CE2',
      'Quartier Ouaga 2000',
      'Taille 1,28 m',
      'Signes distinctifs Petite cicatrice au menton',
      'Bracelet FG-2291',
    ]);
  });

  it('dit qui a modifié quoi, sans recopier les valeurs', async () => {
    const page = await ouvrir({ bracelet: () => of({ numeroSerie: 'FG-2291' } as Bracelet) });

    const entrees = [...page.querySelectorAll('section li')].map(lire);
    expect(entrees).toEqual([
      '2 oct. · Mariam · signes distinctifs modifiés',
      '15 sept. · Ibrahim (tuteur) · école modifiée',
      '1 sept. 2024 · un autre tuteur · école, quartier ou signes distinctifs modifiés',
    ]);
  });

  it('montre la photo chargée par l’API authentifiée, sinon l’initiale', async () => {
    const photo = vi.fn().mockReturnValue(of(new Blob(['png'], { type: 'image/png' })));
    const avec = await ouvrir(
      { bracelet: () => of({ numeroSerie: 'FG-2291' } as Bracelet) },
      { enfant: () => of({ ...AWA, photoModifieeLe: '2026-10-02T10:00:00Z' }), photo } as Partial<ClientFamille>,
    );
    expect(photo).toHaveBeenCalledWith('e-1');
    const image = avec.querySelector('img') as HTMLImageElement;
    expect(image.getAttribute('src')).toMatch(/^blob:/);
    expect(image.alt).toBe("Photo d'Awa");

    TestBed.resetTestingModule();
    const sans = await ouvrir({ bracelet: () => of({ numeroSerie: 'FG-2291' } as Bracelet) }, { photo } as Partial<ClientFamille>);
    expect(sans.querySelector('img')).toBeNull();
    expect(photo).toHaveBeenCalledTimes(1);
  });

  it('signale l’absence de bracelet', async () => {
    const page = await ouvrir({ bracelet: () => throwError(() => INTROUVABLE) });

    expect(lignes(page).at(-1)).toBe('Bracelet aucun bracelet associé');
  });
});
