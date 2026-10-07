import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type FormeSquelette = 'ligne' | 'cercle' | 'carte';

const FORMES: Record<FormeSquelette, string> = {
  ligne: 'h-3 rounded-xs',
  cercle: 'size-11 rounded-full',
  carte: 'h-24 rounded-banner',
};

/** Emplacement de contenu en cours de chargement (shimmer 1,4 s, figé si mouvement réduit). */
@Component({
  selector: 'fg-skeleton',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '',
  host: { 'aria-hidden': 'true', '[class]': 'classes()' },
})
export class FgSquelette {
  readonly forme = input<FormeSquelette>('ligne');

  protected readonly classes = computed(
    () =>
      'block animate-shimmer bg-[linear-gradient(90deg,var(--color-surface-2)_0,var(--color-line-strong)_40px,var(--color-surface-2)_80px)] ' +
      `bg-size-[480px_100%] ${FORMES[this.forme()]}`,
  );
}
