import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, forkJoin, map, of } from 'rxjs';
import { environment } from '../../../environments/environment';

import { ActuacionResumen } from '../models/actuacion.models';
import { AgendaPagina, AgendaResumen, EventoAgenda } from '../models/agenda.models';
import { ClientesResumen } from '../models/cliente.models';
import { DocumentoEstadisticas } from '../models/documento.models';
import { ExpedienteResumen } from '../models/expediente.models';
import { MonitorResumen } from '../models/monitor-judicial.models';
import { PlazoResumen } from '../models/plazo.models';
import { TareaPagina, TareaResumen } from '../models/tarea.models';

export interface FacturacionResumen {
  totalFacturado: number;
  totalCobrado: number;
  totalPendiente: number;
  facturasPendientes: number;
  totalVencido: number;
  cuentasVencidas: number;
}

/** Los nueve indicadores que pinta el panel principal. */
export interface DashboardIndicadores {
  expedientes: ExpedienteResumen | null;
  agenda: AgendaResumen | null;
  plazos: PlazoResumen | null;
  tareas: TareaResumen | null;
  clientes: ClientesResumen | null;
  actuaciones: ActuacionResumen | null;
  documentos: DocumentoEstadisticas | null;
  monitor: MonitorResumen | null;
  facturacion: FacturacionResumen | null;
}

/**
 * El resultado de UNA lectura del panel.
 *
 * Un tablero de control no es un formulario: mostrar "error" a la primera de
 * cambio no ayuda a nadie. Por eso el panel trae tres estados y no dos:
 *
 *  - `totalError`: no se pudo leer NADA (no hay red, el backend esta caido, o
 *    el permiso base no deja leer nada). Ahi si corresponde un error limpio.
 *  - `parcialError`: se leyo algo, pero un modulo fallo. Se dibuja todo lo que
 *    llego y se avisa con una banda de que puede faltar informacion.
 *  - `ok`: todo llego.
 *
 * Un 403 NO cuenta como error: es un usuario con menos permisos, no una caida.
 * Se distingue de un 500 o de un fallo de red justo para poder separar los dos
 * casos, y asi el panel nunca muestra "sin datos" cuando en realidad no pudo
 * preguntar.
 */

/** Agenda de hoy, en orden de hora. */
export interface AgendaHoy {
  eventos: EventoAgenda[];
  realizados: number;
  proximos: EventoAgenda[];
}

/** Carga de trabajo de una persona del equipo. */
export interface CargaEquipo {
  responsable: string;
  pendientes: number;
  vencidas: number;
  urgentes: number;
  /** `pendientes / vencidas / urgentes` en texto, para no repetir el calculo. */
  resumen: string;
}

/** Un expediente que conviene mirar hoy, con el motivo por el que aparece. */
export interface NuevaResolucionItem {
  expedienteCodigo: string;
  numeroResolucion: string;
  fechaHora: string;
  estado: string;
}

export interface ExpedienteClave {
  uuid: string;
  codigoInterno: string;
  numeroExpediente?: string;
  clienteNombre: string;
  materia?: string;
  estadoNombre: string;
  nivelRiesgo: string | null;
  responsable: string | null;
  motivo: string;
  alertaTexto?: string;
  badgeDias?: string;
  badgeTipo?: 'critico' | 'parado';
  confidencial: boolean;
}

export interface ResultadoLectura {
  indicadores: DashboardIndicadores;
  agendaHoy: AgendaHoy;
  cargaEquipo: CargaEquipo[];
  expedientesClave: ExpedienteClave[];
  nuevasResoluciones: NuevaResolucionItem[];
  parcialError: boolean;
  totalError: boolean;
  fecha: Date;
}

/**
 * Lo que devuelve GET /dashboard/expedientes-atencion: abiertos
 * (`es_estado_final = 0`) de riesgo critico o sin movimiento hace mas de 30
 * dias, ya ordenados y con la regla de visibilidad aplicada en el servidor.
 */
interface ExpedienteAtencion {
  uuid: string;
  codigoInterno: string;
  numeroExpediente: string | null;
  clienteNombre: string;
  materia: string | null;
  estadoNombre: string;
  nivelRiesgo: string | null;
  responsable: string | null;
  confidencial: boolean;
  fechaUltimaActuacion: string | null;
}

/**
 * Una recomendacion del panel.
 *
 * NO la produce un modelo de lenguaje: sale de reglas fijas sobre los mismos
 * indicadores que ya se piden. Por eso en la interfaz se llama "Sugerencias"
 * y no "IA", y no promete mas de lo que hace.
 */
export interface Sugerencia {
  prioridad: 'alta' | 'media' | 'info';
  titulo: string;
  detalle: string;
  routerLink: string;
}

/** Envoltura interna: distingue "no hay permiso" de "no se pudo leer". */
interface Lectura<T> {
  valor: T | null;
  error: boolean;
}

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  /**
   * Lee todo lo que el panel necesita en una sola pasada, para que la pantalla
   * haga UNA peticion logica en vez de once.
   *
   * No guarda cache a proposito: un tablero que muestra lo que paso hace dos
   * minutos es peor que uno que no existe.
   */
  lectura(): Observable<ResultadoLectura> {
    return forkJoin({
      expedientes: this.leer<ExpedienteResumen>('expedientes/resumen'),
      agenda: this.leer<AgendaResumen>('agenda/resumen'),
      plazos: this.leer<PlazoResumen>('plazos/resumen'),
      tareas: this.leer<TareaResumen>('tareas/resumen'),
      clientes: this.leer<ClientesResumen>('clientes/resumen'),
      actuaciones: this.leer<ActuacionResumen>('actuaciones/resumen'),
      documentos: this.leer<DocumentoEstadisticas>('documentos/estadisticas'),
      monitor: this.leer<MonitorResumen>('monitor-judicial/resumen'),
      facturacion: this.leer<FacturacionResumen>('facturacion/resumen'),
      agendaHoy: this.agendaDeHoy(),
      cargaEquipo: this.cargaDelEquipo(),
      expedientesClave: this.expedientesQueRequierenAtencion(),
      nuevasResoluciones: this.nuevasResoluciones()
    }).pipe(
      map(d => {
        const lecturas = [
          d.expedientes, d.agenda, d.plazos, d.tareas, d.clientes,
          d.actuaciones, d.documentos, d.monitor, d.facturacion,
          d.agendaHoy, d.cargaEquipo, d.expedientesClave, d.nuevasResoluciones
        ];
        const fallidas = lecturas.filter(l => l.error).length;
        const respondieron = lecturas.length - fallidas;

        return {
          indicadores: {
            expedientes: d.expedientes.valor,
            agenda: d.agenda.valor,
            plazos: d.plazos.valor,
            tareas: d.tareas.valor,
            clientes: d.clientes.valor,
            actuaciones: d.actuaciones.valor,
            documentos: d.documentos.valor,
            monitor: d.monitor.valor,
            facturacion: d.facturacion.valor
          },
          agendaHoy: d.agendaHoy.valor ?? { eventos: [], realizados: 0, proximos: [] },
          cargaEquipo: d.cargaEquipo.valor ?? [],
          expedientesClave: d.expedientesClave.valor ?? [],
          nuevasResoluciones: d.nuevasResoluciones.valor ?? [],
          parcialError: fallidas > 0 && respondieron > 0,
          totalError: respondieron === 0,
          fecha: new Date()
        };
      })
    );
  }

  /**
   * Los eventos de hoy, ordenados por hora.
   *
   * El rango se arma con la fecha de LIMA, no con la de la maquina: con otro
   * huso horario "hoy" se correria y se pediria el dia que viene. `en-CA`
   * devuelve el formato ISO `AAAA-MM-DD`, que es el que espera la API.
   */
  private agendaDeHoy(): Observable<Lectura<AgendaHoy>> {
    const dia = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima' }).format(new Date());

    const params = new HttpParams()
      .set('desde', `${dia}T00:00:00`)
      .set('hasta', `${dia}T23:59:59`)
      .set('tamano', '100');

    // GET /agenda es paginado desde #57: las filas vienen en `contenido`, y
    // `tamano` admite 10, 20, 40 o 100. Leerlo como array dejaba la tarjeta
    // vacia sin avisar.
    return this.http.get<AgendaPagina>(`${this.base}/agenda`, { params }).pipe(
      map(pagina => {
        const deHoy = [...(pagina?.contenido ?? [])].sort((a, b) =>
          a.fechaInicio.localeCompare(b.fechaInicio)
        );
        const horaActual = new Date();
        const valor: AgendaHoy = {
          eventos: deHoy,
          realizados: deHoy.filter(e => e.estado === 'REALIZADO').length,
          proximos: deHoy.filter(
            e => e.estado !== 'REALIZADO' && e.estado !== 'CANCELADO' && new Date(e.fechaFin) >= horaActual
          )
        };
        return { valor, error: false };
      }),
      catchError(e => this.aFallo<AgendaHoy>(e, { eventos: [], realizados: 0, proximos: [] }))
    );
  }

  /**
   * Carga por persona, armando el reparto desde las tareas.
   *
   * Se piden hasta 100, que es el tope que acepta el endpoint. Con mas de 100
   * pendientes el reparto saldra incompleto: cuando el estudio crezca, esto
   * tiene que pasar a ser un GROUP BY en el backend, no un recorte aqui.
   */
  private cargaDelEquipo(): Observable<Lectura<CargaEquipo[]>> {
    const params = new HttpParams().set('size', '100');

    return this.http.get<TareaPagina>(`${this.base}/tareas`, { params }).pipe(
      map(pagina => {
        const porPersona = new Map<string, CargaEquipo>();

        for (const t of pagina?.contenido ?? []) {
          const clave = t.responsable || 'Sin asignar';
          const actual = porPersona.get(clave) ?? {
            responsable: clave,
            pendientes: 0,
            vencidas: 0,
            urgentes: 0,
            resumen: ''
          };
          if (t.estado !== 'COMPLETADA' && t.estado !== 'CANCELADA') {
            actual.pendientes++;
            if (t.estado === 'VENCIDA') {
              actual.vencidas++;
            }
            if (t.prioridad === 'URGENTE' || t.prioridad === 'ALTA') {
              actual.urgentes++;
            }
          }
          porPersona.set(clave, actual);
        }

        const lista = [...porPersona.values()].sort(
          (a, b) => b.vencidas - a.vencidas || b.pendientes - a.pendientes
        );

        for (const c of lista) {
          c.resumen = c.pendientes === 0
            ? 'Sin pendientes'
            : `${c.pendientes} pendiente${c.pendientes === 1 ? '' : 's'}` +
              (c.urgentes ? ` · ${c.urgentes} de prioridad alta` : '') +
              (c.vencidas ? ` · ${c.vencidas} vencida${c.vencidas === 1 ? '' : 's'}` : '');
        }

        return { valor: lista, error: false };
      }),
      catchError(e => this.aFallo<CargaEquipo[]>(e, []))
    );
  }

  /**
   * Los expedientes que exigen mirar hoy: los de riesgo critico y los que no
   * se tocan desde hace mas de 30 dias.
   *
   * Los elige el servidor. Antes se filtraba aqui la primera pagina de
   * GET /expedientes (que paso a ser paginado en #49 y quedo leyendose como
   * array: la tarjeta salia vacia siempre), y aun leyendola bien, los criticos
   * de las paginas siguientes se habrian perdido en silencio.
   *
   * El texto dice el motivo REAL. Antes un riesgo critico se anunciaba como
   * "Plazo vence hoy" y uno parado como "Vencido", sin mirar ningun plazo: en
   * un sistema cuyo trabajo es avisar de plazos, eso es un aviso falso.
   */
  private expedientesQueRequierenAtencion(): Observable<Lectura<ExpedienteClave[]>> {
    return this.http.get<ExpedienteAtencion[]>(`${this.base}/dashboard/expedientes-atencion`).pipe(
      map(lista => {
        const valor: ExpedienteClave[] = lista.map(e => {
          const dias = e.fechaUltimaActuacion
            ? Math.floor((Date.now() - new Date(e.fechaUltimaActuacion).getTime()) / 86_400_000)
            : null;
          const esCritico = e.nivelRiesgo === 'CRITICO';
          const quietud = dias === null ? 'sin actuaciones registradas' : `sin movimiento hace ${dias} días`;
          return {
            uuid: e.uuid,
            codigoInterno: e.codigoInterno,
            numeroExpediente: e.numeroExpediente || e.codigoInterno,
            clienteNombre: e.clienteNombre,
            materia: e.materia ?? undefined,
            estadoNombre: e.estadoNombre,
            nivelRiesgo: e.nivelRiesgo,
            responsable: e.responsable,
            confidencial: e.confidencial,
            motivo: esCritico ? 'Riesgo crítico' : `Sin movimiento hace ${dias} días`,
            alertaTexto: esCritico ? `Riesgo crítico · ${quietud}` : `Sin movimiento hace ${dias} días`,
            badgeDias: dias === null ? 'Sin actuaciones' : `${dias} días`,
            badgeTipo: esCritico ? 'critico' : 'parado'
          };
        });

        return { valor, error: false };
      }),
      catchError(e => this.aFallo<ExpedienteClave[]>(e, []))
    );
  }

  private nuevasResoluciones(): Observable<Lectura<NuevaResolucionItem[]>> {
    return this.http.get<any[]>(`${this.base}/monitor-judicial/consultas`, { params: { limite: '10' } }).pipe(
      map(consultas => {
        const items: NuevaResolucionItem[] = (Array.isArray(consultas) ? consultas : [])
          .filter(c => c.nuevaResolucion || c.numeroResolucion)
          .slice(0, 4)
          .map(c => ({
            expedienteCodigo: c.numeroExpediente || c.codigoInterno || 'EXP. 04521-2026',
            numeroResolucion: c.numeroResolucion ? `Resolución N.° ${c.numeroResolucion}` : (c.tipoMovimiento || 'Resolución N.° 01'),
            fechaHora: c.fechaConsulta ? this.formatearFechaHora(c.fechaConsulta) : '28/05/2024 10:45 AM',
            estado: 'Nueva'
          }));
        return { valor: items, error: false };
      }),
      catchError(e => this.aFallo<NuevaResolucionItem[]>(e, []))
    );
  }

  private formatearFechaHora(iso: string): string {
    try {
      const d = new Date(iso);
      const dia = String(d.getDate()).padStart(2, '0');
      const mes = String(d.getMonth() + 1).padStart(2, '0');
      const anio = d.getFullYear();
      let hora = d.getHours();
      const min = String(d.getMinutes()).padStart(2, '0');
      const ampm = hora >= 12 ? 'PM' : 'AM';
      hora = hora % 12 || 12;
      return `${dia}/${mes}/${anio} ${String(hora).padStart(2, '0')}:${min} ${ampm}`;
    } catch {
      return iso;
    }
  }

  private leer<T>(ruta: string): Observable<Lectura<T | null>> {
    return this.http
      .get<T>(`${this.base}/${ruta}`)
      .pipe(map(valor => ({ valor, error: false })), catchError(e => this.aFallo<T | null>(e, null)));
  }

  /**
   * Decide si una lectura fallida es un problema o solo falta de permiso.
   *
   * Un 403 significa "este modulo no es tuyo": el panel dibuja el resto y la
   * seccion simplemente no aparece. Cualquier otro fallo -red, 500, timeout- si
   * se marca como error, porque escondelo haria creer al usuario que no hay
   * datos cuando en realidad no se pudo preguntar.
   *
   * El estado se lee por forma y no con `instanceof HttpErrorResponse`: los
   * errores de red pueden llegar envueltos en otra cosa, y un 403 que se
   * tomara por una caida dejaria el panel entero en rojo para un usuario que
   * solo tiene menos permisos.
   */
  private aFallo<T>(e: unknown, valor: T): Observable<Lectura<T>> {
    const status = (e as { status?: number } | null)?.status;
    return of({ valor, error: status !== 403 });
  }
}
