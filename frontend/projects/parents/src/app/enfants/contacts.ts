import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ClientFamille, ContactUrgence } from 'api';
import { FgBadge, FgBanniere, FgBouton, FgChamp, FgFeuille, FgIcon, FgInterrupteur, FgSquelette, FgTelephone } from 'ui';

import { erreurLisible } from '../commun/erreurs';

const MAXIMUM = 5;

/**
 * Contacts d'urgence (écran 34, US-PAR-003). Un contact marqué « visible sur la page QR » peut être appelé
 * par la personne qui trouve l'enfant ; son nom n'y apparaît jamais, seulement son lien avec l'enfant.
 */
@Component({
  selector: 'app-contacts',
  imports: [ReactiveFormsModule, RouterLink, FgBadge, FgBanniere, FgBouton, FgChamp, FgFeuille, FgIcon, FgInterrupteur, FgSquelette, FgTelephone],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="-ml-2.5 grid size-11 place-items-center rounded-md focus-visible:outline-2 focus-visible:outline-accent" [routerLink]="['/enfants', id()]" i18n-aria-label="@@etape.retour" aria-label="Retour">
      <fg-icon nom="retour" [taille]="22" />
    </a>
    <h1 class="m-0 text-titre-ecran font-bold tracking-tight" i18n="@@contacts.titre">Contacts d'urgence</h1>
    <p class="m-0 text-label text-text-2" i18n="@@contacts.texte">Cinq contacts au plus. Sur la page QR, seuls le lien avec l'enfant et un bouton d'appel sont affichés.</p>

    @if (contacts(); as liste) {
      @for (contact of liste; track contact.id) {
        <button type="button" class="flex items-center gap-3 rounded-lg border border-line bg-surface p-4 text-left focus-visible:outline-2 focus-visible:outline-accent" (click)="ouvrir(contact)">
          <span class="grid size-11 flex-none place-items-center rounded-full bg-surface-2 font-display text-body font-bold" aria-hidden="true">{{ contact.nom.charAt(0) }}</span>
          <span class="flex min-w-0 flex-1 flex-col gap-0.5">
            <strong class="truncate text-body font-semibold">{{ contact.nom }}</strong>
            <span class="text-label text-text-2 tabular-nums">{{ contact.lien }} · {{ contact.telephone }}</span>
          </span>
          @if (contact.visibleSurQr) {
            <fg-badge ton="accent" icone="qr" i18n="@@contacts.visible">Page QR</fg-badge>
          }
        </button>
      } @empty {
        <p class="m-0 rounded-lg border border-line bg-surface p-5 text-body text-text-2" i18n="@@contacts.vide">Aucun contact pour l'instant. Ajoutez une personne de confiance à prévenir.</p>
      }
      <button fg-button variante="secondary" type="button" [disabled]="liste.length >= maximum" (click)="ouvrir(null)" i18n="@@contacts.ajouter">Ajouter un contact</button>
    } @else if (erreur(); as message) {
      <fg-banner ton="erreur">{{ message }}</fg-banner>
    } @else {
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
  `,
  host: { class: 'mx-auto flex min-h-dvh w-full max-w-md flex-col gap-4 px-5 pt-6 pb-7 lg:mx-0 lg:max-w-2xl lg:px-10 lg:pt-10' },
})
export class Contacts {
  readonly id = input.required<string>();

  private readonly client = inject(ClientFamille);

  protected readonly maximum = MAXIMUM;
  protected readonly contacts = signal<ContactUrgence[] | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly erreurFeuille = signal<string | null>(null);
  protected readonly feuille = signal(false);
  protected readonly edite = signal<ContactUrgence | null>(null);
  protected readonly enCours = signal(false);
  protected readonly titreFeuille = signal('');

  protected readonly nom = new FormControl('', { nonNullable: true });
  protected readonly lien = new FormControl('', { nonNullable: true });
  protected readonly telephone = new FormControl('', { nonNullable: true });
  protected readonly visible = new FormControl(false, { nonNullable: true });

  constructor() {
    effect(() => this.charger(this.id()));
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
    this.client.contacts(id).subscribe({
      next: (contacts) => this.contacts.set(contacts),
      error: (cause: unknown) => this.erreur.set(erreurLisible(cause).message),
    });
  }
}
