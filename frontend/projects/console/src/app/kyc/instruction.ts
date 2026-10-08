import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { Router, RouterLink } from '@angular/router';

import { ClientConsole, DecisionKyc, DossierKycInstruction, PieceKyc, TypePieceKyc } from 'api';
import { FgBadge, FgBanniere, FgBouton, FgSquelette } from 'ui';

import { erreurLisible, estRefus } from '../commun/acces';

const LIBELLES_PIECES: Record<TypePieceKyc, string> = {
  PIECE_RECTO: $localize`:@@piece.recto:Pièce d'identité · recto`,
  PIECE_VERSO: $localize`:@@piece.verso:Pièce d'identité · verso`,
  ACTE_NAISSANCE: $localize`:@@piece.acte:Acte de naissance`,
  JUGEMENT_TUTELLE: $localize`:@@piece.jugement:Jugement de tutelle`,
};

const CONTROLES: readonly string[] = [
  $localize`:@@controle.validite:Pièce d'identité lisible et en cours de validité`,
  $localize`:@@controle.nom:Nom identique sur la pièce et le justificatif`,
  $localize`:@@controle.naissance:Date de naissance de l'enfant cohérente`,
];

const DECISIONS: readonly { valeur: DecisionKyc; libelle: string }[] = [
  { valeur: 'APPROUVER', libelle: $localize`:@@decision.valider:Valider` },
  { valeur: 'DEMANDER_COMPLEMENT', libelle: $localize`:@@decision.complement:Demander un complément` },
  { valeur: 'REJETER', libelle: $localize`:@@decision.rejeter:Rejeter` },
];

/**
 * Instruction d'un dossier KYC (écran 60, US-PAR-001). L'agent prend le dossier en charge, examine les
 * pièces déchiffrées à la demande, coche ses contrôles puis décide. Rien n'est conservé dans le navigateur :
 * les images sont libérées à la fermeture de l'écran.
 */
@Component({
  selector: 'app-instruction-kyc',
  imports: [DatePipe, ReactiveFormsModule, RouterLink, FgBadge, FgBanniere, FgBouton, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './instruction.html',
})
export class InstructionKyc {
  readonly id = input.required<string>();

  private readonly client = inject(ClientConsole);
  private readonly router = inject(Router);
  private readonly assainisseur = inject(DomSanitizer);

  protected readonly dossier = signal<DossierKycInstruction | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly enCours = signal(false);
  protected readonly termine = signal<string | null>(null);
  protected readonly pieceOuverte = signal<{ piece: PieceKyc; url: string; sure: SafeResourceUrl } | null>(null);
  protected readonly controles = CONTROLES.map((libelle) => ({ libelle, coche: signal(false) }));
  protected readonly decisions = DECISIONS;
  protected readonly decision = signal<DecisionKyc | null>(null);
  protected readonly motif = new FormControl('', { nonNullable: true });
  protected readonly libellesPieces = LIBELLES_PIECES;

  protected readonly enInstruction = computed(() => this.dossier()?.statut === 'EN_INSTRUCTION');
  protected readonly libelleAction = computed(() => {
    const choix = DECISIONS.find((d) => d.valeur === this.decision());
    return choix
      ? $localize`:@@decision.confirmer:Confirmer : ${choix.libelle.toLowerCase()}:decision:`
      : $localize`:@@decision.choisir:Choisir une décision`;
  });

  constructor() {
    effect(() => this.charger(this.id()));
    inject(DestroyRef).onDestroy(() => this.fermerPiece());
  }

  protected prendreEnCharge(): void {
    this.appeler(this.client.prendreEnCharge(this.id()), (dossier) => this.dossier.set(dossier));
  }

  protected ouvrirPiece(piece: PieceKyc): void {
    this.erreur.set(null);
    this.client.pieceKyc(this.id(), piece.id).subscribe({
      next: (contenu) => {
        this.fermerPiece();
        const url = URL.createObjectURL(contenu);
        this.pieceOuverte.set({ piece, url, sure: this.assainisseur.bypassSecurityTrustResourceUrl(url) });
      },
      error: (cause: unknown) => this.traiter(cause),
    });
  }

  protected confirmer(): void {
    const decision = this.decision();
    if (!decision) {
      return;
    }
    const motif = this.motif.value.trim();
    if (decision === 'APPROUVER' && this.controles.some((controle) => !controle.coche())) {
      this.erreur.set($localize`:@@decision.controles:Cochez tous les contrôles avant de valider le dossier.`);
      return;
    }
    if (decision !== 'APPROUVER' && !motif) {
      this.erreur.set($localize`:@@decision.motifRequis:Le motif est obligatoire pour un rejet ou une demande de complément.`);
      return;
    }
    this.appeler(this.client.decider(this.id(), decision, motif || undefined), (dossier) => {
      this.dossier.set(dossier);
      this.termine.set(
        decision === 'APPROUVER'
          ? $localize`:@@decision.fait.valider:Compte activé. SMS envoyé au parent.`
          : decision === 'DEMANDER_COMPLEMENT'
            ? $localize`:@@decision.fait.complement:Demande de complément envoyée. Le dossier reviendra dans la file.`
            : $localize`:@@decision.fait.rejeter:Rejet motivé envoyé au parent.`,
      );
    });
  }

  private charger(id: string): void {
    this.dossier.set(null);
    this.termine.set(null);
    this.erreur.set(null);
    this.client.dossierKyc(id).subscribe({
      next: (dossier) => this.dossier.set(dossier),
      error: (cause: unknown) => this.traiter(cause),
    });
  }

  private appeler(appel: ReturnType<ClientConsole['decider']>, suite: (dossier: DossierKycInstruction) => void): void {
    if (this.enCours()) {
      return;
    }
    this.enCours.set(true);
    this.erreur.set(null);
    appel.subscribe({
      next: (dossier) => {
        this.enCours.set(false);
        suite(dossier);
      },
      error: (cause: unknown) => {
        this.enCours.set(false);
        this.traiter(cause);
      },
    });
  }

  private traiter(cause: unknown): void {
    if (estRefus(cause)) {
      void this.router.navigate(['/refuse']);
    } else {
      this.erreur.set(erreurLisible(cause).message);
    }
  }

  private fermerPiece(): void {
    const ouverte = this.pieceOuverte();
    if (ouverte) {
      URL.revokeObjectURL(ouverte.url);
      this.pieceOuverte.set(null);
    }
  }
}
