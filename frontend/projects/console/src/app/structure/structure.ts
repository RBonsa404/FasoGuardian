import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { ClientAuthentification, Compte, RoleInterne, Session } from 'api';
import { FgBouton } from 'ui';

import { LIBELLES_ROLES } from '../commun/acces';

interface Entree {
  readonly libelle: string;
  readonly lien: string;
  readonly role?: RoleInterne;
}

const ENTREES: readonly Entree[] = [
  { libelle: $localize`:@@nav.tableau:Tableau de bord`, lien: '/' },
  { libelle: $localize`:@@nav.kyc:File KYC`, lien: '/kyc', role: 'KYC' },
  { libelle: $localize`:@@nav.parc:Parc de bracelets`, lien: '/sav/parc', role: 'SAV' },
  { libelle: $localize`:@@nav.muets:Bracelets muets`, lien: '/sav/muets', role: 'SAV' },
  { libelle: $localize`:@@nav.audit:Journal d'audit`, lien: '/admin/audit', role: 'ADMIN' },
  { libelle: $localize`:@@nav.conformite:Conformité CIL`, lien: '/conformite', role: 'ADMIN' },
];

/** Structure de la console (écran 56) : la barre latérale ne propose que les écrans du rôle de l'agent. */
@Component({
  selector: 'app-structure',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, FgBouton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <aside class="flex w-62 flex-none flex-col gap-1 border-r border-line bg-surface px-3.5 py-5">
      <div class="flex items-center gap-2.5 px-2 pb-4.5">
        <img src="favicon.svg" alt="" class="size-6" />
        <strong class="font-display text-body font-bold" i18n="@@console.titre">Console</strong>
        <span class="ml-auto flex gap-1">
          @for (role of session.roles(); track role) {
            <span class="rounded-xs bg-surface-2 px-1.5 py-0.5 text-caption font-semibold text-text-2">{{ role }}</span>
          }
        </span>
      </div>
      <nav class="flex flex-col gap-1" i18n-aria-label="@@nav.libelle" aria-label="Navigation principale">
        @for (entree of entrees(); track entree.lien) {
          <a
            class="flex h-10 items-center rounded-sm px-3 text-label font-medium text-text hover:bg-surface-2 focus-visible:outline-2 focus-visible:outline-accent"
            routerLinkActive="bg-accent-soft font-semibold text-accent"
            [routerLinkActiveOptions]="{ exact: entree.lien === '/' }"
            [routerLink]="entree.lien"
            >{{ entree.libelle }}</a
          >
        }
      </nav>
      <div class="mt-auto flex flex-col gap-2.5 rounded-md border border-line bg-bg p-3">
        <div class="flex flex-col gap-0.5">
          <strong class="text-label font-semibold break-all">{{ compte()?.identifiant }}</strong>
          <span class="text-caption text-text-3">{{ fonctions() }}</span>
        </div>
        <button fg-button variante="secondary" taille="sm" type="button" (click)="deconnecter()" i18n="@@nav.deconnexion">
          Se déconnecter
        </button>
      </div>
    </aside>
    <main id="contenu" class="flex min-w-0 flex-1 flex-col gap-5 overflow-auto px-7 py-6"><router-outlet /></main>
  `,
  host: { class: 'flex h-dvh' },
})
export class Structure {
  protected readonly session = inject(Session);
  private readonly client = inject(ClientAuthentification);
  private readonly router = inject(Router);

  protected readonly compte = signal<Compte | null>(null);
  protected readonly entrees = computed(() =>
    ENTREES.filter((entree) => !entree.role || this.session.roles().includes(entree.role)),
  );
  protected readonly fonctions = computed(() =>
    this.session
      .roles()
      .map((role) => LIBELLES_ROLES[role as RoleInterne] ?? role)
      .join(' · '),
  );

  constructor() {
    this.client.moi().subscribe({ next: (compte) => this.compte.set(compte), error: () => undefined });
  }

  protected deconnecter(): void {
    const sortir = () => void this.router.navigate(['/connexion']);
    this.client.deconnecter().subscribe({ next: sortir, error: sortir });
  }
}
