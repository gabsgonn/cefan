import { Routes } from '@angular/router';
import { authGuard } from './core/services/appointment.service';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'agenda'
  },
  {
    path: 'login',
    loadComponent: () => import('./pages/atendimentos/atendimentos.component').then(m => m.LoginComponent)
  },
  {
    path: 'agenda',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/agenda/agenda.component').then(m => m.AgendaComponent)
  },
  {
    path: 'atendimentos',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/atendimentos/atendimentos.component').then(m => m.AtendimentosComponent)
  },
  {
    path: 'configuracoes',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/configuracoes/configuracoes.component').then(m => m.ConfiguracoesComponent)
  },
  {
    path: '**',
    redirectTo: 'agenda'
  }
];
