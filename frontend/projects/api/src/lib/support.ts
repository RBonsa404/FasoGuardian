import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type StatutDemandeSupport = 'OUVERTE' | 'EN_ATTENTE_PARENT' | 'RESOLUE';
export type CategorieAide = 'BRACELET' | 'ALERTES' | 'SAFE_ZONES' | 'PAIEMENT' | 'COMPTE' | 'VIE_PRIVEE';

export interface MessageSupport {
  readonly duSupport: boolean;
  /** Le message est celui de la personne qui consulte. */
  readonly deMoi: boolean;
  readonly texte: string;
  readonly creeLe: string;
}

export interface DemandeSupport {
  readonly id: string;
  /** SUP-001182. */
  readonly reference: string;
  readonly objet: string;
  readonly statut: StatutDemandeSupport;
  readonly ouverteLe: string;
  readonly modifieeLe: string;
  /** Coordonnées du demandeur : renseignées pour l'opérateur seulement. */
  readonly parent: string | null;
  readonly telephone: string | null;
  readonly messages: readonly MessageSupport[];
}

export interface ArticleAide {
  readonly id: string;
  readonly slug: string;
  readonly categorie: CategorieAide;
  readonly titre: string;
  /** Texte simple ; les paragraphes sont séparés par une ligne vide. */
  readonly contenu: string;
  readonly publie: boolean;
  readonly lectures: number;
  readonly modifieLe: string;
}

export interface SaisieArticleAide {
  readonly categorie: CategorieAide;
  readonly titre: string;
  readonly contenu: string;
}

const CONSOLE = '/api/v1/console/support';

/** Client de l'aide et du support (US-PAR-017, US-SUP-001), côté parent et côté opérateur. */
@Injectable({ providedIn: 'root' })
export class ClientSupport {
  private readonly http = inject(HttpClient);

  // Parent

  categories(): Observable<{ categorie: CategorieAide; articles: number }[]> {
    return this.http.get<{ categorie: CategorieAide; articles: number }[]>('/api/v1/aide/categories');
  }

  /** Articles publiés, les plus lus d'abord. */
  articles(filtre: { categorie?: CategorieAide; q?: string } = {}): Observable<ArticleAide[]> {
    const params: Record<string, string> = {};
    if (filtre.categorie) {
      params['categorie'] = filtre.categorie;
    }
    if (filtre.q) {
      params['q'] = filtre.q;
    }
    return this.http.get<ArticleAide[]>('/api/v1/aide/articles', { params });
  }

  article(slug: string): Observable<ArticleAide> {
    return this.http.get<ArticleAide>(`/api/v1/aide/articles/${encodeURIComponent(slug)}`);
  }

  mesDemandes(): Observable<DemandeSupport[]> {
    return this.http.get<DemandeSupport[]>('/api/v1/support/demandes');
  }

  maDemande(id: string): Observable<DemandeSupport> {
    return this.http.get<DemandeSupport>(`/api/v1/support/demandes/${id}`);
  }

  ouvrir(objet: string, message: string): Observable<DemandeSupport> {
    return this.http.post<DemandeSupport>('/api/v1/support/demandes', { objet, message });
  }

  completer(id: string, message: string): Observable<DemandeSupport> {
    return this.http.post<DemandeSupport>(`/api/v1/support/demandes/${id}/messages`, { message });
  }

  // Opérateur

  demandes(statut: StatutDemandeSupport): Observable<DemandeSupport[]> {
    return this.http.get<DemandeSupport[]>(`${CONSOLE}/demandes`, { params: { statut } });
  }

  /** Consultation journalisée. */
  demande(id: string): Observable<DemandeSupport> {
    return this.http.get<DemandeSupport>(`${CONSOLE}/demandes/${id}`);
  }

  repondre(id: string, message: string, suite: 'EN_ATTENTE_PARENT' | 'RESOLUE'): Observable<DemandeSupport> {
    return this.http.post<DemandeSupport>(`${CONSOLE}/demandes/${id}/reponse`, { message, suite });
  }

  tousLesArticles(): Observable<ArticleAide[]> {
    return this.http.get<ArticleAide[]>(`${CONSOLE}/articles`);
  }

  rediger(article: SaisieArticleAide): Observable<ArticleAide> {
    return this.http.post<ArticleAide>(`${CONSOLE}/articles`, article);
  }

  modifier(id: string, article: SaisieArticleAide): Observable<ArticleAide> {
    return this.http.put<ArticleAide>(`${CONSOLE}/articles/${id}`, article);
  }

  publier(id: string): Observable<ArticleAide> {
    return this.http.post<ArticleAide>(`${CONSOLE}/articles/${id}/publication`, null);
  }

  retirer(id: string): Observable<ArticleAide> {
    return this.http.post<ArticleAide>(`${CONSOLE}/articles/${id}/retrait`, null);
  }
}

/** Libellés des catégories de l'aide, communs à l'application Parents et à la console. */
export const CATEGORIES_AIDE: readonly CategorieAide[] = ['BRACELET', 'ALERTES', 'SAFE_ZONES', 'PAIEMENT', 'COMPTE', 'VIE_PRIVEE'];
