import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Silhouette du bracelet portant son numéro gravé (écrans 36 et 37). Le halo bleu signale une connexion
 * en cours ; il est coupé par prefers-reduced-motion (jeton --animate-live-pulse).
 */
@Component({
  selector: 'app-visuel-bracelet',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (halo()) {
      <span class="absolute inset-5 animate-live-pulse rounded-full bg-accent opacity-30" aria-hidden="true"></span>
    }
    <span
      class="relative grid place-items-center bg-linear-160 from-confiance-600 to-confiance-700 shadow-e2"
      [class]="grand() ? 'h-29 w-37.5 rounded-2xl' : 'h-21.5 w-27.5 rounded-xl'"
    >
      <span class="font-mono text-caption font-semibold text-confiance-200">{{ numero() }}</span>
    </span>
  `,
  host: { class: 'relative grid place-items-center', '[class]': "grand() ? 'size-50' : 'size-45'" },
})
export class VisuelBracelet {
  /** Numéro gravé (FG-2291), ou un repère tant qu'il n'est pas connu. */
  readonly numero = input.required<string>();
  readonly halo = input(false);
  readonly grand = input(false);
}
