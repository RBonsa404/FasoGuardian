import { ChangeDetectionStrategy, Component, computed, forwardRef, input, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

import { FgIcon } from '../icone/icone';

let suite = 0;

/** Champ de saisie du design system : libellé, aide ou message d'erreur reliés au champ. */
@Component({
  selector: 'fg-input',
  imports: [FgIcon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => FgChamp), multi: true }],
  template: `
    <label class="text-label font-semibold" [class.text-text-3]="desactive()" [attr.for]="id">{{ libelle() }}</label>
    <div [class]="cadre()">
      <input
        class="min-w-0 flex-1 bg-transparent outline-none placeholder:text-text-3 disabled:text-text-3"
        [id]="id"
        [type]="type()"
        [value]="valeur()"
        [disabled]="desactive()"
        [attr.autocomplete]="autocomplete()"
        [attr.maxlength]="longueurMax()"
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
      <span class="text-caption" [class]="aideValide() ? 'text-success' : 'text-text-3'" [id]="id + '-message'">{{
        message
      }}</span>
    }
  `,
  host: { class: 'flex flex-col gap-1.5' },
})
export class FgChamp implements ControlValueAccessor {
  readonly libelle = input.required<string>();
  readonly type = input<'text' | 'password' | 'email'>('text');
  readonly autocomplete = input<string>();
  readonly longueurMax = input<number>();
  readonly aide = input<string>();
  /** Affiche l'aide en couleur de succès (par exemple « Robuste »), toujours accompagnée de son texte. */
  readonly aideValide = input(false);
  readonly erreur = input<string | null>();

  protected readonly id = `fg-champ-${++suite}`;
  protected readonly valeur = signal('');
  protected readonly desactive = signal(false);
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
    const valeur = (evenement.target as HTMLInputElement).value;
    this.valeur.set(valeur);
    this.notifier(valeur);
  }

  writeValue(valeur: string | null): void {
    this.valeur.set(valeur ?? '');
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
