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
   * en ninguno recibe un 200 generico, salvo la agenda, que recibe una lista
   * vacia.
   *
   * Todo en una pasada a proposito: `http.match()` saca la peticion de la
   * cola aunque despues no la resuelvas, asi que pedir `expectOne` mas tarde
   * sobre algo que el helper ya leyo no se puede. Resolver aqui y comprobar
   * sobre el resultado mantiene el test simple y fiel.
   */
  /**
   * Las rutas que devuelven una lista. El resto devuelve un objeto de resumen.
   */
  function listA(url: string): boolean {
    return url.includes('/agenda?') || url.endsWith('/expedientes');
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

      req.flush(listA(url) ? [] : {});
    }
  }

  it('pide los nueve resumenes mas la agenda, el equipo y los expedientes', () => {
    service.lectura().subscribe();
    const pedidos = http.match(() => true).map(r => r.request.urlWithParams);

    for (const ruta of RUTAS) {
      expect(pedidos.some(u => u.endsWith(ruta))).toBe(true);
    }
    // La agenda de hoy va con rango de fechas, no con el resumen.
    expect(pedidos.some(u => u.includes('/agenda?') && u.includes('desde='))).toBe(true);
    // El reparto del equipo se arma desde las tareas.
    expect(pedidos.some(u => u.includes('/tareas?') && u.includes('size=100'))).toBe(true);
    expect(pedidos.some(u => u.endsWith('/expedientes'))).toBe(true);
  });

  it('ordena la agenda de hoy por hora y cuenta lo ya realizado', async () => {
    const dia = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima' }).format(new Date());
    const p = firstValueFrom(service.lectura());
    responder({
      '/agenda?': [
        { fechaInicio: `${dia}T15:00:00`, fechaFin: `${dia}T15:30:00`, estado: 'CONFIRMADO' },
        { fechaInicio: `${dia}T08:00:00`, fechaFin: `${dia}T09:00:00`, estado: 'REALIZADO' }
      ]
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

  it('deja fuera los expedientes ya cerrados aunque esten parados', async () => {
    const viejo = new Date(Date.now() - 200 * 86_400_000).toISOString();
    const p = firstValueFrom(service.lectura());
    responder({
      '/expedientes': [
        { codigoInterno: 'EXP-1', nivelRiesgo: 'CRITICO', estadoCodigo: 'ACTIVO' },
        { codigoInterno: 'EXP-2', nivelRiesgo: 'BAJO', estadoCodigo: 'ARCHIVADO', fechaUltimaActuacion: viejo },
        { codigoInterno: 'EXP-3', nivelRiesgo: 'BAJO', estadoCodigo: 'CONCLUIDO', fechaUltimaActuacion: viejo }
      ]
    });

    const r = await p;
    // El archivado lleva 200 dias sin movimiento, pero no se audita: por eso
    // tiene que quedar fuera.
    expect(r.expedientesClave.map(e => e.codigoInterno)).toEqual(['EXP-1']);
  });

  it('marca fallo parcial con un 500 y conserva lo que si llego', async () => {    const p = firstValueFrom(service.lectura());
    responder({ 'expedientes/resumen': { total: 10 } }, { 'plazos/resumen': 500 });

    const r = await p;
    expect(r.totalError).toBe(false);
    expect(r.parcialError).toBe(true);
    expect(r.indicadores.plazos).toBeNull();
    // El resto de modulos llego bien y se conserva.
    expect(r.indicadores.expedientes).toEqual({ total: 10 });
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
    responder({}, todo);

    const r = await p;
    expect(r.totalError).toBe(true);
    expect(r.parcialError).toBe(false);
    expect(r.indicadores.plazos).toBeNull();
  });
});
