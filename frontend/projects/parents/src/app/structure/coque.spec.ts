import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { ClientAuthentification, Compte } from 'api';

import { CopieLocale } from '../commun/copie-locale';
import { Coque } from './coque';

function compte(statut: Compte['statut']): Compte {
  return { id: 'c-1', statut, telephoneMasque: '+226 70 •• •• 56', identifiant: null, roles: ['PARENT'] };
}

async function ouvrir(statut: Compte['statut']) {
  const etat = { deconnexions: 0, effacements: 0 };
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      {
        provide: ClientAuthentification,
        useValue: {
          moi: () => of(compte(statut)),
          deconnecter: () => {
            etat.deconnexions++;
            return of(undefined);
          },
        },
      },
      { provide: CopieLocale, useValue: { vider: () => Promise.resolve(void etat.effacements++) } },
    ],
  });
  const fixture = TestBed.createComponent(Coque);
  await fixture.whenStable();
  return { fixture, etat };
}

function liens(page: HTMLElement): string[] {
  return [...page.querySelectorAll('aside nav a')].map((a) => (a.textContent ?? '').trim());
}

function onglets(page: HTMLElement): string[] {
  return [...page.querySelectorAll('nav[aria-label="Onglets"] a')].map((a) => (a.textContent ?? '').trim());
}

describe('coque de l’espace parent', () => {
  it('propose toutes les rubriques à un compte actif', async () => {
    const { fixture } = await ouvrir('ACTIF');
    const page = fixture.nativeElement as HTMLElement;

    expect(liens(page)).toEqual(['Tableau de bord', 'Alertes', 'Mes enfants', 'Mon abonnement', 'Aide et support', 'Paramètres du compte']);
    expect(page.querySelector('aside')?.textContent).toContain('+226 70 •• •• 56');
    // La barre n'apparaît qu'à partir d'un grand écran : sur un téléphone, seul l'écran est montré.
    expect(page.querySelector('aside')?.classList.contains('hidden')).toBe(true);
    expect(page.querySelector('aside')?.classList.contains('lg:flex')).toBe(true);
    expect(page.querySelector('router-outlet')).not.toBeNull();
    // Sur téléphone, l'accueil porte la barre d'onglets de l'écran 16 ; elle disparaît sur grand écran.
    expect(onglets(page)).toEqual(['Accueil', 'Carte', 'Alertes', 'Réglages']);
    expect(page.querySelector('nav[aria-label="Onglets"]')?.classList.contains('lg:hidden')).toBe(true);
  });

  it('ne propose pas les rubriques de suivi tant que le compte est en instruction', async () => {
    const { fixture } = await ouvrir('EN_INSTRUCTION');

    expect(liens(fixture.nativeElement as HTMLElement)).toEqual(['Tableau de bord', 'Aide et support', 'Paramètres du compte']);
    expect(onglets(fixture.nativeElement as HTMLElement)).toEqual(['Accueil', 'Réglages']);
  });

  it('efface la copie locale et ferme la session à la déconnexion', async () => {
    const { fixture, etat } = await ouvrir('ACTIF');
    const navigation = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    (fixture.nativeElement as HTMLElement).querySelector('aside button')!.dispatchEvent(new Event('click'));
    await fixture.whenStable();

    expect(etat).toEqual({ deconnexions: 1, effacements: 1 });
    expect(navigation).toHaveBeenCalledWith(['/connexion']);
  });
});
