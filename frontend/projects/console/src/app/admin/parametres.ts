import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { forkJoin } from 'rxjs';

import { ClientAbonnements, ClientPasserelles, Offre, Passerelle } from 'api';
import { FgBadge, FgBanniere, FgBouton, FgChamp, FgFeuille, FgSquelette } from 'ui';

import { erreurLisible, estRefus } from '../commun/acces';

const EUI = /^[0-9A-Fa-f]{16}$/;
const MONTANT = new Intl.NumberFormat('fr-FR');

/** Lit un nombre saisi avec une virgule ou un point ; `null` s'il n'en est pas un. */
export function nombre(saisie: string): number | null {
  const texte = saisie.trim().replace(',', '.');
  return texte !== '' && Number.isFinite(Number(texte)) ? Number(texte) : null;
}

/**
 * Paramétrage (écran 74, US-SYS-004) : tarifs en vigueur, passerelles LoRaWAN des écoles partenaires et
 * gabarit du SMS d'alerte. Les tarifs et le gabarit se lisent ici ; seul le registre des passerelles s'y modifie.
 */
@Component({
  selector: 'app-parametres',
  imports: [ReactiveFormsModule, FgBadge, FgBanniere, FgBouton, FgChamp, FgFeuille, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 class="m-0 text-titre-ecran font-bold tracking-tight" i18n="@@parametres.titre">Paramétrage</h1>
    @if (erreur(); as message) {
      <fg-banner ton="erreur">{{ message }}</fg-banner>
    }
    @if (charge()) {
      <div class="grid grid-cols-1 items-start gap-4 lg:grid-cols-2">
        <section class="flex flex-col gap-2" aria-labelledby="titre-tarifs">
          <h2 id="titre-tarifs" class="m-0 text-h3 font-semibold" i18n="@@parametres.tarifs">Tarifs mensuels</h2>
          <dl class="m-0 flex flex-col rounded-lg border border-line bg-surface">
            @for (offre of offres(); track offre.code) {
              <div class="flex min-h-11 items-center justify-between gap-4 border-b border-line px-3.5 text-label font-medium last:border-b-0">
                <dt>{{ offre.libelle }}</dt>
                <dd class="m-0 text-text-2 tabular-nums">{{ prix(offre.prixFcfa) }} FCFA</dd>
              </div>
            }
            <div class="flex min-h-11 items-center justify-between gap-4 px-3.5 text-label font-medium">
              <dt i18n="@@parametres.passerelles">Passerelles LoRaWAN</dt>
              <dd class="m-0 text-text-2 tabular-nums">{{ resume() }}</dd>
            </div>
          </dl>

          <h2 class="m-0 mt-2 text-h3 font-semibold" i18n="@@parametres.gabarit">Gabarit SMS · SOS</h2>
          <p class="m-0 rounded-lg bg-surface-2 px-3.5 py-3 font-mono text-label text-text-2">{{ gabarit }}</p>
          <span class="text-caption text-text-3" i18n="@@parametres.gabarit.controle">Contrôle : aucune donnée de santé ni coordonnée GPS autorisée dans un SMS.</span>
        </section>

        <section class="flex flex-col gap-2" aria-labelledby="titre-passerelles">
          <div class="flex flex-wrap items-center justify-between gap-3">
            <h2 id="titre-passerelles" class="m-0 text-h3 font-semibold" i18n="@@parametres.passerelles">Passerelles LoRaWAN</h2>
            <button fg-button variante="secondary" type="button" (click)="ouvrir()" i18n="@@parametres.ajouter">Enregistrer une passerelle</button>
          </div>
          <span class="text-label text-text-2" i18n="@@parametres.passerelles.texte">Un bracelet entendu par une passerelle est tenu pour présent dans l'enceinte de l'établissement, même sans réseau mobile.</span>
          @if (passerelles().length === 0) {
            <p class="m-0 rounded-lg border border-line bg-surface p-4 text-body text-text-2" i18n="@@parametres.passerelles.vide">Aucune passerelle enregistrée.</p>
          } @else {
            <ul class="m-0 flex list-none flex-col rounded-lg border border-line bg-surface p-0">
              @for (passerelle of passerelles(); track passerelle.id) {
                <li class="flex flex-wrap items-center gap-3 border-b border-line px-3.5 py-3 last:border-b-0">
                  <div class="flex min-w-0 flex-1 flex-col gap-0.5">
                    <strong class="truncate text-label font-semibold">{{ passerelle.etablissement }}</strong>
                    <span class="font-mono text-caption text-text-2">{{ passerelle.eui }} · <ng-container i18n="@@parametres.rayon">rayon {{ passerelle.rayonM }} m</ng-container></span>
                  </div>
                  <fg-badge [ton]="passerelle.enLigne ? 'succes' : 'neutre'">{{ passerelle.enLigne ? enLigne : horsLigne }}</fg-badge>
                  <button fg-button variante="danger" type="button" [chargement]="retrait() === passerelle.id" (click)="retirer(passerelle)" i18n="@@parametres.retirer">Retirer</button>
                </li>
              }
            </ul>
          }
        </section>
      </div>
    } @else if (!erreur()) {
      <fg-skeleton forme="carte" />
      <fg-skeleton forme="carte" />
    }

    <fg-sheet i18n-titre="@@parametres.ajouter" titre="Enregistrer une passerelle" [ouverte]="feuille()" (fermee)="feuille.set(false)">
      <form class="flex flex-col gap-3" (submit)="$event.preventDefault(); installer()">
        <fg-input [formControl]="etablissement" i18n-libelle="@@parametres.etablissement" libelle="Établissement" [longueurMax]="80" />
        <fg-input [formControl]="eui" i18n-libelle="@@parametres.eui" libelle="Identifiant de la passerelle (EUI)" i18n-aide="@@parametres.eui.aide" aide="16 chiffres hexadécimaux, inscrits sur l'étiquette de la passerelle." [longueurMax]="16" />
        <div class="grid grid-cols-2 gap-3">
          <fg-input [formControl]="latitude" i18n-libelle="@@parametres.latitude" libelle="Latitude" [longueurMax]="12" />
          <fg-input [formControl]="longitude" i18n-libelle="@@parametres.longitude" libelle="Longitude" [longueurMax]="12" />
        </div>
        <fg-input [formControl]="rayon" i18n-libelle="@@parametres.rayonEnceinte" libelle="Rayon de l'enceinte (m)" i18n-aide="@@parametres.rayon.aide" aide="Entre 20 et 2 000 mètres autour du centre." [longueurMax]="4" />
        @if (erreurSaisie(); as message) {
          <fg-banner ton="erreur">{{ message }}</fg-banner>
        }
        <button fg-button type="submit" [chargement]="enCours()" i18n="@@parametres.enregistrer">Enregistrer</button>
      </form>
    </fg-sheet>
  `,
  host: { class: 'contents' },
})
export class Parametres {
  private readonly client = inject(ClientPasserelles);
  private readonly router = inject(Router);

  protected readonly charge = signal(false);
  protected readonly offres = signal<readonly Offre[]>([]);
  protected readonly passerelles = signal<readonly Passerelle[]>([]);
  protected readonly erreur = signal<string | null>(null);
  protected readonly feuille = signal(false);
  protected readonly erreurSaisie = signal<string | null>(null);
  protected readonly enCours = signal(false);
  protected readonly retrait = signal<string | null>(null);

  protected readonly etablissement = new FormControl('', { nonNullable: true });
  protected readonly eui = new FormControl('', { nonNullable: true });
  protected readonly latitude = new FormControl('', { nonNullable: true });
  protected readonly longitude = new FormControl('', { nonNullable: true });
  protected readonly rayon = new FormControl('150', { nonNullable: true });

  protected readonly enLigne = $localize`:@@parametres.enLigne:En ligne`;
  protected readonly horsLigne = $localize`:@@parametres.horsLigne:Hors ligne`;
  /** Texte envoyé par le serveur pour un SOS, donné ici pour contrôle : il ne se modifie pas depuis la console. */
  protected readonly gabarit = $localize`:@@parametres.gabarit.texte:FasoGuardian : ALERTE SOS. Le bouton SOS du bracelet de votre enfant a été déclenché. Ouvrez l'application.`;

  protected readonly resume = computed(() => {
    const liste = this.passerelles();
    if (liste.length === 0) {
      return $localize`:@@parametres.resume.aucune:aucune`;
    }
    const enLigne = liste.filter((p) => p.enLigne).length;
    return $localize`:@@parametres.resume:${liste.length}:nombre: installée(s) · ${enLigne}:enLigne: en ligne`;
  });

  constructor() {
    forkJoin({ offres: inject(ClientAbonnements).offres(), passerelles: this.client.enService() }).subscribe({
      next: ({ offres, passerelles }) => {
        this.offres.set(offres);
        this.passerelles.set(passerelles);
        this.charge.set(true);
      },
      error: (cause: unknown) => this.echec(cause),
    });
  }

  protected prix(fcfa: number): string {
    return MONTANT.format(fcfa);
  }

  protected ouvrir(): void {
    this.erreurSaisie.set(null);
    this.feuille.set(true);
  }

  protected installer(): void {
    const latitude = nombre(this.latitude.value);
    const longitude = nombre(this.longitude.value);
    const rayonM = nombre(this.rayon.value);
    const eui = this.eui.value.trim().toUpperCase();
    const etablissement = this.etablissement.value.trim();
    if (!etablissement) {
      this.erreurSaisie.set($localize`:@@parametres.erreur.etablissement:Indiquez l'établissement que couvre la passerelle.`);
      return;
    }
    if (!EUI.test(eui)) {
      this.erreurSaisie.set($localize`:@@parametres.erreur.eui:L'identifiant compte 16 chiffres hexadécimaux (0 à 9, A à F).`);
      return;
    }
    if (latitude === null || longitude === null || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) {
      this.erreurSaisie.set($localize`:@@parametres.erreur.position:Saisissez la latitude et la longitude du centre de l'enceinte, en degrés décimaux.`);
      return;
    }
    if (rayonM === null || !Number.isInteger(rayonM) || rayonM < 20 || rayonM > 2000) {
      this.erreurSaisie.set($localize`:@@parametres.erreur.rayon:Le rayon est un nombre entier de mètres, entre 20 et 2 000.`);
      return;
    }
    this.erreurSaisie.set(null);
    this.enCours.set(true);
    this.client.installer({ eui, etablissement, latitude, longitude, rayonM }).subscribe({
      next: (passerelle) => {
        this.enCours.set(false);
        this.feuille.set(false);
        this.passerelles.update((liste) => [...liste, passerelle].sort((a, b) => a.etablissement.localeCompare(b.etablissement, 'fr')));
        for (const champ of [this.etablissement, this.eui, this.latitude, this.longitude]) {
          champ.reset();
        }
      },
      error: (cause: unknown) => {
        this.enCours.set(false);
        if (estRefus(cause)) {
          void this.router.navigate(['/refuse']);
          return;
        }
        this.erreurSaisie.set(erreurLisible(cause).message);
      },
    });
  }

  protected retirer(passerelle: Passerelle): void {
    this.erreur.set(null);
    this.retrait.set(passerelle.id);
    this.client.retirer(passerelle.id).subscribe({
      next: () => {
        this.retrait.set(null);
        this.passerelles.update((liste) => liste.filter((p) => p.id !== passerelle.id));
      },
      error: (cause: unknown) => this.echec(cause),
    });
  }

  private echec(cause: unknown): void {
    this.retrait.set(null);
    if (estRefus(cause)) {
      void this.router.navigate(['/refuse']);
      return;
    }
    this.erreur.set(erreurLisible(cause).message);
  }
}
