import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subject, Subscription, debounceTime, distinctUntilChanged, forkJoin } from 'rxjs';
import { PanelComponent } from '../panel/panel.component';
import { AuthService } from '../../core/services/auth.service';
import { TareaService } from '../../core/services/tarea.service';
import { DocumentoService } from '../../core/services/documento.service';
import { TareasExcelService } from './exceltareas/tareas-excel.service';
import {
  EtapaTarea,
  EstadoTarea,
  Tarea,
  TareaCatalogos,
  TareaDetalle,
  TareaFiltro,
  TareaGuardar,
  TareaResumen
} from '../../core/models/tarea.models';

@Component({
  selector: 'app-tareas',
  standalone: true,
  imports: [CommonModule, FormsModule, PanelComponent],
  templateUrl: './tareas.component.html',
  styleUrl: './tareas.component.css'
})
export class TareasComponent implements OnInit, OnDestroy {

  private readonly service = inject(TareaService);
  private readonly documentos = inject(DocumentoService);
  readonly auth = inject(AuthService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly excel = inject(TareasExcelService);
  private readonly busqueda$ = new Subject<string>();
  private readonly subs = new Subscription();

  tareas: Tarea[] = [];

  pagina = 0;
  tamanoPagina = 24;
  totalElementos = 0;
  totalPaginas = 0;

  resumen: TareaResumen = {
    pendientes: 0,
    proceso: 0,
    revision: 0,
    vencidas: 0,
    completadas: 0,
    total: 0
  };

  catalogos: TareaCatalogos = {
    expedientes: [],
    usuarios: []
  };

  filtro: TareaFiltro = {};

  vista: 'kanban' | 'tabla' = 'kanban';

  cargando = false;
  mensaje = '';
  error = '';

  modal = false;
  detalle: TareaDetalle | null = null;

  modoEditar = false;

  comentario = '';

  tiempoReal: number | null = null;

  form: TareaGuardar = this.nuevoForm();

  subtareaNueva = '';

  archivoAdjunto: File | null = null;

  readonly etapas: EtapaTarea[] = [
    'ELABORAR',
    'REVISAR',
    'CORREGIR',
    'APROBAR',
    'FIRMAR',
    'PRESENTAR',
    'CARGAR_CARGO',
    'CERRAR'
  ];

  readonly columnas: {
    titulo: string;
    estados: EstadoTarea[];
    clase: string;
  }[] = [
    {
      titulo: 'Pendientes',
      estados: ['PENDIENTE', 'VENCIDA'],
      clase: 'pending'
    },
    {
      titulo: 'En proceso',
      estados: ['EN_PROCESO', 'OBSERVADA'],
      clase: 'progress'
    },
    {
      titulo: 'En revisión',
      estados: ['EN_REVISION', 'APROBADA'],
      clase: 'review'
    },
    {
      titulo: 'Presentadas / completadas',
      estados: ['PRESENTADA', 'COMPLETADA'],
      clase: 'completed'
    }
  ];

  ngOnInit(): void {
    this.subs.add(
      this.busqueda$
        .pipe(debounceTime(350), distinctUntilChanged())
        .subscribe(() => {
          this.pagina = 0;
          this.cargarTareas();
        })
    );
    this.cargarTodo();
  }

  ngOnDestroy(): void {
    this.subs.unsubscribe();
  }

  cargarTodo(): void {
    this.cargando = true;

    forkJoin({
      t: this.service.listar(this.filtro, this.pagina, this.tamanoPagina),
      r: this.service.resumen(),
      c: this.service.catalogos()
    }).subscribe({
      next: x => {
        this.aplicarPagina(x.t);
        this.resumen = x.r;
        this.catalogos = x.c;
        this.cargando = false;
        this.actualizarVista();
      },
      error: e => this.fallo(e)
    });
  }

  cargarTareas(): void {
    this.cargando = true;
    this.service.listar(this.filtro, this.pagina, this.tamanoPagina).subscribe({
      next: pagina => {
        this.aplicarPagina(pagina);
        this.cargando = false;
        this.actualizarVista();
      },
      error: e => this.fallo(e)
    });
  }

  aplicarFiltros(): void {
    this.pagina = 0;
    this.cargarTareas();
  }

  programarBusqueda(valor: string): void {
    this.filtro.texto = valor;
    this.busqueda$.next((valor ?? '').trim());
  }

  limpiarFiltros(): void {
    this.filtro = {};
    this.pagina = 0;
    this.cargarTareas();
  }

  cambiarPagina(nuevaPagina: number): void {
    if (nuevaPagina < 0 || nuevaPagina >= this.totalPaginas || nuevaPagina === this.pagina) return;
    this.pagina = nuevaPagina;
    this.cargarTareas();
  }

  cambiarTamanoPagina(): void {
    this.pagina = 0;
    this.cargarTareas();
  }

  trackTarea(_: number, tarea: Tarea): string {
    return tarea.uuid;
  }

  porColumna(estados: EstadoTarea[]): Tarea[] {
    return this.tareas.filter(
      t => estados.includes(t.estado)
    );
  }

  nueva(): void {
    this.detalle = null;
    this.form = this.nuevoForm();
    this.modoEditar = false;
    this.modal = true;
    this.error = '';
    this.actualizarVista();
  }

  abrir(t: Tarea): void {
    this.service.detalle(t.uuid).subscribe({
      next: d => {
        this.detalle = d;
        this.tiempoReal = d.tarea.tiempoRealMinutos;
        this.modal = true;
        this.modoEditar = false;
        this.actualizarVista();
      },
      error: e => this.fallo(e)
    });
  }

  editar(): void {
    if (!this.detalle) {
      return;
    }

    const d = this.detalle;

    this.form = {
      expedienteId: d.tarea.expedienteId,
      titulo: d.tarea.titulo,
      descripcion: d.tarea.descripcion ?? '',
      responsableId: d.tarea.responsableId,
      supervisorId: d.tarea.supervisorId,
      prioridad: d.tarea.prioridad,
      fechaLimite: this.localInput(d.tarea.fechaLimite),
      tiempoEstimadoMinutos: d.tarea.tiempoEstimadoMinutos,
      subtareas: d.subtareas.map(s => ({
        titulo: s.titulo,
        responsableId: s.responsableId
      }))
    };

    this.modoEditar = true;
    this.actualizarVista();
  }

  guardar(): void {
    if (
      !this.form.expedienteId ||
      !this.form.responsableId ||
      !this.form.titulo.trim() ||
      !this.form.fechaLimite
    ) {
      this.error =
        'Completa expediente, tarea, responsable y fecha límite.';

      this.actualizarVista();

      return;
    }

    const req = {
      ...this.form,
      fechaLimite: this.aIso(this.form.fechaLimite)
    };

    const obs = this.detalle
      ? this.service.actualizar(
          this.detalle.tarea.uuid,
          req
        )
      : this.service.crear(req);

    obs.subscribe({
      next: d => {
        this.detalle = d;
        this.modoEditar = false;
        this.exito('Tarea guardada correctamente.');
        this.refrescarTrasCambio();
      },
      error: e => this.fallo(e)
    });
  }

  agregarSubtarea(): void {
    if (!this.subtareaNueva.trim()) {
      return;
    }

    this.form.subtareas.push({
      titulo: this.subtareaNueva.trim(),
      responsableId: this.form.responsableId
    });

    this.subtareaNueva = '';

    this.actualizarVista();
  }

  quitarSubtarea(i: number): void {
    this.form.subtareas.splice(i, 1);
    this.actualizarVista();
  }

  toggleSub(sid: number): void {
    if (!this.detalle) {
      return;
    }

    this.service
      .toggleSubtarea(
        this.detalle.tarea.uuid,
        sid
      )
      .subscribe({
        next: d => {
          this.detalle = d;
          this.sincronizarDetalleEnLista(d);
          this.actualizarVista();
        },
        error: e => this.fallo(e)
      });
  }

  avanzar(etapa: EtapaTarea): void {
    if (!this.detalle) {
      return;
    }

    this.service
      .cambiarFlujo(
        this.detalle.tarea.uuid,
        etapa
      )
      .subscribe({
        next: d => {
          this.detalle = d;
          this.sincronizarDetalleEnLista(d);

          this.exito(
            `Flujo actualizado a ${this.etiquetaEtapa(etapa)}.`
          );

          this.refrescarResumen();
        },
        error: e => this.fallo(e)
      });
  }

  siguienteEtapa(): EtapaTarea | null {
    if (!this.detalle) {
      return null;
    }

    if (
      this.detalle.tarea.etapaFlujo === 'ELABORAR' &&
      ['PENDIENTE', 'VENCIDA'].includes(
        this.detalle.tarea.estado
      )
    ) {
      return 'ELABORAR';
    }

    const i = this.etapas.indexOf(
      this.detalle.tarea.etapaFlujo
    );

    return i >= 0 &&
      i < this.etapas.length - 1
      ? this.etapas[i + 1]
      : null;
  }

  textoAccion(e: EtapaTarea): string {
    return e === 'ELABORAR'
      ? 'Iniciar elaboración'
      : `Avanzar a ${this.etiquetaEtapa(e)}`;
  }

  puedeAprobar(e: EtapaTarea): boolean {
    return (
      !['APROBAR', 'FIRMAR'].includes(e) ||
      this.auth.tienePermiso('TAREA_APROBAR')
    );
  }

  observar(): void {
    if (
      !this.detalle ||
      this.detalle.tarea.etapaFlujo !== 'REVISAR'
    ) {
      return;
    }

    this.avanzar('CORREGIR');
  }

  comentar(): void {
    if (
      !this.detalle ||
      !this.comentario.trim()
    ) {
      return;
    }

    this.service
      .comentar(
        this.detalle.tarea.uuid,
        this.comentario
      )
      .subscribe({
        next: d => {
          this.detalle = d;
          this.sincronizarDetalleEnLista(d);
          this.comentario = '';
          this.actualizarVista();
        },
        error: e => this.fallo(e)
      });
  }

  guardarTiempo(): void {
    if (!this.detalle) {
      return;
    }

    this.service
      .registrarTiempo(
        this.detalle.tarea.uuid,
        this.tiempoReal
      )
      .subscribe({
        next: d => {
          this.detalle = d;
          this.exito('Tiempo registrado.');
        },
        error: e => this.fallo(e)
      });
  }

  seleccionarAdjunto(ev: Event): void {
    const input = ev.target as HTMLInputElement;

    this.archivoAdjunto =
      input.files?.[0] ?? null;

    this.actualizarVista();
  }

  subirAdjunto(): void {
    if (
      !this.detalle ||
      !this.archivoAdjunto
    ) {
      return;
    }

    const t = this.detalle.tarea;

    this.documentos.crear(
      {
        expedienteId: t.expedienteId,
        actuacionId: null,
        tareaId: t.id,
        nombre: this.archivoAdjunto.name,
        descripcion: `Adjunto de tarea: ${t.titulo}`,
        categoria: 'OTRO',
        estado: 'BORRADOR',
        esConfidencial: false,
        observaciones:
          'Cargado desde el panel de tareas'
      },
      this.archivoAdjunto
    ).subscribe({
      next: () => {
        this.archivoAdjunto = null;

        this.service
          .detalle(t.uuid)
          .subscribe({
            next: d => {
              this.detalle = d;
              this.sincronizarDetalleEnLista(d);
              this.actualizarVista();
            },
            error: e => this.fallo(e)
          });

        this.exito(
          'Adjunto cargado con la gestión documental.'
        );
      },
      error: e => this.fallo(e)
    });
  }

  cerrar(): void {
    this.modal = false;
    this.detalle = null;
    this.modoEditar = false;
    this.error = '';

    this.actualizarVista();
  }

  exportarExcel(): void {
    this.service.listarParaExportar(this.filtro).subscribe({
      next: tareas => {
        this.excel.exportar(tareas).catch(() => {
          this.error = 'No se pudo generar el archivo Excel.';
          this.actualizarVista();
        });
      },
      error: e => this.fallo(e)
    });
  }

  private aplicarPagina(p: import('../../core/models/tarea.models').TareaPagina): void {
    this.tareas = p.contenido;
    this.pagina = p.pagina;
    this.tamanoPagina = p.tamano;
    this.totalElementos = p.totalElementos;
    this.totalPaginas = p.totalPaginas;
  }

  private sincronizarDetalleEnLista(d: TareaDetalle): void {
    const i = this.tareas.findIndex(t => t.uuid === d.tarea.uuid);
    if (i < 0) return;
    const actual = this.tareas[i];
    this.tareas = [
      ...this.tareas.slice(0, i),
      {
        ...actual,
        titulo: d.tarea.titulo,
        expedienteId: d.tarea.expedienteId,
        expedienteCodigo: d.tarea.expedienteCodigo,
        numeroExpediente: d.tarea.numeroExpediente,
        cliente: d.tarea.cliente,
        responsableId: d.tarea.responsableId,
        responsable: d.tarea.responsable,
        supervisorId: d.tarea.supervisorId,
        supervisor: d.tarea.supervisor,
        prioridad: d.tarea.prioridad,
        estado: d.tarea.estado,
        etapaFlujo: d.tarea.etapaFlujo,
        fechaLimite: d.tarea.fechaLimite,
        porcentajeAvance: d.tarea.porcentajeAvance,
        subtareasTotal: d.tarea.subtareasTotal,
        subtareasCompletadas: d.tarea.subtareasCompletadas,
        adjuntos: d.tarea.adjuntos,
        comentarios: d.tarea.comentarios
      },
      ...this.tareas.slice(i + 1)
    ];
  }

  private refrescarResumen(): void {
    this.service.resumen().subscribe({
      next: r => {
        this.resumen = r;
        this.actualizarVista();
      },
      error: e => this.fallo(e)
    });
  }

  private refrescarTrasCambio(): void {
    forkJoin({
      t: this.service.listar(this.filtro, this.pagina, this.tamanoPagina),
      r: this.service.resumen()
    }).subscribe({
      next: x => {
        this.aplicarPagina(x.t);
        this.resumen = x.r;
        this.actualizarVista();
      },
      error: e => this.fallo(e)
    });
  }

  etiquetaEstado(e: string): string {
    return (
      {
        PENDIENTE: 'Pendiente',
        EN_PROCESO: 'En proceso',
        EN_REVISION: 'Revisión',
        OBSERVADA: 'Observada',
        APROBADA: 'Aprobada',
        PRESENTADA: 'Presentada',
        COMPLETADA: 'Completada',
        CANCELADA: 'Cancelada',
        VENCIDA: 'Vencida'
      } as Record<string, string>
    )[e] ?? e;
  }

  etiquetaEtapa(e: string): string {
    return e
      .toLowerCase()
      .replaceAll('_', ' ')
      .replace(
        /^./,
        c => c.toUpperCase()
      );
  }

  iniciales(n: string): string {
    return n
      .split(/\s+/)
      .slice(0, 2)
      .map(
        x => x[0] ?? ''
      )
      .join('')
      .toUpperCase();
  }

  fechaCorta(v: string): string {
    return new Intl.DateTimeFormat(
      'es-PE',
      {
        day: '2-digit',
        month: 'short'
      }
    ).format(new Date(v));
  }

  private nuevoForm(): TareaGuardar {
    return {
      expedienteId: null,
      titulo: '',
      descripcion: '',
      responsableId: null,
      supervisorId: null,
      prioridad: 'MEDIA',
      fechaLimite: '',
      tiempoEstimadoMinutos: null,
      subtareas: []
    };
  }

  private localInput(v: string): string {
    return v?.slice(0, 16) ?? '';
  }

  private aIso(v: string): string {
    return v.length === 16
      ? `${v}:00`
      : v;
  }

  private exito(m: string): void {
    this.mensaje = m;
    this.error = '';

    this.actualizarVista();

    setTimeout(() => {
      this.mensaje = '';
      this.actualizarVista();
    }, 2800);
  }

  private fallo(e: any): void {
    this.cargando = false;

    this.error =
      e?.error?.mensaje ||
      'No se pudo completar la operación.';

    this.actualizarVista();
  }

  private actualizarVista(): void {
    try {
      this.cdr.detectChanges();
    } catch {}
  }
}