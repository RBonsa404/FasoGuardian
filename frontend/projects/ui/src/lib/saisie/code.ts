import { ChangeDetectionStrategy, Component, computed, forwardRef, input, output, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

let suite = 0;

/**
 * Saisie du code à six chiffres reçu par SMS. Un seul champ réel, superposé à six cases : le remplissage
 * automatique du code (autocomplete="one-time-code"), le collage et les flèches fonctionnent nativement.
 */
@Component({
  selector: 'fg-otp',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => FgCode), multi: true }],
  template: `
    <label class="text-label font-semibold" [attr.for]="id">{{ libelle() }}</label>
    <div class="relative flex gap-2">
      @for (case of cases(); track $index) {
        <span [class]="case.classes" aria-hidden="true">{{ case.chiffre }}</span>
      }
      <input
        class="absolute inset-0 w-full cursor-text opacity-0"
        type="text"
        inputmode="numeric"
        autocomplete="one-time-code"
        pattern="[0-9]*"
        maxlength="6"
        [id]="id"
        [value]="valeur()"
        [disabled]="desactive()"
        [attr.aria-invalid]="erreur() ? 'true' : null"
        [attr.aria-describedby]="erreur() ? id + '-message' : null"
        (input)="saisir($event)"
        (focus)="focalise.set(true)"
        (blur)="focalise.set(false); toucher()"
      />
    </div>
    @if (erreur(); as message) {
      <span class="text-caption font-medium text-danger" role="alert" [id]="id + '-message'">{{ message }}</span>
    }
  `,
  host: { class: 'flex flex-col gap-2.5' },
})
export class FgCode implements ControlValueAccessor {
  readonly libelle = input.required<string>();
  readonly erreur = input<string | null>();
  /** Émis dès que les six chiffres sont saisis. */
  readonly complet = output<string>();

  protected readonly id = `fg-code-${++suite}`;
  protected readonly valeur = signal('');
  protected readonly desactive = signal(false);
  protected readonly focalise = signal(false);
  protected readonly cases = computed(() => {
    const valeur = this.valeur();
    const active = this.focalise() ? Math.min(valeur.length, 5) : -1;
    return Array.from({ length: 6 }, (_, index) => ({
      chiffre: valeur[index] ?? '',
      classes:
        'grid h-14 w-11.5 place-items-center rounded-md bg-surface font-display text-h2 font-semibold ' +
        (this.erreur()
          ? 'border-2 border-danger'
          : index === active
            ? 'border-2 border-accent ring-4 ring-accent-soft'
            : 'border-(length:--border-width-trait) border-line-strong'),
    }));
  });

  private notifier: (valeur: string) => void = () => {};
  protected toucher: () => void = () => {};

  protected saisir(evenement: Event): void {
    const champ = evenement.target as HTMLInputElement;
    const chiffres = champ.value.replace(/\D/g, '').slice(0, 6);
    champ.value = chiffres;
    this.valeur.set(chiffres);
    this.notifier(chiffres);
    if (chiffres.length === 6) {
      this.complet.emit(chiffres);
    }
  }

  writeValue(valeur: string | null): void {
    this.valeur.set((valeur ?? '').replace(/\D/g, '').slice(0, 6));
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
