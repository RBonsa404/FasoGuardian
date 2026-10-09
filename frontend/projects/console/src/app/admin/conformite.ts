import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { forkJoin } from 'rxjs';

import { ClientConformite, DemandeEffacement, TableauConformite } from 'api';
import { FgBadge, FgBanniere, FgBouton, FgFeuille, FgSquelette } from 'ui';

import { erreurLisible, estRefus } from '../commun/acces';

const JOUR = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'short', year: 'numeric' });
const MOIS = new Intl.DateTimeFormat('fr-FR', { month: 'long' });
const HEURE = new Intl.DateTimeFormat('fr-FR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });

/** Libellés des purges inscrites au registre, par code de traitement. */
const TRAITEMENTS: Record<string, string> = {
  POSITIONS: $localize`:@@conformite.purge.positions:Positions`,
  PIECES_KYC: $localize`:@@conformite.purge.kyc:Pièces KYC`,
  CONSULTATIONS_PAGE_QR: $localize`:@@conformite.purge.qr:Consultations de la page QR`,
  NUMEROS_DES_TIERS: $localize`:@@conformite.purge.tiers:Numéros des tiers`,
  DOSSIERS_DE_SIGNALEMENT: $localize`:@@conformite.purge.dossiers:Dossiers de signalement`,
  NOTIFICATIONS: $localize`:@@conformite.purge.notifications:Notifications`,
};

/**
 * Conformité CIL (écran 71, US-ADM-003) : état de l'analyse d'impact, durées de conservation appliquées,
 * purges exécutées, demandes d'effacement à exécuter et rapport mensuel.
 */
@Component({
  selector: 'app-conformite',
  imports: [FgBadge, FgBanniere, FgBouton, FgFeuille, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 class="m-0 text-titre-ecran font-bold tracking-tight" i18n="@@conformite.titre">Conformité CIL</h1>

    @if (erreur(); as message) {
      <fg-banner ton="erreur">{{ message }}</fg-banner>
    }
    @if (tableau(); as t) {
      @if (t.aipd.documentee) {
        <fg-banner ton="succes" i18n="@@conformite.aipd.ok">Analyse d'impact documentée : référence {{ t.aipd.reference }}, validée le {{ jour(t.aipd.valideeLe) }}. Délégué à la protection des données : {{ t.aipd.delegue }}.</fg-banner>
      } @else {
        <section class="flex flex-col gap-1.5 rounded-lg border border-danger bg-surface p-4" role="alert">
          <strong class="text-body font-semibold text-danger" i18n="@@conformite.aipd.bloque">Mise en production bloquée</strong>
          <span class="text-label text-text-2" i18n="@@conformite.aipd.texte">L'analyse d'impact (AIPD) n'est pas documentée. Aucun déploiement avec des données réelles n'est possible tant que sa référence, sa date de validation et le délégué à la protection des données ne sont pas renseignés.</span>
        </section>
      }

      <div class="grid grid-cols-1 gap-3 sm:grid-cols-3">
        <div class="flex flex-col gap-1.5 rounded-lg border border-line bg-surface p-4">
          <span class="text-caption font-medium text-text-3" i18n="@@conformite.kpi.acces">Demandes d'accès</span>
          <strong class="font-display text-data font-bold tabular-nums">{{ t.demandesAcces }}</strong>
          <span class="text-caption font-medium text-text-2" i18n="@@conformite.kpi.acces.texte">ce mois-ci, servies aussitôt</span>
        </div>
        <div class="flex flex-col gap-1.5 rounded-lg border border-line bg-surface p-4">
          <span class="text-caption font-medium text-text-3" i18n="@@conformite.kpi.effacement">Demandes d'effacement</span>
          <strong class="font-display text-data font-bold tabular-nums">{{ t.demandesEffacement }}</strong>
          <span class="text-caption font-medium" [class]="t.effacementsEnAttente > 0 ? 'text-attention-text' : 'text-text-2'" i18n="@@conformite.kpi.effacement.texte">ce mois-ci · {{ t.effacementsEnAttente }} en attente</span>
        </div>
        <div class="flex flex-col gap-1.5 rounded-lg border border-line bg-surface p-4">
          <span class="text-caption font-medium text-text-3" i18n="@@conformite.kpi.purges">Purges exécutées</span>
          <strong class="font-display text-data font-bold tabular-nums">{{ t.joursDePurge }} / {{ t.joursEcoules }}</strong>
          <span class="text-caption font-medium text-text-2" i18n="@@conformite.kpi.purges.texte">jours du mois</span>
        </div>
      </div>

      <section class="flex flex-col gap-2" aria-labelledby="titre-demandes">
        <h2 id="titre-demandes" class="m-0 text-h3 font-semibold" i18n="@@conformite.demandes">Demandes d'effacement</h2>
        @if (demandes().length === 0) {
          <p class="m-0 rounded-lg border border-line bg-surface p-4 text-body text-text-2" i18n="@@conformite.demandes.vide">Aucune demande d'effacement.</p>
        } @else {
          <div class="overflow-hidden rounded-lg border border-line bg-surface" role="table" i18n-aria-label="@@conformite.demandes" aria-label="Demandes d'effacement">
            <div class="grid h-10 grid-cols-[130px_1fr_1fr_1.2fr_150px] items-center bg-surface-2 px-4 text-caption font-semibold text-text-2" role="row">
              <span role="columnheader" i18n="@@conformite.col.reference">Référence</span>
              <span role="columnheader" i18n="@@conformite.col.recue">Reçue le</span>
              <span role="columnheader" i18n="@@conformite.col.echeance">Échéance légale</span>
              <span role="columnheader" i18n="@@conformite.col.statut">Statut</span>
              <span role="columnheader"></span>
            </div>
            @for (demande of demandes(); track demande.id) {
              <div class="grid min-h-12 grid-cols-[130px_1fr_1fr_1.2fr_150px] items-center border-t border-line px-4 text-label" role="row">
                <span class="font-mono text-caption" role="cell">{{ demande.reference }}</span>
                <span class="text-text-2" role="cell">{{ jour(demande.recueLe) }}</span>
                <span role="cell" [class]="proche(demande) ? 'font-semibold text-danger' : 'text-text-2'">{{ jour(demande.echeanceLe) }}</span>
                <span role="cell">
                  @if (demande.statut === 'RECUE') {
                    <fg-badge ton="attention" i18n="@@conformite.statut.recue">À exécuter</fg-badge>
                  } @else if (demande.traiteeParLeSysteme) {
                    <fg-badge ton="neutre" i18n="@@conformite.statut.systeme">Exécutée par le système le {{ jour(demande.traiteeLe) }}</fg-badge>
                  } @else {
                    <fg-badge ton="succes" i18n="@@conformite.statut.traitee">Exécutée le {{ jour(demande.traiteeLe) }}</fg-badge>
                  }
                </span>
                <span class="flex justify-end" role="cell">
                  @if (demande.statut === 'RECUE') {
                    <button fg-button variante="secondary" taille="sm" type="button" (click)="choisie.set(demande)" i18n="@@conformite.executer">Exécuter</button>
                  }
                </span>
              </div>
            }
          </div>
        }
      </section>

      <div class="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <section class="flex flex-col gap-2" aria-labelledby="titre-durees">
          <h2 id="titre-durees" class="m-0 text-h3 font-semibold" i18n="@@conformite.durees">Durées de conservation</h2>
          <dl class="m-0 flex flex-col rounded-lg border border-line bg-surface">
            @for (duree of t.conservation; track duree.donnee) {
              <div class="flex items-baseline justify-between gap-4 border-b border-line px-4 py-3 last:border-b-0">
                <dt class="text-label font-medium">{{ duree.donnee }}</dt>
                <dd class="m-0 text-right text-label text-text-2">{{ duree.duree }}</dd>
              </div>
            }
          </dl>
        </section>
        <section class="flex flex-col gap-2" aria-labelledby="titre-purges">
          <h2 id="titre-purges" class="m-0 text-h3 font-semibold" i18n="@@conformite.purges">Purges du mois</h2>
          @if (t.purges.length === 0) {
            <p class="m-0 rounded-lg border border-line bg-surface p-4 text-body text-text-2" i18n="@@conformite.purges.vide">Aucune purge n'a encore tourné ce mois-ci.</p>
          } @else {
            <dl class="m-0 flex flex-col rounded-lg border border-line bg-surface">
              @for (purge of t.purges; track purge.traitement) {
                <div class="flex items-baseline justify-between gap-4 border-b border-line px-4 py-3 last:border-b-0">
                  <dt class="text-label font-medium">{{ traitement(purge.traitement) }}</dt>
                  <dd class="m-0 text-right text-label text-text-2 tabular-nums" i18n="@@conformite.purge.ligne">{{ purge.elements }} supprimés · dernière le {{ heure(purge.derniereExecution) }}</dd>
                </div>
              }
            </dl>
          }
        </section>
      </div>

      <button fg-button variante="secondary" type="button" class="self-start" [chargement]="export()" (click)="exporter()" i18n="@@conformite.rapport">Exporter le rapport de {{ moisPrecedent.libelle }} (PDF)</button>
    } @else if (!erreur()) {
      <fg-skeleton forme="carte" />
      <fg-skeleton forme="carte" />
    }

    <fg-sheet [titre]="titreConfirmation()" [ouverte]="choisie() !== null" (fermee)="choisie.set(null)">
      <p class="m-0 text-body text-text-2" i18n="@@conformite.confirmer.texte">Les données du compte et des enfants dont il est le seul tuteur seront supprimées dans tous les modules, puis un accusé partira par SMS. Cette action est définitive et journalisée.</p>
      <button fg-button type="button" [chargement]="execution()" (click)="executer()" i18n="@@conformite.confirmer">Exécuter l'effacement</button>
      <button fg-button variante="ghost" type="button" (click)="choisie.set(null)" i18n="@@commun.annuler">Annuler</button>
    </fg-sheet>
  `,
  host: { class: 'contents' },
})
export class Conformite {
  private readonly client = inject(ClientConformite);
  private readonly router = inject(Router);

  protected readonly tableau = signal<TableauConformite | null>(null);
  protected readonly demandes = signal<readonly DemandeEffacement[]>([]);
  protected readonly choisie = signal<DemandeEffacement | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly execution = signal(false);
  protected readonly export = signal(false);
  protected readonly moisPrecedent = moisPrecedent(new Date());

  protected readonly titreConfirmation = computed(() => {
    const reference = this.choisie()?.reference ?? '';
    return $localize`:@@conformite.confirmer.titre:Exécuter la demande ${reference}:reference: ?`;
  });

  constructor() {
    this.charger();
  }

  protected jour(iso: string | null): string {
    return iso ? JOUR.format(new Date(iso)) : '';
  }

  protected heure(iso: string): string {
    return HEURE.format(new Date(iso));
  }

  protected traitement(code: string): string {
    return TRAITEMENTS[code] ?? code;
  }

  /** L'échéance légale approche : moins de sept jours. */
  protected proche(demande: DemandeEffacement): boolean {
    return demande.statut === 'RECUE' && Date.parse(demande.echeanceLe) - Date.now() < 7 * 86_400_000;
  }

  protected executer(): void {
    const demande = this.choisie();
    if (!demande || this.execution()) {
      return;
    }
    this.execution.set(true);
    this.client.executer(demande.id).subscribe({
      next: () => {
        this.execution.set(false);
        this.choisie.set(null);
        this.charger();
      },
      error: (cause: unknown) => {
        this.execution.set(false);
        this.choisie.set(null);
        this.signaler(cause);
      },
    });
  }

  protected exporter(): void {
    this.export.set(true);
    this.erreur.set(null);
    this.client.rapport(this.moisPrecedent.code).subscribe({
      next: (pdf) => {
        this.export.set(false);
        const adresse = URL.createObjectURL(pdf);
        const lien = document.createElement('a');
        lien.href = adresse;
        lien.download = `rapport-conformite-${this.moisPrecedent.code}.pdf`;
        lien.click();
        URL.revokeObjectURL(adresse);
      },
      error: (cause: unknown) => {
        this.export.set(false);
        this.signaler(cause);
      },
    });
  }

  private charger(): void {
    this.erreur.set(null);
    forkJoin({ tableau: this.client.tableau(), demandes: this.client.demandes() }).subscribe({
      next: ({ tableau, demandes }) => {
        this.tableau.set(tableau);
        this.demandes.set(demandes);
      },
      error: (cause: unknown) => this.signaler(cause),
    });
  }

  private signaler(cause: unknown): void {
    if (estRefus(cause)) {
      void this.router.navigate(['/refuse']);
    } else {
      this.erreur.set(erreurLisible(cause).message);
    }
  }
}

/** Le rapport porte sur le dernier mois complet. */
export function moisPrecedent(aujourdhui: Date): { code: string; libelle: string } {
  const premier = new Date(aujourdhui.getFullYear(), aujourdhui.getMonth() - 1, 1);
  return { code: `${premier.getFullYear()}-${String(premier.getMonth() + 1).padStart(2, '0')}`, libelle: MOIS.format(premier) };
}
