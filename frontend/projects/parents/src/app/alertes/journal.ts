import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Alerte, ClientAlertes, TypeAlerte } from 'api';
import { FgBanniere, FgIcon, FgSquelette } from 'ui';

import { erreurLisible } from '../commun/erreurs';
import { actionLisible, etiquetteAlerte, titreCourt } from './libelles';

type Filtre = 'TOUTES' | 'SOS' | 'RETRAIT' | 'SORTIE_ZONE' | 'BATTERIE_CRITIQUE';

const FILTRES: readonly { code: Filtre; libelle: string }[] = [
  { code: 'TOUTES', libelle: $localize`:@@journal.filtre.toutes:Toutes` },
  { code: 'SOS', libelle: 'SOS' },
  { code: 'RETRAIT', libelle: $localize`:@@journal.filtre.retrait:Retrait` },
  { code: 'SORTIE_ZONE', libelle: $localize`:@@journal.filtre.zones:Zones` },
  { code: 'BATTERIE_CRITIQUE', libelle: $localize`:@@journal.filtre.batterie:Batterie` },
];

/** Couleur du sigle, comme la maquette : SOS en ambre plein, zone en ambre doux, batterie en violet. */
const TEINTES: Record<TypeAlerte, string> = {
  SOS: 'bg-alert text-on-alert',
  RETRAIT: 'bg-surface-2 text-text-2',
  SIGNALEMENT: 'bg-alert-soft text-alert-ink',
  SORTIE_ZONE: 'bg-alert-soft text-alert-ink',
  BATTERIE_CRITIQUE: 'bg-attention-soft text-attention-text',
  CHUTE: 'bg-alert-soft text-alert-ink',
};

/**
 * Journal des alertes d'un enfant (écran 20, US-PAR-008) : déclenchement, prise en charge, escalade et levée
 * de chaque alerte, horodatés. Rien n'y est modifiable. La liste se filtre par type ; chaque alerte ouvre son
 * détail et son journal complet.
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
      <div class="flex flex-wrap gap-2" role="group" i18n-aria-label="@@journal.filtres" aria-label="Type d'alerte">
        @for (f of filtres; track f.code) {
          <button type="button" class="h-10 rounded-full px-3.5 text-label font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent" [class]="filtre() === f.code ? 'border-(length:--border-width-trait) border-accent bg-accent-soft' : 'border border-line-strong'" [attr.aria-pressed]="filtre() === f.code" (click)="filtre.set(f.code)">{{ f.libelle }}</button>
        }
      </div>
      <ul class="m-0 flex list-none flex-col divide-y divide-line p-0">
        @for (alerte of visibles(); track alerte.id) {
          <li>
            <a class="flex items-center gap-3 py-3.5 focus-visible:outline-2 focus-visible:outline-accent" [routerLink]="['/alertes', alerte.id]">
              <span class="grid size-9 flex-none place-items-center rounded-[10px] text-[10px] font-bold" [class]="teinte(alerte)" aria-hidden="true">{{ sigle(alerte) }}</span>
              <span class="flex min-w-0 flex-1 flex-col gap-0.5">
                <span class="text-body font-semibold">{{ titre(alerte) }}</span>
                <span class="text-caption text-text-2">{{ resume(alerte) }}</span>
              </span>
              <span class="flex-none text-caption text-text-2 tabular-nums">{{ alerte.ouverteLe | date: 'd MMM HH:mm' }}</span>
            </a>
          </li>
        } @empty {
          <li class="py-4 text-body text-text-2">
            @if (liste.length === 0) {
              <ng-container i18n="@@journal.vide">Aucune alerte n'a été enregistrée pour cet enfant.</ng-container>
            } @else {
              <ng-container i18n="@@journal.videFiltre">Aucune alerte de ce type.</ng-container>
            }
          </li>
        }
      </ul>
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
  host: { class: 'page-parent mx-auto flex min-h-dvh w-full max-w-md flex-col gap-4 px-5 pt-6 pb-7 lg:max-w-3xl lg:gap-5 lg:px-10 lg:pt-10' },
})
export class JournalAlertes {
  readonly id = input.required<string>();

  private readonly client = inject(ClientAlertes);

  protected readonly titre = titreCourt;
  protected readonly sigle = etiquetteAlerte;
  protected readonly filtres = FILTRES;
  protected readonly filtre = signal<Filtre>('TOUTES');
  protected readonly alertes = signal<Alerte[] | null>(null);
  protected readonly visibles = computed(() => (this.alertes() ?? []).filter((a) => this.filtre() === 'TOUTES' || a.type === this.filtre()));
  protected readonly erreur = signal<string | null>(null);

  constructor() {
    effect(() => this.charger(this.id()));
  }

  protected teinte(alerte: Alerte): string {
    return TEINTES[alerte.type];
  }

  /** La dernière étape inscrite au journal : « Levée · par vous · Enfant retrouvé ». */
  protected resume(alerte: Alerte): string {
    const derniere = alerte.actions.at(-1);
    return derniere ? actionLisible(derniere) : '';
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
