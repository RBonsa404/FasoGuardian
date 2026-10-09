import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { ArticleAide, CategorieAide, ClientSupport, DemandeSupport } from 'api';

import { Aide } from './aide';
import { DemandesSupport, FilDemande } from './demandes';

const SANGLE: ArticleAide = { id: 'a-1', slug: 'sangle', categorie: 'BRACELET', titre: "Que faire si la sangle s'ouvre seule ?", contenu: 'Le fermoir…', publie: true, lectures: 12, modifieLe: '2026-10-01T10:00:00Z' };

function demande(partiel: Partial<DemandeSupport> = {}): DemandeSupport {
  return {
    id: 'd-1',
    reference: 'SUP-001182',
    objet: 'La sangle se détache toute seule',
    statut: 'EN_ATTENTE_PARENT',
    ouverteLe: '2026-10-06T18:02:00Z',
    modifieeLe: '2026-10-07T08:40:00Z',
    parent: null,
    telephone: null,
    messages: [
      { duSupport: false, deMoi: true, texte: 'Depuis hier la sangle s’ouvre.', creeLe: '2026-10-06T18:02:00Z' },
      { duSupport: true, deMoi: false, texte: 'Échange gratuit au point relais.', creeLe: '2026-10-07T08:40:00Z' },
    ],
    ...partiel,
  };
}

/** Double du client : retient les recherches, les ouvertures et les réponses. */
class SupportFactice {
  recherches: { categorie?: CategorieAide; q?: string }[] = [];
  ouvertures: { objet: string; message: string }[] = [];
  reponses: string[] = [];
  courante = demande();

  categories() {
    return of([{ categorie: 'BRACELET' as CategorieAide, articles: 12 }]);
  }

  articles(filtre: { categorie?: CategorieAide; q?: string }) {
    this.recherches.push(filtre);
    return of(filtre.q === 'introuvable' ? [] : [SANGLE]);
  }

  mesDemandes() {
    return of([this.courante]);
  }

  maDemande() {
    return of(this.courante);
  }

  ouvrir(objet: string, message: string) {
    this.ouvertures.push({ objet, message });
    return of(this.courante);
  }

  completer(_id: string, message: string) {
    this.reponses.push(message);
    this.courante = demande({ statut: 'OUVERTE', messages: [...this.courante.messages, { duSupport: false, deMoi: true, texte: message, creeLe: '2026-10-07T09:00:00Z' }] });
    return of(this.courante);
  }
}

function lire(element: Element): string {
  return (element.textContent ?? '').replace(/\s+/g, ' ');
}

function bouton(page: HTMLElement, nom: string): HTMLButtonElement {
  return [...page.querySelectorAll('button')].find((b) => lire(b).includes(nom)) as HTMLButtonElement;
}

function preparer(client: SupportFactice) {
  TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: ClientSupport, useValue: client }] });
}

describe('aide', () => {
  it('montre les articles les plus lus, filtre par catégorie et cherche par mots', async () => {
    const client = new SupportFactice();
    preparer(client);
    const fixture = TestBed.createComponent(Aide);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    expect(lire(page)).toContain('Les plus lus');
    expect(lire(page)).toContain("Que faire si la sangle s'ouvre seule ?");
    expect(lire(bouton(page, 'Bracelet'))).toContain('12 articles');

    bouton(page, 'Bracelet').click();
    await fixture.whenStable();
    expect(client.recherches.at(-1)).toEqual({ categorie: 'BRACELET', q: undefined });

    const recherche = page.querySelector('input[type="search"]') as HTMLInputElement;
    recherche.value = 'introuvable';
    recherche.dispatchEvent(new Event('input'));
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    expect(client.recherches.at(-1)).toEqual({ categorie: undefined, q: 'introuvable' });
    expect(lire(page)).toContain('Résultats pour « introuvable »');
    expect(lire(page)).toContain('Aucun article ne correspond.');
  });
});

describe('demandes au support', () => {
  it('liste les demandes avec leur statut et n’envoie pas une demande vide', async () => {
    const client = new SupportFactice();
    preparer(client);
    const fixture = TestBed.createComponent(DemandesSupport);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    expect(lire(page)).toContain('Votre réponse est attendue · SUP-001182');
    expect(lire(page)).toContain('2 messages');

    bouton(page, 'Écrire au support').click();
    await fixture.whenStable();
    bouton(page, 'Envoyer').click();
    await fixture.whenStable();

    expect(lire(page)).toContain('Donnez un objet et décrivez votre demande.');
    expect(client.ouvertures).toEqual([]);
  });

  it('montre le fil, distingue les réponses du support et envoie celle du parent', async () => {
    const client = new SupportFactice();
    preparer(client);
    const fixture = TestBed.createComponent(FilDemande);
    fixture.componentRef.setInput('tid', 'd-1');
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    const messages = [...page.querySelectorAll('ol li')].map((ligne) => lire(ligne));
    expect(messages).toHaveLength(2);
    expect(messages[0]).toContain('Vous ·');
    expect(messages[1]).toContain('Support ·');

    const reponse = page.querySelector('textarea') as HTMLTextAreaElement;
    reponse.value = " Merci, j'y passe demain. ";
    reponse.dispatchEvent(new Event('input'));
    bouton(page, 'Envoyer').click();
    await fixture.whenStable();

    expect(client.reponses).toEqual(["Merci, j'y passe demain."]);
    expect(page.querySelectorAll('ol li')).toHaveLength(3);
    expect(lire(page)).toContain('En cours · SUP-001182');
  });
});
