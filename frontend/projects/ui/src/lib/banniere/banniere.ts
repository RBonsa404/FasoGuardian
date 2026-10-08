import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { FgIcon } from '../icone/icone';
import { NomIcone } from '../icone/icones';

export type TonBanniere = 'info' | 'hors-ligne' | 'attention' | 'erreur' | 'succes';

const TONS: Record<TonBanniere, { classes: string; icone: NomIcone }> = {
  info: { classes: 'bg-surface-2 text-text', icone: 'info' },
  'hors-ligne': { classes: 'bg-surface-2 text-text', icone: 'hors-ligne' },
  // Violet « attention » : avertissements non critiques (batterie, muet, impayé). Jamais d'ambre ici.
  attention: { classes: 'bg-attention-soft text-attention-text', icone: 'info' },
  erreur: { classes: 'bg-surface-2 text-danger', icone: 'info' },
  succes: { classes: 'bg-success-soft text-success', icone: 'valider' },
};

/** Bannière d'information non bloquante, annoncée aux lecteurs d'écran sans voler le focus. */
@Component({
  selector: 'fg-banner',
  imports: [FgIcon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fg-icon [nom]="icone() ?? ton().icone" [taille]="18" />
    <span class="min-w-0"><ng-content /></span>
  `,
  host: { role: 'status', '[class]': 'classes()' },
})
export class FgBanniere {
  readonly tonalite = input<TonBanniere>('info', { alias: 'ton' });
  readonly icone = input<NomIcone>();

  protected readonly ton = computed(() => TONS[this.tonalite()]);
  protected readonly classes = computed(
    () => `flex items-start gap-2.5 rounded-banner px-3.5 py-3 text-label font-medium ${this.ton().classes}`,
  );
}
