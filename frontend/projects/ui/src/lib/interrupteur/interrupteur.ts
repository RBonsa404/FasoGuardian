import { ChangeDetectionStrategy, Component, computed, forwardRef, input, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

/**
 * Interrupteur du design system (52 × 32 px), exposé comme role="switch". Le libellé projeté décrit
 * le réglage ; l'état n'est jamais porté par la seule couleur (position de la pastille).
 */
@Component({
  selector: 'fg-switch',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => FgInterrupteur), multi: true }],
  template: `
    <span class="flex min-w-0 flex-1 flex-col gap-0.5"><ng-content /></span>
    <button
      type="button"
      role="switch"
      class="relative h-8 w-13 flex-none rounded-full transition-colors duration-(--duration-fast) ease-standard focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent disabled:cursor-not-allowed disabled:opacity-60"
      [class]="actif() ? 'bg-accent' : 'border border-line-strong bg-surface-2'"
      [attr.aria-checked]="actif()"
      [attr.aria-label]="libelle()"
      [disabled]="desactive() || verrouille()"
      (click)="basculer()"
    >
      <span [class]="pastille()"></span>
    </button>
  `,
  host: { class: 'flex min-h-11 items-center justify-between gap-3' },
})
export class FgInterrupteur implements ControlValueAccessor {
  /** Nom accessible du réglage. */
  readonly libelle = input.required<string>();
  /** Réglage imposé (consentement obligatoire) : l'interrupteur reste lisible mais non modifiable. */
  readonly verrouille = input(false);

  protected readonly actif = signal(false);
  protected readonly desactive = signal(false);
  protected readonly pastille = computed(
    () =>
      'absolute top-0.75 size-6.5 rounded-full shadow-e1 transition-[left,background-color] duration-(--duration-fast) ease-standard ' +
      (this.actif() ? 'left-5.75 bg-white' : 'left-0.75 bg-text-3'),
  );

  private notifier: (valeur: boolean) => void = () => {};
  private toucher: () => void = () => {};

  protected basculer(): void {
    this.actif.update((valeur) => !valeur);
    this.notifier(this.actif());
    this.toucher();
  }

  writeValue(valeur: boolean | null): void {
    this.actif.set(!!valeur);
  }

  registerOnChange(fonction: (valeur: boolean) => void): void {
    this.notifier = fonction;
  }

  registerOnTouched(fonction: () => void): void {
    this.toucher = fonction;
  }

  setDisabledState(desactive: boolean): void {
    this.desactive.set(desactive);
  }
}
