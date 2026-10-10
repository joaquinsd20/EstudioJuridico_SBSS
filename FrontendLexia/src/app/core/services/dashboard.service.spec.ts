import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { DashboardService } from './dashboard.service';

/**
 * El panel depende de este servicio para todo, asi que se prueban las dos
 * cosas que salen mal y son invisibles en una pantalla: que un modulo sin
 * permiso NO se confunda con una caida, y que una caida real no se esconda
 * como si el estudio no tuviera datos.
 */
describe('DashboardService', () => {
  let service: DashboardService;
  let http: HttpTestingController;

  const RUTAS = [
    'expedientes/resumen',
    'agenda/resumen',
    'plazos/resumen',
    'tareas/resumen',
    'clientes/resumen',
    'actuaciones/resumen',
    'documentos/estadisticas',
    'monitor-judicial/resumen',
    'facturacion/resumen'
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    });
    service = TestBed.inject(DashboardService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  /**
   * Contesta todas las peticiones pendientes en una sola pasada.
   *
   * `ok` y `fallan` se indexan por un fragmento de la URL. Lo que no aparezca
   * en ninguno recibe un 200 con la FORMA REAL de su endpoint (ver porDefecto).
   *
   * Todo en una pasada a proposito: `http.match()` saca la peticion de la
   * cola aunque despues no la resuelvas, asi que pedir `expectOne` mas tarde
   * sobre algo que el helper ya leyo no se puede. Resolver aqui y comprobar
   * sobre el resultado mantiene el test simple y fiel.
   */
  /**
   * La respuesta vacia de cada endpoint, con su forma real. Antes la agenda y
   * los expedientes se simulaban como arrays cuando el backend ya devolvia una
   * pagina (#49, #57): las pruebas pasaban mientras el panel salia vacio. Si un
   * contrato cambia, este es el sitio que hay que actualizar.
   */
  function porDefecto(url: string): unknown {
    if (url.includes('/agenda?')) {
      return { contenido: [], pagina: 0, tamano: 100, totalElementos: 0, totalPaginas: 0 };
    }
    if (url.includes('/dashboard/expedientes-atencion') || url.includes('/monitor-judicial/consultas')) {
      return [];
    }
    return {};
  }

  function responder(
    ok: Record<string, unknown> = {},
    fallan: Record<string, number> = {}
  ): void {
    for (const req of http.match(() => true)) {
      // `url` no trae el query string: los params viajan aparte, asi que para
      // reconocer "/agenda?desde=..." hay que usar `urlWithParams`.
      const url = req.request.urlWithParams;

      const conFallo = Object.keys(fallan).find(k => url.includes(k));
      if (conFallo) {
        // `req.error` y no `flush` con status: `flush` entrega un cuerpo
        // raro y no arma bien el HttpErrorResponse con ese codigo.
        req.error(new ProgressEvent('error'), {
          status: fallan[conFallo],
          statusText: 'Error simulado'
        });
        continue;
      }

      const conDato = Object.keys(ok).find(k => url.includes(k));
      if (conDato) {
        req.flush(ok[conDato] as Record<string, unknown>);
        continue;
      }

      req.flush(porDefecto(url) as Record<string, unknown>);
    }
  }

  it('pide los nueve resumenes mas la agenda, el equipo y los expedientes que piden atencion', () => {
    service.lectura().subscribe();
    const pedidos = http.match(() => true).map(r => r.request.urlWithParams);

    for (const ruta of RUTAS) {
      expect(pedidos.some(u => u.endsWith(ruta))).toBe(true);
    }
    // La agenda de hoy va con rango de fechas, no con el resumen.
    // GET /agenda es paginado: `tamano` (10/20/40/100), no `limite`.
    expect(pedidos.some(u => u.includes('/agenda?') && u.includes('desde=') && u.includes('tamano=100'))).toBe(true);
    // El reparto del equipo se arma desde las tareas.
    expect(pedidos.some(u => u.includes('/tareas?') && u.includes('size=100'))).toBe(true);
    // Los elige el servidor: filtrar una pagina de /expedientes perdia los demas.
    expect(pedidos.some(u => u.endsWith('/dashboard/expedientes-atencion'))).toBe(true);
  });

  it('ordena la agenda de hoy por hora y cuenta lo ya realizado', async () => {
    const dia = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima' }).format(new Date());
    const p = firstValueFrom(service.lectura());
    responder({
      '/agenda?': {
        contenido: [
          { fechaInicio: `${dia}T15:00:00`, fechaFin: `${dia}T15:30:00`, estado: 'CONFIRMADO' },
          { fechaInicio: `${dia}T08:00:00`, fechaFin: `${dia}T09:00:00`, estado: 'REALIZADO' }
        ],
        pagina: 0, tamano: 100, totalElementos: 2, totalPaginas: 1
      }
    });

    const r = await p;
    expect(r.agendaHoy.eventos.length).toBe(2);
    expect(r.agendaHoy.eventos[0].fechaInicio).toBe(`${dia}T08:00:00`);
    expect(r.agendaHoy.realizados).toBe(1);
  });

  it('reparte la carga del equipo y cuenta las vencidas aparte', async () => {
    const p = firstValueFrom(service.lectura());
    responder({
      '/tareas?': {
        contenido: [
          { responsable: 'Ana', estado: 'VENCIDA', prioridad: 'URGENTE' },
          { responsable: 'Ana', estado: 'EN_PROCESO', prioridad: 'BAJA' },
          { responsable: 'Luis', estado: 'COMPLETADA', prioridad: 'MEDIA' }
        ]
      }
    });

    const r = await p;
    expect(r.cargaEquipo.length).toBe(2);
    const ana = r.cargaEquipo.find(c => c.responsable === 'Ana')!;
    expect(ana.pendientes).toBe(2);
    expect(ana.vencidas).toBe(1);
    expect(ana.urgentes).toBe(1);
    expect(ana.resumen).toContain('2 pendientes');
    // Lo completado no cuenta como carga de trabajo.
    const luis = r.cargaEquipo.find(c => c.responsable === 'Luis')!;
    expect(luis.pendientes).toBe(0);
    expect(luis.resumen).toBe('Sin pendientes');
  });

  it('dice el motivo real de cada expediente y nunca anuncia un plazo que no existe', async () => {
    const hace = (dias: number) => new Date(Date.now() - dias * 86_400_000).toISOString();
    const p = firstValueFrom(service.lectura());
    responder({
      '/dashboard/expedientes-atencion': [
        { codigoInterno: 'EXP-1', nivelRiesgo: 'CRITICO', fechaUltimaActuacion: hace(5), clienteNombre: 'A' },
        { codigoInterno: 'EXP-2', nivelRiesgo: 'BAJO', fechaUltimaActuacion: hace(40), clienteNombre: 'B' }
      ]
    });

    const r = await p;
    // El orden lo decide el servidor (criticos primero) y se respeta.
    expect(r.expedientesClave.map(e => e.codigoInterno)).toEqual(['EXP-1', 'EXP-2']);

    const [critico, parado] = r.expedientesClave;
    expect(critico.badgeTipo).toBe('critico');
    expect(critico.alertaTexto).toContain('Riesgo crítico');
    expect(parado.badgeTipo).toBe('parado');
    expect(parado.alertaTexto).toBe('Sin movimiento hace 40 días');

    // Antes un critico se anunciaba "Plazo vence hoy" y uno parado "Vencido",
    // sin mirar ningun plazo. Ninguno de estos datos habla de plazos.
    for (const e of r.expedientesClave) {
      expect(`${e.motivo} ${e.alertaTexto} ${e.badgeDias}`).not.toMatch(/plazo|vence|vencido/i);
    }
  });

  it('marca fallo parcial con un 500 y conserva lo que si llego', async () => {
    const p = firstValueFrom(service.lectura());
    responder({ 'expedientes/resumen': { total: 10 } }, { 'plazos/resumen': 500 });

    const r = await p;
    expect(r.totalError).toBe(false);
    expect(r.parcialError).toBe(true);
    expect(r.indicadores.plazos).toBeNull();
    // El resto de modulos llego bien y se conserva.
    expect(r.indicadores.expedientes).toEqual({ total: 10 });
  });

  it('nuevas resoluciones: solo las marcadas como nuevas, de los ultimos 30 dias y sin relleno', async () => {
    const p = firstValueFrom(service.lectura());
    const pedido = http.match(r => r.url.endsWith('/monitor-judicial/consultas'))[0];
    // La ventana de 30 dias evita que una nueva quede fuera por diez consultas
    // mas recientes sin novedad.
    expect(pedido.request.params.get('desde')).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(pedido.request.params.get('limite')).toBe('100');
    pedido.flush([
      { uuid: 'c1', codigoInterno: 'EXP-1', numeroExpediente: '01452-2026-0-1601-JR-FC-03', fechaConsulta: '2026-10-04T12:07:00',
        nuevaResolucion: true, numeroResolucion: 'Resolucion N.o 0011', tipoResolucion: 'AUTO' },
      // La misma resolucion vista en una consulta anterior: no es otra nueva.
      { uuid: 'c0', codigoInterno: 'EXP-1', numeroExpediente: '01452-2026-0-1601-JR-FC-03', fechaConsulta: '2026-10-01T10:00:00',
        nuevaResolucion: true, numeroResolucion: 'Resolucion N.o 0011', tipoResolucion: 'AUTO' },
      // Trae numero de resolucion pero NO es nueva: antes salia con el sello "Nueva".
      { uuid: 'c2', codigoInterno: 'EXP-2', numeroExpediente: null, fechaConsulta: '2026-10-03T09:00:00',
        nuevaResolucion: false, numeroResolucion: 'Resolucion N.o 0004', tipoResolucion: 'DECRETO' },
      // Nueva sin numero ni expediente judicial: se dice, no se inventa.
      { uuid: 'c3', codigoInterno: 'EXP-3', numeroExpediente: null, fechaConsulta: '2026-10-02T08:30:00',
        nuevaResolucion: true, numeroResolucion: null, tipoResolucion: null }
    ]);
    responder();

    const r = await p;
    expect(r.nuevasResoluciones.map(n => n.expedienteCodigo)).toEqual(['01452-2026-0-1601-JR-FC-03', 'EXP-3']);
    // La etiqueta del juzgado se muestra tal cual: antes salia "Resolución N.° Resolucion N.o 0011".
    expect(r.nuevasResoluciones[0].numeroResolucion).toBe('Resolucion N.o 0011');
    expect(r.nuevasResoluciones[1].numeroResolucion).toBe('Resolución sin número registrado');
    for (const n of r.nuevasResoluciones) {
      expect(`${n.expedienteCodigo} ${n.numeroResolucion} ${n.fechaHora}`).not.toMatch(/04521|N\.° 01|2024/);
    }
  });

  it('NO marca error con un 403: es falta de permiso, no una caida', async () => {
    const p = firstValueFrom(service.lectura());
    responder({ 'expedientes/resumen': { total: 10 } }, { 'facturacion/resumen': 403 });

    const r = await p;
    expect(r.parcialError).toBe(false);
    expect(r.totalError).toBe(false);
    // Sin permiso, ese indicador simplemente no aparece.
    expect(r.indicadores.facturacion).toBeNull();
    expect(r.indicadores.expedientes).toEqual({ total: 10 });
  });

  it('marca fallo total cuando no responde ninguno', async () => {
    const p = firstValueFrom(service.lectura());
    const todo: Record<string, number> = {};
    for (const ruta of RUTAS) {
      todo[ruta] = 0;
    }
    todo['/agenda?'] = 0;
    todo['/tareas?'] = 0;
    todo['/expedientes'] = 0;
    // Las nuevas resoluciones se leen de aqui; sin simular su caida, esa
    // peticion respondia 200 y el panel nunca llegaba a "fallo total".
    todo['monitor-judicial/consultas'] = 0;
    responder({}, todo);

    const r = await p;
    expect(r.totalError).toBe(true);
    expect(r.parcialError).toBe(false);
    expect(r.indicadores.plazos).toBeNull();
  });
});
