import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ClientAbonnements, ClientFamille, ClientZones, PositionConnue, SafeZone, Trajet } from 'api';
import { FgBanniere, FgIcon, FgSquelette } from 'ui';

import { erreurLisible } from '../commun/erreurs';
import { de } from '../commun/francais';
import { heure } from '../commun/temps';
import { Carte, PositionSurCarte } from './fond';
import { EtapeTrajet, frise } from './frise';

const JOURS_PROPOSES = 7;
const NOMS_OFFRES: Record<string, string> = {
  ESSENTIEL: $localize`:@@offre.essentiel:Essentiel`,
  INTERMEDIAIRE: $localize`:@@offre.intermediaire:Intermédiaire`,
  PREMIUM: $localize`:@@offre.premium:Premium`,
  ECOLE: $localize`:@@offre.ecole:École`,
};
/** Pas de la relecture : un point du trajet toutes les 400 ms. */
const PAS_RELECTURE_MS = 400;

interface Jour {
  readonly iso: string;
  readonly semaine: string;
  readonly numero: string;
  readonly complet: string;
}

interface LigneFrise {
  readonly titre: string;
  readonly detail: string;
  readonly heure: string;
  readonly pastille: string;
}

/** Ce qu'affiche une ligne de la frise : le lieu, s'il est connu par une zone, puis ce qui s'y est passé. */
export function ligneDe(etape: EtapeTrajet): LigneFrise {
  const lieu = etape.zone;
  switch (etape.nature) {
    case 'depart':
      return { titre: lieu ?? $localize`:@@trajets.depart.titre:Premier point du jour`, detail: $localize`:@@trajets.depart:Départ`, heure: heure(etape.debut), pastille: 'bg-text-3' };
    case 'arret':
      return lieu
        ? { titre: $localize`:@@trajets.arret.zone:${lieu}:lieu: (dans la zone)`, detail: $localize`:@@trajets.arret.duree:Arrêt ${etape.minutes}:minutes: min`, heure: heure(etape.debut), pastille: 'bg-success' }
        : { titre: $localize`:@@trajets.arret:Arrêt`, detail: $localize`:@@trajets.minutes:${etape.minutes}:minutes: min`, heure: heure(etape.debut), pastille: 'bg-accent' };
    case 'entree':
      return { titre: lieu ?? '', detail: $localize`:@@trajets.entree:Entrée dans la zone`, heure: heure(etape.debut), pastille: 'bg-success' };
    case 'sortie':
      return { titre: lieu ?? '', detail: $localize`:@@trajets.sortie:Sortie de la zone`, heure: heure(etape.debut), pastille: 'bg-attention' };
  }
}

/**
 * Historique des trajets (écran 19, US-PAR-008) : le tracé d'une journée parmi les sept dernières, que l'on
 * peut rejouer, et la frise des lieux (départ, arrêts, entrées et sorties des Safe Zones). La durée de
 * conservation de l'offre est rappelée.
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
      <h1 class="m-0 text-titre-ecran font-bold tracking-tight">{{ titre() }}</h1>
      <div class="flex gap-1" role="group" i18n-aria-label="@@trajets.jours" aria-label="Jour affiché">
        @for (j of jours; track j.iso) {
          <button type="button" class="flex h-14 flex-1 flex-col items-center justify-center rounded-md focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent" [class]="j.iso === jour() ? 'bg-primary text-white' : 'text-text-2 hover:bg-surface-2'" [attr.aria-pressed]="j.iso === jour()" [attr.aria-label]="j.complet" (click)="choisir(j.iso)">
            <span class="text-caption font-medium" aria-hidden="true">{{ j.semaine }}</span>
            <span class="text-body font-semibold tabular-nums" aria-hidden="true">{{ j.numero }}</span>
          </button>
        }
      </div>
    </div>

    <div class="relative mx-4 mt-4 h-60 flex-none overflow-hidden rounded-xl border border-line lg:col-start-2 lg:row-span-2 lg:row-start-1 lg:m-0 lg:h-full lg:rounded-none lg:border-0">
      <app-carte class="absolute inset-0" i18n-libelle="@@trajets.carte" libelle="Carte du trajet de la journée" [trace]="points()" [position]="repere()" [margeBas]="points().length > 1 ? 64 : 0" />
      @if (points().length > 1) {
        <div class="absolute inset-x-3 bottom-3 z-1000 flex items-center gap-3 rounded-lg bg-surface/90 px-3 py-2 shadow-e2 backdrop-blur">
          <button type="button" class="grid size-9 flex-none place-items-center rounded-full bg-primary text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent" [attr.aria-label]="enLecture() ? pause : lecture" (click)="basculerLecture()">
            @if (enLecture()) {
              <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M6 4h4v16H6zM14 4h4v16h-4z" /></svg>
            } @else {
              <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M7 4l13 8-13 8z" /></svg>
            }
          </button>
          <input type="range" class="min-w-0 flex-1 accent-accent" min="0" [max]="points().length - 1" step="1" [value]="curseur()" i18n-aria-label="@@trajets.curseur" aria-label="Moment du trajet" [attr.aria-valuetext]="heureCurseur()" (input)="placer($event)" />
          <span class="w-11 flex-none text-right text-label font-semibold tabular-nums">{{ heureCurseur() }}</span>
        </div>
      }
    </div>

    <div class="flex flex-1 flex-col gap-4 px-5 pt-4 pb-7" aria-live="polite">
      @if (trajet(); as t) {
        @if (lignes().length > 0) {
          <ol class="m-0 flex list-none flex-col p-0" i18n-aria-label="@@trajets.frise" aria-label="Lieux de la journée">
            @for (ligne of lignes(); track $index; let derniere = $last) {
              <li class="flex gap-3">
                <div class="flex flex-col items-center" aria-hidden="true">
                  <span class="mt-1.5 size-3 flex-none rounded-full" [class]="ligne.pastille"></span>
                  @if (!derniere) {
                    <span class="w-0.5 flex-1 bg-line"></span>
                  }
                </div>
                <div class="flex flex-1 justify-between gap-3 pb-3.5">
                  <div class="flex min-w-0 flex-col gap-0.5">
                    <strong class="text-body font-semibold">{{ ligne.titre }}</strong>
                    <span class="text-label text-text-2">{{ ligne.detail }}</span>
                  </div>
                  <span class="text-label font-semibold text-text-2 tabular-nums">{{ ligne.heure }}</span>
                </div>
              </li>
            }
          </ol>
        } @else {
          <p class="m-0 rounded-lg border border-line bg-surface p-5 text-body text-text-2" i18n="@@trajets.vide">Aucune position enregistrée ce jour-là.</p>
        }
        <p class="m-0 mt-auto rounded-banner bg-surface-2 px-4 py-3 text-label text-text-2">
          {{ conservation() }}
          @if (t.joursConserves < 90) {
            <a class="font-semibold text-accent underline" routerLink="/abonnement" i18n="@@trajets.premium">Passez en Premium pour 90 jours.</a>
          }
        </p>
      } @else if (erreur(); as message) {
        <fg-banner ton="erreur">{{ message }}</fg-banner>
      } @else {
        <fg-skeleton forme="carte" />
      }
    </div>
  `,
  // Grand écran : jours et frise à gauche, carte du trajet sur toute la hauteur à droite.
  host: { class: 'mx-auto flex min-h-dvh w-full max-w-md flex-col lg:mx-0 lg:grid lg:h-dvh lg:max-w-none lg:grid-cols-[26rem_minmax(0,1fr)] lg:grid-rows-[auto_1fr]' },
})
export class Trajets {
  readonly id = input.required<string>();

  private readonly client = inject(ClientZones);
  private readonly famille = inject(ClientFamille);
  private readonly abonnements = inject(ClientAbonnements);

  protected readonly jours = derniersJours(JOURS_PROPOSES);
  protected readonly jour = signal(this.jours[this.jours.length - 1].iso);
  protected readonly trajet = signal<Trajet | null>(null);
  protected readonly erreur = signal<string | null>(null);
  private readonly prenom = signal<string | null>(null);
  private readonly zones = signal<readonly SafeZone[]>([]);
  private readonly offre = signal<string | null>(null);
  /** Point du trajet montré par la relecture. */
  protected readonly curseur = signal(0);
  protected readonly enLecture = signal(false);
  private minuterie: ReturnType<typeof setInterval> | undefined;

  protected readonly lecture = $localize`:@@trajets.lire:Rejouer le trajet`;
  protected readonly pause = $localize`:@@trajets.pause:Mettre la relecture en pause`;

  protected readonly titre = computed(() => {
    const prenom = this.prenom();
    return prenom ? $localize`:@@trajets.titre.enfant:Trajets ${de(prenom)}:dePrenom:` : $localize`:@@trajets.titre:Trajets`;
  });
  protected readonly points = computed<readonly PositionConnue[]>(() =>
    [...(this.trajet()?.points ?? [])].sort((a, b) => Date.parse(a.mesureeLe) - Date.parse(b.mesureeLe)),
  );
  protected readonly lignes = computed(() => frise(this.points(), this.zones()).map(ligneDe));
  protected readonly repere = computed<PositionSurCarte | null>(() => {
    const point = this.points()[this.curseur()];
    return point ? { latitude: point.latitude, longitude: point.longitude, precisionM: point.precisionM, initiale: (this.prenom() ?? '·').charAt(0), attenuee: false } : null;
  });
  protected readonly heureCurseur = computed(() => {
    const point = this.points()[this.curseur()];
    return point ? heure(point.mesureeLe) : '';
  });
  protected readonly conservation = computed(() => {
    const jours = this.trajet()?.joursConserves ?? 0;
    const offre = this.offre();
    const duree = jours <= 1 ? $localize`:@@trajets.duree.jour:24 heures` : $localize`:@@trajets.duree.jours:${jours}:jours: jours`;
    return offre
      ? $localize`:@@trajets.conservation.offre:Offre ${offre}:offre: : ${duree}:duree: conservés, puis effacés.`
      : $localize`:@@trajets.conservation:Les trajets sont conservés ${duree}:duree:, puis effacés.`;
  });

  constructor() {
    effect(() => this.charger(this.id(), this.jour()));
    effect(() => {
      const id = this.id();
      this.famille.enfant(id).subscribe({ next: (enfant) => this.prenom.set(enfant.prenom), error: () => undefined });
      this.client.zones(id).subscribe({ next: (zones) => this.zones.set(zones.zones), error: () => undefined });
      this.abonnements.abonnement(id).subscribe({
        // Sans paiement confirmé, l'enfant a les droits de l'offre d'accueil : c'est elle qui fixe la conservation.
        next: (abonnement) => this.offre.set(abonnement.libelle ?? NOMS_OFFRES[abonnement.droits.offre] ?? null),
        error: () => undefined,
      });
    });
    inject(DestroyRef).onDestroy(() => this.arreterLecture());
  }

  protected choisir(jour: string): void {
    this.arreterLecture();
    this.jour.set(jour);
  }

  protected basculerLecture(): void {
    if (this.enLecture()) {
      this.arreterLecture();
      return;
    }
    // Relecture terminée : elle repart du début.
    if (this.curseur() >= this.points().length - 1) {
      this.curseur.set(0);
    }
    this.enLecture.set(true);
    this.minuterie = setInterval(() => {
      if (this.curseur() >= this.points().length - 1) {
        this.arreterLecture();
        return;
      }
      this.curseur.update((valeur) => valeur + 1);
    }, PAS_RELECTURE_MS);
  }

  protected placer(evenement: Event): void {
    this.arreterLecture();
    this.curseur.set(Number((evenement.target as HTMLInputElement).value));
  }

  private arreterLecture(): void {
    clearInterval(this.minuterie);
    this.enLecture.set(false);
  }

  private charger(id: string, jour: string): void {
    this.trajet.set(null);
    this.erreur.set(null);
    this.client.trajet(id, jour).subscribe({
      next: (trajet) => {
        this.trajet.set(trajet);
        // Le repère se place sur la dernière position du jour.
        this.curseur.set(Math.max(0, trajet.points.length - 1));
      },
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
