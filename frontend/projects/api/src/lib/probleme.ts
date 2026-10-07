/** Réponse d'erreur des API FasoGuardian au format RFC 9457, enrichie d'un code métier stable. */
export interface Probleme {
  readonly type: string;
  readonly title: string;
  readonly status: number;
  readonly detail?: string;
  readonly instance?: string;
  readonly code: CodeErreur;
}

/** Codes d'erreur stables publiés par le serveur (bf.fasoguardian.plateforme.erreurs.CodeErreur). */
export type CodeErreur =
  | 'NON_AUTHENTIFIE'
  | 'ACCES_REFUSE'
  | 'RESSOURCE_INTROUVABLE'
  | 'REQUETE_INVALIDE'
  | 'CONFLIT'
  | 'TROP_DE_REQUETES'
  | 'ERREUR_INTERNE';

const CODES: ReadonlySet<string> = new Set<CodeErreur>([
  'NON_AUTHENTIFIE',
  'ACCES_REFUSE',
  'RESSOURCE_INTROUVABLE',
  'REQUETE_INVALIDE',
  'CONFLIT',
  'TROP_DE_REQUETES',
  'ERREUR_INTERNE',
]);

export function estProbleme(corps: unknown): corps is Probleme {
  if (typeof corps !== 'object' || corps === null) {
    return false;
  }
  const candidat = corps as Record<string, unknown>;
  return typeof candidat['status'] === 'number' && typeof candidat['code'] === 'string' && CODES.has(candidat['code']);
}
