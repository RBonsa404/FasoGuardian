import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ClientFamille, ClientPartages, ContactUrgence, FicheEnfant, PartagePosition } from 'api';
import { FgBanniere, FgBouton, FgIcon, FgSquelette } from 'ui';

import { erreurLisible } from '../commun/erreurs';
import { heure } from '../commun/temps';

/** Durées proposées, en minutes. */
export const DUREES: readonly { minutes: number; libelle: string }[] = [
  { minutes: 30, libelle: $localize`:@@partage.duree.30:30 min` },
  { minutes: 60, libelle: $localize`:@@partage.duree.60:1 h` },
  { minutes: 120, libelle: $localize`:@@partage.duree.120:2 h` },
  { minutes: 240, libelle: $localize`:@@partage.duree.240:4 h` },
];

/** « 1:24:10 » : heures, minutes, secondes restantes. */
export function decompte(secondes: number): string {
  const s = Math.max(0, secondes);
  return `${Math.floor(s / 3600)}:${String(Math.floor((s % 3600) / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
}

/**
 * Partage temporaire (écran 35, US-SEC-001) : le parent envoie à un contact d'urgence un lien qui montre la
 * position de l'enfant pendant la durée choisie, et peut le révoquer à tout moment.
 */
@Component({
  selector: 'app-partage',
  imports: [RouterLink, FgBanniere, FgBouton, FgIcon, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="-ml-2.5 grid size-11 place-items-center rounded-md focus-visible:outline-2 focus-visible:outline-accent" [routerLink]="['/enfants', id()]" i18n-aria-label="@@etape.retour" aria-label="Retour">
      <fg-icon nom="retour" [taille]="22" />
    </a>
    @if (erreur(); as message) {
      <fg-banner ton="erreur">{{ message }}</fg-banner>
    }
    @if (charge()) {
      @if (partage(); as p) {
        <div class="flex flex-col gap-1.5">
          <h1 class="m-0 text-h2 font-bold tracking-tight" i18n="@@partage.enCours">Partage en cours</h1>
          <p class="m-0 text-body text-text-2" i18n="@@partage.enCours.texte">{{ p.lien }} voit la position de {{ prenom() }} jusqu'à {{ heure(p.fin) }}.</p>
        </div>
        <div class="flex flex-col items-center gap-1 rounded-lg bg-accent-soft p-5" role="timer">
          <strong class="font-display text-display font-bold tabular-nums text-accent">{{ restant() }}</strong>
          <span class="text-label text-text-2" i18n="@@partage.restant">restant</span>
        </div>
        <dl class="m-0 flex flex-col rounded-lg border border-line bg-surface">
          <div class="flex items-baseline justify-between gap-4 border-b border-line px-4 py-3"><dt class="text-label text-text-2" i18n="@@partage.destinataire">Destinataire</dt><dd class="m-0 text-label font-medium tabular-nums">{{ p.destinataire }}</dd></div>
          <div class="flex items-baseline justify-between gap-4 border-b border-line px-4 py-3"><dt class="text-label text-text-2" i18n="@@partage.envoi">Envoyé par</dt><dd class="m-0 text-label font-medium" i18n="@@partage.envoi.sms">SMS · lien unique</dd></div>
          <div class="flex items-baseline justify-between gap-4 px-4 py-3"><dt class="text-label text-text-2" i18n="@@partage.ouvert">Ouvert</dt><dd class="m-0 text-label font-medium">{{ ouvertures(p) }}</dd></div>
        </dl>
        <div class="mt-auto flex flex-col gap-2">
          <button fg-button variante="danger" taille="lg" type="button" [chargement]="enCours()" (click)="revoquer()" i18n="@@partage.revoquer">Révoquer maintenant</button>
        </div>
      } @else {
        <div class="flex flex-col gap-1.5">
          <h1 class="m-0 text-h2 font-bold tracking-tight" i18n="@@partage.titre">Partager la position</h1>
          <p class="m-0 text-body text-text-2" i18n="@@partage.texte">Un contact d'urgence reçoit par SMS un lien personnel. Il voit où est {{ prenom() }} pendant la durée choisie, sans compte ni accès à l'historique.</p>
        </div>
        @if (contacts().length === 0) {
          <fg-banner ton="info" i18n="@@partage.sansContact">Ajoutez d'abord un contact d'urgence : le partage ne se fait qu'avec une personne que vous avez désignée.</fg-banner>
          <a class="grid min-h-12 place-items-center text-body font-semibold text-accent focus-visible:outline-2 focus-visible:outline-accent" [routerLink]="['/enfants', id(), 'contacts']" i18n="@@partage.ajouterContact">Ajouter un contact d'urgence</a>
        } @else {
          <fieldset class="m-0 flex flex-col gap-2 border-0 p-0">
            <legend class="pb-2 text-label font-semibold" i18n="@@partage.avec">Partager avec</legend>
            @for (contact of contacts(); track contact.id) {
              <label class="flex min-h-14 cursor-pointer items-center gap-3 rounded-lg bg-surface px-3.5 py-2.5 focus-within:outline-2 focus-within:outline-accent" [class]="choisi() === contact.id ? 'border-2 border-accent' : 'border border-line'">
                <input class="sr-only" type="radio" name="contact" [value]="contact.id" [checked]="choisi() === contact.id" (change)="choisi.set(contact.id)" />
                <span class="flex flex-col">
                  <strong class="text-body font-semibold">{{ contact.lien }} · {{ contact.nom }}</strong>
                  <span class="text-caption text-text-2 tabular-nums">{{ contact.telephone }}</span>
                </span>
              </label>
            }
          </fieldset>
          <fieldset class="m-0 flex flex-col gap-2 border-0 p-0">
            <legend class="pb-2 text-label font-semibold" i18n="@@partage.pendant">Pendant</legend>
            <div class="grid grid-cols-4 gap-2">
              @for (duree of durees; track duree.minutes) {
                <label class="grid min-h-12 cursor-pointer place-items-center rounded-md text-label font-semibold focus-within:outline-2 focus-within:outline-accent" [class]="minutes() === duree.minutes ? 'border-2 border-accent bg-accent-soft' : 'border border-line bg-surface'">
                  <input class="sr-only" type="radio" name="duree" [value]="duree.minutes" [checked]="minutes() === duree.minutes" (change)="minutes.set(duree.minutes)" />{{ duree.libelle }}
                </label>
              }
            </div>
          </fieldset>
          <div class="mt-auto flex flex-col gap-2">
            <button fg-button taille="lg" type="button" [disabled]="!choisi()" [chargement]="enCours()" (click)="partager()" i18n="@@partage.envoyer">Envoyer le lien par SMS</button>
            <p class="m-0 text-center text-caption text-text-3" i18n="@@partage.note">Vous pourrez révoquer le partage à tout moment.</p>
          </div>
        }
      }
    } @else if (!erreur()) {
      <fg-skeleton forme="carte" />
      <fg-skeleton forme="carte" />
    }
  `,
  host: { class: 'mx-auto flex min-h-dvh w-full max-w-md flex-col gap-4 px-5 pt-6 pb-7' },
})
export class Partage {
  readonly id = input.required<string>();

  private readonly client = inject(ClientPartages);
  private readonly famille = inject(ClientFamille);

  protected readonly durees = DUREES;
  protected readonly heure = heure;

  protected readonly charge = signal(false);
  protected readonly partage = signal<PartagePosition | null>(null);
  protected readonly contacts = signal<readonly ContactUrgence[]>([]);
  protected readonly enfant = signal<FicheEnfant | null>(null);
  protected readonly choisi = signal<string | null>(null);
  protected readonly minutes = signal(60);
  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);
  private readonly maintenant = signal(Date.now());

  protected readonly prenom = computed(() => this.enfant()?.prenom ?? $localize`:@@partage.enfant:votre enfant`);
  protected readonly restant = computed(() => {
    const p = this.partage();
    return p ? decompte(Math.ceil((Date.parse(p.fin) - this.maintenant()) / 1000)) : '';
  });

  constructor() {
    effect(() => this.charger(this.id()));
    const minuterie = setInterval(() => {
      this.maintenant.set(Date.now());
      const p = this.partage();
      // À l'échéance, l'écran revient de lui-même au choix d'un nouveau partage.
      if (p && Date.parse(p.fin) <= Date.now()) {
        this.partage.set(null);
      }
    }, 1000);
    inject(DestroyRef).onDestroy(() => clearInterval(minuterie));
  }

  protected ouvertures(p: PartagePosition): string {
    if (p.ouvertures === 0 || !p.derniereOuverture) {
      return $localize`:@@partage.jamaisOuvert:pas encore`;
    }
    return $localize`:@@partage.ouvertures:${p.ouvertures}:nombre: fois · dernier ${heure(p.derniereOuverture)}:heure:`;
  }

  protected partager(): void {
    const contact = this.choisi();
    if (!contact || this.enCours()) {
      return;
    }
    this.enCours.set(true);
    this.erreur.set(null);
    this.client.partager(this.id(), contact, this.minutes()).subscribe({
      next: (partage) => {
        this.enCours.set(false);
        this.partage.set(partage);
      },
      error: (cause: unknown) => {
        this.enCours.set(false);
        this.erreur.set(erreurLisible(cause).message);
      },
    });
  }

  protected revoquer(): void {
    if (this.enCours()) {
      return;
    }
    this.enCours.set(true);
    this.erreur.set(null);
    this.client.revoquer(this.id()).subscribe({
      next: () => {
        this.enCours.set(false);
        this.partage.set(null);
      },
      error: (cause: unknown) => {
        this.enCours.set(false);
        this.erreur.set(erreurLisible(cause).message);
      },
    });
  }

  private charger(id: string): void {
    this.charge.set(false);
    this.famille.enfant(id).subscribe({ next: (fiche) => this.enfant.set(fiche), error: () => undefined });
    this.famille.contacts(id).subscribe({
      next: (contacts) => {
        this.contacts.set(contacts);
        this.choisi.set(contacts[0]?.id ?? null);
      },
      error: (cause: unknown) => this.erreur.set(erreurLisible(cause).message),
    });
    this.client.enCours(id).subscribe({
      next: (partage) => {
        this.partage.set(partage);
        this.charge.set(true);
      },
      error: (cause: unknown) => {
        // 404 : aucun partage en cours, c'est l'état ordinaire.
        if (cause instanceof HttpErrorResponse && cause.status === 404) {
          this.partage.set(null);
          this.charge.set(true);
        } else {
          this.erreur.set(erreurLisible(cause).message);
        }
      },
    });
  }
}
