import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Alerte, ClientAlertes } from 'api';
import { FgBanniere, FgIcon, FgSquelette } from 'ui';

import { erreurLisible } from '../commun/erreurs';
import { actionLisible, etiquetteAlerte, titreCourt } from './libelles';

/**
 * Journal des alertes d'un enfant (écran 20, US-PAR-008) : déclenchement, prise en charge, escalade et levée
 * de chaque alerte, horodatés. Rien n'y est modifiable.
 */
@Component({
  selector: 'app-journal-alertes',
  imports: [DatePipe, RouterLink, FgBanniere, FgIcon, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="-ml-2.5 grid size-11 place-items-center rounded-md focus-visible:outline-2 focus-visible:outline-accent" [routerLink]="['/enfants', id()]" i18n-aria-label="@@etape.retour" aria-label="Retour">
      <fg-icon nom="retour" [taille]="22" />
    </a>
    <h1 class="m-0 text-titre-ecran font-bold tracking-tight" i18n="@@journal.titre">Journal des alertes</h1>

    @if (alertes(); as liste) {
      @for (alerte of liste; track alerte.id) {
        <article class="flex gap-3 rounded-lg border border-line bg-surface p-4">
          <span class="grid h-7 min-w-12 flex-none place-items-center rounded-xs px-1.5 text-caption font-bold" [class]="etiquette(alerte)" aria-hidden="true">{{ sigle(alerte) }}</span>
          <div class="flex min-w-0 flex-1 flex-col gap-1.5">
            <h2 class="m-0 text-body font-semibold">{{ titre(alerte) }}</h2>
            <ol class="m-0 flex list-none flex-col gap-1 p-0">
              @for (ligne of alerte.actions; track $index) {
                <li class="flex gap-2 text-label text-text-2">
                  <span class="flex-none tabular-nums">{{ ligne.effectueeLe | date: 'd MMM HH:mm' }}</span>
                  <span class="text-text">{{ action(ligne) }}</span>
                </li>
              }
            </ol>
          </div>
        </article>
      } @empty {
        <p class="m-0 rounded-lg border border-line bg-surface p-5 text-body text-text-2" i18n="@@journal.vide">Aucune alerte n'a été enregistrée pour cet enfant.</p>
      }
      <p class="m-0 mt-auto flex items-center gap-2 text-caption text-text-3">
        <fg-icon nom="cadenas" [taille]="16" /><ng-container i18n="@@alerte.scelle">Entrées scellées : aucune ne peut être modifiée ni supprimée.</ng-container>
      </p>
    } @else if (erreur(); as message) {
      <fg-banner ton="erreur">{{ message }}</fg-banner>
    } @else {
      <fg-skeleton forme="carte" />
      <fg-skeleton forme="carte" />
    }
  `,
  host: { class: 'mx-auto flex min-h-dvh w-full max-w-md flex-col gap-4 px-5 pt-6 pb-7 lg:mx-0 lg:max-w-2xl lg:px-10 lg:pt-10' },
})
export class JournalAlertes {
  readonly id = input.required<string>();

  private readonly client = inject(ClientAlertes);

  protected readonly titre = titreCourt;
  protected readonly sigle = etiquetteAlerte;
  protected readonly action = actionLisible;
  protected readonly alertes = signal<Alerte[] | null>(null);
  protected readonly erreur = signal<string | null>(null);

  constructor() {
    effect(() => this.charger(this.id()));
  }

  protected etiquette(alerte: Alerte): string {
    return alerte.gravite === 'CRITIQUE' ? 'bg-alert text-on-alert' : 'bg-alert-soft text-alert-ink';
  }

  private charger(id: string): void {
    this.alertes.set(null);
    this.erreur.set(null);
    this.client.journal(id).subscribe({
      next: (alertes) => this.alertes.set(alertes),
      error: (cause: unknown) => this.erreur.set(erreurLisible(cause).message),
    });
  }
}
