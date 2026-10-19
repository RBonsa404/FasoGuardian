import { TestBed } from '@angular/core/testing';

import { PriseDeVue } from './prise-de-vue';

describe('prise de vue d’une pièce (écran 11b)', () => {
  const getUserMedia = vi.fn();
  const arreter = vi.fn();

  beforeEach(() => {
    getUserMedia.mockReset();
    arreter.mockReset();
    Object.defineProperty(navigator, 'mediaDevices', { value: { getUserMedia }, configurable: true });
  });

  afterEach(() => {
    Object.defineProperty(navigator, 'mediaDevices', { value: undefined, configurable: true });
  });

  async function ouvrir() {
    const fixture = TestBed.createComponent(PriseDeVue);
    fixture.componentRef.setInput('titre', 'Recto de la CNIB');
    fixture.componentRef.setInput('ouverte', true);
    await fixture.whenStable();
    await new Promise((resoudre) => setTimeout(resoudre));
    await fixture.whenStable();
    return { fixture, page: fixture.nativeElement as HTMLElement };
  }

  it('ouvre la caméra arrière, montre le cadre et le conseil, sans prétendre juger la lisibilité', async () => {
    getUserMedia.mockResolvedValue({ getTracks: () => [{ stop: arreter }] });
    const { fixture, page } = await ouvrir();

    expect(getUserMedia).toHaveBeenCalledWith(expect.objectContaining({ video: expect.objectContaining({ facingMode: { ideal: 'environment' } }), audio: false }));
    expect(page.querySelector('dialog')?.getAttribute('aria-label')).toBe('Recto de la CNIB');
    expect(page.textContent).toContain('Placez la pièce dans le cadre');
    expect(page.textContent).toContain('Le cliché est chiffré et visible uniquement par un agent KYC.');
    expect(page.textContent).not.toContain('lisible');
    expect(page.querySelector('video')).toBeTruthy();

    let fermetures = 0;
    fixture.componentInstance.fermee.subscribe(() => fermetures++);
    (page.querySelector('button[aria-label=Fermer]') as HTMLButtonElement).click();
    expect(arreter).toHaveBeenCalled();
    expect(fermetures).toBe(1);
  });

  it('caméra refusée : propose le sélecteur du téléphone et remet la photo choisie', async () => {
    getUserMedia.mockRejectedValue(new DOMException('refus', 'NotAllowedError'));
    const { fixture, page } = await ouvrir();

    expect(page.textContent).toContain("L'appareil photo n'a pas pu s'ouvrir dans l'application.");
    const photos: Blob[] = [];
    fixture.componentInstance.photo.subscribe((photo) => photos.push(photo));
    const champ = page.querySelector('input[type=file]') as HTMLInputElement;
    const fichier = new File(['recto'], 'recto.jpg', { type: 'image/jpeg' });
    Object.defineProperty(champ, 'files', { value: [fichier], configurable: true });
    champ.dispatchEvent(new Event('change'));
    expect(photos).toEqual([fichier]);
    expect((page.querySelector('button[aria-label="Prendre la photo"]') as HTMLButtonElement).disabled).toBe(true);
  });
});
