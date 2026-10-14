import { ChangeDetectionStrategy, Component, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { Router } from '@angular/router';

import { ClientConsole, RoleInterne, Session } from 'api';

import { erreurLisible } from '../commun/acces';
import { ENTREES } from './entrees';

/** Ce que propose la recherche : un écran, ou la fiche désignée par une référence. */
export interface Resultat {
  readonly libelle: string;
  readonly precision: string;
  /** Adresse et paramètres de l'écran ; pour un dossier KYC, la référence est d'abord résolue. */
  readonly lien: string;
  readonly parametres?: Record<string, string>;
  readonly referenceKyc?: string;
}

interface FormatReference {
  readonly role: RoleInterne;
  readonly motif: RegExp;
  readonly resultat: (ref: string) => Resultat;
}

/**
 * Références reconnues à leur forme. Chacune mène à l'écran qui la cherche avec ses propres contrôles et sa
 * propre journalisation : la recherche ne lit rien elle-même. L'ordre compte (FG-SIG avant FG-).
 */
const FORMATS: readonly FormatReference[] = [
  {
    role: 'FDS',
    motif: /^FG-SIG-\d{1,6}$/,
    resultat: (ref) => ({ libelle: $localize`:@@recherche.signalement:Signalement ${ref}:ref:`, precision: $localize`:@@nav.fds:Signalements`, lien: '/fds', parametres: { ref } }),
  },
  {
    role: 'SAV',
    motif: /^FG-\d{1,8}$/,
    resultat: (ref) => ({ libelle: $localize`:@@recherche.bracelet:Bracelet ${ref}:ref:`, precision: $localize`:@@nav.parc:Parc de bracelets`, lien: `/sav/parc/${ref}` }),
  },
  {
    role: 'SAV',
    motif: /^SAV-\d{1,6}$/,
    resultat: (ref) => ({ libelle: $localize`:@@recherche.ticket:Ticket ${ref}:ref:`, precision: $localize`:@@nav.muets:Bracelets muets`, lien: '/sav/muets', parametres: { ticket: ref } }),
  },
  {
    role: 'KYC',
    motif: /^KYC-\d{1,8}$/,
    resultat: (ref) => ({ libelle: $localize`:@@recherche.dossier:Dossier ${ref}:ref:`, precision: $localize`:@@nav.kyc:File KYC`, lien: '/kyc', referenceKyc: ref }),
  },
  {
    role: 'SUPPORT',
    motif: /^SUP-\d{1,6}$/,
    resultat: (ref) => ({ libelle: $localize`:@@recherche.demande:Demande ${ref}:ref:`, precision: $localize`:@@nav.tickets:Tickets support`, lien: '/support/tickets', parametres: { reference: ref } }),
  },
];

const EXEMPLES: Partial<Record<RoleInterne, string>> = {
  KYC: $localize`:@@recherche.exemple.kyc:un dossier KYC-20418`,
  SUPPORT: $localize`:@@recherche.exemple.support:une demande SUP-000012`,
  SAV: $localize`:@@recherche.exemple.sav:un FG-XXXX, un ticket SAV-000123`,
  FDS: $localize`:@@recherche.exemple.fds:un signalement FG-SIG-000412`,
};

function sansAccents(texte: string): string {
  return texte.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
}

/** Ce que la recherche propose pour une saisie, selon les rôles de l'agent. */
export function resultatsPour(saisie: string, roles: readonly string[]): Resultat[] {
  const texte = saisie.trim();
  const reference = texte.toUpperCase().replace(/\s+/g, '');
  const references = FORMATS.filter((f) => roles.includes(f.role)).find((f) => f.motif.test(reference));
  const ecrans = ENTREES.filter((e) => !e.role || roles.includes(e.role))
    .filter((e) => sansAccents(e.libelle).includes(sansAccents(texte)))
    .map((e) => ({ libelle: e.libelle, precision: $localize`:@@recherche.ecran:Écran`, lien: e.lien }));
  return [...(references ? [references.resultat(reference)] : []), ...ecrans];
}

/**
 * Recherche de la console (écran 56), ouverte par le champ de la barre du haut ou par Ctrl K. Elle trouve les
 * écrans de l'agent et reconnaît les références de ses seuls rôles ; elle ne cherche pas les personnes par
 * leur nom, ce qui élargirait l'accès aux données personnelles au-delà des files de chaque rôle.
 */
@Component({
  selector: 'app-recherche',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button type="button" class="flex h-10 w-full max-w-[520px] items-center gap-2 rounded-[10px] border border-line bg-surface px-3 text-left text-label font-medium text-text-3 hover:border-line-strong focus-visible:outline-2 focus-visible:outline-accent" (click)="ouvrir()" aria-keyshortcuts="Control+K">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" aria-hidden="true"><circle cx="11" cy="11" r="7"></circle><path d="M20 20l-4-4"></path></svg>
      <span class="truncate">{{ invite() }}</span>
      <kbd class="ml-auto rounded-[5px] border border-line-strong px-1.5 py-0.5 font-sans text-[11px] font-semibold">Ctrl K</kbd>
    </button>

    <dialog #fenetre class="m-auto mt-[12vh] w-[min(560px,calc(100vw-32px))] rounded-xl border border-line bg-surface p-0 text-text shadow-xl backdrop:bg-black/40" i18n-aria-label="@@recherche.titre" aria-label="Recherche" (close)="fermee()">
      <div class="flex items-center gap-2 border-b border-line px-4">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" aria-hidden="true" class="text-text-3"><circle cx="11" cy="11" r="7"></circle><path d="M20 20l-4-4"></path></svg>
        <input #champ class="h-13 min-w-0 flex-1 bg-transparent text-body outline-none placeholder:text-text-3" type="search" role="combobox" aria-autocomplete="list" aria-controls="recherche-resultats" [attr.aria-activedescendant]="resultats().length ? 'recherche-' + actif() : null" [attr.aria-expanded]="resultats().length > 0" [placeholder]="invite()" [value]="saisie()" (input)="saisir($event)" (keydown)="clavier($event)" i18n-aria-label="@@recherche.champ" aria-label="Rechercher" autocomplete="off" />
      </div>
      <ul id="recherche-resultats" class="m-0 flex max-h-[50vh] list-none flex-col overflow-auto p-2" role="listbox" i18n-aria-label="@@recherche.resultats" aria-label="Résultats">
        @for (r of resultats(); track r.libelle + r.lien; let i = $index) {
          <li [id]="'recherche-' + i" role="option" [attr.aria-selected]="i === actif()" class="flex cursor-pointer items-center justify-between gap-3 rounded-md px-3 py-2.5" [class.bg-accent-soft]="i === actif()" (click)="aller(r)" (mousemove)="actif.set(i)">
            <span class="text-label font-semibold">{{ r.libelle }}</span>
            <span class="text-caption text-text-3">{{ r.precision }}</span>
          </li>
        } @empty {
          <li class="px-3 py-4 text-label text-text-2" i18n="@@recherche.rien">Aucun écran ni aucune référence de votre rôle ne correspond.</li>
        }
      </ul>
      @if (erreur(); as message) {
        <p class="m-0 border-t border-line px-4 py-3 text-label font-medium text-danger" role="alert">{{ message }}</p>
      }
    </dialog>
  `,
  host: { class: 'contents', '(document:keydown)': 'raccourci($event)' },
})
export class Recherche {
  private readonly session = inject(Session);
  private readonly client = inject(ClientConsole);
  private readonly router = inject(Router);
  private readonly fenetre = viewChild.required<ElementRef<HTMLDialogElement>>('fenetre');
  private readonly champ = viewChild.required<ElementRef<HTMLInputElement>>('champ');

  protected readonly saisie = signal('');
  protected readonly actif = signal(0);
  protected readonly erreur = signal<string | null>(null);
  protected readonly resultats = computed(() => resultatsPour(this.saisie(), this.session.roles()));
  protected readonly invite = computed(() => {
    const exemples = this.session.roles().map((r) => EXEMPLES[r as RoleInterne]).filter((e): e is string => !!e);
    return [$localize`:@@recherche.invite:Rechercher un écran`, ...exemples].join(', ') + '…';
  });

  protected raccourci(evenement: KeyboardEvent): void {
    if ((evenement.ctrlKey || evenement.metaKey) && evenement.key.toLowerCase() === 'k') {
      evenement.preventDefault();
      this.ouvrir();
    }
  }

  protected ouvrir(): void {
    const fenetre = this.fenetre().nativeElement;
    if (fenetre.open) {
      return;
    }
    this.saisie.set('');
    this.actif.set(0);
    this.erreur.set(null);
    fenetre.showModal();
    this.champ().nativeElement.focus();
  }

  protected fermee(): void {
    this.erreur.set(null);
  }

  protected saisir(evenement: Event): void {
    this.saisie.set((evenement.target as HTMLInputElement).value);
    this.actif.set(0);
    this.erreur.set(null);
  }

  protected clavier(evenement: KeyboardEvent): void {
    const nombre = this.resultats().length;
    if (evenement.key === 'ArrowDown' && nombre) {
      evenement.preventDefault();
      this.actif.set((this.actif() + 1) % nombre);
    } else if (evenement.key === 'ArrowUp' && nombre) {
      evenement.preventDefault();
      this.actif.set((this.actif() - 1 + nombre) % nombre);
    } else if (evenement.key === 'Enter' && nombre) {
      evenement.preventDefault();
      this.aller(this.resultats()[this.actif()]);
    }
  }

  protected aller(resultat: Resultat): void {
    if (resultat.referenceKyc) {
      this.client.dossierKycParReference(resultat.referenceKyc).subscribe({
        next: ({ id }) => this.naviguer(`/kyc/${id}`),
        error: (cause: unknown) => this.erreur.set(erreurLisible(cause).message),
      });
      return;
    }
    this.naviguer(resultat.lien, resultat.parametres);
  }

  private naviguer(lien: string, parametres?: Record<string, string>): void {
    this.fenetre().nativeElement.close();
    void this.router.navigate([lien], parametres ? { queryParams: parametres } : {});
  }
}
