import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subject, catchError, debounceTime, distinctUntilChanged, finalize, of, startWith, switchMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ErrorRespuesta } from '../../core/models/auth.models';
import {
  CargaDetallada, CredencialTemporal, FiltroEstadoUsuario, GuardarUsuario, ReasignarRequest,
  ResultadoReasignacion, ResumenUsuarios, RolOpcion, UsuarioDetalle, UsuarioLista
} from '../../core/models/usuario.models';
import { AuthService } from '../../core/services/auth.service';
import { UsuarioService } from '../../core/services/usuario.service';
import { FotoUsuarioComponent } from '../../core/components/foto-usuario.component';
import { PanelComponent } from '../panel/panel.component';

/** Accion pendiente de confirmar: desactivar o restablecer no se hacen de un clic. */
interface Confirmacion {
  titulo: string;
  mensaje: string;
  boton: string;
  peligrosa: boolean;
  ejecutar: () => void;
}

function formularioVacio(): GuardarUsuario {
  return {
    nombres: '', apellidos: '', email: '', cargo: '', telefono: '',
    tipoDocumento: '', numeroDocumento: '', numeroColegiatura: '', roles: []
  };
}

/**
 * Gestion del personal del estudio (RF-01).
 *
 * La maqueta traia columnas de Salario y un boton de CV: no se implementan.
 * La regla 12 de CLAUDE.md saca los datos sensibles del empleado de `usuarios`
 * (esa tabla se carga en cada peticion) y el CV seria un binario en MySQL
 * (regla 7). Iran a `empleado_datos` cuando exista.
 *
 * Estado en signals: la aplicacion no usa zone.js, asi que un campo normal
 * modificado dentro de un subscribe no repintaria la vista.
 */
@Component({
  selector: 'app-usuarios',
  standalone: true,
  imports: [PanelComponent, FotoUsuarioComponent, FormsModule, DatePipe],
  templateUrl: './usuarios.component.html',
  styleUrl: './usuarios.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class UsuariosComponent implements OnInit, OnDestroy {

  private readonly service = inject(UsuarioService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  // ---- datos ---------------------------------------------------------------
  readonly usuarios = signal<UsuarioLista[]>([]);
  readonly totales = signal<ResumenUsuarios>({ total: 0, activos: 0, inactivos: 0, bloqueados: 0 });
  readonly roles = signal<RolOpcion[]>([]);
  readonly cargando = signal(true);
  readonly sinPermiso = signal(false);
  readonly errorPagina = signal<string | null>(null);
  readonly accionEnCurso = signal(false);

  // ---- filtros / paginacion ------------------------------------------------
  texto = '';
  rol = '';
  estado: FiltroEstadoUsuario = '';
  readonly pagina = signal(0);
  readonly tamano = signal(20);
  readonly totalElementos = signal(0);
  readonly totalPaginas = signal(0);
  readonly finPagina = computed(() => Math.min((this.pagina() + 1) * this.tamano(), this.totalElementos()));
  readonly tamanosPagina = [10, 20, 40, 100] as const;
  private readonly busqueda = new Subject<string>();
  private readonly recargaTabla = new Subject<void>();

  // ---- modales -------------------------------------------------------------
  readonly detalle = signal<UsuarioDetalle | null>(null);
  readonly formularioAbierto = signal(false);
  readonly editando = signal<string | null>(null);
  readonly guardando = signal(false);
  readonly errorFormulario = signal<string | null>(null);
  readonly credencial = signal<CredencialTemporal | null>(null);
  readonly copiado = signal(false);
  readonly confirmacion = signal<Confirmacion | null>(null);
  formulario: GuardarUsuario = formularioVacio();
  fotoSeleccionada: File | null = null;
  vistaPreviaFoto: string | null = null;
  posicionFotoX = 50;
  posicionFotoY = 50;
  readonly diametroRecorte = 300;
  // Son signals porque las dimensiones se obtienen de un evento Image.onload,
  // fuera del ciclo de detección de cambios del componente OnPush/zoneless.
  readonly anchoFotoRender = signal(this.diametroRecorte);
  readonly altoFotoRender = signal(this.diametroRecorte);
  readonly anchoSuperficieFoto = computed(() => this.anchoFotoRender()
    + Math.max(0, this.anchoFotoRender() - this.diametroRecorte) + 60);
  readonly altoSuperficieFoto = computed(() => this.altoFotoRender()
    + Math.max(0, this.altoFotoRender() - this.diametroRecorte) + 60);
  readonly arrastrandoFoto = signal(false);
  readonly procesandoEncuadre = signal(false);
  readonly fotoAplicada = signal(false);

  private urlVistaPreviaLocal: string | null = null;
  private urlFotoAplicadaLocal: string | null = null;
  private fotoProcesadaParaSubir: File | null = null;
  private inicioArrastreFoto: { x: number; y: number; escala: number; posicionX: number; posicionY: number } | null = null;

  ngOnDestroy(): void {
    this.liberarVistaPreviaLocal();
  }

  /** Centra el recorte sin modificar la imagen original seleccionada. */
  centrarFoto(): void {
    this.posicionFotoX = 50;
    this.posicionFotoY = 50;
  }

  /** Posición de la imagen bajo el círculo de recorte; el lienzo deja visible la foto completa. */
  izquierdaFoto(): number {
    return (this.anchoSuperficieFoto() - this.diametroRecorte) / 2
      - Math.max(0, this.anchoFotoRender() - this.diametroRecorte) * this.posicionFotoX / 100;
  }

  superiorFoto(): number {
    return (this.altoSuperficieFoto() - this.diametroRecorte) / 2
      - Math.max(0, this.altoFotoRender() - this.diametroRecorte) * this.posicionFotoY / 100;
  }

  anchoFotoPorcentaje(): number { return this.anchoFotoRender() / this.anchoSuperficieFoto() * 100; }
  altoFotoPorcentaje(): number { return this.altoFotoRender() / this.altoSuperficieFoto() * 100; }
  izquierdaFotoPorcentaje(): number { return this.izquierdaFoto() / this.anchoSuperficieFoto() * 100; }
  superiorFotoPorcentaje(): number { return this.superiorFoto() / this.altoSuperficieFoto() * 100; }
  izquierdaMarcoPorcentaje(): number {
    return (this.anchoSuperficieFoto() - this.diametroRecorte) / 2 / this.anchoSuperficieFoto() * 100;
  }
  superiorMarcoPorcentaje(): number {
    return (this.altoSuperficieFoto() - this.diametroRecorte) / 2 / this.altoSuperficieFoto() * 100;
  }
  anchoMarcoPorcentaje(): number { return this.diametroRecorte / this.anchoSuperficieFoto() * 100; }
  altoMarcoPorcentaje(): number { return this.diametroRecorte / this.altoSuperficieFoto() * 100; }
  maxAnchoEditorFoto(): number { return Math.min(this.anchoSuperficieFoto(), 560); }

  /** Inicia el arrastre con ratón, lápiz o pantalla táctil. */
  iniciarArrastreFoto(evento: PointerEvent): void {
    if (!this.fotoSeleccionada || (evento.pointerType === 'mouse' && evento.button !== 0)) return;
    evento.preventDefault();
    const objetivo = evento.currentTarget as HTMLElement | null;
    objetivo?.setPointerCapture?.(evento.pointerId);
    const anchoVisible = objetivo?.getBoundingClientRect().width ?? this.anchoSuperficieFoto();
    this.inicioArrastreFoto = {
      x: evento.clientX,
      y: evento.clientY,
      escala: anchoVisible > 0 ? anchoVisible / this.anchoSuperficieFoto() : 1,
      posicionX: this.posicionFotoX,
      posicionY: this.posicionFotoY
    };
    this.arrastrandoFoto.set(true);
  }

  arrastrarFoto(evento: PointerEvent): void {
    const inicio = this.inicioArrastreFoto;
    if (!inicio || !this.fotoSeleccionada) return;
    const excesoX = Math.max(0, this.anchoFotoRender() - this.diametroRecorte);
    const excesoY = Math.max(0, this.altoFotoRender() - this.diametroRecorte);
    if (excesoX > 0) {
      this.posicionFotoX = this.limitarPorcentaje(inicio.posicionX
        - (((evento.clientX - inicio.x) / inicio.escala) / excesoX) * 100);
    }
    if (excesoY > 0) {
      this.posicionFotoY = this.limitarPorcentaje(inicio.posicionY
        - (((evento.clientY - inicio.y) / inicio.escala) / excesoY) * 100);
    }
  }

  terminarArrastreFoto(evento?: PointerEvent): void {
    if (evento) {
      const objetivo = evento.currentTarget as HTMLElement | null;
      if (objetivo?.hasPointerCapture?.(evento.pointerId)) objetivo.releasePointerCapture(evento.pointerId);
    }
    this.inicioArrastreFoto = null;
    this.arrastrandoFoto.set(false);
  }

  private limitarPorcentaje(valor: number): number {
    return Math.max(0, Math.min(100, valor));
  }

  private cargarDimensionesFoto(url: string): void {
    const imagen = new Image();
    imagen.onload = () => {
      // Una carga antigua no debe sobreescribir las dimensiones de la última foto.
      if (this.vistaPreviaFoto !== url) return;
      const ladoMenor = Math.min(imagen.naturalWidth, imagen.naturalHeight);
      if (!ladoMenor) return;
      this.anchoFotoRender.set(this.diametroRecorte * imagen.naturalWidth / ladoMenor);
      this.altoFotoRender.set(this.diametroRecorte * imagen.naturalHeight / ladoMenor);
    };
    imagen.onerror = () => {
      if (this.vistaPreviaFoto !== url || this.fotoSeleccionada === null) return;
      this.reiniciarFoto();
      this.errorFormulario.set('No se pudo abrir la imagen seleccionada. Prueba con otro archivo PNG, JPG, WEBP o GIF.');
    };
    imagen.src = url;
  }

  private liberarVistaPreviaLocal(): void {
    if (this.urlVistaPreviaLocal) URL.revokeObjectURL(this.urlVistaPreviaLocal);
    if (this.urlFotoAplicadaLocal) URL.revokeObjectURL(this.urlFotoAplicadaLocal);
    this.urlVistaPreviaLocal = null;
    this.urlFotoAplicadaLocal = null;
    this.fotoProcesadaParaSubir = null;
  }

  private reiniciarFoto(): void {
    this.liberarVistaPreviaLocal();
    this.fotoSeleccionada = null;
    this.vistaPreviaFoto = null;
    this.anchoFotoRender.set(this.diametroRecorte);
    this.altoFotoRender.set(this.diametroRecorte);
    this.fotoAplicada.set(false);
    this.procesandoEncuadre.set(false);
    this.centrarFoto();
    this.terminarArrastreFoto();
  }

  /** Genera la vista previa final con el mismo recorte que se enviará al backend. */
  aplicarEncuadreFoto(): void {
    if (!this.fotoSeleccionada || this.procesandoEncuadre()) return;
    const archivoOriginal = this.fotoSeleccionada;
    this.procesandoEncuadre.set(true);
    this.recortarFotoParaSubir(archivoOriginal)
      .then(archivoProcesado => {
        // Si se cerró el formulario o se eligió otra foto durante el proceso,
        // descartamos el resultado atrasado sin crear una URL temporal huérfana.
        if (this.fotoSeleccionada !== archivoOriginal) return;
        if (this.urlFotoAplicadaLocal) URL.revokeObjectURL(this.urlFotoAplicadaLocal);
        this.fotoProcesadaParaSubir = archivoProcesado;
        this.urlFotoAplicadaLocal = URL.createObjectURL(archivoProcesado);
        this.vistaPreviaFoto = this.urlFotoAplicadaLocal;
        this.fotoAplicada.set(true);
        this.errorFormulario.set(null);
      })
      .catch(() => this.errorFormulario.set('No se pudo aplicar el encuadre. Prueba con otra imagen.'))
      .finally(() => this.procesandoEncuadre.set(false));
  }

  /** Permite ajustar de nuevo la foto original sin perderla ni volver a seleccionarla. */
  editarEncuadreFoto(): void {
    if (!this.fotoSeleccionada || !this.urlVistaPreviaLocal) return;
    this.vistaPreviaFoto = this.urlVistaPreviaLocal;
    this.fotoAplicada.set(false);
    this.fotoProcesadaParaSubir = null;
  }

  private recortarFotoParaSubir(archivo: File): Promise<File> {
    return new Promise((resolve, reject) => {
      const imagen = new Image();
      const urlTemporal = URL.createObjectURL(archivo);
      imagen.onload = () => {
        try {
          const lado = Math.min(imagen.naturalWidth, imagen.naturalHeight);
          if (!lado || !imagen.naturalWidth || !imagen.naturalHeight) {
            URL.revokeObjectURL(urlTemporal);
            reject(new Error('La imagen no tiene dimensiones válidas.'));
            return;
          }
          const x = (imagen.naturalWidth - lado) * this.posicionFotoX / 100;
          const y = (imagen.naturalHeight - lado) * this.posicionFotoY / 100;
          const canvas = document.createElement('canvas');
          canvas.width = 512;
          canvas.height = 512;
          const contexto = canvas.getContext('2d');
          if (!contexto) {
            URL.revokeObjectURL(urlTemporal);
            reject(new Error('No se pudo procesar la foto.'));
            return;
          }
          contexto.drawImage(imagen, x, y, lado, lado, 0, 0, 512, 512);
          canvas.toBlob(blob => {
            URL.revokeObjectURL(urlTemporal);
            if (!blob) {
              reject(new Error('No se pudo procesar la foto.'));
              return;
            }
            resolve(new File([blob], 'foto-usuario.jpg', { type: 'image/jpeg' }));
          }, 'image/jpeg', 0.9);
        } catch {
          URL.revokeObjectURL(urlTemporal);
          reject(new Error('No se pudo procesar la foto.'));
        }
      };
      imagen.onerror = () => {
        URL.revokeObjectURL(urlTemporal);
        reject(new Error('No se pudo leer la imagen.'));
      };
      imagen.src = urlTemporal;
    });
  }

  // ---- reasignacion --------------------------------------------------------
  /** Usuario cuyo trabajo se esta reasignando (se guarda para recargar su ficha). */
  readonly reasignando = signal<{ uuid: string; nombre: string } | null>(null);
  readonly carga = signal<CargaDetallada | null>(null);
  readonly reasignandoEnCurso = signal(false);
  readonly errorReasignacion = signal<string | null>(null);
  readonly resultado = signal<ResultadoReasignacion | null>(null);
  destinos: ReasignarRequest = { expedientesA: null, tareasA: null, clientesA: null };
  readonly abogados = computed(() => (this.carga()?.candidatos ?? []).filter(c => c.esAbogado));

  // ---- derivados -----------------------------------------------------------
  readonly rolesAsignables = computed(() => this.roles().filter(r => !r.esPortalCliente));
  private readonly nombresRol = computed(() => new Map(this.roles().map(r => [r.codigo, r.nombre])));
  /** Uuid de quien esta en sesion: no se le ofrece desactivarse a si mismo. */
  readonly miUuid = computed(() => this.auth.usuario()?.uuid ?? null);

  ngOnInit(): void {
    // Una sola tuberia para la tabla: switchMap evita que una respuesta vieja
    // reemplace a una busqueda mas reciente.
    this.recargaTabla.pipe(
      startWith(void 0),
      switchMap(() => {
        this.cargando.set(true);
        this.errorPagina.set(null);
        return this.service.listarPagina(
          { texto: this.texto, rol: this.rol, estado: this.estado },
          this.pagina(),
          this.tamano()
        ).pipe(
          catchError((e: HttpErrorResponse) => {
            this.manejarErrorCarga(e);
            return of(null);
          }),
          finalize(() => this.cargando.set(false))
        );
      }),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(pagina => {
      if (!pagina) return;
      if (pagina.totalPaginas > 0 && pagina.pagina >= pagina.totalPaginas) {
        this.pagina.set(pagina.totalPaginas - 1);
        this.recargaTabla.next();
        return;
      }
      this.usuarios.set(pagina.contenido);
      this.totalElementos.set(pagina.totalElementos);
      this.totalPaginas.set(pagina.totalPaginas);
      this.pagina.set(pagina.pagina);
    });

    this.busqueda.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(() => {
      this.pagina.set(0);
      this.recargaTabla.next();
    });

    this.service.roles()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: roles => this.roles.set(roles),
        error: (e: HttpErrorResponse) => this.manejarErrorCarga(e)
      });

    this.cargarResumen();
  }

  // ---- carga ---------------------------------------------------------------

  recargar(): void {
    this.recargaTabla.next();
    this.cargarResumen();
  }

  buscar(): void {
    this.busqueda.next(JSON.stringify({
      texto: this.texto.trim(),
      rol: this.rol,
      estado: this.estado
    }));
  }

  cambiarTamano(tamano: number | string): void {
    const valor = Number(tamano);
    if (![10, 20, 40, 100].includes(valor)) return;
    this.tamano.set(valor);
    this.pagina.set(0);
    this.recargaTabla.next();
  }

  irPagina(destino: number): void {
    const total = this.totalPaginas();
    if (destino < 0 || (total > 0 && destino >= total) || destino === this.pagina()) return;
    this.pagina.set(destino);
    this.recargaTabla.next();
  }

  limpiarFiltros(): void {
    this.texto = '';
    this.rol = '';
    this.estado = '';
    this.pagina.set(0);
    this.buscar();
  }

  private cargarResumen(): void {
    this.service.resumen()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: resumen => this.totales.set(resumen),
        error: (e: HttpErrorResponse) => this.manejarErrorCarga(e)
      });
  }

  private manejarErrorCarga(e: HttpErrorResponse): void {
    if (e.status === 403) {
      this.sinPermiso.set(true);
    } else {
      this.errorPagina.set(this.mensaje(e, 'No se pudo cargar la lista de usuarios.'));
    }
  }

  // ---- presentacion --------------------------------------------------------

  nombreRol(codigo: string): string {
    return this.nombresRol().get(codigo) ?? codigo;
  }

  iniciales(nombre: string): string {
    const partes = nombre.trim().split(/\s+/);
    return ((partes[0]?.[0] ?? '') + (partes.length > 1 ? partes[partes.length - 1][0] : '')).toUpperCase();
  }

  estadoVisible(u: { activo: boolean; estado: string }): { texto: string; clase: string } {
    if (u.estado === 'BLOQUEADO') return { texto: 'Bloqueado', clase: 'warn' };
    if (!u.activo || u.estado === 'INACTIVO') return { texto: 'Inactivo', clase: 'off' };
    return { texto: 'Activo', clase: '' };
  }

  bloqueaBaja(d: UsuarioDetalle): boolean {
    const c = d.cargaTrabajo;
    return c.expedientes > 0 || c.plazosAbiertos > 0 || c.tareasAbiertas > 0
      || c.actuacionesAbiertas > 0 || c.clientes > 0 || c.equiposApoyo > 0;
  }

  // ---- ver -----------------------------------------------------------------

  ver(u: UsuarioLista): void {
    this.service.detalle(u.uuidPublico).subscribe({
      next: d => this.detalle.set(d),
      error: (e: HttpErrorResponse) => this.errorPagina.set(this.mensaje(e, 'No se pudo abrir la ficha.'))
    });
  }

  cerrarDetalle(): void {
    this.detalle.set(null);
  }

  // ---- crear / editar ------------------------------------------------------

  nuevo(): void {
    this.formulario = formularioVacio();
    this.reiniciarFoto();
    this.editando.set(null);
    this.errorFormulario.set(null);
    this.formularioAbierto.set(true);
  }

  editar(uuid: string): void {
    this.service.detalle(uuid).subscribe({
      next: d => {
        this.formulario = {
          nombres: d.nombres, apellidos: d.apellidos, email: d.email,
          cargo: d.cargo ?? '', telefono: d.telefono ?? '',
          tipoDocumento: d.tipoDocumento ?? '', numeroDocumento: d.numeroDocumento ?? '',
          numeroColegiatura: d.numeroColegiatura ?? '', roles: [...d.roles]
        };
        this.reiniciarFoto();
        this.vistaPreviaFoto = d.fotoUrl;
        this.fotoSeleccionada = null;
        this.detalle.set(null);
        this.editando.set(uuid);
        this.errorFormulario.set(null);
        this.formularioAbierto.set(true);
      },
      error: (e: HttpErrorResponse) => this.errorPagina.set(this.mensaje(e, 'No se pudo abrir el usuario.'))
    });
  }

  seleccionarFoto(evento: Event): void {
    const input = evento.target as HTMLInputElement;
    const archivo = input.files?.[0] ?? null;
    if (!archivo) return;
    const permitidos = ['image/png', 'image/jpeg', 'image/webp', 'image/gif'];
    if (!permitidos.includes(archivo.type) || archivo.size > 5 * 1024 * 1024) {
      this.errorFormulario.set('Solo se permiten imágenes PNG, JPG, WEBP o GIF de hasta 5 MB.');
      input.value = '';
      return;
    }

    this.liberarVistaPreviaLocal();
    this.fotoSeleccionada = archivo;
    this.fotoAplicada.set(false);
    this.fotoProcesadaParaSubir = null;
    const urlPrevia = URL.createObjectURL(archivo);
    this.urlVistaPreviaLocal = urlPrevia;
    this.vistaPreviaFoto = urlPrevia;
    this.anchoFotoRender.set(this.diametroRecorte);
    this.altoFotoRender.set(this.diametroRecorte);
    this.centrarFoto();
    this.terminarArrastreFoto();
    this.cargarDimensionesFoto(urlPrevia);
    // Permite volver a seleccionar el mismo archivo en caso de que el usuario lo cambie.
    input.value = '';
    this.errorFormulario.set(null);
  }

  private subirFotoSiCorresponde(
    uuid: string,
    alTerminar: () => void,
    alFallar?: () => void
  ): void {
    if (!this.fotoSeleccionada) { alTerminar(); return; }
    // La imagen ya fue recortada al pulsar Aplicar; se envía ese mismo archivo,
    // evitando que el recorte de la vista previa y el archivo enviado difieran.
    if (!this.fotoProcesadaParaSubir || !this.fotoAplicada()) {
      this.guardando.set(false);
      this.errorFormulario.set('Primero centra la foto y pulsa Aplicar.');
      alFallar?.();
      return;
    }
    this.service.subirFoto(uuid, this.fotoProcesadaParaSubir).subscribe({
      next: () => {
        if (uuid === this.miUuid()) this.auth.restaurarSesion().subscribe();
        alTerminar();
      },
      error: (e: HttpErrorResponse) => {
        this.alFallarGuardado(e);
        alFallar?.();
      }
    });
  }

  tieneRol(codigo: string): boolean {
    return this.formulario.roles.includes(codigo);
  }

  alternarRol(codigo: string): void {
    this.formulario.roles = this.tieneRol(codigo)
      ? this.formulario.roles.filter(r => r !== codigo)
      : [...this.formulario.roles, codigo];
  }

  cerrarFormulario(): void {
    this.formularioAbierto.set(false);
    this.liberarVistaPreviaLocal();
    this.fotoSeleccionada = null;
    this.vistaPreviaFoto = null;
    this.fotoAplicada.set(false);
    this.procesandoEncuadre.set(false);
    this.terminarArrastreFoto();
  }

  guardar(): void {
    if (!this.formulario.nombres.trim() || !this.formulario.apellidos.trim() || !this.formulario.email.trim()) {
      this.errorFormulario.set('Nombres, apellidos y correo son obligatorios.');
      return;
    }
    if (this.formulario.roles.length === 0) {
      this.errorFormulario.set('Asigna al menos un rol.');
      return;
    }
    if (this.fotoSeleccionada && !this.fotoAplicada()) {
      this.errorFormulario.set('Centra la foto y pulsa Aplicar antes de guardar el usuario.');
      return;
    }

    this.guardando.set(true);
    this.errorFormulario.set(null);
    const uuid = this.editando();

    if (uuid) {
      this.service.actualizar(uuid, this.formulario).subscribe({
        next: () => this.subirFotoSiCorresponde(uuid, () => this.alGuardar()),
        error: (e: HttpErrorResponse) => this.alFallarGuardado(e)
      });
    } else {
      this.service.crear(this.formulario).subscribe({
        next: credencial => {
          const uuidCreado = credencial.uuidPublico;
          // Si falla la foto, el siguiente intento actualiza esa cuenta ya creada
          // en vez de darla de alta otra vez. La clave temporal sigue disponible.
          this.editando.set(uuidCreado);
          this.subirFotoSiCorresponde(
            uuidCreado,
            () => { this.alGuardar(); this.mostrarCredencial(credencial); },
            () => this.mostrarCredencial(credencial)
          );
        },
        error: (e: HttpErrorResponse) => this.alFallarGuardado(e)
      });
    }
  }

  private alGuardar(): void {
    this.guardando.set(false);
    this.formularioAbierto.set(false);
    this.liberarVistaPreviaLocal();
    this.recargar();
  }

  private alFallarGuardado(e: HttpErrorResponse): void {
    this.guardando.set(false);
    this.errorFormulario.set(this.mensaje(e, 'No se pudo guardar el usuario.'));
  }

  // ---- acciones de ciclo de vida -------------------------------------------

  pedirDesactivar(u: { uuidPublico: string; nombreCompleto?: string; nombres?: string }): void {
    const nombre = u.nombreCompleto ?? u.nombres ?? 'este usuario';
    this.confirmacion.set({
      titulo: 'Desactivar usuario',
      mensaje: `${nombre} dejará de poder entrar y desaparecerá de los selectores de responsables. `
        + 'Si todavía tiene expedientes, plazos o tareas abiertas, el sistema no lo permitirá hasta reasignarlos.',
      boton: 'Desactivar',
      peligrosa: true,
      ejecutar: () => this.accion(this.service.desactivar(u.uuidPublico))
    });
  }

  activar(u: UsuarioLista): void {
    this.accion(this.service.activar(u.uuidPublico));
  }

  desbloquear(u: UsuarioLista): void {
    this.accion(this.service.desbloquear(u.uuidPublico));
  }

  pedirRestablecer(u: UsuarioLista): void {
    this.confirmacion.set({
      titulo: 'Restablecer contraseña',
      mensaje: `Se generará una contraseña temporal para ${u.nombreCompleto} y se cerrarán todas sus `
        + 'sesiones abiertas. Tendrá que cambiarla al entrar.',
      boton: 'Restablecer',
      peligrosa: false,
      ejecutar: () => {
        if (this.accionEnCurso()) return;
        this.accionEnCurso.set(true);
        this.service.restablecerPassword(u.uuidPublico)
          .pipe(finalize(() => this.accionEnCurso.set(false)))
          .subscribe({
            next: credencial => {
              this.confirmacion.set(null);
              this.mostrarCredencial(credencial);
              this.recargar();
            },
            error: (e: HttpErrorResponse) => this.alFallarAccion(e)
          });
      }
    });
  }

  confirmar(): void {
    this.confirmacion()?.ejecutar();
  }

  cancelarConfirmacion(): void {
    this.confirmacion.set(null);
  }

  private accion(peticion: ReturnType<UsuarioService['activar']>): void {
    if (this.accionEnCurso()) return;
    this.errorPagina.set(null);
    this.accionEnCurso.set(true);
    peticion.pipe(finalize(() => this.accionEnCurso.set(false))).subscribe({
      next: () => {
        this.confirmacion.set(null);
        this.detalle.set(null);
        this.recargar();
      },
      error: (e: HttpErrorResponse) => this.alFallarAccion(e)
    });
  }

  private alFallarAccion(e: HttpErrorResponse): void {
    this.confirmacion.set(null);
    this.errorPagina.set(this.mensaje(e, 'No se pudo completar la acción.'));
  }

  // ---- reasignacion de trabajo ---------------------------------------------

  abrirReasignacion(d: UsuarioDetalle): void {
    this.reasignando.set({ uuid: d.uuidPublico, nombre: `${d.nombres} ${d.apellidos}` });
    this.destinos = { expedientesA: null, tareasA: null, clientesA: null };
    this.carga.set(null);
    this.resultado.set(null);
    this.errorReasignacion.set(null);
    this.detalle.set(null);
    this.service.carga(d.uuidPublico).subscribe({
      next: c => this.carga.set(c),
      error: (e: HttpErrorResponse) => this.errorReasignacion.set(this.mensaje(e, 'No se pudo cargar su trabajo.'))
    });
  }

  /** Lo que falta elegir, para no dejar enviar una reasignacion incompleta. */
  faltaDestino(): boolean {
    const c = this.carga();
    if (!c) return true;
    return (c.expedientes.length > 0 && !this.destinos.expedientesA)
      || (c.tareas.length > 0 && !this.destinos.tareasA)
      || (c.clientes.length > 0 && !this.destinos.clientesA);
  }

  nada(): boolean {
    const c = this.carga();
    return !!c && !c.expedientes.length && !c.plazos.length && !c.tareas.length
      && !c.actuaciones.length && !c.clientes.length && !c.equipos.length;
  }

  confirmarReasignacion(): void {
    const r = this.reasignando();
    if (!r || this.faltaDestino()) return;
    this.reasignandoEnCurso.set(true);
    this.errorReasignacion.set(null);
    this.service.reasignar(r.uuid, this.destinos).subscribe({
      next: resultado => {
        this.reasignandoEnCurso.set(false);
        this.resultado.set(resultado);
        this.recargar();
      },
      error: (e: HttpErrorResponse) => {
        this.reasignandoEnCurso.set(false);
        this.errorReasignacion.set(this.mensaje(e, 'No se pudo reasignar. No se movió nada.'));
      }
    });
  }

  /** Al terminar vuelve a su ficha, donde ya puede darse de baja. */
  cerrarReasignacion(): void {
    const r = this.reasignando();
    const hubo = !!this.resultado();
    this.reasignando.set(null);
    this.carga.set(null);
    this.resultado.set(null);
    if (r && hubo) {
      this.service.detalle(r.uuid).subscribe({ next: d => this.detalle.set(d) });
    }
  }

  // ---- credencial temporal -------------------------------------------------

  private mostrarCredencial(credencial: CredencialTemporal): void {
    this.copiado.set(false);
    this.credencial.set(credencial);
  }

  copiarCredencial(): void {
    const c = this.credencial();
    if (!c) return;
    navigator.clipboard?.writeText(c.passwordTemporal).then(
      () => this.copiado.set(true),
      () => this.copiado.set(false));
  }

  /** Al cerrar se descarta: la contraseña temporal no se vuelve a mostrar. */
  cerrarCredencial(): void {
    this.credencial.set(null);
  }

  private mensaje(e: HttpErrorResponse, porDefecto: string): string {
    if (e.status === 0) return 'No se pudo conectar con el servidor.';
    return (e.error as ErrorRespuesta | null)?.mensaje ?? porDefecto;
  }
}
