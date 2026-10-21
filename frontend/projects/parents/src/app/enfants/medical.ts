import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Subscription, merge } from 'rxjs';

import { ClientFamille, ElementMedical, FicheSante, RevisionSante, TypeElementMedical } from 'api';
import { FgBadge, FgBanniere, FgBouton, FgChamp, FgIcon, FgInterrupteur, FgSquelette } from 'ui';

import { erreurLisible } from '../commun/erreurs';

const GROUPES = ['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'] as const;

const TYPES: readonly { valeur: TypeElementMedical; libelle: string }[] = [
  { valeur: 'ALLERGIE', libelle: $localize`:@@medical.type.allergie:Allergie` },
  { valeur: 'PATHOLOGIE', libelle: $localize`:@@medical.type.pathologie:Maladie chronique` },
  { valeur: 'TRAITEMENT', libelle: $localize`:@@medical.type.traitement:Traitement` },
  { valeur: 'AUTRE', libelle: $localize`:@@medical.type.autre:Autre` },
];

const DATE_REVISION = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'short', year: 'numeric' });

interface Ligne {
  readonly type: FormControl<TypeElementMedical>;
  readonly libelle: FormControl<string>;
  readonly critique: FormControl<boolean>;
}

/**
 * Fiche médicale (écran 32, US-PAR-011). Seuls les éléments marqués « critiques » apparaissent sur la page
 * publique du QR ; tout le reste n'est visible que des tuteurs. En lecture, chaque bascule critique / privé
 * s'enregistre aussitôt ; la modification des informations passe par le formulaire. Chaque enregistrement
 * crée une révision.
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

    @if (!charge()) {
      @if (erreur(); as message) {
        <fg-banner ton="erreur">{{ message }}</fg-banner>
      } @else {
        <fg-skeleton forme="carte" />
        <fg-skeleton forme="carte" />
      }
    } @else if (!edition()) {
      <ul class="m-0 flex list-none flex-col gap-2.5 p-0">
        @for (ligne of lignes(); track ligne) {
          <li class="flex items-center gap-3 rounded-lg border border-line bg-surface px-4 py-3">
            <span class="flex min-w-0 flex-1 flex-col gap-0.5">
              <span class="text-caption text-text-2">{{ libelleType(ligne.type.value) }}</span>
              <span class="text-body font-semibold break-words">{{ ligne.libelle.value }}</span>
            </span>
            <span class="flex flex-none flex-col items-center">
              <fg-switch [formControl]="ligne.critique" [libelle]="libelleBascule(ligne)" />
              <span class="text-caption font-semibold text-text-2">{{ ligne.critique.value ? critique : prive }}</span>
            </span>
          </li>
        }
        @if (groupe.value) {
          <li class="flex items-center gap-3 rounded-lg border border-line bg-surface px-4 py-3">
            <span class="flex min-w-0 flex-1 flex-col gap-0.5">
              <span class="text-caption text-text-2" i18n="@@medical.groupe">Groupe sanguin</span>
              <span class="text-body font-semibold">{{ groupe.value }}</span>
            </span>
            <span class="flex flex-none flex-col items-center">
              <fg-switch [formControl]="groupeSurQr" i18n-libelle="@@medical.groupe.qr" libelle="Afficher le groupe sanguin sur la page QR" />
              <span class="text-caption font-semibold text-text-2">{{ groupeSurQr.value ? critique : prive }}</span>
            </span>
          </li>
        }
        @if (lignes().length === 0 && !groupe.value) {
          <li class="rounded-lg border border-line bg-surface p-4 text-body text-text-2" i18n="@@medical.vide">
            Aucune information médicale pour l'instant. Une allergie ou un traitement à connaître en urgence ? Ajoutez-le ici.
          </li>
        }
      </ul>

      @if (derniere(); as revision) {
        <p class="m-0 text-label text-text-2">
          <ng-container i18n="@@medical.derniere">Dernière révision : {{ dateRevision(revision.modifieLe) }} par {{ auteur(revision) }}</ng-container>
          ·
          <button type="button" class="font-semibold text-accent focus-visible:outline-2 focus-visible:outline-accent" [attr.aria-expanded]="journalOuvert()" (click)="journalOuvert.set(!journalOuvert())" i18n="@@medical.journal.lien">journal des révisions</button>
        </p>
        @if (journalOuvert()) {
          <ul class="m-0 flex list-none flex-col gap-1.5 rounded-lg bg-surface-2 p-3" i18n-aria-label="@@medical.journal" aria-label="Journal des révisions">
            @for (r of revisions(); track $index) {
              <li class="flex items-center justify-between gap-3 text-label text-text-2">
                <span>{{ r.modifieLe | date: 'd MMM y à HH:mm' }} · {{ auteur(r) }}</span>
                <fg-badge ton="neutre" i18n="@@medical.revision">{{ r.nombreElements }} information(s), dont {{ r.nombreCritiques }} critique(s)</fg-badge>
              </li>
            }
          </ul>
        }
      }

      @if (erreur(); as message) {
        <fg-banner ton="erreur">{{ message }}</fg-banner>
      }
      @if (enregistre()) {
        <fg-banner ton="info" icone="valider" i18n="@@medical.enregistre">Fiche médicale enregistrée.</fg-banner>
      }
      <button fg-button variante="secondary" type="button" [disabled]="enCours()" (click)="modifier()" i18n="@@medical.modifier">Modifier les informations</button>
      <button fg-button class="mt-auto" taille="lg" type="button" (click)="voirQr()" i18n="@@medical.voirQr">Voir la page QR</button>
    } @else {
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
        <button fg-button taille="lg" type="submit" [chargement]="enCours()" i18n="@@commun.enregistrer">Enregistrer</button>
        <button fg-button variante="ghost" type="button" [disabled]="enCours()" (click)="annuler()" i18n="@@commun.annuler">Annuler</button>
      </form>
    }
  `,
  host: { class: 'mx-auto flex min-h-dvh w-full max-w-md flex-col gap-4 px-5 pt-6 pb-7 lg:max-w-3xl lg:gap-5 lg:px-10 lg:pt-10' },
})
export class Medical {
  readonly id = input.required<string>();

  private readonly client = inject(ClientFamille);
  private readonly router = inject(Router);

  protected readonly groupes = GROUPES;
  protected readonly types = TYPES;
  protected readonly critique = $localize`:@@medical.etat.critique:Critique`;
  protected readonly prive = $localize`:@@medical.etat.prive:Privé`;
  protected readonly groupe = new FormControl('', { nonNullable: true });
  protected readonly groupeSurQr = new FormControl(false, { nonNullable: true });
  protected readonly lignes = signal<Ligne[]>([]);
  protected readonly revisions = signal<RevisionSante[]>([]);
  protected readonly derniere = computed(() => this.revisions()[0] ?? null);
  protected readonly charge = signal(false);
  protected readonly edition = signal(false);
  protected readonly journalOuvert = signal(false);
  protected readonly enCours = signal(false);
  protected readonly enregistre = signal(false);
  protected readonly erreur = signal<string | null>(null);

  /** Dernier état enregistré : point de retour d'une modification annulée ou d'une bascule refusée. */
  private fiche: FicheSante | null = null;
  private bascules: Subscription | null = null;

  constructor() {
    effect(() => this.charger(this.id()));
    inject(DestroyRef).onDestroy(() => this.bascules?.unsubscribe());
  }

  protected libelleType(type: TypeElementMedical): string {
    return TYPES.find((t) => t.valeur === type)?.libelle ?? type;
  }

  protected libelleBascule(ligne: Ligne): string {
    return $localize`:@@medical.bascule:${ligne.libelle.value}:information: · critique, visible sur la page QR`;
  }

  protected dateRevision(iso: string): string {
    return DATE_REVISION.format(new Date(iso));
  }

  protected auteur(revision: RevisionSante): string {
    if (revision.parVous) {
      return revision.auteur || $localize`:@@enfant.auteur.vous:vous`;
    }
    return revision.auteur ? $localize`:@@enfant.auteur.tuteur:${revision.auteur}:prenom: (tuteur)` : $localize`:@@enfant.auteur.autre:un autre tuteur`;
  }

  protected modifier(): void {
    this.erreur.set(null);
    this.enregistre.set(false);
    this.edition.set(true);
  }

  protected annuler(): void {
    this.erreur.set(null);
    if (this.fiche) {
      this.afficher(this.fiche);
    }
    this.edition.set(false);
  }

  protected voirQr(): void {
    void this.router.navigate(['/enfants', this.id(), 'qr']);
  }

  protected ajouter(): void {
    this.lignes.update((lignes) => [...lignes, ligne({ type: 'ALLERGIE', libelle: '', critique: false })]);
  }

  protected retirer(index: number): void {
    this.lignes.update((lignes) => lignes.filter((_, i) => i !== index));
  }

  protected enregistrer(): void {
    const elements = this.saisie();
    if (elements.some((element) => !element.libelle)) {
      this.erreur.set($localize`:@@medical.incomplet:Renseignez chaque information, ou retirez les lignes vides.`);
      return;
    }
    this.envoyer(elements);
  }

  private saisie(): ElementMedical[] {
    return this.lignes().map((l) => ({ type: l.type.value, libelle: l.libelle.value.trim(), critique: l.critique.value }));
  }

  private envoyer(elements: ElementMedical[]): void {
    this.enCours.set(true);
    this.erreur.set(null);
    this.enregistre.set(false);
    this.client
      .enregistrerSante(this.id(), { groupeSanguin: this.groupe.value || null, groupeSanguinSurQr: this.groupeSurQr.value, elements })
      .subscribe({
        next: (fiche) => {
          this.enCours.set(false);
          this.enregistre.set(true);
          this.afficher(fiche);
          this.edition.set(false);
          this.client.revisionsSante(this.id()).subscribe({ next: (r) => this.revisions.set(r), error: () => undefined });
        },
        error: (cause: unknown) => {
          this.enCours.set(false);
          this.erreur.set(erreurLisible(cause).message);
          // Une bascule refusée reprend sa position enregistrée ; une saisie refusée reste à corriger.
          if (!this.edition() && this.fiche) {
            this.afficher(this.fiche);
          }
        },
      });
  }

  private afficher(fiche: FicheSante): void {
    this.fiche = fiche;
    this.groupe.setValue(fiche.groupeSanguin ?? '');
    this.groupeSurQr.setValue(fiche.groupeSanguinSurQr, { emitEvent: false });
    const lignes = fiche.elements.map(ligne);
    this.lignes.set(lignes);
    this.bascules?.unsubscribe();
    // En lecture, une bascule s'enregistre aussitôt ; en modification, elle attend « Enregistrer ».
    this.bascules = merge(this.groupeSurQr.valueChanges, ...lignes.map((l) => l.critique.valueChanges)).subscribe(() => {
      if (!this.edition()) {
        this.envoyer(this.saisie());
      }
    });
  }

  private charger(id: string): void {
    this.charge.set(false);
    this.edition.set(false);
    this.erreur.set(null);
    this.client.sante(id).subscribe({
      next: (fiche) => {
        this.afficher(fiche);
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
