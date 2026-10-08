import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';

import { FgCodeAppairage } from './code-appairage';

@Component({
  imports: [ReactiveFormsModule, FgCodeAppairage],
  template: `<fg-pairing-code libelle="Code d'appairage" [erreur]="erreur()" [formControl]="code" (complet)="complets.push($event)" />`,
})
class Hote {
  readonly code = new FormControl('', { nonNullable: true });
  readonly complets: string[] = [];
  readonly erreur = signal<string | null>(null);
}

describe("saisie du code d'appairage", () => {
  async function monter() {
    const fixture = TestBed.createComponent(Hote);
    await fixture.whenStable();
    const champ = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    const cases = () => Array.from(fixture.nativeElement.querySelectorAll('span[aria-hidden]') as NodeListOf<HTMLElement>).map((c) => c.textContent);
    return { fixture, hote: fixture.componentInstance, champ, cases };
  }

  it('met la saisie en majuscules, ignore le tiret et la présente en huit cases', async () => {
    const { fixture, hote, champ, cases } = await monter();

    champ.value = 'k7q4-m2x';
    champ.dispatchEvent(new Event('input'));
    await fixture.whenStable();

    expect(champ.value).toBe('K7Q4M2X');
    expect(hote.code.value).toBe('K7Q4M2X');
    expect(hote.complets).toEqual(['K7Q4M2X']);
    expect(cases()).toEqual(['K', '7', 'Q', '4', '–', 'M', '2', 'X']);
  });

  it("n'émet rien tant que le code est incomplet et borne la saisie à sept caractères", async () => {
    const { hote, champ } = await monter();

    champ.value = 'k7q4';
    champ.dispatchEvent(new Event('input'));
    expect(hote.complets).toEqual([]);

    champ.value = 'K7Q4M2XZZ';
    champ.dispatchEvent(new Event('input'));
    expect(hote.code.value).toBe('K7Q4M2X');
  });

  it('le champ porte un nom accessible et son message est relié', async () => {
    const { fixture, hote, champ } = await monter();
    expect(fixture.nativeElement.querySelector('label')?.textContent).toBe("Code d'appairage");

    hote.erreur.set("Ce code n'est pas reconnu.");
    await fixture.whenStable();

    expect(champ.getAttribute('aria-invalid')).toBe('true');
    const message = fixture.nativeElement.querySelector('[role="alert"]') as HTMLElement;
    expect(message.textContent).toBe("Ce code n'est pas reconnu.");
    expect(champ.getAttribute('aria-describedby')).toBe(message.id);
  });
});
