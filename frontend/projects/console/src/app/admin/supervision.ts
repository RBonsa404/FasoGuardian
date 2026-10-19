import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { ClientConformite, TableauSupervision } from 'api';
import { FgBanniere, FgSquelette } from 'ui';

import { erreurLisible, estRefus } from '../commun/acces';

const RAFRAICHISSEMENT_MS = 30_000;
const NOMBRE = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 2 });
const ENTIER = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 });
const DECIMALE = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 1 });
const HORODATAGE = new Intl.DateTimeFormat('fr-FR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
const GIGA = 1024 ** 3;

/** Ton de la mention en haut à droite d'une tuile. */
type Ton = 'succes' | 'danger' | 'attention' | 'neutre';

interface Barre {
  /** Hauteur, en part de la hauteur du graphique ; `null` pour une période sans mesure. */
  readonly hauteur: number | null;
  /** Vrai si la mesure est hors objectif. */
  readonly horsObjectif: boolean;
}

interface Tuile {
  readonly libelle: string;
  readonly valeur: string;
  readonly mention: string;
  readonly ton: Ton;
  readonly ok: boolean;
  readonly note: string;
  readonly periode: string;
  readonly barres: readonly Barre[];
}

/**
 * Supervision (écran 72, US-ADM-004) : les six indicateurs de FG-DOC-06 (tableau 20), chacun face à son objectif
 * et avec son évolution : trente jours pour la disponibilité, vingt-quatre heures pour les autres (ADR 0039).
 * L'écran se rafraîchit de lui-même.
 */
@Component({
  selector: 'app-supervision',
  imports: [FgBanniere, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex flex-wrap items-center justify-between gap-3">
      <h1 class="m-0 font-display text-[26px] font-bold tracking-tight" i18n="@@supervision.titre">Supervision</h1>
      @if (tableau()) {
        <span class="flex items-center gap-2 text-caption font-semibold text-text-2" role="status">
          <span class="size-2 rounded-full bg-success" aria-hidden="true"></span>
          <ng-container i18n="@@supervision.direct">En direct · actualisé il y a {{ age() }} s</ng-container>
        </span>
      }
    </div>
    @if (erreur(); as message) {
      <fg-banner ton="erreur">{{ message }}</fg-banner>
    }
    @if (tableau(); as t) {
      @if (t.delaiHorsSeuil) {
        <fg-banner ton="erreur" i18n="@@supervision.alerte">Alerte d'exploitation : le délai entre un événement du bracelet et la notification des parents dépasse {{ t.seuilDeDelaiS }} s au 95e centile.</fg-banner>
      }
      <div class="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
        @for (tuile of tuiles(); track tuile.libelle) {
          <section class="flex min-h-[340px] flex-col gap-2 rounded-xl border bg-surface p-[18px]" [class.border-line]="tuile.ok" [class.border-danger]="!tuile.ok">
            <div class="flex items-baseline justify-between gap-3">
              <h2 class="m-0 text-label font-semibold">{{ tuile.libelle }}</h2>
              <span class="shrink-0 text-[11px] font-semibold" [class]="tons[tuile.ton]">{{ tuile.mention }}</span>
            </div>
            <strong class="font-display text-[30px] leading-tight font-bold tabular-nums" [class.text-danger]="!tuile.ok">{{ tuile.valeur }}</strong>
            <div class="flex min-h-40 flex-1 items-end gap-[3px] pt-4" role="img" [attr.aria-label]="tuile.periode">
              @for (barre of tuile.barres; track $index) {
                @if (barre.hauteur === null) {
                  <span class="h-0.5 flex-1 rounded-sm bg-surface-2"></span>
                } @else {
                  <span class="flex-1 rounded-t-sm" [class]="barre.horsObjectif ? 'bg-attention' : 'bg-accent'" [style.height.%]="barre.hauteur * 100"></span>
                }
              }
            </div>
            <span class="text-caption font-medium" [class]="tuile.ok ? 'text-text-3' : 'text-danger'">{{ tuile.note }}</span>
          </section>
        }
      </div>
      <p class="m-0 text-caption text-text-3" i18n="@@supervision.note">Une barre par jour pour la disponibilité, par heure pour les autres indicateurs ; en violet, les mesures hors objectif. La disponibilité est la part des minutes où le serveur a répondu et joint sa base. La télésurveillance (Prometheus, Grafana) garde l'historique long.</p>
    } @else if (!erreur()) {
      <fg-skeleton forme="carte" />
      <fg-skeleton forme="carte" />
    }
  `,
  host: { class: 'contents' },
})
export class Supervision {
  private readonly client = inject(ClientConformite);
  private readonly router = inject(Router);

  protected readonly tons: Record<Ton, string> = { succes: 'text-success', danger: 'text-danger', attention: 'text-attention', neutre: 'text-text-3' };
  protected readonly tableau = signal<TableauSupervision | null>(null);
  protected readonly erreur = signal<string | null>(null);
  private readonly recuLe = signal(Date.now());
  private readonly maintenant = signal(Date.now());

  protected readonly age = computed(() => Math.max(0, Math.round((this.maintenant() - this.recuLe()) / 1000)));
  protected readonly tuiles = computed<readonly Tuile[]>(() => {
    const t = this.tableau();
    return t ? tuiles(t) : [];
  });

  constructor() {
    this.charger();
    const rafraichissement = setInterval(() => this.charger(), RAFRAICHISSEMENT_MS);
    const horloge = setInterval(() => this.maintenant.set(Date.now()), 1000);
    inject(DestroyRef).onDestroy(() => {
      clearInterval(rafraichissement);
      clearInterval(horloge);
    });
  }

  private charger(): void {
    this.client.supervision().subscribe({
      next: (tableau) => {
        this.tableau.set(tableau);
        this.erreur.set(null);
        this.recuLe.set(Date.now());
        this.maintenant.set(Date.now());
      },
      error: (cause: unknown) => (estRefus(cause) ? void this.router.navigate(['/refuse']) : this.erreur.set(erreurLisible(cause).message)),
    });
  }
}

/**
 * Barres d'une série : la plus haute mesure, ou le seuil s'il est plus haut, occupe toute la hauteur ; au-dessus du
 * plancher seulement, pour qu'une disponibilité de 99,2 % ne ressemble pas à 100 %.
 */
export function barres(valeurs: readonly (number | null)[], horsObjectif: (valeur: number) => boolean, plafond = 0, plancher = 0): readonly Barre[] {
  const haut = Math.max(plafond, ...valeurs.filter((v): v is number => v !== null));
  return valeurs.map((valeur) => ({
    hauteur: valeur === null ? null : haut <= plancher ? 0.04 : Math.max(0.04, (valeur - plancher) / (haut - plancher)),
    horsObjectif: valeur !== null && horsObjectif(valeur),
  }));
}

function hausseAnormale(variation: number | null, enService: number): boolean {
  return variation !== null && variation > 0 && enService > 0 && (variation * 100) / enService > 10;
}

function taille(octets: number): string {
  return octets >= GIGA ? `${DECIMALE.format(octets / GIGA)} Go` : `${ENTIER.format(octets / 1024 ** 2)} Mo`;
}

/** Les indicateurs suivis, dans l'ordre de l'écran ; exporté pour les essais. */
export function tuiles(t: TableauSupervision): readonly Tuile[] {
  const jours = Math.max(1, Math.round(t.minutesMesurees / 1440));
  const disponible = t.disponibilite >= t.objectifDeDisponibilite;
  const delaiOk = !t.delaiHorsSeuil && (t.delaiP95S === null || t.delaiP95S < t.objectifDeDelaiS);
  const latenceOk = t.latenceP95Ms === null || t.latenceP95Ms < t.objectifDeLatenceMs;
  const erreursOk = t.erreursBraceletsPct === null || t.erreursBraceletsPct < t.objectifDErreursPct;
  const variation = t.braceletsMuetsIlYAUneHeure === null ? null : t.braceletsMuets - t.braceletsMuetsIlYAUneHeure;
  const partMuets = t.braceletsEnService === 0 ? 0 : (t.braceletsMuets * 100) / t.braceletsEnService;
  const disque = t.capaciteBaseOctets ? (t.tailleBaseOctets * 100) / t.capaciteBaseOctets : null;
  const vingtQuatreHeures = $localize`:@@supervision.periode.heures:Évolution sur 24 heures, une barre par heure`;
  return [
    {
      libelle: $localize`:@@supervision.disponibilite:Disponibilité API · ${jours}:jours: j`,
      valeur: `${NOMBRE.format(t.disponibilite)} %`,
      mention: $localize`:@@supervision.objectif:Objectif ${NOMBRE.format(t.objectifDeDisponibilite)}:objectif: %`,
      ton: disponible ? 'succes' : 'danger',
      ok: disponible,
      note:
        t.minutesIndisponibles === 0
          ? $localize`:@@supervision.aucunIncident:Aucune minute d'indisponibilité`
          : $localize`:@@supervision.indisponible:${t.minutesIndisponibles}:minutes: min d'indisponibilité`,
      periode: $localize`:@@supervision.periode.jours:Disponibilité des 30 derniers jours, une barre par jour`,
      barres: barres(t.disponibiliteParJour, (v) => v < t.objectifDeDisponibilite, 100, Math.min(t.objectifDeDisponibilite - 1, ...t.disponibiliteParJour.filter((v): v is number => v !== null))),
    },
    {
      libelle: $localize`:@@supervision.delai:Événement → notification · p95`,
      valeur: t.delaiP95S === null ? '—' : `${NOMBRE.format(t.delaiP95S)} s`,
      mention: $localize`:@@supervision.objectifDelai:Objectif < ${t.objectifDeDelaiS}:objectif: s`,
      ton: delaiOk ? 'succes' : 'danger',
      ok: delaiOk,
      note:
        t.delaiP95S === null
          ? $localize`:@@supervision.sansMesure:Aucune alerte récente à mesurer`
          : t.derniereAlerte
            ? $localize`:@@supervision.derniereAlerte:Dernière alerte d'exploitation le ${HORODATAGE.format(new Date(t.derniereAlerte))}:date:`
            : $localize`:@@supervision.sansAlerte:Aucune alerte d'exploitation (seuil ${t.seuilDeDelaiS}:seuil: s)`,
      periode: vingtQuatreHeures,
      barres: barres(t.historiques.DELAI_P95_S, (v) => v > t.seuilDeDelaiS, t.seuilDeDelaiS),
    },
    {
      libelle: $localize`:@@supervision.latence:Latence API · p95`,
      valeur: t.latenceP95Ms === null ? '—' : `${ENTIER.format(t.latenceP95Ms)} ms`,
      mention: $localize`:@@supervision.objectifLatence:Objectif < ${t.objectifDeLatenceMs}:objectif: ms`,
      ton: latenceOk ? 'succes' : 'danger',
      ok: latenceOk,
      note:
        t.requetesParMinute === null
          ? $localize`:@@supervision.sansRequetes:Aucun relevé de l'heure passée`
          : $localize`:@@supervision.requetes:${DECIMALE.format(t.requetesParMinute)}:nombre: req/min`,
      periode: vingtQuatreHeures,
      barres: barres(t.historiques.LATENCE_P95_MS, (v) => v >= t.objectifDeLatenceMs, t.objectifDeLatenceMs),
    },
    {
      libelle: $localize`:@@supervision.erreurs:Erreurs des bracelets · 24 h`,
      valeur: t.erreursBraceletsPct === null ? '—' : `${NOMBRE.format(t.erreursBraceletsPct)} %`,
      mention: $localize`:@@supervision.objectifErreurs:Objectif < ${NOMBRE.format(t.objectifDErreursPct)}:objectif: %`,
      ton: erreursOk ? 'succes' : 'danger',
      ok: erreursOk,
      note:
        t.messagesBracelets24h === 0
          ? $localize`:@@supervision.sansMessage:Aucun message reçu`
          : $localize`:@@supervision.messages:Appareil inconnu ou message invalide, sur ${ENTIER.format(t.messagesBracelets24h)}:nombre: messages reçus`,
      periode: vingtQuatreHeures,
      barres: barres(t.historiques.ERREURS_BRACELETS_PCT, (v) => v >= t.objectifDErreursPct, t.objectifDErreursPct),
    },
    {
      libelle: $localize`:@@supervision.muets:Bracelets muets`,
      valeur: ENTIER.format(t.braceletsMuets),
      mention:
        variation === null
          ? ''
          : variation === 0
            ? $localize`:@@supervision.muets.stable:Stable depuis 1 h`
            : variation > 0
              ? $localize`:@@supervision.muets.hausse:+${variation}:nombre: depuis 1 h`
              : $localize`:@@supervision.muets.baisse:−${-variation}:nombre: depuis 1 h`,
      ton: variation === null || variation === 0 ? 'neutre' : variation > 0 ? 'attention' : 'succes',
      // FG-DOC-06 : le nombre est une information ; seule une hausse de plus de 10 % du parc actif en une heure alerte,
      // signe d'une panne de réseau plutôt que de bracelets déchargés.
      ok: !hausseAnormale(variation, t.braceletsEnService),
      note:
        t.braceletsEnService === 0
          ? $localize`:@@supervision.aucunEnService:Aucun bracelet en service`
          : $localize`:@@supervision.partMuets:${DECIMALE.format(partMuets)}:part: % des ${ENTIER.format(t.braceletsEnService)}:nombre: bracelets en service`,
      periode: vingtQuatreHeures,
      barres: barres(t.historiques.BRACELETS_MUETS, () => false, 1).map((barre, rang) => {
        const avant = t.historiques.BRACELETS_MUETS[rang - 1];
        const valeur = t.historiques.BRACELETS_MUETS[rang];
        return { ...barre, horsObjectif: avant != null && valeur != null && hausseAnormale(valeur - avant, t.braceletsEnService) };
      }),
    },
    {
      libelle: $localize`:@@supervision.disque:Espace disque · base`,
      valeur: disque === null ? taille(t.tailleBaseOctets) : `${ENTIER.format(disque)} %`,
      mention: disque === null ? '' : $localize`:@@supervision.objectifDisque:Objectif < ${ENTIER.format(t.objectifDeDisquePct)}:objectif: %`,
      ton: disque === null ? 'neutre' : disque < t.objectifDeDisquePct ? 'succes' : 'danger',
      ok: disque === null || disque < 80,
      note:
        disque === null
          ? $localize`:@@supervision.capaciteInconnue:Taille de la base ; capacité du volume non déclarée`
          : $localize`:@@supervision.capacite:${taille(t.tailleBaseOctets)}:taille: sur ${taille(t.capaciteBaseOctets!)}:capacite:`,
      periode: vingtQuatreHeures,
      barres: t.capaciteBaseOctets
        ? barres(
            t.historiques.BASE_OCTETS.map((v) => (v === null ? null : (v * 100) / t.capaciteBaseOctets!)),
            (v) => v >= t.objectifDeDisquePct,
            100,
          )
        : barres(t.historiques.BASE_OCTETS, () => false),
    },
  ];
}
