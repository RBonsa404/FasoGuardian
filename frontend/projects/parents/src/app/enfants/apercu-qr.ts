import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';

import { Bracelet, ClientBracelet, ClientFamille, ContactUrgence, FicheSante, TypeElementMedical } from 'api';
import { FgBanniere, FgIcon, FgSquelette } from 'ui';

import { erreurLisible } from '../commun/erreurs';

const TYPES: Record<TypeElementMedical, string> = {
  ALLERGIE: $localize`:@@medical.type.allergie:Allergie`,
  PATHOLOGIE: $localize`:@@medical.type.pathologie:Pathologie`,
  TRAITEMENT: $localize`:@@medical.type.traitement:Traitement`,
  AUTRE: $localize`:@@medical.type.autre:Autre`,
};

interface Ligne {
  readonly cle: string;
  readonly valeur: string;
}

/**
 * Aperçu de la page QR (écran 33, US-TRS-001) : ce que voit la personne qui scanne le bracelet. L'aperçu est
 * composé des mêmes réglages que la page publique : contacts marqués joignables, informations médicales
 * marquées critiques, groupe sanguin s'il est autorisé. Ni nom, ni photo, ni position.
 */
@Component({
  selector: 'app-apercu-qr',
  imports: [RouterLink, FgBanniere, FgIcon, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="-ml-2.5 grid size-11 place-items-center rounded-md focus-visible:outline-2 focus-visible:outline-accent" [routerLink]="['/enfants', id()]" i18n-aria-label="@@etape.retour" aria-label="Retour">
      <fg-icon nom="retour" [taille]="22" />
    </a>
    <div class="flex flex-col gap-1.5">
      <h1 class="m-0 text-h2 font-bold tracking-tight" i18n="@@apercuQr.titre">Ce que voit un tiers</h1>
      <p class="m-0 text-label text-text-2" i18n="@@apercuQr.texte">Ni nom, ni photo, ni position. Uniquement le numéro et les informations critiques.</p>
    </div>

    @if (charge()) {
      @if (bracelet(); as b) {
        <section class="mx-1 flex flex-col overflow-hidden rounded-xl border-6 border-surface-2 bg-bg" i18n-aria-label="@@apercuQr.cadre" aria-label="Aperçu de la page publique">
          <div class="flex items-center justify-between border-b border-line px-3.5 py-3">
            <strong class="text-label">FasoGuardian</strong>
            <span class="rounded-xs bg-surface-2 px-2 py-0.5 font-mono text-caption font-semibold">{{ b.numeroSerie }}</span>
          </div>
          @if (b.statut === 'PERDU') {
            <p class="m-0 px-3.5 pt-4 text-h3 font-bold" i18n="@@apercuQr.desactive">Ce bracelet est désactivé</p>
            <p class="m-0 px-3.5 pt-1.5 pb-4 text-label text-text-2" i18n="@@apercuQr.desactive.texte">Il a été déclaré perdu : la page ne montre ni contact ni information médicale.</p>
          } @else {
            <p class="m-0 px-3.5 pt-4 pb-2 text-h3 font-bold" i18n="@@apercuQr.trouve">Vous avez trouvé un enfant ?</p>
            <div class="flex flex-col gap-2 px-3.5 py-1.5">
              @for (contact of joignables(); track contact.id) {
                <span class="flex min-h-12 items-center gap-2.5 rounded-md bg-primary px-3.5 text-body font-bold text-white"><fg-icon nom="telephone" [taille]="18" /><ng-container i18n="@@apercuQr.appeler">Appeler</ng-container> · {{ contact.lien }}</span>
              }
              <div class="grid grid-cols-2 gap-2">
                <span class="flex min-h-11 flex-col justify-center rounded-md border border-line bg-surface px-3"><strong class="text-label font-bold">17</strong><span class="text-caption text-text-2" i18n="@@apercuQr.police">Police secours</span></span>
                <span class="flex min-h-11 flex-col justify-center rounded-md border border-line bg-surface px-3"><strong class="text-label font-bold">18</strong><span class="text-caption text-text-2" i18n="@@apercuQr.pompiers">Sapeurs-pompiers</span></span>
              </div>
            </div>
            @if (medical().length > 0) {
              <dl class="m-0 mx-3.5 mt-2 mb-3.5 flex flex-col gap-1.5 rounded-md border border-line bg-surface p-3 text-label">
                @for (ligne of medical(); track ligne.cle + ligne.valeur) {
                  <div class="flex justify-between gap-3">
                    <dt class="text-text-2">{{ ligne.cle }}</dt>
                    <dd class="m-0 text-right font-semibold">{{ ligne.valeur }}</dd>
                  </div>
                }
              </dl>
            } @else {
              <span class="pb-3.5"></span>
            }
          }
        </section>

        @if (b.statut !== 'PERDU' && joignables().length === 0) {
          <fg-banner ton="attention"><ng-container i18n="@@apercuQr.aucunContact">Aucun contact n'est joignable depuis la page : un tiers ne pourra appeler que le 17 ou le 18.</ng-container> <a class="font-semibold underline" [routerLink]="['/enfants', id(), 'contacts']" i18n="@@apercuQr.contacts">Régler les contacts</a></fg-banner>
        }
        <p class="m-0 rounded-banner bg-surface-2 px-3.5 py-3 text-label font-medium text-text-2" i18n="@@apercuQr.scan">Vous êtes prévenu à chaque scan du bracelet. En appuyant sur « Appeler », le tiers compose le numéro du contact : ne marquez joignables que des personnes de confiance.</p>
        <div class="flex flex-wrap gap-x-5 gap-y-1">
          <a class="py-2 text-label font-semibold text-accent" [routerLink]="['/enfants', id(), 'medical']" i18n="@@apercuQr.medical">Modifier la fiche médicale</a>
          <a class="py-2 text-label font-semibold text-accent" [routerLink]="['/enfants', id(), 'contacts']" i18n="@@apercuQr.contacts">Régler les contacts</a>
        </div>
      } @else {
        <fg-banner ton="info" i18n="@@apercuQr.sansBracelet">Aucun bracelet n'est associé à cet enfant : sa page QR n'existe pas encore.</fg-banner>
        <a class="self-start py-2 text-label font-semibold text-accent" routerLink="/bracelet/associer" [queryParams]="{ enfant: id() }" i18n="@@apercu.associer">Associer</a>
      }
    } @else if (erreur(); as message) {
      <fg-banner ton="erreur">{{ message }}</fg-banner>
    } @else {
      <fg-skeleton forme="carte" />
      <fg-skeleton forme="carte" />
    }
  `,
  host: { class: 'mx-auto flex min-h-dvh w-full max-w-md flex-col gap-4 px-5 pt-6 pb-7 lg:mx-0 lg:max-w-2xl lg:px-10 lg:pt-10' },
})
export class ApercuQr {
  readonly id = input.required<string>();

  private readonly famille = inject(ClientFamille);
  private readonly client = inject(ClientBracelet);

  protected readonly charge = signal(false);
  protected readonly bracelet = signal<Bracelet | null>(null);
  private readonly contacts = signal<readonly ContactUrgence[]>([]);
  private readonly sante = signal<FicheSante | null>(null);
  protected readonly erreur = signal<string | null>(null);

  protected readonly joignables = computed(() => this.contacts().filter((contact) => contact.visibleSurQr));

  protected readonly medical = computed<Ligne[]>(() => {
    const fiche = this.sante();
    if (!fiche) {
      return [];
    }
    const lignes: Ligne[] = fiche.elements.filter((element) => element.critique).map((element) => ({ cle: TYPES[element.type], valeur: element.libelle }));
    if (fiche.groupeSanguin && fiche.groupeSanguinSurQr) {
      lignes.push({ cle: $localize`:@@apercuQr.groupe:Groupe sanguin`, valeur: fiche.groupeSanguin });
    }
    return lignes;
  });

  constructor() {
    effect(() => {
      const id = this.id();
      forkJoin({
        // Sans bracelet, la page n'existe pas : ce n'est pas une erreur.
        bracelet: this.client.bracelet(id).pipe(catchError(() => of(null))),
        contacts: this.famille.contacts(id),
        sante: this.famille.sante(id),
      }).subscribe({
        next: ({ bracelet, contacts, sante }) => {
          this.bracelet.set(bracelet);
          this.contacts.set(contacts);
          this.sante.set(sante);
          this.charge.set(true);
        },
        error: (cause: unknown) => this.erreur.set(erreurLisible(cause).message),
      });
    });
  }
}
