/** Clé du témoin « introduction déjà vue » : il ne dit rien de la personne, seulement que l'appareil l'a montrée. */
export const TEMOIN_INTRODUCTION = 'fg.introduction';

export function introductionVue(): boolean {
  try {
    return localStorage.getItem(TEMOIN_INTRODUCTION) === '1';
  } catch {
    // Stockage indisponible (navigation privée) : l'introduction n'est pas imposée à chaque visite.
    return true;
  }
}

export function noterIntroductionVue(): void {
  try {
    localStorage.setItem(TEMOIN_INTRODUCTION, '1');
  } catch {
    // Sans stockage, l'introduction sera simplement proposée de nouveau.
  }
}

/** Écran d'accueil d'une personne sans session ni fiche gardée : l'introduction la première fois, puis la connexion. */
export function entreeSansSession(): string {
  return introductionVue() ? '/connexion' : '/bienvenue';
}
