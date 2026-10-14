import { Pipe, PipeTransform } from '@angular/core';

/** Voyelles, accentuées ou non : devant elles, « de » s'élide. Ni le h (muet ou aspiré selon le prénom) ni le y (« de Yacouba ») ne comptent. */
const VOYELLE = /^[aeiouàâäéèêëîïôöùûüœæ]/i;

/** « de » suivi d'un prénom, élidé devant une voyelle : « d'Awa », « de Yacouba ». */
export function de(prenom: string): string {
  return VOYELLE.test(prenom) ? `d'${prenom}` : `de ${prenom}`;
}

/** Même règle, dans un gabarit : « le bracelet {{ prenom | de }} ». */
@Pipe({ name: 'de' })
export class DePipe implements PipeTransform {
  transform(prenom: string | null | undefined): string {
    return de(prenom ?? '');
  }
}
