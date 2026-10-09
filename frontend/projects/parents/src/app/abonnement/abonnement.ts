import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { Abonnement, ClientAbonnements, ClientFamille, FicheEnfant, Offre } from 'api';
import { FgBanniere, FgBouton, FgIcon, FgInterrupteur, FgSquelette } from 'ui';

import { erreurLisible } from '../commun/erreurs';
import { jourCourt, montant, nomMoyen, resumeOffre } from './libelles';

/**
 * Mon abonnement (écrans 41, 44 et 45 ; US-PAR-015, US-PAR-016, US-SYS-008) : l'offre en cours, son échéance,
 * l'état d'un impayé et les offres à comparer. Avec plusieurs enfants, le parent choisit celui qu'il regarde.
 */
@Component({
  selector: 'app-abonnement',
  imports: [ReactiveFormsModule, RouterLink, FgBanniere, FgBouton, FgIcon, FgInterrupteur, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="-ml-2.5 grid size-11 place-items-center rounded-md focus-visible:outline-2 focus-visible:outline-accent" routerLink="/" i18n-aria-label="@@etape.retour" aria-label="Retour">
      <fg-icon nom="retour" [taille]="22" />
    </a>
    <h1 class="m-0 text-h2 font-bold tracking-tight" i18n="@@abonnement.titre">Mon abonnement</h1>

    @if (enfants().length > 1) {
      <div class="flex flex-wrap gap-2" role="group" i18n-aria-label="@@abonnement.enfants" aria-label="Enfant concerné">
        @for (fiche of enfants(); track fiche.id) {
          <button
            type="button"
            class="min-h-11 rounded-full border px-4 text-label font-semibold focus-visible:outline-2 focus-visible:outline-accent"
            [class]="fiche.id === choisi() ? 'border-accent bg-accent-soft text-text' : 'border-line bg-surface text-text-2'"
            [attr.aria-pressed]="fiche.id === choisi()"
            (click)="choisir(fiche.id)"
          >
            {{ fiche.prenom }}
          </button>
        }
      </div>
    }

    @if (abonnement(); as a) {
      @if (a.paiementEnCours; as attente) {
        <fg-banner ton="info" icone="mobile-money">
          <span i18n="@@abonnement.attente">Un paiement de {{ montant(attente.montantFcfa) }} FCFA attend votre validation sur le téléphone.</span>
          <a class="font-semibold text-accent underline" [routerLink]="['/abonnement/paiement']" [queryParams]="{ enfant: choisi(), paiement: attente.id }" i18n="@@abonnement.attente.suivre">Suivre</a>
        </fg-banner>
      }

      @if (a.statut) {
        <section class="flex flex-col gap-2.5 rounded-lg bg-primary p-4 text-white" aria-labelledby="offre-en-cours">
          <div class="flex items-center justify-between gap-3">
            <strong id="offre-en-cours" class="font-display text-h3 font-semibold">{{ a.libelle }}</strong>
            <span class="text-body font-semibold tabular-nums" i18n="@@abonnement.prix">{{ montant(a.prixFcfa ?? 0) }} FCFA / mois</span>
          </div>
          <span class="text-label opacity-80">{{ echeance(a) }}</span>
        </section>

        @switch (a.statut) {
          @case ('EN_RETARD') {
            <section class="flex flex-col gap-1.5 rounded-lg bg-attention-soft p-4" role="status">
              <strong class="text-body font-semibold text-attention-text" i18n="@@abonnement.retard.titre">Paiement non reçu</strong>
              <span class="text-label text-text-2" i18n="@@abonnement.retard.texte">Tout fonctionne encore. Réglez avant le {{ jour(a.restrictionLe) }} : ce jour-là, le suivi continu et l'historique étendu seront suspendus.</span>
              <span class="flex items-center gap-1.5 text-caption font-semibold text-success"><fg-icon nom="valider" [taille]="14" /><ng-container i18n="@@abonnement.toujours">Page QR et SOS toujours actifs</ng-container></span>
            </section>
          }
          @case ('RESTREINT') {
            <section class="flex flex-col gap-1.5 rounded-lg border border-danger bg-surface p-4" role="status">
              <strong class="text-body font-semibold text-danger" i18n="@@abonnement.restreint.titre">Suivi continu suspendu</strong>
              <span class="text-label text-text-2" i18n="@@abonnement.restreint.texte">Position à la demande uniquement, historique limité à 24 h. Le paiement rétablit tout aussitôt.</span>
              <span class="flex items-center gap-1.5 text-caption font-semibold text-success"><fg-icon nom="valider" [taille]="14" /><ng-container i18n="@@abonnement.toujours">Page QR et SOS toujours actifs</ng-container></span>
            </section>
          }
        }

        @if (a.moyen) {
          <section class="rounded-lg border border-line bg-surface p-4">
            <fg-switch [formControl]="renouvellement" i18n-libelle="@@abonnement.auto" libelle="Renouvellement automatique">
              <strong class="text-body font-semibold" i18n="@@abonnement.auto">Renouvellement automatique</strong>
              <span class="text-label text-text-2">{{ nomMoyen(a.moyen) }} {{ a.numeroMasque }}</span>
            </fg-switch>
          </section>
        }
      } @else if (a.droits.offre === 'PREMIUM') {
        <p class="m-0 rounded-lg border border-line bg-surface p-4 text-body text-text-2" i18n="@@abonnement.couvert">Cet enfant est couvert par l'abonnement Premium de la famille : il n'a rien à payer de plus.</p>
      } @else {
        <p class="m-0 rounded-lg border border-line bg-surface p-4 text-body text-text-2" i18n="@@abonnement.aucun">Aucun abonnement pour cet enfant. Choisissez une offre : elle démarre dès que l'opérateur confirme votre paiement.</p>
      }

      <h2 class="m-0 pt-1 text-label font-semibold" i18n="@@abonnement.comparer">Comparer les offres</h2>
      <div class="flex flex-col gap-2">
        @for (offre of offres(); track offre.code) {
          <button
            type="button"
            class="flex flex-col gap-1.5 rounded-lg bg-surface px-3.5 py-3 text-left focus-visible:outline-2 focus-visible:outline-accent"
            [class]="offre.code === a.offre ? 'border-2 border-accent' : 'border border-line'"
            (click)="payer(offre)"
          >
            <span class="flex items-center justify-between gap-3">
              <strong class="text-body font-semibold">
                {{ offre.libelle }}
                @if (offre.code === a.offre) {
                  <ng-container i18n="@@abonnement.actuelle"> · actuelle</ng-container>
                }
              </strong>
              <span class="text-label font-semibold tabular-nums">{{ montant(offre.prixFcfa) }} FCFA</span>
            </span>
            <span class="text-caption text-text-2">{{ resume(offre) }}</span>
          </button>
        }
      </div>
      <p class="m-0 text-caption font-medium text-text-3" i18n="@@abonnement.inclus">SOS, détection de retrait et page QR inclus dans toutes les offres.</p>

      @if (erreur(); as message) {
        <fg-banner ton="erreur">{{ message }}</fg-banner>
      }

      <div class="mt-auto flex flex-col gap-2 pt-2">
        @if (aRegler(); as offre) {
          <button fg-button taille="lg" type="button" (click)="payer(offre)" i18n="@@abonnement.payer">Payer {{ montant(offre.prixFcfa) }} FCFA</button>
        }
        <a class="grid min-h-12 place-items-center text-body font-semibold text-accent focus-visible:outline-2 focus-visible:outline-accent" routerLink="/abonnement/recus" i18n="@@abonnement.recus">Voir mes reçus</a>
      </div>
    } @else if (erreur(); as message) {
      <fg-banner ton="erreur">{{ message }}</fg-banner>
    } @else if (sansEnfant()) {
      <p class="m-0 rounded-lg border border-line bg-surface p-4 text-body text-text-2" i18n="@@abonnement.sansEnfant">Ajoutez d'abord un enfant : l'abonnement se prend pour chacun d'eux.</p>
    } @else {
      <fg-skeleton forme="carte" />
      <fg-skeleton forme="carte" />
    }
  `,
  host: { class: 'mx-auto flex min-h-dvh w-full max-w-md flex-col gap-4 px-5 pt-6 pb-7 lg:mx-0 lg:max-w-2xl lg:px-10 lg:pt-10' },
})
export class EcranAbonnement {
  /** Enfant à montrer (paramètre de requête) ; à défaut, le premier. */
  readonly enfant = input<string>();

  private readonly client = inject(ClientAbonnements);
  private readonly famille = inject(ClientFamille);
  private readonly router = inject(Router);

  protected readonly montant = montant;
  protected readonly nomMoyen = nomMoyen;
  protected readonly resume = resumeOffre;

  protected readonly enfants = signal<readonly FicheEnfant[]>([]);
  protected readonly sansEnfant = signal(false);
  protected readonly offres = signal<readonly Offre[]>([]);
  protected readonly abonnement = signal<Abonnement | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly renouvellement = new FormControl(false, { nonNullable: true });

  protected readonly choisi = computed(() => {
    const demande = this.enfant();
    const fiches = this.enfants();
    return fiches.find((fiche) => fiche.id === demande)?.id ?? fiches[0]?.id ?? null;
  });

  /** L'offre à payer d'un geste : celle en cours, quand son échéance est dépassée ou proche. */
  protected readonly aRegler = computed(() => {
    const a = this.abonnement();
    if (!a?.statut || a.paiementEnCours) {
      return null;
    }
    const proche = a.prochaineEcheance !== null && new Date(a.prochaineEcheance).getTime() - Date.now() < 7 * 86_400_000;
    return a.statut !== 'ACTIF' || proche ? (this.offres().find((offre) => offre.code === a.offre) ?? null) : null;
  });

  constructor() {
    this.famille.mesEnfants().subscribe({
      next: (fiches) => {
        this.enfants.set(fiches);
        this.sansEnfant.set(fiches.length === 0);
      },
      error: (cause: unknown) => this.erreur.set(erreurLisible(cause).message),
    });
    this.client.offres().subscribe({
      next: (offres) => this.offres.set(offres),
      error: (cause: unknown) => this.erreur.set(erreurLisible(cause).message),
    });
    effect(() => {
      const id = this.choisi();
      if (id) {
        this.charger(id);
      }
    });
    this.renouvellement.valueChanges.subscribe((automatique) => this.regler(automatique));
  }

  protected jour(iso: string | null): string {
    return iso ? jourCourt(iso) : '';
  }

  protected echeance(a: Abonnement): string {
    const jour = this.jour(a.prochaineEcheance);
    if (a.statut !== 'ACTIF') {
      return $localize`:@@abonnement.echeance.depassee:Échéance du ${jour}:jour: non réglée`;
    }
    return a.renouvellementAuto && a.moyen
      ? $localize`:@@abonnement.echeance.auto:Prochaine échéance ${jour}:jour: · renouvellement auto · ${nomMoyen(a.moyen)}:moyen:`
      : $localize`:@@abonnement.echeance.manuel:Prochaine échéance ${jour}:jour: · à renouveler vous-même`;
  }

  protected choisir(id: string): void {
    void this.router.navigate([], { queryParams: { enfant: id }, replaceUrl: true });
  }

  protected payer(offre: Offre): void {
    void this.router.navigate(['/abonnement/paiement'], { queryParams: { enfant: this.choisi(), offre: offre.code } });
  }

  private regler(automatique: boolean): void {
    const id = this.choisi();
    if (!id) {
      return;
    }
    this.erreur.set(null);
    this.client.choisirRenouvellement(id, automatique).subscribe({
      next: (abonnement) => this.abonnement.set(abonnement),
      error: (cause: unknown) => {
        this.erreur.set(erreurLisible(cause).message);
        this.renouvellement.setValue(!automatique, { emitEvent: false });
      },
    });
  }

  private charger(id: string): void {
    this.abonnement.set(null);
    this.erreur.set(null);
    this.client.abonnement(id).subscribe({
      next: (abonnement) => {
        this.abonnement.set(abonnement);
        this.renouvellement.setValue(abonnement.renouvellementAuto, { emitEvent: false });
      },
      error: (cause: unknown) => this.erreur.set(erreurLisible(cause).message),
    });
  }
}
