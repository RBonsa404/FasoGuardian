import { ErreurLisible, lireErreur } from 'api';

/** Lit une erreur d'API avec les messages de repli de l'application Parents. */
export function erreurLisible(erreur: unknown): ErreurLisible {
  return lireErreur(erreur, {
    horsLigne: $localize`:@@erreur.horsLigne:Pas de connexion. Vérifiez votre réseau puis réessayez.`,
    inconnue: $localize`:@@erreur.inconnue:Une erreur est survenue. Réessayez dans un instant.`,
  });
}

/** L'API répond que la ressource demandée n'existe pas : souvent un état normal (rien à montrer), pas une panne. */
export function estIntrouvable(erreur: unknown): boolean {
  return erreurLisible(erreur).code === 'RESSOURCE_INTROUVABLE';
}
