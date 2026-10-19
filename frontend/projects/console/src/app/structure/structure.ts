import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { ClientAuthentification, Compte, RoleInterne, Session } from 'api';
import { FgBouton, PreferenceTheme, Theme } from 'ui';

import { LIBELLES_ROLES } from '../commun/acces';
import { ENTREES } from './entrees';
import { Recherche } from './recherche';
import { Verrou } from './verrou';

/** Structure de la console (écran 56) : la barre latérale ne propose que les écrans du rôle de l'agent. */
@Component({
  selector: 'app-structure',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, FgBouton, Recherche, Verrou],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <aside class="flex w-62 flex-none flex-col gap-1 border-r border-line bg-surface px-3.5 py-5">
      <div class="flex flex-wrap items-center gap-2.5 px-2 pb-4.5">
        <img src="favicon.svg" alt="" class="size-6" />
        <strong class="font-display text-body font-bold" i18n="@@console.titre">Console</strong>
        <span class="ml-auto flex flex-wrap justify-end gap-1">
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
    <div class="flex min-w-0 flex-1 flex-col">
      <header class="flex h-16 flex-none items-center gap-3.5 border-b border-line px-7">
        <app-recherche />
        <!-- Apparence (écran 56) : un bouton qui passe de « système » à « sombre » puis « clair ». -->
        <button
          type="button"
          class="ml-auto grid size-10 flex-none place-items-center rounded-[10px] border border-line text-text hover:border-line-strong focus-visible:outline-2 focus-visible:outline-accent"
          [attr.aria-label]="libelleTheme()"
          [title]="libelleTheme()"
          (click)="changerTheme()"
        >
          @switch (theme.preference()) {
            @case ('clair') {
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" aria-hidden="true"><circle cx="12" cy="12" r="4"></circle><path d="M12 2v2M12 20v2M2 12h2M20 12h2M5 5l1.5 1.5M17.5 17.5 19 19M5 19l1.5-1.5M17.5 6.5 19 5"></path></svg>
            }
            @case ('sombre') {
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 14.5A8 8 0 0 1 9.5 4 8 8 0 1 0 20 14.5Z"></path></svg>
            }
            @default {
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" aria-hidden="true"><circle cx="12" cy="12" r="8"></circle><path d="M12 4a8 8 0 0 1 0 16Z" fill="currentColor"></path></svg>
            }
          }
        </button>
      </header>
      <main id="contenu" class="flex min-w-0 flex-1 flex-col gap-5 overflow-auto px-7 py-6"><router-outlet /></main>
    </div>
    @if (compte()?.identifiant; as identifiant) {
      <app-verrou [identifiant]="identifiant" />
    }
  `,
  host: { class: 'flex h-dvh' },
})
export class Structure {
  protected readonly session = inject(Session);
  protected readonly theme = inject(Theme);
  protected readonly libelleTheme = computed(() => {
    const noms: Record<PreferenceTheme, string> = {
      systeme: $localize`:@@theme.systeme:Système`,
      sombre: $localize`:@@theme.sombre:Sombre`,
      clair: $localize`:@@theme.clair:Clair`,
    };
    return $localize`:@@theme.bouton:Apparence : ${noms[this.theme.preference()]}:choix: — changer`;
  });
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

  /** Système, puis sombre, puis clair, puis de nouveau le réglage du système. */
  protected changerTheme(): void {
    const suite: Record<PreferenceTheme, PreferenceTheme> = { systeme: 'sombre', sombre: 'clair', clair: 'systeme' };
    this.theme.choisir(suite[this.theme.preference()]);
  }

  protected deconnecter(): void {
    const sortir = () => void this.router.navigate(['/connexion']);
    this.client.deconnecter().subscribe({ next: sortir, error: sortir });
  }
}
