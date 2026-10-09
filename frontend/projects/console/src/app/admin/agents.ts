import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Observable } from 'rxjs';

import { AgentInterne, ClientAgents, RoleInterne, Session } from 'api';
import { FgBadge, FgBanniere, FgBouton, FgChamp, FgFeuille, FgSquelette } from 'ui';

import { LIBELLES_ROLES, erreurLisible, estRefus } from '../commun/acces';

const ROLES: readonly RoleInterne[] = ['KYC', 'SUPPORT', 'SAV', 'ADMIN', 'FDS'];

type Acces = 'complet' | 'lecture' | 'aucun';

/**
 * Matrice des habilitations (FG-DOC-06, tableau 17) telle que le serveur l'applique : une ligne par
 * permission, une colonne par rôle. Elle documente le cloisonnement ; elle ne se modifie pas d'ici.
 */
const MATRICE: readonly { permission: string; acces: Record<RoleInterne, Acces> }[] = [
  { permission: $localize`:@@matrice.kyc:Instruire un dossier KYC`, acces: { KYC: 'complet', SUPPORT: 'aucun', SAV: 'aucun', ADMIN: 'aucun', FDS: 'aucun' } },
  { permission: $localize`:@@matrice.parc:Suivre le parc et les tickets de maintenance`, acces: { KYC: 'aucun', SUPPORT: 'aucun', SAV: 'complet', ADMIN: 'aucun', FDS: 'aucun' } },
  { permission: $localize`:@@matrice.agents:Gérer les agents et leurs rôles`, acces: { KYC: 'aucun', SUPPORT: 'aucun', SAV: 'aucun', ADMIN: 'complet', FDS: 'aucun' } },
  { permission: $localize`:@@matrice.audit:Consulter le journal d'audit`, acces: { KYC: 'aucun', SUPPORT: 'aucun', SAV: 'aucun', ADMIN: 'lecture', FDS: 'aucun' } },
  { permission: $localize`:@@matrice.conformite:Exécuter les effacements, produire le rapport de conformité`, acces: { KYC: 'aucun', SUPPORT: 'aucun', SAV: 'aucun', ADMIN: 'complet', FDS: 'aucun' } },
  { permission: $localize`:@@matrice.positions:Voir la position ou la fiche santé d'un enfant`, acces: { KYC: 'aucun', SUPPORT: 'aucun', SAV: 'aucun', ADMIN: 'aucun', FDS: 'aucun' } },
];

const SYMBOLES: Record<Acces, { signe: string; classe: string; libelle: string }> = {
  complet: { signe: '●', classe: 'text-success', libelle: $localize`:@@matrice.complet:accès complet` },
  lecture: { signe: '◐', classe: 'text-accent', libelle: $localize`:@@matrice.lecture:lecture seule` },
  aucun: { signe: '—', classe: 'text-text-3', libelle: $localize`:@@matrice.aucun:aucun accès` },
};

/**
 * Agents et rôles (écran 69, US-ADM-001) : matrice des habilitations, agents et leur périmètre. Créer un
 * agent, changer ses rôles ou le suspendre est réservé à l'administrateur, dont la session est déjà à deux
 * facteurs, et chaque action est journalisée.
 */
@Component({
  selector: 'app-agents',
  imports: [ReactiveFormsModule, FgBadge, FgBanniere, FgBouton, FgChamp, FgFeuille, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex flex-wrap items-center justify-between gap-3">
      <h1 class="m-0 text-titre-ecran font-bold tracking-tight" i18n="@@agents.titre">Agents et rôles</h1>
      <button fg-button type="button" (click)="ouvrirCreation()" i18n="@@agents.ajouter">Ajouter un agent</button>
    </div>
    @if (erreur(); as message) {
      <fg-banner ton="erreur">{{ message }}</fg-banner>
    }

    <section class="flex flex-col gap-2" aria-labelledby="titre-agents">
      <h2 id="titre-agents" class="m-0 text-h3 font-semibold" i18n="@@agents.liste">Agents</h2>
      @if (agents(); as liste) {
        <div class="overflow-hidden rounded-lg border border-line bg-surface" role="table" i18n-aria-label="@@agents.liste" aria-label="Agents">
          <div class="grid h-10 grid-cols-[1.4fr_1.6fr_1fr_260px] items-center bg-surface-2 px-4 text-caption font-semibold text-text-2" role="row">
            <span role="columnheader" i18n="@@agents.col.identifiant">Identifiant</span>
            <span role="columnheader" i18n="@@agents.col.roles">Rôles</span>
            <span role="columnheader" i18n="@@agents.col.etat">État</span>
            <span role="columnheader"></span>
          </div>
          @for (agent of liste; track agent.id) {
            <div class="grid min-h-12 grid-cols-[1.4fr_1.6fr_1fr_260px] items-center border-t border-line px-4 text-label" role="row">
              <span class="truncate font-medium" role="cell">{{ agent.identifiant }}</span>
              <span class="flex flex-wrap gap-1.5" role="cell">
                @for (role of agent.roles; track role) {
                  <fg-badge ton="neutre">{{ libelles[role] }}</fg-badge>
                }
              </span>
              <span role="cell">
                @if (agent.suspendu) {
                  <fg-badge ton="attention" i18n="@@agents.suspendu">Suspendu</fg-badge>
                } @else if (!agent.secondFacteurActif) {
                  <fg-badge ton="neutre" i18n="@@agents.aActiver">Second facteur à activer</fg-badge>
                } @else {
                  <fg-badge ton="succes" i18n="@@agents.actif">Actif</fg-badge>
                }
              </span>
              <span class="flex justify-end gap-2" role="cell">
                @if (agent.id === moi) {
                  <span class="text-caption text-text-3" i18n="@@agents.moi">Votre compte</span>
                } @else {
                  <button fg-button variante="secondary" taille="sm" type="button" (click)="ouvrirRoles(agent)" i18n="@@agents.roles">Rôles</button>
                  @if (agent.suspendu) {
                    <button fg-button variante="secondary" taille="sm" type="button" (click)="basculer(agent)" i18n="@@agents.retablir">Rétablir</button>
                  } @else {
                    <button fg-button variante="danger" taille="sm" type="button" (click)="basculer(agent)" i18n="@@agents.suspendre">Suspendre</button>
                  }
                }
              </span>
            </div>
          }
        </div>
      } @else if (!erreur()) {
        <fg-skeleton forme="carte" />
      }
    </section>

    <section class="flex flex-col gap-2" aria-labelledby="titre-matrice">
      <h2 id="titre-matrice" class="m-0 text-h3 font-semibold" i18n="@@agents.matrice">Matrice des habilitations</h2>
      <div class="overflow-hidden rounded-lg border border-line bg-surface" role="table" i18n-aria-label="@@agents.matrice" aria-label="Matrice des habilitations">
        <div class="grid h-10 grid-cols-[2.2fr_repeat(5,1fr)] items-center bg-surface-2 px-4 text-caption font-semibold text-text-2" role="row">
          <span role="columnheader" i18n="@@matrice.permission">Permission</span>
          @for (role of roles; track role) {
            <span role="columnheader">{{ libelles[role] }}</span>
          }
        </div>
        @for (ligne of matrice; track ligne.permission) {
          <div class="grid min-h-11 grid-cols-[2.2fr_repeat(5,1fr)] items-center border-t border-line px-4 text-label font-medium" role="row">
            <span role="cell">{{ ligne.permission }}</span>
            @for (role of roles; track role) {
              <span class="font-bold" role="cell" [class]="symboles[ligne.acces[role]].classe" [attr.aria-label]="symboles[ligne.acces[role]].libelle">{{ symboles[ligne.acces[role]].signe }}</span>
            }
          </div>
        }
      </div>
      <span class="text-caption text-text-3" i18n="@@matrice.legende">● accès complet · ◐ lecture seule · — aucun. Aucun rôle interne ne voit la position ni la fiche santé d'un enfant. Tout changement de rôle est journalisé et ferme les sessions de l'agent.</span>
    </section>

    <fg-sheet [titre]="titreFeuille()" [ouverte]="feuille() !== null" (fermee)="feuille.set(null)">
      @if (feuille() === 'creation') {
        <fg-input [formControl]="identifiant" i18n-libelle="@@agents.col.identifiant" libelle="Identifiant" autocomplete="off" [longueurMax]="64" i18n-aide="@@agents.identifiant.aide" aide="Lettres minuscules, chiffres, points et tirets." />
        <fg-input [formControl]="motDePasse" type="password" i18n-libelle="@@agents.mdp" libelle="Mot de passe provisoire" autocomplete="new-password" i18n-aide="@@agents.mdp.aide" aide="12 caractères au moins. L'agent activera son second facteur à sa première connexion." />
      }
      <fieldset class="m-0 flex flex-col gap-2 border-0 p-0">
        <legend class="pb-2 text-label font-semibold" i18n="@@agents.col.roles">Rôles</legend>
        @for (role of roles; track role) {
          <label class="flex min-h-11 cursor-pointer items-center gap-3 rounded-md border px-3 text-label font-medium focus-within:outline-2 focus-within:outline-accent" [class]="choisis().includes(role) ? 'border-accent bg-accent-soft' : 'border-line'">
            <input class="sr-only" type="checkbox" [checked]="choisis().includes(role)" (change)="cocher(role)" />{{ libelles[role] }}
          </label>
        }
      </fieldset>
      @if (erreurFeuille(); as message) {
        <fg-banner ton="erreur">{{ message }}</fg-banner>
      }
      <button fg-button type="button" [chargement]="enCours()" (click)="valider()">{{ feuille() === 'creation' ? libelleCreer : libelleEnregistrer }}</button>
      <button fg-button variante="ghost" type="button" (click)="feuille.set(null)" i18n="@@commun.annuler">Annuler</button>
    </fg-sheet>
  `,
  host: { class: 'contents' },
})
export class Agents {
  private readonly client = inject(ClientAgents);
  private readonly router = inject(Router);

  protected readonly moi = inject(Session).identifiant();
  protected readonly roles = ROLES;
  protected readonly libelles = LIBELLES_ROLES;
  protected readonly matrice = MATRICE;
  protected readonly symboles = SYMBOLES;
  protected readonly libelleCreer = $localize`:@@agents.creer:Créer l'agent`;
  protected readonly libelleEnregistrer = $localize`:@@agents.enregistrer:Enregistrer les rôles`;

  protected readonly agents = signal<readonly AgentInterne[] | null>(null);
  protected readonly feuille = signal<'creation' | 'roles' | null>(null);
  protected readonly titreFeuille = signal('');
  protected readonly choisis = signal<readonly RoleInterne[]>([]);
  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly erreurFeuille = signal<string | null>(null);

  protected readonly identifiant = new FormControl('', { nonNullable: true });
  protected readonly motDePasse = new FormControl('', { nonNullable: true });

  private vise: AgentInterne | null = null;

  constructor() {
    this.charger();
  }

  protected ouvrirCreation(): void {
    this.vise = null;
    this.identifiant.setValue('');
    this.motDePasse.setValue('');
    this.choisis.set([]);
    this.erreurFeuille.set(null);
    this.titreFeuille.set($localize`:@@agents.ajouter:Ajouter un agent`);
    this.feuille.set('creation');
  }

  protected ouvrirRoles(agent: AgentInterne): void {
    this.vise = agent;
    this.choisis.set(agent.roles);
    this.erreurFeuille.set(null);
    this.titreFeuille.set($localize`:@@agents.roles.titre:Rôles de ${agent.identifiant}:agent:`);
    this.feuille.set('roles');
  }

  protected cocher(role: RoleInterne): void {
    this.choisis.update((roles) => (roles.includes(role) ? roles.filter((r) => r !== role) : [...roles, role]));
  }

  protected valider(): void {
    if (this.enCours()) {
      return;
    }
    if (this.choisis().length === 0) {
      this.erreurFeuille.set($localize`:@@agents.aucunRole:Attribuez au moins un rôle.`);
      return;
    }
    const appel: Observable<AgentInterne> = this.vise
      ? this.client.attribuer(this.vise.id, this.choisis())
      : this.client.creer(this.identifiant.value.trim(), this.motDePasse.value, this.choisis());
    this.enCours.set(true);
    this.erreurFeuille.set(null);
    appel.subscribe({
      next: () => {
        this.enCours.set(false);
        this.feuille.set(null);
        this.charger();
      },
      error: (cause: unknown) => {
        this.enCours.set(false);
        this.erreurFeuille.set(erreurLisible(cause).message);
      },
    });
  }

  protected basculer(agent: AgentInterne): void {
    this.erreur.set(null);
    (agent.suspendu ? this.client.retablir(agent.id) : this.client.suspendre(agent.id)).subscribe({
      next: () => this.charger(),
      error: (cause: unknown) => this.erreur.set(erreurLisible(cause).message),
    });
  }

  private charger(): void {
    this.client.agents().subscribe({
      next: (agents) => this.agents.set(agents),
      error: (cause: unknown) => (estRefus(cause) ? void this.router.navigate(['/refuse']) : this.erreur.set(erreurLisible(cause).message)),
    });
  }
}
