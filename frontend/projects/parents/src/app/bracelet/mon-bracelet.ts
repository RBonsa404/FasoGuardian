import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { Bracelet, ClientBracelet } from 'api';
import { FgBanniere, FgBouton, FgIcon, FgInterrupteur, FgSquelette } from 'ui';

import { erreurLisible } from '../commun/erreurs';
import { VisuelBracelet } from './visuel';

/**
 * Mon bracelet (écran 37, US-PAR-013) : état, garantie, mode économie et accès aux déclarations. La
 * batterie, le signal et le dernier contact s'y ajoutent avec la télémétrie.
 */
@Component({
  selector: 'app-mon-bracelet',
  imports: [DatePipe, ReactiveFormsModule, RouterLink, FgBanniere, FgBouton, FgIcon, FgInterrupteur, FgSquelette, VisuelBracelet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="-ml-2.5 grid size-11 place-items-center rounded-md focus-visible:outline-2 focus-visible:outline-accent" [routerLink]="['/enfants', id()]" i18n-aria-label="@@etape.retour" aria-label="Retour">
      <fg-icon nom="retour" [taille]="22" />
    </a>
    <h1 class="sr-only" i18n="@@bracelet.titre">Mon bracelet</h1>

    @if (bracelet(); as b) {
      <div class="-mx-5 grid h-62.5 place-items-center bg-surface-2">
        <app-visuel-bracelet [numero]="b.numeroSerie" [grand]="true" />
      </div>

      @if (b.statut === 'PERDU') {
        <fg-banner ton="attention" i18n="@@bracelet.perdu">Déclaré perdu. La page QR est désactivée ; le suivi continue jusqu'au {{ b.suiviJusquAu | date: "d MMM 'à' HH:mm" }}.</fg-banner>
        <button fg-button variante="secondary" type="button" [chargement]="enCours()" (click)="retrouver()" i18n="@@bracelet.retrouve">Je l'ai retrouvé</button>
      }

      <dl class="m-0 grid grid-cols-2 gap-2">
        @for (carte of cartes(); track carte.cle) {
          <div class="flex flex-col gap-0.5 rounded-banner border border-line bg-surface p-3">
            <dt class="text-caption font-medium text-text-3">{{ carte.cle }}</dt>
            <dd class="m-0 text-saisie font-semibold tabular-nums">{{ carte.valeur }}</dd>
            <dd class="m-0 text-caption text-text-3">{{ carte.detail }}</dd>
          </div>
        }
      </dl>

      <fg-switch class="rounded-lg border border-line bg-surface p-3.5" [formControl]="economie" i18n-libelle="@@bracelet.economie" libelle="Mode économie">
        <strong class="text-body font-semibold" i18n="@@bracelet.economie">Mode économie</strong>
        <span class="text-caption text-text-3" i18n="@@bracelet.economie.texte">Position toutes les 15 min au lieu de 5 · autonomie prolongée</span>
      </fg-switch>

      @if (erreur(); as message) {
        <fg-banner ton="erreur">{{ message }}</fg-banner>
      }

      <nav class="mt-auto flex flex-col divide-y divide-line" i18n-aria-label="@@bracelet.actions" aria-label="Actions sur le bracelet">
        <a class="flex min-h-12 items-center justify-between text-body font-semibold focus-visible:outline-2 focus-visible:outline-accent" [routerLink]="['/enfants', id(), 'bracelet', 'perte']">
          <ng-container i18n="@@bracelet.declarer">Déclarer perdu ou volé</ng-container>
          <fg-icon class="rotate-180 text-text-3" nom="retour" [taille]="16" />
        </a>
        <a class="flex min-h-12 items-center justify-between text-body font-semibold focus-visible:outline-2 focus-visible:outline-accent" [routerLink]="['/enfants', id(), 'bracelet', 'perte']" fragment="desappairer">
          <ng-container i18n="@@bracelet.desappairer">Désappairer</ng-container>
          <fg-icon class="rotate-180 text-text-3" nom="retour" [taille]="16" />
        </a>
      </nav>
    } @else if (absent()) {
      <div class="grid place-items-center pt-6">
        <app-visuel-bracelet numero="FG-····" />
      </div>
      <p class="m-0 text-center text-body text-text-2" i18n="@@bracelet.absent">Aucun bracelet n'est associé à cet enfant.</p>
      <button fg-button class="mt-auto" taille="lg" type="button" (click)="associer()" i18n="@@bracelet.associer">Associer un bracelet</button>
    } @else if (erreur(); as message) {
      <fg-banner ton="erreur">{{ message }}</fg-banner>
    } @else {
      <fg-skeleton forme="carte" />
      <fg-skeleton forme="carte" />
    }
  `,
  host: { class: 'mx-auto flex min-h-dvh w-full max-w-md flex-col gap-4 px-5 pt-6 pb-7' },
})
export class MonBracelet {
  readonly id = input.required<string>();

  private readonly client = inject(ClientBracelet);
  private readonly router = inject(Router);

  protected readonly bracelet = signal<Bracelet | null>(null);
  protected readonly absent = signal(false);
  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly economie = new FormControl(false, { nonNullable: true });
  protected readonly cartes = computed(() => {
    const b = this.bracelet();
    if (!b) {
      return [];
    }
    return [
      { cle: $localize`:@@bracelet.etat:État`, valeur: ETATS[b.statut] ?? b.statut, detail: $localize`:@@bracelet.etat.detail:Numéro ${b.numeroSerie}:numero:` },
      {
        cle: $localize`:@@bracelet.positions:Positions`,
        valeur: $localize`:@@bracelet.positions.valeur:toutes les ${Math.round(b.intervalleS / 60)}:minutes: min`,
        detail: b.modeEconomie ? $localize`:@@bracelet.positions.economie:mode économie` : $localize`:@@bracelet.positions.normal:mode normal`,
      },
      { cle: $localize`:@@bracelet.logiciel:Logiciel`, valeur: b.versionLogiciel, detail: $localize`:@@bracelet.logiciel.detail:révision ${b.revisionMaterielle}:revision:` },
      { cle: $localize`:@@bracelet.etancheite:Étanchéité`, valeur: 'IP67', detail: $localize`:@@bracelet.etancheite.detail:1 m pendant 30 min` },
      {
        cle: $localize`:@@bracelet.garantie:Garantie`,
        valeur: b.garantieJusquAu ? $localize`:@@bracelet.garantie.valeur:jusqu'au ${moisAnnee(b.garantieJusquAu)}:date:` : '—',
        detail: $localize`:@@bracelet.garantie.detail:12 mois`,
      },
    ];
  });

  constructor() {
    effect(() => this.charger(this.id()));
    this.economie.valueChanges.subscribe((actif) => this.reglerEconomie(actif));
  }

  protected associer(): void {
    void this.router.navigate(['/bracelet/associer'], { queryParams: { enfant: this.id() } });
  }

  protected retrouver(): void {
    this.enCours.set(true);
    this.erreur.set(null);
    this.client.retrouver(this.id()).subscribe({
      next: (bracelet) => {
        this.enCours.set(false);
        this.afficher(bracelet);
      },
      error: (cause: unknown) => {
        this.enCours.set(false);
        this.erreur.set(erreurLisible(cause).message);
      },
    });
  }

  private reglerEconomie(actif: boolean): void {
    this.erreur.set(null);
    this.client.reglerModeEconomie(this.id(), actif).subscribe({
      next: (bracelet) => this.afficher(bracelet),
      error: (cause: unknown) => {
        // Le réglage n'a pas été pris en compte : l'interrupteur revient à sa position réelle.
        this.economie.setValue(!actif, { emitEvent: false });
        this.erreur.set(erreurLisible(cause).message);
      },
    });
  }

  private charger(id: string): void {
    this.bracelet.set(null);
    this.absent.set(false);
    this.erreur.set(null);
    this.client.bracelet(id).subscribe({
      next: (bracelet) => this.afficher(bracelet),
      error: (cause: unknown) => {
        const lisible = erreurLisible(cause);
        if (lisible.code === 'RESSOURCE_INTROUVABLE') {
          this.absent.set(true);
        } else {
          this.erreur.set(lisible.message);
        }
      },
    });
  }

  private afficher(bracelet: Bracelet): void {
    this.bracelet.set(bracelet);
    this.economie.setValue(bracelet.modeEconomie, { emitEvent: false });
  }
}

const ETATS: Partial<Record<Bracelet['statut'], string>> = {
  ACTIF: $localize`:@@bracelet.etat.actif:Actif`,
  PERDU: $localize`:@@bracelet.etat.perdu:Perdu`,
};

/** « 10/27 » à partir d'une date ISO. */
function moisAnnee(dateIso: string): string {
  return `${dateIso.slice(5, 7)}/${dateIso.slice(2, 4)}`;
}
