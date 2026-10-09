import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Observable } from 'rxjs';

import { CampagneOta, ClientOta, StatutCampagneOta, VagueOta } from 'api';
import { FgBadge, FgBanniere, FgBouton, FgChamp, FgFeuille, FgSquelette, TonBadge } from 'ui';

import { erreurLisible, estRefus } from '../commun/acces';

const NOMBRE = new Intl.NumberFormat('fr-FR');

const STATUTS: Record<StatutCampagneOta, { libelle: string; ton: TonBadge }> = {
  PREPAREE: { libelle: $localize`:@@ota.statut.preparee:Préparée`, ton: 'neutre' },
  EN_COURS: { libelle: $localize`:@@ota.statut.enCours:En cours`, ton: 'accent' },
  EN_PAUSE: { libelle: $localize`:@@ota.statut.enPause:En pause`, ton: 'attention' },
  TERMINEE: { libelle: $localize`:@@ota.statut.terminee:Terminée`, ton: 'succes' },
};

/** Taille d'une image en kilo-octets, comme l'écrit le poste de publication. */
export function taille(octets: number): string {
  return `${NOMBRE.format(Math.round(octets / 1024))} Ko`;
}

/** Ce qu'il faut savoir d'une vague en une ligne : où elle en est, et ce qui reste à faire. */
export function situation(vague: VagueOta): string {
  switch (vague.etat) {
    case 'TERMINEE':
      return vague.cibles === 0 ? $localize`:@@ota.vague.sansCible:Terminée · aucun bracelet à viser` : $localize`:@@ota.vague.terminee:Terminée`;
    case 'EN_COURS':
      return $localize`:@@ota.vague.attente:${vague.cibles - vague.installes}:nombre: en attente d'installation`;
    case 'PRETE':
      return $localize`:@@ota.vague.prete:Prête · déclenchement manuel`;
    default:
      return $localize`:@@ota.vague.planifiee:Planifiée`;
  }
}

/**
 * Campagnes OTA (écran 68, US-PAR-013). L'agent enregistre une image signée par la clé de publication, puis
 * lance les vagues une à une : chaque lancement informe les parents des bracelets visés. Une image dont la
 * signature ne se vérifie pas n'entre jamais en campagne.
 */
@Component({
  selector: 'app-ota',
  imports: [ReactiveFormsModule, FgBadge, FgBanniere, FgBouton, FgChamp, FgFeuille, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex flex-wrap items-center justify-between gap-3">
      <h1 class="m-0 text-titre-ecran font-bold tracking-tight" i18n="@@ota.titre">Campagnes OTA</h1>
      <button fg-button variante="secondary" type="button" (click)="ouvrir()" i18n="@@ota.nouvelle">Nouvelle image</button>
    </div>
    @if (erreur(); as message) {
      <fg-banner ton="erreur">{{ message }}</fg-banner>
    }
    @if (campagnes(); as liste) {
      @for (campagne of liste; track campagne.id) {
        <section class="flex max-w-4xl flex-col gap-3.5 rounded-lg border border-line bg-surface p-6" [attr.aria-label]="titre(campagne)">
          <div class="flex flex-wrap items-center justify-between gap-3">
            <h2 class="m-0 text-h3 font-semibold">{{ titre(campagne) }}</h2>
            <fg-badge [ton]="statuts[campagne.statut].ton">{{ statuts[campagne.statut].libelle }}</fg-badge>
          </div>
          <p class="m-0 text-label text-text-2">
            <ng-container i18n="@@ota.image">Image signée (ECDSA P-256)</ng-container> · {{ tailleDe(campagne) }}@if (campagne.note) { · {{ campagne.note }}}. <ng-container i18n="@@ota.parents">Les parents sont informés avant l'installation.</ng-container>
          </p>
          <ol class="m-0 flex list-none flex-col gap-2 p-0">
            @for (vague of campagne.vagues; track vague.numero) {
              <li class="grid grid-cols-[220px_1fr_130px] items-center gap-4 rounded-md bg-bg px-4 py-3 text-label">
                <strong class="font-semibold"><ng-container i18n="@@ota.vague">Vague</ng-container> {{ vague.numero }} · {{ vague.pourcentage }} %</strong>
                <div class="flex flex-col gap-1.5">
                  <div class="h-1.5 overflow-hidden rounded-full bg-surface-2" role="progressbar" aria-valuemin="0" [attr.aria-valuemax]="vague.cibles" [attr.aria-valuenow]="vague.installes" [attr.aria-label]="avancement(vague)">
                    <div class="h-full rounded-full" [class]="vague.etat === 'TERMINEE' ? 'bg-success' : 'bg-accent'" [style.width.%]="part(vague)"></div>
                  </div>
                  <span class="text-caption text-text-2">{{ situationDe(vague) }}</span>
                </div>
                <span class="text-right text-text-2 tabular-nums">{{ vague.installes }} / {{ vague.cibles }}</span>
              </li>
            }
          </ol>
          <div class="flex flex-wrap gap-2">
            @if (prochaine(campagne); as vague) {
              <button fg-button type="button" [chargement]="enCours() === campagne.id" [disabled]="campagne.statut === 'EN_PAUSE'" (click)="lancer(campagne)"><ng-container i18n="@@ota.lancer">Lancer la vague</ng-container> {{ vague.numero }} ({{ vague.pourcentage }} %)</button>
            }
            @if (campagne.statut === 'EN_COURS') {
              <button fg-button variante="secondary" type="button" [chargement]="enCours() === campagne.id" (click)="pause(campagne)" i18n="@@ota.pause">Mettre en pause</button>
            } @else if (campagne.statut === 'EN_PAUSE') {
              <button fg-button variante="secondary" type="button" [chargement]="enCours() === campagne.id" (click)="reprise(campagne)" i18n="@@ota.reprendre">Reprendre</button>
            }
          </div>
        </section>
      } @empty {
        <p class="m-0 max-w-4xl rounded-lg border border-line bg-surface p-5 text-body text-text-2" i18n="@@ota.vide">Aucune campagne. Enregistrez une image signée pour préparer la première.</p>
      }
    } @else if (!erreur()) {
      <fg-skeleton forme="carte" />
    }

    <fg-sheet i18n-titre="@@ota.nouvelle" titre="Nouvelle image" [ouverte]="feuille()" (fermee)="feuille.set(false)">
      <form class="flex flex-col gap-3" (submit)="$event.preventDefault(); preparer()">
        <p class="m-0 text-label text-text-2" i18n="@@ota.feuille.texte">Reportez le manifeste produit par le poste de publication. La plateforme vérifie sa signature : une image non signée est refusée.</p>
        <div class="grid grid-cols-2 gap-3">
          <fg-input [formControl]="version" i18n-libelle="@@ota.version" libelle="Version" [longueurMax]="11" />
          <fg-input [formControl]="octets" i18n-libelle="@@ota.taille" libelle="Taille (octets)" [longueurMax]="8" />
        </div>
        <fg-input [formControl]="url" i18n-libelle="@@ota.url" libelle="Adresse de l'image (https)" [longueurMax]="300" />
        <fg-input [formControl]="sha256" i18n-libelle="@@ota.sha" libelle="Empreinte SHA-256" [longueurMax]="64" />
        <fg-input [formControl]="signature" i18n-libelle="@@ota.signature" libelle="Signature du manifeste" [longueurMax]="128" />
        <fg-input [formControl]="note" i18n-libelle="@@ota.note" libelle="Objet de la mise à jour" [longueurMax]="200" />
        @if (erreurSaisie(); as message) {
          <fg-banner ton="erreur">{{ message }}</fg-banner>
        }
        <button fg-button type="submit" [chargement]="envoi()" i18n="@@ota.enregistrer">Vérifier et enregistrer</button>
      </form>
    </fg-sheet>
  `,
  host: { class: 'contents' },
})
export class CampagnesOta {
  private readonly client = inject(ClientOta);
  private readonly router = inject(Router);

  protected readonly statuts = STATUTS;
  protected readonly campagnes = signal<readonly CampagneOta[] | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly enCours = signal<string | null>(null);
  protected readonly feuille = signal(false);
  protected readonly envoi = signal(false);
  protected readonly erreurSaisie = signal<string | null>(null);

  protected readonly version = new FormControl('', { nonNullable: true });
  protected readonly octets = new FormControl('', { nonNullable: true });
  protected readonly url = new FormControl('', { nonNullable: true });
  protected readonly sha256 = new FormControl('', { nonNullable: true });
  protected readonly signature = new FormControl('', { nonNullable: true });
  protected readonly note = new FormControl('', { nonNullable: true });

  constructor() {
    this.client.campagnes().subscribe({ next: (campagnes) => this.campagnes.set(campagnes), error: (cause: unknown) => this.echec(cause) });
  }

  protected titre(campagne: CampagneOta): string {
    return $localize`:@@ota.campagne:Campagne OTA ${campagne.version}:version:`;
  }

  protected tailleDe(campagne: CampagneOta): string {
    return taille(campagne.tailleOctets);
  }

  protected situationDe(vague: VagueOta): string {
    return situation(vague);
  }

  protected avancement(vague: VagueOta): string {
    return $localize`:@@ota.avancement:Vague ${vague.numero}:numero: : ${vague.installes}:installes: bracelets à jour sur ${vague.cibles}:cibles:`;
  }

  protected part(vague: VagueOta): number {
    return vague.cibles === 0 ? (vague.etat === 'TERMINEE' ? 100 : 0) : Math.round((100 * vague.installes) / vague.cibles);
  }

  /** Vague qui attend son déclenchement, s'il en reste une. */
  protected prochaine(campagne: CampagneOta): VagueOta | null {
    return campagne.statut === 'TERMINEE' ? null : (campagne.vagues.find((vague) => vague.etat === 'PRETE') ?? null);
  }

  protected lancer(campagne: CampagneOta): void {
    this.agir(campagne, this.client.lancerLaVagueSuivante(campagne.id));
  }

  protected pause(campagne: CampagneOta): void {
    this.agir(campagne, this.client.mettreEnPause(campagne.id));
  }

  protected reprise(campagne: CampagneOta): void {
    this.agir(campagne, this.client.reprendre(campagne.id));
  }

  protected ouvrir(): void {
    this.erreurSaisie.set(null);
    this.feuille.set(true);
  }

  protected preparer(): void {
    const version = this.version.value.trim();
    const tailleOctets = Number(this.octets.value.trim());
    const sha256 = this.sha256.value.trim();
    if (!/^\d{1,3}\.\d{1,3}\.\d{1,3}$/.test(version)) {
      this.erreurSaisie.set($localize`:@@ota.erreur.version:La version s'écrit en trois nombres, par exemple 2.4.2.`);
      return;
    }
    if (!Number.isInteger(tailleOctets) || tailleOctets <= 0) {
      this.erreurSaisie.set($localize`:@@ota.erreur.taille:Indiquez la taille de l'image en octets.`);
      return;
    }
    if (!this.url.value.trim().startsWith('https://')) {
      this.erreurSaisie.set($localize`:@@ota.erreur.url:L'image doit être servie en https.`);
      return;
    }
    if (!/^[0-9a-fA-F]{64}$/.test(sha256)) {
      this.erreurSaisie.set($localize`:@@ota.erreur.sha:L'empreinte SHA-256 compte 64 chiffres hexadécimaux.`);
      return;
    }
    if (!this.signature.value.trim()) {
      this.erreurSaisie.set($localize`:@@ota.erreur.signature:Reportez la signature du manifeste.`);
      return;
    }
    this.erreurSaisie.set(null);
    this.envoi.set(true);
    this.client
      .preparer({ version, urlImage: this.url.value.trim(), tailleOctets, sha256, signature: this.signature.value.trim(), note: this.note.value.trim() || null })
      .subscribe({
        next: (campagne) => {
          this.envoi.set(false);
          this.feuille.set(false);
          this.campagnes.update((liste) => [campagne, ...(liste ?? [])]);
          for (const champ of [this.version, this.octets, this.url, this.sha256, this.signature, this.note]) {
            champ.reset();
          }
        },
        error: (cause: unknown) => {
          this.envoi.set(false);
          if (estRefus(cause)) {
            void this.router.navigate(['/refuse']);
            return;
          }
          this.erreurSaisie.set(erreurLisible(cause).message);
        },
      });
  }

  private agir(campagne: CampagneOta, appel: Observable<CampagneOta>): void {
    this.erreur.set(null);
    this.enCours.set(campagne.id);
    appel.subscribe({
      next: (maj) => {
        this.enCours.set(null);
        this.campagnes.update((liste) => (liste ?? []).map((c) => (c.id === maj.id ? maj : c)));
      },
      error: (cause: unknown) => this.echec(cause),
    });
  }

  private echec(cause: unknown): void {
    this.enCours.set(null);
    if (estRefus(cause)) {
      void this.router.navigate(['/refuse']);
      return;
    }
    this.erreur.set(erreurLisible(cause).message);
  }
}
