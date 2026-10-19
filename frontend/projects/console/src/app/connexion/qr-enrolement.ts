import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import qrcode from 'qrcode-generator';

/**
 * QR code de l'adresse otpauth:// d'enrôlement TOTP (écran 55), à scanner avec l'application d'authentification.
 * Il est dessiné dans la page : l'adresse, qui contient le secret, ne part vers aucun service extérieur.
 */
@Component({
  selector: 'app-qr-enrolement',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg class="block size-full" [attr.viewBox]="'0 0 ' + dessin().taille + ' ' + dessin().taille" shape-rendering="crispEdges" role="img" [attr.aria-label]="libelle()">
      <rect [attr.width]="dessin().taille" [attr.height]="dessin().taille" fill="#ffffff" />
      <path [attr.d]="dessin().chemin" fill="#0a0e1a" />
    </svg>
  `,
  host: { class: 'block rounded-sm bg-white p-1.5' },
})
export class QrEnrolement {
  readonly uri = input.required<string>();
  readonly libelle = input.required<string>();

  /** Un carré par module sombre, avec une marge de deux modules (lecture fiable par les appareils photo). */
  protected readonly dessin = computed(() => {
    const code = qrcode(0, 'M');
    code.addData(this.uri());
    code.make();
    const modules = code.getModuleCount();
    let chemin = '';
    for (let ligne = 0; ligne < modules; ligne++) {
      for (let colonne = 0; colonne < modules; colonne++) {
        if (code.isDark(ligne, colonne)) {
          chemin += `M${colonne + 2} ${ligne + 2}h1v1h-1z`;
        }
      }
    }
    return { taille: modules + 4, chemin };
  });
}
