import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { ClientConsole, ClientTableau, DossierKycFile, IndicateurConsole } from 'api';
import { FgBadge, FgBanniere, FgBouton, FgSquelette, TonBadge } from 'ui';

import { erreurLisible, estRefus } from '../commun/acces';
import { libeller } from '../commun/indicateurs';

type Filtre = 'TOUS' | 'PRIORITAIRES' | 'EN_LIGNE' | 'POINT' | 'COMPLEMENT';

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
      <span class="text-body text-text-2 first-letter:uppercase" i18n="@@kyc.file.objectif">{{ jour }} · objectif : décision sous 48 h ouvrées</span>
    </div>
    @if (indicateurs().length > 0) {
      <!-- Les mêmes indicateurs que le tableau de bord (écran 57) : des décomptes, sans donnée personnelle. -->
      <dl class="m-0 grid grid-cols-2 gap-3 xl:grid-cols-4">
        @for (i of indicateurs(); track i.code) {
          @let l = libeller(i);
          <div class="flex flex-col gap-1.5 rounded-[14px] border bg-surface p-4" [class]="i.alerte ? 'border-attention' : 'border-line'">
            <dt class="text-caption font-medium text-text-3">{{ l.libelle }}</dt>
            <dd class="m-0 font-display text-[28px] font-bold tabular-nums" [class.text-attention-text]="i.alerte">{{ l.valeur }}</dd>
            <dd class="m-0 text-caption font-medium text-text-2">{{ l.detail }}</dd>
          </div>
        }
      </dl>
    }
    @if (lignes(); as toutes) {
      <div class="flex flex-wrap gap-2" role="group" i18n-aria-label="@@kyc.filtres" aria-label="Filtrer la file">
        @for (f of filtres(); track f.code) {
          <button
            type="button"
            class="h-9 rounded-md px-3 text-label font-semibold focus-visible:outline-2 focus-visible:outline-accent"
            [class]="filtre() === f.code ? 'border-[1.5px] border-accent bg-accent-soft text-text' : 'border border-line bg-surface text-text hover:border-line-strong'"
            [attr.aria-pressed]="filtre() === f.code"
            (click)="filtre.set(f.code)"
          >
            {{ f.libelle }}
          </button>
        }
      </div>
      @let liste = filtrees();
      @if (toutes.length === 0) {
        <p class="m-0 rounded-lg border border-line bg-surface p-6 text-center text-body text-text-2" i18n="@@kyc.file.vide">
          Aucun dossier en attente d'instruction.
        </p>
      } @else if (liste.length === 0) {
        <p class="m-0 rounded-lg border border-line bg-surface p-6 text-center text-body text-text-2" i18n="@@kyc.file.filtreVide">Aucun dossier ne correspond à ce filtre.</p>
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
  protected readonly indicateurs = signal<readonly IndicateurConsole[]>([]);
  protected readonly filtre = signal<Filtre>('TOUS');
  protected readonly libeller = libeller;
  protected readonly jour = new Intl.DateTimeFormat('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' }).format(new Date());
  private readonly tableau = inject(ClientTableau);

  protected readonly filtres = computed(() => {
    const lignes = this.lignes() ?? [];
    const complements = lignes.filter((l) => l.dossier.complementRecu).length;
    return [
      { code: 'TOUS' as Filtre, libelle: $localize`:@@kyc.filtre.tous:Tous · ${this.total()}:n:` },
      { code: 'PRIORITAIRES' as Filtre, libelle: $localize`:@@kyc.filtre.prio:> 36 h · ${this.urgents()}:n:` },
      { code: 'EN_LIGNE' as Filtre, libelle: $localize`:@@kyc.canal.enLigne:En ligne` },
      { code: 'POINT' as Filtre, libelle: $localize`:@@kyc.canal.point:Point d'inscription` },
      { code: 'COMPLEMENT' as Filtre, libelle: $localize`:@@kyc.filtre.complement:Complément reçu · ${complements}:n:` },
    ];
  });
  protected readonly filtrees = computed(() => {
    const lignes = this.lignes() ?? [];
    switch (this.filtre()) {
      case 'PRIORITAIRES':
        return lignes.filter((l) => l.urgent);
      case 'EN_LIGNE':
        return lignes.filter((l) => l.dossier.canal === 'EN_LIGNE');
      case 'POINT':
        return lignes.filter((l) => l.dossier.canal === 'POINT_INSCRIPTION');
      case 'COMPLEMENT':
        return lignes.filter((l) => l.dossier.complementRecu);
      default:
        return lignes;
    }
  });

  constructor() {
    this.charger();
  }

  protected charger(): void {
    this.erreur.set(null);
    this.tableau.indicateurs().subscribe({
      next: (groupes) => this.indicateurs.set(groupes.find((groupe) => groupe.role === 'KYC')?.indicateurs ?? []),
      error: () => undefined,
    });
    this.client.fileKyc(0, 100).subscribe({
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
      (dossier.natureLien === 'TUTEUR'
        ? $localize`:@@kyc.lien.tuteur:Tuteur · jugement de tutelle`
        : $localize`:@@kyc.lien.parent:Parent · acte de naissance`) +
      (dossier.secondTuteur ? $localize`:@@kyc.lien.second: · second tuteur` : ''),
    canal: dossier.canal === 'EN_LIGNE' ? $localize`:@@kyc.canal.enLigne:En ligne` : $localize`:@@kyc.canal.point:Point d'inscription`,
    priorite: urgent
      ? { libelle: $localize`:@@kyc.priorite.haute:Haute`, ton: 'attention' }
      : dossier.natureLien === 'TUTEUR'
        ? { libelle: $localize`:@@kyc.priorite.tutelle:Tutelle`, ton: 'accent' }
        : { libelle: $localize`:@@kyc.priorite.normale:Normale`, ton: 'neutre' },
    statut: dossier.complementRecu && !dossier.prisEnCharge
      ? $localize`:@@kyc.statut.complement:Complément reçu`
      : dossier.prisEnChargeParMoi
      ? $localize`:@@kyc.statut.aVous:À vous`
      : dossier.prisEnCharge
        ? $localize`:@@kyc.statut.attribue:Attribué`
        : $localize`:@@kyc.statut.libre:Non attribué`,
  };
}
