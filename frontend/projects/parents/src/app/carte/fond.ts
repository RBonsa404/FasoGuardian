import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  InjectionToken,
  ViewEncapsulation,
  afterNextRender,
  effect,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import type { LayerGroup, Map as CarteLeaflet } from 'leaflet';

import { PointGeo, SafeZone } from 'api';

/** Fournisseur des tuiles du fond de carte. */
export interface FondDeCarte {
  readonly url: string;
  readonly attribution: string;
  readonly zoomMaximal: number;
}

/**
 * Fond de carte par défaut : tuiles publiques d'OpenStreetMap, suffisantes pour le développement et le
 * pilote restreint. Un fournisseur sous contrat se branche ici sans toucher aux écrans.
 */
export const FOND_DE_CARTE = new InjectionToken<FondDeCarte>('FOND_DE_CARTE', {
  providedIn: 'root',
  factory: () => ({
    url: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
    attribution: '© OpenStreetMap',
    zoomMaximal: 19,
  }),
});

/** Position à afficher : le cercle d'incertitude a pour rayon la précision de la mesure. */
export interface PositionSurCarte extends PointGeo {
  readonly precisionM: number;
  /** Initiale affichée dans le repère. */
  readonly initiale: string;
  /** Position ancienne ou approximative : repère gris, sans halo. */
  readonly attenuee: boolean;
}

/** Zone en cours de tracé dans l'éditeur. */
export interface Brouillon {
  readonly centre: PointGeo | null;
  readonly rayonM: number;
  readonly sommets: readonly PointGeo[];
}

const OUAGADOUGOU: [number, number] = [12.3714, -1.5197];

/**
 * Carte Leaflet partagée par les écrans de position, de zones et de trajets. Leaflet et sa feuille de style
 * ne sont chargés qu'avec ces écrans, jamais au démarrage de l'application (budget de 250 Ko).
 */
@Component({
  selector: 'app-carte',
  changeDetection: ChangeDetectionStrategy.OnPush,
  encapsulation: ViewEncapsulation.None,
  styleUrl: './fond.css',
  template: `<div #hote class="fg-carte size-full" role="application" [attr.aria-label]="libelle()"></div>`,
  host: { class: 'block' },
})
export class Carte {
  readonly libelle = input.required<string>();
  readonly position = input<PositionSurCarte | null>(null);
  readonly zones = input<readonly SafeZone[]>([]);
  readonly trace = input<readonly PointGeo[]>([]);
  readonly brouillon = input<Brouillon | null>(null);
  /** Point touché sur la carte (éditeur de zone). */
  readonly touche = output<PointGeo>();

  private readonly hote = viewChild.required<ElementRef<HTMLElement>>('hote');
  private readonly fond = inject(FOND_DE_CARTE);
  private readonly pret = signal<{ L: typeof import('leaflet'); carte: CarteLeaflet; calque: LayerGroup } | null>(null);
  private cadre = '';

  constructor() {
    const detruire = inject(DestroyRef);
    afterNextRender(async () => {
      const L = await import('leaflet');
      const carte = L.map(this.hote().nativeElement, { zoomControl: false, attributionControl: true }).setView(OUAGADOUGOU, 13);
      carte.attributionControl.setPrefix(false);
      L.tileLayer(this.fond.url, { attribution: this.fond.attribution, maxZoom: this.fond.zoomMaximal }).addTo(carte);
      carte.on('click', (evenement) => this.touche.emit({ latitude: evenement.latlng.lat, longitude: evenement.latlng.lng }));
      detruire.onDestroy(() => carte.remove());
      this.pret.set({ L, carte, calque: L.layerGroup().addTo(carte) });
    });
    effect(() => this.dessiner());
  }

  private dessiner(): void {
    const pret = this.pret();
    if (!pret) {
      return;
    }
    const { L, carte, calque } = pret;
    calque.clearLayers();
    const etendue: [number, number][] = [];

    for (const zone of this.zones()) {
      const classe = zone.statut === 'SUSPENDUE' || !zone.dansLaPlage ? 'fg-zone fg-zone-inactive' : zone.sortieEnCours ? 'fg-zone fg-zone-sortie' : 'fg-zone';
      if (zone.centre && zone.rayonM) {
        L.circle([zone.centre.latitude, zone.centre.longitude], { radius: zone.rayonM, className: classe }).addTo(calque);
        etendue.push(...autour(zone.centre, zone.rayonM));
      } else if (zone.sommets) {
        const sommets = zone.sommets.map((s): [number, number] => [s.latitude, s.longitude]);
        L.polygon(sommets, { className: classe }).addTo(calque);
        etendue.push(...sommets);
      }
    }

    const trace = this.trace().map((p): [number, number] => [p.latitude, p.longitude]);
    if (trace.length > 0) {
      L.polyline(trace, { className: 'fg-trace' }).addTo(calque);
      L.circleMarker(trace[0], { radius: 5, className: 'fg-trace-borne' }).addTo(calque);
      L.circleMarker(trace[trace.length - 1], { radius: 7, className: 'fg-trace-borne fg-trace-fin' }).addTo(calque);
      etendue.push(...trace);
    }

    const brouillon = this.brouillon();
    if (brouillon?.centre) {
      L.circle([brouillon.centre.latitude, brouillon.centre.longitude], { radius: brouillon.rayonM, className: 'fg-zone fg-zone-brouillon' }).addTo(calque);
      L.circleMarker([brouillon.centre.latitude, brouillon.centre.longitude], { radius: 6, className: 'fg-sommet' }).addTo(calque);
      etendue.push(...autour(brouillon.centre, brouillon.rayonM));
    }
    if (brouillon && brouillon.sommets.length > 0) {
      const sommets = brouillon.sommets.map((s): [number, number] => [s.latitude, s.longitude]);
      (sommets.length > 2 ? L.polygon(sommets, { className: 'fg-zone fg-zone-brouillon' }) : L.polyline(sommets, { className: 'fg-zone fg-zone-brouillon' })).addTo(calque);
      sommets.forEach((sommet) => L.circleMarker(sommet, { radius: 6, className: 'fg-sommet' }).addTo(calque));
    }

    const position = this.position();
    if (position) {
      const point: [number, number] = [position.latitude, position.longitude];
      L.circle(point, { radius: position.precisionM, className: 'fg-incertitude' }).addTo(calque);
      const repere = L.divIcon({
        className: '',
        iconSize: [44, 44],
        iconAnchor: [22, 22],
        html: `<span class="fg-repere${position.attenuee ? ' fg-repere-attenue' : ''}"><span>${echapper(position.initiale)}</span></span>`,
      });
      L.marker(point, { icon: repere, keyboard: false, interactive: false }).addTo(calque);
      etendue.push(...autour(position, Math.max(position.precisionM, 150)));
    }

    // La vue n'est recadrée que lorsque ce qui est montré change (nature des éléments, cercle en cours de tracé),
    // pas à chaque rafraîchissement de la position.
    const cercle = brouillon?.centre ? `${brouillon.centre.latitude.toFixed(5)},${brouillon.centre.longitude.toFixed(5)},${brouillon.rayonM}` : '';
    const cadre = `${this.zones().length}|${trace.length}|${position ? 'p' : ''}|${cercle}`;
    if (etendue.length > 0 && cadre !== this.cadre && !brouillon?.sommets.length) {
      this.cadre = cadre;
      carte.fitBounds(L.latLngBounds(etendue), { padding: [32, 32], maxZoom: 17 });
    }
  }
}

/** Quatre points cardinaux à la distance donnée, pour inclure un cercle dans le cadrage. */
function autour(centre: PointGeo, rayonM: number): [number, number][] {
  const dLat = rayonM / 111_320;
  const dLon = rayonM / (111_320 * Math.cos((centre.latitude * Math.PI) / 180));
  return [
    [centre.latitude + dLat, centre.longitude],
    [centre.latitude - dLat, centre.longitude],
    [centre.latitude, centre.longitude + dLon],
    [centre.latitude, centre.longitude - dLon],
  ];
}

function echapper(texte: string): string {
  return texte.replace(/[&<>"']/g, '');
}
