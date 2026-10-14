import { SafeZone, Situation } from 'api';

import { resumer } from './enfants';

const MAINTENANT = new Date('2026-10-14T14:32:00Z');

const SITUATION: Situation = {
  numeroSerie: 'FG-2291',
  position: { latitude: 12.37, longitude: -1.52, precisionM: 8, source: 'GNSS', mesureeLe: '2026-10-14T14:30:00Z' } as Situation['position'],
  etat: {
    batterie: 78,
    signalDbm: -71,
    reseau: '4G',
    operateur: null,
    enMouvement: false,
    enLigne: true,
    versionLogiciel: '2.4.1',
    dernierContact: '2026-10-14T14:30:00Z',
  },
};

function zone(nom: string, enfantDedans: boolean, sortieEnCours = false): SafeZone {
  return { nom, enfantDedans, sortieEnCours } as SafeZone;
}

describe('résumé de l’écran 17', () => {
  it('dit dans quelle Safe Zone se trouve l’enfant, avec la fraîcheur de sa position', () => {
    const resume = resumer(SITUATION, [zone('École', true)], MAINTENANT);
    expect(resume.texte).toBe('Dans « École »');
    expect(resume.ton).toBe('succes');
    expect(resume.details).toMatch(/^\d{2}:\d{2} · il y a 2 min · 4G · 78 %$/);
  });

  it('fait passer une sortie de zone avant tout le reste', () => {
    expect(resumer(SITUATION, [zone('Maison', false), zone('École', true, true)], MAINTENANT)).toMatchObject({ texte: 'Hors de « École »', ton: 'attention' });
  });

  it('distingue le déplacement d’un arrêt hors des zones', () => {
    expect(resumer({ ...SITUATION, etat: { ...SITUATION.etat!, enMouvement: true } }, [], MAINTENANT).texte).toBe('En déplacement');
    expect(resumer(SITUATION, [], MAINTENANT).texte).toBe('Hors des Safe Zones');
  });

  it('n’invente rien sans bracelet ni position', () => {
    expect(resumer(null, [], MAINTENANT)).toEqual({ texte: 'Aucun bracelet associé', ton: 'neutre', details: '' });
    expect(resumer({ ...SITUATION, position: null }, [], MAINTENANT).texte).toBe('En attente de la première position');
    expect(resumer(undefined, [], MAINTENANT).texte).toBe('');
  });
});
