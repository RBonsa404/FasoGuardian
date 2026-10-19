import { HttpContext, HttpContextToken } from '@angular/common/http';

/**
 * Actions de configuration rejouables hors ligne (FG-DOC-06 §5.4, US-SYS-002). Une action protégée par un code
 * SMS n'en fait jamais partie : le code expire avant le retour du réseau. Une action liée à l'instant (localiser,
 * partager la position, suivi rapproché) non plus : la rejouer plus tard n'aurait plus de sens.
 */
export type GenreActionRejouable =
  | 'FICHE_ENFANT'
  | 'FICHE_SANTE'
  | 'CONTACT_URGENCE'
  | 'PRISE_EN_CHARGE'
  | 'LEVEE_ALERTE'
  | 'FAUSSE_ALERTE'
  | 'ZONE_REACTIVEE'
  | 'MODE_ECONOMIE'
  | 'RENOUVELLEMENT'
  | 'NOTIFICATIONS_LUES'
  | 'MESSAGE_SUPPORT';

/** Genre d'une écriture que l'application peut garder sans réseau et renvoyer plus tard. */
export const REJOUABLE = new HttpContextToken<GenreActionRejouable | null>(() => null);

/** Options d'une requête rejouable, à passer au client HTTP. */
export function rejouable(genre: GenreActionRejouable): { context: HttpContext } {
  return { context: new HttpContext().set(REJOUABLE, genre) };
}

/** L'action n'a pas pu partir faute de réseau : elle est gardée et partira au retour du réseau. */
export class ActionMiseEnAttente extends Error {
  constructor(readonly genre: GenreActionRejouable) {
    super('Action mise en attente du réseau');
    this.name = 'ActionMiseEnAttente';
  }
}
