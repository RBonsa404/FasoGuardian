import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ClientFamille, FicheEnfant, RevisionEnfant } from 'api';
import { FgBanniere, FgBouton, FgChamp, FgFeuille, FgIcon, FgSquelette } from 'ui';

import { erreurLisible } from '../commun/erreurs';

/** Âge en années révolues à partir d'une date ISO. */
export function age(dateNaissance: string, aujourdhui = new Date()): number {
  const naissance = new Date(dateNaissance);
  const ans = aujourdhui.getFullYear() - naissance.getFullYear();
  const anniversairePasse =
    aujourdhui.getMonth() > naissance.getMonth() ||
    (aujourdhui.getMonth() === naissance.getMonth() && aujourdhui.getDate() >= naissance.getDate());
  return anniversairePasse ? ans : ans - 1;
}

const LIBELLES_CHAMPS: Record<string, string> = {
  prenom: $localize`:@@enfant.champ.prenom:prénom`,
  nom: $localize`:@@enfant.champ.nom:nom`,
  profil: $localize`:@@enfant.champ.profil:école, quartier ou signes distinctifs`,
};

/** Tous mes enfants (écran 17, US-PAR-003) : un enfant par lien de tutelle actif. */
@Component({
  selector: 'app-enfants',
  imports: [RouterLink, FgBanniere, FgBouton, FgIcon, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="-ml-2.5 grid size-11 place-items-center rounded-md focus-visible:outline-2 focus-visible:outline-accent" routerLink="/" i18n-aria-label="@@etape.retour" aria-label="Retour">
      <fg-icon nom="retour" [taille]="22" />
    </a>
    <h1 class="m-0 text-titre-ecran font-bold tracking-tight" i18n="@@enfants.titre">Mes enfants</h1>
    @if (enfants(); as liste) {
      @for (enfant of liste; track enfant.id) {
        <a class="flex items-center gap-3 rounded-lg border border-line bg-surface p-4 focus-visible:outline-2 focus-visible:outline-accent" [routerLink]="['/enfants', enfant.id]">
          <span class="grid size-12 flex-none place-items-center rounded-full bg-surface-2 font-display text-body-lg font-bold" aria-hidden="true">{{ enfant.prenom.charAt(0) }}</span>
          <span class="flex min-w-0 flex-1 flex-col gap-0.5">
            <strong class="truncate font-display text-body-lg font-semibold">{{ enfant.prenom }} {{ enfant.nom }}</strong>
            <span class="text-label text-text-2" i18n="@@enfants.age">{{ ageDe(enfant) }} ans</span>
          </span>
        </a>
      } @empty {
        <p class="m-0 rounded-lg border border-line bg-surface p-5 text-body text-text-2" i18n="@@enfants.vide">
          Aucun enfant n'est encore rattaché à votre compte. La fiche de votre enfant apparaîtra ici dès que votre dossier de vérification sera validé.
        </p>
        <a class="self-start text-label font-semibold text-accent" routerLink="/verification" i18n="@@accueil.suivre">Suivre ma vérification</a>
      }
    } @else if (erreur(); as message) {
      <fg-banner ton="erreur">{{ message }}</fg-banner>
      <button fg-button variante="secondary" type="button" class="self-start" (click)="charger()" i18n="@@commun.reessayer">Réessayer</button>
    } @else {
      <fg-skeleton forme="carte" />
    }
  `,
  host: { class: 'mx-auto flex min-h-dvh w-full max-w-md flex-col gap-4 px-5 pt-6 pb-7 lg:mx-0 lg:max-w-2xl lg:px-10 lg:pt-10' },
})
export class Enfants {
  private readonly client = inject(ClientFamille);

  protected readonly enfants = signal<FicheEnfant[] | null>(null);
  protected readonly erreur = signal<string | null>(null);

  constructor() {
    this.charger();
  }

  protected ageDe(enfant: FicheEnfant): number {
    return age(enfant.dateNaissance);
  }

  protected charger(): void {
    this.erreur.set(null);
    this.client.mesEnfants().subscribe({
      next: (enfants) => this.enfants.set(enfants),
      error: (cause: unknown) => this.erreur.set(erreurLisible(cause).message),
    });
  }
}

/** Fiche enfant (écran 31, US-PAR-004) : identité, éléments de reconnaissance, historique des modifications. */
@Component({
  selector: 'app-fiche-enfant',
  imports: [DatePipe, ReactiveFormsModule, RouterLink, FgBanniere, FgBouton, FgChamp, FgFeuille, FgIcon, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './fiche-enfant.html',
  host: { class: 'mx-auto flex min-h-dvh w-full max-w-md flex-col gap-4 px-5 pt-6 pb-7 lg:mx-0 lg:max-w-2xl lg:px-10 lg:pt-10' },
})
export class FicheEnfantEcran {
  readonly id = input.required<string>();

  private readonly client = inject(ClientFamille);

  protected readonly enfant = signal<FicheEnfant | null>(null);
  protected readonly historique = signal<RevisionEnfant[]>([]);
  protected readonly erreur = signal<string | null>(null);
  protected readonly edition = signal(false);
  protected readonly enCours = signal(false);
  protected readonly age = computed(() => (this.enfant() ? age(this.enfant()!.dateNaissance) : 0));
  protected readonly lignes = computed(() => {
    const profil = this.enfant()?.profil;
    return [
      { cle: $localize`:@@enfant.ecole:École`, valeur: profil?.ecole },
      { cle: $localize`:@@enfant.quartier:Quartier`, valeur: profil?.quartier },
      { cle: $localize`:@@enfant.taille:Taille`, valeur: profil?.tailleCm ? `${(profil.tailleCm / 100).toFixed(2).replace('.', ',')} m` : null },
      { cle: $localize`:@@enfant.signes:Signes distinctifs`, valeur: profil?.signesDistinctifs },
    ];
  });

  protected readonly formulaire = new FormGroup({
    prenom: new FormControl('', { nonNullable: true }),
    nom: new FormControl('', { nonNullable: true }),
    ecole: new FormControl('', { nonNullable: true }),
    quartier: new FormControl('', { nonNullable: true }),
    tailleCm: new FormControl('', { nonNullable: true }),
    signesDistinctifs: new FormControl('', { nonNullable: true }),
  });

  constructor() {
    effect(() => this.charger(this.id()));
  }

  protected libelleChamp(champ: string): string {
    return LIBELLES_CHAMPS[champ] ?? champ;
  }

  protected modifier(): void {
    const enfant = this.enfant()!;
    this.formulaire.setValue({
      prenom: enfant.prenom,
      nom: enfant.nom,
      ecole: enfant.profil.ecole ?? '',
      quartier: enfant.profil.quartier ?? '',
      tailleCm: enfant.profil.tailleCm ? String(enfant.profil.tailleCm) : '',
      signesDistinctifs: enfant.profil.signesDistinctifs ?? '',
    });
    this.erreur.set(null);
    this.edition.set(true);
  }

  protected enregistrer(): void {
    const saisie = this.formulaire.getRawValue();
    const taille = saisie.tailleCm.trim() ? Number(saisie.tailleCm) : null;
    if (!saisie.prenom.trim() || !saisie.nom.trim() || (taille !== null && !(taille >= 30 && taille <= 220))) {
      this.erreur.set($localize`:@@enfant.invalide:Renseignez le prénom et le nom ; la taille est comprise entre 30 et 220 cm.`);
      return;
    }
    this.enCours.set(true);
    this.erreur.set(null);
    this.client
      .modifierEnfant(this.id(), {
        prenom: saisie.prenom.trim(),
        nom: saisie.nom.trim(),
        profil: {
          ecole: saisie.ecole.trim() || null,
          quartier: saisie.quartier.trim() || null,
          tailleCm: taille,
          signesDistinctifs: saisie.signesDistinctifs.trim() || null,
        },
      })
      .subscribe({
        next: (enfant) => {
          this.enCours.set(false);
          this.enfant.set(enfant);
          this.edition.set(false);
          this.client.historique(this.id()).subscribe({ next: (h) => this.historique.set(h), error: () => undefined });
        },
        error: (cause: unknown) => {
          this.enCours.set(false);
          this.erreur.set(erreurLisible(cause).message);
        },
      });
  }

  private charger(id: string): void {
    this.enfant.set(null);
    this.erreur.set(null);
    this.client.enfant(id).subscribe({
      next: (enfant) => this.enfant.set(enfant),
      error: (cause: unknown) => this.erreur.set(erreurLisible(cause).message),
    });
    this.client.historique(id).subscribe({ next: (h) => this.historique.set(h), error: () => undefined });
  }
}
