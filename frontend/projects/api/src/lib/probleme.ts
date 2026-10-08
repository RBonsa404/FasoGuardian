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
  | 'ERREUR_INTERNE'
  | 'TELEPHONE_INVALIDE'
  | 'CODE_INCORRECT'
  | 'CODE_EXPIRE'
  | 'CODE_EPUISE'
  | 'MOT_DE_PASSE_REFUSE'
  | 'CONSENTEMENT_REQUIS'
  | 'IDENTIFIANTS_INVALIDES'
  | 'COMPTE_VERROUILLE'
  | 'SESSION_EXPIREE'
  | 'TOTP_A_ACTIVER'
  | 'CODE_TOTP_REQUIS'
  | 'DOSSIER_INCOMPLET'
  | 'PIECE_REFUSEE'
  | 'SECOND_FACTEUR_REQUIS';

const CODES: ReadonlySet<string> = new Set<CodeErreur>([
  'NON_AUTHENTIFIE',
  'ACCES_REFUSE',
  'RESSOURCE_INTROUVABLE',
  'REQUETE_INVALIDE',
  'CONFLIT',
  'TROP_DE_REQUETES',
  'ERREUR_INTERNE',
  'TELEPHONE_INVALIDE',
  'CODE_INCORRECT',
  'CODE_EXPIRE',
  'CODE_EPUISE',
  'MOT_DE_PASSE_REFUSE',
  'CONSENTEMENT_REQUIS',
  'IDENTIFIANTS_INVALIDES',
  'COMPTE_VERROUILLE',
  'SESSION_EXPIREE',
  'TOTP_A_ACTIVER',
  'CODE_TOTP_REQUIS',
  'DOSSIER_INCOMPLET',
  'PIECE_REFUSEE',
  'SECOND_FACTEUR_REQUIS',
]);

export function estProbleme(corps: unknown): corps is Probleme {
  if (typeof corps !== 'object' || corps === null) {
    return false;
  }
  const candidat = corps as Record<string, unknown>;
  return typeof candidat['status'] === 'number' && typeof candidat['code'] === 'string' && CODES.has(candidat['code']);
}
