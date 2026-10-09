import { ChangeDetectionStrategy, Component, computed, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ESPACE_PARENTS, POINTS_RELAIS, QUESTIONS, URGENCE } from '../contenu';

/** Points d'inscription et relais (écran 7), par ville. */
@Component({
  selector: 'app-points-relais',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="mx-auto flex w-full max-w-6xl flex-col gap-8 px-5 py-14 lg:px-8 lg:py-20">
      <div class="flex flex-col gap-3">
        <span class="text-caption font-semibold tracking-widest text-accent uppercase">Points d'inscription et relais</span>
        <h1 class="m-0 text-h1 font-bold tracking-tight lg:text-display">Points relais</h1>
        <p class="m-0 max-w-3xl text-body-lg text-text-2">On s'y inscrit avec ses pièces, on y retire son bracelet, on y fait échanger une sangle.</p>
      </div>
      <p class="m-0 max-w-3xl rounded-lg bg-attention-soft p-4 text-label text-attention" role="note">Le réseau est en cours d'ouverture : cette liste est donnée à titre indicatif. Les adresses et les horaires seront publiés ici à l'ouverture de chaque point.</p>
      <div class="flex flex-wrap gap-2" role="group" aria-label="Ville">
        @for (ville of villes; track ville.nom) {
          <button type="button" class="h-11 rounded-full px-4 text-label font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent" [class]="choisie() === ville.nom ? 'border-2 border-accent bg-accent-soft' : 'border border-line-strong text-text-2'" [attr.aria-pressed]="choisie() === ville.nom" (click)="choisie.set(ville.nom)">{{ ville.nom }} · {{ ville.nombre }}</button>
        }
      </div>
      <ul class="m-0 grid list-none gap-3 p-0 lg:grid-cols-2">
        @for (point of points(); track point.nom) {
          <li class="flex flex-col gap-1 rounded-lg border border-line bg-surface p-5">
            <strong class="text-body font-semibold">{{ point.nom }}</strong>
            <span class="text-label text-text-2">{{ point.repere }} · {{ point.services }}</span>
          </li>
        }
      </ul>
    </section>
  `,
  host: { class: 'block' },
})
export class PointsRelais {
  protected readonly villes = [...new Set(POINTS_RELAIS.map((point) => point.ville))].map((nom) => ({ nom, nombre: POINTS_RELAIS.filter((point) => point.ville === nom).length }));
  protected readonly choisie = signal<string>(this.villes[0].nom);
  protected readonly points = computed(() => POINTS_RELAIS.filter((point) => point.ville === this.choisie()));
}

/** Questions fréquentes (écran 8) : une réponse ouverte à la fois. */
@Component({
  selector: 'app-faq',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="mx-auto flex w-full max-w-6xl flex-col gap-8 px-5 py-14 lg:px-8 lg:py-20">
      <h1 class="m-0 text-h1 font-bold tracking-tight lg:text-display">Questions fréquentes</h1>
      <ul class="m-0 flex max-w-3xl list-none flex-col gap-2 p-0">
        @for (question of questions; track question.question; let i = $index) {
          <li class="rounded-lg border border-line bg-surface">
            <h2 class="m-0">
              <button type="button" class="flex min-h-14 w-full items-center justify-between gap-4 rounded-lg px-5 text-left text-body font-semibold focus-visible:outline-2 focus-visible:outline-accent" [attr.aria-expanded]="ouverte() === i" [attr.aria-controls]="'reponse-' + i" (click)="ouverte.set(ouverte() === i ? -1 : i)">
                {{ question.question }}<span class="flex-none text-h3 text-accent" aria-hidden="true">{{ ouverte() === i ? '−' : '+' }}</span>
              </button>
            </h2>
            @if (ouverte() === i) {
              <p class="m-0 px-5 pb-5 text-body text-text-2" [id]="'reponse-' + i">{{ question.reponse }}</p>
            }
          </li>
        }
      </ul>
      <a class="inline-flex min-h-13 items-center justify-center rounded-md border-(length:--border-width-trait) border-line-strong px-6 text-body font-semibold hover:bg-surface-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent self-start" routerLink="/contact">Une autre question ?</a>
    </section>
  `,
  host: { class: 'block' },
})
export class Faq {
  protected readonly questions = QUESTIONS;
  protected readonly ouverte = signal(0);
}

/** Contact (écran 8). Le site ne recueille aucune donnée : les demandes passent par l'application ou un point relais. */
@Component({
  selector: 'app-contact',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="mx-auto flex w-full max-w-6xl flex-col gap-8 px-5 py-14 lg:px-8 lg:py-20">
      <h1 class="m-0 text-h1 font-bold tracking-tight lg:text-display">Contact</h1>
      <p class="m-0 max-w-3xl rounded-lg bg-alert-soft p-4 text-body font-semibold text-alert-ink" role="note">{{ urgence }}</p>
      <ul class="m-0 grid list-none gap-4 p-0 lg:grid-cols-3">
        <li class="flex flex-col gap-2 rounded-lg border border-line bg-surface p-6">
          <h2 class="m-0 text-h3 font-semibold">Vous êtes parent</h2>
          <p class="m-0 text-label text-text-2">Écrivez au support depuis l'application, rubrique « Aide et support » : la réponse arrive dans votre compte, et personne d'autre ne la lit.</p>
          <a class="mt-auto self-start py-2 text-label font-semibold text-accent" [href]="espaceParents">Ouvrir l'espace parents</a>
        </li>
        <li class="flex flex-col gap-2 rounded-lg border border-line bg-surface p-6">
          <h2 class="m-0 text-h3 font-semibold">Vous voulez vous inscrire</h2>
          <p class="m-0 text-label text-text-2">Un agent vous reçoit en point d'inscription, vérifie vos pièces et vous remet le bracelet.</p>
          <a class="mt-auto self-start py-2 text-label font-semibold text-accent" routerLink="/points-relais">Voir les points relais</a>
        </li>
        <li class="flex flex-col gap-2 rounded-lg border border-line bg-surface p-6">
          <h2 class="m-0 text-h3 font-semibold">Vous dirigez un établissement</h2>
          <p class="m-0 text-label text-text-2">L'offre École se met en place par convention. Notre équipe se déplace pour la présenter.</p>
          <a class="mt-auto self-start py-2 text-label font-semibold text-accent" routerLink="/ecoles">Découvrir l'offre École</a>
        </li>
      </ul>
      <p class="m-0 max-w-3xl text-label text-text-3">Ce site ne comporte aucun formulaire et ne recueille aucune donnée personnelle. Les coordonnées du support (téléphone, courriel) seront publiées ici à l'ouverture du service.</p>
    </section>
  `,
  host: { class: 'block' },
})
export class Contact {
  protected readonly urgence = URGENCE;
  protected readonly espaceParents = ESPACE_PARENTS;
}

/** Page introuvable (écran 8) : oriente aussi la personne qui a recopié l'adresse d'un bracelet. */
@Component({
  selector: 'app-introuvable',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="mx-auto flex w-full max-w-6xl flex-col gap-8 px-5 py-14 lg:px-8 lg:py-20">
      <span class="font-display text-display font-bold text-accent">404</span>
      <h1 class="m-0 text-h1 font-bold tracking-tight lg:text-display">Cette page n'existe pas.</h1>
      <p class="m-0 max-w-2xl text-body-lg text-text-2">Vous avez scanné un bracelet ? Utilisez l'appareil photo de votre téléphone directement sur le QR code.</p>
      <a class="inline-flex min-h-13 items-center justify-center rounded-md bg-primary px-6 text-body font-semibold text-white hover:bg-primary-hover focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent self-start" routerLink="/">Retour à l'accueil</a>
    </section>
  `,
  host: { class: 'block' },
})
export class Introuvable {}
