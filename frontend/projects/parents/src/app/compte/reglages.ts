import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { ClientConformite, ClientProfil } from 'api';
import { FgBanniere, FgBouton, FgChamp, FgCode, FgFeuille, FgIcon, FgTelephone } from 'ui';

import { CopieLocale } from '../commun/copie-locale';
import { erreurLisible } from '../commun/erreurs';
import { SecondFacteur } from '../commun/second-facteur';

type Feuille = 'mot-de-passe' | 'telephone' | 'cloture' | null;

/**
 * Paramètres du compte (écran 48, US-PAR-002) : mot de passe, numéro de téléphone, clôture du compte.
 * La clôture, action sensible, passe par la feuille de second facteur.
 */
@Component({
  selector: 'app-reglages',
  imports: [ReactiveFormsModule, RouterLink, FgBanniere, FgBouton, FgChamp, FgCode, FgFeuille, FgIcon, FgTelephone, SecondFacteur],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './reglages.html',
  host: { class: 'mx-auto flex min-h-dvh w-full max-w-md flex-col gap-4 px-5 pt-6 pb-7' },
})
export class Reglages {
  private readonly client = inject(ClientProfil);
  private readonly router = inject(Router);
  private readonly conformite = inject(ClientConformite);
  private readonly copie = inject(CopieLocale);

  protected readonly feuille = signal<Feuille>(null);
  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly succes = signal<string | null>(null);
  protected readonly codeEnvoye = signal(false);
  protected readonly export = signal(false);

  protected readonly actuel = new FormControl('', { nonNullable: true });
  protected readonly nouveau = new FormControl('', { nonNullable: true });
  protected readonly telephone = new FormControl('', { nonNullable: true });
  protected readonly code = new FormControl('', { nonNullable: true });
  protected readonly explicationCloture = $localize`:@@cloture.explication:La clôture est définitive : votre compte sera fermé et vos données supprimées sous 30 jours. Saisissez le code reçu par SMS pour confirmer.`;

  /** Droit d'accès : le fichier est produit à la demande et la demande est journalisée. */
  protected telechargerMesDonnees(): void {
    this.export.set(true);
    this.erreur.set(null);
    this.conformite.mesDonnees().subscribe({
      next: (fichier) => {
        this.export.set(false);
        const adresse = URL.createObjectURL(fichier);
        const lien = document.createElement('a');
        lien.href = adresse;
        lien.download = 'mes-donnees-fasoguardian.json';
        lien.click();
        URL.revokeObjectURL(adresse);
        this.succes.set($localize`:@@reglages.donnees.succes:Vos données ont été téléchargées.`);
      },
      error: (cause: unknown) => {
        this.export.set(false);
        this.erreur.set(erreurLisible(cause).message);
      },
    });
  }

  protected ouvrir(feuille: Feuille): void {
    this.erreur.set(null);
    this.succes.set(null);
    this.codeEnvoye.set(false);
    for (const champ of [this.actuel, this.nouveau, this.telephone, this.code]) {
      champ.setValue('');
    }
    this.feuille.set(feuille);
  }

  protected changerMotDePasse(): void {
    if (!this.actuel.value || this.nouveau.value.length < 10 || !/\d/.test(this.nouveau.value)) {
      this.erreur.set($localize`:@@reglages.mdp.incomplet:Saisissez votre mot de passe actuel et un nouveau d'au moins 10 caractères avec un chiffre.`);
      return;
    }
    this.appeler(this.client.changerMotDePasse(this.actuel.value, this.nouveau.value), () =>
      this.terminer($localize`:@@reglages.mdp.fait:Mot de passe modifié.`),
    );
  }

  protected demanderCodeTelephone(): void {
    if (this.telephone.value.length !== 8) {
      this.erreur.set($localize`:@@inscription.numero.invalide:Saisissez 8 chiffres.`);
      return;
    }
    this.appeler(this.client.demanderCodeNouveauTelephone(this.telephone.value), () => this.codeEnvoye.set(true));
  }

  protected changerTelephone(): void {
    if (this.code.value.length !== 6 || !this.actuel.value) {
      this.erreur.set($localize`:@@reglages.telephone.incomplet:Saisissez le code reçu sur le nouveau numéro et votre mot de passe.`);
      return;
    }
    this.appeler(this.client.changerTelephone(this.telephone.value, this.code.value, this.actuel.value), () =>
      this.terminer($localize`:@@reglages.telephone.fait:Numéro modifié. Vos alertes arriveront sur le nouveau numéro.`),
    );
  }

  protected clore(code: string): void {
    this.appeler(this.client.clore(code), () => {
      // Compte clos : plus rien ne doit rester sur l'appareil.
      void this.copie.vider();
      void this.router.navigate(['/connexion'], { queryParams: { compteClos: 1 } });
    });
  }

  private terminer(message: string): void {
    this.feuille.set(null);
    this.succes.set(message);
  }

  private appeler(appel: ReturnType<ClientProfil['clore']>, suite: () => void): void {
    if (this.enCours()) {
      return;
    }
    this.enCours.set(true);
    this.erreur.set(null);
    appel.subscribe({
      next: () => {
        this.enCours.set(false);
        suite();
      },
      error: (cause: unknown) => {
        this.enCours.set(false);
        this.erreur.set(erreurLisible(cause).message);
      },
    });
  }
}
