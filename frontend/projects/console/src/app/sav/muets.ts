import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { ClientSav, ResolutionTicket, TicketMaintenance } from 'api';
import { FgBadge, FgBanniere, FgBouton, FgFeuille, FgSquelette } from 'ui';

import { erreurLisible, estRefus } from '../commun/acces';
import { LIBELLES_RESOLUTION, RESOLUTIONS, depuis, jourEtHeure } from './libelles';

/**
 * Bracelets muets (écrans 66 et 67, US-SAV-001) : tickets ouverts par la supervision quand un bracelet n'a rien
 * émis depuis plus de trois intervalles. L'agent prend un ticket en charge puis dit comment il s'est résolu ;
 * le parent a déjà été informé par la plateforme.
 */
@Component({
  selector: 'app-muets',
  imports: [ReactiveFormsModule, RouterLink, FgBadge, FgBanniere, FgBouton, FgFeuille, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex flex-col gap-1">
      <h1 class="m-0 text-titre-ecran font-bold tracking-tight" i18n="@@muets.titre">Bracelets muets</h1>
      <span class="text-label text-text-2" i18n="@@muets.sousTitre">&gt; 3 intervalles sans nouvelles · ticket ouvert automatiquement</span>
    </div>
    <div class="flex gap-2" role="group" i18n-aria-label="@@muets.vue" aria-label="Tickets affichés">
      <button fg-button taille="sm" type="button" [variante]="resolus() ? 'secondary' : 'primary'" [attr.aria-pressed]="!resolus()" (click)="montrer(false)" i18n="@@muets.aTraiter">À traiter</button>
      <button fg-button taille="sm" type="button" [variante]="resolus() ? 'primary' : 'secondary'" [attr.aria-pressed]="resolus()" (click)="montrer(true)" i18n="@@muets.resolus">Résolus</button>
    </div>
    @if (erreur(); as message) {
      <fg-banner ton="erreur">{{ message }}</fg-banner>
    }
    @if (tickets(); as liste) {
      @if (liste.length === 0) {
        <p class="m-0 rounded-lg border border-line bg-surface p-6 text-center text-body text-text-2">
          @if (resolus()) {
            <ng-container i18n="@@muets.vide.resolus">Aucun ticket résolu.</ng-container>
          } @else {
            <ng-container i18n="@@muets.vide">Tous les bracelets en service donnent des nouvelles.</ng-container>
          }
        </p>
      } @else {
        <ul class="m-0 flex list-none flex-col gap-2 p-0">
          @for (ticket of liste; track ticket.id) {
            <li class="flex flex-wrap items-center gap-4 rounded-lg border border-line bg-surface p-4">
              <div class="flex w-20 flex-col">
                <strong class="font-display text-h3 font-bold tabular-nums" [class.text-danger]="ticket.statut === 'OUVERT'">{{ depuis(ticket.ouvertLe) }}</strong>
                <span class="text-caption text-text-3" i18n="@@muets.ouvert">ouvert</span>
              </div>
              <div class="flex min-w-0 flex-1 flex-col gap-0.5">
                <span class="flex flex-wrap items-center gap-2 text-body font-semibold">
                  <span class="font-mono text-label">{{ ticket.reference }}</span>
                  <a class="font-mono text-label text-accent underline focus-visible:outline-2 focus-visible:outline-accent" [routerLink]="['/sav/parc', ticket.numeroSerie]">{{ ticket.numeroSerie }}</a>
                  @switch (ticket.statut) {
                    @case ('OUVERT') {
                      <fg-badge ton="attention" i18n="@@muets.statut.ouvert">Non attribué</fg-badge>
                    }
                    @case ('EN_COURS') {
                      <fg-badge ton="accent">{{ ticket.prisEnChargeParMoi ? aMoi : attribue }}</fg-badge>
                    }
                    @case ('RESOLU') {
                      <fg-badge ton="succes">{{ ticket.resolution ? resolutions[ticket.resolution] : '' }}</fg-badge>
                    }
                  }
                </span>
                <span class="text-label text-text-2">{{ etat(ticket) }}</span>
                @if (ticket.note) {
                  <span class="text-label text-text-2">« {{ ticket.note }} »</span>
                }
              </div>
              @if (ticket.statut === 'OUVERT') {
                <button fg-button variante="secondary" taille="sm" type="button" (click)="prendre(ticket)" i18n="@@muets.prendre">Prendre en charge</button>
              }
              @if (ticket.statut !== 'RESOLU') {
                <button fg-button taille="sm" type="button" (click)="ouvrir(ticket)" i18n="@@muets.resoudre">Résoudre</button>
              }
            </li>
          }
        </ul>
      }
    } @else if (!erreur()) {
      <fg-skeleton forme="carte" />
      <fg-skeleton forme="carte" />
    }

    <fg-sheet [titre]="titre()" [ouverte]="choisi() !== null" (fermee)="choisi.set(null)">
      <fieldset class="m-0 flex flex-col gap-2 border-0 p-0">
        <legend class="pb-2 text-label font-semibold" i18n="@@muets.issue">Comment le ticket s'est-il résolu ?</legend>
        @for (option of options; track option.code) {
          <label class="flex min-h-11 cursor-pointer items-center gap-3 rounded-md border px-3 text-label font-medium focus-within:outline-2 focus-within:outline-accent" [class]="resolution.value === option.code ? 'border-accent bg-accent-soft' : 'border-line'">
            <input class="sr-only" type="radio" name="resolution" [formControl]="resolution" [value]="option.code" />{{ option.libelle }}
          </label>
        }
      </fieldset>
      <label class="flex flex-col gap-1.5 text-label font-semibold">
        <span i18n="@@muets.note">Note (facultative, sans donnée personnelle)</span>
        <textarea class="min-h-20 rounded-md border border-line-strong bg-surface p-3 text-label font-normal outline-none focus-visible:outline-2 focus-visible:outline-accent" [formControl]="note" maxlength="300"></textarea>
      </label>
      <button fg-button type="button" [chargement]="enCours()" (click)="resoudre()" i18n="@@muets.clore">Clore le ticket</button>
      <button fg-button variante="ghost" type="button" (click)="choisi.set(null)" i18n="@@commun.annuler">Annuler</button>
    </fg-sheet>
  `,
  host: { class: 'contents' },
})
export class Muets {
  private readonly client = inject(ClientSav);
  private readonly router = inject(Router);

  protected readonly options = RESOLUTIONS;
  protected readonly resolutions = LIBELLES_RESOLUTION;
  protected readonly depuis = depuis;
  protected readonly aMoi = $localize`:@@muets.statut.aMoi:À vous`;
  protected readonly attribue = $localize`:@@muets.statut.attribue:Attribué`;

  protected readonly tickets = signal<readonly TicketMaintenance[] | null>(null);
  protected readonly resolus = signal(false);
  protected readonly choisi = signal<TicketMaintenance | null>(null);
  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly titre = signal('');

  protected readonly resolution = new FormControl<ResolutionTicket>('RECHARGE', { nonNullable: true });
  protected readonly note = new FormControl('', { nonNullable: true });

  constructor() {
    this.charger();
  }

  /** État du bracelet relevé à l'ouverture du ticket : rien sur l'enfant ni sur sa position. */
  protected etat(ticket: TicketMaintenance): string {
    const contact = ticket.dernierContact
      ? $localize`:@@muets.contact:Dernier contact ${jourEtHeure(ticket.dernierContact)}:date:`
      : $localize`:@@muets.jamais:Aucun message depuis l'appairage`;
    const batterie = ticket.batterie === null ? '' : $localize`:@@muets.batterie: · batterie ${ticket.batterie}:niveau: %`;
    return contact + batterie + (ticket.reseau ? ` · ${ticket.reseau}` : '');
  }

  protected montrer(resolus: boolean): void {
    this.resolus.set(resolus);
    this.tickets.set(null);
    this.charger();
  }

  protected prendre(ticket: TicketMaintenance): void {
    this.erreur.set(null);
    this.client.prendreEnCharge(ticket.id).subscribe({
      next: () => this.charger(),
      error: (cause: unknown) => {
        this.signaler(cause);
        this.charger();
      },
    });
  }

  protected ouvrir(ticket: TicketMaintenance): void {
    this.resolution.setValue('RECHARGE');
    this.note.setValue('');
    this.titre.set($localize`:@@muets.clore.titre:Clore ${ticket.reference}:reference: · ${ticket.numeroSerie}:bracelet:`);
    this.choisi.set(ticket);
  }

  protected resoudre(): void {
    const ticket = this.choisi();
    if (!ticket || this.enCours()) {
      return;
    }
    this.enCours.set(true);
    this.erreur.set(null);
    this.client.resoudre(ticket.id, this.resolution.value, this.note.value.trim()).subscribe({
      next: () => {
        this.enCours.set(false);
        this.choisi.set(null);
        this.charger();
      },
      error: (cause: unknown) => {
        this.enCours.set(false);
        this.choisi.set(null);
        this.signaler(cause);
      },
    });
  }

  private charger(): void {
    this.client.tickets(this.resolus()).subscribe({
      next: (tickets) => this.tickets.set(tickets),
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
