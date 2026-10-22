import { entreeActive } from './entrees';

describe('entreeActive', () => {
  const liens = ['/', '/kyc', '/kyc/litiges', '/support/tickets'];

  it('surligne la plus précise des entrées qui correspondent', () => {
    expect(entreeActive(liens, '/kyc/litiges')).toBe('/kyc/litiges');
    expect(entreeActive(liens, '/kyc/litiges/7b1e')).toBe('/kyc/litiges');
  });

  it('rattache un dossier à sa file', () => {
    expect(entreeActive(liens, '/kyc/2f4a?onglet=pieces')).toBe('/kyc');
  });

  it("ne surligne le tableau de bord que sur l'accueil", () => {
    expect(entreeActive(liens, '/')).toBe('/');
    expect(entreeActive(liens, '/refuse')).toBeNull();
    expect(entreeActive(liens, '/kycx')).toBeNull();
  });
});
