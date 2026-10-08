import { TestBed } from '@angular/core/testing';

import { App } from './app';

describe('App (gabarit de la page publique QR)', () => {
  it("porte les quatre états et n'affiche jamais de nom, de photo ni de position", async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    expect(Array.from(page.querySelectorAll('fg-etat')).map((etat) => etat.getAttribute('nom'))).toEqual([
      'trouve',
      'prevenu',
      'inconnu',
      'desactive',
    ]);
    expect(page.innerHTML).not.toMatch(/\[\[(prenom|nom|photo|position|latitude|longitude)\]\]/);
    expect(page.querySelector('img')).toBeNull();
    expect(page.querySelector('form')?.getAttribute('method')).toBe('post');
  });
});
