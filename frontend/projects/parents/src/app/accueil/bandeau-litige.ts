import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';

import { ClientMesLitiges, LitigeDuTuteur } from 'api';
import { FgIcon } from 'ui';

/**
 * Litige de filiation en cours sur le lien du parent avec l'enfant affiché (US-KYC-001, ADR 0019) : le tableau
 * de bord dit pourquoi les réglages sont gelés, avant tout refus. Ni le motif ni l'auteur du signalement ne sont
 * montrés ; une erreur de lecture reste silencieuse, chaque action gelée donne de toute façon la raison du refus.
 */
@Component({
  selector: 'app-bandeau-litige',
  imports: [FgIcon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (litige(); as l) {
      <section class="flex flex-col gap-1.5 rounded-lg border border-attention bg-attention-soft p-3.5" i18n-aria-label="@@litige.parent.region" aria-label="Signalement en cours">
        <strong class="text-body font-semibold text-attention-text" i18n="@@litige.parent.titre">Signalement en cours d'instruction</strong>
        <span class="text-label text-text-2">
          <ng-container i18n="@@litige.parent.gel">Un signalement sur votre lien avec {{ prenom() }} est en cours d'instruction. Ses réglages sont gelés jusqu'à la décision, dont vous serez prévenu par SMS.</ng-container>
          @if (l.geolocalisationSuspendue) {
            <ng-container i18n="@@litige.parent.position"> Sa position est suspendue pendant ce temps.</ng-container>
          }
        </span>
        <span class="inline-flex items-center gap-1.5 text-caption font-semibold text-success">
          <fg-icon nom="valider" [taille]="14" />
          <ng-container i18n="@@bandeau.toujours">Page QR et SOS toujours actifs</ng-container>
        </span>
      </section>
    }
  `,
})
export class BandeauLitige {
  readonly enfant = input.required<string>();
  readonly prenom = input.required<string>();

  private readonly litiges = signal<readonly LitigeDuTuteur[]>([]);
  protected readonly litige = computed(() => this.litiges().find((litige) => litige.enfant === this.enfant()) ?? null);

  constructor() {
    inject(ClientMesLitiges)
      .mesLitiges()
      .subscribe({ next: (litiges) => this.litiges.set(litiges), error: () => undefined });
  }
}
