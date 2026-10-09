import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ClientFamille, ElementMedical, RevisionSante, TypeElementMedical } from 'api';
import { FgBadge, FgBanniere, FgBouton, FgChamp, FgIcon, FgInterrupteur, FgSquelette } from 'ui';

import { erreurLisible } from '../commun/erreurs';

const GROUPES = ['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'] as const;

const TYPES: readonly { valeur: TypeElementMedical; libelle: string }[] = [
  { valeur: 'ALLERGIE', libelle: $localize`:@@medical.type.allergie:Allergie` },
  { valeur: 'PATHOLOGIE', libelle: $localize`:@@medical.type.pathologie:Maladie` },
  { valeur: 'TRAITEMENT', libelle: $localize`:@@medical.type.traitement:Traitement` },
  { valeur: 'AUTRE', libelle: $localize`:@@medical.type.autre:Autre` },
];

interface Ligne {
  readonly type: FormControl<TypeElementMedical>;
  readonly libelle: FormControl<string>;
  readonly critique: FormControl<boolean>;
}

/**
 * Fiche médicale (écran 32, US-PAR-011). Seuls les éléments marqués « critiques » apparaissent sur la page
 * publique du QR ; tout le reste n'est visible que des tuteurs. Chaque enregistrement crée une révision.
 */
@Component({
  selector: 'app-medical',
  imports: [DatePipe, ReactiveFormsModule, RouterLink, FgBadge, FgBanniere, FgBouton, FgChamp, FgIcon, FgInterrupteur, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="-ml-2.5 grid size-11 place-items-center rounded-md focus-visible:outline-2 focus-visible:outline-accent" [routerLink]="['/enfants', id()]" i18n-aria-label="@@etape.retour" aria-label="Retour">
      <fg-icon nom="retour" [taille]="22" />
    </a>
    <h1 class="m-0 text-titre-ecran font-bold tracking-tight" i18n="@@medical.titre">Fiche médicale</h1>
    <p class="m-0 text-label text-text-2" i18n="@@medical.texte">Activez « critique » pour qu'une information apparaisse sur la page QR. Le reste n'est visible que de vous.</p>

    @if (charge()) {
      <form class="contents" (submit)="$event.preventDefault(); enregistrer()">
        <div class="flex flex-col gap-3 rounded-lg border border-line bg-surface p-4">
          <label class="flex flex-col gap-1.5 text-label font-semibold" for="groupe">
            <ng-container i18n="@@medical.groupe">Groupe sanguin</ng-container>
            <select id="groupe" class="h-13 rounded-md border-(length:--border-width-trait) border-line-strong bg-bg px-3 text-saisie font-medium text-text" [formControl]="groupe">
              <option value="" i18n="@@medical.groupe.inconnu">Non renseigné</option>
              @for (g of groupes; track g) {
                <option [value]="g">{{ g }}</option>
              }
            </select>
          </label>
          <fg-switch [formControl]="groupeSurQr" i18n-libelle="@@medical.groupe.qr" libelle="Afficher le groupe sanguin sur la page QR">
            <span class="text-label font-medium" i18n="@@medical.groupe.qr">Afficher le groupe sanguin sur la page QR</span>
          </fg-switch>
        </div>

        @for (ligne of lignes(); track ligne; let i = $index) {
          <fieldset class="m-0 flex flex-col gap-3 rounded-lg border border-line bg-surface p-4">
            <legend class="sr-only" i18n="@@medical.element">Information médicale {{ i + 1 }}</legend>
            <div class="flex items-center justify-between gap-2">
              <select class="h-11 rounded-md border-(length:--border-width-trait) border-line-strong bg-bg px-3 text-label font-semibold text-text" [formControl]="ligne.type" i18n-aria-label="@@medical.type" aria-label="Type">
                @for (t of types; track t.valeur) {
                  <option [value]="t.valeur">{{ t.libelle }}</option>
                }
              </select>
              <button type="button" class="grid size-11 place-items-center rounded-md text-text-2 focus-visible:outline-2 focus-visible:outline-accent" i18n-aria-label="@@medical.retirer" aria-label="Retirer cette information" (click)="retirer(i)">
                <fg-icon nom="fermer" [taille]="20" />
              </button>
            </div>
            <fg-input i18n-libelle="@@medical.libelle" libelle="Information" [longueurMax]="120" [formControl]="ligne.libelle" />
            <fg-switch [formControl]="ligne.critique" i18n-libelle="@@medical.critique" libelle="Critique · visible sur la page QR">
              <span class="text-label font-medium" i18n="@@medical.critique">Critique · visible sur la page QR</span>
            </fg-switch>
          </fieldset>
        }
        <button fg-button variante="secondary" type="button" [disabled]="lignes().length >= 20" (click)="ajouter()" i18n="@@medical.ajouter">Ajouter une information</button>

        @if (erreur(); as message) {
          <fg-banner ton="erreur">{{ message }}</fg-banner>
        }
        @if (enregistre()) {
          <fg-banner ton="info" icone="valider" i18n="@@medical.enregistre">Fiche médicale enregistrée.</fg-banner>
        }
        <button fg-button taille="lg" type="submit" [chargement]="enCours()" i18n="@@commun.enregistrer">Enregistrer</button>
      </form>
      <a class="self-start py-2 text-label font-semibold text-accent" [routerLink]="['/enfants', id(), 'qr']" i18n="@@medical.voirQr">Voir la page QR</a>

      @if (revisions().length > 0) {
        <section class="flex flex-col gap-2">
          <h2 class="m-0 text-h3 font-semibold" i18n="@@medical.journal">Journal des modifications</h2>
          <ul class="m-0 flex list-none flex-col gap-1.5 p-0">
            @for (revision of revisions(); track $index) {
              <li class="flex items-center justify-between gap-3 text-label text-text-2">
                <span>{{ revision.modifieLe | date: 'd MMM y à HH:mm' }}</span>
                <fg-badge ton="neutre" i18n="@@medical.revision">{{ revision.nombreElements }} information(s), dont {{ revision.nombreCritiques }} critique(s)</fg-badge>
              </li>
            }
          </ul>
        </section>
      }
    } @else if (erreur(); as message) {
      <fg-banner ton="erreur">{{ message }}</fg-banner>
    } @else {
      <fg-skeleton forme="carte" />
      <fg-skeleton forme="carte" />
    }
  `,
  host: { class: 'mx-auto flex min-h-dvh w-full max-w-md flex-col gap-4 px-5 pt-6 pb-7 lg:mx-0 lg:max-w-2xl lg:px-10 lg:pt-10' },
})
export class Medical {
  readonly id = input.required<string>();

  private readonly client = inject(ClientFamille);

  protected readonly groupes = GROUPES;
  protected readonly types = TYPES;
  protected readonly groupe = new FormControl('', { nonNullable: true });
  protected readonly groupeSurQr = new FormControl(false, { nonNullable: true });
  protected readonly lignes = signal<Ligne[]>([]);
  protected readonly revisions = signal<RevisionSante[]>([]);
  protected readonly charge = signal(false);
  protected readonly enCours = signal(false);
  protected readonly enregistre = signal(false);
  protected readonly erreur = signal<string | null>(null);

  constructor() {
    effect(() => this.charger(this.id()));
  }

  protected ajouter(): void {
    this.enregistre.set(false);
    this.lignes.update((lignes) => [...lignes, ligne({ type: 'ALLERGIE', libelle: '', critique: false })]);
  }

  protected retirer(index: number): void {
    this.enregistre.set(false);
    this.lignes.update((lignes) => lignes.filter((_, i) => i !== index));
  }

  protected enregistrer(): void {
    const elements: ElementMedical[] = this.lignes().map((l) => ({
      type: l.type.value,
      libelle: l.libelle.value.trim(),
      critique: l.critique.value,
    }));
    if (elements.some((element) => !element.libelle)) {
      this.erreur.set($localize`:@@medical.incomplet:Renseignez chaque information, ou retirez les lignes vides.`);
      return;
    }
    this.enCours.set(true);
    this.erreur.set(null);
    this.enregistre.set(false);
    this.client
      .enregistrerSante(this.id(), { groupeSanguin: this.groupe.value || null, groupeSanguinSurQr: this.groupeSurQr.value, elements })
      .subscribe({
        next: () => {
          this.enCours.set(false);
          this.enregistre.set(true);
          this.client.revisionsSante(this.id()).subscribe({ next: (r) => this.revisions.set(r), error: () => undefined });
        },
        error: (cause: unknown) => {
          this.enCours.set(false);
          this.erreur.set(erreurLisible(cause).message);
        },
      });
  }

  private charger(id: string): void {
    this.charge.set(false);
    this.erreur.set(null);
    this.client.sante(id).subscribe({
      next: (fiche) => {
        this.groupe.setValue(fiche.groupeSanguin ?? '');
        this.groupeSurQr.setValue(fiche.groupeSanguinSurQr);
        this.lignes.set(fiche.elements.map(ligne));
        this.charge.set(true);
      },
      error: (cause: unknown) => this.erreur.set(erreurLisible(cause).message),
    });
    this.client.revisionsSante(id).subscribe({ next: (r) => this.revisions.set(r), error: () => undefined });
  }
}

function ligne(element: ElementMedical): Ligne {
  return {
    type: new FormControl(element.type, { nonNullable: true }),
    libelle: new FormControl(element.libelle, { nonNullable: true }),
    critique: new FormControl(element.critique, { nonNullable: true }),
  };
}
