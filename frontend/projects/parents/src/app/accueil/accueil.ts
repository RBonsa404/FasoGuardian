import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { ClientAuthentification, Compte } from 'api';
import { FgBadge, FgBanniere, FgBouton, FgSquelette } from 'ui';

import { erreurLisible } from '../commun/erreurs';

/**
 * Accueil du parent connecté. Tant que le dossier KYC n'est pas validé, il n'affiche que l'état du
 * compte ; le tableau de bord des enfants (écran 16) s'y ajoute avec les modules famille et telemetrie.
 */
@Component({
  selector: 'app-accueil',
  imports: [RouterLink, FgBadge, FgBanniere, FgBouton, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 class="m-0 text-titre-ecran font-bold tracking-tight" i18n="@@accueil.titre">Mon compte</h1>
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
      <a class="flex min-h-14 items-center justify-between rounded-lg border border-line bg-surface px-4 text-body font-semibold focus-visible:outline-2 focus-visible:outline-accent" routerLink="/enfants" i18n="@@accueil.enfants">Mes enfants</a>
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

  protected readonly compte = signal<Compte | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly sortie = signal(false);

  constructor() {
    this.charger();
  }

  protected charger(): void {
    this.erreur.set(null);
    this.client.moi().subscribe({
      next: (compte) => this.compte.set(compte),
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
