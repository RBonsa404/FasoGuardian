import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ArticleAide, CATEGORIES_AIDE, CategorieAide, ClientSupport } from 'api';
import { FgBanniere, FgIcon, FgSquelette } from 'ui';

import { erreurLisible } from '../commun/erreurs';

export const LIBELLES_CATEGORIES: Record<CategorieAide, string> = {
  BRACELET: $localize`:@@aide.categorie.bracelet:Bracelet`,
  ALERTES: $localize`:@@aide.categorie.alertes:Alertes et SOS`,
  SAFE_ZONES: $localize`:@@aide.categorie.zones:Safe Zones`,
  PAIEMENT: $localize`:@@aide.categorie.paiement:Paiement`,
  COMPTE: $localize`:@@aide.categorie.compte:Compte et KYC`,
  VIE_PRIVEE: $localize`:@@aide.categorie.viePrivee:Vie privée`,
};

/**
 * Aide (écran 46, US-SUP-001) : recherche, catégories et articles les plus lus de la base de connaissances.
 * Le parent qui ne trouve pas sa réponse ouvre une demande de support.
 */
@Component({
  selector: 'app-aide',
  imports: [ReactiveFormsModule, RouterLink, FgBanniere, FgIcon, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="-ml-2.5 grid size-11 place-items-center rounded-md focus-visible:outline-2 focus-visible:outline-accent" routerLink="/" i18n-aria-label="@@etape.retour" aria-label="Retour">
      <fg-icon nom="retour" [taille]="22" />
    </a>
    <h1 class="m-0 text-h2 font-bold tracking-tight" i18n="@@aide.titre">Aide</h1>
    <form role="search" (submit)="$event.preventDefault(); chercher()">
      <label class="sr-only" for="recherche-aide" i18n="@@aide.recherche">Rechercher dans l'aide</label>
      <input id="recherche-aide" class="h-13 w-full rounded-md border border-line-strong bg-surface px-3.5 text-saisie font-medium outline-none placeholder:text-text-3 focus-visible:outline-2 focus-visible:outline-accent" type="search" maxlength="80" [formControl]="recherche" i18n-placeholder="@@aide.recherche.exemple" placeholder="Rechercher : recharge, SOS, zone…" />
    </form>

    <div class="grid grid-cols-2 gap-2" role="group" i18n-aria-label="@@aide.categories" aria-label="Catégories">
      @for (categorie of categories; track categorie) {
        <button type="button" class="flex flex-col gap-1 rounded-lg bg-surface p-3.5 text-left focus-visible:outline-2 focus-visible:outline-accent" [class]="choisie() === categorie ? 'border-2 border-accent' : 'border border-line'" [attr.aria-pressed]="choisie() === categorie" (click)="choisir(categorie)">
          <strong class="text-body font-semibold">{{ libelles[categorie] }}</strong>
          <span class="text-caption text-text-3" i18n="@@aide.articles">{{ decompte()[categorie] ?? 0 }} articles</span>
        </button>
      }
    </div>

    <h2 class="m-0 pt-1 text-label font-semibold">{{ titreListe() }}</h2>
    @if (erreur(); as message) {
      <fg-banner ton="erreur">{{ message }}</fg-banner>
    }
    @if (articles(); as liste) {
      <ul class="m-0 flex list-none flex-col p-0">
        @for (article of liste; track article.id) {
          <li class="border-b border-line">
            <a class="flex min-h-12 items-center justify-between gap-3 py-2 text-body font-medium focus-visible:outline-2 focus-visible:outline-accent" [routerLink]="['/aide', article.slug]">{{ article.titre }}</a>
          </li>
        } @empty {
          <li class="rounded-lg border border-line bg-surface p-4 text-body text-text-2" i18n="@@aide.aucun">Aucun article ne correspond. Écrivez-nous : nous vous répondons dans votre compte.</li>
        }
      </ul>
    } @else if (!erreur()) {
      <fg-skeleton forme="carte" />
    }

    <a class="mt-auto grid min-h-13 place-items-center rounded-md border border-line-strong text-body font-semibold focus-visible:outline-2 focus-visible:outline-accent" routerLink="/aide/demandes" i18n="@@aide.demandes">Mes demandes au support</a>
  `,
  host: { class: 'mx-auto flex min-h-dvh w-full max-w-md flex-col gap-4 px-5 pt-6 pb-7' },
})
export class Aide {
  private readonly client = inject(ClientSupport);

  protected readonly categories = CATEGORIES_AIDE;
  protected readonly libelles = LIBELLES_CATEGORIES;
  protected readonly recherche = new FormControl('', { nonNullable: true });

  protected readonly articles = signal<readonly ArticleAide[] | null>(null);
  protected readonly decompte = signal<Partial<Record<CategorieAide, number>>>({});
  protected readonly choisie = signal<CategorieAide | null>(null);
  protected readonly titreListe = signal($localize`:@@aide.plusLus:Les plus lus`);
  protected readonly erreur = signal<string | null>(null);

  constructor() {
    this.client.categories().subscribe({
      next: (liste) => this.decompte.set(Object.fromEntries(liste.map((ligne) => [ligne.categorie, ligne.articles]))),
      error: () => undefined,
    });
    this.charger();
  }

  /** Un second appui sur la même catégorie revient aux articles les plus lus. */
  protected choisir(categorie: CategorieAide): void {
    this.choisie.update((actuelle) => (actuelle === categorie ? null : categorie));
    this.recherche.setValue('');
    this.charger();
  }

  protected chercher(): void {
    this.choisie.set(null);
    this.charger();
  }

  private charger(): void {
    const categorie = this.choisie();
    const q = this.recherche.value.trim();
    this.titreListe.set(
      q
        ? $localize`:@@aide.resultats:Résultats pour « ${q}:mots: »`
        : categorie
          ? LIBELLES_CATEGORIES[categorie]
          : $localize`:@@aide.plusLus:Les plus lus`,
    );
    this.erreur.set(null);
    this.client.articles({ categorie: categorie ?? undefined, q: q || undefined }).subscribe({
      next: (articles) => this.articles.set(articles),
      error: (cause: unknown) => this.erreur.set(erreurLisible(cause).message),
    });
  }
}

/** Un article de l'aide (`/aide/:slug`) : texte simple, paragraphes séparés par une ligne vide. */
@Component({
  selector: 'app-article-aide',
  imports: [RouterLink, FgBanniere, FgIcon, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="-ml-2.5 grid size-11 place-items-center rounded-md focus-visible:outline-2 focus-visible:outline-accent" routerLink="/aide" i18n-aria-label="@@etape.retour" aria-label="Retour">
      <fg-icon nom="retour" [taille]="22" />
    </a>
    @if (article(); as a) {
      <span class="text-label font-semibold text-accent">{{ libelles[a.categorie] }}</span>
      <h1 class="m-0 text-h2 font-bold tracking-tight">{{ a.titre }}</h1>
      <p class="m-0 whitespace-pre-line text-body text-text-2">{{ a.contenu }}</p>
      <a class="mt-auto grid min-h-13 place-items-center rounded-md border border-line-strong text-body font-semibold focus-visible:outline-2 focus-visible:outline-accent" routerLink="/aide/demandes" i18n="@@aide.pasResolu">Cela ne résout pas mon problème</a>
    } @else if (erreur(); as message) {
      <fg-banner ton="erreur">{{ message }}</fg-banner>
    } @else {
      <fg-skeleton forme="carte" />
    }
  `,
  host: { class: 'mx-auto flex min-h-dvh w-full max-w-md flex-col gap-4 px-5 pt-6 pb-7' },
})
export class ArticleDAide {
  readonly slug = input.required<string>();

  private readonly client = inject(ClientSupport);
  protected readonly libelles = LIBELLES_CATEGORIES;
  protected readonly article = signal<ArticleAide | null>(null);
  protected readonly erreur = signal<string | null>(null);

  constructor() {
    effect(() => {
      this.client.article(this.slug()).subscribe({
        next: (article) => this.article.set(article),
        error: (cause: unknown) => this.erreur.set(erreurLisible(cause).message),
      });
    });
  }
}
