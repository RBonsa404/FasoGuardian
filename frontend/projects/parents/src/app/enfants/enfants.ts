import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { catchError, forkJoin, map, of } from 'rxjs';

import { ClientBracelet, ClientFamille, ClientZones, FicheEnfant, RevisionEnfant, SafeZone, Situation } from 'api';
import { FgBanniere, FgBouton, FgChamp, FgFeuille, FgIcon, FgSquelette } from 'ui';

import { erreurLisible } from '../commun/erreurs';
import { heure, ilYA } from '../commun/temps';
import { EtatVerification, allegerPhoto } from '../verification/etat-verification';
import { DePipe } from '../commun/francais';

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
  prenom: $localize`:@@enfant.champ.prenom:prénom modifié`,
  nom: $localize`:@@enfant.champ.nom:nom modifié`,
  ecole: $localize`:@@enfant.champ.ecole:école modifiée`,
  quartier: $localize`:@@enfant.champ.quartier:quartier modifié`,
  tailleCm: $localize`:@@enfant.champ.taille:taille modifiée`,
  photo: $localize`:@@enfant.champ.photo:photo modifiée`,
  signesDistinctifs: $localize`:@@enfant.champ.signes:signes distinctifs modifiés`,
  // Traces antérieures au détail par champ.
  profil: $localize`:@@enfant.champ.profil:école, quartier ou signes distinctifs modifiés`,
};

const JOUR_MOIS = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'short' });
const JOUR_MOIS_ANNEE = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'short', year: 'numeric' });

/** État d'un enfant tel que la carte de l'écran 17 le résume. */
interface Resume {
  readonly texte: string;
  readonly ton: 'succes' | 'accent' | 'attention' | 'neutre';
  readonly details: string;
}

const ANNEAUX: Record<Resume['ton'], string> = {
  succes: 'border-success',
  accent: 'border-accent',
  attention: 'border-attention',
  neutre: 'border-line-strong',
};

const TEXTES: Record<Resume['ton'], string> = {
  succes: 'text-success',
  accent: 'text-accent',
  attention: 'text-attention-text',
  neutre: 'text-text-2',
};

/** Résumé de l'état de l'enfant : où il est par rapport à ses Safe Zones, et la fraîcheur des données. */
export function resumer(situation: Situation | null | undefined, zones: readonly SafeZone[], maintenant = new Date()): Resume {
  if (situation === undefined) {
    return { texte: '', ton: 'neutre', details: '' };
  }
  if (situation === null) {
    return { texte: $localize`:@@enfants.sansBracelet:Aucun bracelet associé`, ton: 'neutre', details: '' };
  }
  const position = situation.position;
  const etat = situation.etat;
  const details = [
    position ? heure(position.mesureeLe) : null,
    position ? ilYA(position.mesureeLe, maintenant) : null,
    etat?.reseau ?? null,
    etat?.batterie != null ? `${etat.batterie} %` : null,
  ]
    .filter(Boolean)
    .join(' · ');
  if (!position) {
    return { texte: $localize`:@@enfants.attente:En attente de la première position`, ton: 'neutre', details };
  }
  const sortie = zones.find((z) => z.sortieEnCours);
  if (sortie) {
    return { texte: $localize`:@@enfants.sortie:Hors de « ${sortie.nom}:zone: »`, ton: 'attention', details };
  }
  const dedans = zones.find((z) => z.enfantDedans);
  if (dedans) {
    return { texte: $localize`:@@enfants.dedans:Dans « ${dedans.nom}:zone: »`, ton: 'succes', details };
  }
  if (etat?.enMouvement) {
    return { texte: $localize`:@@enfants.mouvement:En déplacement`, ton: 'accent', details };
  }
  return { texte: $localize`:@@enfants.horsZones:Hors des Safe Zones`, ton: 'accent', details };
}

/**
 * Tous mes enfants (écran 17, US-PAR-004) : un enfant par lien de tutelle actif, avec sa photo, où il se
 * trouve par rapport à ses Safe Zones et la fraîcheur de sa position ; l'ajout d'un enfant ouvre une
 * nouvelle vérification de lien.
 */
@Component({
  selector: 'app-enfants',
  imports: [RouterLink, FgBanniere, FgBouton, FgIcon, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="-ml-2.5 grid size-11 place-items-center rounded-md focus-visible:outline-2 focus-visible:outline-accent lg:hidden" routerLink="/" i18n-aria-label="@@etape.retour" aria-label="Retour">
      <fg-icon nom="retour" [taille]="22" />
    </a>
    <h1 class="m-0 text-titre-ecran font-bold tracking-tight" i18n="@@enfants.titre">Mes enfants</h1>
    @if (enfants(); as liste) {
      @for (enfant of liste; track enfant.id) {
        <a class="flex items-center gap-3 rounded-lg border border-line bg-surface p-4 focus-visible:outline-2 focus-visible:outline-accent" [routerLink]="['/enfants', enfant.id]">
          @if (photos()[enfant.id]; as url) {
            <img class="size-12 flex-none rounded-full border-[3px] object-cover" [class]="anneau(enfant)" [src]="url" alt="" />
          } @else {
            <span class="grid size-12 flex-none place-items-center rounded-full border-[3px] bg-surface-2 font-display text-body-lg font-bold" [class]="anneau(enfant)" aria-hidden="true">{{ enfant.prenom.charAt(0) }}</span>
          }
          <span class="flex min-w-0 flex-1 flex-col gap-0.5">
            <strong class="truncate font-display text-body-lg font-semibold" i18n="@@enfants.titreCarte">{{ enfant.prenom }} · {{ ageDe(enfant) }} ans</strong>
            @if (resume(enfant); as r) {
              @if (r.texte) {
                <span class="truncate text-label font-medium" [class]="couleur(r)">{{ r.texte }}</span>
              }
              @if (r.details) {
                <span class="truncate text-caption text-text-2 tabular-nums">{{ r.details }}</span>
              }
            }
          </span>
        </a>
      } @empty {
        <p class="m-0 rounded-lg border border-line bg-surface p-5 text-body text-text-2" i18n="@@enfants.vide">
          Aucun enfant n'est encore rattaché à votre compte. La fiche de votre enfant apparaîtra ici dès que votre dossier de vérification sera validé.
        </p>
        <a class="self-start text-label font-semibold text-accent" routerLink="/verification" i18n="@@accueil.suivre">Suivre ma vérification</a>
      }
      @if (liste.length > 0) {
        <button type="button" class="flex min-h-14 items-center justify-center gap-2 rounded-lg border border-dashed border-line-strong text-body font-semibold text-text-2 focus-visible:outline-2 focus-visible:outline-accent" (click)="ajouter()" i18n="@@enfants.ajouter">+ Ajouter un enfant</button>
        <p class="m-0 text-center text-label text-text-2" i18n="@@enfants.ajouter.texte">Chaque enfant passe par sa propre vérification du lien de filiation.</p>
      }
    } @else if (erreur(); as message) {
      <fg-banner ton="erreur">{{ message }}</fg-banner>
      <button fg-button variante="secondary" type="button" class="self-start" (click)="charger()" i18n="@@commun.reessayer">Réessayer</button>
    } @else {
      <fg-skeleton forme="carte" />
    }
  `,
  host: { class: 'mx-auto flex min-h-dvh w-full max-w-md flex-col gap-4 px-5 pt-6 pb-7 lg:max-w-3xl lg:gap-5 lg:px-10 lg:pt-10' },
})
export class Enfants {
  private readonly client = inject(ClientFamille);
  private readonly bracelets = inject(ClientBracelet);
  private readonly clientZones = inject(ClientZones);
  private readonly router = inject(Router);
  private readonly etat = inject(EtatVerification);

  protected readonly enfants = signal<FicheEnfant[] | null>(null);
  protected readonly erreur = signal<string | null>(null);
  /** Situation de chaque enfant : `undefined` en cours de chargement, `null` sans bracelet. */
  protected readonly situations = signal<Record<string, { situation: Situation | null; zones: readonly SafeZone[] }>>({});
  protected readonly photos = signal<Record<string, string>>({});

  constructor() {
    this.charger();
    inject(DestroyRef).onDestroy(() => Object.values(this.photos()).forEach((url) => URL.revokeObjectURL(url)));
  }

  protected ageDe(enfant: FicheEnfant): number {
    return age(enfant.dateNaissance);
  }

  protected resume(enfant: FicheEnfant): Resume {
    const suivi = this.situations()[enfant.id];
    return resumer(suivi ? suivi.situation : undefined, suivi?.zones ?? []);
  }

  protected anneau(enfant: FicheEnfant): string {
    return ANNEAUX[this.resume(enfant).ton];
  }

  protected couleur(resume: Resume): string {
    return TEXTES[resume.ton];
  }

  protected ajouter(): void {
    this.etat.effacer();
    void this.router.navigate(['/verification', 'mode']);
  }

  protected charger(): void {
    this.erreur.set(null);
    this.client.mesEnfants().subscribe({
      next: (enfants) => {
        this.enfants.set(enfants);
        enfants.forEach((enfant) => this.suivre(enfant));
      },
      error: (cause: unknown) => this.erreur.set(erreurLisible(cause).message),
    });
  }

  /** Chaque carte se complète à son rythme ; un enfant sans bracelet n'a ni position ni zone. */
  private suivre(enfant: FicheEnfant): void {
    forkJoin({
      situation: this.bracelets.situation(enfant.id).pipe(catchError(() => of(null))),
      zones: this.clientZones.zones(enfant.id).pipe(
        map((reponse) => reponse.zones),
        catchError(() => of([] as SafeZone[])),
      ),
    }).subscribe((suivi) => this.situations.update((tous) => ({ ...tous, [enfant.id]: suivi })));
    if (enfant.photoModifieeLe) {
      this.client.photo(enfant.id).subscribe({
        next: (image) => this.photos.update((toutes) => ({ ...toutes, [enfant.id]: URL.createObjectURL(image) })),
        error: () => undefined,
      });
    }
  }
}

/** Fiche enfant (écran 31, US-PAR-004) : identité, éléments de reconnaissance, historique des modifications. */
@Component({
  selector: 'app-fiche-enfant',
  imports: [DePipe, DatePipe, ReactiveFormsModule, RouterLink, FgBanniere, FgBouton, FgChamp, FgFeuille, FgIcon, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './fiche-enfant.html',
  host: { class: 'mx-auto flex min-h-dvh w-full max-w-md flex-col gap-4 px-5 pt-6 pb-7 lg:max-w-3xl lg:gap-5 lg:px-10 lg:pt-10' },
})
export class FicheEnfantEcran {
  readonly id = input.required<string>();

  private readonly client = inject(ClientFamille);
  private readonly bracelets = inject(ClientBracelet);

  protected readonly enfant = signal<FicheEnfant | null>(null);
  protected readonly historique = signal<RevisionEnfant[]>([]);
  protected readonly numeroBracelet = signal<string | null>(null);
  /** Adresse locale de la photo chargée : la photo ne passe que par l'API authentifiée, jamais par une URL publique. */
  protected readonly photo = signal<string | null>(null);
  protected readonly envoiPhoto = signal(false);
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
    // Le chargement lit et écrit la photo : seul l'identifiant de l'enfant doit relancer l'effet.
    effect(() => {
      const id = this.id();
      untracked(() => this.charger(id));
    });
    inject(DestroyRef).onDestroy(() => this.oublierPhoto());
  }

  protected async choisirPhoto(evenement: Event): Promise<void> {
    const champ = evenement.target as HTMLInputElement;
    const fichier = champ.files?.[0];
    champ.value = '';
    if (!fichier) {
      return;
    }
    this.envoiPhoto.set(true);
    this.erreur.set(null);
    this.client.definirPhoto(this.id(), await allegerPhoto(fichier)).subscribe({
      next: (enfant) => this.apresPhoto(enfant),
      error: (cause: unknown) => {
        this.envoiPhoto.set(false);
        this.erreur.set(erreurLisible(cause).message);
      },
    });
  }

  protected retirerPhoto(): void {
    this.envoiPhoto.set(true);
    this.erreur.set(null);
    this.client.retirerPhoto(this.id()).subscribe({
      next: (enfant) => this.apresPhoto(enfant),
      error: (cause: unknown) => {
        this.envoiPhoto.set(false);
        this.erreur.set(erreurLisible(cause).message);
      },
    });
  }

  private apresPhoto(enfant: FicheEnfant): void {
    this.envoiPhoto.set(false);
    this.enfant.set(enfant);
    this.chargerPhoto(enfant);
    this.client.historique(this.id()).subscribe({ next: (h) => this.historique.set(h), error: () => undefined });
  }

  private chargerPhoto(enfant: FicheEnfant): void {
    this.oublierPhoto();
    if (enfant.photoModifieeLe) {
      this.client.photo(enfant.id).subscribe({ next: (image) => this.photo.set(URL.createObjectURL(image)), error: () => undefined });
    }
  }

  private oublierPhoto(): void {
    const url = this.photo();
    if (url) {
      URL.revokeObjectURL(url);
      this.photo.set(null);
    }
  }

  protected libelleChamp(champ: string): string {
    return LIBELLES_CHAMPS[champ] ?? champ;
  }

  protected dateRevision(iso: string): string {
    const date = new Date(iso);
    return (date.getFullYear() === new Date().getFullYear() ? JOUR_MOIS : JOUR_MOIS_ANNEE).format(date);
  }

  /** Prénom vérifié de l'auteur ; un autre tuteur est nommé comme tel. */
  protected auteur(revision: RevisionEnfant): string {
    if (revision.parVous) {
      return revision.auteur || $localize`:@@enfant.auteur.vous:vous`;
    }
    return revision.auteur ? $localize`:@@enfant.auteur.tuteur:${revision.auteur}:prenom: (tuteur)` : $localize`:@@enfant.auteur.autre:un autre tuteur`;
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
      next: (enfant) => {
        this.enfant.set(enfant);
        this.chargerPhoto(enfant);
      },
      error: (cause: unknown) => this.erreur.set(erreurLisible(cause).message),
    });
    this.client.historique(id).subscribe({ next: (h) => this.historique.set(h), error: () => undefined });
    this.numeroBracelet.set(null);
    this.bracelets.bracelet(id).subscribe({ next: (b) => this.numeroBracelet.set(b.numeroSerie), error: () => undefined });
  }
}
