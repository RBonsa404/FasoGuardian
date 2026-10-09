import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { ClientConformite, TableauSupervision } from 'api';
import { FgBanniere, FgSquelette } from 'ui';

import { erreurLisible, estRefus } from '../commun/acces';

const RAFRAICHISSEMENT_MS = 30_000;
const NOMBRE = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 2 });
const HORODATAGE = new Intl.DateTimeFormat('fr-FR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });

interface Tuile {
  readonly libelle: string;
  readonly valeur: string;
  readonly objectif: string;
  readonly ok: boolean;
  readonly note: string;
}

/**
 * Supervision (écran 72, US-ADM-004) : disponibilité mesurée sur trente jours, délai entre un événement du
 * bracelet et la notification des parents, état du parc en service. L'écran se rafraîchit de lui-même.
 */
@Component({
  selector: 'app-supervision',
  imports: [FgBanniere, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex flex-wrap items-center justify-between gap-3">
      <h1 class="m-0 text-titre-ecran font-bold tracking-tight" i18n="@@supervision.titre">Supervision</h1>
      @if (tableau()) {
        <span class="flex items-center gap-2 text-label font-semibold text-text-2" role="status">
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
      <div class="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
        @for (tuile of tuiles(); track tuile.libelle) {
          <section class="flex flex-col gap-2 rounded-lg bg-surface p-4" [class]="tuile.ok ? 'border border-line' : 'border border-danger'">
            <div class="flex items-baseline justify-between gap-3">
              <h2 class="m-0 text-label font-semibold">{{ tuile.libelle }}</h2>
              <span class="text-caption font-medium text-text-3">{{ tuile.objectif }}</span>
            </div>
            <strong class="font-display text-data font-bold tabular-nums" [class.text-danger]="!tuile.ok">{{ tuile.valeur }}</strong>
            <span class="text-caption font-medium" [class]="tuile.ok ? 'text-text-2' : 'text-danger'">{{ tuile.note }}</span>
          </section>
        }
      </div>
      <p class="m-0 text-caption text-text-3" i18n="@@supervision.note">La disponibilité est la part des minutes où le serveur a répondu et joint sa base. Les décomptes d'activité partent du dernier démarrage du serveur ; la télésurveillance (Prometheus, Grafana) en garde l'historique.</p>
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

/** Les indicateurs suivis, dans l'ordre de l'écran ; exporté pour les essais. */
export function tuiles(t: TableauSupervision): readonly Tuile[] {
  const disponible = t.disponibilite >= t.objectifDeDisponibilite;
  const jours = Math.max(1, Math.round(t.minutesMesurees / 1440));
  return [
    {
      libelle: $localize`:@@supervision.disponibilite:Disponibilité · ${jours}:jours: j`,
      valeur: `${NOMBRE.format(t.disponibilite)} %`,
      objectif: $localize`:@@supervision.objectif:Objectif ${NOMBRE.format(t.objectifDeDisponibilite)}:objectif: %`,
      ok: disponible,
      note:
        t.minutesIndisponibles === 0
          ? $localize`:@@supervision.aucunIncident:Aucune minute d'indisponibilité`
          : $localize`:@@supervision.indisponible:${t.minutesIndisponibles}:minutes: min d'indisponibilité`,
    },
    {
      libelle: $localize`:@@supervision.delai:Événement → notification · 95e centile`,
      valeur: t.delaiP95S === null ? '—' : `${NOMBRE.format(t.delaiP95S)} s`,
      objectif: $localize`:@@supervision.seuil:Seuil ${t.seuilDeDelaiS}:seuil: s`,
      ok: !t.delaiHorsSeuil,
      note:
        t.delaiP95S === null
          ? $localize`:@@supervision.sansMesure:Aucune alerte récente à mesurer`
          : t.derniereAlerte
            ? $localize`:@@supervision.derniereAlerte:Dernière alerte d'exploitation le ${HORODATAGE.format(new Date(t.derniereAlerte))}:date:`
            : $localize`:@@supervision.sansAlerte:Aucune alerte d'exploitation`,
    },
    {
      libelle: $localize`:@@supervision.bracelets:Bracelets en service`,
      valeur: NOMBRE.format(t.braceletsEnService),
      objectif: '',
      ok: t.braceletsMuets === 0,
      note: t.braceletsMuets === 0 ? $localize`:@@supervision.tousParlent:Tous donnent des nouvelles` : $localize`:@@supervision.muets:${t.braceletsMuets}:nombre: sans nouvelles`,
    },
    {
      libelle: $localize`:@@supervision.alertes:Alertes ouvertes`,
      valeur: NOMBRE.format(t.alertesOuvertes),
      objectif: '',
      ok: true,
      note: $localize`:@@supervision.depuisDemarrage:depuis le démarrage du serveur`,
    },
    {
      libelle: $localize`:@@supervision.notifications:Notifications`,
      valeur: NOMBRE.format(t.notificationsPoussees + t.smsEnvoyes),
      objectif: '',
      ok: true,
      note: $localize`:@@supervision.canaux:${t.notificationsPoussees}:push: push · ${t.smsEnvoyes}:sms: SMS`,
    },
    {
      libelle: $localize`:@@supervision.refuses:Messages de bracelets refusés`,
      valeur: NOMBRE.format(t.messagesRefuses),
      objectif: '',
      ok: true,
      note: $localize`:@@supervision.refuses.note:appareil inconnu ou message invalide`,
    },
  ];
}
