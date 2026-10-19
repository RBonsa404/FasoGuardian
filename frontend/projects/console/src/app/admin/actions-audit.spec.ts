import { libelleAction } from './actions-audit';

describe('actions du journal d’audit mises en mots (écran 70)', () => {
  it('traduit les codes connus et laisse les autres tels quels', () => {
    expect(libelleAction('DECISION_KYC_APPROUVE')).toBe('Dossier KYC validé');
    expect(libelleAction('EFFACEMENT_EXECUTE')).toBe('Effacement exécuté');
    expect(libelleAction('CODE_INCONNU_FUTUR')).toBe('CODE_INCONNU_FUTUR');
  });
});
