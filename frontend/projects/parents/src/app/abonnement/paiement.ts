import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ClientAbonnements, CodeOffre, MoyenPaiement, Offre, Paiement } from 'api';
import { FgBanniere, FgBouton, FgIcon, FgInterrupteur, FgSquelette, FgTelephone } from 'ui';

import { erreurLisible } from '../commun/erreurs';
import { jourLong, montant, nomMoyen } from './libelles';

/** Délai entre deux interrogations du serveur pendant l'attente de la validation. */
export const INTERROGATION_MS = 5000;

type Etape = 'saisie' | 'attente' | 'recu' | 'echec' | 'delai';

/**
 * Paiement par mobile money (écran 42, US-PAR-015). Le parent choisit son portefeuille, puis valide sur son
 * téléphone ; l'écran interroge le serveur toutes les cinq secondes pendant deux minutes. Rien n'est tenu
 * pour payé avant la confirmation de l'opérateur.
 */
@Component({
  selector: 'app-paiement',
  imports: [ReactiveFormsModule, RouterLink, FgBanniere, FgBouton, FgIcon, FgInterrupteur, FgSquelette, FgTelephone],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @switch (etape()) {
      @case ('saisie') {
        <a class="-ml-2.5 grid size-11 place-items-center rounded-md focus-visible:outline-2 focus-visible:outline-accent" routerLink="/abonnement" [queryParams]="{ enfant: enfant() }" i18n-aria-label="@@etape.retour" aria-label="Retour">
          <fg-icon nom="retour" [taille]="22" />
        </a>
        @if (choisie(); as o) {
          <div class="flex flex-col gap-1">
            <h1 class="m-0 text-h2 font-bold tracking-tight" i18n="@@paiement.titre">Payer l'abonnement</h1>
            <p class="m-0 text-body text-text-2">{{ o.libelle }}</p>
          </div>
          <div class="flex items-baseline justify-between rounded-lg border border-line bg-surface p-4">
            <span class="text-label text-text-2" i18n="@@paiement.montant">Montant</span>
            <span class="font-display text-data font-bold tabular-nums">{{ montant(o.prixFcfa) }} <span class="text-label font-semibold text-text-2">FCFA</span></span>
          </div>

          <fieldset class="m-0 flex flex-col gap-2 border-0 p-0">
            <legend class="pb-2 text-label font-semibold" i18n="@@paiement.moyen">Moyen de paiement</legend>
            @for (option of moyens; track option.code) {
              <label class="flex min-h-14 cursor-pointer items-center gap-3 rounded-lg bg-surface px-3.5 py-2.5 focus-within:outline-2 focus-within:outline-accent" [class]="moyen.value === option.code ? 'border-2 border-accent' : 'border border-line'">
                <input class="sr-only" type="radio" name="moyen" [formControl]="moyen" [value]="option.code" />
                <span class="grid size-9 flex-none place-items-center rounded-md bg-surface-2 text-text-2" aria-hidden="true"><fg-icon nom="mobile-money" [taille]="18" /></span>
                <span class="flex flex-col">
                  <strong class="text-body font-semibold">{{ option.nom }}</strong>
                  <span class="text-caption text-text-2">{{ option.aide }}</span>
                </span>
              </label>
            }
          </fieldset>

          <fg-phone-input [formControl]="numero" [libelle]="libelleNumero()" [erreur]="erreurNumero()" />

          <fg-switch [formControl]="auto" i18n-libelle="@@abonnement.auto" libelle="Renouvellement automatique">
            <strong class="text-body font-semibold" i18n="@@abonnement.auto">Renouvellement automatique</strong>
            <span class="text-label text-text-2" i18n="@@paiement.auto.texte">Chaque mois, la demande arrive sur ce numéro. Vous la validez à chaque fois.</span>
          </fg-switch>

          @if (erreur(); as message) {
            <fg-banner ton="erreur">{{ message }}</fg-banner>
          }
          <div class="mt-auto flex flex-col gap-2">
            <button fg-button taille="lg" type="button" [chargement]="envoi()" (click)="payer()" i18n="@@abonnement.payer">Payer {{ montant(o.prixFcfa) }} FCFA</button>
            <p class="m-0 text-center text-caption text-text-3" i18n="@@paiement.frais">Aucun frais FasoGuardian. Frais opérateur éventuels à votre charge.</p>
          </div>
        } @else if (erreur(); as message) {
          <fg-banner ton="erreur">{{ message }}</fg-banner>
        } @else {
          <fg-skeleton forme="carte" />
          <fg-skeleton forme="carte" />
        }
      }
      @case ('attente') {
        <div class="my-auto flex flex-col items-center gap-4 text-center" role="status">
          <span class="grid size-18 place-items-center rounded-full bg-accent-soft text-accent motion-safe:animate-pulse" aria-hidden="true"><fg-icon nom="telephone" [taille]="32" /></span>
          <h1 class="m-0 text-h2 font-bold tracking-tight" i18n="@@paiement.attente.titre">Validez sur votre téléphone</h1>
          <p class="m-0 text-body text-text-2" i18n="@@paiement.attente.texte">{{ operateur() }} vous envoie une demande. Saisissez votre code secret {{ operateur() }} pour confirmer {{ montant(paiementSuivi()?.montantFcfa ?? 0) }} FCFA.</p>
          <span class="text-label font-semibold text-accent tabular-nums" i18n="@@paiement.attente.expire">Expire dans {{ decompte() }}</span>
        </div>
      }
      @case ('recu') {
        <div class="my-auto flex flex-col items-center gap-4 text-center" role="status">
          <span class="grid size-18 place-items-center rounded-full bg-success-soft text-success" aria-hidden="true"><fg-icon nom="valider" [taille]="32" /></span>
          <h1 class="m-0 text-h2 font-bold tracking-tight" i18n="@@paiement.recu.titre">Paiement reçu</h1>
          <p class="m-0 text-body text-text-2">{{ confirmation() }}</p>
        </div>
        <div class="flex flex-col gap-2">
          <a class="grid min-h-13 place-items-center rounded-md bg-primary text-body font-semibold text-white hover:bg-primary-hover focus-visible:outline-2 focus-visible:outline-accent" routerLink="/abonnement/recus" i18n="@@paiement.recu.voir">Voir le reçu</a>
          <a class="grid min-h-12 place-items-center text-body font-semibold text-accent focus-visible:outline-2 focus-visible:outline-accent" routerLink="/abonnement" [queryParams]="{ enfant: enfant() }" i18n="@@paiement.terminer">Terminer</a>
        </div>
      }
      @case ('echec') {
        <div class="my-auto flex flex-col items-center gap-4 text-center" role="alert">
          <span class="grid size-18 place-items-center rounded-full bg-surface-2 text-danger" aria-hidden="true"><fg-icon nom="fermer" [taille]="32" /></span>
          <h1 class="m-0 text-h2 font-bold tracking-tight" i18n="@@paiement.echec.titre">Paiement non abouti</h1>
          <p class="m-0 text-body text-text-2">
            @if (paiementSuivi()?.motifEchec; as motif) {
              {{ motif }}.
            }
            <ng-container i18n="@@paiement.echec.texte">Aucun débit n'a été effectué.</ng-container>
          </p>
        </div>
        <div class="flex flex-col gap-2">
          <button fg-button taille="lg" type="button" (click)="reessayer()" i18n="@@paiement.reessayer">Réessayer</button>
          <a class="grid min-h-12 place-items-center text-body font-semibold text-accent focus-visible:outline-2 focus-visible:outline-accent" routerLink="/abonnement" [queryParams]="{ enfant: enfant() }" i18n="@@paiement.plusTard">Plus tard</a>
        </div>
      }
      @case ('delai') {
        <div class="my-auto flex flex-col items-center gap-4 text-center" role="alert">
          <span class="grid size-18 place-items-center rounded-full bg-surface-2 text-text-2" aria-hidden="true"><fg-icon nom="horloge" [taille]="32" /></span>
          <h1 class="m-0 text-h2 font-bold tracking-tight" i18n="@@paiement.delai.titre">Pas de confirmation reçue</h1>
          <p class="m-0 text-body text-text-2" i18n="@@paiement.delai.texte">Sans validation de votre part, rien n'est débité. Si vous venez de valider, l'abonnement s'activera dès la confirmation de l'opérateur et vous serez prévenu par SMS.</p>
        </div>
        <div class="flex flex-col gap-2">
          <button fg-button taille="lg" type="button" (click)="reessayer()" i18n="@@paiement.reessayer">Réessayer</button>
          <a class="grid min-h-12 place-items-center text-body font-semibold text-accent focus-visible:outline-2 focus-visible:outline-accent" routerLink="/abonnement" [queryParams]="{ enfant: enfant() }" i18n="@@paiement.terminer">Terminer</a>
        </div>
      }
    }
  `,
  host: { class: 'mx-auto flex min-h-dvh w-full max-w-md flex-col gap-4 px-5 pt-6 pb-7 lg:mx-0 lg:max-w-2xl lg:px-10 lg:pt-10' },
})
export class EcranPaiement {
  readonly enfant = input<string>();
  readonly offre = input<CodeOffre>();
  /** Paiement déjà demandé, à suivre (reprise depuis « Mon abonnement »). */
  readonly paiement = input<string>();

  private readonly client = inject(ClientAbonnements);

  protected readonly montant = montant;
  protected readonly moyens: readonly { code: MoyenPaiement; nom: string; aide: string }[] = [
    { code: 'ORANGE_MONEY', nom: 'Orange Money', aide: $localize`:@@paiement.moyen.orange:Demande de validation sur votre téléphone` },
    { code: 'MOOV_MONEY', nom: 'Moov Money', aide: $localize`:@@paiement.moyen.moov:Demande de validation sur votre téléphone` },
  ];

  protected readonly etape = signal<Etape>('saisie');
  protected readonly offres = signal<readonly Offre[]>([]);
  protected readonly paiementSuivi = signal<Paiement | null>(null);
  protected readonly confirmation = signal('');
  protected readonly erreur = signal<string | null>(null);
  protected readonly erreurNumero = signal<string | null>(null);
  protected readonly envoi = signal(false);
  protected readonly restant = signal(0);

  protected readonly moyen = new FormControl<MoyenPaiement>('ORANGE_MONEY', { nonNullable: true });
  protected readonly numero = new FormControl('', { nonNullable: true, validators: [Validators.pattern(/^\d{8}$/)] });
  protected readonly auto = new FormControl(true, { nonNullable: true });

  protected readonly choisie = computed(() => this.offres().find((o) => o.code === this.offre()) ?? null);
  protected readonly moyenChoisi = signal<MoyenPaiement>('ORANGE_MONEY');
  protected readonly operateur = computed(() => nomMoyen(this.paiementSuivi()?.moyen ?? this.moyenChoisi()));
  protected readonly libelleNumero = computed(() => $localize`:@@paiement.numero:Numéro ${nomMoyen(this.moyenChoisi())}:moyen:`);
  protected readonly decompte = computed(() => {
    const secondes = this.restant();
    return `${Math.floor(secondes / 60)}:${String(secondes % 60).padStart(2, '0')}`;
  });

  /** Une clé par tentative : la même demande rejouée ne sollicite pas le portefeuille deux fois. */
  private cle = crypto.randomUUID();
  private minuterie: ReturnType<typeof setInterval> | null = null;
  private depuisInterrogation = 0;

  constructor() {
    this.moyen.valueChanges.subscribe((moyen) => this.moyenChoisi.set(moyen));
    this.client.offres().subscribe({
      next: (offres) => {
        this.offres.set(offres);
        if (!this.paiement() && !offres.some((o) => o.code === this.offre())) {
          this.erreur.set($localize`:@@paiement.offreInconnue:Cette offre n'est pas proposée. Revenez à votre abonnement pour en choisir une.`);
        }
      },
      error: (cause: unknown) => this.erreur.set(erreurLisible(cause).message),
    });
    effect(() => {
      const id = this.paiement();
      if (id) {
        this.client.paiement(id).subscribe({
          next: (paiement) => this.suivre(paiement),
          error: (cause: unknown) => this.erreur.set(erreurLisible(cause).message),
        });
      }
    });
    inject(DestroyRef).onDestroy(() => this.arreter());
  }

  protected payer(): void {
    const enfant = this.enfant();
    const offre = this.choisie();
    if (!enfant || !offre || this.envoi()) {
      return;
    }
    if (this.numero.value.length !== 8 || this.numero.invalid) {
      this.erreurNumero.set($localize`:@@paiement.numero.invalide:Saisissez les 8 chiffres du numéro.`);
      return;
    }
    this.erreurNumero.set(null);
    this.erreur.set(null);
    this.envoi.set(true);
    this.client.payer(enfant, { offre: offre.code, moyen: this.moyen.value, numero: this.numero.value, renouvellementAuto: this.auto.value }, this.cle).subscribe({
      next: (paiement) => {
        this.envoi.set(false);
        this.suivre(paiement);
      },
      error: (cause: unknown) => {
        this.envoi.set(false);
        const lisible = erreurLisible(cause);
        if (lisible.code === 'TELEPHONE_INVALIDE') {
          this.erreurNumero.set(lisible.message);
        } else {
          this.erreur.set(lisible.message);
        }
      },
    });
  }

  protected reessayer(): void {
    this.arreter();
    this.cle = crypto.randomUUID();
    this.paiementSuivi.set(null);
    this.erreur.set(null);
    this.etape.set('saisie');
  }

  /** Place l'écran selon l'état du paiement ; tant qu'il attend, lance le décompte et l'interrogation. */
  private suivre(paiement: Paiement): void {
    this.paiementSuivi.set(paiement);
    switch (paiement.statut) {
      case 'CONFIRME':
        this.arreter();
        this.confirmer(paiement);
        break;
      case 'ECHOUE':
        this.arreter();
        this.etape.set('echec');
        break;
      case 'EXPIRE':
        this.arreter();
        this.etape.set('delai');
        break;
      case 'INITIE':
        this.restant.set(this.secondesAvant(paiement.expireLe));
        if (this.restant() === 0) {
          this.arreter();
          this.etape.set('delai');
        } else {
          this.etape.set('attente');
          this.minuterie ??= setInterval(() => this.battre(), 1000);
        }
        break;
    }
  }

  private battre(): void {
    const paiement = this.paiementSuivi();
    if (!paiement) {
      return;
    }
    this.restant.set(this.secondesAvant(paiement.expireLe));
    this.depuisInterrogation += 1000;
    if (this.depuisInterrogation >= INTERROGATION_MS || this.restant() === 0) {
      this.depuisInterrogation = 0;
      this.client.paiement(paiement.id).subscribe({
        next: (aJour) => this.suivre(aJour),
        // Réseau coupé pendant l'attente : la prochaine interrogation réessaiera, le décompte continue.
        error: () => undefined,
      });
    }
  }

  private confirmer(paiement: Paiement): void {
    const enfant = this.enfant();
    const recu = paiement.recu ?? '';
    this.confirmation.set($localize`:@@paiement.recu.court:Reçu ${recu}:recu: envoyé par SMS.`);
    this.etape.set('recu');
    if (enfant) {
      this.client.abonnement(enfant).subscribe({
        next: (a) => {
          if (a.libelle && a.prochaineEcheance) {
            this.confirmation.set(
              $localize`:@@paiement.recu.texte:Abonnement ${a.libelle}:offre: actif jusqu'au ${jourLong(a.prochaineEcheance)}:jour:. Reçu ${recu}:recu: envoyé par SMS.`,
            );
          }
        },
        error: () => undefined,
      });
    }
  }

  private secondesAvant(iso: string): number {
    return Math.max(0, Math.ceil((new Date(iso).getTime() - Date.now()) / 1000));
  }

  private arreter(): void {
    if (this.minuterie !== null) {
      clearInterval(this.minuterie);
      this.minuterie = null;
    }
    this.depuisInterrogation = 0;
  }
}
