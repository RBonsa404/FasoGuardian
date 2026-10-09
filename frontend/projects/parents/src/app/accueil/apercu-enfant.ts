import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { ClientBracelet, ClientZones, FicheEnfant, SafeZone, Situation } from 'api';
import { FgBadge, FgBanniere, FgBouton, FgIcon, FgSquelette, NomIcone } from 'ui';

import { Carte } from '../carte/fond';
import { forceDuSignal, positionAncienne, repereDe, titrePosition } from '../carte/lecture';
import { erreurLisible } from '../commun/erreurs';
import { heure, ilYA } from '../commun/temps';

const RAFRAICHISSEMENT_MS = 60_000;

const RACCOURCIS: readonly { chemin: string; libelle: string; icone: NomIcone }[] = [
  { chemin: 'carte', libelle: $localize`:@@apercu.carte:Carte`, icone: 'position' },
  { chemin: 'zones', libelle: $localize`:@@apercu.zones:Zones`, icone: 'safe-zone' },
  { chemin: 'trajets', libelle: $localize`:@@apercu.trajets:Trajets`, icone: 'historique' },
  { chemin: 'bracelet', libelle: $localize`:@@apercu.bracelet:Bracelet`, icone: 'bracelet' },
  { chemin: 'bracelet/retrait', libelle: $localize`:@@apercu.retrait:Retrait`, icone: 'retrait' },
  { chemin: 'journal', libelle: $localize`:@@apercu.journal:Journal`, icone: 'audit' },
  { chemin: 'partage', libelle: $localize`:@@apercu.partage:Partager`, icone: 'partager' },
];

/**
 * Aperçu d'un enfant sur le tableau de bord (écran 16, US-PAR-006) : carte, lieu, précision, heure de la
 * position, batterie et réseau, rafraîchis chaque minute. Sans bracelet, l'aperçu invite à l'appairage.
 */
@Component({
  selector: 'app-apercu-enfant',
  imports: [RouterLink, Carte, FgBadge, FgBanniere, FgBouton, FgIcon, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (situation(); as s) {
      <div class="relative h-56 overflow-hidden rounded-xl border border-line">
        <app-carte class="absolute inset-0" [libelle]="libelleCarte()" [position]="repere()" [zones]="zones()" />
        <a class="absolute top-3 right-3 z-1000 rounded-full bg-surface px-3 py-1.5 text-caption font-semibold shadow-e1 focus-visible:outline-2 focus-visible:outline-accent" [routerLink]="['/enfants', enfant().id, 'carte']" i18n="@@apercu.agrandir">Agrandir</a>
      </div>

      <section class="flex flex-col gap-2 rounded-lg border border-line bg-surface p-4" aria-live="polite">
        <span class="text-label font-semibold text-text-2">{{ enfant().prenom }}</span>
        @if (s.position; as p) {
          <h2 class="m-0 text-h3 font-semibold">{{ titre() }}</h2>
          @if (p.source === 'GNSS') {
            <fg-badge class="self-start" ton="succes" icone="position" i18n="@@carte.precise">Précise ± {{ p.precisionM }} m</fg-badge>
          } @else {
            <fg-badge class="self-start" ton="attention" icone="info" i18n="@@carte.approximative">Approx. ± {{ p.precisionM }} m</fg-badge>
          }
          @if (ancienne()) {
            <p class="m-0 text-label text-text-2" i18n="@@carte.ancienne">Le bracelet n'a pas transmis de position plus récente.</p>
          }
        } @else {
          <h2 class="m-0 text-h3 font-semibold" i18n="@@carte.aucune">Pas encore de position</h2>
          <p class="m-0 text-label text-text-2" i18n="@@carte.aucune.texte">Le bracelet {{ s.numeroSerie }} n'a pas encore transmis de position. Vérifiez qu'il est allumé et chargé.</p>
        }
      </section>

      <dl class="m-0 grid grid-cols-3 gap-2">
        @for (mesure of mesures(); track mesure.cle) {
          <div class="flex min-w-0 flex-col gap-0.5 rounded-banner border border-line bg-surface p-3">
            <dt class="text-caption font-medium text-text-3">{{ mesure.cle }}</dt>
            <dd class="m-0 truncate text-saisie font-semibold tabular-nums">{{ mesure.valeur }}</dd>
            <dd class="m-0 truncate text-caption text-text-3">{{ mesure.detail }}</dd>
          </div>
        }
      </dl>

      <nav class="grid grid-cols-3 gap-2" [attr.aria-label]="libelleRaccourcis()">
        @for (raccourci of raccourcis; track raccourci.chemin) {
          <a class="flex min-h-18 flex-col items-center justify-center gap-1.5 rounded-banner border border-line bg-surface text-caption font-semibold focus-visible:outline-2 focus-visible:outline-accent" [routerLink]="'/enfants/' + enfant().id + '/' + raccourci.chemin">
            <fg-icon class="text-accent" [nom]="raccourci.icone" [taille]="22" />{{ raccourci.libelle }}
          </a>
        }
      </nav>
    } @else if (sansBracelet()) {
      <section class="flex flex-col gap-3 rounded-lg border border-line bg-surface p-5">
        <h2 class="m-0 text-h3 font-semibold" i18n="@@apercu.associer.titre">Associez le bracelet de {{ enfant().prenom }}</h2>
        <p class="m-0 text-body text-text-2" i18n="@@apercu.associer.texte">Le code d'appairage est sur la carte glissée dans la boîte.</p>
        <button fg-button type="button" (click)="associer()" i18n="@@apercu.associer">Associer</button>
      </section>
    } @else if (erreur(); as message) {
      <fg-banner ton="erreur">{{ message }}</fg-banner>
      <button fg-button variante="secondary" type="button" class="self-start" (click)="rafraichir()" i18n="@@commun.reessayer">Réessayer</button>
    } @else {
      <fg-skeleton forme="carte" />
      <fg-skeleton forme="carte" />
    }
  `,
  host: { class: 'flex flex-col gap-3' },
})
export class ApercuEnfant {
  readonly enfant = input.required<FicheEnfant>();

  private readonly bracelets = inject(ClientBracelet);
  private readonly clientZones = inject(ClientZones);
  private readonly router = inject(Router);

  protected readonly raccourcis = RACCOURCIS;
  protected readonly situation = signal<Situation | null>(null);
  protected readonly zones = signal<readonly SafeZone[]>([]);
  protected readonly sansBracelet = signal(false);
  protected readonly erreur = signal<string | null>(null);
  private readonly maintenant = signal(new Date());

  protected readonly libelleCarte = computed(() => $localize`:@@apercu.carte.libelle:Carte de la position de ${this.enfant().prenom}:prenom:`);
  protected readonly libelleRaccourcis = computed(() => $localize`:@@apercu.raccourcis:Raccourcis pour ${this.enfant().prenom}:prenom:`);
  protected readonly ancienne = computed(() => positionAncienne(this.situation(), this.maintenant()));
  protected readonly repere = computed(() => repereDe(this.situation(), this.enfant().prenom, this.maintenant()));
  protected readonly titre = computed(() => titrePosition(this.zones(), this.enfant().prenom));
  protected readonly mesures = computed(() => {
    const situation = this.situation();
    const etat = situation?.etat;
    const position = situation?.position;
    return [
      {
        cle: $localize`:@@apercu.position:Position`,
        valeur: position ? heure(position.mesureeLe) : '—',
        detail: position ? ilYA(position.mesureeLe, this.maintenant()) : '',
      },
      {
        cle: $localize`:@@bracelet.batterie:Batterie`,
        valeur: etat?.batterie != null ? `${etat.batterie} %` : '—',
        detail: etat?.batterie != null && etat.batterie < 20 ? $localize`:@@bracelet.batterie.faible:batterie faible` : '',
      },
      {
        cle: $localize`:@@apercu.reseau:Réseau`,
        valeur: etat?.reseau ?? '—',
        detail: etat?.signalDbm != null ? [etat.operateur, forceDuSignal(etat.signalDbm)].filter(Boolean).join(' · ') : '',
      },
    ];
  });

  constructor() {
    effect(() => {
      this.enfant();
      this.situation.set(null);
      this.sansBracelet.set(false);
      this.rafraichir();
    });
    const minuterie = setInterval(() => {
      this.maintenant.set(new Date());
      if (!this.sansBracelet()) {
        this.rafraichir();
      }
    }, RAFRAICHISSEMENT_MS);
    inject(DestroyRef).onDestroy(() => clearInterval(minuterie));
  }

  protected associer(): void {
    void this.router.navigate(['/bracelet/associer'], { queryParams: { enfant: this.enfant().id } });
  }

  protected rafraichir(): void {
    const id = this.enfant().id;
    forkJoin({ situation: this.bracelets.situation(id), zones: this.clientZones.zones(id) }).subscribe({
      next: ({ situation, zones }) => {
        this.erreur.set(null);
        this.situation.set(situation);
        this.zones.set(zones.zones);
      },
      error: (cause: unknown) => {
        const lisible = erreurLisible(cause);
        if (lisible.code === 'RESSOURCE_INTROUVABLE') {
          this.sansBracelet.set(true);
        } else if (!this.situation()) {
          // Une position déjà affichée reste à l'écran avec son heure si le réseau manque au rafraîchissement.
          this.erreur.set(lisible.message);
        }
      },
    });
  }
}
