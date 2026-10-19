import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { ClientAuthentification, ClientProfil } from 'api';

import { SecondFacteur } from './second-facteur';

function lire(element: Element): string {
  return (element.textContent ?? '').replace(/\s+/g, ' ');
}

describe('feuille de second facteur (écran 13)', () => {
  async function ouvrir() {
    const demandes: string[] = [];
    TestBed.configureTestingModule({
      providers: [
        { provide: ClientProfil, useValue: { demanderSecondFacteur: (action: string) => (demandes.push(action), of(undefined)) } },
        { provide: ClientAuthentification, useValue: { moi: () => of({ telephoneMasque: '+226 70 •• •• 56' }) } },
      ],
    });
    const fixture = TestBed.createComponent(SecondFacteur);
    fixture.componentRef.setInput('action', 'AUTORISER_RETRAIT');
    fixture.componentRef.setInput('explication', 'Pour autoriser un retrait de 1 h.');
    fixture.componentRef.setInput('ouverte', true);
    await fixture.whenStable();
    return { fixture, demandes, page: fixture.nativeElement as HTMLElement };
  }

  it('envoie le code à l’ouverture et dit où il est parti, avec les essais permis', async () => {
    const { page, demandes } = await ouvrir();

    expect(demandes).toEqual(['AUTORISER_RETRAIT']);
    expect(lire(page)).toContain('Confirmez avec le code SMS');
    expect(lire(page)).toContain('Pour autoriser un retrait de 1 h. Code envoyé au +226 70 •• •• 56.');
    expect(lire(page)).toContain('Renvoyer le code dans 1:00');
    expect(lire(page)).toContain('· 3 essais');
  });

  it('« Confirmer » ne s’active qu’avec six chiffres, et remet le code saisi', async () => {
    const { fixture, page } = await ouvrir();
    const recus: string[] = [];
    fixture.componentInstance.saisi.subscribe((code) => recus.push(code));
    const confirmer = [...page.querySelectorAll('button')].find((b) => lire(b).includes('Confirmer')) as HTMLButtonElement;
    const saisie = page.querySelector('fg-otp input') as HTMLInputElement;
    const taper = async (code: string) => {
      saisie.value = code;
      saisie.dispatchEvent(new Event('input'));
      await fixture.whenStable();
    };

    expect(confirmer.disabled).toBe(true);
    await taper('48190');
    expect(confirmer.disabled).toBe(true);
    await taper('481902');
    expect(confirmer.disabled).toBe(false);
    recus.length = 0;
    confirmer.click();
    expect(recus).toEqual(['481902']);
  });
});
