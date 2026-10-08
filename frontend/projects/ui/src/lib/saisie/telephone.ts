import { ChangeDetectionStrategy, Component, computed, forwardRef, input, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

import { FgIcon } from '../icone/icone';

let suite = 0;

/**
 * Saisie d'un numéro mobile burkinabè : préfixe +226 fixe, masque « XX XX XX XX ».
 * La valeur du contrôle est la suite des 8 chiffres, sans espace.
 */
@Component({
  selector: 'fg-phone-input',
  imports: [FgIcon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => FgTelephone), multi: true }],
  template: `
    <label class="text-label font-semibold" [class.text-text-3]="desactive()" [attr.for]="id">{{ libelle() }}</label>
    <div [class]="cadre()">
      <span class="text-text-2" aria-hidden="true">+226</span>
      <input
        class="min-w-0 flex-1 bg-transparent tabular-nums outline-none placeholder:text-text-3 disabled:text-text-3"
        type="tel"
        inputmode="numeric"
        autocomplete="tel-national"
        placeholder="70 12 34 56"
        maxlength="11"
        [id]="id"
        [value]="affichage()"
        [disabled]="desactive()"
        [attr.aria-invalid]="erreur() ? 'true' : null"
        [attr.aria-describedby]="erreur() || aide() ? id + '-message' : null"
        (input)="saisir($event)"
        (blur)="toucher()"
      />
    </div>
    @if (erreur(); as message) {
      <span class="flex items-center gap-1.5 text-caption font-medium text-danger" [id]="id + '-message'">
        <fg-icon nom="info" [taille]="14" />{{ message }}
      </span>
    } @else if (aide(); as message) {
      <span class="text-caption text-text-3" [id]="id + '-message'">{{ message }}</span>
    }
  `,
  host: { class: 'flex flex-col gap-1.5' },
})
export class FgTelephone implements ControlValueAccessor {
  readonly libelle = input.required<string>();
  readonly aide = input<string>();
  readonly erreur = input<string | null>();

  protected readonly id = `fg-telephone-${++suite}`;
  protected readonly chiffres = signal('');
  protected readonly desactive = signal(false);
  protected readonly affichage = computed(() => this.chiffres().replace(/(\d{2})(?=\d)/g, '$1 '));
  protected readonly cadre = computed(() => {
    const base = 'flex h-13 items-center gap-2.5 rounded-md bg-surface px-3.5 text-saisie font-medium ';
    if (this.desactive()) {
      return base + 'border-(length:--border-width-trait) border-dashed border-line-strong bg-surface-2 text-text-3';
    }
    if (this.erreur()) {
      return base + 'border-2 border-danger';
    }
    return (
      base +
      'border-(length:--border-width-trait) border-line-strong ' +
      'focus-within:border-2 focus-within:border-accent focus-within:ring-4 focus-within:ring-accent-soft'
    );
  });

  private notifier: (valeur: string) => void = () => {};
  protected toucher: () => void = () => {};

  protected saisir(evenement: Event): void {
    const champ = evenement.target as HTMLInputElement;
    const chiffres = champ.value.replace(/\D/g, '').slice(0, 8);
    this.chiffres.set(chiffres);
    champ.value = this.affichage();
    this.notifier(chiffres);
  }

  writeValue(valeur: string | null): void {
    this.chiffres.set((valeur ?? '').replace(/\D/g, '').slice(0, 8));
  }

  registerOnChange(fonction: (valeur: string) => void): void {
    this.notifier = fonction;
  }

  registerOnTouched(fonction: () => void): void {
    this.toucher = fonction;
  }

  setDisabledState(desactive: boolean): void {
    this.desactive.set(desactive);
  }
}
