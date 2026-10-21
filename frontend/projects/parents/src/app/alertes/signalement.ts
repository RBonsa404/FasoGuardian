import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ActionAlerte, Alerte, ApercuSignalement, ClientAlertes, ClientFamille, Signalement } from 'api';
import { FgBadge, FgBanniere, FgBouton, FgIcon, FgSquelette } from 'ui';

import { erreurLisible } from '../commun/erreurs';
import { DePipe } from '../commun/francais';
import { SecondFacteur } from '../commun/second-facteur';
import { heure, ilYA } from '../commun/temps';
import { age } from '../enfants/enfants';
import { actionLisible } from './libelles';

interface EtapeSuivi {
  readonly titre: string;
  readonly detail: string | null;
  readonly le: string;
  /** Accusé de réception des forces de sécurité : dernière étape, en vert. */
  readonly accuse: boolean;
}

/** Titre d'une étape du journal, sur la frise du suivi (écran 29b). */
function etapeDe(action: ActionAlerte): EtapeSuivi {
  const qui = action.auteur === 'VOUS' ? $localize`:@@suivi.qui.vous:Par vous` : action.auteur === 'AUTRE_TUTEUR' ? $localize`:@@suivi.qui.autre:Par un autre tuteur` : null;
  const titres: Partial<Record<ActionAlerte['type'], [string, string | null]>> = {
    OUVERTURE: [$localize`:@@suivi.ouverture:Alerte déclenchée`, null],
    ACQUITTEMENT: [$localize`:@@suivi.acquittement:Alerte prise en charge`, qui],
    CONTACT_SOLLICITE: [$localize`:@@suivi.contact:Contacts d'urgence sollicités`, action.motif],
    INSTITUTION_SOLLICITEE: [$localize`:@@suivi.institution:Point de contact institutionnel sollicité`, action.motif],
    ESCALADE: [$localize`:@@suivi.escalade:Signalement confirmé`, $localize`:@@suivi.escalade.detail:Confirmé par code SMS`],
  };
  const [titre, detail] = titres[action.type] ?? [actionLisible(action), null];
  return { titre, detail, le: action.effectueeLe, accuse: false };
}

/**
 * Signalement d'une disparition (écran 29, US-PAR-010). Le parent voit ce que le dossier contiendra, confirme
 * par code SMS, puis télécharge le PDF à remettre aux autorités : aucun commissariat n'est encore relié à
 * FasoGuardian, la transmission directe viendra avec la convention.
 */
@Component({
  selector: 'app-signalement',
  imports: [DatePipe, DePipe, RouterLink, FgBadge, FgBanniere, FgBouton, FgIcon, FgSquelette, SecondFacteur],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './signalement.html',
  host: { class: 'mx-auto flex min-h-dvh w-full max-w-md flex-col gap-4 px-5 pt-6 pb-7 lg:max-w-3xl lg:gap-5 lg:px-10 lg:pt-10' },
})
export class EcranSignalement {
  readonly aid = input.required<string>();

  private readonly client = inject(ClientAlertes);

  protected readonly heure = heure;
  protected readonly ilYA = ilYA;

  protected readonly alerte = signal<Alerte | null>(null);
  /** Photo de l'enfant jointe au dossier (ADR 0033), chargée par l'API authentifiée. */
  protected readonly photo = signal<string | null>(null);
  /** Étapes de l'alerte, du déclenchement à l'accusé de réception des forces de sécurité. */
  protected readonly etapesSuivi = computed<EtapeSuivi[]>(() => {
    const etapes = [...(this.alerte()?.actions ?? [])].sort((a, b) => a.effectueeLe.localeCompare(b.effectueeLe)).map(etapeDe);
    const accuse = this.signalement()?.accuseLe;
    return accuse
      ? [...etapes, { titre: $localize`:@@suivi.accuse:Accusé de réception des forces de sécurité`, detail: null, le: accuse, accuse: true }]
      : etapes;
  });
  private readonly famille = inject(ClientFamille);
  protected readonly apercu = signal<ApercuSignalement | null>(null);
  protected readonly signalement = signal<Signalement | null>(null);
  /** L'alerte n'est plus en attente d'escalade et n'a pas de dossier. */
  protected readonly sansDossier = signal(false);
  protected readonly confirmation = signal(false);
  protected readonly enCours = signal(false);
  protected readonly telechargement = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly erreurCode = signal<string | null>(null);

  protected readonly identite = computed(() => {
    const enfant = this.apercu()?.enfant;
    return enfant ? $localize`:@@signalement.identite:${enfant.prenom}:prenom: ${enfant.nom}:nom: · ${age(enfant.dateNaissance)}:age: ans` : '';
  });
  protected readonly signes = computed(() => {
    const enfant = this.apercu()?.enfant;
    if (!enfant) {
      return '';
    }
    const taille = enfant.tailleCm ? `${(enfant.tailleCm / 100).toFixed(2).replace('.', ',')} m` : null;
    return [taille, enfant.signesDistinctifs].filter(Boolean).join(' · ');
  });

  constructor() {
    effect(() => this.charger(this.aid()));
    inject(DestroyRef).onDestroy(() => this.liberer());
  }

  protected confirmer(code: string): void {
    if (this.enCours()) {
      return;
    }
    this.enCours.set(true);
    this.erreurCode.set(null);
    this.client.escalader(this.aid(), code).subscribe({
      next: (signalement) => {
        this.enCours.set(false);
        this.confirmation.set(false);
        this.signalement.set(signalement);
        // Le journal gagne l'étape du signalement : la frise du suivi la montre aussitôt.
        this.client.alerte(this.aid()).subscribe({ next: (alerte) => this.alerte.set(alerte), error: () => undefined });
      },
      error: (cause: unknown) => {
        this.enCours.set(false);
        const lisible = erreurLisible(cause);
        if (lisible.code.startsWith('CODE_')) {
          this.erreurCode.set(lisible.message);
        } else {
          this.confirmation.set(false);
          this.erreur.set(lisible.message);
        }
      },
    });
  }

  /** Télécharge le PDF avec le jeton de session, puis le remet au navigateur comme un fichier. */
  protected telecharger(): void {
    const signalement = this.signalement();
    if (!signalement || this.telechargement()) {
      return;
    }
    this.telechargement.set(true);
    this.erreur.set(null);
    this.client.dossierSignalement(this.aid()).subscribe({
      next: (pdf) => {
        this.telechargement.set(false);
        const adresse = URL.createObjectURL(pdf);
        const lien = document.createElement('a');
        lien.href = adresse;
        lien.download = `${signalement.reference}.pdf`;
        lien.click();
        URL.revokeObjectURL(adresse);
      },
      error: (cause: unknown) => {
        this.telechargement.set(false);
        this.erreur.set(erreurLisible(cause).message);
      },
    });
  }

  private liberer(): void {
    const url = this.photo();
    if (url) {
      URL.revokeObjectURL(url);
    }
  }

  private charger(id: string): void {
    this.erreur.set(null);
    this.client.alerte(id).subscribe({
      next: (alerte) => {
        this.alerte.set(alerte);
        if (alerte.statut === 'ACQUITTEE') {
          this.client.apercuSignalement(id).subscribe({
            next: (apercu) => {
              this.apercu.set(apercu);
              this.famille.photo(alerte.enfantId).subscribe({
                next: (image) => {
                  this.liberer();
                  this.photo.set(URL.createObjectURL(image));
                },
                // Pas de photo : la vignette reste un pictogramme.
                error: () => undefined,
              });
            },
            error: (cause: unknown) => this.erreur.set(erreurLisible(cause).message),
          });
        } else {
          // Déjà escaladée, ou close depuis : le dossier existe peut-être.
          this.client.signalement(id).subscribe({ next: (signalement) => this.signalement.set(signalement), error: () => this.sansDossier.set(true) });
        }
      },
      error: (cause: unknown) => this.erreur.set(erreurLisible(cause).message),
    });
  }
}
