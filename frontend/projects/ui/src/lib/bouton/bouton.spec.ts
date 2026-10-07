import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { FgBouton, VarianteBouton } from './bouton';

@Component({
  imports: [FgBouton],
  template: `<button
    fg-button
    [variante]="variante()"
    [disabled]="desactive()"
    [chargement]="chargement()"
    (click)="clics.set(clics() + 1)"
  >
    Valider
  </button>`,
})
class Hote {
  readonly variante = signal<VarianteBouton>('primary');
  readonly desactive = signal(false);
  readonly chargement = signal(false);
  readonly clics = signal(0);
}

describe('FgBouton', () => {
  let fixture: ComponentFixture<Hote>;
  let bouton: HTMLButtonElement;

  beforeEach(async () => {
    fixture = TestBed.createComponent(Hote);
    await fixture.whenStable();
    bouton = fixture.nativeElement.querySelector('button');
  });

  it('émet un clic et conserve son libellé accessible', async () => {
    bouton.click();
    await fixture.whenStable();

    expect(fixture.componentInstance.clics()).toBe(1);
    expect(bouton.textContent).toContain('Valider');
  });

  it('est désactivé lorsque disabled est vrai', async () => {
    fixture.componentInstance.desactive.set(true);
    await fixture.whenStable();

    expect(bouton.disabled).toBe(true);
  });

  it("ne désactive jamais la variante d'alerte : une alerte reste toujours actionnable", async () => {
    fixture.componentInstance.variante.set('alert');
    fixture.componentInstance.desactive.set(true);
    await fixture.whenStable();

    expect(bouton.disabled).toBe(false);
    bouton.click();
    expect(fixture.componentInstance.clics()).toBe(1);
  });

  it('en chargement, annonce aria-busy, affiche le spinner et bloque les clics sans perdre le focus', async () => {
    fixture.componentInstance.chargement.set(true);
    await fixture.whenStable();

    expect(bouton.getAttribute('aria-busy')).toBe('true');
    expect(bouton.querySelector('[data-fg-spinner]')).not.toBeNull();
    expect(bouton.disabled).toBe(false);

    bouton.click();
    await fixture.whenStable();
    expect(fixture.componentInstance.clics()).toBe(0);
  });
});
