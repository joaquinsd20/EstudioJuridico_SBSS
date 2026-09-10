import { Routes } from '@angular/router';

export const routes: Routes = [

  /* =====================================================
     INICIO
  ===================================================== */

  {
    path: '',
    redirectTo: 'login',
    pathMatch: 'full'
  },


  /* =====================================================
     LOGIN
  ===================================================== */

  {
    path: 'login',
    loadComponent: () =>
      import('./pages/login/login.component')
        .then(m => m.LoginComponent)
  },


  /* =====================================================
     DASHBOARD
  ===================================================== */

  {
    path: 'dashboard',
    loadComponent: () =>
      import('./pages/dashboard/dashboard.component')
        .then(m => m.DashboardComponent)
  },


  /* =====================================================
     EXPEDIENTES
  ===================================================== */

  {
    path: 'expedientes',
    loadComponent: () =>
      import('./pages/expedientes/expedientes.component')
        .then(m => m.ExpedientesComponent)
  },


  /* =====================================================
     CLIENTES
  ===================================================== */

  {
    path: 'clientes',
    loadComponent: () =>
      import('./pages/clientes/clientes.component')
        .then(m => m.ClientesComponent)
  },


  /* =====================================================
     AGENDA
  ===================================================== */

  


  /* =====================================================
     ACTUACIONES
  ===================================================== */

  


  /* =====================================================
     PLAZOS
  ===================================================== */

  


  /* =====================================================
     DOCUMENTOS
  ===================================================== */

  {
    path: 'documentos',
    loadComponent: () =>
      import('./pages/documentos/documentos.component')
        .then(m => m.DocumentosComponent)
  },


  /* =====================================================
     USUARIOS
  ===================================================== */

  


  /* =====================================================
     RUTA NO ENCONTRADA
  ===================================================== */

  {
    path: '**',
    redirectTo: 'login'
  }

];