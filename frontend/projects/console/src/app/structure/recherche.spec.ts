import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { ClientConsole, Session } from 'api';

import { Recherche, resultatsPour } from './recherche';

describe('recherche de la console', () => {
  it('reconnaît les références des seuls rôles de l’agent', () => {
    expect(resultatsPour('fg-3307', ['SAV'])[0]).toMatchObject({ libelle: 'Bracelet FG-3307', lien: '/sav/parc/FG-3307' });
    expect(resultatsPour('FG-SIG-000412', ['FDS'])[0]).toMatchObject({ lien: '/fds', parametres: { ref: 'FG-SIG-000412' } });
    expect(resultatsPour('SAV-007731', ['SAV'])[0]).toMatchObject({ lien: '/sav/muets', parametres: { ticket: 'SAV-007731' } });
    expect(resultatsPour('SUP-000012', ['SUPPORT'])[0]).toMatchObject({ lien: '/support/tickets', parametres: { reference: 'SUP-000012' } });
    expect(resultatsPour('KYC-20418', ['KYC'])[0]).toMatchObject({ referenceKyc: 'KYC-20418' });

    // Hors de son rôle, une référence n'ouvre rien.
    expect(resultatsPour('FG-3307', ['KYC'])).toEqual([]);
    expect(resultatsPour('KYC-20418', ['SUPPORT'])).toEqual([]);
    // Un signalement n'est pas pris pour un bracelet.
    expect(resultatsPour('FG-SIG-000412', ['SAV'])).toEqual([]);
  });

  it('trouve les écrans du rôle, sans tenir compte des accents', () => {
    expect(resultatsPour('securite', ['ADMIN']).map((r) => r.lien)).toEqual(['/admin/securite']);
    expect(resultatsPour('', ['SUPPORT']).map((r) => r.lien)).toEqual(['/', '/support/tickets', '/support/faq']);
    expect(resultatsPour('parc', ['SUPPORT'])).toEqual([]);
  });

  async function monter(roles: string[], client: Partial<ClientConsole> = {}) {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: Session, useValue: { roles: () => roles } }, { provide: ClientConsole, useValue: client }],
    });
    const fixture = TestBed.createComponent(Recherche);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    const dialog = page.querySelector('dialog') as HTMLDialogElement;
    dialog.showModal = function () {
      this.setAttribute('open', '');
    };
    dialog.close = function () {
      this.removeAttribute('open');
    };
    return { fixture, page, dialog };
  }

  it('s’ouvre par Ctrl K, et Entrée mène au dossier KYC dont la référence est résolue', async () => {
    const navigation = vi.fn().mockResolvedValue(true);
    const { fixture, page, dialog } = await monter(['KYC'], { dossierKycParReference: () => of({ id: 'd-1' }) });
    TestBed.inject(Router).navigate = navigation;
    expect(page.querySelector('button')!.textContent).toContain('un dossier KYC-20418…');

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'k', ctrlKey: true }));
    await fixture.whenStable();
    expect(dialog.hasAttribute('open')).toBe(true);

    const champ = page.querySelector('input') as HTMLInputElement;
    champ.value = 'kyc-20418';
    champ.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    expect(page.querySelector('[role="option"]')!.textContent).toContain('Dossier KYC-20418');
    champ.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    await fixture.whenStable();

    expect(navigation).toHaveBeenCalledWith(['/kyc/d-1'], {});
    expect(dialog.hasAttribute('open')).toBe(false);
  });

  it('dit quand la référence ne désigne aucun dossier', async () => {
    const { fixture, page } = await monter(['KYC'], {
      dossierKycParReference: () => throwError(() => ({ status: 404, error: { detail: 'Aucun dossier ne porte cette référence.' } })),
    });
    (page.querySelector('button') as HTMLButtonElement).click();
    const champ = page.querySelector('input') as HTMLInputElement;
    champ.value = 'KYC-1';
    champ.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    (page.querySelector('[role="option"]') as HTMLElement).click();
    await fixture.whenStable();

    expect(page.querySelector('[role="alert"]')).not.toBeNull();
  });
});
