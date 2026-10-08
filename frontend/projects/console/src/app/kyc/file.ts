import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { ClientConsole, DossierKycFile } from 'api';
import { FgBadge, FgBanniere, FgBouton, FgSquelette, TonBadge } from 'ui';

import { erreurLisible, estRefus } from '../commun/acces';

const SEUIL_URGENT_H = 36;

interface Ligne {
  readonly dossier: DossierKycFile;
  readonly lien: string;
  readonly canal: string;
  readonly heures: number;
  readonly urgent: boolean;
  readonly priorite: { libelle: string; ton: TonBadge };
  readonly statut: string;
}

/** File d'instruction KYC (écran 59) : les dossiers les plus anciens d'abord. */
@Component({
  selector: 'app-file-kyc',
  imports: [RouterLink, FgBadge, FgBanniere, FgBouton, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex flex-col gap-1">
      <h1 class="m-0 text-titre-ecran font-bold tracking-tight" i18n="@@kyc.file.titre">Dossiers KYC</h1>
      <span class="text-label text-text-2" i18n="@@kyc.file.objectif">Objectif : décision sous 48 h ouvrées</span>
    </div>
    @if (lignes(); as liste) {
      <div class="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <div class="flex flex-col gap-1.5 rounded-lg border border-line bg-surface p-4">
          <span class="text-caption font-medium text-text-3" i18n="@@kyc.kpi.file">En file</span>
          <strong class="font-display text-data font-bold tabular-nums">{{ total() }}</strong>
        </div>
        <div class="flex flex-col gap-1.5 rounded-lg border border-line bg-surface p-4">
          <span class="text-caption font-medium text-text-3" i18n="@@kyc.kpi.urgents">&gt; 36 h</span>
          <strong class="font-display text-data font-bold tabular-nums">{{ urgents() }}</strong>
          <span class="text-caption font-medium text-text-2" i18n="@@kyc.kpi.urgents.texte">à traiter en priorité</span>
        </div>
      </div>
      @if (liste.length === 0) {
        <p class="m-0 rounded-lg border border-line bg-surface p-6 text-center text-body text-text-2" i18n="@@kyc.file.vide">
          Aucun dossier en attente d'instruction.
        </p>
      } @else {
        <div class="overflow-hidden rounded-lg border border-line bg-surface" role="table" i18n-aria-label="@@kyc.file.table" aria-label="Dossiers à instruire">
          <div class="grid h-10 grid-cols-[120px_1.4fr_1fr_150px_110px_120px_120px] items-center bg-surface-2 px-4 text-caption font-semibold text-text-2" role="row">
            <span role="columnheader" i18n="@@kyc.col.dossier">Dossier</span>
            <span role="columnheader" i18n="@@kyc.col.parent">Parent · enfant</span>
            <span role="columnheader" i18n="@@kyc.col.lien">Lien déclaré</span>
            <span role="columnheader" i18n="@@kyc.col.canal">Canal</span>
            <span role="columnheader" aria-sort="descending" i18n="@@kyc.col.anciennete">Ancienneté</span>
            <span role="columnheader" i18n="@@kyc.col.priorite">Priorité</span>
            <span role="columnheader" i18n="@@kyc.col.statut">Statut</span>
          </div>
          @for (ligne of liste; track ligne.dossier.id) {
            <a
              class="grid h-11 grid-cols-[120px_1.4fr_1fr_150px_110px_120px_120px] items-center border-t border-line px-4 text-label font-medium hover:bg-surface-2 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-accent"
              role="row"
              [routerLink]="['/kyc', ligne.dossier.id]"
            >
              <span class="font-mono text-caption" role="cell">{{ ligne.dossier.reference }}</span>
              <span class="truncate" role="cell" i18n="@@kyc.ligne.parent">{{ ligne.dossier.demandeur }} → {{ ligne.dossier.enfantPrenom }}, {{ ligne.dossier.enfantAge }} ans</span>
              <span class="text-text-2" role="cell">{{ ligne.lien }}</span>
              <span class="text-text-2" role="cell">{{ ligne.canal }}</span>
              <span class="tabular-nums" role="cell" [class.text-danger]="ligne.urgent" [class.font-semibold]="ligne.urgent">{{ ligne.heures }} h</span>
              <span role="cell"><fg-badge [ton]="ligne.priorite.ton">{{ ligne.priorite.libelle }}</fg-badge></span>
              <span class="text-text-2" role="cell">{{ ligne.statut }}</span>
            </a>
          }
        </div>
      }
    } @else if (erreur(); as message) {
      <fg-banner ton="erreur">{{ message }}</fg-banner>
      <button fg-button variante="secondary" type="button" class="self-start" (click)="charger()" i18n="@@commun.reessayer">Réessayer</button>
    } @else {
      <fg-skeleton forme="carte" />
      <fg-skeleton forme="carte" />
    }
  `,
  host: { class: 'contents' },
})
export class FileKyc {
  private readonly client = inject(ClientConsole);
  private readonly router = inject(Router);

  private readonly dossiers = signal<readonly DossierKycFile[] | null>(null);
  protected readonly total = signal(0);
  protected readonly erreur = signal<string | null>(null);
  protected readonly lignes = computed(() => this.dossiers()?.map(ligne) ?? null);
  protected readonly urgents = computed(() => this.lignes()?.filter((l) => l.urgent).length ?? 0);

  constructor() {
    this.charger();
  }

  protected charger(): void {
    this.erreur.set(null);
    this.client.fileKyc().subscribe({
      next: (page) => {
        this.total.set(page.total);
        this.dossiers.set(page.elements);
      },
      error: (cause: unknown) =>
        estRefus(cause) ? void this.router.navigate(['/refuse']) : this.erreur.set(erreurLisible(cause).message),
    });
  }
}

function ligne(dossier: DossierKycFile): Ligne {
  const heures = Math.max(0, Math.floor((Date.now() - Date.parse(dossier.deposeLe)) / 3_600_000));
  const urgent = heures > SEUIL_URGENT_H;
  return {
    dossier,
    heures,
    urgent,
    lien:
      dossier.natureLien === 'TUTEUR'
        ? $localize`:@@kyc.lien.tuteur:Tuteur · jugement de tutelle`
        : $localize`:@@kyc.lien.parent:Parent · acte de naissance`,
    canal: dossier.canal === 'EN_LIGNE' ? $localize`:@@kyc.canal.enLigne:En ligne` : $localize`:@@kyc.canal.point:Point d'inscription`,
    priorite: urgent
      ? { libelle: $localize`:@@kyc.priorite.haute:Haute`, ton: 'attention' }
      : dossier.natureLien === 'TUTEUR'
        ? { libelle: $localize`:@@kyc.priorite.tutelle:Tutelle`, ton: 'accent' }
        : { libelle: $localize`:@@kyc.priorite.normale:Normale`, ton: 'neutre' },
    statut: dossier.prisEnChargeParMoi
      ? $localize`:@@kyc.statut.aVous:À vous`
      : dossier.prisEnCharge
        ? $localize`:@@kyc.statut.attribue:Attribué`
        : $localize`:@@kyc.statut.libre:Non attribué`,
  };
}
