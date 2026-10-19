import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';

import { BraceletParc, ClientSav, EtatTechniqueBracelet } from 'api';
import { FgBadge, FgBanniere, FgBouton, FgSquelette } from 'ui';

import { erreurLisible, estRefus } from '../commun/acces';
import { FicheBracelet } from './fiche-bracelet';
import { GROUPES_PARC, LIBELLES_STATUT, TONS_STATUT, contact } from './libelles';

/**
 * Parc de bracelets (écrans 64 et 65, US-SAV-002) : décompte par état, vue consolidée filtrable et fiche du
 * bracelet choisi à côté. Le service après-vente voit l'état du matériel, jamais l'enfant qui le porte ni où
 * il se trouve (ADR 0009) : la colonne « Titulaire · zone » de la maquette devient « Porté ».
 */
@Component({
  selector: 'app-parc',
  imports: [RouterLink, FgBadge, FgBanniere, FgBouton, FgSquelette, FicheBracelet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="grid min-h-0 grid-cols-1 overflow-hidden rounded-xl border border-line bg-bg xl:grid-cols-[1fr_420px]">
      <div class="flex min-w-0 flex-col gap-4 border-line px-7 py-6 xl:border-r">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <h1 class="m-0 font-display text-[26px] font-bold tracking-tight" i18n="@@parc.titre">Parc de bracelets</h1>
          <button fg-button variante="secondary" type="button" [disabled]="visibles().length === 0" (click)="exporter()" i18n="@@parc.exporter">Exporter CSV</button>
        </div>
        @if (parc(); as tous) {
          <div class="grid grid-cols-2 gap-2.5 md:grid-cols-5" role="group" i18n-aria-label="@@parc.filtres" aria-label="Filtrer par état">
            @for (groupe of groupes; track groupe.libelle; let i = $index) {
              <button
                type="button"
                class="flex flex-col gap-1 rounded-xl border bg-surface p-3.5 text-left focus-visible:outline-2 focus-visible:outline-accent"
                [class.border-attention]="filtre() === i"
                [class.border-line]="filtre() !== i"
                [attr.aria-pressed]="filtre() === i"
                (click)="filtrer(i)"
              >
                <span class="text-caption font-medium text-text-3">{{ groupe.libelle }}</span>
                <strong class="font-display text-2xl font-bold tabular-nums">{{ nombre(decompte()[i]) }}</strong>
              </button>
            }
          </div>
          @if (visibles().length === 0) {
            <p class="m-0 rounded-xl border border-line bg-surface p-6 text-center text-body text-text-2" i18n="@@parc.vide">Aucun bracelet dans cet état.</p>
          } @else {
            <div class="overflow-x-auto rounded-xl border border-line bg-surface" role="table" i18n-aria-label="@@parc.table" aria-label="Bracelets du parc">
              <div class="grid h-10 min-w-[640px] grid-cols-[96px_110px_minmax(110px,1fr)_76px_64px_70px_104px] items-center bg-surface-2 px-4 text-caption font-semibold text-text-2" role="row">
                <span role="columnheader" aria-sort="ascending" i18n="@@parc.col.serie">Série</span>
                <span role="columnheader" i18n="@@parc.col.etat">État</span>
                <span role="columnheader" i18n="@@parc.col.porte">Porté</span>
                <span role="columnheader" i18n="@@parc.col.batterie">Batterie</span>
                <span role="columnheader" i18n="@@parc.col.reseau">Réseau</span>
                <span role="columnheader" i18n="@@parc.col.logiciel">Logiciel</span>
                <span role="columnheader" i18n="@@parc.col.contact">Dernier contact</span>
              </div>
              @for (bracelet of visibles(); track bracelet.numeroSerie) {
                @let etat = etats().get(bracelet.numeroSerie);
                <a
                  class="grid h-11 min-w-[640px] grid-cols-[96px_110px_minmax(110px,1fr)_76px_64px_70px_104px] items-center border-t border-line px-4 text-label font-medium hover:bg-surface-2 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-accent"
                  [class.bg-accent-soft]="bracelet.numeroSerie === numero()"
                  role="row"
                  [attr.aria-current]="bracelet.numeroSerie === numero() ? 'true' : null"
                  [routerLink]="['/sav/parc', bracelet.numeroSerie]"
                >
                  <span class="font-mono text-caption" role="cell">{{ bracelet.numeroSerie }}</span>
                  <span role="cell"><fg-badge [ton]="tons[bracelet.statut]">{{ libelles[bracelet.statut] }}</fg-badge></span>
                  <span class="truncate text-text-2" role="cell">
                    @if (bracelet.appaire) {
                      <ng-container i18n="@@parc.porte.oui">Par un enfant</ng-container>
                    } @else {
                      —
                    }
                    @if (bracelet.certificatRevoque) {
                      <span class="font-semibold text-danger" i18n="@@parc.revoque"> · certificat révoqué</span>
                    }
                  </span>
                  <span class="tabular-nums" role="cell">{{ etat?.batterie != null ? etat!.batterie + ' %' : '—' }}</span>
                  <span role="cell">{{ etat?.reseau ?? '—' }}</span>
                  <span class="text-text-2 tabular-nums" role="cell">{{ bracelet.versionLogiciel }}</span>
                  <span class="text-text-2 tabular-nums" role="cell">{{ contact(etat?.dernierContact ?? null) }}</span>
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
      </div>
      <aside class="flex min-w-0 flex-col gap-3.5 border-t border-line p-6 xl:border-t-0" i18n-aria-label="@@parc.ficheZone" aria-label="Fiche du bracelet">
        @if (numero(); as choisi) {
          <app-fiche-bracelet [numero]="choisi" [etat]="etats().get(choisi) ?? null" (modifie)="charger()" />
        } @else {
          <p class="m-0 my-auto text-center text-body text-text-2" i18n="@@parc.choisir">Choisissez un bracelet pour voir sa fiche.</p>
        }
      </aside>
    </div>
  `,
  host: { class: 'contents' },
})
export class Parc {
  /** Numéro de série du bracelet dont la fiche est ouverte, tiré de l'adresse. */
  readonly numero = input<string | undefined>();

  private readonly client = inject(ClientSav);
  private readonly router = inject(Router);

  protected readonly groupes = GROUPES_PARC;
  protected readonly libelles = LIBELLES_STATUT;
  protected readonly tons = TONS_STATUT;
  protected readonly contact = contact;
  protected readonly nombre = new Intl.NumberFormat('fr-FR').format;

  protected readonly parc = signal<readonly BraceletParc[] | null>(null);
  protected readonly etats = signal<ReadonlyMap<string, EtatTechniqueBracelet>>(new Map());
  /** Indice du groupe d'états affiché, ou `null` pour tout le parc. */
  protected readonly filtre = signal<number | null>(null);
  protected readonly erreur = signal<string | null>(null);

  protected readonly decompte = computed(() =>
    GROUPES_PARC.map((groupe) => (this.parc() ?? []).filter((bracelet) => groupe.statuts.includes(bracelet.statut)).length),
  );
  protected readonly visibles = computed(() => {
    const filtre = this.filtre();
    const statuts = filtre === null ? null : GROUPES_PARC[filtre].statuts;
    return (this.parc() ?? []).filter((bracelet) => !statuts || statuts.includes(bracelet.statut));
  });

  constructor() {
    this.charger();
  }

  /** Un second appui sur le même indicateur lève le filtre. */
  protected filtrer(groupe: number): void {
    this.filtre.update((actuel) => (actuel === groupe ? null : groupe));
  }

  /** L'état technique complète la vue : s'il manque, le parc reste lisible avec des tirets. */
  protected charger(): void {
    this.erreur.set(null);
    forkJoin([this.client.parc(), this.client.etatsParc().pipe(catchError(() => of([] as EtatTechniqueBracelet[])))]).subscribe({
      next: ([parc, etats]) => {
        this.parc.set(parc);
        this.etats.set(new Map(etats.map((etat) => [etat.numeroSerie, etat])));
      },
      error: (cause: unknown) => (estRefus(cause) ? void this.router.navigate(['/refuse']) : this.erreur.set(erreurLisible(cause).message)),
    });
  }

  /** Export des lignes affichées, pour l'approvisionnement : état du matériel seulement. */
  protected exporter(): void {
    const etats = this.etats();
    const lignes = [
      ['serie', 'etat', 'porte', 'batterie', 'reseau', 'materiel', 'logiciel', 'garantie', 'dernier_contact', 'dernier_mouvement'],
      ...this.visibles().map((b) => {
        const etat = etats.get(b.numeroSerie);
        return [
          b.numeroSerie,
          b.statut,
          b.appaire ? 'oui' : 'non',
          etat?.batterie ?? '',
          etat?.reseau ?? '',
          b.revisionMaterielle,
          b.versionLogiciel,
          b.garantieJusquAu ?? '',
          etat?.dernierContact ?? '',
          b.modifieLe,
        ];
      }),
    ];
    const adresse = URL.createObjectURL(new Blob([lignes.map((ligne) => ligne.join(';')).join('\n')], { type: 'text/csv;charset=utf-8' }));
    const lien = document.createElement('a');
    lien.href = adresse;
    lien.download = 'parc-bracelets.csv';
    lien.click();
    URL.revokeObjectURL(adresse);
  }
}
