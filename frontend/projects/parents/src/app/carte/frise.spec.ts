import { PositionConnue, SafeZone } from 'api';

import { dansZone, frise } from './frise';

const ECOLE: SafeZone = {
  id: 'z-1',
  forme: 'CERCLE',
  nom: 'École Les Manguiers',
  categorie: 'ECOLE',
  centre: { latitude: 12.37, longitude: -1.52 },
  rayonM: 150,
  sommets: null,
  jours: [1, 2, 3, 4, 5],
  debut: '07:00',
  fin: '17:30',
  toleranceS: 300,
  statut: 'ACTIVE',
  dansLaPlage: true,
  sortieEnCours: false,
  enfantDedans: false,
} as unknown as SafeZone;

const MAISON: SafeZone = {
  ...ECOLE,
  id: 'z-2',
  forme: 'POLYGONE',
  nom: 'Maison',
  centre: null,
  rayonM: null,
  sommets: [
    { latitude: 12.35, longitude: -1.56 },
    { latitude: 12.35, longitude: -1.55 },
    { latitude: 12.36, longitude: -1.55 },
    { latitude: 12.36, longitude: -1.56 },
  ],
} as unknown as SafeZone;

function point(heureMinute: string, latitude: number, longitude: number): PositionConnue {
  return { latitude, longitude, precisionM: 10, source: 'GNSS', mesureeLe: `2026-10-07T${heureMinute}:00Z` } as PositionConnue;
}

describe('frise des lieux', () => {
  it('dit le départ, l’arrêt de plus de cinq minutes, puis l’entrée dans une zone', () => {
    const etapes = frise(
      [
        point('06:52', 12.355, -1.555),
        point('06:58', 12.36, -1.54),
        // Arrêt de huit minutes hors zone.
        point('07:05', 12.365, -1.53),
        point('07:09', 12.3651, -1.5301),
        point('07:13', 12.3652, -1.5302),
        point('07:21', 12.37, -1.52),
        point('12:10', 12.3701, -1.5201),
        point('12:55', 12.3702, -1.5201),
      ],
      [ECOLE, MAISON],
    );

    expect(etapes.map((e) => [e.nature, e.zone, e.debut.slice(11, 16), e.minutes])).toEqual([
      ['depart', 'Maison', '06:52', null],
      ['sortie', 'Maison', '06:58', null],
      ['arret', null, '07:05', 8],
      ['entree', 'École Les Manguiers', '07:21', null],
      ['arret', 'École Les Manguiers', '07:21', 334],
    ]);
  });

  it('ne compte pas pour un arrêt un séjour de moins de cinq minutes, ni un jour sans position', () => {
    expect(frise([], [ECOLE])).toEqual([]);
    const etapes = frise([point('08:00', 12.30, -1.50), point('08:01', 12.31, -1.50), point('08:04', 12.3101, -1.50), point('08:10', 12.32, -1.50)], []);
    expect(etapes.map((e) => e.nature)).toEqual(['depart']);
  });

  it('sait si un point est dans un cercle ou dans un polygone', () => {
    expect(dansZone({ latitude: 12.3705, longitude: -1.5205 }, ECOLE)).toBe(true);
    expect(dansZone({ latitude: 12.38, longitude: -1.52 }, ECOLE)).toBe(false);
    expect(dansZone({ latitude: 12.355, longitude: -1.555 }, MAISON)).toBe(true);
    expect(dansZone({ latitude: 12.365, longitude: -1.555 }, MAISON)).toBe(false);
  });
});
