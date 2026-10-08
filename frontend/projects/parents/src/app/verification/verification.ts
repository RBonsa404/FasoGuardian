import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { Observable, concat, defer, last, switchMap } from 'rxjs';

import { ClientKyc, DossierKycParent, TypePieceKyc } from 'api';
import { FgBadge, FgBanniere, FgBouton, FgChamp, FgIcon, FgInterrupteur, FgSquelette, NomIcone } from 'ui';

import { erreurLisible } from '../commun/erreurs';
import { Etape } from '../gabarit/etape';
import { EtatVerification, allegerPhoto } from './etat-verification';

type NomEtape = 'mode' | 'identite' | 'piece' | 'filiation' | 'recap' | 'instruction';

const SAISIE: readonly NomEtape[] = ['mode', 'identite', 'piece', 'filiation', 'recap'];

interface CarteFichier {
  readonly type: TypePieceKyc;
  readonly titre: string;
  readonly invite: string;
}

/**
 * Vérification du lien avec l'enfant (écran 11, US-PAR-001 et US-PAR-005). Une question par écran ;
 * le choix « en point d'inscription » saute les étapes photo. Le dossier n'est créé sur le serveur
 * qu'à l'envoi : rien de partiel n'y reste si le parent abandonne.
 */
@Component({
  selector: 'app-verification',
  imports: [DatePipe, NgTemplateOutlet, ReactiveFormsModule, Etape, FgBadge, FgBanniere, FgBouton, FgChamp, FgIcon, FgInterrupteur, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './verification.html',
})
export class Verification {
  readonly etape = input.required<NomEtape>();

  private readonly client = inject(ClientKyc);
  private readonly router = inject(Router);
  protected readonly etat = inject(EtatVerification);

  protected readonly identite = new FormGroup({
    nom: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(80)] }),
    prenoms: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(120)] }),
    numeroPiece: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(32)] }),
  });
  protected readonly enfant = new FormGroup({
    prenom: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(80)] }),
    nom: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(80)] }),
    dateNaissance: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });
  protected readonly tuteur = new FormControl(false, { nonNullable: true });

  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly dossier = signal<DossierKycParent | null | undefined>(undefined);

  protected readonly enLigne = computed(() => this.etat.canal() === 'EN_LIGNE');
  protected readonly etapes = computed<readonly NomEtape[]>(() => {
    if (this.etat.aCompleter()) {
      return ['piece', 'filiation', 'recap'];
    }
    return this.enLigne() ? SAISIE : ['mode', 'identite', 'filiation', 'recap'];
  });
  protected readonly numero = computed(() => this.etapes().indexOf(this.etape()) + 1);
  protected readonly total = computed(() => this.etapes().length);
  protected readonly surtitre = computed(
    () => $localize`:@@verification.surtitre:Vérification · ${this.numero()}:numero:/${this.total()}:total:`,
  );
  protected readonly justificatif = computed<CarteFichier>(() =>
    this.etat.natureLien() === 'TUTEUR'
      ? { type: 'JUGEMENT_TUTELLE', titre: $localize`:@@piece.jugement:Jugement de tutelle`, invite: $localize`:@@piece.inviteDocument:Touchez pour photographier ou choisir un PDF` }
      : { type: 'ACTE_NAISSANCE', titre: $localize`:@@piece.acte:Acte de naissance de l'enfant`, invite: $localize`:@@piece.inviteDocument:Touchez pour photographier ou choisir un PDF` },
  );
  protected readonly cartesPiece: readonly CarteFichier[] = [
    { type: 'PIECE_RECTO', titre: $localize`:@@piece.recto:Recto`, invite: $localize`:@@piece.invitePhoto:Touchez pour photographier` },
    { type: 'PIECE_VERSO', titre: $localize`:@@piece.verso:Verso`, invite: $localize`:@@piece.invitePhoto:Touchez pour photographier` },
  ];

  constructor() {
    this.tuteur.valueChanges.subscribe((tuteur) => this.etat.natureLien.set(tuteur ? 'TUTEUR' : 'PARENT'));
    effect(() => {
      const etape = this.etape();
      if (etape === 'instruction') {
        this.chargerDossier();
      } else if (!this.etapes().includes(etape) || this.manqueUnPrealable(etape)) {
        void this.router.navigate(['/verification', this.etapes()[0]], { replaceUrl: true });
      }
    });
  }

  protected reculer(): void {
    const index = this.etapes().indexOf(this.etape());
    void this.router.navigate(index <= 0 ? ['/'] : ['/verification', this.etapes()[index - 1]]);
  }

  protected choisirCanal(canal: 'EN_LIGNE' | 'POINT_INSCRIPTION'): void {
    this.etat.canal.set(canal);
  }

  protected validerIdentite(): void {
    if (this.identite.invalid) {
      this.erreur.set($localize`:@@verification.identite.incomplet:Renseignez votre nom, vos prénoms et le numéro de votre pièce.`);
      return;
    }
    const { nom, prenoms, numeroPiece } = this.identite.getRawValue();
    this.etat.identite.set({ nom: nom.trim(), prenoms: prenoms.trim(), typePiece: 'CNIB', numeroPiece: numeroPiece.trim() });
    this.avancer();
  }

  protected async joindre(type: TypePieceKyc, evenement: Event): Promise<void> {
    const champ = evenement.target as HTMLInputElement;
    const fichier = champ.files?.[0];
    champ.value = '';
    if (fichier) {
      this.erreur.set(null);
      this.etat.joindre(type, await allegerPhoto(fichier));
    }
  }

  protected validerPiece(): void {
    if (!this.etat.fichiers().PIECE_RECTO && !this.etat.aCompleter()) {
      this.erreur.set($localize`:@@verification.piece.manque:Photographiez au moins le recto de votre pièce.`);
      return;
    }
    this.avancer();
  }

  protected validerFiliation(): void {
    if (!this.etat.aCompleter()) {
      if (this.enfant.invalid) {
        this.erreur.set($localize`:@@verification.enfant.incomplet:Renseignez le prénom, le nom et la date de naissance de l'enfant.`);
        return;
      }
      this.etat.enfant.set(this.enfant.getRawValue());
      if (this.enLigne() && !this.etat.fichiers()[this.justificatif().type]) {
        this.erreur.set($localize`:@@verification.filiation.manque:Ajoutez le justificatif du lien avec l'enfant.`);
        return;
      }
    }
    this.avancer();
  }

  /** Crée le dossier (ou reprend celui à compléter), envoie les pièces une à une puis le dépose. */
  protected envoyer(): void {
    if (this.enCours()) {
      return;
    }
    const aCompleter = this.etat.aCompleter();
    const ouverture: Observable<DossierKycParent> = aCompleter
      ? defer(() => [aCompleter])
      : this.client.ouvrir({
          canal: this.etat.canal(),
          natureLien: this.etat.natureLien(),
          demandeur: this.etat.identite()!,
          enfant: this.etat.enfant()!,
        });
    this.enCours.set(true);
    this.erreur.set(null);
    ouverture
      .pipe(
        switchMap((dossier) => {
          const envois = (Object.entries(this.etat.fichiers()) as [TypePieceKyc, Blob][]).map(([type, fichier]) =>
            this.client.ajouterPiece(dossier.id, type, fichier),
          );
          return concat(...envois, this.client.deposer(dossier.id)).pipe(last());
        }),
      )
      .subscribe({
        next: () => {
          this.enCours.set(false);
          this.etat.effacer();
          this.identite.reset();
          this.enfant.reset();
          void this.router.navigate(['/verification', 'instruction']);
        },
        error: (cause: unknown) => {
          this.enCours.set(false);
          this.erreur.set(erreurLisible(cause).message);
        },
      });
  }

  protected completer(dossier: DossierKycParent): void {
    this.etat.effacer();
    this.etat.canal.set(dossier.canal);
    this.etat.natureLien.set(dossier.natureLien);
    this.etat.aCompleter.set(dossier);
    void this.router.navigate(['/verification', 'piece']);
  }

  protected recommencer(): void {
    this.etat.effacer();
    void this.router.navigate(['/verification', 'mode']);
  }

  protected accueil(): void {
    void this.router.navigate(['/']);
  }

  protected chargerDossier(): void {
    this.dossier.set(undefined);
    this.erreur.set(null);
    this.client.courant().subscribe({
      next: (dossier) => {
        if (!dossier || dossier.statut === 'BROUILLON') {
          this.recommencer();
        } else {
          this.dossier.set(dossier);
        }
      },
      error: (cause: unknown) => this.erreur.set(erreurLisible(cause).message),
    });
  }

  protected readonly nombrePieces = computed(() => Object.keys(this.etat.fichiers()).length);

  protected avancerDepuisMode(): void {
    this.avancer();
  }

  protected classesChoix(choisi: boolean): string {
    return (
      'flex min-h-11 w-full cursor-pointer items-center gap-3 rounded-lg bg-surface p-3.5 focus-visible:outline-2 focus-visible:outline-accent ' +
      (choisi ? 'border-2 border-accent' : 'border border-line')
    );
  }

  protected classesPastille(active: boolean): string {
    return (
      'grid size-11 flex-none place-items-center rounded-md ' +
      (active ? 'bg-accent-soft text-accent' : 'bg-surface-2 text-text-2')
    );
  }

  protected fichierDe(type: TypePieceKyc): Blob | undefined {
    return this.etat.fichiers()[type];
  }

  protected poids(fichier: Blob | undefined): string {
    return fichier ? $localize`:@@piece.jointe:Photo jointe · ${Math.max(1, Math.round(fichier.size / 1000))}:poids: Ko` : '';
  }

  protected icone(fichier: Blob | undefined): NomIcone {
    return fichier ? 'valider' : 'kyc-piece';
  }

  private avancer(): void {
    this.erreur.set(null);
    const index = this.etapes().indexOf(this.etape());
    void this.router.navigate(['/verification', this.etapes()[index + 1]]);
  }

  private manqueUnPrealable(etape: NomEtape): boolean {
    if (this.etat.aCompleter() || etape === 'mode' || etape === 'identite') {
      return false;
    }
    return !this.etat.identite() || (etape === 'recap' && !this.etat.enfant());
  }
}
