import { CategorieZone, SafeZone } from 'api';
import { NomIcone } from 'ui';

export const CATEGORIES: readonly { valeur: CategorieZone; libelle: string; icone: NomIcone }[] = [
  { valeur: 'ECOLE', libelle: $localize`:@@zone.categorie.ecole:École`, icone: 'ecole' },
  { valeur: 'MAISON', libelle: $localize`:@@zone.categorie.maison:Maison`, icone: 'maison' },
  { valeur: 'FAMILLE', libelle: $localize`:@@zone.categorie.famille:Famille`, icone: 'filiation' },
  { valeur: 'CULTE', libelle: $localize`:@@zone.categorie.culte:Lieu de culte`, icone: 'safe-zone' },
  { valeur: 'AUTRE', libelle: $localize`:@@zone.categorie.autre:Autre`, icone: 'position' },
];

/** Abréviations des jours, lundi en premier (1 = lundi … 7 = dimanche). */
export const JOURS: readonly { valeur: number; initiale: string; abrege: string; nom: string }[] = [
  { valeur: 1, initiale: 'L', abrege: $localize`:@@jour.lun:lun.`, nom: $localize`:@@jour.lundi:lundi` },
  { valeur: 2, initiale: 'M', abrege: $localize`:@@jour.mar:mar.`, nom: $localize`:@@jour.mardi:mardi` },
  { valeur: 3, initiale: 'M', abrege: $localize`:@@jour.mer:mer.`, nom: $localize`:@@jour.mercredi:mercredi` },
  { valeur: 4, initiale: 'J', abrege: $localize`:@@jour.jeu:jeu.`, nom: $localize`:@@jour.jeudi:jeudi` },
  { valeur: 5, initiale: 'V', abrege: $localize`:@@jour.ven:ven.`, nom: $localize`:@@jour.vendredi:vendredi` },
  { valeur: 6, initiale: 'S', abrege: $localize`:@@jour.sam:sam.`, nom: $localize`:@@jour.samedi:samedi` },
  { valeur: 7, initiale: 'D', abrege: $localize`:@@jour.dim:dim.`, nom: $localize`:@@jour.dimanche:dimanche` },
];

export function iconeDe(zone: SafeZone): NomIcone {
  return CATEGORIES.find((categorie) => categorie.valeur === zone.categorie)?.icone ?? 'position';
}

/** « lun.–ven. », « tous les jours », « sam., dim. ». */
export function joursLisibles(jours: readonly number[]): string {
  const tries = [...jours].sort((a, b) => a - b);
  if (tries.length === 7) {
    return $localize`:@@zone.tousLesJours:tous les jours`;
  }
  const consecutifs = tries.length > 2 && tries.every((jour, index) => index === 0 || jour === tries[index - 1] + 1);
  const abrege = (jour: number) => JOURS[jour - 1].abrege;
  return consecutifs ? `${abrege(tries[0])}–${abrege(tries[tries.length - 1])}` : tries.map(abrege).join(', ');
}

/** « Cercle 220 m · lun.–ven. 07:00–17:30 ». */
export function resume(zone: SafeZone): string {
  const forme = zone.forme === 'CERCLE' ? $localize`:@@zone.cercle:Cercle ${distance(zone.rayonM ?? 0)}:rayon:` : $localize`:@@zone.polygone:Polygone`;
  const heures = zone.debut === zone.fin ? '' : ` ${zone.debut}–${zone.fin}`;
  return `${forme} · ${joursLisibles(zone.jours)}${heures}`;
}

export function distance(metres: number): string {
  return metres >= 1000 ? `${(metres / 1000).toFixed(1).replace('.', ',')} km` : `${metres} m`;
}
