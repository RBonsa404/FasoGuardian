import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { ClientFamille, FicheSante, RevisionSante } from 'api';

import { Medical } from './medical';

const FICHE: FicheSante = {
  groupeSanguin: 'O+',
  groupeSanguinSurQr: true,
  elements: [
    { type: 'ALLERGIE', libelle: 'Arachides (sévère)', critique: true },
    { type: 'TRAITEMENT', libelle: 'Ventoline si crise', critique: false },
  ],
  modifieLe: '2026-10-02T10:00:00Z',
};

const REVISIONS: RevisionSante[] = [
  { nombreElements: 2, nombreCritiques: 1, modifieLe: '2026-10-02T10:00:00Z', auteur: 'Mariam', parVous: true },
  { nombreElements: 1, nombreCritiques: 1, modifieLe: '2026-09-15T10:00:00Z', auteur: 'Ibrahim', parVous: false },
];

function lire(element: Element): string {
  return (element.textContent ?? '').replace(/\s+/g, ' ').trim();
}

function bascule(page: HTMLElement, nom: string): HTMLButtonElement {
  return page.querySelector(`[role="switch"][aria-label^="${nom}"]`) as HTMLButtonElement;
}

function bouton(page: HTMLElement, nom: string): HTMLButtonElement {
  return [...page.querySelectorAll('button')].find((b) => lire(b) === nom) as HTMLButtonElement;
}

async function ouvrir(famille: Partial<ClientFamille>) {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      { provide: ClientFamille, useValue: { sante: () => of(FICHE), revisionsSante: () => of(REVISIONS), ...famille } },
    ],
  });
  const fixture = TestBed.createComponent(Medical);
  fixture.componentRef.setInput('id', 'e-1');
  await fixture.whenStable();
  return { fixture, page: fixture.nativeElement as HTMLElement };
}

describe('fiche médicale', () => {
  it('présente chaque information avec son état critique ou privé et la dernière révision', async () => {
    const { page } = await ouvrir({});

    // Chaque carte : type, information, état ; les textes feuilles sont lus séparément.
    const cartes = [...page.querySelectorAll('ul > li')].map((carte) =>
      [...carte.querySelectorAll('span')].filter((s) => s.children.length === 0 && lire(s)).map(lire).join(' '),
    );
    expect(cartes).toEqual([
      'Allergie Arachides (sévère) Critique',
      'Traitement Ventoline si crise Privé',
      'Groupe sanguin O+ Critique',
    ]);
    expect(bascule(page, 'Arachides').getAttribute('aria-checked')).toBe('true');
    expect(lire(page)).toContain('Dernière révision : 2 oct. 2026 par Mariam');
  });

  it('enregistre aussitôt une bascule, et la remet en place si le serveur refuse', async () => {
    const enregistrerSante = vi.fn().mockReturnValueOnce(of({ ...FICHE, elements: [FICHE.elements[0], { ...FICHE.elements[1], critique: true }] }));
    const { fixture, page } = await ouvrir({ enregistrerSante } as Partial<ClientFamille>);

    bascule(page, 'Ventoline').click();
    await fixture.whenStable();
    expect(enregistrerSante).toHaveBeenCalledWith('e-1', {
      groupeSanguin: 'O+',
      groupeSanguinSurQr: true,
      elements: [
        { type: 'ALLERGIE', libelle: 'Arachides (sévère)', critique: true },
        { type: 'TRAITEMENT', libelle: 'Ventoline si crise', critique: true },
      ],
    });
    expect(bascule(page, 'Ventoline').getAttribute('aria-checked')).toBe('true');

    enregistrerSante.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 503, error: { status: 503, detail: 'Indisponible.' } })));
    bascule(page, 'Arachides').click();
    await fixture.whenStable();
    expect(bascule(page, 'Arachides').getAttribute('aria-checked')).toBe('true');
    expect(page.querySelector('fg-banner')).not.toBeNull();
  });

  it('ouvre le journal des révisions avec leur auteur', async () => {
    const { fixture, page } = await ouvrir({});

    bouton(page, 'journal des révisions').click();
    await fixture.whenStable();
    const journal = [...page.querySelectorAll('ul[aria-label="Journal des révisions"] li')].map(lire);
    expect(journal).toHaveLength(2);
    expect(journal[1]).toContain('Ibrahim (tuteur)');
    expect(journal[1]).toContain('1 information(s), dont 1 critique(s)');
  });

  it('n’enregistre rien en modification avant « Enregistrer », et l’annulation rend l’état enregistré', async () => {
    const enregistrerSante = vi.fn();
    const { fixture, page } = await ouvrir({ enregistrerSante } as Partial<ClientFamille>);

    bouton(page, 'Modifier les informations').click();
    await fixture.whenStable();
    bascule(page, 'Critique').click();
    bouton(page, 'Annuler').click();
    await fixture.whenStable();
    expect(enregistrerSante).not.toHaveBeenCalled();
    expect(bascule(page, 'Arachides').getAttribute('aria-checked')).toBe('true');
  });
});
