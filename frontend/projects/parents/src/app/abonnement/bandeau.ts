import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Abonnement, ClientAbonnements } from 'api';
import { FgIcon } from 'ui';

import { jourCourt, montant, nomMoyen } from './libelles';

const JOUR_MS = 86_400_000;

/** Paliers des relances (US-PAR-016, US-SYS-008) : rappel à J-7, rappels à J+1 et J+8, suspension à J+15. */
type Palier = 'ECHEANCE' | 'RAPPEL_1' | 'RAPPEL_2' | 'RESTREINT';

/** Nombre de jours entiers entre aujourd'hui et une date ISO (AAAA-MM-JJ), négatif si elle est passée. */
function joursAvant(iso: string, maintenant: number): number {
  const aujourdhui = Date.UTC(new Date(maintenant).getUTCFullYear(), new Date(maintenant).getUTCMonth(), new Date(maintenant).getUTCDate());
  return Math.round((Date.parse(iso) - aujourdhui) / JOUR_MS);
}

/** Palier à afficher, d'après le statut et les dates calculées par le serveur. */
export function palierDe(abonnement: Abonnement, maintenant = Date.now()): Palier | null {
  if (abonnement.statut === 'RESTREINT') {
    return 'RESTREINT';
  }
  if (abonnement.statut === 'EN_RETARD') {
    // Le second rappel part sept jours avant la suspension (J+8 pour une suspension à J+15).
    return abonnement.restrictionLe && joursAvant(abonnement.restrictionLe, maintenant) <= 7 ? 'RAPPEL_2' : 'RAPPEL_1';
  }
  if (abonnement.statut === 'ACTIF' && abonnement.prochaineEcheance) {
    const jours = joursAvant(abonnement.prochaineEcheance, maintenant);
    return jours >= 0 && jours <= 7 ? 'ECHEANCE' : null;
  }
  return null;
}

/**
 * Rappels d'abonnement sur le tableau de bord (écrans 44 et 45). Rien ne s'affiche tant que l'échéance est
 * loin ; une erreur de lecture reste silencieuse, l'écran « Mon abonnement » la dira. Chaque palier rappelle
 * que la page QR et le SOS ne sont jamais coupés.
 */
@Component({
  selector: 'app-bandeau-abonnement',
  imports: [RouterLink, FgIcon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (abonnement(); as a) {
      @if (palier(); as p) {
        <section
          class="flex flex-col gap-1.5 rounded-lg border p-3.5"
          [class]="classes[p]"
          i18n-aria-label="@@bandeau.titre"
          aria-label="Abonnement"
        >
          <strong class="text-body font-semibold" [class]="p === 'RESTREINT' ? 'text-danger' : p === 'ECHEANCE' ? 'text-text' : 'text-attention-text'">
            @switch (p) {
              @case ('ECHEANCE') {
                <ng-container i18n="@@bandeau.echeance.titre">Votre abonnement se renouvelle le {{ jour(a.prochaineEcheance) }}</ng-container>
              }
              @case ('RAPPEL_1') {
                <ng-container i18n="@@bandeau.rappel1.titre">Paiement non reçu</ng-container>
              }
              @case ('RAPPEL_2') {
                <ng-container i18n="@@bandeau.rappel2.titre">Dernier rappel avant suspension</ng-container>
              }
              @case ('RESTREINT') {
                <ng-container i18n="@@bandeau.restreint.titre">Suivi continu suspendu</ng-container>
              }
            }
          </strong>
          <span class="text-label text-text-2">
            @switch (p) {
              @case ('ECHEANCE') {
                @if (a.renouvellementAuto && a.moyen && a.prixFcfa) {
                  <ng-container i18n="@@bandeau.echeance.auto">{{ montant(a.prixFcfa) }} FCFA seront demandés sur {{ moyen(a) }}.</ng-container>
                } @else {
                  <ng-container i18n="@@bandeau.echeance.manuel">Réglez avant cette date pour garder toutes les fonctions.</ng-container>
                }
              }
              @case ('RAPPEL_1') {
                <ng-container i18n="@@bandeau.retard">Tout fonctionne encore. Réglez avant le {{ jour(a.restrictionLe) }}.</ng-container>
              }
              @case ('RAPPEL_2') {
                <ng-container i18n="@@bandeau.rappel2">Le {{ jour(a.restrictionLe) }}, le suivi continu et l'historique étendu seront suspendus.</ng-container>
              }
              @case ('RESTREINT') {
                <ng-container i18n="@@bandeau.restreint">Position à la demande uniquement, historique limité à 24 h.</ng-container>
              }
            }
          </span>
          <span class="flex flex-wrap items-center justify-between gap-2">
            <span class="inline-flex items-center gap-1.5 text-caption font-semibold text-success">
              <fg-icon nom="valider" [taille]="14" />
              <ng-container i18n="@@bandeau.toujours">Page QR et SOS toujours actifs</ng-container>
            </span>
            @if (p !== 'ECHEANCE' || !a.renouvellementAuto) {
              <a class="text-label font-semibold text-accent underline" routerLink="/abonnement" [queryParams]="{ enfant: enfant() }" i18n="@@bandeau.regler">Régler</a>
            }
          </span>
        </section>
      }
    }
  `,
})
export class BandeauAbonnement {
  readonly enfant = input.required<string>();

  private readonly client = inject(ClientAbonnements);
  protected readonly abonnement = signal<Abonnement | null>(null);
  protected readonly palier = computed(() => {
    const abonnement = this.abonnement();
    return abonnement ? palierDe(abonnement) : null;
  });
  protected readonly montant = montant;
  protected readonly classes: Record<Palier, string> = {
    ECHEANCE: 'border-line bg-surface',
    RAPPEL_1: 'border-transparent bg-attention-soft',
    RAPPEL_2: 'border-attention bg-attention-soft',
    RESTREINT: 'border-danger bg-surface-2',
  };

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

  protected moyen(abonnement: Abonnement): string {
    return abonnement.moyen ? nomMoyen(abonnement.moyen) : '';
  }
}
