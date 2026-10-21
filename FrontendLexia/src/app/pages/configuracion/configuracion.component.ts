import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, OnInit, computed, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { catchError, finalize, map, Observable, of, TimeoutError } from 'rxjs';
import { PanelComponent } from '../panel/panel.component';
import { AuthService } from '../../core/services/auth.service';
import {
  ActualizarEstudioConfiguracion,
  ConfiguracionService,
  EstudioConfiguracion,
  ParametrosEstudio,
  RolConfiguracion,
} from '../../core/services/configuracion.service';

type SeccionConfiguracion = 'estudio' | 'parametros' | 'roles' | 'seguridad' | 'integraciones';
type ResultadoCarga<T> = { ok: true; valor: T } | { ok: false; error: unknown };

@Component({
  selector: 'app-configuracion',
  standalone: true,
  imports: [PanelComponent, FormsModule],
  templateUrl: './configuracion.component.html',
  styleUrl: './configuracion.component.css',
})
export class ConfiguracionComponent implements OnInit {
  private readonly api = inject(ConfiguracionService);
  private readonly auth = inject(AuthService);
  // Angular 21 funciona sin Zone.js por defecto. Las mutaciones de propiedades
  // normales realizadas dentro de suscripciones RxJS deben notificar el render.
  private readonly cdr = inject(ChangeDetectorRef);

  readonly usuario = this.auth.usuario;
  readonly puedeGestionarUsuarios = computed(() => this.auth.tienePermiso('USUARIO_GESTIONAR'));
  readonly secciones: { id: SeccionConfiguracion; titulo: string }[] = [
    { id: 'estudio', titulo: 'Estudio jurídico' },
    { id: 'parametros', titulo: 'Reglas operativas' },
    { id: 'roles', titulo: 'Roles disponibles' },
    { id: 'seguridad', titulo: 'Seguridad y respaldos' },
    { id: 'integraciones', titulo: 'Integraciones' },
  ];

  seccion: SeccionConfiguracion = 'estudio';
  cargando = true;
  cargandoEstudio = true;
  cargandoParametros = true;
  estudioDisponible = false;
  parametrosDisponibles = false;
  guardando = false;
  mensajeExito = '';
  mensajeError = '';
  rolesCargando = false;
  rolesError = '';
  roles: RolConfiguracion[] = [];
  private rolesConsultados = false;

  estudio: ActualizarEstudioConfiguracion = {
    razonSocial: '', nombreComercial: null, ruc: '', direccion: null,
    telefono: null, email: null, zonaHoraria: 'America/Lima', monedaBase: 'PEN',
  };
  parametros: ParametrosEstudio = {
    dominioCorreoInterno: '', intentosLoginMaximos: 5,
    plazoColchonInternoDias: 2, alertasDiasPrevios: [15, 10, 7, 5, 3, 1, 0],
  };
  alertasDiasTexto = '15, 10, 7, 5, 3, 1, 0';

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.cargando = true;
    this.cargandoEstudio = true;
    this.cargandoParametros = true;
    this.estudioDisponible = false;
    this.parametrosDisponibles = false;
    this.mensajeError = '';
    this.mensajeExito = '';

    // Las dos secciones cargan de forma independiente: un endpoint atascado o
    // con error no debe bloquear toda la pantalla ni ocultar la otra sección.
    this.capturar(this.api.obtenerEstudio()).pipe(
      finalize(() => {
        this.cargandoEstudio = false;
        this.actualizarEstadoCarga();
        this.cdr.markForCheck();
      }),
    ).subscribe(resultado => {
      if (resultado.ok) {
        this.aplicarEstudio(resultado.valor);
        this.estudioDisponible = true;
      } else {
        this.estudioDisponible = false;
        this.agregarErrorCarga(this.errorDe(resultado.error,
          'No se pudo cargar la información institucional.'));
      }
    });

    this.capturar(this.api.obtenerParametros()).pipe(
      finalize(() => {
        this.cargandoParametros = false;
        this.actualizarEstadoCarga();
        this.cdr.markForCheck();
      }),
    ).subscribe(resultado => {
      if (resultado.ok) {
        this.parametros = {
          ...resultado.valor,
          alertasDiasPrevios: [...(resultado.valor.alertasDiasPrevios ?? [])],
        };
        this.alertasDiasTexto = this.parametros.alertasDiasPrevios.join(', ');
        this.parametrosDisponibles = true;
      } else {
        this.parametrosDisponibles = false;
        this.agregarErrorCarga(this.errorDe(resultado.error,
          'No se pudieron cargar las reglas operativas.'));
      }
    });
  }

  private capturar<T>(peticion: Observable<T>): Observable<ResultadoCarga<T>> {
    return peticion.pipe(
      map(valor => ({ ok: true as const, valor })),
      catchError(error => of({ ok: false as const, error })),
    );
  }

  private actualizarEstadoCarga(): void {
    this.cargando = this.cargandoEstudio || this.cargandoParametros;
  }

  private agregarErrorCarga(mensaje: string): void {
    this.mensajeError = [...new Set([this.mensajeError, mensaje].filter(Boolean))].join(' ');
  }

  seleccionarSeccion(seccion: SeccionConfiguracion): void {
    this.seccion = seccion;
    this.mensajeExito = '';
    if (this.estudioDisponible && this.parametrosDisponibles) this.mensajeError = '';
    if (seccion === 'roles' && !this.rolesConsultados) this.cargarRoles();
  }

  guardarSeccion(): void {
    if (this.seccion === 'estudio') this.guardarEstudio();
    else if (this.seccion === 'parametros') this.guardarParametros();
  }

  guardarEstudio(): void {
    if (this.guardando) return;
    if (!this.estudioDisponible) {
      this.error('No se puede guardar: la información institucional no se ha cargado correctamente. Pulsa Recargar e inténtalo otra vez.');
      return;
    }
    if (!this.validarEstudio()) return;
    this.guardando = true;
    this.mensajeError = '';
    this.mensajeExito = '';
    const request: ActualizarEstudioConfiguracion = {
      razonSocial: this.estudio.razonSocial.trim(),
      nombreComercial: this.limpiar(this.estudio.nombreComercial),
      ruc: this.estudio.ruc.trim(),
      direccion: this.limpiar(this.estudio.direccion),
      telefono: this.limpiar(this.estudio.telefono),
      email: this.limpiar(this.estudio.email),
      zonaHoraria: this.estudio.zonaHoraria.trim(),
      monedaBase: this.estudio.monedaBase,
    };
    this.api.actualizarEstudio(request).subscribe({
      next: estudio => {
        this.aplicarEstudio(estudio);
        this.mensajeExito = 'La información institucional se guardó correctamente.';
        this.guardando = false;
        this.cdr.markForCheck();
      },
      error: error => {
        this.mensajeError = this.errorDe(error, 'No se pudo guardar la información institucional.');
        this.guardando = false;
        this.cdr.markForCheck();
      },
    });
  }

  guardarParametros(): void {
    if (this.guardando) return;
    if (!this.parametrosDisponibles) {
      this.error('No se puede guardar: las reglas operativas no se han cargado correctamente. Pulsa Recargar e inténtalo otra vez.');
      return;
    }
    if (!this.validarParametros()) return;
    this.guardando = true;
    this.mensajeError = '';
    this.mensajeExito = '';
    const request: ParametrosEstudio = {
      ...this.parametros,
      dominioCorreoInterno: this.parametros.dominioCorreoInterno.trim().toLowerCase(),
      alertasDiasPrevios: this.parsearAlertas(),
    };
    this.api.actualizarParametros(request).subscribe({
      next: parametros => {
        this.parametros = parametros;
        this.alertasDiasTexto = parametros.alertasDiasPrevios.join(', ');
        this.mensajeExito = 'Las reglas operativas se guardaron correctamente.';
        this.guardando = false;
        this.cdr.markForCheck();
      },
      error: error => {
        this.mensajeError = this.errorDe(error, 'No se pudieron guardar las reglas operativas.');
        this.guardando = false;
        this.cdr.markForCheck();
      },
    });
  }

  recargar(): void {
    this.mensajeExito = '';
    this.mensajeError = '';
    this.cargar();
  }

  private cargarRoles(): void {
    if (!this.puedeGestionarUsuarios()) {
      this.rolesError = 'Tu usuario puede consultar la configuración institucional, pero no tiene permiso para consultar el catálogo de roles.';
      this.rolesConsultados = true;
      return;
    }
    this.rolesCargando = true;
    this.api.obtenerRolesAsignables().subscribe({
      next: roles => {
        this.roles = roles;
        this.rolesCargando = false;
        this.rolesConsultados = true;
        this.cdr.markForCheck();
      },
      error: error => {
        this.rolesError = this.errorDe(error, 'No se pudo consultar el catálogo de roles.');
        this.rolesCargando = false;
        this.rolesConsultados = true;
        this.cdr.markForCheck();
      },
    });
  }

  private aplicarEstudio(estudio: EstudioConfiguracion): void {
    this.estudio = {
      razonSocial: estudio.razonSocial ?? '',
      nombreComercial: estudio.nombreComercial,
      ruc: estudio.ruc ?? '',
      direccion: estudio.direccion,
      telefono: estudio.telefono,
      email: estudio.email,
      zonaHoraria: estudio.zonaHoraria ?? 'America/Lima',
      monedaBase: estudio.monedaBase ?? 'PEN',
    };
  }

  private validarEstudio(): boolean {
    if (!this.estudio.razonSocial.trim()) return this.error('La razón social es obligatoria.');
    if (!/^\d{11}$/.test(this.estudio.ruc.trim())) return this.error('El RUC debe contener exactamente 11 dígitos.');
    if (this.estudio.email?.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.estudio.email.trim())) {
      return this.error('El correo institucional no tiene un formato válido.');
    }
    if (!this.estudio.zonaHoraria.trim()) return this.error('Selecciona una zona horaria.');
    if (!/^[A-Z]{3}$/.test(this.estudio.monedaBase)) return this.error('Selecciona una moneda válida.');
    return true;
  }

  private validarParametros(): boolean {
    const dominio = this.parametros.dominioCorreoInterno.trim();
    if (!/^[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?$/i.test(dominio) || !dominio.includes('.')) {
      return this.error('Introduce un dominio válido, por ejemplo estudio.pe.');
    }
    if (!Number.isInteger(this.parametros.intentosLoginMaximos) || this.parametros.intentosLoginMaximos < 3 || this.parametros.intentosLoginMaximos > 10) {
      return this.error('Los intentos de acceso deben estar entre 3 y 10.');
    }
    if (!Number.isInteger(this.parametros.plazoColchonInternoDias) || this.parametros.plazoColchonInternoDias < 0 || this.parametros.plazoColchonInternoDias > 30) {
      return this.error('La anticipación interna debe estar entre 0 y 30 días.');
    }
    if (!this.alertasDiasTexto.trim()) return this.error('Indica los días de alerta separados por comas.');
    const alertas = this.parsearAlertas();
    if (!alertas.length || alertas.length > 20 || alertas.some(d => !Number.isInteger(d) || d < 0 || d > 365)) {
      return this.error('Indica de 1 a 20 días de alerta, separados por comas y entre 0 y 365.');
    }
    if (new Set(alertas).size !== alertas.length) return this.error('No repitas los días de alerta.');
    this.parametros.alertasDiasPrevios = alertas;
    return true;
  }

  private parsearAlertas(): number[] {
    const partes = this.alertasDiasTexto.split(',').map(valor => valor.trim());
    if (!partes.length || partes.some(valor => !/^\d+$/.test(valor))) return [];
    return partes.map(valor => Number(valor));
  }

  private error(mensaje: string): false {
    this.mensajeError = mensaje;
    this.mensajeExito = '';
    return false;
  }

  private limpiar(valor: string | null | undefined): string | null {
    const limpio = valor?.trim();
    return limpio ? limpio : null;
  }

  private errorDe(error: unknown, fallback: string): string {
    if (error instanceof TimeoutError) {
      return 'El servicio de configuración no respondió en 8 segundos. Comprueba que el backend esté activo y revisa su consola.';
    }

    if (error instanceof HttpErrorResponse) {
      const detalle = error.error as { mensaje?: string; message?: string; codigo?: string } | null;
      const mensajeServidor = detalle?.mensaje || detalle?.message;
      if (error.status === 0) {
        return 'No se pudo conectar con el backend. Comprueba que Spring Boot esté activo en el puerto 8080 y que Angular esté usando proxy.conf.js.';
      }
      if (error.status === 401) return 'La sesión ya no es válida. Vuelve a iniciar sesión.';
      if (error.status === 403) return 'El servidor denegó el acceso. Comprueba que la cuenta tenga CONFIGURACION_EDITAR y que la base de datos tenga aplicada la migración de permisos.';
      if (error.status === 404) return 'El endpoint de configuración no existe en el backend activo. Comprueba que hayas incorporado los archivos Java de configuración y reiniciado Spring Boot.';
      if (error.status >= 500) return mensajeServidor || 'El backend encontró un error al consultar la configuración. Revisa la consola de Spring Boot.';
      return mensajeServidor || `No se pudo completar la solicitud (HTTP ${error.status}).`;
    }

    const detalle = error as { error?: { mensaje?: string; message?: string }; message?: string };
    return detalle?.error?.mensaje || detalle?.error?.message || detalle?.message || fallback;
  }
}
