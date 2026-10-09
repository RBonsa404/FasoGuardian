import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { ClientAuthentification, Compte } from 'api';
import { FgBouton } from 'ui';

import { CopieLocale } from '../commun/copie-locale';

interface Entree {
  readonly libelle: string;
  readonly lien: string;
  /** Réservée aux comptes actifs : sans dossier validé, ces écrans n'ont rien à montrer. */
  readonly compteActif?: boolean;
}

const ENTREES: readonly Entree[] = [
  { libelle: $localize`:@@coque.tableau:Tableau de bord`, lien: '/' },
  { libelle: $localize`:@@accueil.centre:Alertes`, lien: '/alertes', compteActif: true },
  { libelle: $localize`:@@accueil.enfants:Mes enfants`, lien: '/enfants', compteActif: true },
  {
    libelle: $localize`:@@accueil.abonnement:Mon abonnement`,
    lien: '/abonnement',
    compteActif: true,
  },
  { libelle: $localize`:@@accueil.aide:Aide et support`, lien: '/aide' },
  { libelle: $localize`:@@accueil.reglages:Paramètres du compte`, lien: '/reglages' },
];

/**
 * Coque de l'espace parent connecté. Sur un téléphone elle ne montre rien de plus que l'écran. À partir de
 * 1024 px de large, une barre latérale donne accès aux rubriques, et l'écran occupe le reste de la fenêtre :
 * c'est la version « web » de l'application, la même que celle installée sur le téléphone.
 */
@Component({
  selector: 'app-coque',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, FgBouton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <aside
      class="hidden w-62 flex-none flex-col gap-1 border-r border-line bg-surface px-3.5 py-5 lg:sticky lg:top-0 lg:flex lg:h-dvh"
    >
      <div class="flex items-center gap-2.5 px-2 pb-4.5">
        <img src="favicon.svg" alt="" class="size-6" />
        <strong class="font-display text-body font-bold">FasoGuardian</strong>
      </div>
      <nav
        class="flex flex-col gap-1"
        i18n-aria-label="@@coque.navigation"
        aria-label="Navigation principale"
      >
        @for (entree of entrees(); track entree.lien) {
          <a
            class="flex h-11 items-center rounded-sm px-3 text-label font-medium text-text hover:bg-surface-2 focus-visible:outline-2 focus-visible:outline-accent"
            routerLinkActive="bg-accent-soft font-semibold text-accent"
            [routerLinkActiveOptions]="{ exact: entree.lien === '/' }"
            [routerLink]="entree.lien"
            >{{ entree.libelle }}</a
          >
        }
      </nav>
      <div class="mt-auto flex flex-col gap-2.5 rounded-md border border-line bg-bg p-3">
        @if (compte(); as c) {
          <span class="text-label font-semibold tabular-nums">{{ c.telephoneMasque }}</span>
        }
        <button
          fg-button
          variante="secondary"
          taille="sm"
          type="button"
          [chargement]="sortie()"
          (click)="deconnecter()"
          i18n="@@accueil.deconnexion"
        >
          Se déconnecter
        </button>
      </div>
    </aside>
    <div class="min-w-0 flex-1"><router-outlet /></div>
  `,
  host: { class: 'block lg:flex' },
})
export class Coque {
  private readonly client = inject(ClientAuthentification);
  private readonly router = inject(Router);
  private readonly copie = inject(CopieLocale);

  protected readonly compte = signal<Compte | null>(null);
  protected readonly sortie = signal(false);
  protected readonly entrees = computed(() =>
    ENTREES.filter((entree) => !entree.compteActif || this.compte()?.statut === 'ACTIF'),
  );

  constructor() {
    this.client
      .moi()
      .subscribe({ next: (compte) => this.compte.set(compte), error: () => undefined });
  }

  protected deconnecter(): void {
    this.sortie.set(true);
    // Un appareil dont le parent se déconnecte ne garde aucune fiche.
    void this.copie.vider();
    const sortir = () => void this.router.navigate(['/connexion']);
    this.client.deconnecter().subscribe({ next: sortir, error: sortir });
  }
}
