import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';

import { ClientZones, SafeZone, SafeZones } from 'api';
import { FgBadge, FgBanniere, FgBouton, FgFeuille, FgIcon, FgSquelette } from 'ui';

import { erreurLisible } from '../commun/erreurs';
import { SecondFacteur } from '../commun/second-facteur';
import { iconeDe, resume } from './libelles';

type Action = 'suspendre' | 'supprimer';

/**
 * Safe Zones de l'enfant (écrans 21 et 23, US-PAR-007) : liste avec l'état du moment ; suspendre, réactiver,
 * modifier ou supprimer une zone. Suspendre et supprimer sont confirmés par code SMS.
 */
@Component({
  selector: 'app-zones',
  imports: [RouterLink, FgBadge, FgBanniere, FgBouton, FgFeuille, FgIcon, FgSquelette, SecondFacteur],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="-ml-2.5 grid size-11 place-items-center rounded-md focus-visible:outline-2 focus-visible:outline-accent" [routerLink]="['/enfants', id()]" i18n-aria-label="@@etape.retour" aria-label="Retour">
      <fg-icon nom="retour" [taille]="22" />
    </a>
    <div class="flex items-baseline justify-between gap-3">
      <h1 class="m-0 text-titre-ecran font-bold tracking-tight" i18n="@@zones.titre">Safe Zones</h1>
      @if (donnees(); as d) {
        <span class="text-label font-semibold text-text-2 tabular-nums" i18n-aria-label="@@zones.quota" [attr.aria-label]="d.zones.length + ' zones sur ' + d.maximum">{{ d.zones.length }} / {{ d.maximum }}</span>
      }
    </div>

    @if (donnees(); as d) {
      @for (zone of d.zones; track zone.id) {
        <button type="button" class="flex items-center gap-3 rounded-lg border border-line bg-surface p-4 text-left focus-visible:outline-2 focus-visible:outline-accent" (click)="ouvrir(zone)">
          <span class="grid size-11 flex-none place-items-center rounded-full" [class]="pastille(zone)" aria-hidden="true"><fg-icon [nom]="icone(zone)" [taille]="20" /></span>
          <span class="flex min-w-0 flex-1 flex-col gap-0.5">
            <strong class="truncate text-body font-semibold">{{ zone.nom }}</strong>
            <span class="text-label text-text-2">{{ resume(zone) }}</span>
          </span>
          @if (zone.sortieEnCours) {
            <fg-badge ton="alerte" icone="sortie-zone" i18n="@@zones.sortie">Sortie en cours</fg-badge>
          } @else if (zone.statut === 'SUSPENDUE') {
            <fg-badge ton="attention" i18n="@@zones.suspendue">Suspendue</fg-badge>
          } @else if (zone.dansLaPlage) {
            <fg-badge ton="succes" i18n="@@zones.active">Active</fg-badge>
          } @else {
            <fg-badge ton="neutre" i18n="@@zones.horsPlage">Hors plage horaire</fg-badge>
          }
        </button>
      } @empty {
        <p class="m-0 rounded-lg border border-line bg-surface p-5 text-body text-text-2" i18n="@@zones.vide">Aucune zone pour l'instant. Tracez l'école ou la maison : vous serez prévenu si votre enfant en sort pendant les heures prévues.</p>
      }
      @if (erreur(); as message) {
        <fg-banner ton="erreur">{{ message }}</fg-banner>
      }
      <button fg-button class="mt-auto" taille="lg" type="button" [disabled]="d.zones.length >= d.maximum" (click)="creer()" i18n="@@zones.ajouter">Ajouter une zone</button>
      @if (d.zones.length >= d.maximum) {
        <p class="m-0 text-center text-label text-text-3" i18n="@@zones.maximum">Votre offre permet {{ d.maximum }} zones.</p>
      }
    } @else if (erreur(); as message) {
      <fg-banner ton="erreur">{{ message }}</fg-banner>
    } @else {
      <fg-skeleton forme="carte" />
      <fg-skeleton forme="carte" />
    }

    <fg-sheet [titre]="choisie()?.nom ?? ''" [ouverte]="feuille()" (fermee)="feuille.set(false)">
      @if (choisie(); as zone) {
        <p class="m-0 text-label text-text-2">{{ resume(zone) }}</p>
        @if (zone.statut === 'ACTIVE') {
          <p class="m-0 text-body text-text-2" i18n="@@zones.suspendre.texte">Suspendue, la zone garde sa configuration mais aucune sortie n'est signalée. SOS et retrait restent actifs.</p>
          <button fg-button type="button" (click)="confirmer('suspendre')" i18n="@@zones.suspendre">Suspendre · code SMS</button>
        } @else {
          <button fg-button type="button" [chargement]="enCours()" (click)="reactiver(zone)" i18n="@@zones.reactiver">Réactiver</button>
        }
        <button fg-button variante="secondary" type="button" (click)="modifier(zone)" i18n="@@zones.modifier">Modifier</button>
        <button fg-button variante="ghost" type="button" class="text-danger" (click)="confirmer('supprimer')" i18n="@@zones.supprimer">Supprimer la zone</button>
      }
    </fg-sheet>

    <app-second-facteur
      action="MODIFIER_SAFE_ZONE"
      [ouverte]="action() !== null"
      [explication]="explication()"
      [erreur]="erreurCode()"
      (saisi)="executer($event)"
      (annule)="action.set(null)"
    />
  `,
  host: { class: 'mx-auto flex min-h-dvh w-full max-w-md flex-col gap-4 px-5 pt-6 pb-7' },
})
export class Zones {
  readonly id = input.required<string>();

  private readonly client = inject(ClientZones);
  private readonly router = inject(Router);

  protected readonly resume = resume;
  protected readonly icone = iconeDe;
  protected readonly donnees = signal<SafeZones | null>(null);
  protected readonly choisie = signal<SafeZone | null>(null);
  protected readonly feuille = signal(false);
  protected readonly action = signal<Action | null>(null);
  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly erreurCode = signal<string | null>(null);
  protected readonly explication = signal('');

  constructor() {
    effect(() => this.charger(this.id()));
  }

  protected pastille(zone: SafeZone): string {
    return zone.statut === 'ACTIVE' && zone.dansLaPlage ? 'bg-success-soft text-success' : 'border border-dashed border-text-3 text-text-2';
  }

  protected ouvrir(zone: SafeZone): void {
    this.choisie.set(zone);
    this.erreur.set(null);
    this.feuille.set(true);
  }

  protected creer(): void {
    void this.router.navigate(['/enfants', this.id(), 'zones', 'nouvelle']);
  }

  protected modifier(zone: SafeZone): void {
    void this.router.navigate(['/enfants', this.id(), 'zones', zone.id]);
  }

  protected confirmer(action: Action): void {
    this.feuille.set(false);
    this.erreurCode.set(null);
    this.explication.set(
      action === 'suspendre'
        ? $localize`:@@zones.code.suspendre:Saisissez le code reçu par SMS pour suspendre « ${this.choisie()?.nom}:zone: ».`
        : $localize`:@@zones.code.supprimer:Saisissez le code reçu par SMS pour supprimer « ${this.choisie()?.nom}:zone: ».`,
    );
    this.action.set(action);
  }

  protected executer(code: string): void {
    const zone = this.choisie();
    const action = this.action();
    if (!zone || !action || this.enCours()) {
      return;
    }
    this.enCours.set(true);
    this.erreurCode.set(null);
    const appel: Observable<unknown> = action === 'suspendre' ? this.client.suspendre(this.id(), zone.id, code) : this.client.supprimer(this.id(), zone.id, code);
    appel.subscribe({
      next: () => {
        this.enCours.set(false);
        this.action.set(null);
        this.charger(this.id());
      },
      error: (cause: unknown) => {
        this.enCours.set(false);
        const lisible = erreurLisible(cause);
        if (lisible.code.startsWith('CODE_')) {
          this.erreurCode.set(lisible.message);
        } else {
          this.action.set(null);
          this.erreur.set(lisible.message);
        }
      },
    });
  }

  protected reactiver(zone: SafeZone): void {
    this.enCours.set(true);
    this.client.reactiver(this.id(), zone.id).subscribe({
      next: () => {
        this.enCours.set(false);
        this.feuille.set(false);
        this.charger(this.id());
      },
      error: (cause: unknown) => {
        this.enCours.set(false);
        this.feuille.set(false);
        this.erreur.set(erreurLisible(cause).message);
      },
    });
  }

  private charger(id: string): void {
    this.client.zones(id).subscribe({
      next: (donnees) => this.donnees.set(donnees),
      error: (cause: unknown) => this.erreur.set(erreurLisible(cause).message),
    });
  }
}
