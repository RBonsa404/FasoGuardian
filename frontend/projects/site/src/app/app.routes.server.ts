import { RenderMode, ServerRoute } from '@angular/ssr';

/** Toutes les pages sont statiques : elles sont rendues une fois, à la construction. */
export const serverRoutes: ServerRoute[] = [
  { path: '**', renderMode: RenderMode.Prerender },
];
