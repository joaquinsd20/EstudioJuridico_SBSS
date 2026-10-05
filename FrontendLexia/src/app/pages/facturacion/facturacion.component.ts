import { HonorarioLista, ResumenBackend, ResumenFacturacion, Opcion, CatalogosFacturacion, RegistroMovimientoDTO, MovimientoResponse, ActualizarHonorarioDTO, PagoHonorarioDetalle, DetalleHonorario } from '../../core/models/facturacion.models';
import { FacturacionService } from '../../core/services/facturacion.service';
import { AuthService } from '../../core/services/auth.service';
import {
  ChangeDetectorRef,
  Component,
  OnDestroy,
  OnInit
} from '@angular/core';

import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { HttpParams } from '@angular/common/http';

import {
  NavigationEnd,
  Router
} from '@angular/router';

import { Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';

import { PanelComponent } from '../panel/panel.component';


@Component({

  selector: 'app-facturacion',
  standalone: true,

  imports: [
    CommonModule,
    FormsModule,
    PanelComponent
  ],

  templateUrl: './facturacion.component.html',
  styleUrl: './facturacion.component.css'

})
export class FacturacionComponent
  implements OnInit, OnDestroy {

  private routerSubscription?: Subscription;

  // Control de acceso: mismo patrón utilizado por el módulo Plazos.
  readonly puedeLeer: boolean;

  // Paginación real: el backend ya soporta LIMIT/OFFSET.
  readonly tamanosPagina = [10, 20, 40, 100];
  pagina = 0;
  tamanoPagina = 10;
  haySiguiente = false;

  honorarios: HonorarioLista[] = [];


  resumen: ResumenFacturacion = {
    totalPactado: 0,
    totalCobrado: 0,
    saldosPendientes: 0,
    totalVencido: 0,
    cuentasVencidas: 0

  };


  catalogos: CatalogosFacturacion = {
    clientes: [],
    expedientes: [],
    modalidadesCobro: [],
    estados: []

  };


  filtroTexto: string = '';
  filtroModalidad: number | null = null;
  filtroEstado: string = '';


  loading: boolean = false;
  cargandoClientes: boolean = false;
  mostrarModal: boolean = false;
  guardando: boolean = false;

  mostrarModalDetalle: boolean = false;
  cargandoDetalle: boolean = false;
  descargandoComprobante: boolean = false;
  detalleHonorario: DetalleHonorario | null = null;

  honorarioSeleccionadoUuid: string | null = null;
  modoFormulario: 'NUEVO' | 'PAGO' | 'EDICION' = 'NUEVO';
  honorarioEditando: HonorarioLista | null = null;

  nuevoMovimiento: RegistroMovimientoDTO =
    this.obtenerEstadoInicialFormulario();

  constructor(
    private service: FacturacionService,
    private router: Router,
    private cdr: ChangeDetectorRef,
    private auth: AuthService
  ) {
    this.puedeLeer = this.auth.tienePermiso('FACTURACION_LEER');
  }


  ngOnInit(): void {
    // Si no posee FACTURACION_LEER, se muestra Acceso restringido y
    // no se realizan peticiones que terminarían en 403 Forbidden.
    if (!this.puedeLeer) {
      return;
    }

    this.refrescarFacturacion();
    this.routerSubscription =
      this.router.events
        .pipe(
          filter(
            evento =>
              evento instanceof NavigationEnd
          )
        )

        .subscribe((evento) => {
          const navigation =
            evento as NavigationEnd;
          if (
            navigation.urlAfterRedirects
              .toLowerCase()
              .includes('facturacion')
          ) {

            console.log(
              'Regreso detectado a Facturación. Actualizando datos...'
            );

            this.refrescarFacturacion();
          }
        });
  }

  /* =======================================================
     DESTRUIR COMPONENTE
     ======================================================= */
  ngOnDestroy(): void {
    if (this.routerSubscription) {
      this.routerSubscription.unsubscribe();
    }

  }

  /* =======================================================
     ACTUALIZAR TODO
     ======================================================= */

  private refrescarFacturacion(): void {
    console.log(
      'Actualizando módulo de facturación...'
    );

    this.cargarResumen();
    this.cargarCatalogos();
    this.aplicarFiltros(false);
  }

  /* =======================================================
     RESUMEN FINANCIERO
     ======================================================= */

  cargarResumen(): void {
    const params =
      new HttpParams()
        .set(
          '_t',
          Date.now().toString()
        );

    this.service.resumen(params)
      .subscribe({

        next: (data) => {

          console.log(
            'Resumen financiero recibido:',
            data
          );


          this.resumen = {

            totalPactado:
              this.convertirNumero(
                data?.totalFacturado
              ),

            totalCobrado:
              this.convertirNumero(
                data?.totalCobrado
              ),

            saldosPendientes:
              this.convertirNumero(
                data?.totalPendiente
              ),

            totalVencido:
              this.convertirNumero(
                data?.totalVencido
              ),

            cuentasVencidas:
              this.convertirNumero(
                data?.cuentasVencidas
              )

          };

          this.cdr.detectChanges();
        },


        error: (err) => {

          console.error(
            'Error al obtener el resumen financiero:',
            err
          );


          this.resumen = {
            totalPactado: 0,
            totalCobrado: 0,
            saldosPendientes: 0,
            totalVencido: 0,
            cuentasVencidas: 0
          };

          this.cdr.detectChanges();
        }
      });
  }


  /* =======================================================
     CATÁLOGOS
     ======================================================= */

  cargarCatalogos(): void {

    this.cargandoClientes = true;


    const params =
      new HttpParams()
        .set(
          '_t',
          Date.now().toString()
        );


    this.service.catalogos(params)
      .subscribe({

        next: (data) => {

          console.log(
            'Respuesta de catálogos:',
            data
          );


          this.catalogos = {

            clientes:
              this.normalizarOpciones(
                data?.clientes ?? []
              ),

            expedientes:
              this.normalizarOpciones(
                data?.expedientes ?? []
              ),

            modalidadesCobro:
              this.normalizarOpciones(
                data?.modalidadesCobro ?? []
              ),

            estados:
              this.normalizarOpciones(
                data?.estados ?? []
              )

          };


          console.log(
            'CLIENTES CARGADOS:',
            this.catalogos.clientes
          );


          console.log(
            'TOTAL CLIENTES:',
            this.catalogos.clientes.length
          );


          console.log(
            'EXPEDIENTES CARGADOS:',
            this.catalogos.expedientes
          );


          this.cargandoClientes = false;


          this.cdr.detectChanges();

        },


        error: (err) => {

          console.error(
            'Error al cargar catálogos:',
            err
          );


          this.catalogos = {
            clientes: [],
            expedientes: [],
            modalidadesCobro: [],
            estados: []
          };

          this.cargandoClientes = false;
          this.cdr.detectChanges();
        }
      });
  }


  /* =======================================================
     NORMALIZAR CATÁLOGOS
     ======================================================= */

  private normalizarOpciones(
    lista: Opcion[] | null | undefined
  ): Opcion[] {

    if (!Array.isArray(lista)) {
      return [];
    }
    return lista
      .filter(item =>
        item !== null &&
        item !== undefined &&
        item.id !== null &&
        item.id !== undefined
      )

      .map(item => ({
        id:
          Number(item.id),
        nombre:
          item.nombre ||
          'Sin nombre',
        detalle:
          item.detalle ||
          '',
        clienteId:
          item.clienteId !== null &&
          item.clienteId !== undefined
            ? Number(item.clienteId)
            : null
      }));
  }


  /* =======================================================
     EXPEDIENTES DISPONIBLES
     ======================================================= */

  obtenerExpedientesDisponibles(): Opcion[] {

    const clienteId =
      this.nuevoMovimiento.clienteId !== null &&
      this.nuevoMovimiento.clienteId !== undefined
        ? Number(this.nuevoMovimiento.clienteId)
        : null;

    if (
      clienteId === null ||
      Number.isNaN(clienteId) ||
      clienteId <= 0
    ) {

      return [];

    }

    return this.catalogos.expedientes.filter(
      expediente =>
        Number(expediente.clienteId) === clienteId
    );

  }

  onClienteChange(): void {

    const expedienteSeleccionado =
      this.nuevoMovimiento.expedienteId;

    if (
      expedienteSeleccionado === null ||
      expedienteSeleccionado === undefined
    ) {
      return;
    }

    const sigueDisponible =
      this.obtenerExpedientesDisponibles()
        .some(
          expediente =>
            Number(expediente.id) ===
            Number(expedienteSeleccionado)
        );

    if (!sigueDisponible) {
      this.nuevoMovimiento.expedienteId = null;
    }
  }

  /* =======================================================
     CONSULTAR HONORARIOS
     ======================================================= */

  aplicarFiltros(reiniciarPagina: boolean = true): void {

    if (!this.puedeLeer) {
      return;
    }

    if (reiniciarPagina) {
      this.pagina = 0;
    }

    this.loading = true;

    let params =
      new HttpParams();

    params = params.set(
      '_t',
      Date.now().toString()
    );


    if (
      this.filtroTexto &&
      this.filtroTexto.trim()
    ) {

      params = params.set(

        'cliente',

        this.filtroTexto.trim()

      );

    }


    if (

      this.filtroModalidad !== null &&

      this.filtroModalidad !== undefined

    ) {

      params = params.set(

        'modalidadId',

        this.filtroModalidad.toString()

      );

    }


    if (this.filtroEstado) {

      params = params.set(

        'estado',

        this.filtroEstado

      );

    }


    // Se solicita un registro adicional para saber si existe una página
    // siguiente sin descargar toda la tabla desde el servidor.
    params = params
      .set('limite', (this.tamanoPagina + 1).toString())
      .set('desplazamiento', (this.pagina * this.tamanoPagina).toString());

    this.service.listar(params)
      .subscribe({

        next: (data) => {

          console.log(
            'Honorarios recibidos:',
            data
          );


          const recibidos = Array.isArray(data) ? data : [];

          this.haySiguiente = recibidos.length > this.tamanoPagina;

          this.honorarios = recibidos
            .slice(0, this.tamanoPagina)
            .map(item => this.normalizarHonorario(item));


          console.log(
            'Honorarios normalizados:',
            this.honorarios
          );


          this.loading = false;


          this.cdr.detectChanges();

        },


        error: (err) => {

          console.error(
            'Error al consultar honorarios:',
            err
          );


          console.error(
            'Respuesta backend:',
            err?.error
          );


          this.honorarios = [];
          this.haySiguiente = false;


          this.loading = false;


          this.cdr.detectChanges();

        }

      });

  }


  /* =======================================================
     NORMALIZAR HONORARIO
     ======================================================= */

  private normalizarHonorario(
    item: any
  ): HonorarioLista {

    return {

      uuid:
        item?.uuid ||
        '',


      clienteId:
        this.convertirNumero(
          item?.clienteId
        ),


      expedienteId:
        this.convertirNumero(
          item?.expedienteId
        ),


      clienteNombre:
        item?.clienteNombre ||
        'Cliente sin nombre',


      clienteDocumento:
        item?.clienteDocumento ||
        '',


      expedienteNumero:
        item?.expedienteNumero ||
        '',


      concepto:
        item?.concepto ||
        'Honorarios profesionales',


      modalidadCobro:
        item?.modalidadCobro ||
        '',


      montoTotal:
        this.convertirNumero(
          item?.montoTotal
        ),


      montoPagado:
        this.convertirNumero(
          item?.montoPagado
        ),


      montoPendiente:
        this.convertirNumero(
          item?.montoPendiente
        ),


      moneda:
        item?.moneda ||
        'PEN',


      estadoCodigo:
        item?.estadoCodigo ||
        'PENDIENTE',


      estadoNombre:
        item?.estadoNombre ||
        item?.estadoCodigo ||
        'Pendiente',


      estadoColor:
        item?.estadoColor ||
        '',


      fechaEmision:
        item?.fechaEmision ??
        null,


      fechaVencimiento:
        item?.fechaVencimiento ??
        null

    };

  }


  /* =======================================================
     PAGINACIÓN
     ======================================================= */

  paginaAnterior(): void {
    if (this.pagina <= 0 || this.loading) return;
    this.pagina--;
    this.aplicarFiltros(false);
  }

  paginaSiguiente(): void {
    if (!this.haySiguiente || this.loading) return;
    this.pagina++;
    this.aplicarFiltros(false);
  }

  cambiarTamanoPagina(): void {
    this.pagina = 0;
    this.aplicarFiltros(false);
  }

  /* =======================================================
     LIMPIAR FILTROS
     ======================================================= */

  limpiarFiltros(): void {

    this.filtroTexto = '';

    this.filtroModalidad = null;

    this.filtroEstado = '';


    this.aplicarFiltros();

  }


  /* =======================================================
     ABRIR MODAL NUEVO HONORARIO
     ======================================================= */

  abrirModalRegistro(): void {

    this.modoFormulario = 'NUEVO';

    this.honorarioEditando = null;

    this.honorarioSeleccionadoUuid =
      null;


    this.nuevoMovimiento =
      this.obtenerEstadoInicialFormulario();


    if (

      (
        this.catalogos.clientes.length === 0 ||

        this.catalogos.expedientes.length === 0
      )

      &&

      !this.cargandoClientes

    ) {

      this.cargarCatalogos();

    }


    this.mostrarModal = true;


    this.cdr.detectChanges();

  }


  /* =======================================================
     ABRIR MODAL PAGO
     ======================================================= */

  abrirModalPago(
    item: HonorarioLista | string
  ): void {

    this.modoFormulario = 'PAGO';

    this.honorarioEditando = null;

    this.nuevoMovimiento =
      this.obtenerEstadoInicialFormulario();


    this.nuevoMovimiento.tipoOperacion =
      'Abono / Pago de Cuota';


    if (

      (
        this.catalogos.clientes.length === 0 ||

        this.catalogos.expedientes.length === 0
      )

      &&

      !this.cargandoClientes

    ) {

      this.cargarCatalogos();

    }


    if (
      typeof item === 'string'
    ) {

      this.honorarioSeleccionadoUuid =
        item;


      const registro =
        this.honorarios.find(

          h => h.uuid === item

        );


      if (!registro) {

        alert(
          'No se encontró el honorario seleccionado.'
        );

        return;

      }


      this.cargarDatosHonorarioEnFormulario(
        registro
      );

    }


    else {

      this.honorarioSeleccionadoUuid =
        item.uuid;


      this.cargarDatosHonorarioEnFormulario(
        item
      );

    }


    this.nuevoMovimiento.honorarioUuid =
      this.honorarioSeleccionadoUuid;


    this.mostrarModal = true;


    this.cdr.detectChanges();

  }


  /* =======================================================
     ABRIR MODAL EDITAR HONORARIO
     ======================================================= */

  abrirModalEditar(
    item: HonorarioLista
  ): void {

    if (!item || !item.uuid) {

      alert(
        'No se encontró el honorario seleccionado.'
      );

      return;

    }


    this.modoFormulario = 'EDICION';

    this.honorarioEditando = item;

    this.honorarioSeleccionadoUuid =
      item.uuid;

    this.nuevoMovimiento =
      this.obtenerEstadoInicialFormulario();

    this.nuevoMovimiento.clienteId =
      Number(item.clienteId);

    this.nuevoMovimiento.expedienteId =
      Number(item.expedienteId);

    this.nuevoMovimiento.tipoOperacion =
      'Honorario Pactado';

    this.nuevoMovimiento.modalidad =
      item.modalidadCobro ||
      'Por Etapas';

    this.nuevoMovimiento.monto =
      this.convertirNumero(
        item.montoTotal
      );

    this.nuevoMovimiento.fecha =
      item.fechaEmision ||
      this.nuevoMovimiento.fecha;

    this.nuevoMovimiento.fechaVencimiento =
      item.fechaVencimiento ??
      null;

    this.nuevoMovimiento.concepto =
      item.concepto ||
      'Honorarios profesionales';

    this.nuevoMovimiento.honorarioUuid =
      item.uuid;


    if (
      (
        this.catalogos.clientes.length === 0 ||
        this.catalogos.expedientes.length === 0
      )
      &&
      !this.cargandoClientes
    ) {

      this.cargarCatalogos();

    }


    this.mostrarModal = true;

    this.cdr.detectChanges();

  }


  /* =======================================================
     CARGAR DATOS HONORARIO PARA PAGO
     ======================================================= */

  private cargarDatosHonorarioEnFormulario(
    registro: HonorarioLista
  ): void {

    this.nuevoMovimiento.clienteId =
      Number(registro.clienteId);


    this.nuevoMovimiento.expedienteId =
      Number(registro.expedienteId);


    this.nuevoMovimiento.modalidad =
      registro.modalidadCobro ||
      'Por Etapas';


    this.nuevoMovimiento.tipoOperacion =
      'Abono / Pago de Cuota';


    this.nuevoMovimiento.honorarioUuid =
      registro.uuid;

  }


  /* =======================================================
     CERRAR MODAL
     ======================================================= */

  cerrarModal(): void {

    if (this.guardando) {

      return;

    }


    this.cerrarModalForzado();

  }


  private cerrarModalForzado(): void {

    this.mostrarModal = false;


    this.honorarioSeleccionadoUuid =
      null;

    this.modoFormulario = 'NUEVO';

    this.honorarioEditando = null;


    this.nuevoMovimiento =
      this.obtenerEstadoInicialFormulario();


    this.cdr.detectChanges();

  }


  /* =======================================================
     GUARDAR MOVIMIENTO
     ======================================================= */

  guardarMovimiento(): void {

    if (this.guardando) {

      return;

    }


    if (this.modoFormulario === 'EDICION') {

      this.guardarEdicionHonorario();

      return;

    }


    const clienteIdParsed =

      this.nuevoMovimiento.clienteId !== null &&

      this.nuevoMovimiento.clienteId !== undefined

        ? Number(
            this.nuevoMovimiento.clienteId
          )

        : null;


    const expedienteIdParsed =

      this.nuevoMovimiento.expedienteId !== null &&

      this.nuevoMovimiento.expedienteId !== undefined

        ? Number(
            this.nuevoMovimiento.expedienteId
          )

        : null;


    const montoParsed =

      this.nuevoMovimiento.monto !== null &&

      this.nuevoMovimiento.monto !== undefined

        ? Number(
            this.nuevoMovimiento.monto
          )

        : 0;


    if (

      clienteIdParsed === null ||

      Number.isNaN(clienteIdParsed) ||

      clienteIdParsed <= 0

    ) {

      alert(
        'Por favor seleccione un cliente.'
      );

      return;

    }


    if (

      expedienteIdParsed === null ||

      Number.isNaN(expedienteIdParsed) ||

      expedienteIdParsed <= 0

    ) {

      alert(
        'Por favor seleccione un expediente.'
      );

      return;

    }


    const tipoOperacion =
      this.nuevoMovimiento
        .tipoOperacion
        ?.trim();


    if (!tipoOperacion) {

      alert(
        'Por favor seleccione un tipo de operación.'
      );

      return;

    }


    if (

      tipoOperacion ===
        'Abono / Pago de Cuota'

      &&

      (
        !this.honorarioSeleccionadoUuid ||

        !this.honorarioSeleccionadoUuid.trim()
      )

    ) {

      alert(
        'Para registrar un pago debe seleccionar un honorario existente desde la tabla.'
      );

      return;

    }


    if (

      tipoOperacion ===
        'Gasto Reembolsable'

      &&

      (
        !this.honorarioSeleccionadoUuid ||

        !this.honorarioSeleccionadoUuid.trim()
      )

    ) {

      alert(
        'Para registrar un gasto debe seleccionar un honorario existente.'
      );

      return;

    }


    const modalidad =
      this.nuevoMovimiento
        .modalidad
        ?.trim();


    if (!modalidad) {

      alert(
        'Por favor seleccione una modalidad de cobro.'
      );

      return;

    }


    if (

      Number.isNaN(montoParsed) ||

      montoParsed <= 0

    ) {

      alert(
        'Por favor ingrese un monto válido mayor a 0.'
      );

      return;

    }


    if (

      !this.nuevoMovimiento.fecha ||

      !this.nuevoMovimiento.fecha.trim()

    ) {

      alert(
        'Por favor seleccione una fecha.'
      );

      return;

    }


    if (
      tipoOperacion === 'Honorario Pactado' &&
      (
        !this.nuevoMovimiento.fechaVencimiento ||
        !this.nuevoMovimiento.fechaVencimiento.trim()
      )
    ) {

      alert(
        'Por favor seleccione una fecha de vencimiento.'
      );

      return;

    }


    const payload: RegistroMovimientoDTO = {

      clienteId:
        clienteIdParsed,

      expedienteId:
        expedienteIdParsed,

      tipoOperacion:
        tipoOperacion,

      modalidad:
        modalidad,

      monto:
        montoParsed,

      fecha:
        this.nuevoMovimiento.fecha,

      fechaVencimiento:
        tipoOperacion === 'Honorario Pactado'
          ? this.nuevoMovimiento.fechaVencimiento
          : null,

      etapaProcesal:
        this.nuevoMovimiento
          .etapaProcesal
          ?.trim() ||
        'Sin etapa específica',

      concepto:
        this.nuevoMovimiento
          .concepto
          ?.trim() ||
        'Honorarios profesionales',

      observaciones:
        this.nuevoMovimiento
          .observaciones
          ?.trim() ||
        '',

      honorarioUuid:

        tipoOperacion ===
          'Honorario Pactado'

          ? null

          : this.honorarioSeleccionadoUuid

    };


    console.log(
      '==================================='
    );

    console.log(
      'GUARDANDO MOVIMIENTO'
    );

    console.log(
      'PAYLOAD:',
      payload
    );

    console.log(
      '==================================='
    );


    this.guardando = true;


    this.cdr.detectChanges();


    this.service.registrarMovimiento(payload)
      .subscribe({


        next: (respuesta) => {

          console.log(
            'MOVIMIENTO REGISTRADO CORRECTAMENTE:',
            respuesta
          );


          this.guardando = false;


          this.cerrarModalForzado();


          this.cargarResumen();

          this.aplicarFiltros();


          if (
            tipoOperacion ===
            'Honorario Pactado'
          ) {

            alert(
              'Honorario registrado correctamente.'
            );

          }

          else if (
            tipoOperacion ===
            'Abono / Pago de Cuota'
          ) {

            alert(
              'Pago registrado correctamente.'
            );

          }

          else {

            alert(
              'Movimiento registrado correctamente.'
            );

          }

        },


        error: (err) => {

          this.guardando = false;


          this.cdr.detectChanges();


          console.error(
            'ERROR AL REGISTRAR MOVIMIENTO:',
            err
          );


          console.error(
            'Código HTTP:',
            err?.status
          );


          console.error(
            'Respuesta backend:',
            err?.error
          );


          let mensaje =
            'Ocurrió un error al registrar el movimiento financiero.';


          const mensajeBackend =
            this.extraerMensajeError(
              err?.error
            );


          if (mensajeBackend) {

            mensaje =
              mensajeBackend;

          }

          else if (
            err?.status === 400
          ) {

            mensaje =
              'El backend rechazó los datos enviados. Revise cliente, expediente y monto.';

          }

          else if (
            err?.status === 401
          ) {

            mensaje =
              'La sesión no está autorizada. Inicie sesión nuevamente.';

          }

          else if (
            err?.status === 403
          ) {

            mensaje =
              'No tiene permisos para registrar este movimiento.';

          }

          else if (
            err?.status === 404
          ) {

            mensaje =
              'No se encontró el recurso solicitado.';

          }

          else if (
            err?.status === 409
          ) {

            mensaje =
              'Existe un conflicto con los datos registrados.';

          }

          else if (
            err?.status === 500
          ) {

            mensaje =
              'El backend produjo un error. Revise la consola de Spring Boot.';

          }

          else if (
            err?.status === 0
          ) {

            mensaje =
              'No se pudo conectar con Spring Boot.';

          }


          alert(mensaje);

        }

      });

  }


  /* =======================================================
     GUARDAR EDICIÓN DE HONORARIO
     ======================================================= */

  private guardarEdicionHonorario(): void {

    if (
      !this.honorarioSeleccionadoUuid ||
      !this.honorarioSeleccionadoUuid.trim()
    ) {

      alert(
        'No se encontró el honorario que desea editar.'
      );

      return;

    }


    const clienteId =
      this.nuevoMovimiento.clienteId !== null &&
      this.nuevoMovimiento.clienteId !== undefined
        ? Number(this.nuevoMovimiento.clienteId)
        : 0;

    const expedienteId =
      this.nuevoMovimiento.expedienteId !== null &&
      this.nuevoMovimiento.expedienteId !== undefined
        ? Number(this.nuevoMovimiento.expedienteId)
        : 0;

    const montoPactado =
      this.nuevoMovimiento.monto !== null &&
      this.nuevoMovimiento.monto !== undefined
        ? Number(this.nuevoMovimiento.monto)
        : 0;

    const modalidad =
      this.nuevoMovimiento.modalidad?.trim() || '';

    const fecha =
      this.nuevoMovimiento.fecha?.trim() || '';

    const fechaVencimiento =
      this.nuevoMovimiento.fechaVencimiento?.trim() || '';


    if (
      Number.isNaN(clienteId) ||
      clienteId <= 0
    ) {

      alert(
        'Por favor seleccione un cliente.'
      );

      return;

    }


    if (
      Number.isNaN(expedienteId) ||
      expedienteId <= 0
    ) {

      alert(
        'Por favor seleccione un expediente.'
      );

      return;

    }


    const expedienteValido =
      this.catalogos.expedientes.some(
        expediente =>
          Number(expediente.id) === expedienteId &&
          Number(expediente.clienteId) === clienteId
      );


    if (!expedienteValido) {

      alert(
        'El expediente seleccionado no pertenece al cliente indicado.'
      );

      return;

    }


    if (!modalidad) {

      alert(
        'Por favor seleccione una modalidad de cobro.'
      );

      return;

    }


    if (
      Number.isNaN(montoPactado) ||
      montoPactado <= 0
    ) {

      alert(
        'Por favor ingrese un monto pactado válido mayor a 0.'
      );

      return;

    }


    if (!fecha) {

      alert(
        'Por favor seleccione una fecha.'
      );

      return;

    }


    if (!fechaVencimiento) {

      alert(
        'Por favor seleccione una fecha de vencimiento.'
      );

      return;

    }


    const payload: ActualizarHonorarioDTO = {

      clienteId,

      expedienteId,

      modalidad,

      montoPactado,

      fecha,

      fechaVencimiento,

      etapaProcesal:
        this.nuevoMovimiento
          .etapaProcesal
          ?.trim() ||
        'Sin etapa específica',

      concepto:
        this.nuevoMovimiento
          .concepto
          ?.trim() ||
        'Honorarios profesionales',

      observaciones:
        this.nuevoMovimiento
          .observaciones
          ?.trim() ||
        ''

    };


    this.guardando = true;

    this.cdr.detectChanges();


    this.service.actualizarHonorario(this.honorarioSeleccionadoUuid, payload)
      .subscribe({

        next: (respuesta) => {

          console.log(
            'HONORARIO ACTUALIZADO CORRECTAMENTE:',
            respuesta
          );


          this.guardando = false;

          this.cerrarModalForzado();

          this.cargarResumen();

          this.aplicarFiltros();

          alert(
            'Honorario actualizado correctamente.'
          );

        },


        error: (err) => {

          this.guardando = false;

          this.cdr.detectChanges();

          console.error(
            'ERROR AL ACTUALIZAR HONORARIO:',
            err
          );


          let mensaje =
            'Ocurrió un error al actualizar el honorario.';


          const mensajeBackend =
            this.extraerMensajeError(
              err?.error
            );


          if (mensajeBackend) {

            mensaje = mensajeBackend;

          }

          else if (err?.status === 400) {

            mensaje =
              'El backend rechazó los datos enviados. Revise cliente, expediente, modalidad y monto.';

          }

          else if (err?.status === 401) {

            mensaje =
              'La sesión no está autorizada. Inicie sesión nuevamente.';

          }

          else if (err?.status === 403) {

            mensaje =
              'No tiene permisos para editar este honorario.';

          }

          else if (err?.status === 404) {

            mensaje =
              'No se encontró el honorario seleccionado.';

          }

          else if (err?.status === 500) {

            mensaje =
              'El backend produjo un error. Revise la consola de Spring Boot.';

          }

          else if (err?.status === 0) {

            mensaje =
              'No se pudo conectar con Spring Boot.';

          }


          alert(mensaje);

        }

      });

  }


  /* =======================================================
     NUEVO - VER DETALLE
     ======================================================= */

  verDetalle(
    item: HonorarioLista | string
  ): void {

    const uuid =
      typeof item === 'string'
        ? item
        : item?.uuid;


    if (
      !uuid ||
      !uuid.trim()
    ) {

      alert(
        'No se encontró el honorario seleccionado.'
      );

      return;

    }


    this.cargandoDetalle = true;

    this.detalleHonorario = null;

    this.mostrarModalDetalle = true;

    this.cdr.detectChanges();


    const params =
      new HttpParams()
        .set(
          '_t',
          Date.now().toString()
        );


    this.service.detalle(uuid, params)
      .subscribe({

        next: (data) => {

          console.log(
            'DETALLE DEL HONORARIO:',
            data
          );


          this.detalleHonorario =
            this.normalizarDetalleHonorario(
              data
            );


          this.cargandoDetalle = false;


          this.cdr.detectChanges();

        },


        error: (err) => {

          console.error(
            'ERROR AL OBTENER EL DETALLE DEL HONORARIO:',
            err
          );


          this.cargandoDetalle = false;

          this.mostrarModalDetalle = false;

          this.detalleHonorario = null;


          this.cdr.detectChanges();


          let mensaje =
            'No se pudo obtener el detalle del honorario.';


          const mensajeBackend =
            this.extraerMensajeError(
              err?.error
            );


          if (mensajeBackend) {

            mensaje = mensajeBackend;

          }

          else if (
            err?.status === 400
          ) {

            mensaje =
              'No se pudo consultar el honorario seleccionado.';

          }

          else if (
            err?.status === 401
          ) {

            mensaje =
              'La sesión no está autorizada. Inicie sesión nuevamente.';

          }

          else if (
            err?.status === 403
          ) {

            mensaje =
              'No tiene permisos para consultar este honorario.';

          }

          else if (
            err?.status === 404
          ) {

            mensaje =
              'No se encontró el honorario seleccionado.';

          }

          else if (
            err?.status === 500
          ) {

            mensaje =
              'El backend produjo un error al obtener el detalle. Revise la consola de Spring Boot.';

          }

          else if (
            err?.status === 0
          ) {

            mensaje =
              'No se pudo conectar con Spring Boot.';

          }


          alert(mensaje);

        }

      });

  }


  /* =======================================================
     NUEVO - CERRAR MODAL DE DETALLE
     ======================================================= */

  cerrarDetalle(): void {

    if (this.descargandoComprobante) {

      return;

    }


    this.mostrarModalDetalle = false;

    this.cargandoDetalle = false;

    this.detalleHonorario = null;


    this.cdr.detectChanges();

  }


  /* =======================================================
     NUEVO - EMITIR COMPROBANTE
     ======================================================= */

  emitirComprobante(): void {

    const detalle =
      this.detalleHonorario;


    if (
      !detalle ||
      !detalle.uuid ||
      !detalle.uuid.trim()
    ) {

      alert(
        'No se encontró el honorario seleccionado.'
      );

      return;

    }


    /*
     * El frontend verifica la condición para evitar mostrar
     * una acción inválida.
     *
     * El backend también realiza esta validación, por lo que
     * la seguridad no depende únicamente de Angular.
     */
    if (!detalle.puedeEmitirComprobante) {

      alert(
        'El comprobante solo puede emitirse cuando el honorario se encuentra completamente pagado.'
      );

      return;

    }


    if (this.descargandoComprobante) {

      return;

    }


    this.descargandoComprobante = true;


    this.cdr.detectChanges();


    this.service.comprobante(detalle.uuid)
      .subscribe({

        next: (response) => {

          this.descargandoComprobante = false;


          const blob =
            response.body;


          if (
            !blob ||
            blob.size === 0
          ) {

            this.cdr.detectChanges();


            alert(
              'El backend no devolvió un comprobante válido.'
            );

            return;

          }


          const nombreArchivo =
            this.obtenerNombreArchivoComprobante(

              response.headers.get(
                'content-disposition'
              ),

              detalle.uuid

            );


          const url =
            window.URL.createObjectURL(
              blob
            );


          const enlace =
            document.createElement(
              'a'
            );


          enlace.href =
            url;

          enlace.download =
            nombreArchivo;

          enlace.style.display =
            'none';


          document.body.appendChild(
            enlace
          );


          enlace.click();


          document.body.removeChild(
            enlace
          );


          setTimeout(
            () => {

              window.URL.revokeObjectURL(
                url
              );

            },
            1000
          );


          this.cdr.detectChanges();

        },


        error: (err) => {

          this.descargandoComprobante = false;


          this.cdr.detectChanges();


          console.error(
            'ERROR AL GENERAR EL COMPROBANTE:',
            err
          );


          let mensaje =
            'No se pudo generar el comprobante de pago.';


          if (
            err?.status === 400
          ) {

            mensaje =
              'El comprobante solo puede emitirse cuando el honorario se encuentra completamente pagado.';

          }

          else if (
            err?.status === 401
          ) {

            mensaje =
              'La sesión no está autorizada. Inicie sesión nuevamente.';

          }

          else if (
            err?.status === 403
          ) {

            mensaje =
              'No tiene permisos para emitir este comprobante.';

          }

          else if (
            err?.status === 404
          ) {

            mensaje =
              'No se encontró el honorario seleccionado.';

          }

          else if (
            err?.status === 500
          ) {

            mensaje =
              'El backend produjo un error al generar el PDF. Revise la consola de Spring Boot.';

          }

          else if (
            err?.status === 0
          ) {

            mensaje =
              'No se pudo conectar con Spring Boot.';

          }


          alert(mensaje);

        }

      });

  }


  /* =======================================================
     NUEVO - NORMALIZAR DETALLE
     ======================================================= */

  private normalizarDetalleHonorario(
    item: any
  ): DetalleHonorario {

    const pagos: PagoHonorarioDetalle[] =

      Array.isArray(
        item?.pagos
      )

        ? item.pagos.map(
            (pago: any) => ({

              id:
                this.convertirNumero(
                  pago?.id
                ),

              fecha:
                pago?.fecha ??
                null,

              monto:
                this.convertirNumero(
                  pago?.monto
                ),

              concepto:
                pago?.concepto ||
                '',

              observaciones:
                pago?.observaciones ||
                ''

            })
          )

        : [];


    return {

      uuid:
        item?.uuid ||
        '',


      clienteId:
        this.convertirNumero(
          item?.clienteId
        ),


      expedienteId:
        this.convertirNumero(
          item?.expedienteId
        ),


      clienteNombre:
        item?.clienteNombre ||
        'Cliente sin nombre',


      clienteDocumento:
        item?.clienteDocumento ||
        '',


      expedienteNumero:
        item?.expedienteNumero ||
        '',


      concepto:
        item?.concepto ||
        'Honorarios profesionales',


      modalidadCobro:
        item?.modalidadCobro ||
        '',


      etapaProcesal:
        item?.etapaProcesal ||
        'Sin etapa específica',


      observaciones:
        item?.observaciones ||
        '',


      montoPactado:
        this.convertirNumero(
          item?.montoPactado
        ),


      montoPagado:
        this.convertirNumero(
          item?.montoPagado
        ),


      saldoPendiente:
        this.convertirNumero(
          item?.saldoPendiente
        ),


      moneda:
        item?.moneda ||
        'PEN',


      estadoCodigo:
        item?.estadoCodigo ||
        'PENDIENTE',


      estadoNombre:
        item?.estadoNombre ||
        item?.estadoCodigo ||
        'Pendiente',


      fechaEmision:
        item?.fechaEmision ??
        null,


      fechaVencimiento:
        item?.fechaVencimiento ??
        null,


      vencido:
        Boolean(
          item?.vencido
        ),


      puedeEmitirComprobante:
        Boolean(
          item?.puedeEmitirComprobante
        ),


      pagos

    };

  }


  /* =======================================================
     NUEVO - OBTENER NOMBRE DEL PDF
     ======================================================= */

  private obtenerNombreArchivoComprobante(
    contentDisposition: string | null,
    uuid: string
  ): string {

    if (contentDisposition) {


      /*
       * Primero intentamos leer:
       *
       * filename*=UTF-8''archivo.pdf
       */
      const coincidenciaUtf8 =
        contentDisposition.match(
          /filename\*=UTF-8''([^;]+)/i
        );


      if (
        coincidenciaUtf8 &&
        coincidenciaUtf8[1]
      ) {

        try {

          return decodeURIComponent(

            coincidenciaUtf8[1]
              .replace(
                /["']/g,
                ''
              )
              .trim()

          );

        }

        catch {

          /*
           * Si no puede decodificarse,
           * utilizamos el nombre de respaldo.
           */

        }
      }


      /*
       * Formato normal:
       *
       * filename="archivo.pdf"
       */
      const coincidencia =
        contentDisposition.match(
          /filename="?([^";]+)"?/i
        );


      if (
        coincidencia &&
        coincidencia[1]
      ) {

        return coincidencia[1]
          .trim();

      }
    }


    const uuidLimpio =

      (
        uuid ||
        'honorario'
      )

        .replace(
          /[^a-zA-Z0-9-]/g,
          ''
        )

      ||

      'honorario';

    return `comprobante-pago-${uuidLimpio}.pdf`;
  }


  /* =======================================================
     ESTADO INICIAL FORMULARIO
     ======================================================= */

  private obtenerEstadoInicialFormulario():
    RegistroMovimientoDTO {

    const fechaActual =
      new Date();

    const anio =
      fechaActual.getFullYear();

    const mes =
      String(
        fechaActual.getMonth() + 1
      )
        .padStart(
          2,
          '0'
        );

    const dia =
      String(
        fechaActual.getDate()
      )
        .padStart(
          2,
          '0'
        );


    return {

      clienteId:
        null,
      expedienteId:
        null,
      tipoOperacion:
        'Honorario Pactado',
      modalidad:
        'Por Etapas',
      monto:
        null,
      fecha:
        `${anio}-${mes}-${dia}`,
      fechaVencimiento:
        null,
      etapaProcesal:
        'Sin etapa específica',
      concepto:
        '',
      observaciones:
        '',
      honorarioUuid:
        null
    };
  }


  /* =======================================================
     CUENTA VENCIDA
     ======================================================= */

  esHonorarioVencido(
    item: HonorarioLista
  ): boolean {

    if (
      !item ||
      this.convertirNumero(item.montoPendiente) <= 0 ||
      !item.fechaVencimiento
    ) {

      return false;

    }
    return item.fechaVencimiento <
      this.obtenerFechaLocalActual();
  }


  private obtenerFechaLocalActual(): string {
    const fechaActual =
      new Date();
    const anio =
      fechaActual.getFullYear();
    const mes =
      String(
        fechaActual.getMonth() + 1
      )
        .padStart(
          2,
          '0'
        );
    const dia =
      String(
        fechaActual.getDate()
      )
        .padStart(
          2,
          '0'
        );
    return `${anio}-${mes}-${dia}`;
  }


  /* =======================================================
     UTILIDADES
     ======================================================= */

  private convertirNumero(
    valor: any
  ): number {

    if (
      valor === null ||
      valor === undefined ||
      valor === ''

    ) {
      return 0;
    }

    const numero =
      Number(valor);

    return Number.isNaN(numero)
      ? 0
      : numero;
  }

  private extraerMensajeError(
    error: any
  ): string {

    if (!error) {
      return '';
    }

    if (
      typeof error === 'string'
    ) {
      return error;
    }

    if (
      typeof error?.message === 'string'
    ) {
      return error.message;
    }

    if (
      typeof error?.mensaje === 'string'
    ) {
      return error.mensaje;
    }


    if (
      typeof error?.error === 'string'
    ) {
      return error.error;
    }

    return '';
  }
}