import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { FgChoixTheme } from 'ui';

import { ESPACE_PARENTS, NAVIGATION, PRIX_BRACELET } from './contenu';

const PIED = [
  { titre: 'Produit', liens: [{ libelle: 'Le bracelet', lien: '/bracelet' }, { libelle: 'Offres', lien: '/offres' }, { libelle: 'Écoles', lien: '/ecoles' }, { libelle: 'Sécurité et données', lien: '/securite' }] },
  { titre: 'Aide', liens: [{ libelle: 'FAQ', lien: '/faq' }, { libelle: 'Points relais', lien: '/points-relais' }, { libelle: 'Contact', lien: '/contact' }] },
  { titre: 'Légal', liens: [{ libelle: 'Mentions légales', lien: '/mentions' }, { libelle: 'Confidentialité', lien: '/confidentialite' }, { libelle: 'Conditions générales', lien: '/cgu' }] },
] as const;

/** Coque du site vitrine : en-tête collant, navigation repliée en menu sous 1024 px, pied de page. */
@Component({
  imports: [RouterOutlet, RouterLink, RouterLinkActive, FgChoixTheme],
  selector: 'app-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  protected readonly navigation = NAVIGATION;
  protected readonly pied = PIED;
  protected readonly prix = PRIX_BRACELET;
  protected readonly espaceParents = ESPACE_PARENTS;
  protected readonly menu = signal(false);
}
