import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, signal } from '@angular/core';

import { ClientPartages, VuePartagee } from 'api';
import { FgIcon, FgSquelette } from 'ui';

import { Carte, PositionSurCarte } from '../carte/fond';
import { heure, ilYA } from '../commun/temps';
import { decompte } from './partage';

const RAFRAICHISSEMENT_MS = 30_000;

/**
 * Vue du contact secondaire (écran 50, US-SEC-001) : la personne qui a reçu le lien voit la dernière position,
 * sans compte, jusqu'à l'échéance ou la révocation. Rien d'autre n'est montré : ni nom de l'enfant, ni
 * historique. Lien expiré, révoqué ou inconnu : le même message.
 */
@Component({
  selector: 'app-vue-contact',
  imports: [Carte, FgIcon, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (vue(); as v) {
      <div class="flex flex-col gap-1">
        <h1 class="m-0 text-h2 font-bold tracking-tight">{{ titre() }}</h1>
        <span class="text-label text-text-2" i18n="@@contact.personnel">Lien personnel · ne le transférez pas</span>
      </div>
      <div class="flex items-center justify-between gap-3 rounded-lg bg-accent-soft px-4 py-3" role="timer">
        <span class="text-label text-text-2" i18n="@@contact.restant">Partage encore actif</span>
        <strong class="font-display text-h3 font-bold tabular-nums text-accent">{{ restant() }}</strong>
      </div>
      @if (v.position; as p) {
        <div class="relative h-80 overflow-hidden rounded-xl border border-line">
          <app-carte class="absolute inset-0" [libelle]="libelleCarte" [position]="repere()" />
        </div>
        <section class="flex flex-col gap-1 rounded-lg border border-line bg-surface p-4" aria-live="polite">
          <strong class="text-body font-semibold tabular-nums">{{ heure(p.mesureeLe) }} · {{ ilYA(p.mesureeLe) }}</strong>
          <span class="text-label text-text-2">
            @if (p.approximative) {
              <ng-container i18n="@@contact.approximative">Position approximative ± {{ p.precisionM }} m</ng-container>
            } @else {
              <ng-container i18n="@@contact.precise">Position précise ± {{ p.precisionM }} m</ng-container>
            }
          </span>
        </section>
      } @else {
        <p class="m-0 rounded-lg border border-line bg-surface p-5 text-body text-text-2" i18n="@@contact.sansPosition">Le bracelet n'a pas donné de position récente. Cette page se met à jour toute seule.</p>
      }
    } @else if (termine()) {
      <div class="my-auto flex flex-col items-center gap-4 text-center" role="status">
        <span class="grid size-18 place-items-center rounded-full bg-surface-2 text-text-2" aria-hidden="true"><fg-icon nom="horloge" [taille]="32" /></span>
        <h1 class="m-0 text-h2 font-bold tracking-tight" i18n="@@contact.termine">Ce partage est terminé.</h1>
        <p class="m-0 text-body text-text-2" i18n="@@contact.termine.texte">Contactez la personne qui vous l'a envoyé si besoin.</p>
      </div>
    } @else {
      <fg-skeleton forme="carte" />
      <fg-skeleton forme="carte" />
    }
  `,
  host: { class: 'mx-auto flex min-h-dvh w-full max-w-md flex-col gap-4 px-5 pt-6 pb-7' },
})
export class VueContact {
  /** Jeton du lien personnel (segment de l'adresse). */
  readonly jeton = input.required<string>();

  private readonly client = inject(ClientPartages);

  protected readonly heure = heure;
  protected readonly libelleCarte = $localize`:@@contact.carte:Carte de la position partagée`;

  protected readonly vue = signal<VuePartagee | null>(null);
  protected readonly termine = signal(false);
  private readonly maintenant = signal(Date.now());

  protected readonly titre = computed(() => {
    const par = this.vue()?.partagePar;
    return par ? $localize`:@@contact.titre:Position partagée par ${par}:prenom:` : $localize`:@@contact.titre.anonyme:Position partagée avec vous`;
  });
  protected readonly restant = computed(() => {
    const v = this.vue();
    return v ? decompte(Math.ceil((Date.parse(v.fin) - this.maintenant()) / 1000)) : '';
  });
  protected readonly repere = computed<PositionSurCarte | null>(() => {
    const p = this.vue()?.position;
    return p ? { latitude: p.latitude, longitude: p.longitude, precisionM: p.precisionM, initiale: '', attenuee: p.approximative } : null;
  });

  constructor() {
    effect(() => this.charger(this.jeton()));
    const horloge = setInterval(() => {
      this.maintenant.set(Date.now());
      const v = this.vue();
      // À l'échéance, la position disparaît de l'écran sans attendre la prochaine interrogation.
      if (v && Date.parse(v.fin) <= Date.now()) {
        this.terminer();
      }
    }, 1000);
    const rafraichissement = setInterval(() => {
      if (!this.termine()) {
        this.charger(this.jeton());
      }
    }, RAFRAICHISSEMENT_MS);
    inject(DestroyRef).onDestroy(() => {
      clearInterval(horloge);
      clearInterval(rafraichissement);
    });
  }

  protected ilYA(iso: string): string {
    return ilYA(iso, new Date(this.maintenant()));
  }

  private charger(jeton: string): void {
    this.client.consulter(jeton).subscribe({
      next: (vue) => this.vue.set(vue),
      error: (cause: unknown) => {
        // Réseau coupé : la dernière position reste affichée jusqu'à l'échéance ; 404 : le partage est terminé.
        if (cause instanceof HttpErrorResponse && cause.status === 404) {
          this.terminer();
        }
      },
    });
  }

  private terminer(): void {
    this.vue.set(null);
    this.termine.set(true);
  }
}
