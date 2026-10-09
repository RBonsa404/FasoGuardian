import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { Alerte, ClientAlertes, ClientAuthentification, ClientFamille, Compte, FicheEnfant } from 'api';
import { FgBadge, FgBanniere, FgBouton, FgIcon, FgSquelette } from 'ui';

import { erreurLisible } from '../commun/erreurs';
import { BandeauAbonnement } from '../abonnement/bandeau';
import { ApercuEnfant } from './apercu-enfant';

/**
 * Accueil du parent connecté. Tant que le dossier KYC n'est pas validé, il n'affiche que l'état du
 * compte ; une fois le compte actif, il devient le tableau de bord (écran 16) : un aperçu par enfant.
 */
@Component({
  selector: 'app-accueil',
  imports: [RouterLink, ApercuEnfant, BandeauAbonnement, FgBadge, FgBanniere, FgBouton, FgIcon, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (compte()?.statut === 'ACTIF') {
      <h1 class="sr-only" i18n="@@accueil.tableau">Tableau de bord</h1>
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
      @if (enfants(); as liste) {
        @if (liste.length > 1) {
          <div class="flex gap-1.5" role="group" i18n-aria-label="@@accueil.choixEnfant" aria-label="Enfant affiché">
            @for (enfant of liste; track enfant.id) {
              <button type="button" class="h-11 flex-1 truncate rounded-full px-3 text-label font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent" [class]="enfant.id === choisi()?.id ? 'border-2 border-accent bg-accent-soft' : 'border border-line-strong text-text-2'" [attr.aria-pressed]="enfant.id === choisi()?.id" (click)="choix.set(enfant.id)">{{ enfant.prenom }}</button>
            }
          </div>
        }
        @if (choisi(); as enfant) {
          <app-bandeau-abonnement [enfant]="enfant.id" />
          <app-apercu-enfant [enfant]="enfant" />
        }
      }
    } @else {
      <h1 class="m-0 text-titre-ecran font-bold tracking-tight" i18n="@@accueil.titre">Mon compte</h1>
    }
    @if (compte(); as c) {
      <section class="flex flex-col gap-3 rounded-lg border border-line bg-surface p-4">
        <div class="flex items-center justify-between gap-3">
          <span class="text-body font-semibold tabular-nums">{{ c.telephoneMasque }}</span>
          @if (c.statut === 'ACTIF') {
            <fg-badge ton="succes" icone="valider" i18n="@@compte.actif">Actif</fg-badge>
          } @else if (c.statut === 'EN_INSTRUCTION') {
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
    } @else if (erreur(); as message) {
      <fg-banner ton="erreur">{{ message }}</fg-banner>
      <button fg-button variante="secondary" type="button" (click)="charger()" i18n="@@commun.reessayer">Réessayer</button>
    } @else {
      <fg-skeleton forme="carte" />
    }
    @if (compte()?.statut === 'ACTIF') {
      <a class="flex min-h-14 items-center justify-between rounded-lg border border-line bg-surface px-4 text-body font-semibold focus-visible:outline-2 focus-visible:outline-accent" routerLink="/alertes" i18n="@@accueil.centre">Alertes</a>
      <a class="flex min-h-14 items-center justify-between rounded-lg border border-line bg-surface px-4 text-body font-semibold focus-visible:outline-2 focus-visible:outline-accent" routerLink="/enfants" i18n="@@accueil.enfants">Mes enfants</a>
      <a class="flex min-h-14 items-center justify-between rounded-lg border border-line bg-surface px-4 text-body font-semibold focus-visible:outline-2 focus-visible:outline-accent" routerLink="/abonnement" i18n="@@accueil.abonnement">Mon abonnement</a>
    }
    <a class="mt-auto self-start py-2 text-label font-semibold text-accent" routerLink="/reglages" i18n="@@accueil.reglages">Paramètres du compte</a>
    <button fg-button variante="secondary" type="button" [chargement]="sortie()" (click)="deconnecter()" i18n="@@accueil.deconnexion">
      Se déconnecter
    </button>
  `,
  host: { class: 'mx-auto flex min-h-dvh w-full max-w-md flex-col gap-4 px-5 pt-10 pb-7' },
})
export class Accueil {
  private readonly client = inject(ClientAuthentification);
  private readonly router = inject(Router);

  private readonly famille = inject(ClientFamille);
  private readonly clientAlertes = inject(ClientAlertes);

  /** Alertes en cours de tous les enfants, relues toutes les trente secondes. */
  private readonly alertes = signal<Alerte[]>([]);
  protected readonly alertesEnCours = computed(() => this.alertes().length);
  protected readonly lienAlertes = computed(() => (this.alertes().length === 1 ? ['/alertes', this.alertes()[0].id] : ['/alertes']));

  protected readonly compte = signal<Compte | null>(null);
  protected readonly enfants = signal<FicheEnfant[] | null>(null);
  /** Enfant choisi par le parent ; à défaut, le premier. */
  protected readonly choix = signal<string | null>(null);
  protected readonly choisi = computed(() => {
    const liste = this.enfants() ?? [];
    return liste.find((enfant) => enfant.id === this.choix()) ?? liste[0] ?? null;
  });
  protected readonly erreur = signal<string | null>(null);
  protected readonly sortie = signal(false);

  constructor() {
    this.charger();
    const minuterie = setInterval(() => {
      if (this.compte()?.statut === 'ACTIF') {
        this.lireAlertes();
      }
    }, 30_000);
    inject(DestroyRef).onDestroy(() => clearInterval(minuterie));
  }

  private lireAlertes(): void {
    this.clientAlertes.mesAlertes(true).subscribe({ next: (alertes) => this.alertes.set(alertes), error: () => undefined });
  }

  protected charger(): void {
    this.erreur.set(null);
    this.client.moi().subscribe({
      next: (compte) => {
        this.compte.set(compte);
        if (compte.statut === 'ACTIF') {
          this.famille.mesEnfants().subscribe({ next: (enfants) => this.enfants.set(enfants), error: () => this.enfants.set([]) });
          this.lireAlertes();
        }
      },
      error: (cause: unknown) => this.erreur.set(erreurLisible(cause).message),
    });
  }

  protected deconnecter(): void {
    this.sortie.set(true);
    // La session locale est fermée même si le serveur est injoignable.
    this.client.deconnecter().subscribe({
      next: () => void this.router.navigate(['/connexion']),
      error: () => void this.router.navigate(['/connexion']),
    });
  }
}
