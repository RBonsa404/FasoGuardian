import { ChangeDetectionStrategy, Component } from '@angular/core';

import { FgBracelet3d, FgDecorMarque } from 'ui';

/** Scène du bracelet de l'accueil : le bracelet en trois dimensions posé sur le décor de marque. */
@Component({
  selector: 'app-scene-bracelet',
  imports: [FgBracelet3d, FgDecorMarque],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fg-decor-marque />
    <div class="absolute inset-0">
      <fg-bracelet-3d class="size-full" [libelle]="libelle" />
    </div>
  `,
  host: { class: 'relative block overflow-hidden' },
})
export class SceneBracelet {
  protected readonly libelle =
    'Bracelet FasoGuardian en trois dimensions : boîtier bleu sans écran, QR code gravé, bouton SOS ambre sur la tranche. Tournez-le avec les flèches gauche et droite.';
}
