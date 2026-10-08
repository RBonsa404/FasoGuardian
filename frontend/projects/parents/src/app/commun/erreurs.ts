import { ErreurLisible, lireErreur } from 'api';

/** Lit une erreur d'API avec les messages de repli de l'application Parents. */
export function erreurLisible(erreur: unknown): ErreurLisible {
  return lireErreur(erreur, {
    horsLigne: $localize`:@@erreur.horsLigne:Pas de connexion. Vérifiez votre réseau puis réessayez.`,
    inconnue: $localize`:@@erreur.inconnue:Une erreur est survenue. Réessayez dans un instant.`,
  });
}
