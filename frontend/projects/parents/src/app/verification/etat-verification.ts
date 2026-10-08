import { Injectable, signal } from '@angular/core';

import { CanalKyc, DossierKycParent, EnfantDeclare, IdentiteDeclaree, NatureLien, TypePieceKyc } from 'api';

/**
 * État du parcours de vérification, conservé en mémoire le temps de la saisie. Les photos et l'identité
 * ne sont jamais écrites dans le stockage du navigateur ; elles sont effacées dès le dépôt du dossier.
 */
@Injectable({ providedIn: 'root' })
export class EtatVerification {
  readonly canal = signal<CanalKyc>('EN_LIGNE');
  readonly natureLien = signal<NatureLien>('PARENT');
  readonly identite = signal<IdentiteDeclaree | null>(null);
  readonly enfant = signal<EnfantDeclare | null>(null);
  readonly fichiers = signal<Partial<Record<TypePieceKyc, Blob>>>({});
  /** Dossier existant à compléter (statut COMPLEMENT_DEMANDE). */
  readonly aCompleter = signal<DossierKycParent | null>(null);

  joindre(type: TypePieceKyc, fichier: Blob): void {
    this.fichiers.update((fichiers) => ({ ...fichiers, [type]: fichier }));
  }

  effacer(): void {
    this.canal.set('EN_LIGNE');
    this.natureLien.set('PARENT');
    this.identite.set(null);
    this.enfant.set(null);
    this.fichiers.set({});
    this.aCompleter.set(null);
  }
}

const POIDS_CIBLE = 400_000;
const COTE_MAX = 1600;

/**
 * Réduit une photo à 400 Ko environ avant envoi (réseau 2G). Les PDF et les images déjà légères passent
 * tels quels ; si le navigateur ne sait pas redimensionner, le fichier d'origine est envoyé.
 */
export async function allegerPhoto(fichier: Blob): Promise<Blob> {
  if (!fichier.type.startsWith('image/') || fichier.size <= POIDS_CIBLE || typeof createImageBitmap !== 'function') {
    return fichier;
  }
  try {
    const image = await createImageBitmap(fichier);
    const echelle = Math.min(1, COTE_MAX / Math.max(image.width, image.height));
    const toile = document.createElement('canvas');
    toile.width = Math.round(image.width * echelle);
    toile.height = Math.round(image.height * echelle);
    toile.getContext('2d')!.drawImage(image, 0, 0, toile.width, toile.height);
    for (const qualite of [0.8, 0.65, 0.5]) {
      const reduit = await new Promise<Blob | null>((resoudre) => toile.toBlob(resoudre, 'image/jpeg', qualite));
      if (reduit && reduit.size <= POIDS_CIBLE) {
        return reduit;
      }
      if (reduit && qualite === 0.5) {
        return reduit;
      }
    }
  } catch {
    // Format non décodable par le navigateur : le serveur tranchera.
  }
  return fichier;
}
