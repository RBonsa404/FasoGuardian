import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { BraceletParc, ClientSav, StatutBraceletParc } from 'api';
import { FgBadge, FgBanniere, FgBouton, FgSquelette } from 'ui';

import { erreurLisible, estRefus } from '../commun/acces';
import { LIBELLES_STATUT, STATUTS, TONS_STATUT, jour, jourEtHeure } from './libelles';

/**
 * Parc de bracelets (écran 64, US-SAV-002) : décompte par état et vue consolidée, filtrable. Le service
 * après-vente voit l'état des bracelets, jamais l'enfant qui les porte.
 */
@Component({
  selector: 'app-parc',
  imports: [RouterLink, FgBadge, FgBanniere, FgBouton, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex flex-wrap items-center justify-between gap-3">
      <h1 class="m-0 text-titre-ecran font-bold tracking-tight" i18n="@@parc.titre">Parc de bracelets</h1>
      <button fg-button variante="secondary" type="button" [disabled]="visibles().length === 0" (click)="exporter()" i18n="@@parc.exporter">Exporter CSV</button>
    </div>
    @if (parc(); as tous) {
      <div class="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6" role="group" i18n-aria-label="@@parc.filtres" aria-label="Filtrer par état">
        @for (statut of statuts; track statut) {
          <button
            type="button"
            class="flex flex-col gap-1.5 rounded-lg bg-surface p-4 text-left focus-visible:outline-2 focus-visible:outline-accent"
            [class]="filtre() === statut ? 'border-2 border-accent' : 'border border-line'"
            [attr.aria-pressed]="filtre() === statut"
            (click)="filtrer(statut)"
          >
            <span class="text-caption font-medium text-text-3">{{ libelles[statut] }}</span>
            <strong class="font-display text-data font-bold tabular-nums">{{ decompte()[statut] }}</strong>
          </button>
        }
      </div>
      @if (visibles().length === 0) {
        <p class="m-0 rounded-lg border border-line bg-surface p-6 text-center text-body text-text-2" i18n="@@parc.vide">Aucun bracelet dans cet état.</p>
      } @else {
        <div class="overflow-hidden rounded-lg border border-line bg-surface" role="table" i18n-aria-label="@@parc.table" aria-label="Bracelets du parc">
          <div class="grid h-10 grid-cols-[130px_150px_1fr_110px_110px_150px_150px] items-center bg-surface-2 px-4 text-caption font-semibold text-text-2" role="row">
            <span role="columnheader" aria-sort="ascending" i18n="@@parc.col.serie">Série</span>
            <span role="columnheader" i18n="@@parc.col.etat">État</span>
            <span role="columnheader" i18n="@@parc.col.porte">Porté</span>
            <span role="columnheader" i18n="@@parc.col.materiel">Matériel</span>
            <span role="columnheader" i18n="@@parc.col.logiciel">Logiciel</span>
            <span role="columnheader" i18n="@@parc.col.garantie">Garantie</span>
            <span role="columnheader" i18n="@@parc.col.modifie">Dernier mouvement</span>
          </div>
          @for (bracelet of visibles(); track bracelet.numeroSerie) {
            <a class="grid h-11 grid-cols-[130px_150px_1fr_110px_110px_150px_150px] items-center border-t border-line px-4 text-label font-medium hover:bg-surface-2 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-accent" role="row" [routerLink]="['/sav/parc', bracelet.numeroSerie]">
              <span class="font-mono text-caption" role="cell">{{ bracelet.numeroSerie }}</span>
              <span role="cell"><fg-badge [ton]="tons[bracelet.statut]">{{ libelles[bracelet.statut] }}</fg-badge></span>
              <span class="text-text-2" role="cell">
                @if (bracelet.appaire) {
                  <ng-container i18n="@@parc.porte.oui">Par un enfant</ng-container>
                } @else {
                  —
                }
                @if (bracelet.certificatRevoque) {
                  <span class="font-semibold text-danger" i18n="@@parc.revoque"> · certificat révoqué</span>
                }
              </span>
              <span class="text-text-2" role="cell">{{ bracelet.revisionMaterielle }}</span>
              <span class="text-text-2 tabular-nums" role="cell">{{ bracelet.versionLogiciel }}</span>
              <span class="text-text-2" role="cell">{{ jour(bracelet.garantieJusquAu) }}</span>
              <span class="text-text-2 tabular-nums" role="cell">{{ jourEtHeure(bracelet.modifieLe) }}</span>
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
export class Parc {
  private readonly client = inject(ClientSav);
  private readonly router = inject(Router);

  protected readonly statuts = STATUTS;
  protected readonly libelles = LIBELLES_STATUT;
  protected readonly tons = TONS_STATUT;
  protected readonly jour = jour;
  protected readonly jourEtHeure = jourEtHeure;

  protected readonly parc = signal<readonly BraceletParc[] | null>(null);
  protected readonly filtre = signal<StatutBraceletParc | null>(null);
  protected readonly erreur = signal<string | null>(null);

  protected readonly decompte = computed(() => {
    const total = Object.fromEntries(STATUTS.map((statut) => [statut, 0])) as Record<StatutBraceletParc, number>;
    for (const bracelet of this.parc() ?? []) {
      total[bracelet.statut]++;
    }
    return total;
  });
  protected readonly visibles = computed(() => {
    const filtre = this.filtre();
    return (this.parc() ?? []).filter((bracelet) => !filtre || bracelet.statut === filtre);
  });

  constructor() {
    this.charger();
  }

  /** Un second appui sur le même état lève le filtre. */
  protected filtrer(statut: StatutBraceletParc): void {
    this.filtre.update((actuel) => (actuel === statut ? null : statut));
  }

  protected charger(): void {
    this.erreur.set(null);
    this.client.parc().subscribe({
      next: (parc) => this.parc.set(parc),
      error: (cause: unknown) => (estRefus(cause) ? void this.router.navigate(['/refuse']) : this.erreur.set(erreurLisible(cause).message)),
    });
  }

  /** Export des lignes affichées, pour l'approvisionnement : état du matériel seulement. */
  protected exporter(): void {
    const lignes = [
      ['serie', 'etat', 'porte', 'materiel', 'logiciel', 'garantie', 'dernier_mouvement'],
      ...this.visibles().map((b) => [b.numeroSerie, b.statut, b.appaire ? 'oui' : 'non', b.revisionMaterielle, b.versionLogiciel, b.garantieJusquAu ?? '', b.modifieLe]),
    ];
    const adresse = URL.createObjectURL(new Blob([lignes.map((ligne) => ligne.join(';')).join('\n')], { type: 'text/csv;charset=utf-8' }));
    const lien = document.createElement('a');
    lien.href = adresse;
    lien.download = 'parc-bracelets.csv';
    lien.click();
    URL.revokeObjectURL(adresse);
  }
}
