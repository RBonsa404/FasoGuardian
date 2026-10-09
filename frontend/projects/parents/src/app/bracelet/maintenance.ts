import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ClientBracelet, ClientFamille, ContactUrgence, FicheEnfant, SuiviMaintenance } from 'api';
import { FgBadge, FgBanniere, FgIcon, FgSquelette } from 'ui';

import { erreurLisible, estIntrouvable } from '../commun/erreurs';
import { heure } from '../commun/temps';

interface Etape {
  readonly titre: string;
  readonly detail: string;
  readonly heure: string;
  /** Franchie, en cours ou à venir : la pastille le dit, le texte aussi. */
  readonly etat: 'faite' | 'attente';
}

/**
 * Maintenance (écran 40, US-SAV-001) : quand le bracelet ne répond plus, un ticket est ouvert de lui-même au
 * service après-vente. Le parent en suit les étapes et voit ce qu'il peut vérifier en attendant.
 */
@Component({
  selector: 'app-maintenance',
  imports: [RouterLink, FgBadge, FgBanniere, FgIcon, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="-ml-2.5 grid size-11 place-items-center rounded-md focus-visible:outline-2 focus-visible:outline-accent" [routerLink]="['/enfants', id(), 'bracelet']" i18n-aria-label="@@etape.retour" aria-label="Retour">
      <fg-icon nom="retour" [taille]="22" />
    </a>
    @if (suivi(); as s) {
      <div class="flex flex-col gap-2">
        <fg-badge class="self-start" ton="attention"><ng-container i18n="@@maintenance.ticket">Ticket</ng-container> {{ s.reference }} · <ng-container i18n="@@maintenance.automatique">ouvert automatiquement</ng-container></fg-badge>
        <h1 class="m-0 text-titre-ecran font-bold tracking-tight" i18n="@@maintenance.titre">Le bracelet de {{ prenom() }} ne répond plus</h1>
        <p class="m-0 text-label text-text-2">{{ dernierContact() }}</p>
      </div>

      <ol class="m-0 flex list-none flex-col rounded-lg border border-line bg-surface p-4" i18n-aria-label="@@maintenance.etapes" aria-label="Étapes du ticket">
        @for (etape of etapes(); track etape.titre; let derniere = $last) {
          <li class="flex gap-3">
            <div class="flex flex-col items-center" aria-hidden="true">
              <span class="mt-1 size-3 rounded-full" [class]="etape.etat === 'faite' ? 'bg-accent' : 'bg-line-strong'"></span>
              @if (!derniere) {
                <span class="min-h-4.5 w-0.5 flex-1 bg-line"></span>
              }
            </div>
            <div class="flex flex-1 justify-between gap-3 pb-3">
              <div class="flex flex-col gap-0.5">
                <strong class="text-label font-semibold">{{ etape.titre }}</strong>
                <span class="text-caption text-text-3">{{ etape.detail }}</span>
              </div>
              <span class="text-caption font-semibold text-text-2 tabular-nums">{{ etape.heure }}</span>
            </div>
          </li>
        }
      </ol>

      <p class="m-0 rounded-banner bg-surface-2 p-3.5 text-label font-medium text-text-2" i18n="@@maintenance.verifier">À vérifier : le bracelet est-il chargé ? {{ prenom() }} est-il dans un bâtiment sans réseau ?</p>

      <div class="mt-auto flex flex-col gap-2">
        @if (contact(); as c) {
          <a class="grid min-h-13 place-items-center rounded-md bg-primary px-4 text-center text-body font-semibold text-white hover:bg-primary-hover focus-visible:outline-2 focus-visible:outline-accent" [href]="'tel:' + c.telephone"><ng-container i18n="@@maintenance.appeler">Appeler</ng-container> {{ c.nom }} ({{ c.lien }})</a>
        }
        <a class="grid min-h-12 place-items-center rounded-md border-(length:--border-width-trait) border-line-strong px-4 text-center text-label font-semibold focus-visible:outline-2 focus-visible:outline-accent" [routerLink]="['/enfants', id(), 'bracelet']" i18n="@@maintenance.bracelet">Voir le bracelet</a>
      </div>
    } @else if (aucun()) {
      <h1 class="m-0 text-titre-ecran font-bold tracking-tight" i18n="@@maintenance.aucun.titre">Aucun incident en cours</h1>
      <fg-banner ton="succes" i18n="@@maintenance.aucun">Le bracelet répond normalement : aucun ticket de maintenance n'est ouvert.</fg-banner>
      <a class="mt-auto grid min-h-12 place-items-center rounded-md border-(length:--border-width-trait) border-line-strong px-4 text-center text-label font-semibold focus-visible:outline-2 focus-visible:outline-accent" [routerLink]="['/enfants', id(), 'bracelet']" i18n="@@maintenance.bracelet">Voir le bracelet</a>
    } @else if (erreur(); as message) {
      <fg-banner ton="erreur">{{ message }}</fg-banner>
    } @else {
      <fg-skeleton forme="carte" />
      <fg-skeleton forme="carte" />
    }
  `,
  host: { class: 'mx-auto flex min-h-dvh w-full max-w-md flex-col gap-4 px-5 pt-6 pb-7 lg:mx-0 lg:max-w-2xl lg:px-10 lg:pt-10' },
})
export class MaintenanceBracelet {
  readonly id = input.required<string>();

  private readonly client = inject(ClientBracelet);
  private readonly famille = inject(ClientFamille);

  protected readonly suivi = signal<SuiviMaintenance | null>(null);
  protected readonly aucun = signal(false);
  protected readonly erreur = signal<string | null>(null);
  private readonly enfant = signal<FicheEnfant | null>(null);
  /** Premier contact d'urgence : la personne à joindre pour savoir où est l'enfant. */
  protected readonly contact = signal<ContactUrgence | null>(null);

  protected readonly prenom = computed(() => this.enfant()?.prenom ?? $localize`:@@maintenance.enfant:votre enfant`);

  protected readonly dernierContact = computed(() => {
    const s = this.suivi();
    if (!s?.dernierContact) {
      return $localize`:@@maintenance.jamais:Le bracelet n'a encore jamais donné de nouvelles.`;
    }
    const morceaux = [$localize`:@@maintenance.dernier:Dernier contact ${heure(s.dernierContact)}:heure:`];
    if (s.batterie !== null) {
      morceaux.push($localize`:@@maintenance.batterie:batterie ${s.batterie}:niveau: %`);
    }
    if (s.reseau) {
      morceaux.push(s.reseau);
    }
    return morceaux.join(' · ') + '.';
  });

  protected readonly etapes = computed<Etape[]>(() => {
    const s = this.suivi();
    if (!s) {
      return [];
    }
    const pris = s.prisEnChargeLe;
    return [
      { titre: $localize`:@@maintenance.etape.muet:Bracelet muet détecté`, detail: $localize`:@@maintenance.etape.muet.detail:3 intervalles sans nouvelles`, heure: heure(s.ouvertLe), etat: 'faite' },
      { titre: $localize`:@@maintenance.etape.notifies:Tuteurs notifiés`, detail: $localize`:@@maintenance.etape.notifies.detail:Notification + SMS`, heure: heure(s.ouvertLe), etat: 'faite' },
      {
        titre: $localize`:@@maintenance.etape.ticket:Ticket SAV ouvert`,
        detail: pris ? $localize`:@@maintenance.etape.ticket.pris:Pris en charge par un agent` : $localize`:@@maintenance.etape.ticket.attente:En attente d'un agent`,
        heure: heure(pris ?? s.ouvertLe),
        etat: 'faite',
      },
      { titre: $localize`:@@maintenance.etape.diagnostic:Diagnostic à distance`, detail: $localize`:@@maintenance.etape.diagnostic.detail:En attente de reconnexion`, heure: '—', etat: 'attente' },
    ];
  });

  constructor() {
    effect(() => {
      const id = this.id();
      this.client.maintenance(id).subscribe({
        next: (suivi) => this.suivi.set(suivi),
        error: (cause: unknown) => (estIntrouvable(cause) ? this.aucun.set(true) : this.erreur.set(erreurLisible(cause).message)),
      });
      this.famille.enfant(id).subscribe({ next: (enfant) => this.enfant.set(enfant), error: () => undefined });
      this.famille.contacts(id).subscribe({ next: (contacts) => this.contact.set(contacts[0] ?? null), error: () => undefined });
    });
  }
}
