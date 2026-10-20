import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { FgIcon } from 'ui';

import { Apparition } from '../apparition';
import { SceneBracelet } from '../scene-bracelet';
import { ATOUTS, ETAPES, OFFRES, PRIX_BRACELET, SCENARIOS } from '../contenu';

const TONS = {
  alerte: 'bg-alert-soft text-alert-ink',
  accent: 'bg-accent-soft text-accent',
  succes: 'bg-success-soft text-success',
} as const;

/** Accueil (écran 1) : promesse, bracelet en trois dimensions, étapes, scénarios, offres, écoles. */
@Component({
  selector: 'app-accueil',
  imports: [RouterLink, FgIcon, Apparition, SceneBracelet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="halo relative mx-auto grid w-full max-w-6xl items-center gap-10 px-5 py-14 lg:grid-cols-2 lg:px-8 lg:py-24">
      <div class="trame pointer-events-none absolute inset-0 -z-10" aria-hidden="true"></div>
      <div class="flex animate-(--animate-rise) flex-col gap-5">
        <span class="inline-flex items-center gap-2 self-start rounded-full border border-line bg-surface/70 px-3 py-1 text-caption font-semibold tracking-widest text-accent uppercase backdrop-blur">
          <span class="size-1.5 rounded-full bg-success" aria-hidden="true"></span>Conçu et suivi au Burkina Faso
        </span>
        <h1 class="m-0 text-h1 font-bold tracking-tight text-balance lg:text-display">Toujours près d'eux. <span class="texte-degrade">Jamais sur leur dos.</span></h1>
        <p class="m-0 max-w-xl text-body-lg text-text-2">Un bracelet sans écran pour votre enfant : SOS au poignet, position quand vous en avez besoin, et un QR code pour que la personne qui le trouve vous prévienne.</p>
        <div class="flex flex-wrap gap-3">
          <a class="group inline-flex min-h-13 items-center justify-center gap-2 rounded-md bg-primary px-6 text-body font-semibold text-white shadow-e2 transition-[background-color,transform] duration-(--duration-fast) hover:bg-primary-hover active:scale-[.98] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent" routerLink="/offres">
            Protéger mon enfant
            <span class="transition-transform duration-(--duration-base) group-hover:translate-x-1" aria-hidden="true">→</span>
          </a>
          <a class="inline-flex min-h-13 items-center justify-center rounded-md border-(length:--border-width-trait) border-line-strong bg-surface/60 px-6 text-body font-semibold backdrop-blur transition-colors duration-(--duration-fast) hover:bg-surface-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent" routerLink="/fonctionnement">Voir comment ça marche</a>
        </div>
        <ul class="m-0 flex list-none flex-wrap gap-x-5 gap-y-2 p-0 text-label text-text-2">
          <li class="flex items-center gap-1.5"><fg-icon class="text-success" nom="valider" [taille]="16" />Aucune biométrie</li>
          <li class="flex items-center gap-1.5"><fg-icon class="text-success" nom="valider" [taille]="16" />Données chiffrées</li>
          <li class="flex items-center gap-1.5"><fg-icon class="text-success" nom="valider" [taille]="16" />Paiement par mobile money</li>
        </ul>
      </div>
      <div class="relative flex flex-col gap-2">
        <div class="pointer-events-none absolute inset-6 -z-10 rounded-full bg-accent/20 blur-3xl" aria-hidden="true"></div>
        <app-scene-bracelet class="h-80 rounded-xl border border-line shadow-e3 lg:h-112" />
        <!-- Pastilles d'illustration : ce que le parent voit arriver sur son téléphone. -->
        <div class="flottant absolute -top-4 left-2 hidden items-center gap-2.5 rounded-lg border border-line bg-surface/90 px-3.5 py-2.5 shadow-e2 backdrop-blur sm:flex lg:-left-8" aria-hidden="true">
          <span class="grid size-8 place-items-center rounded-full bg-alert text-on-alert"><fg-icon nom="sos" [taille]="16" /></span>
          <span class="flex flex-col"><strong class="text-label font-semibold">SOS reçu</strong><span class="text-caption text-text-2">en moins de 60 s</span></span>
        </div>
        <div class="flottant-lent absolute right-2 bottom-10 hidden items-center gap-2.5 rounded-lg border border-line bg-surface/90 px-3.5 py-2.5 shadow-e2 backdrop-blur sm:flex lg:-right-8" aria-hidden="true">
          <span class="grid size-8 place-items-center rounded-full bg-success-soft text-success"><fg-icon nom="ecole" [taille]="16" /></span>
          <span class="flex flex-col"><strong class="text-label font-semibold">Arrivée à l'école</strong><span class="text-caption text-text-2">07:42 · dans la Safe Zone</span></span>
        </div>
        <span class="text-center text-caption text-text-3">Faites tourner le bracelet</span>
      </div>
    </section>

    <section class="mx-auto flex w-full max-w-6xl flex-col gap-8 px-5 py-14 lg:px-8 lg:py-20" aria-labelledby="titre-etapes">
      <div class="flex flex-col gap-2">
        <span class="text-caption font-semibold tracking-widest text-accent uppercase">Comment ça marche</span>
        <h2 id="titre-etapes" class="m-0 text-h2 font-bold tracking-tight lg:text-h1">Trois étapes, une seule fois.</h2>
      </div>
      <ol class="relative m-0 grid list-none gap-4 p-0 lg:grid-cols-3">
        <span class="pointer-events-none absolute top-12 right-[16%] left-[16%] hidden border-t-2 border-dashed border-line-strong lg:block" aria-hidden="true"></span>
        @for (etape of etapes; track etape.numero; let rang = $index) {
          <li class="carte-vivante relative flex flex-col gap-3 rounded-lg border border-line bg-surface p-6" [apparition]="rang * 120">
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
        @for (atout of atouts; track atout.titre; let rang = $index) {
          <li class="flex flex-col gap-2" [apparition]="rang * 90">
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
        @for (scenario of scenarios; track scenario.titre; let rang = $index) {
          <li class="carte-vivante flex flex-col gap-2 rounded-lg border border-line bg-surface p-6" [apparition]="rang * 120">
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
        @for (offre of offres; track offre.nom; let rang = $index) {
          <li class="carte-vivante flex flex-col gap-3 rounded-lg bg-surface p-6" [class]="offre.miseEnAvant ? 'border-2 border-accent shadow-e3' : 'border border-line'" [apparition]="rang * 90">
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
      <div class="halo relative flex flex-col gap-4 overflow-hidden rounded-xl border border-line bg-surface p-8 lg:p-12" apparition>
        <span class="text-caption font-semibold tracking-widest text-accent uppercase">Établissements scolaires</span>
        <h2 id="titre-ecoles" class="m-0 text-h2 font-bold tracking-tight lg:text-h1">L'offre École : le bracelet fourni par l'établissement.</h2>
        <p class="m-0 max-w-2xl text-body-lg text-text-2">4 000 FCFA par élève et par mois, sur convention. Inscription accompagnée des familles sur place.</p>
        <a class="inline-flex min-h-13 items-center justify-center rounded-md bg-primary px-6 text-body font-semibold text-white transition-[background-color,transform] duration-(--duration-fast) hover:bg-primary-hover active:scale-[.98] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent self-start" routerLink="/ecoles">Découvrir l'offre École</a>
      </div>
    </section>

    <!-- Appel final : les deux gestes qui suivent la visite, acheter et trouver où. -->
    <section class="mx-auto w-full max-w-6xl px-5 pb-20 lg:px-8" aria-labelledby="titre-appel">
      <div class="relative overflow-hidden rounded-xl bg-(image:--fond-hero) px-8 py-12 text-hero-fg shadow-e3 lg:px-14 lg:py-16" apparition>
        <div class="flex flex-col gap-6 lg:flex-row lg:items-center lg:justify-between">
          <div class="flex max-w-xl flex-col gap-3">
            <h2 id="titre-appel" class="m-0 text-h2 font-bold tracking-tight text-balance lg:text-h1">Un bracelet, et vous savez qu'on vous préviendra.</h2>
            <p class="m-0 text-body-lg text-hero-fg-2">Bracelet {{ prix }}, abonnement sans engagement, inscription accompagnée en point relais.</p>
          </div>
          <div class="flex flex-wrap gap-3">
            <a class="inline-flex min-h-13 items-center justify-center rounded-md bg-hero-btn px-6 text-body font-semibold text-hero-btn-fg transition-transform duration-(--duration-fast) hover:-translate-y-0.5 active:scale-[.98] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent" routerLink="/offres">Protéger mon enfant</a>
            <a class="inline-flex min-h-13 items-center justify-center rounded-md border-(length:--border-width-trait) border-current/30 px-6 text-body font-semibold transition-colors duration-(--duration-fast) hover:bg-white/10 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent" routerLink="/points-relais">Trouver un point relais</a>
          </div>
        </div>
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
