import { Routes } from '@angular/router';

export const routes: Routes = [

  /* =======================================================
     RUTA INICIAL
  ======================================================= */

  {
    path: '',
    redirectTo: 'login',
    pathMatch: 'full'
  },


  /* =======================================================
     LOGIN
  ======================================================= */

  {
    path: 'login',
    loadComponent: () =>
      import('./pages/login/login.component')
        .then(m => m.LoginComponent)
  },


  /* =======================================================
     DASHBOARD
  ======================================================= */

  {
    path: 'dashboard',
    loadComponent: () =>
      import('./pages/dashboard/dashboard.component')
        .then(m => m.DashboardComponent)
  },


  /* =======================================================
     EXPEDIENTES
  ======================================================= */

  {
    path: 'expedientes',
    loadComponent: () =>
      import('./pages/expedientes/expedientes.component')
        .then(m => m.ExpedientesComponent)
  },


  /* =======================================================
     CLIENTES
  ======================================================= */

  {
    path: 'clientes',
    loadComponent: () =>
      import('./pages/clientes/clientes.component')
        .then(m => m.ClientesComponent)
  },


  /* =======================================================
     AGENDA
  ======================================================= */

  {
    path: 'agenda',
    loadComponent: () =>
      import('./pages/agenda/agenda.component')
        .then(m => m.AgendaComponent)
  },


  /* =======================================================
     ACTUACIONES
  ======================================================= */

  {
    path: 'actuaciones',
    loadComponent: () =>
      import('./pages/actuaciones/actuaciones.component')
        .then(m => m.ActuacionesComponent)
  },


  /* =======================================================
     PLAZOS
  ======================================================= */

  {
    path: 'plazos',
    loadComponent: () =>
      import('./pages/plazos/plazos.component')
        .then(m => m.PlazosComponent)
  },


  /* =======================================================
     TAREAS
  ======================================================= */

  {
    path: 'tareas',
    loadComponent: () =>
      import('./pages/tareas/tareas.component')
        .then(m => m.TareasComponent)
  },


  /* =======================================================
     DOCUMENTOS
  ======================================================= */

  {
    path: 'documentos',
    loadComponent: () =>
      import('./pages/documentos/documentos.component')
        .then(m => m.DocumentosComponent)
  },


  /* =======================================================
     ESCRITOS CON IA
  ======================================================= */

  {
    path: 'escritos-ia',
    loadComponent: () =>
      import('./pages/escritos-ia/escritos-ia.component')
        .then(m => m.EscritosIaComponent)
  },


  /* =======================================================
     BIBLIOTECA JURÍDICA
  ======================================================= */

  {
    path: 'biblioteca-juridica',
    loadComponent: () =>
      import('./pages/biblioteca-juridica/biblioteca-juridica.component')
        .then(m => m.BibliotecaJuridicaComponent)
  },


  /* =======================================================
     MONITOR JUDICIAL
  ======================================================= */

  {
    path: 'monitor-judicial',
    loadComponent: () =>
      import('./pages/monitor-judicial/monitor-judicial.component')
        .then(m => m.MonitorJudicialComponent)
  },


  /* =======================================================
     REPORTES
  ======================================================= */

  {
    path: 'reportes',
    loadComponent: () =>
      import('./pages/reportes/reportes.component')
        .then(m => m.ReportesComponent)
  },


  /* =======================================================
     FACTURACIÓN
  ======================================================= */

  {
    path: 'facturacion',
    loadComponent: () =>
      import('./pages/facturacion/facturacion.component')
        .then(m => m.FacturacionComponent)
  },


  /* =======================================================
     USUARIOS
  ======================================================= */

  {
    path: 'usuarios',
    loadComponent: () =>
      import('./pages/usuarios/usuarios.component')
        .then(m => m.UsuariosComponent)
  },


  /* =======================================================
     CONFIGURACIÓN
  ======================================================= */

  {
    path: 'configuracion',
    loadComponent: () =>
      import('./pages/configuracion/configuracion.component')
        .then(m => m.ConfiguracionComponent)
  },


  /* =======================================================
     CUALQUIER RUTA INCORRECTA
  ======================================================= */

  {
    path: '**',
    redirectTo: 'login'
  }

];