import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { Alerte, ClientAlertes, ClientFamille, ClientNotifications, FicheEnfant, NotificationRecue } from 'api';
import { FgBanniere, FgBouton, FgFeuille, FgIcon, FgSquelette, NomIcone } from 'ui';

import { erreurLisible } from '../commun/erreurs';
import { heure, ilYA } from '../commun/temps';
import { enCours, iconeAlerte, parGravite, statutLisible, titreComplet, titreCourt } from './libelles';

const RAFRAICHISSEMENT_MS = 30_000;
const JOUR = new Intl.DateTimeFormat('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' });

/** Alertes d'un enfant qui attendent encore une prise en charge (écran 27). */
interface Urgence {
  readonly enfant: FicheEnfant | undefined;
  readonly enfantId: string;
  readonly alertes: readonly Alerte[];
}

/** Une ligne du fil : une alerte ou une notification, au même format. */
interface Entree {
  readonly id: string;
  readonly date: string;
  readonly titre: string;
  readonly texte: string;
  readonly etat: string;
  readonly icone: NomIcone;
  readonly pastille: string;
  readonly lien: string;
  /** Point bleu : notification non lue, ou alerte qui attend encore quelqu'un. */
  readonly nonLue: boolean;
  readonly close: boolean;
  readonly notification: string | null;
}

interface Jour {
  readonly libelle: string;
  readonly entrees: readonly Entree[];
}

/** Icône d'une notification, d'après ce qu'elle concerne. */
function iconeNotification(notification: NotificationRecue): NomIcone {
  if (notification.modele.startsWith('PAIEMENT') || notification.lien?.startsWith('/abonnement')) {
    return 'recu';
  }
  if (notification.modele === 'REPONSE_DU_SUPPORT') {
    return 'support';
  }
  if (notification.modele.startsWith('SIGNALEMENT')) {
    return 'drapeau';
  }
  if (notification.lien?.includes('/bracelet/retrait')) {
    return 'retrait';
  }
  return notification.lien?.includes('/bracelet') ? 'bracelet' : 'info';
}

/** « Aujourd'hui », « Hier », sinon le jour en toutes lettres. */
export function libelleJour(iso: string, maintenant = new Date()): string {
  const jour = new Date(iso);
  const minuit = new Date(maintenant.getFullYear(), maintenant.getMonth(), maintenant.getDate());
  const ecart = Math.round((minuit.getTime() - new Date(jour.getFullYear(), jour.getMonth(), jour.getDate()).getTime()) / 86_400_000);
  if (ecart <= 0) {
    return $localize`:@@alertes.jour.aujourdhui:Aujourd'hui`;
  }
  if (ecart === 1) {
    return $localize`:@@alertes.jour.hier:Hier`;
  }
  return JOUR.format(jour);
}

/**
 * Centre des alertes (écrans 24, 26 et 27 ; US-ENF-001, US-PAR-018). En tête, les alertes qui attendent une
 * prise en charge, regroupées par enfant et classées par gravité : une seule prise en charge suffit pour le
 * groupe. Puis un fil par jour, où alertes et informations se lisent au même endroit, même sans push.
 */
@Component({
  selector: 'app-centre-alertes',
  imports: [RouterLink, FgBanniere, FgBouton, FgFeuille, FgIcon, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex items-center justify-between gap-3">
      <h1 class="m-0 text-titre-ecran font-bold tracking-tight" i18n="@@alertes.titre">Alertes</h1>
      @if (nonLues() > 0) {
        <button type="button" class="min-h-11 text-label font-semibold text-accent focus-visible:outline-2 focus-visible:outline-accent" (click)="toutMarquerLu()" i18n="@@alertes.toutLu">Tout marquer lu</button>
      }
    </div>

    <div class="grid grid-cols-2 gap-1 rounded-lg bg-surface-2 p-1" role="group" i18n-aria-label="@@alertes.filtre" aria-label="Alertes affichées">
      <button type="button" class="min-h-11 rounded-md px-3 text-label font-semibold focus-visible:outline-2 focus-visible:outline-accent" [class]="onglet(!toutes())" [attr.aria-pressed]="!toutes()" (click)="toutes.set(false)" i18n="@@alertes.enCours">En cours · {{ nombreEnCours() }}</button>
      <button type="button" class="min-h-11 rounded-md px-3 text-label font-semibold focus-visible:outline-2 focus-visible:outline-accent" [class]="onglet(toutes())" [attr.aria-pressed]="toutes()" (click)="toutes.set(true)" i18n="@@alertes.toutes">Toutes</button>
    </div>

    @if (alertes()) {
      @if (erreur(); as message) {
        <fg-banner ton="erreur">{{ message }}</fg-banner>
      }

      @for (urgence of urgences(); track urgence.enfantId) {
        <section class="flex flex-col overflow-hidden rounded-lg border border-alert bg-surface" [attr.aria-label]="titreUrgence(urgence)">
          <div class="flex flex-col gap-1 bg-alert px-4 py-3.5 text-on-alert">
            <span class="text-caption font-bold tracking-wider uppercase">{{ surtitre(urgence) }}</span>
            <h2 class="m-0 font-display text-h3 font-bold">{{ titreUrgence(urgence) }}</h2>
            @if (urgence.alertes.length > 1) {
              <p class="m-0 text-label" i18n="@@alertes.groupe.ordre">Regroupées par ordre de gravité</p>
            }
          </div>
          <ol class="m-0 flex list-none flex-col divide-y divide-line p-0">
            @for (alerte of urgence.alertes; track alerte.id; let rang = $index) {
              <li>
                <a class="flex min-h-14 items-center gap-3 px-4 py-2.5 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-accent" [routerLink]="['/alertes', alerte.id]">
                  <span class="grid size-10 flex-none place-items-center rounded-md" [class]="pastille(alerte)" aria-hidden="true"><fg-icon [nom]="icone(alerte)" [taille]="20" /></span>
                  <span class="flex min-w-0 flex-1 flex-col gap-0.5">
                    <strong class="truncate text-body font-semibold">{{ titre(alerte) }}</strong>
                    <span class="text-label text-text-2 tabular-nums">{{ heure(alerte.ouverteLe) }} · {{ ilYA(alerte.ouverteLe) }}</span>
                  </span>
                  @if (urgence.alertes.length > 1) {
                    <span class="text-label font-bold text-alert-ink tabular-nums" aria-hidden="true">{{ rang + 1 }}</span>
                  }
                </a>
              </li>
            }
          </ol>
          <div class="flex flex-col gap-2 border-t border-line p-4">
            @if (urgence.alertes.length > 1) {
              <p class="m-0 text-label text-text-2" i18n="@@alertes.groupe.aide">Une seule prise en charge suffit. Chaque alerte reste tracée séparément dans le journal.</p>
            }
            <button fg-button variante="alert" type="button" [chargement]="enTraitement() === urgence.enfantId" (click)="prendreEnCharge(urgence)">
              @if (urgence.alertes.length > 1) {
                <ng-container i18n="@@alertes.toutPrendre">Tout prendre en charge</ng-container>
              } @else {
                <ng-container i18n="@@alertes.prendre">Prendre en charge</ng-container>
              }
            </button>
          </div>
        </section>
      }

      @if (!toutes() && nombreEnCours() === 0) {
        <p class="m-0 rounded-lg border border-line bg-surface p-5 text-body text-text-2" i18n="@@alertes.aucune">Aucune alerte en cours.</p>
      }

      @for (jour of jours(); track jour.libelle) {
        <section class="flex flex-col gap-2.5" [attr.aria-label]="jour.libelle">
          <h2 class="m-0 text-caption font-bold tracking-wider text-text-3 uppercase">{{ jour.libelle }}</h2>
          @for (entree of jour.entrees; track entree.id) {
            <a
              class="flex gap-3 rounded-lg border p-3.5 focus-visible:outline-2 focus-visible:outline-accent"
              [class]="entree.close ? 'border-transparent bg-surface-2' : 'border-line bg-surface'"
              [routerLink]="entree.lien"
              (click)="ouvrir(entree)"
            >
              <span class="grid size-10 flex-none place-items-center rounded-md" [class]="entree.pastille" aria-hidden="true"><fg-icon [nom]="entree.icone" [taille]="20" /></span>
              <span class="flex min-w-0 flex-1 flex-col gap-0.5">
                <strong class="text-body font-semibold" [class.text-text-2]="entree.close">{{ entree.titre }}</strong>
                <span class="text-label text-text-2">{{ entree.texte }}</span>
                <span class="text-caption text-text-3 tabular-nums">{{ heure(entree.date) }} · {{ entree.etat }}</span>
              </span>
              @if (entree.nonLue) {
                <span class="mt-1.5 size-2.5 flex-none rounded-full bg-accent" i18n-aria-label="@@alertes.nonLue" aria-label="non lue" role="img"></span>
              }
            </a>
          }
        </section>
      } @empty {
        @if (toutes()) {
          <p class="m-0 rounded-lg border border-line bg-surface p-5 text-body text-text-2" i18n="@@alertes.jamais">Aucune alerte n'a été enregistrée.</p>
        }
      }

      @if (enfants().length > 0) {
        <button fg-button class="mt-auto" variante="secondary" type="button" (click)="signalement.set(true)" i18n="@@alertes.signaler">Signaler une disparition</button>
      }
    } @else if (erreur(); as message) {
      <fg-banner ton="erreur">{{ message }}</fg-banner>
      <button fg-button variante="secondary" type="button" class="self-start" (click)="charger()" i18n="@@commun.reessayer">Réessayer</button>
    } @else {
      <fg-skeleton forme="carte" />
      <fg-skeleton forme="carte" />
    }

    <fg-sheet i18n-titre="@@alertes.signaler.titre" titre="Signaler une disparition" [ouverte]="signalement()" (fermee)="signalement.set(false)">
      <p class="m-0 text-body text-text-2" i18n="@@alertes.signaler.texte">Le signalement est horodaté et les autres tuteurs sont prévenus. Vous restez seul à décider de la transmission aux forces de sécurité.</p>
      @for (enfant of aSignaler(); track enfant.id) {
        <button fg-button variante="alert" type="button" [chargement]="enTraitement() === enfant.id" (click)="signaler(enfant)" i18n="@@alertes.signaler.pour">Signaler pour {{ enfant.prenom }}</button>
      }
      <button fg-button variante="secondary" type="button" (click)="signalement.set(false)" i18n="@@commun.annuler">Annuler</button>
    </fg-sheet>
  `,
  host: { class: 'mx-auto flex min-h-[calc(100dvh-4.5rem)] w-full max-w-md flex-col gap-4 px-5 pt-6 pb-7 lg:mx-0 lg:min-h-dvh lg:max-w-2xl lg:px-10 lg:pt-10' },
})
export class CentreAlertes {
  private readonly client = inject(ClientAlertes);
  private readonly famille = inject(ClientFamille);
  private readonly notifications = inject(ClientNotifications);
  private readonly router = inject(Router);
  protected readonly signalement = signal(false);
  /** Enfant pour lequel le tableau de bord a demandé un signalement (action « Signaler » de l'écran 16). */
  readonly demandeSignalement = input<string | undefined>(undefined, { alias: 'signaler' });
  protected readonly aSignaler = computed(() => {
    const id = this.demandeSignalement();
    const pour = id ? this.enfants().filter((enfant) => enfant.id === id) : [];
    return pour.length > 0 ? pour : this.enfants();
  });

  protected readonly heure = heure;
  protected readonly ilYA = ilYA;
  protected readonly icone = iconeAlerte;
  protected readonly titre = titreCourt;

  protected readonly alertes = signal<Alerte[] | null>(null);
  protected readonly enfants = signal<FicheEnfant[]>([]);
  /** Notifications reçues hors alertes : elles restent lisibles ici même si le push n'est pas arrivé. */
  protected readonly informations = signal<readonly NotificationRecue[]>([]);
  protected readonly toutes = signal(false);
  protected readonly enTraitement = signal<string | null>(null);
  protected readonly erreur = signal<string | null>(null);

  protected readonly nombreEnCours = computed(() => (this.alertes() ?? []).filter(enCours).length);
  protected readonly nonLues = computed(() => this.informations().filter((information) => !information.lue).length);
  protected readonly urgences = computed((): Urgence[] => {
    const parEnfant = new Map<string, Alerte[]>();
    for (const alerte of (this.alertes() ?? []).filter((a) => a.statut === 'OUVERTE')) {
      parEnfant.set(alerte.enfantId, [...(parEnfant.get(alerte.enfantId) ?? []), alerte]);
    }
    return [...parEnfant.entries()].map(([enfantId, alertes]) => ({
      enfantId,
      enfant: this.enfants().find((enfant) => enfant.id === enfantId),
      alertes: parGravite(alertes),
    }));
  });
  /**
   * « En cours » : les alertes prises en charge mais pas encore closes, et les informations non lues ; celles
   * qui attendent une prise en charge sont déjà en tête. « Toutes » : tout, le plus récent d'abord.
   */
  protected readonly jours = computed((): Jour[] => {
    const toutes = this.toutes();
    const alertes = (this.alertes() ?? []).filter((a) => toutes || (enCours(a) && a.statut !== 'OUVERTE'));
    const informations = this.informations().filter((information) => toutes || !information.lue);
    const entrees = [...alertes.map((a) => this.entreeAlerte(a)), ...informations.map((i) => this.entreeInformation(i))]
      .sort((a, b) => b.date.localeCompare(a.date));
    const parJour = new Map<string, Entree[]>();
    for (const entree of entrees) {
      const libelle = libelleJour(entree.date);
      parJour.set(libelle, [...(parJour.get(libelle) ?? []), entree]);
    }
    return [...parJour.entries()].map(([libelle, liste]) => ({ libelle, entrees: liste }));
  });

  constructor() {
    effect(() => {
      if (this.demandeSignalement()) {
        this.signalement.set(true);
      }
    });
    this.charger();
    const minuterie = setInterval(() => this.charger(), RAFRAICHISSEMENT_MS);
    inject(DestroyRef).onDestroy(() => clearInterval(minuterie));
  }

  protected onglet(actif: boolean): string {
    return actif ? 'bg-surface text-text shadow-e1' : 'text-text-2';
  }

  protected pastille(alerte: Alerte): string {
    // L'ambre signale une alerte qui attend encore quelqu'un ; une alerte close redevient neutre.
    return alerte.closeLe ? 'bg-surface text-text-2' : alerte.gravite === 'CRITIQUE' ? 'bg-alert text-on-alert' : 'bg-alert-soft text-alert-ink';
  }

  protected surtitre(urgence: Urgence): string {
    const prenom = urgence.enfant?.prenom ?? '';
    return urgence.alertes.length > 1
      ? $localize`:@@alertes.groupe.surtitre:${urgence.alertes.length}:nombre: alertes · ${prenom}:prenom:`
      : $localize`:@@alertes.groupe.surtitreUne:Alerte · ${prenom}:prenom:`;
  }

  protected titreUrgence(urgence: Urgence): string {
    return titreComplet(urgence.alertes[0], urgence.enfant?.prenom ?? '');
  }

  protected ouvrir(entree: Entree): void {
    if (entree.notification && entree.nonLue) {
      this.notifications.marquerLue(entree.notification).subscribe({ next: () => undefined, error: () => undefined });
    }
  }

  protected toutMarquerLu(): void {
    this.notifications.toutMarquerLu().subscribe({
      next: () => this.informations.update((liste) => liste.map((information) => ({ ...information, lue: true }))),
      error: (cause: unknown) => this.erreur.set(erreurLisible(cause).message),
    });
  }

  protected prendreEnCharge(urgence: Urgence): void {
    this.enTraitement.set(urgence.enfantId);
    this.erreur.set(null);
    this.client.prendreEnCharge(urgence.enfantId).subscribe({
      next: () => {
        this.enTraitement.set(null);
        this.charger();
      },
      error: (cause: unknown) => {
        this.enTraitement.set(null);
        this.erreur.set(erreurLisible(cause).message);
      },
    });
  }

  protected signaler(enfant: FicheEnfant): void {
    this.enTraitement.set(enfant.id);
    this.client.signaler(enfant.id).subscribe({
      next: (alerte) => void this.router.navigate(['/alertes', alerte.id]),
      error: (cause: unknown) => {
        this.enTraitement.set(null);
        this.signalement.set(false);
        this.erreur.set(erreurLisible(cause).message);
      },
    });
  }

  protected charger(): void {
    this.notifications.recues().subscribe({ next: (recues) => this.informations.set(recues), error: () => undefined });
    forkJoin({ alertes: this.client.mesAlertes(false), enfants: this.famille.mesEnfants() }).subscribe({
      next: ({ alertes, enfants }) => {
        this.erreur.set(null);
        this.enfants.set(enfants);
        this.alertes.set(alertes);
      },
      error: (cause: unknown) => {
        // Les alertes déjà affichées restent à l'écran si le réseau manque au rafraîchissement.
        if (!this.alertes()) {
          this.erreur.set(erreurLisible(cause).message);
        }
      },
    });
  }

  private entreeAlerte(alerte: Alerte): Entree {
    const prenom = this.enfants().find((enfant) => enfant.id === alerte.enfantId)?.prenom ?? '';
    return {
      id: alerte.id,
      date: alerte.ouverteLe,
      titre: titreCourt(alerte),
      texte: titreComplet(alerte, prenom),
      etat: statutLisible(alerte.statut).toLowerCase(),
      icone: iconeAlerte(alerte),
      pastille: this.pastille(alerte),
      lien: `/alertes/${alerte.id}`,
      nonLue: alerte.statut === 'OUVERTE',
      close: !!alerte.closeLe,
      notification: null,
    };
  }

  /** Les textes sont rédigés pour suivre « FasoGuardian : » dans un SMS : à l'écran, ils prennent une majuscule. */
  private entreeInformation(information: NotificationRecue): Entree {
    return {
      id: information.id,
      date: information.creeeLe,
      titre: information.titre,
      texte: information.texte.charAt(0).toUpperCase() + information.texte.slice(1),
      etat: information.lue ? $localize`:@@alertes.lue:lue` : $localize`:@@alertes.nonLueEtat:non lue`,
      icone: iconeNotification(information),
      pastille: 'bg-attention-soft text-attention-text',
      lien: information.lien ?? '/',
      nonLue: !information.lue,
      close: false,
      notification: information.id,
    };
  }
}
