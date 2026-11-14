import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'agenda'
  },
  {
    path: 'login',
    loadComponent: () => import('./pages/login/login.component').then(m => m.LoginComponent)
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
    path: 'profissionais',
    canActivate: [authGuard, roleGuard(['OWNER', 'ADMIN'])],
    loadComponent: () => import('./pages/profissionais/profissionais.component').then(m => m.ProfissionaisComponent)
  },
  {
    path: 'configuracoes',
    canActivate: [authGuard, roleGuard(['OWNER', 'ADMIN'])],
    loadComponent: () => import('./pages/configuracoes/configuracoes.component').then(m => m.ConfiguracoesComponent)
  },
  {
    path: '**',
    redirectTo: 'agenda'
  }
];
