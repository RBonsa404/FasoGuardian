import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';

import { FgCode } from './code';
import { FgTelephone } from './telephone';

@Component({
  imports: [ReactiveFormsModule, FgTelephone, FgCode],
  template: `
    <fg-phone-input libelle="Numéro mobile" aide="Les alertes arriveront sur ce numéro." [formControl]="telephone" />
    <fg-otp libelle="Code à 6 chiffres" [formControl]="code" (complet)="complets.push($event)" />
  `,
})
class Hote {
  readonly telephone = new FormControl('', { nonNullable: true });
  readonly code = new FormControl('', { nonNullable: true });
  readonly complets: string[] = [];
}

function saisir(champ: HTMLInputElement, texte: string): void {
  champ.value = texte;
  champ.dispatchEvent(new Event('input'));
}

describe('saisies du design system', () => {
  async function monter() {
    const fixture = TestBed.createComponent(Hote);
    await fixture.whenStable();
    const [telephone, code] = Array.from(fixture.nativeElement.querySelectorAll('input')) as HTMLInputElement[];
    return { fixture, hote: fixture.componentInstance, telephone, code };
  }

  it('le téléphone est masqué par paires et le contrôle reçoit les 8 chiffres', async () => {
    const { hote, telephone } = await monter();

    saisir(telephone, '70123456');

    expect(telephone.value).toBe('70 12 34 56');
    expect(hote.telephone.value).toBe('70123456');
  });

  it('le téléphone ignore les caractères non numériques et les chiffres en trop', async () => {
    const { hote, telephone } = await monter();

    saisir(telephone, '+226 70-12-34-56-99');

    expect(hote.telephone.value).toBe('22670123');
  });

  it('le téléphone est relié à son libellé et à son aide', async () => {
    const { fixture, telephone } = await monter();
    const libelle = fixture.nativeElement.querySelector('label') as HTMLLabelElement;

    expect(libelle.htmlFor).toBe(telephone.id);
    expect(document.getElementById(telephone.getAttribute('aria-describedby')!)?.textContent).toContain('alertes');
  });

  it('le code accepte un collage, se limite à six chiffres et signale sa complétude', async () => {
    const { fixture, hote, code } = await monter();

    saisir(code, '48 19 02 7');
    await fixture.whenStable();

    expect(hote.code.value).toBe('481902');
    expect(hote.complets).toEqual(['481902']);
    expect(code.getAttribute('autocomplete')).toBe('one-time-code');
    const cases = Array.from(fixture.nativeElement.querySelectorAll('fg-otp span[aria-hidden]')) as HTMLElement[];
    expect(cases.map((element) => element.textContent)).toEqual(['4', '8', '1', '9', '0', '2']);
  });
});
