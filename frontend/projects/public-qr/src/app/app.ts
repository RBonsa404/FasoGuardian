import { CUSTOM_ELEMENTS_SCHEMA, ChangeDetectionStrategy, Component } from '@angular/core';

import { FgIcon } from 'ui';

/**
 * Page publique QR (écrans 51 à 54). Ce composant n'est jamais exécuté dans le navigateur : sa version
 * pré-rendue devient le gabarit que le serveur remplit à chaque scan (ADR 0006). Les balises fg-etat,
 * fg-si et fg-pour ainsi que les marqueurs [[nom]] sont interprétés par le serveur.
 */
@Component({
  imports: [FgIcon],
  selector: 'app-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './app.html',
  host: { class: 'mx-auto flex min-h-dvh w-full max-w-md flex-col gap-6 px-5 py-6' },
})
export class App {}
