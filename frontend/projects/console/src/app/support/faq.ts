import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Observable } from 'rxjs';

import { ArticleAide, CATEGORIES_AIDE, CategorieAide, ClientSupport } from 'api';
import { FgBadge, FgBanniere, FgBouton, FgChamp, FgSquelette } from 'ui';

import { erreurLisible, estRefus } from '../commun/acces';

const CATEGORIES: Record<CategorieAide, string> = {
  BRACELET: $localize`:@@aide.categorie.bracelet:Bracelet`,
  ALERTES: $localize`:@@aide.categorie.alertes:Alertes et SOS`,
  SAFE_ZONES: $localize`:@@aide.categorie.zones:Safe Zones`,
  PAIEMENT: $localize`:@@aide.categorie.paiement:Paiement`,
  COMPTE: $localize`:@@aide.categorie.compte:Compte et KYC`,
  VIE_PRIVEE: $localize`:@@aide.categorie.viePrivee:Vie privée`,
};

/**
 * Éditeur de la base de connaissances (écran 63, US-SUP-001) : l'opérateur rédige un article, en voit
 * l'aperçu tel que le parent le lira, puis le publie. Un article reste en brouillon tant qu'il n'est pas publié.
 */
@Component({
  selector: 'app-faq',
  imports: [ReactiveFormsModule, FgBadge, FgBanniere, FgBouton, FgChamp, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex flex-wrap items-center justify-between gap-3">
      <h1 class="m-0 text-titre-ecran font-bold tracking-tight" i18n="@@faq.titre">FAQ · éditeur</h1>
      <button fg-button variante="secondary" type="button" (click)="nouveau()" i18n="@@faq.nouveau">Nouvel article</button>
    </div>
    @if (erreur(); as message) {
      <fg-banner ton="erreur">{{ message }}</fg-banner>
    }
    <div class="grid grid-cols-1 gap-4 lg:grid-cols-[320px_1fr_340px]">
      <div class="flex flex-col gap-2">
        @if (articles(); as liste) {
          @for (article of liste; track article.id) {
            <button type="button" class="flex flex-col gap-1 rounded-lg bg-surface p-3.5 text-left focus-visible:outline-2 focus-visible:outline-accent" [class]="edite()?.id === article.id ? 'border-2 border-accent' : 'border border-line'" (click)="ouvrir(article)">
              <strong class="text-label font-semibold">{{ article.titre }}</strong>
              <span class="flex items-center gap-2 text-caption text-text-2">
                <fg-badge [ton]="article.publie ? 'succes' : 'neutre'">{{ article.publie ? publie : brouillon }}</fg-badge>
                {{ categories[article.categorie] }} · <ng-container i18n="@@faq.lectures">{{ article.lectures }} lectures</ng-container>
              </span>
            </button>
          } @empty {
            <p class="m-0 rounded-lg border border-line bg-surface p-4 text-body text-text-2" i18n="@@faq.vide">Aucun article pour l'instant.</p>
          }
        } @else if (!erreur()) {
          <fg-skeleton forme="carte" />
        }
      </div>

      <form class="flex flex-col gap-3 rounded-lg border border-line bg-surface p-5" (submit)="$event.preventDefault()">
        <div class="flex items-center justify-between gap-3">
          <h2 class="m-0 text-h3 font-semibold">{{ edite() ? titreModifier : titreNouveau }}</h2>
          @if (edite(); as a) {
            <fg-badge [ton]="a.publie ? 'succes' : 'neutre'">{{ a.publie ? publie : brouillon }}</fg-badge>
          }
        </div>
        <label class="flex flex-col gap-1.5 text-label font-semibold">
          <span i18n="@@faq.categorie">Catégorie</span>
          <select class="h-11 rounded-md border border-line-strong bg-surface px-3 text-label outline-none focus-visible:outline-2 focus-visible:outline-accent" [formControl]="categorie">
            @for (code of codes; track code) {
              <option [value]="code">{{ categories[code] }}</option>
            }
          </select>
        </label>
        <fg-input [formControl]="titre" i18n-libelle="@@faq.titreArticle" libelle="Titre" [longueurMax]="120" />
        <label class="flex flex-col gap-1.5 text-label font-semibold">
          <span i18n="@@faq.contenu">Contenu</span>
          <textarea class="min-h-56 rounded-md border border-line-strong bg-surface p-3 text-label font-normal outline-none focus-visible:outline-2 focus-visible:outline-accent" maxlength="6000" [formControl]="contenu"></textarea>
          <span class="text-caption font-normal text-text-3" i18n="@@faq.aide">Texte simple. Laissez une ligne vide entre deux paragraphes ; numérotez les étapes à la main.</span>
        </label>
        <div class="flex flex-wrap justify-end gap-2">
          <button fg-button variante="secondary" type="button" [chargement]="enCours()" (click)="enregistrer()" i18n="@@faq.enregistrer">Enregistrer</button>
          @if (edite(); as a) {
            @if (a.publie) {
              <button fg-button variante="danger" type="button" [chargement]="enCours()" (click)="basculer(a)" i18n="@@faq.retirer">Retirer de la publication</button>
            } @else {
              <button fg-button type="button" [chargement]="enCours()" (click)="basculer(a)" i18n="@@faq.publier">Publier</button>
            }
          }
        </div>
      </form>

      <section class="flex flex-col gap-2" aria-labelledby="titre-apercu">
        <h2 id="titre-apercu" class="m-0 text-label font-semibold text-text-2" i18n="@@faq.apercu">Aperçu mobile</h2>
        <div class="flex min-h-80 flex-col gap-3 rounded-xl border border-line bg-bg p-5">
          <span class="text-label font-semibold text-accent">{{ categories[categorie.value] }}</span>
          <strong class="font-display text-h3 font-bold tracking-tight">{{ titre.value || titreVide }}</strong>
          <p class="m-0 whitespace-pre-line text-body text-text-2">{{ contenu.value }}</p>
        </div>
      </section>
    </div>
  `,
  host: { class: 'contents' },
})
export class Faq {
  private readonly client = inject(ClientSupport);
  private readonly router = inject(Router);

  protected readonly codes = CATEGORIES_AIDE;
  protected readonly categories = CATEGORIES;
  protected readonly publie = $localize`:@@faq.publie:Publié`;
  protected readonly brouillon = $localize`:@@faq.brouillon:Brouillon`;
  protected readonly titreNouveau = $localize`:@@faq.nouveau:Nouvel article`;
  protected readonly titreModifier = $localize`:@@faq.modifier:Modifier l'article`;
  protected readonly titreVide = $localize`:@@faq.titreVide:Titre de l'article`;

  protected readonly articles = signal<readonly ArticleAide[] | null>(null);
  protected readonly edite = signal<ArticleAide | null>(null);
  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);

  protected readonly categorie = new FormControl<CategorieAide>('BRACELET', { nonNullable: true });
  protected readonly titre = new FormControl('', { nonNullable: true });
  protected readonly contenu = new FormControl('', { nonNullable: true });

  constructor() {
    this.charger();
  }

  protected nouveau(): void {
    this.edite.set(null);
    this.categorie.setValue('BRACELET');
    this.titre.setValue('');
    this.contenu.setValue('');
    this.erreur.set(null);
  }

  protected ouvrir(article: ArticleAide): void {
    this.edite.set(article);
    this.categorie.setValue(article.categorie);
    this.titre.setValue(article.titre);
    this.contenu.setValue(article.contenu);
    this.erreur.set(null);
  }

  protected enregistrer(): void {
    if (!this.titre.value.trim() || !this.contenu.value.trim()) {
      this.erreur.set($localize`:@@faq.incomplet:Donnez un titre et un contenu à l'article.`);
      return;
    }
    const saisie = { categorie: this.categorie.value, titre: this.titre.value.trim(), contenu: this.contenu.value.trim() };
    const article = this.edite();
    this.appeler(article ? this.client.modifier(article.id, saisie) : this.client.rediger(saisie));
  }

  protected basculer(article: ArticleAide): void {
    this.appeler(article.publie ? this.client.retirer(article.id) : this.client.publier(article.id));
  }

  private appeler(appel: Observable<ArticleAide>): void {
    if (this.enCours()) {
      return;
    }
    this.enCours.set(true);
    this.erreur.set(null);
    appel.subscribe({
      next: (article) => {
        this.enCours.set(false);
        this.edite.set(article);
        this.charger();
      },
      error: (cause: unknown) => {
        this.enCours.set(false);
        this.signaler(cause);
      },
    });
  }

  private charger(): void {
    this.client.tousLesArticles().subscribe({
      next: (articles) => this.articles.set(articles),
      error: (cause: unknown) => this.signaler(cause),
    });
  }

  private signaler(cause: unknown): void {
    if (estRefus(cause)) {
      void this.router.navigate(['/refuse']);
    } else {
      this.erreur.set(erreurLisible(cause).message);
    }
  }
}
