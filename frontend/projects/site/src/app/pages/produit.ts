import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { FgIcon } from 'ui';

import { COMPARATIF, CONSERVATION, ENGAGEMENTS, ETAPES_ECOLE, FONCTIONNEMENT, OFFRES, PRIX_BRACELET, QUESTIONS_TARIFS } from '../contenu';

const TONS = { accent: 'text-accent', succes: 'text-success', alerte: 'text-alert-ink', neutre: 'text-text-2' } as const;

/** Fonctionnement (écran 3) : ce que voit le parent, ce que voit un inconnu, ce que personne ne voit. */
@Component({
  selector: 'app-fonctionnement',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="mx-auto flex w-full max-w-6xl flex-col gap-8 px-5 py-14 lg:px-8 lg:py-20">
      <div class="flex flex-col gap-3">
        <span class="text-caption font-semibold tracking-widest text-accent uppercase">Fonctionnement</span>
        <h1 class="m-0 text-h1 font-bold tracking-tight lg:text-display">Ce que vous voyez, ce que voit un inconnu, ce que personne ne voit.</h1>
      </div>
      <ul class="m-0 grid list-none gap-4 p-0 lg:grid-cols-2">
        @for (bloc of blocs; track bloc.surtitre) {
          <li class="flex flex-col gap-2 rounded-lg border border-line bg-surface p-6">
            <span class="text-caption font-semibold tracking-widest" [class]="tons[bloc.ton]">{{ bloc.surtitre }}</span>
            <h2 class="m-0 text-h3 font-semibold">{{ bloc.titre }}</h2>
            <p class="m-0 text-body text-text-2">{{ bloc.texte }}</p>
          </li>
        }
      </ul>
      <div class="flex flex-wrap gap-3">
        <a class="inline-flex min-h-13 items-center justify-center rounded-md bg-primary px-6 text-body font-semibold text-white hover:bg-primary-hover focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent" routerLink="/offres">Voir les offres</a>
        <a class="inline-flex min-h-13 items-center justify-center rounded-md border-(length:--border-width-trait) border-line-strong px-6 text-body font-semibold hover:bg-surface-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent" routerLink="/securite">Sécurité et données</a>
      </div>
    </section>
  `,
  host: { class: 'block' },
})
export class Fonctionnement {
  protected readonly blocs = FONCTIONNEMENT;
  protected readonly tons = TONS;
}

/** Offres, comparatif et questions tarifaires (écran 4). */
@Component({
  selector: 'app-offres',
  imports: [RouterLink, FgIcon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="mx-auto flex w-full max-w-6xl flex-col gap-8 px-5 py-14 lg:px-8 lg:py-20">
      <div class="flex flex-col gap-3">
        <span class="text-caption font-semibold tracking-widest text-accent uppercase">Offres</span>
        <h1 class="m-0 text-h1 font-bold tracking-tight lg:text-display">SOS, retrait et page QR inclus partout.</h1>
        <p class="m-0 text-body-lg text-text-2">Bracelet {{ prix }}, puis un abonnement mensuel sans engagement, par enfant.</p>
      </div>
      <ul class="m-0 grid list-none gap-4 p-0 sm:grid-cols-2 lg:grid-cols-4">
        @for (offre of offres; track offre.nom) {
          <li class="flex flex-col gap-3 rounded-lg bg-surface p-6" [class]="offre.miseEnAvant ? 'border-2 border-accent' : 'border border-line'">
            <div class="flex min-h-6 items-center justify-between gap-2">
              <h2 class="m-0 text-h3 font-semibold">{{ offre.nom }}</h2>
              @if (offre.etiquette) {
                <span class="rounded-full bg-accent-soft px-2.5 py-0.5 text-caption font-semibold text-accent">{{ offre.etiquette }}</span>
              }
            </div>
            <p class="m-0 flex items-baseline gap-1.5"><strong class="font-display text-h1 font-bold tabular-nums">{{ offre.prix }}</strong><span class="text-label text-text-2">FCFA / mois</span></p>
            <ul class="m-0 flex list-none flex-col gap-1.5 p-0 text-label text-text-2">
              @for (point of offre.points; track point) {
                <li class="flex items-start gap-1.5"><fg-icon class="mt-0.5 flex-none text-success" nom="valider" [taille]="16" />{{ point }}</li>
              }
            </ul>
          </li>
        }
      </ul>
    </section>

    <section class="mx-auto flex w-full max-w-6xl flex-col gap-8 px-5 py-14 lg:px-8 lg:py-20" aria-labelledby="titre-comparatif">
      <h2 id="titre-comparatif" class="m-0 text-h2 font-bold tracking-tight lg:text-h1">Comparer les offres</h2>
      <div class="overflow-x-auto rounded-lg border border-line bg-surface">
        <table class="w-full min-w-160 border-collapse text-left text-label">
          <thead>
            <tr class="bg-surface-2">
              <th class="px-4 py-3 font-semibold" scope="col"><span class="sr-only">Critère</span></th>
              @for (offre of offres; track offre.nom) {
                <th class="px-4 py-3 font-semibold" scope="col">{{ offre.nom }}</th>
              }
            </tr>
          </thead>
          <tbody>
            @for (ligne of comparatif; track ligne.critere) {
              <tr class="border-t border-line">
                <th class="px-4 py-3 font-medium text-text-2" scope="row">{{ ligne.critere }}</th>
                @for (valeur of ligne.valeurs; track $index) {
                  <td class="px-4 py-3 tabular-nums">{{ valeur }}</td>
                }
              </tr>
            }
          </tbody>
        </table>
      </div>
      <dl class="m-0 grid gap-4 lg:grid-cols-2">
        @for (question of questions; track question.question) {
          <div class="flex flex-col gap-1.5 rounded-lg border border-line bg-surface p-5">
            <dt class="text-body font-semibold">{{ question.question }}</dt>
            <dd class="m-0 text-label text-text-2">{{ question.reponse }}</dd>
          </div>
        }
      </dl>
      <a class="inline-flex min-h-13 items-center justify-center rounded-md bg-primary px-6 text-body font-semibold text-white hover:bg-primary-hover focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent self-start" routerLink="/points-relais">Trouver un point d'inscription</a>
    </section>
  `,
  host: { class: 'block' },
})
export class Offres {
  protected readonly offres = OFFRES;
  protected readonly comparatif = COMPARATIF;
  protected readonly questions = QUESTIONS_TARIFS;
  protected readonly prix = PRIX_BRACELET;
}

/** Établissements scolaires (écran 5). */
@Component({
  selector: 'app-ecoles',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="mx-auto flex w-full max-w-6xl flex-col gap-8 px-5 py-14 lg:px-8 lg:py-20">
      <div class="flex flex-col gap-3">
        <span class="text-caption font-semibold tracking-widest text-accent uppercase">Pour les établissements</span>
        <h1 class="m-0 text-h1 font-bold tracking-tight lg:text-display">L'offre École : le bracelet fourni par l'établissement.</h1>
        <p class="m-0 max-w-3xl text-body-lg text-text-2">4 000 FCFA par élève et par mois, sur convention : les bracelets, l'inscription des familles sur place et leur accompagnement. Chaque parent garde son compte ; l'établissement n'accède à aucune position.</p>
      </div>
      <ol class="m-0 grid list-none gap-4 p-0 sm:grid-cols-2 lg:grid-cols-4">
        @for (etape of etapes; track etape.numero) {
          <li class="flex flex-col gap-2 rounded-lg border border-line bg-surface p-6">
            <span class="font-display text-h2 font-bold text-accent">{{ etape.numero }}</span>
            <h2 class="m-0 text-h3 font-semibold">{{ etape.titre }}</h2>
            <p class="m-0 text-label text-text-2">{{ etape.texte }}</p>
          </li>
        }
      </ol>
      <p class="m-0 max-w-3xl rounded-lg bg-surface-2 p-5 text-label text-text-2">Une passerelle radio dans la cour, pour confirmer la présence des élèves sans réseau mobile, est à l'étude pour de futurs pilotes. Elle ne fait pas partie de l'offre actuelle.</p>
      <a class="inline-flex min-h-13 items-center justify-center rounded-md bg-primary px-6 text-body font-semibold text-white hover:bg-primary-hover focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent self-start" routerLink="/contact">Parler à notre équipe Écoles</a>
    </section>
  `,
  host: { class: 'block' },
})
export class Ecoles {
  protected readonly etapes = ETAPES_ECOLE;
}

/** Sécurité et protection des données (écran 6). */
@Component({
  selector: 'app-securite',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="mx-auto flex w-full max-w-6xl flex-col gap-8 px-5 py-14 lg:px-8 lg:py-20">
      <div class="flex flex-col gap-3">
        <span class="text-caption font-semibold tracking-widest text-accent uppercase">Sécurité et données</span>
        <h1 class="m-0 text-h1 font-bold tracking-tight lg:text-display">Sécurité et protection des données</h1>
        <p class="m-0 max-w-3xl text-body-lg text-text-2">Les données d'un enfant ne servent qu'à le protéger. Voici ce que nous faisons, et pendant combien de temps nous gardons quoi.</p>
      </div>
      <ul class="m-0 grid list-none gap-4 p-0 sm:grid-cols-2 lg:grid-cols-3">
        @for (engagement of engagements; track engagement.titre) {
          <li class="flex flex-col gap-2 rounded-lg border border-line bg-surface p-6">
            <h2 class="m-0 text-h3 font-semibold">{{ engagement.titre }}</h2>
            <p class="m-0 text-label text-text-2">{{ engagement.texte }}</p>
          </li>
        }
      </ul>
      <div class="flex flex-col gap-3">
        <h2 class="m-0 text-h2 font-bold tracking-tight lg:text-h1">Durées de conservation</h2>
        <dl class="m-0 flex flex-col rounded-lg border border-line bg-surface">
          @for (ligne of conservation; track ligne.donnee) {
            <div class="flex flex-wrap justify-between gap-x-6 gap-y-1 border-b border-line px-5 py-3.5 text-label last:border-b-0">
              <dt class="font-medium">{{ ligne.donnee }}</dt>
              <dd class="m-0 text-text-2">{{ ligne.duree }}</dd>
            </div>
          }
        </dl>
      </div>
      <a class="inline-flex min-h-13 items-center justify-center rounded-md border-(length:--border-width-trait) border-line-strong px-6 text-body font-semibold hover:bg-surface-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent self-start" routerLink="/confidentialite">Lire la politique de confidentialité</a>
    </section>
  `,
  host: { class: 'block' },
})
export class Securite {
  protected readonly engagements = ENGAGEMENTS;
  protected readonly conservation = CONSERVATION;
}
