import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';

import { ClientConsole } from 'api';
import { FgBanniere, FgBouton, FgChamp, FgCode } from 'ui';

import { erreurLisible } from '../commun/acces';

type Etat = 'identifiants' | 'code' | 'enrolement';

/** Connexion d'un agent (écran 55, US-ADM-001) : identifiant, mot de passe, puis code TOTP obligatoire. */
@Component({
  selector: 'app-connexion',
  imports: [ReactiveFormsModule, FgBanniere, FgBouton, FgChamp, FgCode],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form class="flex w-full max-w-sm flex-col gap-4 rounded-lg border border-line bg-surface p-7 shadow-e2" (submit)="$event.preventDefault(); valider()">
      <div class="flex items-center gap-2.5">
        <img src="favicon.svg" alt="" class="size-7" />
        <strong class="font-display text-body font-bold" i18n="@@console.titre">Console</strong>
      </div>
      @switch (etat()) {
        @case ('identifiants') {
          <h1 class="m-0 text-h2 font-semibold" i18n="@@connexion.titre">Connexion agent</h1>
          <fg-input i18n-libelle="@@connexion.identifiant" libelle="Identifiant" autocomplete="username" [longueurMax]="64" [formControl]="identifiant" />
          <fg-input type="password" i18n-libelle="@@connexion.motDePasse" libelle="Mot de passe" autocomplete="current-password" [longueurMax]="128" [formControl]="motDePasse" />
        }
        @case ('enrolement') {
          <h1 class="m-0 text-h2 font-semibold" i18n="@@enrolement.titre">Premier enrôlement</h1>
          <p class="m-0 text-label text-text-2" i18n="@@enrolement.texte">
            Ajoutez ce compte dans votre application d'authentification (TOTP), puis saisissez le code affiché.
          </p>
          <div class="flex flex-col gap-1 rounded-md bg-surface-2 p-3">
            <span class="text-caption font-medium text-text-3" i18n="@@enrolement.secret">Clé à saisir dans l'application</span>
            <code class="font-mono text-label font-semibold tracking-wider break-all select-all" data-secret>{{ secret() }}</code>
          </div>
          <a class="text-label font-semibold text-accent" [href]="uri()" i18n="@@enrolement.ouvrir">Ouvrir dans mon application d'authentification</a>
          <fg-otp i18n-libelle="@@code.libelle" libelle="Code à 6 chiffres" [formControl]="code" />
        }
        @case ('code') {
          <h1 class="m-0 text-h2 font-semibold" i18n="@@code.titre">Code d'authentification</h1>
          <p class="m-0 text-label text-text-2" i18n="@@code.texte">Ouvrez votre application TOTP et saisissez le code à 6 chiffres.</p>
          <fg-otp i18n-libelle="@@code.libelle" libelle="Code à 6 chiffres" [formControl]="code" />
        }
      }
      @if (erreur(); as message) {
        <fg-banner ton="erreur">{{ message }}</fg-banner>
      }
      <button fg-button type="submit" [chargement]="enCours()">
        @if (etat() === 'identifiants') {
          <ng-container i18n="@@commun.continuer">Continuer</ng-container>
        } @else {
          <ng-container i18n="@@connexion.action">Se connecter</ng-container>
        }
      </button>
      <p class="m-0 text-caption text-text-3" i18n="@@connexion.avertissement">
        Accès réservé au personnel habilité. Toutes les actions sont journalisées.
      </p>
    </form>
  `,
  host: { class: 'grid min-h-dvh place-items-center p-6' },
})
export class Connexion {
  private readonly client = inject(ClientConsole);
  private readonly router = inject(Router);

  protected readonly identifiant = new FormControl('', { nonNullable: true });
  protected readonly motDePasse = new FormControl('', { nonNullable: true });
  protected readonly code = new FormControl('', { nonNullable: true });
  protected readonly etat = signal<Etat>('identifiants');
  protected readonly secret = signal('');
  protected readonly uri = signal('');
  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);

  protected valider(): void {
    if (this.enCours()) {
      return;
    }
    if (!this.identifiant.value.trim() || !this.motDePasse.value) {
      this.erreur.set($localize`:@@connexion.incomplet:Saisissez votre identifiant et votre mot de passe.`);
      return;
    }
    if (this.etat() !== 'identifiants' && this.code.value.length !== 6) {
      this.erreur.set($localize`:@@code.incomplet:Saisissez les 6 chiffres du code.`);
      return;
    }
    this.enCours.set(true);
    this.erreur.set(null);
    const code = this.etat() === 'identifiants' ? undefined : this.code.value;
    this.client.connecter(this.identifiant.value.trim(), this.motDePasse.value, code).subscribe({
      next: () => {
        this.motDePasse.setValue('');
        this.code.setValue('');
        void this.router.navigate(['/']);
      },
      error: (cause: unknown) => {
        this.enCours.set(false);
        this.code.setValue('');
        const lisible = erreurLisible(cause);
        if (lisible.code === 'TOTP_A_ACTIVER' && cause instanceof HttpErrorResponse) {
          this.secret.set(cause.error.secretTotp ?? '');
          this.uri.set(cause.error.uriTotp ?? '');
          this.etat.set('enrolement');
        } else if (lisible.code === 'CODE_TOTP_REQUIS') {
          this.etat.set('code');
        } else {
          this.erreur.set(lisible.message);
        }
      },
    });
  }
}
