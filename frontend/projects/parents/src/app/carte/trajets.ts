import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ClientZones, PositionConnue, Trajet } from 'api';
import { FgBanniere, FgIcon, FgSquelette } from 'ui';

import { erreurLisible } from '../commun/erreurs';
import { heure } from '../commun/temps';
import { distance } from '../zones/libelles';
import { Carte } from './fond';

const JOURS_PROPOSES = 7;

interface Jour {
  readonly iso: string;
  readonly semaine: string;
  readonly numero: string;
  readonly complet: string;
}

/**
 * Historique des trajets (écran 19, US-PAR-008) : le tracé d'une journée parmi les sept dernières, avec ses
 * heures de début et de fin et la distance parcourue. La durée de conservation est rappelée.
 */
@Component({
  selector: 'app-trajets',
  imports: [RouterLink, Carte, FgBanniere, FgIcon, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex flex-col gap-4 px-5 pt-6">
      <a class="-ml-2.5 grid size-11 place-items-center rounded-md focus-visible:outline-2 focus-visible:outline-accent" [routerLink]="['/enfants', id()]" i18n-aria-label="@@etape.retour" aria-label="Retour">
        <fg-icon nom="retour" [taille]="22" />
      </a>
      <h1 class="m-0 text-titre-ecran font-bold tracking-tight" i18n="@@trajets.titre">Trajets</h1>
      <div class="flex gap-1.5" role="group" i18n-aria-label="@@trajets.jours" aria-label="Jour affiché">
        @for (j of jours; track j.iso) {
          <button type="button" class="flex h-14 flex-1 flex-col items-center justify-center rounded-md focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent" [class]="j.iso === jour() ? 'border-2 border-accent bg-accent-soft' : 'border border-line-strong text-text-2'" [attr.aria-pressed]="j.iso === jour()" [attr.aria-label]="j.complet" (click)="jour.set(j.iso)">
            <span class="text-caption font-medium" aria-hidden="true">{{ j.semaine }}</span>
            <span class="text-body font-semibold tabular-nums" aria-hidden="true">{{ j.numero }}</span>
          </button>
        }
      </div>
    </div>

    <app-carte class="mt-4 h-80 flex-none lg:col-start-2 lg:row-span-2 lg:row-start-1 lg:mt-0 lg:h-full" i18n-libelle="@@trajets.carte" libelle="Carte du trajet de la journée" [trace]="trajet()?.points ?? []" />

    <div class="flex flex-1 flex-col gap-4 px-5 pt-4 pb-7" aria-live="polite">
      @if (trajet(); as t) {
        @if (bilan(); as b) {
          <dl class="m-0 grid grid-cols-3 gap-2">
            <div class="flex flex-col gap-0.5 rounded-banner border border-line bg-surface p-3">
              <dt class="text-caption font-medium text-text-3" i18n="@@trajets.debut">Premier point</dt>
              <dd class="m-0 text-saisie font-semibold tabular-nums">{{ b.debut }}</dd>
            </div>
            <div class="flex flex-col gap-0.5 rounded-banner border border-line bg-surface p-3">
              <dt class="text-caption font-medium text-text-3" i18n="@@trajets.fin">Dernier point</dt>
              <dd class="m-0 text-saisie font-semibold tabular-nums">{{ b.fin }}</dd>
            </div>
            <div class="flex flex-col gap-0.5 rounded-banner border border-line bg-surface p-3">
              <dt class="text-caption font-medium text-text-3" i18n="@@trajets.distance">Distance</dt>
              <dd class="m-0 text-saisie font-semibold tabular-nums">{{ b.distance }}</dd>
            </div>
          </dl>
          <p class="m-0 text-label text-text-2" i18n="@@trajets.points">{{ t.points.length }} position(s) enregistrée(s) ce jour-là.</p>
        } @else {
          <p class="m-0 rounded-lg border border-line bg-surface p-5 text-body text-text-2" i18n="@@trajets.vide">Aucune position enregistrée ce jour-là.</p>
        }
        <p class="m-0 mt-auto text-caption text-text-3" i18n="@@trajets.conservation">Les trajets sont conservés {{ t.joursConserves }} jours, puis effacés.</p>
      } @else if (erreur(); as message) {
        <fg-banner ton="erreur">{{ message }}</fg-banner>
      } @else {
        <fg-skeleton forme="carte" />
      }
    </div>
  `,
  // Grand écran : jours et bilan à gauche, carte du trajet sur toute la hauteur à droite.
  host: { class: 'mx-auto flex min-h-dvh w-full max-w-md flex-col lg:mx-0 lg:grid lg:h-dvh lg:max-w-none lg:grid-cols-[26rem_minmax(0,1fr)] lg:grid-rows-[auto_1fr]' },
})
export class Trajets {
  readonly id = input.required<string>();

  private readonly client = inject(ClientZones);

  protected readonly jours = derniersJours(JOURS_PROPOSES);
  protected readonly jour = signal(this.jours[this.jours.length - 1].iso);
  protected readonly trajet = signal<Trajet | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly bilan = computed(() => {
    const points = this.trajet()?.points ?? [];
    if (points.length === 0) {
      return null;
    }
    return { debut: heure(points[0].mesureeLe), fin: heure(points[points.length - 1].mesureeLe), distance: distance(Math.round(longueurM(points))) };
  });

  constructor() {
    effect(() => this.charger(this.id(), this.jour()));
  }

  private charger(id: string, jour: string): void {
    this.trajet.set(null);
    this.erreur.set(null);
    this.client.trajet(id, jour).subscribe({
      next: (trajet) => this.trajet.set(trajet),
      error: (cause: unknown) => this.erreur.set(erreurLisible(cause).message),
    });
  }
}

/** Les derniers jours, du plus ancien à aujourd'hui, à la date locale de l'appareil. */
function derniersJours(nombre: number): Jour[] {
  const aujourdhui = new Date();
  return Array.from({ length: nombre }, (_, index) => {
    const date = new Date(aujourdhui.getFullYear(), aujourdhui.getMonth(), aujourdhui.getDate() - (nombre - 1 - index));
    const deux = (valeur: number) => String(valeur).padStart(2, '0');
    return {
      iso: `${date.getFullYear()}-${deux(date.getMonth() + 1)}-${deux(date.getDate())}`,
      semaine: date.toLocaleDateString('fr', { weekday: 'short' }),
      numero: String(date.getDate()),
      complet: date.toLocaleDateString('fr', { weekday: 'long', day: 'numeric', month: 'long' }),
    };
  });
}

/** Longueur du tracé en mètres (somme des distances orthodromiques entre points successifs). */
export function longueurM(points: readonly PositionConnue[]): number {
  const rayonTerre = 6_371_008.8;
  const radians = (degres: number) => (degres * Math.PI) / 180;
  let total = 0;
  for (let i = 1; i < points.length; i++) {
    const a = points[i - 1];
    const b = points[i];
    const dPhi = radians(b.latitude - a.latitude);
    const dLambda = radians(b.longitude - a.longitude);
    const h = Math.sin(dPhi / 2) ** 2 + Math.cos(radians(a.latitude)) * Math.cos(radians(b.latitude)) * Math.sin(dLambda / 2) ** 2;
    total += 2 * rayonTerre * Math.asin(Math.min(1, Math.sqrt(h)));
  }
  return total;
}
