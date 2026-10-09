import { Routes } from '@angular/router';

import { roleRequis, sessionRequise } from './commun/acces';

export const routes: Routes = [
  { path: 'connexion', loadComponent: () => import('./connexion/connexion').then((m) => m.Connexion) },
  {
    path: '',
    canActivate: [sessionRequise],
    loadComponent: () => import('./structure/structure').then((m) => m.Structure),
    children: [
      { path: '', pathMatch: 'full', loadComponent: () => import('./pages/pages').then((m) => m.Tableau) },
      { path: 'refuse', loadComponent: () => import('./pages/pages').then((m) => m.Refuse) },
      { path: 'kyc/litiges', canActivate: [roleRequis('KYC')], loadComponent: () => import('./kyc/litiges').then((m) => m.Litiges) },
      {
        path: 'kyc/litiges/:id',
        canActivate: [roleRequis('KYC')],
        loadComponent: () => import('./kyc/litige').then((m) => m.EcranLitige),
      },
      { path: 'kyc', canActivate: [roleRequis('KYC')], loadComponent: () => import('./kyc/file').then((m) => m.FileKyc) },
      {
        path: 'kyc/:id',
        canActivate: [roleRequis('KYC')],
        loadComponent: () => import('./kyc/instruction').then((m) => m.InstructionKyc),
      },
      { path: 'sav/parc', canActivate: [roleRequis('SAV')], loadComponent: () => import('./sav/parc').then((m) => m.Parc) },
      {
        path: 'sav/parc/:numero',
        canActivate: [roleRequis('SAV')],
        loadComponent: () => import('./sav/fiche-bracelet').then((m) => m.FicheBracelet),
      },
      { path: 'sav/muets', canActivate: [roleRequis('SAV')], loadComponent: () => import('./sav/muets').then((m) => m.Muets) },
      { path: 'admin/agents', canActivate: [roleRequis('ADMIN')], loadComponent: () => import('./admin/agents').then((m) => m.Agents) },
      {
        path: 'admin/securite',
        canActivate: [roleRequis('ADMIN')],
        loadComponent: () => import('./admin/securite').then((m) => m.Securite),
      },
      {
        path: 'admin/supervision',
        canActivate: [roleRequis('ADMIN')],
        loadComponent: () => import('./admin/supervision').then((m) => m.Supervision),
      },
      { path: 'admin/audit', canActivate: [roleRequis('ADMIN')], loadComponent: () => import('./admin/audit').then((m) => m.Audit) },
      {
        path: 'conformite',
        canActivate: [roleRequis('ADMIN')],
        loadComponent: () => import('./admin/conformite').then((m) => m.Conformite),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
