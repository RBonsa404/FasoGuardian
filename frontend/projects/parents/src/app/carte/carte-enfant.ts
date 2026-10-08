import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { ClientBracelet, ClientFamille, ClientZones, FicheEnfant, SafeZone, Situation } from 'api';
import { FgBadge, FgBanniere, FgIcon, FgSquelette } from 'ui';

import { erreurLisible } from '../commun/erreurs';
import { heure, ilYA, minutesDepuis } from '../commun/temps';
import { Carte, PositionSurCarte } from './fond';

/** Au-delà, la position n'est plus présentée comme actuelle (trois intervalles normaux de 5 minutes). */
const MINUTES_AVANT_ATTENUATION = 15;
const RAFRAICHISSEMENT_MS = 60_000;

/**
 * Carte plein écran (écran 18, US-PAR-006 et US-PAR-009) : dernière position de l'enfant, toujours avec son
 * heure, sa précision et son cercle d'incertitude, et les Safe Zones. Rafraîchie chaque minute.
 */
@Component({
  selector: 'app-carte-enfant',
  imports: [RouterLink, Carte, FgBadge, FgBanniere, FgIcon, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-carte class="absolute inset-0" i18n-libelle="@@carte.libelle" libelle="Carte de la position de l'enfant" [position]="repere()" [zones]="zones()" />

    <a class="absolute top-6 left-4 z-1000 grid size-11 place-items-center rounded-full bg-surface shadow-e2 focus-visible:outline-2 focus-visible:outline-accent" [routerLink]="['/enfants', id()]" i18n-aria-label="@@etape.retour" aria-label="Retour">
      <fg-icon nom="retour" [taille]="22" />
    </a>

    <section class="absolute inset-x-4 bottom-6 z-1000 flex flex-col gap-2 rounded-xl border border-line bg-surface p-4 shadow-e2" aria-live="polite">
      @if (situation(); as s) {
        @if (s.position; as p) {
          <h1 class="m-0 text-h3 font-semibold">{{ titre() }}</h1>
          <div class="flex flex-wrap items-center gap-2">
            @if (p.source === 'GNSS') {
              <fg-badge ton="succes" icone="position" i18n="@@carte.precise">Précise ± {{ p.precisionM }} m</fg-badge>
            } @else {
              <fg-badge ton="attention" icone="info" i18n="@@carte.approximative">Approx. ± {{ p.precisionM }} m</fg-badge>
            }
            <span class="text-label text-text-2 tabular-nums">{{ quand() }}</span>
          </div>
          @if (p.source !== 'GNSS') {
            <p class="m-0 text-label text-text-2" i18n="@@carte.antenne">Position par antenne relais : le GPS est indisponible, par exemple à l'intérieur d'un bâtiment.</p>
          }
          @if (ancienne()) {
            <p class="m-0 text-label text-text-2" i18n="@@carte.ancienne">Le bracelet n'a pas transmis de position plus récente.</p>
          }
        } @else {
          <h1 class="m-0 text-h3 font-semibold" i18n="@@carte.aucune">Pas encore de position</h1>
          <p class="m-0 text-label text-text-2" i18n="@@carte.aucune.texte">Le bracelet {{ s.numeroSerie }} n'a pas encore transmis de position. Vérifiez qu'il est allumé et chargé.</p>
        }
      } @else if (sansBracelet()) {
        <h1 class="m-0 text-h3 font-semibold" i18n="@@bracelet.absent">Aucun bracelet n'est associé à cet enfant.</h1>
        <a class="self-start text-label font-semibold text-accent" routerLink="/bracelet/associer" [queryParams]="{ enfant: id() }" i18n="@@bracelet.associer">Associer un bracelet</a>
      } @else if (erreur(); as message) {
        <fg-banner ton="erreur">{{ message }}</fg-banner>
      } @else {
        <fg-skeleton forme="ligne" />
        <fg-skeleton forme="ligne" />
      }
    </section>
  `,
  host: { class: 'relative block h-dvh w-full overflow-hidden' },
})
export class CarteEnfant {
  readonly id = input.required<string>();

  private readonly bracelets = inject(ClientBracelet);
  private readonly famille = inject(ClientFamille);
  private readonly clientZones = inject(ClientZones);

  protected readonly enfant = signal<FicheEnfant | null>(null);
  protected readonly situation = signal<Situation | null>(null);
  protected readonly zones = signal<readonly SafeZone[]>([]);
  protected readonly sansBracelet = signal(false);
  protected readonly erreur = signal<string | null>(null);
  /** Horloge de l'écran : fait vieillir « il y a 2 min » entre deux rafraîchissements. */
  private readonly maintenant = signal(new Date());

  protected readonly ancienne = computed(() => {
    const position = this.situation()?.position;
    return !!position && minutesDepuis(position.mesureeLe, this.maintenant()) > MINUTES_AVANT_ATTENUATION;
  });
  protected readonly repere = computed((): PositionSurCarte | null => {
    const position = this.situation()?.position;
    return position
      ? {
          latitude: position.latitude,
          longitude: position.longitude,
          precisionM: position.precisionM,
          initiale: this.enfant()?.prenom.charAt(0) ?? '',
          attenuee: this.ancienne() || position.source !== 'GNSS',
        }
      : null;
  });
  protected readonly titre = computed(() => {
    const sortie = this.zones().find((zone) => zone.sortieEnCours);
    if (sortie) {
      return $localize`:@@carte.titre.sortie:Hors de « ${sortie.nom}:zone: »`;
    }
    const dedans = this.zones().find((zone) => zone.enfantDedans);
    if (dedans) {
      return $localize`:@@carte.titre.dedans:Dans « ${dedans.nom}:zone: »`;
    }
    const prenom = this.enfant()?.prenom;
    return prenom ? $localize`:@@carte.titre.position:Position de ${prenom}:prenom:` : $localize`:@@carte.titre.defaut:Dernière position`;
  });
  protected readonly quand = computed(() => {
    const situation = this.situation();
    if (!situation?.position) {
      return '';
    }
    const morceaux = [heure(situation.position.mesureeLe), ilYA(situation.position.mesureeLe, this.maintenant())];
    if (situation.etat?.batterie != null) {
      morceaux.push($localize`:@@carte.batterie:batterie ${situation.etat.batterie}:niveau: %`);
    }
    return morceaux.join(' · ');
  });

  constructor() {
    effect(() => this.charger(this.id()));
    const minuterie = setInterval(() => {
      this.maintenant.set(new Date());
      this.rafraichir(this.id());
    }, RAFRAICHISSEMENT_MS);
    inject(DestroyRef).onDestroy(() => clearInterval(minuterie));
  }

  private charger(id: string): void {
    this.situation.set(null);
    this.sansBracelet.set(false);
    this.erreur.set(null);
    this.famille.enfant(id).subscribe({ next: (enfant) => this.enfant.set(enfant), error: () => undefined });
    this.rafraichir(id);
  }

  private rafraichir(id: string): void {
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
