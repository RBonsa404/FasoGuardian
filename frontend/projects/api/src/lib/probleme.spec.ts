import { estProbleme } from './probleme';

describe('estProbleme', () => {
  it('reconnaît une erreur RFC 9457 portant un code métier connu', () => {
    expect(
      estProbleme({
        type: 'https://fasoguardian.bf/erreurs/non-authentifie',
        title: 'Authentification requise',
        status: 401,
        code: 'NON_AUTHENTIFIE',
      }),
    ).toBe(true);
  });

  it('rejette un corps sans code métier ou avec un code inconnu', () => {
    expect(estProbleme({ status: 500 })).toBe(false);
    expect(estProbleme({ status: 400, code: 'AUTRE' })).toBe(false);
    expect(estProbleme(null)).toBe(false);
    expect(estProbleme('erreur')).toBe(false);
  });
});
