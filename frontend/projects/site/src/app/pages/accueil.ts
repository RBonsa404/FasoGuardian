import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { FgIcon } from 'ui';

import { Bracelet3d } from '../bracelet-3d/bracelet-3d';
import { ATOUTS, ETAPES, OFFRES, PRIX_BRACELET, SCENARIOS } from '../contenu';

const TONS = {
  alerte: 'bg-alert-soft text-alert-ink',
  accent: 'bg-accent-soft text-accent',
  succes: 'bg-success-soft text-success',
} as const;

/** Accueil (écran 1) : promesse, bracelet en trois dimensions, étapes, scénarios, offres, écoles. */
@Component({
  selector: 'app-accueil',
  imports: [RouterLink, FgIcon, Bracelet3d],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="mx-auto grid w-full max-w-6xl items-center gap-8 px-5 py-12 lg:grid-cols-2 lg:px-8 lg:py-20">
      <div class="flex flex-col gap-5">
        <span class="text-caption font-semibold tracking-widest text-accent uppercase">Conçu et suivi au Burkina Faso</span>
        <h1 class="m-0 text-h1 font-bold tracking-tight lg:text-display">Toujours près d'eux. Jamais sur leur dos.</h1>
        <p class="m-0 max-w-xl text-body-lg text-text-2">Un bracelet sans écran pour votre enfant : SOS au poignet, position quand vous en avez besoin, et un QR code pour que la personne qui le trouve vous prévienne.</p>
        <div class="flex flex-wrap gap-3">
          <a class="inline-flex min-h-13 items-center justify-center rounded-md bg-primary px-6 text-body font-semibold text-white hover:bg-primary-hover focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent" routerLink="/offres">Protéger mon enfant</a>
          <a class="inline-flex min-h-13 items-center justify-center rounded-md border-(length:--border-width-trait) border-line-strong px-6 text-body font-semibold hover:bg-surface-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent" routerLink="/fonctionnement">Voir comment ça marche</a>
        </div>
        <ul class="m-0 flex list-none flex-wrap gap-x-5 gap-y-2 p-0 text-label text-text-2">
          <li class="flex items-center gap-1.5"><fg-icon class="text-success" nom="valider" [taille]="16" />Aucune biométrie</li>
          <li class="flex items-center gap-1.5"><fg-icon class="text-success" nom="valider" [taille]="16" />Données chiffrées</li>
          <li class="flex items-center gap-1.5"><fg-icon class="text-success" nom="valider" [taille]="16" />Paiement par mobile money</li>
        </ul>
      </div>
      <div class="flex flex-col gap-2">
        <app-bracelet-3d class="h-80 rounded-xl border border-line bg-surface lg:h-112" libelle="Bracelet FasoGuardian en trois dimensions : boîtier bleu sans écran, QR code gravé, bouton SOS ambre sur la tranche. Tournez-le avec les flèches gauche et droite." />
        <span class="text-center text-caption text-text-3">Faites tourner le bracelet</span>
      </div>
    </section>

    <section class="mx-auto flex w-full max-w-6xl flex-col gap-8 px-5 py-14 lg:px-8 lg:py-20" aria-labelledby="titre-etapes">
      <div class="flex flex-col gap-2">
        <span class="text-caption font-semibold tracking-widest text-accent uppercase">Comment ça marche</span>
        <h2 id="titre-etapes" class="m-0 text-h2 font-bold tracking-tight lg:text-h1">Trois étapes, une seule fois.</h2>
      </div>
      <ol class="m-0 grid list-none gap-4 p-0 lg:grid-cols-3">
        @for (etape of etapes; track etape.numero) {
          <li class="flex flex-col gap-3 rounded-lg border border-line bg-surface p-6">
            <div class="flex items-center justify-between">
              <span class="font-display text-h2 font-bold text-accent tabular-nums">{{ etape.numero }}</span>
              <span class="grid size-12 place-items-center rounded-full bg-accent-soft text-accent"><fg-icon [nom]="etape.icone" [taille]="24" /></span>
            </div>
            <h3 class="m-0 text-h3 font-semibold">{{ etape.titre }}</h3>
            <p class="m-0 text-body text-text-2">{{ etape.texte }}</p>
          </li>
        }
      </ol>
      <ul class="m-0 grid list-none gap-4 p-0 sm:grid-cols-2 lg:grid-cols-4">
        @for (atout of atouts; track atout.titre) {
          <li class="flex flex-col gap-2">
            <span class="grid size-11 place-items-center rounded-md" [class]="tons[atout.ton]"><fg-icon [nom]="atout.icone" [taille]="22" /></span>
            <h3 class="m-0 text-body font-semibold">{{ atout.titre }}</h3>
            <p class="m-0 text-label text-text-2">{{ atout.texte }}</p>
          </li>
        }
      </ul>
    </section>

    <section class="mx-auto flex w-full max-w-6xl flex-col gap-8 px-5 py-14 lg:px-8 lg:py-20" aria-labelledby="titre-scenarios">
      <div class="flex flex-col gap-2">
        <span class="text-caption font-semibold tracking-widest text-accent uppercase">Scénarios</span>
        <h2 id="titre-scenarios" class="m-0 text-h2 font-bold tracking-tight lg:text-h1">À quoi ça sert, concrètement</h2>
        <p class="m-0 text-label text-text-3">Situations illustratives, personnages fictifs.</p>
      </div>
      <ul class="m-0 grid list-none gap-4 p-0 lg:grid-cols-3">
        @for (scenario of scenarios; track scenario.titre) {
          <li class="flex flex-col gap-2 rounded-lg border border-line bg-surface p-6">
            <span class="text-caption font-semibold text-text-3">Scénario · {{ scenario.lieu }}</span>
            <h3 class="m-0 text-h3 font-semibold">{{ scenario.titre }}</h3>
            <p class="m-0 text-body text-text-2">{{ scenario.texte }}</p>
          </li>
        }
      </ul>
    </section>

    <section class="mx-auto flex w-full max-w-6xl flex-col gap-8 px-5 py-14 lg:px-8 lg:py-20" aria-labelledby="titre-offres">
      <div class="flex flex-col gap-2">
        <span class="text-caption font-semibold tracking-widest text-accent uppercase">Offres</span>
        <h2 id="titre-offres" class="m-0 text-h2 font-bold tracking-tight lg:text-h1">SOS, retrait et page QR inclus partout.</h2>
        <p class="m-0 text-body text-text-2">Bracelet {{ prix }}, puis un abonnement mensuel sans engagement.</p>
      </div>
      <ul class="m-0 grid list-none gap-4 p-0 sm:grid-cols-2 lg:grid-cols-4">
        @for (offre of offres; track offre.nom) {
          <li class="flex flex-col gap-3 rounded-lg bg-surface p-6" [class]="offre.miseEnAvant ? 'border-2 border-accent' : 'border border-line'">
            <div class="flex min-h-6 items-center justify-between gap-2">
              <h3 class="m-0 text-h3 font-semibold">{{ offre.nom }}</h3>
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
      <a class="inline-flex min-h-13 items-center justify-center rounded-md border-(length:--border-width-trait) border-line-strong px-6 text-body font-semibold hover:bg-surface-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent self-start" routerLink="/offres">Comparer les offres</a>
    </section>

    <section class="mx-auto flex w-full max-w-6xl flex-col gap-8 px-5 py-14 lg:px-8 lg:py-20" aria-labelledby="titre-ecoles">
      <div class="flex flex-col gap-4 rounded-xl border border-line bg-surface p-8 lg:p-12">
        <span class="text-caption font-semibold tracking-widest text-accent uppercase">Établissements scolaires</span>
        <h2 id="titre-ecoles" class="m-0 text-h2 font-bold tracking-tight lg:text-h1">L'offre École : le bracelet fourni par l'établissement.</h2>
        <p class="m-0 max-w-2xl text-body-lg text-text-2">4 000 FCFA par élève et par mois, sur convention. Inscription accompagnée des familles sur place.</p>
        <a class="inline-flex min-h-13 items-center justify-center rounded-md bg-primary px-6 text-body font-semibold text-white hover:bg-primary-hover focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent self-start" routerLink="/ecoles">Découvrir l'offre École</a>
      </div>
    </section>
  `,
  host: { class: 'block' },
})
export class Accueil {
  protected readonly etapes = ETAPES;
  protected readonly atouts = ATOUTS;
  protected readonly scenarios = SCENARIOS;
  protected readonly offres = OFFRES;
  protected readonly prix = PRIX_BRACELET;
  protected readonly tons = TONS;
}
