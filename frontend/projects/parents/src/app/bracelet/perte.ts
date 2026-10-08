import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { Bracelet, ClientBracelet, MotifDeclaration } from 'api';
import { FgBanniere, FgBouton, FgFeuille, FgIcon, FgSquelette } from 'ui';

import { erreurLisible } from '../commun/erreurs';
import { SecondFacteur } from '../commun/second-facteur';

const MOTIFS: readonly { valeur: MotifDeclaration; libelle: string }[] = [
  { valeur: 'PERDU', libelle: $localize`:@@perte.motif.perdu:Perdu` },
  { valeur: 'VOLE', libelle: $localize`:@@perte.motif.vole:Volé` },
  { valeur: 'CASSE', libelle: $localize`:@@perte.motif.casse:Cassé` },
];

const REMPLACEMENT = $localize`:@@perte.remplacement:Un bracelet de remplacement s'obtient en point relais (30 000 FCFA, ou inclus si garantie).`;

/** Ce qui se passe après la déclaration, selon le motif (ADR 0009). */
const SUITES: Record<MotifDeclaration, readonly string[]> = {
  PERDU: [
    $localize`:@@perte.suite.page:La page QR affichera « bracelet désactivé » (aucune donnée médicale).`,
    $localize`:@@perte.suite.suivi:Le suivi continue 72 h pour vous aider à le retrouver.`,
    REMPLACEMENT,
  ],
  VOLE: [
    $localize`:@@perte.suite.page:La page QR affichera « bracelet désactivé » (aucune donnée médicale).`,
    $localize`:@@perte.suite.certificat:Le bracelet ne pourra plus se connecter : il est bloqué aussitôt.`,
    REMPLACEMENT,
  ],
  CASSE: [
    $localize`:@@perte.suite.pageActive:La page QR reste active tant que l'enfant porte le bracelet.`,
    $localize`:@@perte.suite.sav:Rapportez le bracelet en point relais : le service après-vente le prend en charge.`,
    REMPLACEMENT,
  ],
};

/**
 * Perte, vol, casse et désappairage (écran 39, US-PAR-014). La déclaration est confirmée par code SMS ;
 * le désappairage sans déclaration demande une confirmation explicite.
 */
@Component({
  selector: 'app-perte-bracelet',
  imports: [RouterLink, FgBanniere, FgBouton, FgFeuille, FgIcon, FgSquelette, SecondFacteur],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="-ml-2.5 grid size-11 place-items-center rounded-md focus-visible:outline-2 focus-visible:outline-accent" [routerLink]="['/enfants', id(), 'bracelet']" i18n-aria-label="@@etape.retour" aria-label="Retour">
      <fg-icon nom="retour" [taille]="22" />
    </a>

    @if (bracelet(); as b) {
      <div class="flex flex-col gap-1.5">
        <h1 class="m-0 text-h2 font-bold tracking-tight" i18n="@@perte.titre">Déclarer {{ b.numeroSerie }} perdu ou volé</h1>
        <p class="m-0 text-body text-text-2" i18n="@@perte.texte">Ce qui va se passer :</p>
      </div>

      <ol class="m-0 flex list-none flex-col gap-3 rounded-lg border border-line bg-surface p-4 text-body font-medium">
        @for (suite of suites(); track suite; let i = $index) {
          <li class="flex gap-2.5">
            <span class="grid size-5.5 flex-none place-items-center rounded-full bg-surface-2 text-caption font-bold" aria-hidden="true">{{ i + 1 }}</span>
            {{ suite }}
          </li>
        }
      </ol>

      <fieldset class="m-0 flex flex-col gap-1.5 border-0 p-0">
        <legend class="mb-1.5 p-0 text-label font-semibold" i18n="@@perte.motif">Motif</legend>
        <div class="flex gap-1.5">
          @for (m of motifs; track m.valeur) {
            <label class="grid h-11 flex-1 cursor-pointer place-items-center rounded-md text-body font-semibold has-focus-visible:outline-2 has-focus-visible:outline-offset-2 has-focus-visible:outline-accent" [class]="motif() === m.valeur ? 'border-2 border-accent' : 'border border-line-strong'">
              <input class="sr-only" type="radio" name="motif" [value]="m.valeur" [checked]="motif() === m.valeur" (change)="motif.set(m.valeur)" />
              {{ m.libelle }}
            </label>
          }
        </div>
      </fieldset>

      @if (erreur(); as message) {
        <fg-banner ton="erreur">{{ message }}</fg-banner>
      }

      <div class="mt-auto flex flex-col gap-2">
        <button fg-button taille="lg" type="button" [chargement]="enCours()" (click)="confirmation.set(true)" i18n="@@perte.declarer">Déclarer · code SMS</button>
        <button fg-button variante="ghost" type="button" class="text-danger" (click)="desappairage.set(true)" i18n="@@perte.desappairer">Désappairer sans déclarer</button>
      </div>

      <app-second-facteur
        action="DECLARER_BRACELET"
        [ouverte]="confirmation()"
        i18n-explication="@@perte.secondFacteur"
        explication="Saisissez le code reçu par SMS pour confirmer la déclaration."
        [erreur]="erreurCode()"
        (saisi)="declarer($event)"
        (annule)="confirmation.set(false)"
      />

      <fg-sheet i18n-titre="@@perte.desappairer.titre" titre="Désappairer ce bracelet ?" [ouverte]="desappairage()" (fermee)="desappairage.set(false)">
        <p class="m-0 text-body text-text-2" i18n="@@perte.desappairer.texte">{{ b.numeroSerie }} ne protégera plus votre enfant et sa page QR ne répondra plus. Pour l'associer de nouveau, il devra repasser par un point relais.</p>
        <button fg-button variante="danger" type="button" [chargement]="enCours()" (click)="desappairer()" i18n="@@perte.desappairer.confirmer">Désappairer</button>
        <button fg-button variante="secondary" type="button" (click)="desappairage.set(false)" i18n="@@commun.annuler">Annuler</button>
      </fg-sheet>
    } @else if (erreur(); as message) {
      <fg-banner ton="erreur">{{ message }}</fg-banner>
    } @else {
      <fg-skeleton forme="carte" />
    }
  `,
  host: { class: 'mx-auto flex min-h-dvh w-full max-w-md flex-col gap-4 px-5 pt-6 pb-7' },
})
export class PerteBracelet {
  readonly id = input.required<string>();

  private readonly client = inject(ClientBracelet);
  private readonly router = inject(Router);

  protected readonly motifs = MOTIFS;
  protected readonly bracelet = signal<Bracelet | null>(null);
  protected readonly motif = signal<MotifDeclaration>('PERDU');
  protected readonly suites = computed(() => SUITES[this.motif()]);
  protected readonly confirmation = signal(false);
  protected readonly desappairage = signal(false);
  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly erreurCode = signal<string | null>(null);

  constructor() {
    effect(() => this.charger(this.id()));
    if (this.router.parseUrl(this.router.url).fragment === 'desappairer') {
      this.desappairage.set(true);
    }
  }

  protected declarer(code: string): void {
    if (this.enCours()) {
      return;
    }
    this.enCours.set(true);
    this.erreurCode.set(null);
    this.client.declarer(this.id(), this.motif(), code).subscribe({
      next: () => this.revenir(),
      error: (cause: unknown) => {
        this.enCours.set(false);
        const lisible = erreurLisible(cause);
        if (lisible.code?.startsWith('CODE_')) {
          this.erreurCode.set(lisible.message);
        } else {
          this.confirmation.set(false);
          this.erreur.set(lisible.message);
        }
      },
    });
  }

  protected desappairer(): void {
    this.enCours.set(true);
    this.client.desappairer(this.id()).subscribe({
      next: () => this.revenir(),
      error: (cause: unknown) => {
        this.enCours.set(false);
        this.desappairage.set(false);
        this.erreur.set(erreurLisible(cause).message);
      },
    });
  }

  private revenir(): void {
    void this.router.navigate(['/enfants', this.id(), 'bracelet']);
  }

  private charger(id: string): void {
    this.client.bracelet(id).subscribe({
      next: (bracelet) => this.bracelet.set(bracelet),
      error: (cause: unknown) => this.erreur.set(erreurLisible(cause).message),
    });
  }
}
