import { ChangeDetectionStrategy, Component, ElementRef, OnDestroy, effect, input, output, signal, viewChild } from '@angular/core';

import { FgIcon } from 'ui';

/** L'appareil photo peut s'ouvrir dans la page : sinon, le champ fichier ouvre l'appareil photo du téléphone. */
export function viseurDisponible(): boolean {
  return typeof navigator !== 'undefined' && typeof navigator.mediaDevices?.getUserMedia === 'function';
}

/**
 * Prise de vue d'une pièce (écran 11b) : l'image de la caméra arrière, un cadre au format d'une carte d'identité,
 * le déclencheur. Aucune analyse de l'image n'est faite sur le téléphone : l'agent KYC juge de la lisibilité.
 * Le viseur reste sombre dans les deux thèmes, comme l'appareil photo du téléphone.
 */
@Component({
  selector: 'app-prise-de-vue',
  imports: [FgIcon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'contents' },
  template: `
    <dialog
      #fenetre
      class="m-0 h-dvh max-h-none w-full max-w-none bg-nuit-950 p-0 text-nuit-50 backdrop:bg-nuit-950"
      [attr.aria-label]="titre()"
      (cancel)="$event.preventDefault(); fermer()"
    >
      <div class="mx-auto flex h-full max-w-md flex-col">
        <header class="relative flex h-16 flex-none items-center justify-center bg-nuit-900 px-5">
          <button
            type="button"
            class="absolute left-3 grid size-11 place-items-center rounded-full text-nuit-200 focus-visible:outline-2 focus-visible:outline-electrique-400"
            [attr.aria-label]="libelleFermer"
            (click)="fermer()"
          >
            <fg-icon nom="fermer" [taille]="22" />
          </button>
          <h2 class="m-0 text-body font-semibold text-nuit-200">{{ titre() }}</h2>
        </header>
        <div class="flex flex-1 flex-col items-center justify-center gap-3 px-7">
          @if (indisponible()) {
            <p class="m-0 text-center text-body text-nuit-200">{{ libelleIndisponible }}</p>
            <label
              class="flex h-12 cursor-pointer items-center rounded-md bg-nuit-50 px-5 text-body font-semibold text-nuit-950 has-focus-visible:outline-2 has-focus-visible:outline-electrique-400"
            >
              {{ libelleChoisir }}
              <input class="sr-only" type="file" accept="image/jpeg,image/png" capture="environment" (change)="choisir($event)" />
            </label>
          } @else {
            <span class="text-label font-semibold text-nuit-100">{{ libelleCadre }}</span>
            <div class="relative aspect-[85.6/54] w-full overflow-hidden rounded-lg border-[3px] border-electrique-400 bg-nuit-800">
              <video #video class="size-full object-cover" autoplay playsinline muted></video>
            </div>
          }
        </div>
        <footer class="flex flex-none flex-col items-center gap-6 bg-nuit-900 px-7 pt-6 pb-8">
          <p class="m-0 text-center text-label text-nuit-300">{{ libelleConseil }}</p>
          <button
            type="button"
            class="grid size-20 place-items-center rounded-full border-4 border-nuit-200 focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-electrique-400 disabled:opacity-40"
            [attr.aria-label]="libelleDeclencher"
            [disabled]="!prete()"
            (click)="declencher()"
          >
            <span class="size-16 rounded-full bg-nuit-300" aria-hidden="true"></span>
          </button>
        </footer>
      </div>
    </dialog>
  `,
})
export class PriseDeVue implements OnDestroy {
  readonly ouverte = input(false);
  readonly titre = input.required<string>();
  protected readonly libelleFermer = $localize`:@@priseDeVue.fermer:Fermer`;
  protected readonly libelleCadre = $localize`:@@priseDeVue.cadre:Placez la pièce dans le cadre`;
  protected readonly libelleConseil = $localize`:@@priseDeVue.conseil:Posez la pièce à plat, sans reflet. Le cliché est chiffré et visible uniquement par un agent KYC.`;
  protected readonly libelleDeclencher = $localize`:@@priseDeVue.declencher:Prendre la photo`;
  protected readonly libelleIndisponible = $localize`:@@priseDeVue.indisponible:L'appareil photo n'a pas pu s'ouvrir dans l'application.`;
  protected readonly libelleChoisir = $localize`:@@priseDeVue.choisir:Prendre ou choisir une photo`;

  readonly photo = output<Blob>();
  readonly fermee = output<void>();

  private readonly fenetre = viewChild.required<ElementRef<HTMLDialogElement>>('fenetre');
  private readonly video = viewChild<ElementRef<HTMLVideoElement>>('video');
  protected readonly indisponible = signal(false);
  protected readonly prete = signal(false);
  private flux: MediaStream | null = null;

  constructor() {
    effect(() => {
      const fenetre = this.fenetre().nativeElement;
      if (this.ouverte() && !fenetre.open) {
        // showModal est absent de certains moteurs de test : repli sur l'attribut open.
        typeof fenetre.showModal === 'function' ? fenetre.showModal() : fenetre.setAttribute('open', '');
        void this.allumer();
      } else if (!this.ouverte() && fenetre.open) {
        this.eteindre();
        typeof fenetre.close === 'function' ? fenetre.close() : fenetre.removeAttribute('open');
      }
    });
  }

  ngOnDestroy(): void {
    this.eteindre();
  }

  protected fermer(): void {
    this.eteindre();
    this.fermee.emit();
  }

  protected async declencher(): Promise<void> {
    const video = this.video()?.nativeElement;
    if (!video || !video.videoWidth) {
      return;
    }
    const toile = document.createElement('canvas');
    toile.width = video.videoWidth;
    toile.height = video.videoHeight;
    toile.getContext('2d')!.drawImage(video, 0, 0);
    const image = await new Promise<Blob | null>((resoudre) => toile.toBlob(resoudre, 'image/jpeg', 0.9));
    if (image) {
      this.eteindre();
      this.photo.emit(image);
    }
  }

  protected choisir(evenement: Event): void {
    const champ = evenement.target as HTMLInputElement;
    const fichier = champ.files?.[0];
    champ.value = '';
    if (fichier) {
      this.photo.emit(fichier);
    }
  }

  private async allumer(): Promise<void> {
    this.indisponible.set(false);
    this.prete.set(false);
    try {
      this.flux = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: { ideal: 'environment' }, width: { ideal: 1920 } },
        audio: false,
      });
      const video = this.video()?.nativeElement;
      if (!video || !this.ouverte()) {
        this.eteindre();
        return;
      }
      video.srcObject = this.flux;
      video.onloadedmetadata = () => this.prete.set(true);
    } catch {
      // Permission refusée ou caméra absente : le parent choisit une photo par le sélecteur du téléphone.
      this.indisponible.set(true);
    }
  }

  private eteindre(): void {
    this.flux?.getTracks().forEach((piste) => piste.stop());
    this.flux = null;
    this.prete.set(false);
  }
}
