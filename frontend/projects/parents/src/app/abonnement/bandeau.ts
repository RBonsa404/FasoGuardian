import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Abonnement, ClientAbonnements } from 'api';
import { FgBanniere } from 'ui';

import { jourCourt } from './libelles';

/**
 * Rappel d'impayé sur le tableau de bord (écrans 44 et 45, US-SYS-008). Rien ne s'affiche tant que
 * l'abonnement est à jour ; une erreur de lecture reste silencieuse, l'écran « Mon abonnement » la dira.
 */
@Component({
  selector: 'app-bandeau-abonnement',
  imports: [RouterLink, FgBanniere],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (abonnement(); as a) {
      @if (a.statut === 'EN_RETARD') {
        <fg-banner ton="attention">
          <span i18n="@@bandeau.retard">Paiement non reçu. Tout fonctionne encore : réglez avant le {{ jour(a.restrictionLe) }}.</span>
          <a class="font-semibold underline" routerLink="/abonnement" [queryParams]="{ enfant: enfant() }" i18n="@@bandeau.regler">Régler</a>
        </fg-banner>
      } @else if (a.statut === 'RESTREINT') {
        <fg-banner ton="erreur">
          <span i18n="@@bandeau.restreint">Suivi continu suspendu : position à la demande, historique limité à 24 h. Page QR et SOS toujours actifs.</span>
          <a class="font-semibold underline" routerLink="/abonnement" [queryParams]="{ enfant: enfant() }" i18n="@@bandeau.regler">Régler</a>
        </fg-banner>
      }
    }
  `,
})
export class BandeauAbonnement {
  readonly enfant = input.required<string>();

  private readonly client = inject(ClientAbonnements);
  protected readonly abonnement = signal<Abonnement | null>(null);

  constructor() {
    effect(() => {
      this.abonnement.set(null);
      this.client.abonnement(this.enfant()).subscribe({
        next: (abonnement) => this.abonnement.set(abonnement),
        error: () => undefined,
      });
    });
  }

  protected jour(iso: string | null): string {
    return iso ? jourCourt(iso) : '';
  }
}
