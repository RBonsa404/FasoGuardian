import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';

import { ClientAuthentification, Compte } from 'api';
import { FgBouton, FgIcon, NomIcone } from 'ui';

import { CopieLocale } from '../commun/copie-locale';
import { EnfantCourant } from '../commun/enfant-courant';
import { EtatsTransverses } from './etats-transverses';

interface Onglet {
  readonly libelle: string;
  readonly icone: NomIcone;
  readonly lien: string;
  readonly compteActif?: boolean;
}

/** Écrans de premier niveau sur téléphone : la barre d'onglets n'apparaît que sur eux (écran 16). */
const PREMIER_NIVEAU = ['/', '/alertes', '/reglages'];

interface Entree {
  readonly libelle: string;
  readonly icone: NomIcone;
  readonly lien: string;
  /** Réservée aux comptes actifs : sans dossier validé, ces écrans n'ont rien à montrer. */
  readonly compteActif?: boolean;
}

const ENTREES: readonly Entree[] = [
  { libelle: $localize`:@@coque.tableau:Tableau de bord`, icone: 'maison', lien: '/' },
  { libelle: $localize`:@@accueil.centre:Alertes`, icone: 'cloche', lien: '/alertes', compteActif: true },
  { libelle: $localize`:@@accueil.enfants:Mes enfants`, icone: 'filiation', lien: '/enfants', compteActif: true },
  {
    libelle: $localize`:@@accueil.abonnement:Mon abonnement`,
    icone: 'recu', lien: '/abonnement',
    compteActif: true,
  },
  { libelle: $localize`:@@accueil.aide:Aide et support`, icone: 'support', lien: '/aide' },
  { libelle: $localize`:@@accueil.reglages:Paramètres du compte`, icone: 'reglages', lien: '/reglages' },
];

/**
 * Coque de l'espace parent connecté. Sur un téléphone elle ne montre rien de plus que l'écran. À partir de
 * 1024 px de large, une barre latérale donne accès aux rubriques, et l'écran occupe le reste de la fenêtre :
 * c'est la version « web » de l'application, la même que celle installée sur le téléphone.
 */
@Component({
  selector: 'app-coque',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, FgBouton, FgIcon, EtatsTransverses],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <aside
      class="hidden w-68 flex-none flex-col gap-1 border-r border-line bg-surface px-4 py-6 lg:sticky lg:top-0 lg:flex lg:h-dvh"
    >
      <a routerLink="/" class="group mb-5 flex items-center gap-3 rounded-md px-2 focus-visible:outline-2 focus-visible:outline-accent">
        <span class="grid size-10 place-items-center rounded-md bg-(image:--fond-hero) shadow-e2 transition-transform duration-(--duration-base) group-hover:-rotate-6">
          <img src="favicon.svg" alt="" class="size-6" />
        </span>
        <span class="flex flex-col leading-tight">
          <strong class="font-display text-body font-bold">FasoGuardian</strong>
          <span class="text-caption text-text-3" i18n="@@coque.espace">Espace parent</span>
        </span>
      </a>
      <nav
        class="flex flex-col gap-1"
        i18n-aria-label="@@coque.navigation"
        aria-label="Navigation principale"
      >
        @for (entree of entrees(); track entree.lien) {
          <a
            class="entree group relative flex h-11 items-center gap-3 rounded-md px-3 text-label font-medium text-text-2 transition-colors duration-(--duration-fast) hover:bg-surface-2 hover:text-text focus-visible:outline-2 focus-visible:outline-accent"
            routerLinkActive="entree-active bg-accent-soft font-semibold text-accent!"
            [routerLinkActiveOptions]="{ exact: entree.lien === '/' }"
            [routerLink]="entree.lien"
            ><fg-icon [nom]="entree.icone" [taille]="20" class="transition-transform duration-(--duration-fast) group-hover:scale-110" />{{ entree.libelle }}</a
          >
        }
      </nav>
      <div class="mt-auto flex flex-col gap-3 rounded-lg border border-line bg-bg p-3.5">
        @if (compte(); as c) {
          <div class="flex items-center gap-3">
            <span class="grid size-9 flex-none place-items-center rounded-full bg-accent-soft text-accent"><fg-icon nom="bouclier" [taille]="18" /></span>
            <span class="flex min-w-0 flex-col leading-tight">
              <span class="text-label font-semibold tabular-nums">{{ c.telephoneMasque }}</span>
              @if (etatCompte(); as etat) {
                <span class="text-caption" [class]="etat.ton">{{ etat.libelle }}</span>
              }
            </span>
          </div>
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
    <div [class]="ongletsVisibles() ? 'min-w-0 flex-1 max-lg:pb-18' : 'min-w-0 flex-1'"><app-etats-transverses /><router-outlet /></div>
    @if (ongletsVisibles()) {
      <nav class="fixed inset-x-0 bottom-0 z-1100 grid h-18 border-t border-line bg-bg pb-[env(safe-area-inset-bottom)] lg:hidden" [style.grid-template-columns]="'repeat(' + onglets().length + ', minmax(0, 1fr))'" i18n-aria-label="@@coque.onglets" aria-label="Onglets">
        @for (onglet of onglets(); track onglet.libelle) {
          <a
            class="flex flex-col items-center justify-center gap-1 text-[11px] font-medium text-text-3 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-accent"
            routerLinkActive="text-accent"
            [routerLinkActiveOptions]="{ exact: true }"
            [routerLink]="onglet.lien"
            ><fg-icon [nom]="onglet.icone" [taille]="22" />{{ onglet.libelle }}</a
          >
        }
      </nav>
    }
  `,
  styles: `
    /* Rubrique ouverte : un filet d'accent glisse à gauche de l'entrée. */
    .entree::before {
      content: '';
      position: absolute;
      inset-block: 10px;
      left: 0;
      width: 3px;
      border-radius: 3px;
      background: var(--color-accent);
      transform: scaleY(0);
      transition: transform var(--duration-base) var(--ease-enter);
    }
    .entree-active::before {
      transform: scaleY(1);
    }
  `,
  host: { class: 'block lg:flex' },
})
export class Coque {
  private readonly client = inject(ClientAuthentification);
  private readonly router = inject(Router);
  private readonly copie = inject(CopieLocale);

  private readonly courant = inject(EnfantCourant);
  private readonly adresse = toSignal(
    this.router.events.pipe(
      filter((evenement) => evenement instanceof NavigationEnd),
      map(() => this.router.url),
    ),
    { initialValue: this.router.url },
  );

  protected readonly compte = signal<Compte | null>(null);
  /** État du compte sous le numéro : ce que dit le dossier, rien de plus. */
  protected readonly etatCompte = computed(() => {
    switch (this.compte()?.statut) {
      case 'ACTIF':
        return { libelle: $localize`:@@coque.compte.actif:Compte vérifié`, ton: 'text-success' };
      case 'EN_INSTRUCTION':
        return { libelle: $localize`:@@coque.compte.instruction:Vérification en cours`, ton: 'text-attention-text' };
      case 'SUSPENDU':
        return { libelle: $localize`:@@coque.compte.suspendu:Compte suspendu`, ton: 'text-danger' };
      case 'CLOS':
        return { libelle: $localize`:@@coque.compte.clos:Compte clos`, ton: 'text-text-3' };
      default:
        return null;
    }
  });
  /** Accueil, Carte de l'enfant affiché, Alertes, Réglages ; sans compte actif, l'accueil et les réglages. */
  protected readonly onglets = computed<Onglet[]>(() => {
    const enfant = this.courant.id();
    const tous: Onglet[] = [
      { libelle: $localize`:@@coque.onglet.accueil:Accueil`, icone: 'maison', lien: '/' },
      { libelle: $localize`:@@coque.onglet.carte:Carte`, icone: 'position', lien: enfant ? `/enfants/${enfant}/carte` : '/enfants', compteActif: true },
      { libelle: $localize`:@@coque.onglet.alertes:Alertes`, icone: 'cloche', lien: '/alertes', compteActif: true },
      { libelle: $localize`:@@coque.onglet.reglages:Réglages`, icone: 'reglages', lien: '/reglages' },
    ];
    return tous.filter((onglet) => !onglet.compteActif || this.compte()?.statut === 'ACTIF');
  });
  protected readonly ongletsVisibles = computed(() => PREMIER_NIVEAU.includes(this.adresse().split(/[?#]/)[0]));
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
