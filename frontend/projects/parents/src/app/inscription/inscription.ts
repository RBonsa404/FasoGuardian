import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';

import { ClientAuthentification, TypeConsentement } from 'api';
import { FgBanniere, FgBouton, FgChamp, FgCode, FgInterrupteur, FgTelephone } from 'ui';

import { erreurLisible } from '../commun/erreurs';
import { Etape } from '../gabarit/etape';

type NomEtape = 'numero' | 'code' | 'mot-de-passe' | 'accords';

const ORDRE: readonly NomEtape[] = ['numero', 'code', 'mot-de-passe', 'accords'];
const DELAI_RENVOI_S = 60;

/**
 * Inscription d'un parent (écran 10, US-PAR-001) : numéro, code SMS, mot de passe, accords.
 * Une question par écran ; la validation du serveur fait foi, celle du client évite les allers-retours.
 */
@Component({
  selector: 'app-inscription',
  imports: [NgTemplateOutlet, ReactiveFormsModule, RouterLink, Etape, FgBouton, FgBanniere, FgChamp, FgCode, FgTelephone, FgInterrupteur],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './inscription.html',
})
export class Inscription {
  /** Segment de route : /inscription/:etape. */
  readonly etape = input.required<NomEtape>();

  private readonly client = inject(ClientAuthentification);
  private readonly router = inject(Router);

  protected readonly telephone = new FormControl('', { nonNullable: true });
  protected readonly code = new FormControl('', { nonNullable: true });
  protected readonly motDePasse = new FormControl('', { nonNullable: true });
  protected readonly partageFds = new FormControl(true, { nonNullable: true });
  protected readonly conseilsSms = new FormControl(false, { nonNullable: true });
  protected readonly obligatoire = new FormControl(true, { nonNullable: true });

  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly erreurChamp = signal<string | null>(null);
  protected readonly attenteRenvoi = signal(0);
  private readonly preuve = signal<string | null>(null);
  private readonly saisieMotDePasse = signal('');

  protected readonly numero = computed(() => ORDRE.indexOf(this.etape()) + 1);
  protected readonly aideRobuste = $localize`:@@inscription.mdp.robuste:Robuste ✓`;
  protected get texteCode(): string {
    const numero = this.telephone.value.replace(/(\d{2})(?=\d)/g, '$1 ');
    return $localize`:@@inscription.code.texte:Envoyé au +226 ${numero}:numero:.`;
  }
  protected readonly motDePasseRobuste = computed(
    () => this.saisieMotDePasse().length >= 10 && /\d/.test(this.saisieMotDePasse()),
  );
  protected readonly renvoi = computed(() => {
    const secondes = this.attenteRenvoi();
    return `${Math.floor(secondes / 60)}:${String(secondes % 60).padStart(2, '0')}`;
  });

  constructor() {
    this.motDePasse.valueChanges.subscribe((valeur) => this.saisieMotDePasse.set(valeur));
    // Une étape ne s'ouvre que si les précédentes ont abouti (rechargement ou lien direct).
    effect(() => {
      const etape = this.etape();
      const manque =
        !ORDRE.includes(etape) ||
        (etape !== 'numero' && this.telephone.value.length !== 8) ||
        ((etape === 'mot-de-passe' || etape === 'accords') && !this.preuve());
      if (manque) {
        void this.router.navigate(['/inscription', 'numero'], { replaceUrl: true });
      }
    });
    const minuterie = setInterval(() => this.attenteRenvoi.update((s) => Math.max(0, s - 1)), 1000);
    inject(DestroyRef).onDestroy(() => clearInterval(minuterie));
  }

  protected reculer(): void {
    const index = ORDRE.indexOf(this.etape());
    void this.router.navigate(index <= 0 ? ['/bienvenue'] : ['/inscription', ORDRE[index - 1]]);
  }

  protected envoyerNumero(): void {
    if (this.telephone.value.length !== 8) {
      this.erreurChamp.set($localize`:@@inscription.numero.invalide:Saisissez 8 chiffres.`);
      return;
    }
    this.appeler(this.client.demanderCode(this.telephone.value), () => {
      this.attenteRenvoi.set(DELAI_RENVOI_S);
      this.code.setValue('');
      this.aller('code');
    });
  }

  protected renvoyerCode(): void {
    this.appeler(this.client.demanderCode(this.telephone.value), () => this.attenteRenvoi.set(DELAI_RENVOI_S));
  }

  protected verifierCode(): void {
    if (this.code.value.length !== 6) {
      this.erreurChamp.set($localize`:@@inscription.code.incomplet:Saisissez les 6 chiffres du code.`);
      return;
    }
    this.appeler(this.client.verifierCode(this.telephone.value, this.code.value), (preuve) => {
      this.preuve.set(preuve);
      this.aller('mot-de-passe');
    });
  }

  protected validerMotDePasse(): void {
    if (!this.motDePasseRobuste()) {
      this.erreurChamp.set($localize`:@@inscription.mdp.faible:10 caractères minimum, avec un chiffre.`);
      return;
    }
    this.aller('accords');
  }

  protected terminer(): void {
    const consentements: TypeConsentement[] = ['CONDITIONS_GENERALES', 'DONNEES_ENFANT'];
    if (this.partageFds.value) {
      consentements.push('PARTAGE_FORCES_SECURITE');
    }
    if (this.conseilsSms.value) {
      consentements.push('COMMUNICATION_SMS');
    }
    this.appeler(this.client.terminerInscription(this.preuve()!, this.motDePasse.value, consentements), () => {
      this.motDePasse.setValue('');
      void this.router.navigate(['/']);
    });
  }

  private aller(etape: NomEtape): void {
    this.erreur.set(null);
    this.erreurChamp.set(null);
    void this.router.navigate(['/inscription', etape]);
  }

  private appeler<T>(appel: Observable<T>, suite: (valeur: T) => void): void {
    if (this.enCours()) {
      return;
    }
    this.enCours.set(true);
    this.erreur.set(null);
    this.erreurChamp.set(null);
    appel.subscribe({
      next: (valeur) => {
        this.enCours.set(false);
        suite(valeur);
      },
      error: (cause: unknown) => {
        this.enCours.set(false);
        const lisible = erreurLisible(cause);
        // Les erreurs de saisie s'affichent sous le champ ; les autres en bannière.
        if (['TELEPHONE_INVALIDE', 'CODE_INCORRECT', 'CODE_EXPIRE', 'CODE_EPUISE', 'MOT_DE_PASSE_REFUSE'].includes(lisible.code)) {
          this.erreurChamp.set(lisible.message);
          if (lisible.code === 'MOT_DE_PASSE_REFUSE') {
            void this.router.navigate(['/inscription', 'mot-de-passe']);
          }
        } else {
          this.erreur.set(lisible.message);
        }
      },
    });
  }
}
