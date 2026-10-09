import { Routes } from '@angular/router';

const SUFFIXE = ' · FasoGuardian';

export const routes: Routes = [
  { path: '', pathMatch: 'full', title: 'FasoGuardian — Identifier, localiser, alerter', loadComponent: () => import('./pages/accueil').then((m) => m.Accueil) },
  { path: 'bracelet', title: 'Le bracelet' + SUFFIXE, loadComponent: () => import('./pages/bracelet').then((m) => m.PageBracelet) },
  { path: 'fonctionnement', title: 'Fonctionnement' + SUFFIXE, loadComponent: () => import('./pages/produit').then((m) => m.Fonctionnement) },
  { path: 'offres', title: 'Offres' + SUFFIXE, loadComponent: () => import('./pages/produit').then((m) => m.Offres) },
  { path: 'ecoles', title: 'Établissements scolaires' + SUFFIXE, loadComponent: () => import('./pages/produit').then((m) => m.Ecoles) },
  { path: 'securite', title: 'Sécurité et données' + SUFFIXE, loadComponent: () => import('./pages/produit').then((m) => m.Securite) },
  { path: 'points-relais', title: 'Points relais' + SUFFIXE, loadComponent: () => import('./pages/aide').then((m) => m.PointsRelais) },
  { path: 'faq', title: 'Questions fréquentes' + SUFFIXE, loadComponent: () => import('./pages/aide').then((m) => m.Faq) },
  { path: 'contact', title: 'Contact' + SUFFIXE, loadComponent: () => import('./pages/aide').then((m) => m.Contact) },
  { path: 'mentions', title: 'Mentions légales' + SUFFIXE, loadComponent: () => import('./pages/legal').then((m) => m.Mentions) },
  { path: 'confidentialite', title: 'Confidentialité' + SUFFIXE, loadComponent: () => import('./pages/legal').then((m) => m.Confidentialite) },
  { path: 'cgu', title: 'Conditions générales' + SUFFIXE, loadComponent: () => import('./pages/legal').then((m) => m.Cgu) },
  { path: '**', title: 'Page introuvable' + SUFFIXE, loadComponent: () => import('./pages/aide').then((m) => m.Introuvable) },
];
