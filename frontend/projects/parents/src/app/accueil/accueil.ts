import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';

import { Alerte, ClientAlertes, ClientAuthentification, ClientFamille, Compte, FicheEnfant } from 'api';
import { FgBadge, FgBanniere, FgBouton, FgFeuille, FgIcon, FgSquelette } from 'ui';

import { enCours } from '../alertes/libelles';
import { BandeauAbonnement } from '../abonnement/bandeau';
import { BandeauLitige } from './bandeau-litige';
import { CopieLocale } from '../commun/copie-locale';
import { EnfantCourant } from '../commun/enfant-courant';
import { erreurLisible } from '../commun/erreurs';
import { ApercuEnfant } from './apercu-enfant';

/**
 * Accueil du parent connecté. Tant que le dossier KYC n'est pas validé, il n'affiche que l'état du
 * compte ; une fois le compte actif, il devient le tableau de bord (écran 16) : l'aperçu de l'enfant choisi.
 */
@Component({
  selector: 'app-accueil',
  imports: [DatePipe, RouterLink, ApercuEnfant, BandeauAbonnement, BandeauLitige, FgBadge, FgBanniere, FgBouton, FgFeuille, FgIcon, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (compte()?.statut === 'ACTIF') {
      <!-- Grand écran : un vrai en-tête de page, la date du jour et les enfants à portée de clic. -->
      <header class="flex flex-wrap items-end justify-between gap-4 max-lg:contents">
        <div class="flex flex-col gap-1 max-lg:sr-only">
          <span class="text-caption font-semibold tracking-widest text-accent uppercase">{{ aujourdhui | date: 'EEEE d MMMM' }}</span>
          <h1 class="m-0 animate-(--animate-rise) text-h1 font-bold tracking-tight" i18n="@@accueil.tableau">Tableau de bord</h1>
        </div>
        @if ((enfants() ?? []).length > 1) {
          <div class="hidden gap-2 lg:flex" role="group" i18n-aria-label="@@accueil.choixEnfant" aria-label="Enfant affiché">
            @for (enfant of enfants() ?? []; track enfant.id) {
              <button type="button" class="flex h-11 items-center gap-2 rounded-full border py-1 pr-4 pl-1.5 text-label font-semibold transition-colors duration-(--duration-fast) focus-visible:outline-2 focus-visible:outline-accent" [class]="enfant.id === choisi()?.id ? 'border-accent bg-accent-soft text-accent' : 'border-line bg-surface text-text-2 hover:border-line-strong hover:text-text'" [attr.aria-pressed]="enfant.id === choisi()?.id" (click)="choisir(enfant.id)">
                <span class="grid size-8 place-items-center rounded-full border-2 border-accent bg-surface-2 font-display text-label font-bold text-text" aria-hidden="true">{{ enfant.prenom.charAt(0) }}</span>
                {{ enfant.prenom }}
              </button>
            }
          </div>
        }
      </header>
      @if (erreur(); as message) {
        <fg-banner ton="erreur">{{ message }}</fg-banner>
        <button fg-button variante="secondary" type="button" class="self-start" (click)="charger()" i18n="@@commun.reessayer">Réessayer</button>
      } @else if (enfants(); as liste) {
        @if (choisi(); as enfant) {
          <app-apercu-enfant [enfant]="enfant" [alertes]="alertesDe(enfant.id)" [alertesEnCours]="alertesEnCours()" (changerEnfant)="choixOuvert.set(true)">
            @if (alertesEnCours(); as nombre) {
              <a class="flex min-h-14 items-center gap-3 rounded-lg bg-alert px-4 font-semibold text-on-alert focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent" [routerLink]="lienAlertes()" role="alert">
                <fg-icon nom="cloche" [taille]="22" />
                <span class="flex-1">
                  @if (nombre > 1) {
                    <ng-container i18n="@@accueil.alertes">{{ nombre }} alertes en cours</ng-container>
                  } @else {
                    <ng-container i18n="@@accueil.alerte">1 alerte en cours</ng-container>
                  }
                </span>
                <span class="text-label" i18n="@@accueil.alertes.voir">Voir</span>
              </a>
            }
            <app-bandeau-litige class="empty:hidden" [enfant]="enfant.id" [prenom]="enfant.prenom" />
            <app-bandeau-abonnement class="empty:hidden" [enfant]="enfant.id" />
          </app-apercu-enfant>
        } @else {
          <section class="flex flex-col gap-3 rounded-lg border border-line bg-surface p-5 lg:max-w-md">
            <h2 class="m-0 text-h3 font-semibold" i18n="@@accueil.sansEnfant.titre">Aucun enfant pour le moment</h2>
            <p class="m-0 text-body text-text-2" i18n="@@accueil.sansEnfant">La fiche de votre enfant est créée à la validation de votre dossier.</p>
            <a class="self-start text-label font-semibold text-accent" routerLink="/enfants" i18n="@@accueil.enfants">Mes enfants</a>
          </section>
        }
      } @else {
        <fg-skeleton forme="carte" />
      }

      <fg-sheet i18n-titre="@@accueil.choixEnfant" titre="Enfant affiché" [ouverte]="choixOuvert()" (fermee)="choixOuvert.set(false)">
        <div class="flex flex-col gap-2">
          @for (enfant of enfants() ?? []; track enfant.id) {
            <button type="button" class="flex min-h-13 items-center gap-3 rounded-md border px-3 text-left text-body font-semibold focus-visible:outline-2 focus-visible:outline-accent" [class]="enfant.id === choisi()?.id ? 'border-accent bg-accent-soft' : 'border-line'" [attr.aria-pressed]="enfant.id === choisi()?.id" (click)="choisir(enfant.id)">
              <span class="grid size-8 place-items-center rounded-full border-2 border-accent bg-surface-2 font-display text-label font-bold" aria-hidden="true">{{ enfant.prenom.charAt(0) }}</span>
              {{ enfant.prenom }}
            </button>
          }
          <a class="grid min-h-12 place-items-center text-label font-semibold text-accent focus-visible:outline-2 focus-visible:outline-accent" routerLink="/enfants" i18n="@@accueil.tousMesEnfants">Tous mes enfants</a>
        </div>
      </fg-sheet>
    } @else {
      <h1 class="m-0 text-titre-ecran font-bold tracking-tight" i18n="@@accueil.titre">Mon compte</h1>
      @if (compte(); as c) {
        <section class="flex flex-col gap-3 rounded-lg border border-line bg-surface p-4 lg:max-w-md">
          <div class="flex items-center justify-between gap-3">
            <span class="text-body font-semibold tabular-nums">{{ c.telephoneMasque }}</span>
            @if (c.statut === 'EN_INSTRUCTION') {
              <fg-badge ton="accent" icone="horloge" i18n="@@compte.instruction">En instruction</fg-badge>
            } @else {
              <fg-badge ton="attention" icone="info" i18n="@@compte.suspendu">Suspendu</fg-badge>
            }
          </div>
          @if (c.statut === 'EN_INSTRUCTION') {
            <p class="m-0 text-label text-text-2" i18n="@@accueil.instruction">
              Votre compte sera actif dès que votre lien avec l'enfant aura été vérifié par un agent.
            </p>
            <a class="self-start text-label font-semibold text-accent" routerLink="/verification" i18n="@@accueil.suivre">
              Suivre ma vérification
            </a>
          }
        </section>
        <a class="flex min-h-14 items-center justify-between rounded-lg border border-line bg-surface px-4 text-body font-semibold focus-visible:outline-2 focus-visible:outline-accent lg:hidden" routerLink="/aide" i18n="@@accueil.aide">Aide et support</a>
      } @else if (erreur(); as message) {
        <fg-banner ton="erreur">{{ message }}</fg-banner>
        <button fg-button variante="secondary" type="button" (click)="charger()" i18n="@@commun.reessayer">Réessayer</button>
      } @else {
        <fg-skeleton forme="carte" />
      }
    }
  `,
  host: { class: 'mx-auto flex min-h-dvh w-full max-w-md flex-col gap-4 px-5 pt-10 pb-7 lg:mx-0 lg:max-w-7xl lg:gap-6 lg:px-10 lg:pt-8' },
})
export class Accueil {
  private readonly client = inject(ClientAuthentification);
  private readonly famille = inject(ClientFamille);
  private readonly clientAlertes = inject(ClientAlertes);
  private readonly courant = inject(EnfantCourant);
  private readonly copie = inject(CopieLocale);

  /** Alertes de tous les enfants, les plus récentes d'abord, relues toutes les trente secondes. */
  private readonly alertes = signal<Alerte[]>([]);
  private readonly enCours = computed(() => this.alertes().filter(enCours));
  protected readonly alertesEnCours = computed(() => this.enCours().length);
  protected readonly lienAlertes = computed(() => (this.enCours().length === 1 ? ['/alertes', this.enCours()[0].id] : ['/alertes']));

  protected readonly compte = signal<Compte | null>(null);
  protected readonly enfants = signal<FicheEnfant[] | null>(null);
  protected readonly choixOuvert = signal(false);
  /** Enfant choisi par le parent pendant la session ; à défaut, le premier. */
  protected readonly choisi = computed(() => {
    const liste = this.enfants() ?? [];
    return liste.find((enfant) => enfant.id === this.courant.id()) ?? liste[0] ?? null;
  });
  protected readonly erreur = signal<string | null>(null);
  protected readonly aujourdhui = new Date();

  constructor() {
    this.charger();
    // À chaque ouverture du tableau de bord, la copie locale des fiches est rafraîchie et l'application est
    // mise en cache pour s'ouvrir sans réseau (US-PAR-019).
    void this.copie.synchroniser();
    if (typeof navigator !== 'undefined' && 'serviceWorker' in navigator) {
      void navigator.serviceWorker.register(new URL('sw.js', document.baseURI)).catch(() => undefined);
    }
    const minuterie = setInterval(() => {
      if (this.compte()?.statut === 'ACTIF') {
        this.lireAlertes();
      }
    }, 30_000);
    inject(DestroyRef).onDestroy(() => clearInterval(minuterie));
  }

  protected alertesDe(enfantId: string): Alerte[] {
    return this.alertes().filter((alerte) => alerte.enfantId === enfantId);
  }

  protected choisir(id: string): void {
    this.courant.choisir(id);
    this.choixOuvert.set(false);
  }

  private lireAlertes(): void {
    this.clientAlertes.mesAlertes(false).subscribe({ next: (alertes) => this.alertes.set(alertes), error: () => undefined });
  }

  protected charger(): void {
    this.erreur.set(null);
    this.client.moi().subscribe({
      next: (compte) => {
        this.compte.set(compte);
        if (compte.statut === 'ACTIF') {
          this.famille.mesEnfants().subscribe({
            next: (enfants) => {
              this.enfants.set(enfants);
              const choisi = this.choisi();
              if (choisi && this.courant.id() !== choisi.id) {
                this.courant.choisir(choisi.id);
              }
            },
            // Une erreur n'est pas une absence d'enfant : le parent doit le savoir, plutôt que de lire « Aucun enfant ».
            error: (cause: unknown) => this.erreur.set(erreurLisible(cause).message),
          });
          this.lireAlertes();
        }
      },
      error: (cause: unknown) => this.erreur.set(erreurLisible(cause).message),
    });
  }
}
