import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { FgIcon } from '../icone/icone';
import { NomIcone } from '../icone/icones';

export type TonBadge = 'accent' | 'neutre' | 'attention' | 'succes' | 'alerte';

const TONS: Record<TonBadge, string> = {
  accent: 'bg-accent-soft text-accent font-semibold',
  neutre: 'bg-surface-2 text-text-2 font-semibold',
  attention: 'bg-attention-soft text-attention font-semibold',
  succes: 'bg-success-soft text-success font-semibold',
  // Ambre réservé aux alertes (SOS, retrait non autorisé, sortie de zone).
  alerte: 'bg-alert-soft text-alert-ink font-bold',
};

/** Badge d'état (canal 4G / 2G / SMS, précision, statut). Le texte est toujours présent. */
@Component({
  selector: 'fg-badge',
  imports: [FgIcon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (icone(); as nom) {
      <fg-icon [nom]="nom" [taille]="14" />
    }
    <ng-content />
  `,
  host: { '[class]': 'classes()' },
})
export class FgBadge {
  readonly ton = input<TonBadge>('neutre');
  readonly icone = input<NomIcone>();

  protected readonly classes = computed(
    () => `inline-flex h-7 items-center gap-1.5 rounded-full px-2.5 text-caption whitespace-nowrap ${TONS[this.ton()]}`,
  );
}
