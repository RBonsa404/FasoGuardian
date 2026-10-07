import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  booleanAttribute,
  computed,
  inject,
  input,
} from '@angular/core';

import { FgIcon } from '../icone/icone';

export type VarianteBouton = 'primary' | 'secondary' | 'alert' | 'danger' | 'ghost';
export type TailleBouton = 'sm' | 'md' | 'lg';

const BASE =
  'relative inline-flex items-center justify-center gap-2 rounded-md px-5 font-sans text-body font-semibold ' +
  'select-none cursor-pointer transition-[background-color,border-color,color,transform] ' +
  'duration-(--duration-instant) ease-standard active:scale-[.98] ' +
  'focus-visible:outline-2 focus-visible:outline-offset-2 ' +
  'disabled:cursor-not-allowed disabled:active:scale-100 ' +
  'aria-busy:pointer-events-none';

const VARIANTES: Record<VarianteBouton, string> = {
  primary:
    'bg-primary text-white hover:bg-primary-hover active:bg-primary-pressed focus-visible:outline-accent ' +
    'disabled:bg-surface-2 disabled:text-text-3',
  secondary:
    'border-(length:--border-width-trait) border-line-strong text-text hover:border-text-3 hover:bg-surface ' +
    'active:bg-surface-2 focus-visible:outline-accent ' +
    'disabled:border-dashed disabled:border-line-strong disabled:bg-transparent disabled:text-text-3',
  // Ambre réservé aux alertes ; jamais désactivé : une alerte reste toujours actionnable.
  alert:
    'bg-alert text-on-alert font-bold hover:bg-alert-hover active:bg-alert-pressed focus-visible:outline-text',
  danger:
    'border-(length:--border-width-trait) border-danger text-danger hover:bg-danger hover:text-on-danger ' +
    'active:bg-danger active:text-on-danger focus-visible:outline-accent ' +
    'disabled:border-dashed disabled:border-line-strong disabled:bg-transparent disabled:text-text-3',
  ghost:
    'text-accent hover:bg-accent-soft active:bg-accent-soft focus-visible:outline-accent disabled:text-text-3 ' +
    'disabled:bg-transparent',
};

const TAILLES: Record<TailleBouton, string> = {
  sm: 'h-10 min-w-11',
  md: 'h-12 min-w-12',
  lg: 'h-14 min-w-14',
};

const SUCCES = 'bg-success text-on-success focus-visible:outline-accent';

/**
 * Bouton du design system. S'applique à un `<button>` natif pour conserver sa sémantique :
 * `<button fg-button variante="primary">Valider</button>`.
 */
@Component({
  selector: 'button[fg-button]',
  imports: [FgIcon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (chargement()) {
      <span class="absolute inset-0 grid place-items-center" aria-hidden="true" data-fg-spinner>
        <span
          class="size-5 rounded-full border-(length:--border-width-spinner) border-current border-t-transparent animate-spinner"
          [class.text-accent]="spinnerAccent()"
        ></span>
      </span>
    }
    <span class="contents" [class.invisible]="chargement()">
      @if (succes()) {
        <fg-icon nom="valider" [taille]="18" />
      }
      <ng-content />
    </span>
  `,
  host: {
    '[class]': 'classes()',
    '[disabled]': 'desactive()',
    '[attr.aria-busy]': 'chargement() ? "true" : null',
    '[attr.aria-disabled]': 'chargement() ? "true" : null',
  },
})
export class FgBouton {
  readonly variante = input<VarianteBouton>('primary');
  readonly taille = input<TailleBouton>('md');
  readonly chargement = input(false, { transform: booleanAttribute });
  readonly succes = input(false, { transform: booleanAttribute });
  readonly disabled = input(false, { transform: booleanAttribute });

  protected readonly desactive = computed(() => this.disabled() && this.variante() !== 'alert');
  protected readonly spinnerAccent = computed(() => this.variante() !== 'primary' && this.variante() !== 'alert');
  protected readonly classes = computed(() =>
    [BASE, TAILLES[this.taille()], this.succes() ? SUCCES : VARIANTES[this.variante()]].join(' '),
  );

  constructor() {
    // En chargement, le bouton garde le focus (pas d'attribut disabled) mais n'émet plus de clic.
    // L'écoute en phase de capture passe avant les écouteurs posés par le gabarit parent.
    const bouton = inject<ElementRef<HTMLButtonElement>>(ElementRef).nativeElement;
    const filtrer = (evenement: Event): void => {
      if (this.chargement()) {
        evenement.preventDefault();
        evenement.stopImmediatePropagation();
      }
    };
    bouton.addEventListener('click', filtrer, { capture: true });
    inject(DestroyRef).onDestroy(() => bouton.removeEventListener('click', filtrer, { capture: true }));
  }
}
