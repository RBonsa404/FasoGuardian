import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { ClientAuthentification, Session } from 'api';
import { FgBanniere, FgBouton, FgChamp, FgTelephone } from 'ui';

import { erreurLisible } from '../commun/erreurs';

/** Connexion d'un parent (écran 12, US-PAR-002) ; sert aussi de réauthentification après expiration (US-PAR-019). */
@Component({
  selector: 'app-connexion',
  imports: [ReactiveFormsModule, RouterLink, FgBanniere, FgBouton, FgChamp, FgTelephone],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex flex-col gap-2 px-5 pt-10">
      <img src="logo-sombre.svg" alt="FasoGuardian" class="mb-6 h-8 w-auto self-start" />
      <h1 class="m-0 text-titre-ecran font-bold tracking-tight" i18n="@@connexion.titre">Connexion</h1>
      <p class="m-0 text-body text-text-2" i18n="@@connexion.texte">Retrouvez la position et les alertes de votre enfant.</p>
    </div>
    <form id="connexion" class="flex flex-1 flex-col gap-3 p-5" (submit)="$event.preventDefault(); connecter()">
      @if (session.reauthentificationRequise()) {
        <fg-banner ton="attention" i18n="@@connexion.expiree">Votre session a expiré. Reconnectez-vous pour continuer.</fg-banner>
      }
      <fg-phone-input i18n-libelle="@@connexion.telephone" libelle="Numéro mobile" [formControl]="telephone" />
      <fg-input
        type="password"
        autocomplete="current-password"
        [longueurMax]="128"
        i18n-libelle="@@connexion.motDePasse"
        libelle="Mot de passe"
        [formControl]="motDePasse"
      />
      @if (erreur(); as message) {
        <fg-banner ton="erreur">{{ message }}</fg-banner>
      }
    </form>
    <div class="flex flex-col gap-2 px-5 pt-4 pb-7">
      <button fg-button taille="lg" type="submit" form="connexion" [chargement]="enCours()" i18n="@@connexion.action">
        Se connecter
      </button>
      <a class="py-2 text-center text-label font-medium text-text-3" [routerLink]="['/inscription', 'numero']" i18n="@@connexion.pied">
        Pas encore de compte ? S'inscrire
      </a>
    </div>
  `,
  host: { class: 'mx-auto flex min-h-dvh w-full max-w-md flex-col' },
})
export class Connexion {
  private readonly client = inject(ClientAuthentification);
  private readonly router = inject(Router);
  protected readonly session = inject(Session);

  protected readonly telephone = new FormControl('', { nonNullable: true });
  protected readonly motDePasse = new FormControl('', { nonNullable: true });
  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);

  protected connecter(): void {
    if (this.enCours()) {
      return;
    }
    if (this.telephone.value.length !== 8 || !this.motDePasse.value) {
      this.erreur.set($localize`:@@connexion.incomplet:Saisissez votre numéro et votre mot de passe.`);
      return;
    }
    this.enCours.set(true);
    this.erreur.set(null);
    this.client.connecter(this.telephone.value, this.motDePasse.value).subscribe({
      next: () => {
        this.motDePasse.setValue('');
        void this.router.navigate(['/']);
      },
      error: (cause: unknown) => {
        this.enCours.set(false);
        this.erreur.set(erreurLisible(cause).message);
      },
    });
  }
}
