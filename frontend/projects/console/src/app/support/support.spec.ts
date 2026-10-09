import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { ArticleAide, ClientSupport, DemandeSupport, SaisieArticleAide, StatutDemandeSupport } from 'api';

import { Faq } from './faq';
import { Tickets, attente } from './tickets';

const DEMANDE: DemandeSupport = {
  id: 'd-1',
  reference: 'SUP-001182',
  objet: 'La sangle se détache toute seule',
  statut: 'OUVERTE',
  ouverteLe: '2026-10-06T18:02:00Z',
  modifieeLe: new Date(Date.now() - 21 * 3_600_000).toISOString(),
  parent: 'Mariam',
  telephone: '+22670123456',
  messages: [{ duSupport: false, deMoi: false, texte: 'Depuis hier la sangle s’ouvre.', creeLe: '2026-10-06T18:02:00Z' }],
};

function article(partiel: Partial<ArticleAide> = {}): ArticleAide {
  return { id: 'a-1', slug: 'sangle', categorie: 'BRACELET', titre: 'Sangle qui s’ouvre', contenu: 'Le fermoir…', publie: false, lectures: 0, modifieLe: '2026-10-01T10:00:00Z', ...partiel };
}

/** Double du client : retient ce que l'opérateur demande. */
class SupportFactice {
  files: StatutDemandeSupport[] = [];
  reponses: { message: string; suite: string }[] = [];
  redactions: SaisieArticleAide[] = [];
  publications: string[] = [];
  liste: ArticleAide[] = [];

  demandes(statut: StatutDemandeSupport) {
    this.files.push(statut);
    return of(statut === 'OUVERTE' && this.reponses.length === 0 ? [DEMANDE] : []);
  }

  demande() {
    return of(DEMANDE);
  }

  repondre(_id: string, message: string, suite: string) {
    this.reponses.push({ message, suite });
    return of(DEMANDE);
  }

  tousLesArticles() {
    return of(this.liste);
  }

  rediger(saisie: SaisieArticleAide) {
    this.redactions.push(saisie);
    this.liste = [article({ titre: saisie.titre, contenu: saisie.contenu })];
    return of(this.liste[0]);
  }

  publier(id: string) {
    this.publications.push(id);
    this.liste = [article({ ...this.liste[0], publie: true })];
    return of(this.liste[0]);
  }
}

function lire(element: Element): string {
  return (element.textContent ?? '').replace(/\s+/g, ' ');
}

function bouton(page: HTMLElement, nom: string): HTMLButtonElement {
  return [...page.querySelectorAll('button')].find((b) => lire(b).includes(nom)) as HTMLButtonElement;
}

function saisir(champ: HTMLInputElement | HTMLTextAreaElement, valeur: string): void {
  champ.value = valeur;
  champ.dispatchEvent(new Event('input'));
}

function preparer(client: SupportFactice) {
  TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: ClientSupport, useValue: client }] });
}

describe('tickets support', () => {
  it('ouvre une demande avec les coordonnées du parent, puis répond en la passant en attente', async () => {
    const client = new SupportFactice();
    preparer(client);
    const fixture = TestBed.createComponent(Tickets);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    expect(lire(page)).toContain('SUP-001182');
    expect(lire(page)).toContain('21 h');
    bouton(page, 'La sangle se détache').click();
    await fixture.whenStable();
    expect(lire(page)).toContain('Mariam · +22670123456');

    bouton(page, 'Répondre et attendre le parent').click();
    await fixture.whenStable();
    expect(lire(page)).toContain("Écrivez votre réponse avant de l'envoyer.");
    expect(client.reponses).toEqual([]);

    saisir(page.querySelector('textarea') as HTMLTextAreaElement, ' Échange gratuit au point relais. ');
    bouton(page, 'Répondre et attendre le parent').click();
    await fixture.whenStable();

    expect(client.reponses).toEqual([{ message: 'Échange gratuit au point relais.', suite: 'EN_ATTENTE_PARENT' }]);
    expect(lire(page)).toContain('Aucune demande dans cette file.');
  });

  it('change de file et écrit l’attente en heures puis en jours', async () => {
    const client = new SupportFactice();
    preparer(client);
    const fixture = TestBed.createComponent(Tickets);
    await fixture.whenStable();

    bouton(fixture.nativeElement as HTMLElement, 'Résolus').click();
    await fixture.whenStable();

    expect(client.files).toEqual(['OUVERTE', 'RESOLUE']);
    const maintenant = Date.parse('2026-10-09T12:00:00Z');
    expect(attente('2026-10-08T15:00:00Z', maintenant)).toBe('21 h');
    expect(attente('2026-10-05T12:00:00Z', maintenant)).toBe('4 j');
  });
});

describe('éditeur de la base de connaissances', () => {
  it('rédige un brouillon, en montre l’aperçu, puis le publie', async () => {
    const client = new SupportFactice();
    preparer(client);
    const fixture = TestBed.createComponent(Faq);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    bouton(page, 'Enregistrer').click();
    await fixture.whenStable();
    expect(lire(page)).toContain("Donnez un titre et un contenu à l'article.");

    saisir(page.querySelector('fg-input input') as HTMLInputElement, 'Sangle qui s’ouvre');
    saisir(page.querySelector('textarea') as HTMLTextAreaElement, 'Le fermoir doit produire un clic net.');
    await fixture.whenStable();
    // L'aperçu suit la saisie, tel que le parent lira l'article.
    expect(lire(page.querySelector('[aria-labelledby="titre-apercu"]')!)).toContain('Le fermoir doit produire un clic net.');

    bouton(page, 'Enregistrer').click();
    await fixture.whenStable();
    expect(client.redactions).toEqual([{ categorie: 'BRACELET', titre: 'Sangle qui s’ouvre', contenu: 'Le fermoir doit produire un clic net.' }]);
    expect(lire(page)).toContain('Brouillon');

    bouton(page, 'Publier').click();
    await fixture.whenStable();

    expect(client.publications).toEqual(['a-1']);
    expect(bouton(page, 'Retirer de la publication')).toBeDefined();
  });
});
