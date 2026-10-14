import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { ClientFamille, ContactUrgence, TuteurEnfant, TuteursEnfant } from 'api';
import { FgBadge, FgBanniere, FgBouton, FgChamp, FgFeuille, FgIcon, FgInterrupteur, FgSquelette, FgTelephone } from 'ui';

import { SecondFacteur } from '../commun/second-facteur';
import { erreurLisible } from '../commun/erreurs';

const MAXIMUM = 5;
const CODES_A_RESAISIR = new Set(['CODE_INCORRECT', 'CODE_EXPIRE', 'CODE_EPUISE', 'SECOND_FACTEUR_REQUIS']);
const JOUR = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'long' });

/** « +22670123456 » → « +226 70 12 34 56 ». */
export function telephoneLisible(e164: string | null): string {
  const chiffres = e164?.replace('+226', '') ?? '';
  return chiffres.length === 8 ? `+226 ${chiffres.match(/\d{2}/g)!.join(' ')}` : (e164 ?? '');
}

/**
 * Tuteurs et contacts (écran 34, US-PAR-003). Un second tuteur s'invite par son numéro, après confirmation
 * par code SMS ; il passe sa propre vérification KYC avant d'accéder à l'enfant. Les contacts d'urgence sont
 * prévenus par SMS en cascade ; un contact marqué « visible sur la page QR » peut être appelé par la personne
 * qui trouve l'enfant, qui ne voit que son lien avec lui.
 */
@Component({
  selector: 'app-contacts',
  imports: [ReactiveFormsModule, RouterLink, FgBadge, FgBanniere, FgBouton, FgChamp, FgFeuille, FgIcon, FgInterrupteur, FgSquelette, FgTelephone, SecondFacteur],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="-ml-2.5 grid size-11 place-items-center rounded-md focus-visible:outline-2 focus-visible:outline-accent" [routerLink]="['/enfants', id()]" i18n-aria-label="@@etape.retour" aria-label="Retour">
      <fg-icon nom="retour" [taille]="22" />
    </a>
    <h1 class="m-0 text-titre-ecran font-bold tracking-tight" i18n="@@contacts.titre">Tuteurs et contacts</h1>

    @if (famille(); as f) {
      <ul class="m-0 flex list-none flex-col gap-2.5 p-0" i18n-aria-label="@@tuteurs.liste" aria-label="Tuteurs">
        @for (tuteur of f.tuteurs; track $index) {
          <li class="flex flex-col gap-3 rounded-lg border border-line bg-surface p-4">
            <div class="flex items-center gap-3">
              <span class="grid size-11 flex-none place-items-center rounded-full bg-surface-2 font-display text-body font-bold" aria-hidden="true">{{ initiale(tuteur.prenom) }}</span>
              <span class="flex min-w-0 flex-1 flex-col gap-0.5">
                <strong class="text-body font-semibold">{{ nomTuteur(tuteur) }}</strong>
                <span class="text-label whitespace-nowrap text-text-2 tabular-nums">{{ lisible(tuteur.telephone) }}</span>
              </span>
              @if (tuteur.principal) {
                <fg-badge ton="accent" i18n="@@tuteurs.principal">Tuteur principal</fg-badge>
              } @else {
                <fg-badge ton="succes" i18n="@@tuteurs.second">Second tuteur</fg-badge>
              }
            </div>
            @if (tuteur.suspendu) {
              <fg-banner ton="attention" i18n="@@tuteurs.suspendu">Accès suspendu pendant l'instruction d'un signalement.</fg-banner>
            } @else {
              <ul class="m-0 flex list-none flex-wrap gap-1.5 p-0" i18n-aria-label="@@tuteurs.droits" aria-label="Droits">
                @for (droit of droitsTuteur; track droit) {
                  <li class="rounded-xs bg-surface-2 px-2 py-1 text-caption font-medium text-text-2">{{ droit }}</li>
                }
              </ul>
            }
          </li>
        }
        @if (f.invitation; as invitation) {
          <li class="flex flex-col gap-3 rounded-lg border border-dashed border-line-strong bg-surface p-4">
            <div class="flex items-center gap-3">
              <span class="grid size-11 flex-none place-items-center rounded-full bg-surface-2 text-text-2" aria-hidden="true"><fg-icon nom="telephone" [taille]="20" /></span>
              <span class="flex min-w-0 flex-1 flex-col gap-0.5">
                <strong class="text-body font-semibold tabular-nums">{{ lisible(invitation.telephone) }}</strong>
                <span class="text-label text-text-2">
                  @if (invitation.statut === 'EN_VERIFICATION') {
                    <ng-container i18n="@@tuteurs.invitation.verification">Vérification d'identité en cours</ng-container>
                  } @else {
                    <ng-container i18n="@@tuteurs.invitation.expire">Invitation valable jusqu'au {{ jour(invitation.expireLe) }}</ng-container>
                  }
                </span>
              </span>
              <fg-badge ton="attention" i18n="@@tuteurs.invite">Invité</fg-badge>
            </div>
            <button fg-button variante="ghost" type="button" [chargement]="annulation()" (click)="annulerInvitation()" i18n="@@tuteurs.annuler">Annuler l'invitation</button>
          </li>
        }
      </ul>

      <section class="flex flex-col gap-2.5" aria-labelledby="titre-contacts">
        <h2 id="titre-contacts" class="m-0 text-h3 font-semibold" i18n="@@contacts.urgence">Contacts d'urgence</h2>
        <p class="m-0 text-label text-text-2" i18n="@@contacts.texte">Cinq contacts au plus, prévenus par SMS si une alerte reste sans réponse. Sur la page QR, seuls le lien avec l'enfant et un bouton d'appel sont affichés.</p>
        @for (contact of f.contacts; track contact.id) {
          <button type="button" class="flex flex-col gap-3 rounded-lg border border-line bg-surface p-4 text-left focus-visible:outline-2 focus-visible:outline-accent" (click)="ouvrir(contact)">
            <span class="flex w-full items-center gap-3">
              <span class="grid size-11 flex-none place-items-center rounded-full bg-surface-2 font-display text-body font-bold" aria-hidden="true">{{ initiale(contact.nom) }}</span>
              <span class="flex min-w-0 flex-1 flex-col gap-0.5">
                <strong class="text-body font-semibold">{{ contact.nom }}</strong>
                <span class="text-label text-text-2 tabular-nums">{{ contact.lien }} · <span class="whitespace-nowrap">{{ lisible(contact.telephone) }}</span></span>
              </span>
              <fg-badge ton="neutre" i18n="@@contacts.badge">Contact secondaire</fg-badge>
            </span>
            <span class="flex flex-wrap gap-1.5">
              <span class="rounded-xs bg-surface-2 px-2 py-1 text-caption font-medium text-text-2" i18n="@@contacts.cascade">SMS en cascade</span>
              @if (contact.visibleSurQr) {
                <span class="rounded-xs bg-surface-2 px-2 py-1 text-caption font-medium text-accent" i18n="@@contacts.visible">Page QR</span>
              }
            </span>
          </button>
        } @empty {
          <p class="m-0 rounded-lg border border-line bg-surface p-5 text-body text-text-2" i18n="@@contacts.vide">Aucun contact pour l'instant. Ajoutez une personne de confiance à prévenir.</p>
        }
        <button fg-button variante="secondary" type="button" [disabled]="f.contacts.length >= maximum" (click)="ouvrir(null)" i18n="@@contacts.ajouter">Ajouter un contact</button>
      </section>

      @if (erreur(); as message) {
        <fg-banner ton="erreur">{{ message }}</fg-banner>
      }
      @if (invitationEnvoyee()) {
        <fg-banner ton="info" icone="valider" i18n="@@tuteurs.envoyee">Invitation envoyée par SMS. Le tuteur invité passe sa propre vérification KYC.</fg-banner>
      }
      @if (f.peutInviter) {
        <div class="mt-auto flex flex-col gap-2">
          <button fg-button taille="lg" type="button" (click)="ouvrirInvitation()" i18n="@@tuteurs.inviter">Inviter un second tuteur</button>
          <p class="m-0 text-center text-label text-text-2" i18n="@@tuteurs.kyc">Le tuteur invité passe sa propre vérification KYC.</p>
        </div>
      }
    } @else if (erreur(); as message) {
      <fg-banner ton="erreur">{{ message }}</fg-banner>
    } @else {
      <fg-skeleton forme="carte" />
      <fg-skeleton forme="carte" />
    }

    <fg-sheet [titre]="titreFeuille()" [ouverte]="feuille()" (fermee)="feuille.set(false)">
      <form class="contents" (submit)="$event.preventDefault(); enregistrer()">
        <fg-input i18n-libelle="@@contacts.nom" libelle="Nom" [longueurMax]="80" [formControl]="nom" />
        <fg-input i18n-libelle="@@contacts.lien" libelle="Lien avec l'enfant" i18n-aide="@@contacts.lien.aide" aide="Par exemple : tante, voisin, grand-père." [longueurMax]="40" [formControl]="lien" />
        <fg-phone-input i18n-libelle="@@contacts.telephone" libelle="Numéro mobile" [formControl]="telephone" />
        <fg-switch [formControl]="visible" i18n-libelle="@@contacts.visibleLibelle" libelle="Joignable depuis la page QR">
          <strong class="text-body font-semibold" i18n="@@contacts.visibleLibelle">Joignable depuis la page QR</strong>
          <span class="text-label text-text-2" i18n="@@contacts.visibleTexte">La personne qui trouve l'enfant pourra l'appeler.</span>
        </fg-switch>
        @if (erreurFeuille(); as message) {
          <fg-banner ton="erreur">{{ message }}</fg-banner>
        }
        <button fg-button type="submit" [chargement]="enCours()" i18n="@@commun.enregistrer">Enregistrer</button>
        @if (edite()) {
          <button fg-button variante="danger" type="button" (click)="supprimer()" i18n="@@contacts.supprimer">Supprimer ce contact</button>
        }
      </form>
    </fg-sheet>

    <fg-sheet i18n-titre="@@tuteurs.inviter" titre="Inviter un second tuteur" [ouverte]="invitation()" (fermee)="invitation.set(false)">
      <form class="contents" (submit)="$event.preventDefault(); confirmerNumero()">
        <p class="m-0 text-body text-text-2" i18n="@@tuteurs.invitation.texte">Un autre adulte responsable de l'enfant reçoit une invitation par SMS. Il ouvre l'application avec ce numéro et passe sa propre vérification d'identité ; il n'accède à l'enfant qu'une fois vérifié.</p>
        <fg-phone-input i18n-libelle="@@tuteurs.numero" libelle="Numéro mobile du second tuteur" [formControl]="numeroInvite" />
        @if (erreurInvitation(); as message) {
          <fg-banner ton="erreur">{{ message }}</fg-banner>
        }
        <button fg-button type="submit" [chargement]="enCours()" i18n="@@tuteurs.continuer">Continuer</button>
      </form>
    </fg-sheet>

    <app-second-facteur
      action="AJOUTER_TUTEUR"
      [ouverte]="confirmation()"
      [explication]="explication()"
      [erreur]="erreurCode()"
      (saisi)="inviter($event)"
      (annule)="confirmation.set(false)"
    />
  `,
  host: { class: 'mx-auto flex min-h-dvh w-full max-w-md flex-col gap-4 px-5 pt-6 pb-7 lg:mx-0 lg:max-w-2xl lg:px-10 lg:pt-10' },
})
export class Contacts {
  readonly id = input.required<string>();

  private readonly client = inject(ClientFamille);

  protected readonly maximum = MAXIMUM;
  /** Droits d'un tuteur sur l'enfant : les mêmes pour chacun (FG-DOC-06, tableau 17). */
  protected readonly droitsTuteur = [
    $localize`:@@tuteurs.droit.position:Position`,
    $localize`:@@tuteurs.droit.alertes:Alertes`,
    $localize`:@@tuteurs.droit.retrait:Retrait`,
    $localize`:@@tuteurs.droit.escalade:Escalade`,
  ];
  protected readonly famille = signal<(TuteursEnfant & { contacts: ContactUrgence[] }) | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly erreurFeuille = signal<string | null>(null);
  protected readonly feuille = signal(false);
  protected readonly edite = signal<ContactUrgence | null>(null);
  protected readonly enCours = signal(false);
  protected readonly titreFeuille = signal('');

  protected readonly invitation = signal(false);
  protected readonly confirmation = signal(false);
  protected readonly erreurInvitation = signal<string | null>(null);
  protected readonly erreurCode = signal<string | null>(null);
  protected readonly invitationEnvoyee = signal(false);
  protected readonly annulation = signal(false);
  protected readonly explication = signal('');

  protected readonly nom = new FormControl('', { nonNullable: true });
  protected readonly lien = new FormControl('', { nonNullable: true });
  protected readonly telephone = new FormControl('', { nonNullable: true });
  protected readonly visible = new FormControl(false, { nonNullable: true });
  protected readonly numeroInvite = new FormControl('', { nonNullable: true });

  constructor() {
    effect(() => this.charger(this.id()));
  }

  protected lisible(telephone: string | null): string {
    return telephoneLisible(telephone);
  }

  protected jour(iso: string): string {
    return JOUR.format(new Date(iso));
  }

  protected initiale(nom: string): string {
    return nom.charAt(0).toUpperCase() || '?';
  }

  protected nomTuteur(tuteur: TuteurEnfant): string {
    const nom = [tuteur.prenom, tuteur.nom].filter(Boolean).join(' ') || $localize`:@@tuteurs.sansPrenom:Tuteur`;
    return tuteur.vous ? $localize`:@@tuteurs.vous:${nom}:nom: (vous)` : nom;
  }

  protected ouvrir(contact: ContactUrgence | null): void {
    this.edite.set(contact);
    this.titreFeuille.set(contact ? $localize`:@@contacts.modifier:Modifier le contact` : $localize`:@@contacts.ajouter:Ajouter un contact`);
    this.nom.setValue(contact?.nom ?? '');
    this.lien.setValue(contact?.lien ?? '');
    this.telephone.setValue(contact ? contact.telephone.replace('+226', '') : '');
    this.visible.setValue(contact?.visibleSurQr ?? false);
    this.erreurFeuille.set(null);
    this.feuille.set(true);
  }

  protected enregistrer(): void {
    if (!this.nom.value.trim() || !this.lien.value.trim() || this.telephone.value.length !== 8) {
      this.erreurFeuille.set($localize`:@@contacts.incomplet:Renseignez le nom, le lien avec l'enfant et un numéro à 8 chiffres.`);
      return;
    }
    const saisie = { nom: this.nom.value.trim(), lien: this.lien.value.trim(), telephone: this.telephone.value, visibleSurQr: this.visible.value };
    const edite = this.edite();
    this.appeler(edite ? this.client.modifierContact(this.id(), edite.id, saisie) : this.client.ajouterContact(this.id(), saisie));
  }

  protected supprimer(): void {
    this.appeler(this.client.supprimerContact(this.id(), this.edite()!.id));
  }

  protected ouvrirInvitation(): void {
    this.numeroInvite.setValue('');
    this.erreurInvitation.set(null);
    this.invitationEnvoyee.set(false);
    this.invitation.set(true);
  }

  /** Le numéro est vérifié avant d'envoyer le code : un code n'est demandé que pour une invitation recevable. */
  protected confirmerNumero(): void {
    if (this.numeroInvite.value.length !== 8) {
      this.erreurInvitation.set($localize`:@@tuteurs.numeroInvalide:Saisissez un numéro à 8 chiffres.`);
      return;
    }
    this.explication.set($localize`:@@tuteurs.explication:Pour inviter ${telephoneLisible('+226' + this.numeroInvite.value)}:numero: comme second tuteur, saisissez le code reçu par SMS.`);
    this.erreurCode.set(null);
    this.invitation.set(false);
    this.confirmation.set(true);
  }

  protected inviter(code: string): void {
    if (this.enCours()) {
      return;
    }
    this.enCours.set(true);
    this.erreurCode.set(null);
    this.client.inviterTuteur(this.id(), this.numeroInvite.value, code).subscribe({
      next: () => {
        this.enCours.set(false);
        this.confirmation.set(false);
        this.invitationEnvoyee.set(true);
        this.charger(this.id());
      },
      error: (cause: unknown) => {
        this.enCours.set(false);
        const lisible = erreurLisible(cause);
        if (CODES_A_RESAISIR.has(lisible.code ?? '')) {
          this.erreurCode.set(lisible.message);
        } else {
          // Numéro refusé (déjà tuteur, invitation en cours…) : retour à la saisie du numéro.
          this.confirmation.set(false);
          this.erreurInvitation.set(lisible.message);
          this.invitation.set(true);
        }
      },
    });
  }

  protected annulerInvitation(): void {
    this.annulation.set(true);
    this.erreur.set(null);
    this.client.annulerInvitation(this.id()).subscribe({
      next: () => {
        this.annulation.set(false);
        this.invitationEnvoyee.set(false);
        this.charger(this.id());
      },
      error: (cause: unknown) => {
        this.annulation.set(false);
        this.erreur.set(erreurLisible(cause).message);
      },
    });
  }

  private appeler(appel: ReturnType<ClientFamille['supprimerContact']> | ReturnType<ClientFamille['ajouterContact']>): void {
    if (this.enCours()) {
      return;
    }
    this.enCours.set(true);
    this.erreurFeuille.set(null);
    (appel as ReturnType<ClientFamille['supprimerContact']>).subscribe({
      next: () => {
        this.enCours.set(false);
        this.feuille.set(false);
        this.charger(this.id());
      },
      error: (cause: unknown) => {
        this.enCours.set(false);
        this.erreurFeuille.set(erreurLisible(cause).message);
      },
    });
  }

  private charger(id: string): void {
    this.erreur.set(null);
    forkJoin({ tuteurs: this.client.tuteurs(id), contacts: this.client.contacts(id) }).subscribe({
      next: ({ tuteurs, contacts }) => this.famille.set({ ...tuteurs, contacts }),
      error: (cause: unknown) => this.erreur.set(erreurLisible(cause).message),
    });
  }
}
