import { Injectable } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { ClientBracelet, ClientFamille, FicheEnfant, FicheSante } from 'api';

import { CopieLocale, FicheLocale, TableDeFiches } from '../commun/copie-locale';
import { SessionExpiree, age } from './session-expiree';

const AWA: FicheLocale = {
  id: 'e-1',
  prenom: 'Awa',
  nom: 'Ouédraogo',
  dateNaissance: '2018-03-12',
  ecole: 'École Les Manguiers',
  quartier: 'Ouaga 2000',
  groupeSanguin: 'O+',
  critiques: [{ type: 'ALLERGIE', libelle: 'Arachides (sévère)' }],
  bracelet: 'FG-2291',
  copieLe: '2026-10-07T08:12:00Z',
};

function lire(element: Element): string {
  return (element.textContent ?? '').replace(/\s+/g, ' ');
}

describe('session expirée', () => {
  async function monter(fiches: FicheLocale[]) {
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: CopieLocale, useValue: { fiches: async () => fiches } }] });
    const fixture = TestBed.createComponent(SessionExpiree);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('montre la fiche gardée sur l’appareil, avec la date de sa copie', async () => {
    const page = await monter([AWA]);

    expect(lire(page)).toContain('Votre session a expiré');
    expect(lire(page)).toContain('La fiche de Awa reste consultable.');
    expect(lire(page)).toContain('Awa Ouédraogo');
    expect(lire(page)).toContain('École Les Manguiers, Ouaga 2000');
    expect(lire(page)).toContain('Groupe sanguinO+');
    expect(lire(page)).toContain('AllergieArachides (sévère)');
    expect(lire(page)).toContain('BraceletFG-2291');
    expect(lire(page)).toMatch(/Copie locale du 7 oct\. à \d\d:12/);
    // Ni position ni contact : la copie locale n'en porte pas.
    expect(lire(page)).not.toMatch(/\+226|latitude|position de/i);
  });

  it('propose toujours de se reconnecter et d’appeler le 17', async () => {
    const page = await monter([]);

    expect(lire(page)).toContain('Reconnectez-vous pour voir la position en direct.');
    expect(page.querySelector('section')).toBeNull();
    const liens = [...page.querySelectorAll('a')].map((lien) => lien.getAttribute('href'));
    expect(liens).toContain('/connexion');
    expect(liens).toContain('tel:17');
  });

  it('calcule l’âge en années révolues', () => {
    expect(age('2018-03-12', new Date(2026, 9, 9))).toBe(8);
    expect(age('2018-11-30', new Date(2026, 9, 9))).toBe(7);
  });
});

describe('copie locale des fiches', () => {
  /** Table en mémoire, à la place d'IndexedDB. */
  class TableEnMemoire implements TableDeFiches {
    lignes: FicheLocale[] = [];

    async toArray() {
      return [...this.lignes];
    }

    async bulkPut(fiches: FicheLocale[]) {
      this.lignes.push(...fiches);
    }

    async clear() {
      this.lignes = [];
    }
  }

  @Injectable()
  class CopieEnMemoire extends CopieLocale {
    readonly memoire = new TableEnMemoire();

    protected override ouvrir() {
      return Promise.resolve(this.memoire);
    }
  }

  const ENFANT = { id: 'e-1', prenom: 'Awa', nom: 'Ouédraogo', dateNaissance: '2018-03-12', profil: { ecole: 'École Les Manguiers', quartier: null, tailleCm: 120, signesDistinctifs: 'Cicatrice' } } as FicheEnfant;
  const SANTE = {
    groupeSanguin: 'O+',
    groupeSanguinSurQr: true,
    elements: [
      { type: 'ALLERGIE', libelle: 'Arachides', critique: true },
      { type: 'TRAITEMENT', libelle: 'Vitamine D', critique: false },
    ],
    modifieLe: null,
  } as FicheSante;

  /** Doubles partiels des clients : seules les méthodes que la copie appelle sont fournies. */
  function preparer(famille: object, bracelet: object) {
    TestBed.configureTestingModule({
      providers: [{ provide: CopieLocale, useClass: CopieEnMemoire }, { provide: ClientFamille, useValue: famille }, { provide: ClientBracelet, useValue: bracelet }],
    });
    return TestBed.inject(CopieLocale) as CopieEnMemoire;
  }

  it('ne garde que l’identité, les éléments critiques et le numéro du bracelet', async () => {
    const copie = preparer({ mesEnfants: () => of([ENFANT]), sante: () => of(SANTE) }, { bracelet: () => of({ numeroSerie: 'FG-2291' }) });

    await copie.synchroniser();
    const [fiche] = await copie.fiches();

    expect(fiche.critiques).toEqual([{ type: 'ALLERGIE', libelle: 'Arachides' }]);
    expect(fiche.bracelet).toBe('FG-2291');
    expect(fiche.groupeSanguin).toBe('O+');
    expect(Object.keys(fiche).sort()).toEqual(['bracelet', 'copieLe', 'critiques', 'dateNaissance', 'ecole', 'groupeSanguin', 'id', 'nom', 'prenom', 'quartier']);
    expect(JSON.stringify(fiche)).not.toContain('Vitamine D');
    expect(JSON.stringify(fiche)).not.toContain('Cicatrice');
  });

  it('garde la copie précédente quand le serveur ne répond pas, et s’efface à la déconnexion', async () => {
    const copie = preparer({ mesEnfants: () => throwError(() => new Error('hors ligne')) }, {});
    copie.memoire.lignes = [AWA];

    await copie.synchroniser();
    expect(await copie.fiches()).toEqual([AWA]);

    await copie.vider();
    expect(await copie.fiches()).toEqual([]);
  });

  it('une déconnexion pendant la relecture empêche la copie de revenir sur l’appareil', async () => {
    const copie = preparer({ mesEnfants: () => of([ENFANT]), sante: () => of(SANTE) }, { bracelet: () => of({ numeroSerie: 'FG-2291' }) });

    const relecture = copie.synchroniser();
    await copie.vider();
    await relecture;

    expect(await copie.fiches()).toEqual([]);
  });

  it('un enfant sans bracelet ni fiche santé est copié quand même', async () => {
    const copie = preparer({ mesEnfants: () => of([ENFANT]), sante: () => throwError(() => new Error('404')) }, { bracelet: () => throwError(() => new Error('404')) });

    await copie.synchroniser();
    const [fiche] = await copie.fiches();

    expect(fiche.prenom).toBe('Awa');
    expect(fiche.bracelet).toBeNull();
    expect(fiche.critiques).toEqual([]);
  });
});
