import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { permissionGuard } from './core/guards/permission.guard';

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
     ¿OLVIDASTE TU CONTRASEÑA?
     Publicas: quien las usa no tiene sesion. La misma pantalla
     pide el correo o, con el enlace del correo, la clave nueva.
  ======================================================= */

  {
    path: 'recuperar-password',
    data: { modo: 'solicitar' },
    loadComponent: () =>
      import('./pages/recuperar-password/recuperar-password.component')
        .then(m => m.RecuperarPasswordComponent)
  },

  {
    path: 'restablecer-password',
    data: { modo: 'restablecer' },
    loadComponent: () =>
      import('./pages/recuperar-password/recuperar-password.component')
        .then(m => m.RecuperarPasswordComponent)
  },


  /* =======================================================
     CAMBIAR CONTRASEÑA
     Obligatoria mientras la contrasena sea temporal; voluntaria
     desde el panel lateral. authGuard deja pasar solo aqui.
  ======================================================= */

  {
    path: 'cambiar-password',
    loadComponent: () =>
      import('./pages/cambiar-password/cambiar-password.component')
        .then(m => m.CambiarPasswordComponent),
    canActivate: [authGuard]
  },


  /* =======================================================
     DASHBOARD
  ======================================================= */

  {
    path: 'dashboard',
    loadComponent: () =>
      import('./pages/dashboard/dashboard.component')
        .then(m => m.DashboardComponent),
    canActivate: [authGuard]
  },


  /* =======================================================
     EXPEDIENTES
  ======================================================= */

  {
    path: 'expedientes',
    loadComponent: () =>
      import('./pages/expedientes/expedientes.component')
        .then(m => m.ExpedientesComponent),
    canActivate: [authGuard]
  },


  /* =======================================================
     CLIENTES
  ======================================================= */

  {
    path: 'clientes',
    loadComponent: () =>
      import('./pages/clientes/clientes.component')
        .then(m => m.ClientesComponent),
    canActivate: [authGuard]
  },


  /* =======================================================
     AGENDA
  ======================================================= */

  {
    path: 'agenda',
    loadComponent: () =>
      import('./pages/agenda/agenda.component')
        .then(m => m.AgendaComponent),
    canActivate: [authGuard]
  },


  /* =======================================================
     ACTUACIONES
  ======================================================= */

  {
    path: 'actuaciones',
    loadComponent: () =>
      import('./pages/actuaciones/actuaciones.component')
        .then(m => m.ActuacionesComponent),
    canActivate: [authGuard]
  },


  /* =======================================================
     PLAZOS
  ======================================================= */

  {
    path: 'plazos',
    loadComponent: () =>
      import('./pages/plazos/plazos.component')
        .then(m => m.PlazosComponent),
    canActivate: [authGuard]
  },


  /* =======================================================
     TAREAS
  ======================================================= */

  {
    path: 'tareas',
    loadComponent: () =>
      import('./pages/tareas/tareas.component')
        .then(m => m.TareasComponent),
    canActivate: [authGuard]
  },


  /* =======================================================
     DOCUMENTOS
  ======================================================= */

  {
    path: 'documentos',
    loadComponent: () =>
      import('./pages/documentos/documentos.component')
        .then(m => m.DocumentosComponent),
    canActivate: [authGuard]
  },


  /* =======================================================
     ESCRITOS CON IA
  ======================================================= */

  {
    path: 'escritos-ia',
    loadComponent: () =>
      import('./pages/escritos-ia/escritos-ia.component')
        .then(m => m.EscritosIaComponent),
    canActivate: [authGuard]
  },


  /* =======================================================
     BIBLIOTECA JURÍDICA
  ======================================================= */

  {
    path: 'biblioteca-juridica',
    loadComponent: () =>
      import('./pages/biblioteca-juridica/biblioteca-juridica.component')
        .then(m => m.BibliotecaJuridicaComponent),
    canActivate: [authGuard]
  },


  /* =======================================================
     MONITOR JUDICIAL
  ======================================================= */

  {
    path: 'monitor-judicial',
    loadComponent: () =>
      import('./pages/monitor-judicial/monitor-judicial.component')
        .then(m => m.MonitorJudicialComponent),
    canActivate: [authGuard]
  },


  /* =======================================================
     REPORTES
  ======================================================= */

  {
    path: 'reportes',
    loadComponent: () =>
      import('./pages/reportes/reportes.component')
        .then(m => m.ReportesComponent),
    canActivate: [authGuard]
  },


  /* =======================================================
     FACTURACIÓN
  ======================================================= */

  {
    path: 'facturacion',
    loadComponent: () =>
      import('./pages/facturacion/facturacion.component')
        .then(m => m.FacturacionComponent),
    canActivate: [authGuard]
  },


  /* =======================================================
     USUARIOS
  ======================================================= */

  {
    path: 'usuarios',
    loadComponent: () =>
      import('./pages/usuarios/usuarios.component')
        .then(m => m.UsuariosComponent),
    canActivate: [authGuard]
  },


  /* =======================================================
     MI CONFIGURACIÓN — preferencia propia por usuario
  ======================================================= */

  {
    path: 'mi-configuracion',
    loadComponent: () =>
      import('./pages/mi-configuracion/mi-configuracion.component')
        .then(m => m.MiConfiguracionComponent),
    canActivate: [authGuard]
  },

  /* =======================================================
     CONFIGURACIÓN DEL ESTUDIO — permiso administrativo
  ======================================================= */

  {
    path: 'configuracion',
    loadComponent: () =>
      import('./pages/configuracion/configuracion.component')
        .then(m => m.ConfiguracionComponent),
    canActivate: [authGuard, permissionGuard],
    data: { permission: 'CONFIGURACION_EDITAR' }
  },


  /* =======================================================
     CUALQUIER RUTA INCORRECTA
  ======================================================= */

  {
    path: '**',
    redirectTo: 'login'
  }

];