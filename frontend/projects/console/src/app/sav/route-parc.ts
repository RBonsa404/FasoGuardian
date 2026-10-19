import { UrlMatchResult, UrlSegment } from '@angular/router';

/**
 * `sav/parc` et `sav/parc/FG-2291` désignent le même écran : la liste reste en place quand l'agent passe d'une
 * fiche à l'autre, et un lien direct vers une fiche (recherche Ctrl K) ouvre l'écran avec ce bracelet choisi.
 */
export function routeParc(segments: UrlSegment[]): UrlMatchResult | null {
  if (segments.length < 2 || segments.length > 3 || segments[0].path !== 'sav' || segments[1].path !== 'parc') {
    return null;
  }
  return { consumed: segments, posParams: segments[2] ? { numero: segments[2] } : {} };
}
