import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { ClientSav, ResolutionTicket, TicketMaintenance } from 'api';
import { FgBadge, FgBanniere, FgBouton, FgChamp, FgFeuille, FgSquelette } from 'ui';

import { erreurLisible, estRefus } from '../commun/acces';
import { LIBELLES_RESOLUTION, RESOLUTIONS, depuis, jourEtHeure } from './libelles';

/** Étapes du traitement d'un bracelet muet, de la détection à la clôture (écran 67). */
export type EtapeTicket = 0 | 1 | 2 | 3 | 4;

/**
 * Étape où en est le ticket. Le rendez-vous est facultatif : un parent venu de lui-même au point relais passe
 * directement à la remise.
 */
export function etapeDe(ticket: TicketMaintenance, sansRendezVous = false): EtapeTicket {
  if (ticket.statut === 'RESOLU') {
    return 4;
  }
  if (ticket.statut === 'OUVERT') {
    return 0;
  }
  if (!ticket.diagnosticLe) {
    return 1;
  }
  return ticket.rendezVousLe || ticket.remplacement || sansRendezVous ? 3 : 2;
}

const DATE_LONGUE = new Intl.DateTimeFormat('fr-FR', { weekday: 'long', day: 'numeric', month: 'long', hour: '2-digit', minute: '2-digit' });

/**
 * Bracelets muets (écrans 66 et 67, US-SAV-001) : tickets ouverts par la supervision quand un bracelet n'a rien
 * émis depuis plus de trois intervalles, puis traités jusqu'à l'échange en point relais. Le service après-vente
 * ne voit que l'état du bracelet (ADR 0009) : le rendez-vous est transmis au parent par la plateforme, et c'est
 * le parent qui associe l'unité de remplacement avec son code (ADR 0031).
 */
@Component({
  selector: 'app-muets',
  imports: [ReactiveFormsModule, RouterLink, FgBadge, FgBanniere, FgBouton, FgChamp, FgFeuille, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (erreur(); as message) {
      <fg-banner ton="erreur">{{ message }}</fg-banner>
    }
    <div class="grid min-h-0 grid-cols-1 overflow-hidden rounded-xl border border-line bg-bg lg:grid-cols-[420px_1fr]">
      <div class="flex min-w-0 flex-col border-line lg:border-r">
        <div class="flex flex-col gap-1.5 p-5">
          <h1 class="m-0 font-display text-[22px] font-bold" i18n="@@muets.titre">Bracelets muets</h1>
          <span class="text-label text-text-2" i18n="@@muets.sousTitre">&gt; 3 intervalles sans nouvelles · ticket ouvert automatiquement</span>
          <div class="flex gap-2 pt-2" role="group" i18n-aria-label="@@muets.vue" aria-label="Tickets affichés">
            <button fg-button taille="sm" type="button" [variante]="resolus() ? 'secondary' : 'primary'" [attr.aria-pressed]="!resolus()" (click)="montrer(false)" i18n="@@muets.aTraiter">À traiter</button>
            <button fg-button taille="sm" type="button" [variante]="resolus() ? 'primary' : 'secondary'" [attr.aria-pressed]="resolus()" (click)="montrer(true)" i18n="@@muets.resolus">Résolus</button>
          </div>
        </div>
        @if (tickets(); as liste) {
          @for (ticket of liste; track ticket.id) {
            <button type="button" class="flex items-center gap-3 border-t border-line px-5 py-3.5 text-left focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-accent" [class.bg-surface-2]="choisi()?.id === ticket.id" [attr.aria-current]="choisi()?.id === ticket.id ? 'true' : null" (click)="choisir(ticket)">
              <span class="grid size-9 flex-none place-items-center rounded-[10px] text-[11px] font-bold tabular-nums" [class]="ticket.statut === 'RESOLU' ? 'bg-success-soft text-success' : 'bg-attention-soft text-attention-text'">{{ depuis(ticket.ouvertLe) }}</span>
              <span class="flex min-w-0 flex-1 flex-col gap-0.5">
                <strong class="text-body font-semibold">{{ ticket.numeroSerie }} · {{ ticket.reference }}</strong>
                <span class="truncate text-caption text-text-2">{{ etat(ticket) }}</span>
              </span>
              @switch (ticket.statut) {
                @case ('OUVERT') {
                  <fg-badge ton="attention" i18n="@@muets.statut.ouvert">Non attribué</fg-badge>
                }
                @case ('EN_COURS') {
                  <fg-badge ton="accent">{{ ticket.prisEnChargeParMoi ? aMoi : attribue }}</fg-badge>
                }
              }
            </button>
          } @empty {
            <p class="m-0 border-t border-line p-5 text-body text-text-2">
              @if (resolus()) {
                <ng-container i18n="@@muets.vide.resolus">Aucun ticket résolu.</ng-container>
              } @else {
                <ng-container i18n="@@muets.vide">Tous les bracelets en service donnent des nouvelles.</ng-container>
              }
            </p>
          }
        } @else if (!erreur()) {
          <div class="flex flex-col gap-2 p-5"><fg-skeleton forme="carte" /><fg-skeleton forme="carte" /></div>
        }
      </div>

      @if (choisi(); as t) {
        <section class="flex min-w-0 flex-col gap-4.5 px-5 py-6 lg:px-8" [attr.aria-label]="t.reference">
          <div class="flex flex-wrap items-center justify-between gap-3">
            <div class="flex flex-col gap-1">
              <h2 class="m-0 font-display text-[20px] font-semibold">
                {{ t.reference }} ·
                <a class="text-accent underline focus-visible:outline-2 focus-visible:outline-accent" [routerLink]="['/sav/parc', t.numeroSerie]">{{ t.numeroSerie }}</a>
              </h2>
              <span class="text-label text-text-2">{{ etat(t) }}</span>
            </div>
            <span class="inline-flex h-7 items-center rounded-full bg-accent-soft px-3 text-caption font-semibold text-accent">{{ etapes[etape()].libelle }}</span>
          </div>

          <ol class="m-0 flex list-none gap-0 p-0" i18n-aria-label="@@muets.etapes" aria-label="Avancement du ticket">
            @for (e of etapes; track e.libelle; let i = $index) {
              <li class="flex flex-1 flex-col gap-2 pr-2" [attr.aria-current]="i === etape() ? 'step' : null">
                <span class="h-1.5 rounded-[3px]" [class]="i <= etape() ? 'bg-accent' : 'bg-line-strong'"></span>
                <span class="text-caption font-semibold" [class.text-text-3]="i > etape()">{{ i + 1 }}. {{ e.libelle }}</span>
              </li>
            }
          </ol>

          <div class="flex flex-1 flex-col gap-3.5 rounded-2xl border border-line bg-surface p-6">
            <strong class="font-display text-h3 font-semibold">{{ etapes[etape()].titre }}</strong>
            <p class="m-0 max-w-[720px] text-body text-text-2">{{ description() }}</p>
            <dl class="m-0 grid max-w-[820px] grid-cols-1 gap-2.5 sm:grid-cols-3">
              @for (f of faits(); track f.k) {
                <div class="flex flex-col gap-1 rounded-xl bg-surface-2 p-3.5">
                  <dt class="text-caption font-medium text-text-3">{{ f.k }}</dt>
                  <dd class="m-0 text-body font-semibold">{{ f.v }}</dd>
                </div>
              }
            </dl>

            @switch (etape()) {
              @case (2) {
                <form class="grid max-w-[820px] grid-cols-1 gap-3 sm:grid-cols-2" (submit)="$event.preventDefault()">
                  <fg-input [formControl]="pointRelais" i18n-libelle="@@muets.pointRelais" libelle="Point relais" [longueurMax]="50" i18n-aide="@@muets.pointRelais.aide" aide="Nom court : il figure dans le SMS envoyé au parent." />
                  <fg-input [formControl]="rendezVous" type="datetime-local" i18n-libelle="@@muets.rendezVous" libelle="Rendez-vous" i18n-aide="@@muets.rendezVous.aide" aide="Dans les trente prochains jours." />
                </form>
              }
              @case (3) {
                @if (!t.remplacement) {
                  <form class="max-w-[400px]" (submit)="$event.preventDefault()">
                    <fg-input [formControl]="remplacement" i18n-libelle="@@muets.remplacement" libelle="Unité remise (numéro de série)" autocomplete="off" [longueurMax]="16" i18n-aide="@@muets.remplacement.aide" aide="Une unité « En stock » du point relais." />
                  </form>
                }
              }
            }

            <div class="mt-auto flex flex-wrap gap-2.5 pt-2">
              @switch (etape()) {
                @case (0) {
                  <button fg-button type="button" [chargement]="enCours()" (click)="prendre(t)" i18n="@@muets.prendre">Prendre en charge</button>
                }
                @case (1) {
                  <button fg-button type="button" [chargement]="enCours()" (click)="diagnostiquer(t)" i18n="@@muets.diagnostiquer">Lancer le diagnostic</button>
                }
                @case (2) {
                  <button fg-button type="button" [chargement]="enCours()" (click)="proposerRendezVous(t)" i18n="@@muets.proposer">Proposer le rendez-vous</button>
                  <button fg-button variante="secondary" type="button" (click)="sansRendezVous.set(true)" i18n="@@muets.dejaLa">Le parent est au point relais</button>
                }
                @case (3) {
                  @if (!t.remplacement) {
                    <button fg-button type="button" [chargement]="enCours()" (click)="reserver(t)" i18n="@@muets.reserver">Réserver l'unité</button>
                  }
                }
                @case (4) {
                  <button fg-button type="button" (click)="suivant()" i18n="@@muets.retour">Retour à la file</button>
                }
              }
              @if (t.statut !== 'RESOLU') {
                <button fg-button variante="secondary" type="button" (click)="ouvrir(t)" i18n="@@muets.resoudre">Clore autrement</button>
              }
            </div>
          </div>
        </section>
      } @else {
        <p class="m-0 hidden p-8 text-center text-body text-text-3 lg:block" i18n="@@muets.choisir">Choisissez un ticket pour le traiter.</p>
      }
    </div>

    <fg-sheet [titre]="titre()" [ouverte]="cloture() !== null" (fermee)="cloture.set(null)">
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
      <button fg-button variante="ghost" type="button" (click)="cloture.set(null)" i18n="@@commun.annuler">Annuler</button>
    </fg-sheet>
  `,
  host: { class: 'contents' },
})
export class Muets {
  private readonly client = inject(ClientSav);
  private readonly router = inject(Router);

  protected readonly options = RESOLUTIONS;
  protected readonly depuis = depuis;
  protected readonly aMoi = $localize`:@@muets.statut.aMoi:À vous`;
  protected readonly attribue = $localize`:@@muets.statut.attribue:Attribué`;
  protected readonly etapes = [
    { libelle: $localize`:@@muets.etape.detection:Détection`, titre: $localize`:@@muets.titre.detection:Bracelet muet détecté` },
    { libelle: $localize`:@@muets.etape.diagnostic:Diagnostic`, titre: $localize`:@@muets.titre.diagnostic:Diagnostic à distance` },
    { libelle: $localize`:@@muets.etape.contact:Rendez-vous`, titre: $localize`:@@muets.titre.contact:Rendez-vous en point relais` },
    { libelle: $localize`:@@muets.etape.relais:Point relais`, titre: $localize`:@@muets.titre.relais:Remise en point relais` },
    { libelle: $localize`:@@muets.etape.cloture:Clôture`, titre: $localize`:@@muets.titre.cloture:Ticket clos` },
  ];

  protected readonly tickets = signal<readonly TicketMaintenance[] | null>(null);
  protected readonly resolus = signal(false);
  private readonly choisiId = signal<string | null>(null);
  protected readonly sansRendezVous = signal(false);
  protected readonly cloture = signal<TicketMaintenance | null>(null);
  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly titre = signal('');

  protected readonly choisi = computed(() => this.tickets()?.find((t) => t.id === this.choisiId()) ?? null);
  protected readonly etape = computed(() => {
    const t = this.choisi();
    return t ? etapeDe(t, this.sansRendezVous()) : 0;
  });
  protected readonly description = computed(() => {
    const t = this.choisi();
    return t ? this.decrire(t, this.etape()) : '';
  });
  protected readonly faits = computed(() => {
    const t = this.choisi();
    return t ? this.faitsDe(t, this.etape()) : [];
  });

  protected readonly resolution = new FormControl<ResolutionTicket>('RECHARGE', { nonNullable: true });
  protected readonly note = new FormControl('', { nonNullable: true });
  protected readonly pointRelais = new FormControl('', { nonNullable: true });
  protected readonly rendezVous = new FormControl('', { nonNullable: true });
  protected readonly remplacement = new FormControl('', { nonNullable: true });

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
    this.choisiId.set(null);
    this.charger();
  }

  protected choisir(ticket: TicketMaintenance): void {
    this.choisiId.set(ticket.id);
    this.sansRendezVous.set(false);
    this.pointRelais.setValue('');
    this.rendezVous.setValue('');
    this.remplacement.setValue('');
  }

  protected suivant(): void {
    this.choisiId.set(null);
    this.montrer(false);
  }

  protected prendre(ticket: TicketMaintenance): void {
    this.agir(this.client.prendreEnCharge(ticket.id));
  }

  protected diagnostiquer(ticket: TicketMaintenance): void {
    this.agir(this.client.diagnostiquer(ticket.id));
  }

  protected proposerRendezVous(ticket: TicketMaintenance): void {
    const le = this.rendezVous.value ? new Date(this.rendezVous.value) : null;
    if (!this.pointRelais.value.trim() || !le || Number.isNaN(le.getTime())) {
      this.erreur.set($localize`:@@muets.rendezVous.incomplet:Indiquez le point relais, la date et l'heure du rendez-vous.`);
      return;
    }
    this.agir(this.client.fixerRendezVous(ticket.id, this.pointRelais.value.trim(), le.toISOString()));
  }

  protected reserver(ticket: TicketMaintenance): void {
    if (!this.remplacement.value.trim()) {
      this.erreur.set($localize`:@@muets.remplacement.vide:Indiquez le numéro de l'unité remise.`);
      return;
    }
    this.agir(this.client.reserverRemplacement(ticket.id, this.remplacement.value.trim()));
  }

  protected ouvrir(ticket: TicketMaintenance): void {
    this.resolution.setValue('RECHARGE');
    this.note.setValue('');
    this.titre.set($localize`:@@muets.clore.titre:Clore ${ticket.reference}:reference: · ${ticket.numeroSerie}:bracelet:`);
    this.cloture.set(ticket);
  }

  protected resoudre(): void {
    const ticket = this.cloture();
    if (!ticket || this.enCours()) {
      return;
    }
    this.cloture.set(null);
    this.agir(this.client.resoudre(ticket.id, this.resolution.value, this.note.value.trim()));
  }

  private decrire(t: TicketMaintenance, etape: EtapeTicket): string {
    switch (etape) {
      case 0:
        return $localize`:@@muets.desc.detection:Plus de trois intervalles sans message. Le parent a été prévenu par la plateforme et ce ticket ouvert automatiquement. Prenez-le en charge pour le traiter.`;
      case 1:
        return $localize`:@@muets.desc.diagnostic:Une localisation signée est envoyée au bracelet. S'il l'accuse, il reçoit encore ses commandes ; sans accusé, il est éteint, déchargé ou hors couverture. Beaucoup de bracelets muets à la fois font penser au réseau plutôt qu'au matériel.`;
      case 2:
        return $localize`:@@muets.desc.contact:Le service après-vente ne connaît pas la famille : la plateforme transmet le rendez-vous au parent par notification et SMS. Si le bracelet redonne des nouvelles d'ici là, le ticket se clôt et le parent en est averti.`;
      case 3:
        return t.remplacement
          ? $localize`:@@muets.desc.attente:Unité réservée. Le parent l'associe lui-même en saisissant dans l'application le code de sa carte d'activation : l'ancien bracelet revient alors au service après-vente, sa page QR est détachée de l'enfant et le ticket se clôt. Une autorisation de retrait éventuelle reste émise depuis le compte du parent.`
          : $localize`:@@muets.desc.relais:Échange standard : réservez l'unité en stock remise au parent. C'est lui qui l'associe, avec le code de sa carte d'activation ; vous ne voyez ni l'enfant ni son compte.`;
      case 4:
        return t.resolution ? LIBELLES_RESOLUTION[t.resolution] + (t.note ? ` — « ${t.note} »` : '') : '';
    }
  }

  private faitsDe(t: TicketMaintenance, etape: EtapeTicket): { k: string; v: string }[] {
    const dernier = {
      k: $localize`:@@muets.fait.dernier:Dernier message`,
      v: t.dernierContact ? jourEtHeure(t.dernierContact) + (t.reseau ? ` · ${t.reseau}` : '') : $localize`:@@muets.fait.aucun:Aucun`,
    };
    const batterie = { k: $localize`:@@muets.fait.batterie:Batterie`, v: t.batterie === null ? '—' : `${t.batterie} %` };
    const voisins = { k: $localize`:@@muets.fait.autres:Autres bracelets muets`, v: String(t.autresMuets) };
    switch (etape) {
      case 0:
      case 1:
        return [dernier, batterie, voisins];
      case 2:
        return [{ k: $localize`:@@muets.fait.sondage:Sondage`, v: this.sondage(t) }, voisins, batterie];
      case 3:
        return [
          { k: $localize`:@@muets.fait.ancien:Ancien`, v: t.numeroSerie + $localize`:@@muets.fait.versSav: → SAV` },
          { k: $localize`:@@muets.fait.nouveau:Nouveau`, v: t.remplacement ?? '—' },
          {
            k: t.rendezVousLe ? $localize`:@@muets.fait.rdv:Rendez-vous` : $localize`:@@muets.fait.code:Code d'appairage`,
            v: t.rendezVousLe ? `${t.pointRelais} · ${DATE_LONGUE.format(new Date(t.rendezVousLe))}` : $localize`:@@muets.fait.parParent:Saisi par le parent`,
          },
        ];
      case 4:
        return [
          { k: $localize`:@@muets.fait.ouvert:Ouvert`, v: jourEtHeure(t.ouvertLe) },
          { k: $localize`:@@muets.fait.clos:Clos`, v: jourEtHeure(t.resoluLe) },
          { k: $localize`:@@muets.fait.nouveau:Nouveau`, v: t.remplacement ?? '—' },
        ];
    }
  }

  private sondage(t: TicketMaintenance): string {
    const heure = jourEtHeure(t.diagnosticLe);
    switch (t.diagnostic) {
      case 'ACCUSEE':
        return $localize`:@@muets.sondage.accuse:Accusé (${heure}:heure:)`;
      case 'REFUSEE':
        return $localize`:@@muets.sondage.refuse:Refusé par le bracelet (${heure}:heure:)`;
      case 'EXPIREE':
        return $localize`:@@muets.sondage.expire:Sans réponse (${heure}:heure:)`;
      default:
        return $localize`:@@muets.sondage.attente:En attente (${heure}:heure:)`;
    }
  }

  private agir(action: ReturnType<ClientSav['prendreEnCharge']>): void {
    if (this.enCours()) {
      return;
    }
    this.enCours.set(true);
    this.erreur.set(null);
    action.subscribe({
      next: () => {
        this.enCours.set(false);
        this.charger();
      },
      error: (cause: unknown) => {
        this.enCours.set(false);
        this.signaler(cause);
        this.charger();
      },
    });
  }

  private charger(): void {
    this.client.tickets(this.resolus()).subscribe({
      next: (tickets) => {
        this.tickets.set(tickets);
        // Sur grand écran, le premier ticket est ouvert d'emblée, comme sur l'écran 66.
        if (!this.choisi() && tickets.length > 0) {
          this.choisiId.set(tickets[0].id);
        }
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
