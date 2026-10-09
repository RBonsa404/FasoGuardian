import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { Alerte, ClientAlertes, ClientFamille, FicheEnfant } from 'api';
import { FgBadge, FgBanniere, FgBouton, FgFeuille, FgIcon, FgSquelette } from 'ui';

import { erreurLisible } from '../commun/erreurs';
import { heure, ilYA } from '../commun/temps';
import { enCours, iconeAlerte, parGravite, statutLisible, titreCourt } from './libelles';

const RAFRAICHISSEMENT_MS = 30_000;

interface Groupe {
  readonly enfant: FicheEnfant | undefined;
  readonly enfantId: string;
  readonly alertes: readonly Alerte[];
  readonly aPrendreEnCharge: number;
}

/**
 * Centre des alertes (écrans 24, 26 et 27 ; US-ENF-001, US-PAR-018). Les alertes en cours sont regroupées par
 * enfant et classées par gravité, le SOS en tête ; une seule prise en charge suffit pour tout le groupe.
 */
@Component({
  selector: 'app-centre-alertes',
  imports: [RouterLink, FgBadge, FgBanniere, FgBouton, FgFeuille, FgIcon, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="-ml-2.5 grid size-11 place-items-center rounded-md focus-visible:outline-2 focus-visible:outline-accent" routerLink="/" i18n-aria-label="@@etape.retour" aria-label="Retour">
      <fg-icon nom="retour" [taille]="22" />
    </a>
    <h1 class="m-0 text-titre-ecran font-bold tracking-tight" i18n="@@alertes.titre">Alertes</h1>

    <div class="flex gap-1.5" role="group" i18n-aria-label="@@alertes.filtre" aria-label="Alertes affichées">
      <button type="button" class="h-11 flex-1 rounded-full px-3 text-label font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent" [class]="filtre(!toutes())" [attr.aria-pressed]="!toutes()" (click)="toutes.set(false)" i18n="@@alertes.enCours">En cours · {{ nombreEnCours() }}</button>
      <button type="button" class="h-11 flex-1 rounded-full px-3 text-label font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent" [class]="filtre(toutes())" [attr.aria-pressed]="toutes()" (click)="toutes.set(true)" i18n="@@alertes.toutes">Toutes</button>
    </div>

    @if (alertes(); as liste) {
      @if (erreur(); as message) {
        <fg-banner ton="erreur">{{ message }}</fg-banner>
      }
      @if (!toutes()) {
        @for (groupe of groupes(); track groupe.enfantId) {
          <section class="flex flex-col gap-2 rounded-lg border border-line bg-surface p-4">
            <h2 class="m-0 text-body font-semibold">
              @if (groupe.alertes.length > 1) {
                <ng-container i18n="@@alertes.groupe">{{ groupe.alertes.length }} alertes · {{ groupe.enfant?.prenom }}</ng-container>
              } @else {
                {{ groupe.enfant?.prenom }}
              }
            </h2>
            @if (groupe.alertes.length > 1) {
              <p class="m-0 text-label text-text-2" i18n="@@alertes.groupe.ordre">Regroupées par ordre de gravité</p>
            }
            <ol class="m-0 flex list-none flex-col divide-y divide-line p-0">
              @for (alerte of groupe.alertes; track alerte.id) {
                <li>
                  <a class="flex min-h-14 items-center gap-3 py-2 focus-visible:outline-2 focus-visible:outline-accent" [routerLink]="['/alertes', alerte.id]">
                    <span class="grid size-10 flex-none place-items-center rounded-full" [class]="pastille(alerte)" aria-hidden="true"><fg-icon [nom]="icone(alerte)" [taille]="20" /></span>
                    <span class="flex min-w-0 flex-1 flex-col gap-0.5">
                      <strong class="truncate text-body font-semibold">{{ titre(alerte) }}</strong>
                      <span class="text-label text-text-2 tabular-nums">{{ heure(alerte.ouverteLe) }} · {{ ilYA(alerte.ouverteLe) }} · {{ statut(alerte.statut) }}</span>
                    </span>
                  </a>
                </li>
              }
            </ol>
            @if (groupe.aPrendreEnCharge > 0) {
              @if (groupe.alertes.length > 1) {
                <p class="m-0 text-label text-text-2" i18n="@@alertes.groupe.aide">Une seule prise en charge suffit. Chaque alerte reste tracée séparément dans le journal.</p>
              }
              <button fg-button variante="alert" type="button" [chargement]="enTraitement() === groupe.enfantId" (click)="prendreEnCharge(groupe)">
                @if (groupe.aPrendreEnCharge > 1) {
                  <ng-container i18n="@@alertes.toutPrendre">Tout prendre en charge</ng-container>
                } @else {
                  <ng-container i18n="@@alertes.prendre">Prendre en charge</ng-container>
                }
              </button>
            }
          </section>
        } @empty {
          <p class="m-0 rounded-lg border border-line bg-surface p-5 text-body text-text-2" i18n="@@alertes.aucune">Aucune alerte en cours.</p>
        }
      } @else {
        @for (alerte of liste; track alerte.id) {
          <a class="flex items-center gap-3 rounded-lg border border-line bg-surface p-4 focus-visible:outline-2 focus-visible:outline-accent" [routerLink]="['/alertes', alerte.id]">
            <span class="grid size-10 flex-none place-items-center rounded-full" [class]="pastille(alerte)" aria-hidden="true"><fg-icon [nom]="icone(alerte)" [taille]="20" /></span>
            <span class="flex min-w-0 flex-1 flex-col gap-0.5">
              <strong class="truncate text-body font-semibold">{{ titre(alerte) }} · {{ prenom(alerte) }}</strong>
              <span class="text-label text-text-2 tabular-nums">{{ ilYA(alerte.ouverteLe) }}</span>
              <fg-badge class="mt-1 self-start" [ton]="alerte.closeLe ? 'neutre' : 'alerte'">{{ statut(alerte.statut) }}</fg-badge>
            </span>
          </a>
        } @empty {
          <p class="m-0 rounded-lg border border-line bg-surface p-5 text-body text-text-2" i18n="@@alertes.jamais">Aucune alerte n'a été enregistrée.</p>
        }
      }
      @if (enfants().length > 0) {
        <button fg-button class="mt-auto" variante="secondary" type="button" (click)="signalement.set(true)" i18n="@@alertes.signaler">Signaler une disparition</button>
      }
    } @else if (erreur(); as message) {
      <fg-banner ton="erreur">{{ message }}</fg-banner>
      <button fg-button variante="secondary" type="button" class="self-start" (click)="charger()" i18n="@@commun.reessayer">Réessayer</button>
    } @else {
      <fg-skeleton forme="carte" />
      <fg-skeleton forme="carte" />
    }

    <fg-sheet i18n-titre="@@alertes.signaler.titre" titre="Signaler une disparition" [ouverte]="signalement()" (fermee)="signalement.set(false)">
      <p class="m-0 text-body text-text-2" i18n="@@alertes.signaler.texte">Le signalement est horodaté et les autres tuteurs sont prévenus. Vous restez seul à décider de la transmission aux forces de sécurité.</p>
      @for (enfant of enfants(); track enfant.id) {
        <button fg-button variante="alert" type="button" [chargement]="enTraitement() === enfant.id" (click)="signaler(enfant)" i18n="@@alertes.signaler.pour">Signaler pour {{ enfant.prenom }}</button>
      }
      <button fg-button variante="secondary" type="button" (click)="signalement.set(false)" i18n="@@commun.annuler">Annuler</button>
    </fg-sheet>
  `,
  host: { class: 'mx-auto flex min-h-dvh w-full max-w-md flex-col gap-4 px-5 pt-6 pb-7' },
})
export class CentreAlertes {
  private readonly client = inject(ClientAlertes);
  private readonly famille = inject(ClientFamille);
  private readonly router = inject(Router);
  protected readonly signalement = signal(false);

  protected readonly heure = heure;
  protected readonly ilYA = ilYA;
  protected readonly icone = iconeAlerte;
  protected readonly titre = titreCourt;
  protected readonly statut = statutLisible;

  protected readonly alertes = signal<Alerte[] | null>(null);
  protected readonly enfants = signal<FicheEnfant[]>([]);
  protected readonly toutes = signal(false);
  protected readonly enTraitement = signal<string | null>(null);
  protected readonly erreur = signal<string | null>(null);

  protected readonly nombreEnCours = computed(() => (this.alertes() ?? []).filter(enCours).length);
  protected readonly groupes = computed((): Groupe[] => {
    const parEnfant = new Map<string, Alerte[]>();
    for (const alerte of (this.alertes() ?? []).filter(enCours)) {
      parEnfant.set(alerte.enfantId, [...(parEnfant.get(alerte.enfantId) ?? []), alerte]);
    }
    return [...parEnfant.entries()].map(([enfantId, alertes]) => ({
      enfantId,
      enfant: this.enfants().find((enfant) => enfant.id === enfantId),
      alertes: parGravite(alertes),
      aPrendreEnCharge: alertes.filter((alerte) => alerte.statut === 'OUVERTE').length,
    }));
  });

  constructor() {
    this.charger();
    const minuterie = setInterval(() => this.charger(), RAFRAICHISSEMENT_MS);
    inject(DestroyRef).onDestroy(() => clearInterval(minuterie));
  }

  protected filtre(actif: boolean): string {
    return actif ? 'border-2 border-accent bg-accent-soft' : 'border border-line-strong text-text-2';
  }

  protected pastille(alerte: Alerte): string {
    // L'ambre signale une alerte qui attend encore quelqu'un ; une alerte close redevient neutre.
    return alerte.closeLe ? 'bg-surface-2 text-text-2' : alerte.gravite === 'CRITIQUE' ? 'bg-alert text-on-alert' : 'bg-alert-soft text-alert-ink';
  }

  protected prenom(alerte: Alerte): string {
    return this.enfants().find((enfant) => enfant.id === alerte.enfantId)?.prenom ?? '';
  }

  protected prendreEnCharge(groupe: Groupe): void {
    this.enTraitement.set(groupe.enfantId);
    this.erreur.set(null);
    this.client.prendreEnCharge(groupe.enfantId).subscribe({
      next: () => {
        this.enTraitement.set(null);
        this.charger();
      },
      error: (cause: unknown) => {
        this.enTraitement.set(null);
        this.erreur.set(erreurLisible(cause).message);
      },
    });
  }

  protected signaler(enfant: FicheEnfant): void {
    this.enTraitement.set(enfant.id);
    this.client.signaler(enfant.id).subscribe({
      next: (alerte) => void this.router.navigate(['/alertes', alerte.id]),
      error: (cause: unknown) => {
        this.enTraitement.set(null);
        this.signalement.set(false);
        this.erreur.set(erreurLisible(cause).message);
      },
    });
  }

  protected charger(): void {
    forkJoin({ alertes: this.client.mesAlertes(false), enfants: this.famille.mesEnfants() }).subscribe({
      next: ({ alertes, enfants }) => {
        this.erreur.set(null);
        this.enfants.set(enfants);
        this.alertes.set(alertes);
      },
      error: (cause: unknown) => {
        // Les alertes déjà affichées restent à l'écran si le réseau manque au rafraîchissement.
        if (!this.alertes()) {
          this.erreur.set(erreurLisible(cause).message);
        }
      },
    });
  }
}
