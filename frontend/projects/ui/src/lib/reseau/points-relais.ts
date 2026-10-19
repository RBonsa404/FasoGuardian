/**
 * Points d'inscription et relais. Liste indicative tant que le réseau du pilote n'est pas ouvert : les lieux
 * sont des quartiers réels, les enseignes sont fictives. Partagée par le site public et l'espace parents.
 */
export const POINTS_RELAIS = [
  { ville: 'Ouagadougou', nom: 'Point FasoGuardian · Dassasgho', services: 'Inscription · service après-vente', repere: 'Près du marché' },
  { ville: 'Ouagadougou', nom: 'Pharmacie relais · Pissy', services: 'Remise de bracelets', repere: 'Avenue principale' },
  { ville: 'Ouagadougou', nom: 'Boutique relais · Gounghin', services: 'Échange de sangles', repere: 'Face au marché' },
  { ville: 'Ouagadougou', nom: 'Point FasoGuardian · Tampouy', services: 'Inscription accompagnée', repere: 'Rond-point de Tampouy' },
  { ville: 'Ouagadougou', nom: 'Point relais · Ouaga 2000', services: 'Remise · service après-vente', repere: 'Zone commerciale' },
  { ville: 'Bobo-Dioulasso', nom: 'Point FasoGuardian · Centre', services: 'Inscription · service après-vente', repere: 'Près de la gare' },
] as const;

/** Points où un agent finalise la vérification d'un dossier déposé « en point d'inscription » (US-PAR-001). */
export const POINTS_INSCRIPTION = POINTS_RELAIS.filter((point) => point.services.includes('Inscription'));
