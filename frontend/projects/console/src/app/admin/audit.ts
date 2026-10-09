import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';

import { ChaineAudit, ClientConformite, EntreeAudit, FiltreAudit } from 'api';
import { FgBanniere, FgBouton, FgIcon, FgSquelette } from 'ui';

import { erreurLisible, estRefus } from '../commun/acces';

const TAILLE = 50;
const HORODATAGE = new Intl.DateTimeFormat('fr-FR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' });
const HEURE = new Intl.DateTimeFormat('fr-FR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });

/**
 * Journal d'audit (écran 70, US-ADM-002) : entrées les plus récentes d'abord, état de la chaîne d'empreintes
 * et vérification à la demande. Le journal ne montre que des identifiants techniques.
 */
@Component({
  selector: 'app-audit',
  imports: [ReactiveFormsModule, FgBanniere, FgBouton, FgIcon, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex flex-wrap items-center justify-between gap-3">
      <h1 class="m-0 text-titre-ecran font-bold tracking-tight" i18n="@@audit.titre">Journal d'audit</h1>
      @if (chaine(); as c) {
        @if (c.integre) {
          <span class="flex items-center gap-2 rounded-full bg-success-soft px-3 py-1.5 text-label font-semibold text-success" role="status">
            <fg-icon nom="valider" [taille]="16" />
            @if (c.verifieeLe) {
              <ng-container i18n="@@audit.integre">Chaîne intègre · vérifiée {{ heure(c.verifieeLe) }}</ng-container>
            } @else {
              <ng-container i18n="@@audit.jamais">Chaîne pas encore vérifiée</ng-container>
            }
          </span>
        }
      }
    </div>

    @if (chaine(); as c) {
      @if (!c.integre) {
        <fg-banner ton="erreur" i18n="@@audit.rompue">Rupture détectée à l'entrée {{ c.entreeAlteree }} : le journal a été altéré. Prévenez le délégué à la protection des données et conservez la base en l'état.</fg-banner>
      }
    }

    <form class="flex flex-wrap items-end gap-3" (submit)="$event.preventDefault(); filtrer()">
      <label class="flex flex-col gap-1.5 text-label font-semibold">
        <span i18n="@@audit.filtre.action">Action</span>
        <input class="h-11 w-64 rounded-md border border-line-strong bg-surface px-3 font-mono text-caption uppercase outline-none focus-visible:outline-2 focus-visible:outline-accent" [formControl]="action" placeholder="COMPTE_CLOS" maxlength="64" />
      </label>
      <label class="flex flex-col gap-1.5 text-label font-semibold">
        <span i18n="@@audit.filtre.role">Rôle</span>
        <select class="h-11 rounded-md border border-line-strong bg-surface px-3 text-label outline-none focus-visible:outline-2 focus-visible:outline-accent" [formControl]="role">
          <option value="" i18n="@@audit.filtre.tous">Tous</option>
          @for (r of roles; track r) {
            <option [value]="r">{{ r }}</option>
          }
        </select>
      </label>
      <label class="flex flex-col gap-1.5 text-label font-semibold">
        <span i18n="@@audit.filtre.resultat">Résultat</span>
        <select class="h-11 rounded-md border border-line-strong bg-surface px-3 text-label outline-none focus-visible:outline-2 focus-visible:outline-accent" [formControl]="resultat">
          <option value="" i18n="@@audit.filtre.tous">Tous</option>
          <option value="SUCCES" i18n="@@audit.succes">Succès</option>
          <option value="REFUS" i18n="@@audit.refus">Refus</option>
        </select>
      </label>
      <button fg-button variante="secondary" type="submit" i18n="@@audit.filtrer">Filtrer</button>
      <button fg-button variante="secondary" type="button" class="ml-auto" [chargement]="verification()" (click)="verifier()" i18n="@@audit.verifier">Vérifier la chaîne</button>
    </form>

    @if (erreur(); as message) {
      <fg-banner ton="erreur">{{ message }}</fg-banner>
    }
    @if (entrees(); as liste) {
      @if (liste.length === 0) {
        <p class="m-0 rounded-lg border border-line bg-surface p-6 text-center text-body text-text-2" i18n="@@audit.vide">Aucune entrée ne correspond à ces critères.</p>
      } @else {
        <div class="overflow-hidden rounded-lg border border-line bg-surface" role="table" i18n-aria-label="@@audit.table" aria-label="Entrées du journal d'audit">
          <div class="grid h-10 grid-cols-[150px_150px_1.4fr_1.2fr_90px_110px] items-center bg-surface-2 px-4 text-caption font-semibold text-text-2" role="row">
            <span role="columnheader" aria-sort="descending" i18n="@@audit.col.horodatage">Horodatage</span>
            <span role="columnheader" i18n="@@audit.col.agent">Agent</span>
            <span role="columnheader" i18n="@@audit.col.action">Action</span>
            <span role="columnheader" i18n="@@audit.col.cible">Cible</span>
            <span role="columnheader" i18n="@@audit.col.resultat">Résultat</span>
            <span role="columnheader" i18n="@@audit.col.empreinte">Empreinte</span>
          </div>
          @for (entree of liste; track entree.id) {
            <div class="grid min-h-11 grid-cols-[150px_150px_1.4fr_1.2fr_90px_110px] items-center border-t border-line px-4 text-label" role="row">
              <span class="tabular-nums text-text-2" role="cell">{{ horodatage(entree.horodatage) }}</span>
              <span class="truncate" role="cell" [attr.title]="entree.acteurId">{{ acteur(entree) }}</span>
              <span class="truncate font-mono text-caption" role="cell">{{ entree.action }}</span>
              <span class="truncate text-text-2" role="cell" [attr.title]="entree.cibleId">{{ cible(entree) }}</span>
              <span role="cell" [class]="entree.resultat === 'REFUS' ? 'font-semibold text-danger' : 'text-text-2'">{{ entree.resultat === 'REFUS' ? libelleRefus : libelleSucces }}</span>
              <span class="font-mono text-caption text-text-3" role="cell">{{ entree.empreinte }}</span>
            </div>
          }
        </div>
        <div class="flex items-center justify-between gap-3 text-label text-text-2">
          <span i18n="@@audit.pagination">{{ debut() }} à {{ fin() }} sur {{ total() }}</span>
          <span class="flex gap-2">
            <button fg-button variante="secondary" taille="sm" type="button" [disabled]="page() === 0" (click)="aller(page() - 1)" i18n="@@audit.precedent">Plus récentes</button>
            <button fg-button variante="secondary" taille="sm" type="button" [disabled]="fin() >= total()" (click)="aller(page() + 1)" i18n="@@audit.suivant">Plus anciennes</button>
          </span>
        </div>
      }
    } @else if (!erreur()) {
      <fg-skeleton forme="carte" />
      <fg-skeleton forme="carte" />
    }
  `,
  host: { class: 'contents' },
})
export class Audit {
  private readonly client = inject(ClientConformite);
  private readonly router = inject(Router);

  protected readonly roles = ['PARENT', 'KYC', 'SUPPORT', 'SAV', 'ADMIN', 'FDS', 'SYSTEME'] as const;
  protected readonly libelleSucces = $localize`:@@audit.succes:Succès`;
  protected readonly libelleRefus = $localize`:@@audit.refus:Refus`;

  protected readonly action = new FormControl('', { nonNullable: true });
  protected readonly role = new FormControl('', { nonNullable: true });
  protected readonly resultat = new FormControl<'' | 'SUCCES' | 'REFUS'>('', { nonNullable: true });

  protected readonly entrees = signal<readonly EntreeAudit[] | null>(null);
  protected readonly chaine = signal<ChaineAudit | null>(null);
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly erreur = signal<string | null>(null);
  protected readonly verification = signal(false);

  protected readonly debut = computed(() => (this.total() === 0 ? 0 : this.page() * TAILLE + 1));
  protected readonly fin = computed(() => Math.min(this.total(), this.page() * TAILLE + (this.entrees()?.length ?? 0)));

  constructor() {
    this.aller(0);
  }

  protected horodatage(iso: string): string {
    return HORODATAGE.format(new Date(iso));
  }

  protected heure(iso: string): string {
    return HEURE.format(new Date(iso));
  }

  /** Le journal ne porte que des identifiants : le rôle, suivi du début de l'identifiant du compte. */
  protected acteur(entree: EntreeAudit): string {
    return entree.acteurId ? `${entree.role} · ${entree.acteurId.slice(0, 8)}` : $localize`:@@audit.systeme:Système`;
  }

  protected cible(entree: EntreeAudit): string {
    return entree.cibleId ? `${entree.typeCible} · ${entree.cibleId}` : entree.typeCible;
  }

  protected filtrer(): void {
    this.aller(0);
  }

  protected aller(page: number): void {
    this.erreur.set(null);
    const filtre: FiltreAudit = {
      action: this.action.value.trim().toUpperCase() || undefined,
      role: this.role.value || undefined,
      resultat: this.resultat.value || undefined,
    };
    this.client.journal(filtre, page, TAILLE).subscribe({
      next: (resultat) => {
        this.page.set(resultat.page);
        this.total.set(resultat.total);
        this.entrees.set(resultat.entrees);
        this.chaine.set(resultat.chaine);
      },
      error: (cause: unknown) => this.signaler(cause),
    });
  }

  protected verifier(): void {
    this.verification.set(true);
    this.erreur.set(null);
    this.client.verifierLaChaine().subscribe({
      next: (chaine) => {
        this.verification.set(false);
        this.chaine.set(chaine);
        this.aller(0);
      },
      error: (cause: unknown) => {
        this.verification.set(false);
        this.signaler(cause);
      },
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
