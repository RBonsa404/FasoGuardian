import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { ClientProfil } from 'api';
import { FgBanniere, FgBouton, FgChamp, FgCode, FgTelephone } from 'ui';

import { erreurLisible } from '../commun/erreurs';
import { Etape } from '../gabarit/etape';

/** Mot de passe oublié (écran 12, US-PAR-002) : code reçu sur le téléphone vérifié, puis nouveau mot de passe. */
@Component({
  selector: 'app-mot-de-passe-oublie',
  imports: [ReactiveFormsModule, RouterLink, Etape, FgBanniere, FgBouton, FgChamp, FgCode, FgTelephone],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (etape() === 1) {
      <app-etape
        i18n-surtitre="@@oublie.surtitre1"
        surtitre="Mot de passe oublié · 1/2"
        i18n-titre="@@oublie.titre1"
        titre="Votre numéro de téléphone"
        i18n-texte="@@oublie.texte1"
        texte="Si un compte existe avec ce numéro, vous recevrez un code par SMS."
        [numero]="1"
        [total]="2"
        (retour)="retour()"
      >
        <form id="formulaire" class="contents" (submit)="$event.preventDefault(); demander()">
          <fg-phone-input i18n-libelle="@@connexion.telephone" libelle="Numéro mobile" [erreur]="erreurChamp()" [formControl]="telephone" />
        </form>
        <ng-container pied>
          <button fg-button taille="lg" type="submit" form="formulaire" [chargement]="enCours()" i18n="@@inscription.numero.action">Recevoir le code</button>
        </ng-container>
      </app-etape>
    } @else {
      <app-etape
        i18n-surtitre="@@oublie.surtitre2"
        surtitre="Mot de passe oublié · 2/2"
        i18n-titre="@@oublie.titre2"
        titre="Nouveau mot de passe"
        i18n-texte="@@oublie.texte2"
        texte="Saisissez le code reçu par SMS et choisissez un nouveau mot de passe."
        [numero]="2"
        [total]="2"
        (retour)="etape.set(1)"
      >
        <form id="formulaire" class="contents" (submit)="$event.preventDefault(); reinitialiser()">
          <fg-otp i18n-libelle="@@inscription.code.libelle" libelle="Code à 6 chiffres" [erreur]="erreurChamp()" [formControl]="code" />
          <fg-input
            type="password"
            autocomplete="new-password"
            [longueurMax]="128"
            i18n-libelle="@@oublie.nouveau"
            libelle="Nouveau mot de passe"
            i18n-aide="@@inscription.mdp.texte"
            aide="10 caractères minimum, avec un chiffre."
            [formControl]="motDePasse"
          />
        </form>
        @if (erreur(); as message) {
          <fg-banner ton="erreur">{{ message }}</fg-banner>
        }
        <ng-container pied>
          <button fg-button taille="lg" type="submit" form="formulaire" [chargement]="enCours()" i18n="@@oublie.action">Changer mon mot de passe</button>
          <a class="py-2 text-center text-label font-medium text-text-3" routerLink="/connexion" i18n="@@oublie.pied">Retour à la connexion</a>
        </ng-container>
      </app-etape>
    }
  `,
})
export class MotDePasseOublie {
  private readonly client = inject(ClientProfil);
  private readonly router = inject(Router);

  protected readonly telephone = new FormControl('', { nonNullable: true });
  protected readonly code = new FormControl('', { nonNullable: true });
  protected readonly motDePasse = new FormControl('', { nonNullable: true });
  protected readonly etape = signal<1 | 2>(1);
  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly erreurChamp = signal<string | null>(null);

  protected retour(): void {
    void this.router.navigate(['/connexion']);
  }

  protected demander(): void {
    if (this.telephone.value.length !== 8) {
      this.erreurChamp.set($localize`:@@inscription.numero.invalide:Saisissez 8 chiffres.`);
      return;
    }
    this.appeler(this.client.demanderReinitialisation(this.telephone.value), () => this.etape.set(2), true);
  }

  protected reinitialiser(): void {
    if (this.code.value.length !== 6 || this.motDePasse.value.length < 10 || !/\d/.test(this.motDePasse.value)) {
      this.erreur.set($localize`:@@oublie.incomplet:Saisissez le code à 6 chiffres et un mot de passe d'au moins 10 caractères avec un chiffre.`);
      return;
    }
    this.appeler(this.client.reinitialiser(this.telephone.value, this.code.value, this.motDePasse.value), () => {
      this.motDePasse.setValue('');
      void this.router.navigate(['/connexion'], { queryParams: { motDePasseChange: 1 } });
    });
  }

  private appeler(appel: ReturnType<ClientProfil['reinitialiser']>, suite: () => void, codeRecentAccepte = false): void {
    if (this.enCours()) {
      return;
    }
    this.enCours.set(true);
    this.erreur.set(null);
    this.erreurChamp.set(null);
    appel.subscribe({
      next: () => {
        this.enCours.set(false);
        suite();
      },
      error: (cause: unknown) => {
        this.enCours.set(false);
        const lisible = erreurLisible(cause);
        if (codeRecentAccepte && lisible.code === 'TROP_DE_REQUETES') {
          suite();
        } else if (['CODE_INCORRECT', 'CODE_EXPIRE', 'CODE_EPUISE', 'TELEPHONE_INVALIDE'].includes(lisible.code)) {
          this.erreurChamp.set(lisible.message);
        } else {
          this.erreur.set(lisible.message);
        }
      },
    });
  }
}
