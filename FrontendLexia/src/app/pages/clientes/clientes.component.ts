import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';

import {
  AbogadoOpcion, ClienteGuardar, ClienteResumen, ClientesResumen, CoincidenciaConflicto,
  ContactoCliente, FichaCliente, RegistrarComunicacion, TipoPersona, VerificarConflictoResponse,
  VinculoCliente
} from '../../core/models/cliente.models';
import { AuthService } from '../../core/services/auth.service';
import { ClienteService } from '../../core/services/cliente.service';
import { PanelComponent } from '../panel/panel.component';

@Component({
  selector: 'app-clientes',
  standalone: true,
  imports: [CommonModule, FormsModule, PanelComponent],
  templateUrl: './clientes.component.html',
  styleUrl: './clientes.component.css'
})
export class ClientesComponent implements OnInit {
  private readonly service = inject(ClienteService);
  private readonly cdr = inject(ChangeDetectorRef);
  readonly auth = inject(AuthService);

  clientes: ClienteResumen[] = [];
  resumen: ClientesResumen = {
    total: 0, activos: 0, naturales: 0, juridicas: 0, entidadesPublicas: 0,
    inactivos: 0, nuevosEsteMes: 0, conExpedientes: 0
  };
  abogados: AbogadoOpcion[] = [];
  ficha: FichaCliente | null = null;
  seleccionado: ClienteResumen | null = null;

  cargando = false;
  cargandoFicha = false;
  guardando = false;
  error = '';
  exito = '';

  filtros = { documento: '', nombre: '', tipoPersona: '', estado: '' };
  pagina = 0;
  tamanoPagina = 20;
  readonly tamanosPagina = [10, 20, 40, 100];
  totalPaginas = 0;
  totalElementos = 0;

  modalCliente = false;
  modalComunicacion = false;
  modalConflicto = false;
  modoEdicion = false;
  uuidEdicion: string | null = null;
  creacionPendiente = false;

  conflicto: VerificarConflictoResponse | null = null;
  conflictoManual = { nombre: '', numeroDocumento: '' };

  form: ClienteGuardar = this.nuevoCliente();
  comunicacion: RegistrarComunicacion = this.nuevaComunicacion();

  ngOnInit(): void {
    this.cargarTodo();
  }

  cargarTodo(): void {
    this.cargando = true;
    this.error = '';
    forkJoin({
      pagina: this.service.listar(this.filtros, this.pagina, this.tamanoPagina),
      resumen: this.service.resumen(),
      catalogos: this.service.catalogos()
    }).subscribe({
      next: r => {
        this.clientes = r.pagina.contenido;
        this.totalPaginas = r.pagina.totalPaginas;
        this.totalElementos = r.pagina.totalElementos;
        this.resumen = r.resumen;
        this.abogados = r.catalogos.abogados;
        this.cargando = false;
        if (this.seleccionado) {
          const actual = this.clientes.find(c => c.uuid === this.seleccionado?.uuid);
          if (actual) this.seleccionar(actual);
          else if (this.clientes.length) this.seleccionar(this.clientes[0]);
          else this.limpiarSeleccion();
        } else if (this.clientes.length) {
          this.seleccionar(this.clientes[0]);
        }
        this.actualizarVista();
      },
      error: e => { this.cargando = false; this.mostrarError(this.mensajeError(e)); this.actualizarVista(); }
    });
  }

  buscar(): void {
    this.pagina = 0;
    this.cargarLista();
  }

  limpiarFiltros(): void {
    this.filtros = { documento: '', nombre: '', tipoPersona: '', estado: '' };
    this.buscar();
  }

  filtrarTipo(tipo: string): void {
    this.filtros.tipoPersona = tipo;
    this.buscar();
  }

  cambiarTamanoPagina(tamano: number): void {
    if (this.cargando || !this.tamanosPagina.includes(tamano) || tamano === this.tamanoPagina) return;
    this.tamanoPagina = tamano;
    this.buscar();
  }

  cambiarPagina(delta: number): void {
    if (this.cargando) return;
    const nueva = this.pagina + delta;
    if (nueva < 0 || nueva >= this.totalPaginas) return;
    this.pagina = nueva;
    this.cargarLista();
  }

  seleccionar(cliente: ClienteResumen): void {
    this.seleccionado = cliente;
    this.cargandoFicha = true;
    this.service.ficha(cliente.uuid).subscribe({
      next: ficha => { this.ficha = ficha; this.cargandoFicha = false; this.actualizarVista(); },
      error: e => { this.cargandoFicha = false; this.mostrarError(this.mensajeError(e)); this.actualizarVista(); }
    });
  }

  limpiarSeleccion(): void {
    this.seleccionado = null;
    this.ficha = null;
  }

  abrirNuevo(): void {
    this.modoEdicion = false;
    this.uuidEdicion = null;
    this.form = this.nuevoCliente();
    this.modalCliente = true;
  }

  abrirEditarCliente(cliente: ClienteResumen): void {
    this.seleccionado = cliente;
    this.cargandoFicha = true;
    this.service.ficha(cliente.uuid).subscribe({
      next: ficha => {
        this.ficha = ficha;
        this.cargandoFicha = false;
        this.abrirEditar();
        this.actualizarVista();
      },
      error: e => { this.cargandoFicha = false; this.mostrarError(this.mensajeError(e)); this.actualizarVista(); }
    });
  }

  abrirEditar(): void {
    if (!this.ficha) return;
    const c = this.ficha.cliente;
    this.modoEdicion = true;
    this.uuidEdicion = c.uuid;
    this.form = {
      tipoPersona: c.tipoPersona,
      tipoDocumento: c.tipoDocumento,
      numeroDocumento: c.numeroDocumento,
      nombres: c.nombres ?? '',
      apellidoPaterno: c.apellidoPaterno ?? '',
      apellidoMaterno: c.apellidoMaterno ?? '',
      razonSocial: c.razonSocial ?? '',
      nombreComercial: c.nombreComercial ?? '',
      email: c.email ?? '',
      telefonoPrincipal: c.telefonoPrincipal ?? '',
      telefonoSecundario: c.telefonoSecundario ?? '',
      direccion: c.direccion ?? '',
      distrito: c.distrito ?? '',
      provincia: c.provincia ?? '',
      departamento: c.departamento ?? '',
      estado: c.estado,
      origenCaptacion: c.origenCaptacion ?? '',
      situacionFinanciera: c.situacionFinanciera ?? '',
      abogadoResponsableUuid: c.abogadoResponsableUuid ?? '',
      observaciones: c.observaciones ?? '',
      contactos: this.ficha.contactos.map(x => ({ ...x })),
      vinculados: this.ficha.vinculados.map(x => ({ ...x }))
    };
    this.modalCliente = true;
  }

  cerrarCliente(): void {
    if (this.guardando) return;
    this.modalCliente = false;
  }

  agregarContacto(): void {
    this.form.contactos.push(this.nuevoContacto());
  }

  quitarContacto(i: number): void {
    this.form.contactos.splice(i, 1);
  }

  agregarVinculado(): void {
    this.form.vinculados.push(this.nuevoVinculo());
  }

  quitarVinculado(i: number): void {
    this.form.vinculados.splice(i, 1);
  }

  guardarCliente(): void {
    const error = this.validarFormulario();
    if (error) { this.mostrarError(error); return; }

    if (this.modoEdicion && this.uuidEdicion) {
      this.persistirEdicion();
      return;
    }

    // El alta siempre pasa primero por la revisión de conflicto. Si existen
    // coincidencias no se bloquea automáticamente: se obliga a revisarlas y el
    // usuario decide si continúa, tal como pide el requerimiento.
    this.guardando = true;
    this.service.verificarConflicto(this.payloadConflictoDesdeFormulario()).subscribe({
      next: r => {
        this.guardando = false;
        if (r.posibleConflicto) {
          this.conflicto = r;
          this.creacionPendiente = true;
          this.modalConflicto = true;
        } else {
          this.persistirCreacion();
        }
        this.actualizarVista();
      },
      error: e => { this.guardando = false; this.mostrarError(this.mensajeError(e)); this.actualizarVista(); }
    });
  }

  confirmarCreacionConConflicto(): void {
    if (!this.creacionPendiente) return;
    this.modalConflicto = false;
    this.creacionPendiente = false;
    this.persistirCreacion();
  }

  cancelarConflicto(): void {
    this.modalConflicto = false;
    this.creacionPendiente = false;
  }

  abrirVerificador(): void {
    this.conflicto = null;
    this.conflictoManual = { nombre: '', numeroDocumento: '' };
    this.creacionPendiente = false;
    this.modalConflicto = true;
  }

  verificarManual(): void {
    if (!this.conflictoManual.nombre.trim() && !this.conflictoManual.numeroDocumento.trim()) {
      this.mostrarError('Ingresa al menos un nombre, DNI o RUC para verificar.');
      return;
    }
    this.guardando = true;
    this.service.verificarConflicto({
      nombre: this.conflictoManual.nombre,
      numeroDocumento: this.conflictoManual.numeroDocumento,
      relacionados: []
    }).subscribe({
      next: r => {
        this.conflicto = r;
        this.guardando = false;
        this.actualizarVista();
      },
      error: e => {
        this.guardando = false;
        this.mostrarError(this.mensajeError(e));
        this.actualizarVista();
      }
    });
  }

  abrirComunicacion(): void {
    if (!this.ficha) return;
    this.comunicacion = this.nuevaComunicacion();
    this.modalComunicacion = true;
  }

  guardarComunicacion(): void {
    if (!this.ficha || !this.comunicacion.asunto.trim()) {
      this.mostrarError('El asunto de la comunicación es obligatorio.');
      return;
    }
    this.guardando = true;
    this.service.registrarComunicacion(this.ficha.cliente.uuid, this.comunicacion).subscribe({
      next: () => {
        this.guardando = false;
        this.modalComunicacion = false;
        this.mostrarExito('Comunicación registrada correctamente.');
        if (this.seleccionado) this.seleccionar(this.seleccionado);
        this.cargarLista(false);
        this.actualizarVista();
      },
      error: e => { this.guardando = false; this.mostrarError(this.mensajeError(e)); this.actualizarVista(); }
    });
  }

  nombreTipo(tipo: string): string {
    return ({ NATURAL: 'Persona Natural', JURIDICA: 'Persona Jurídica', ENTIDAD_PUBLICA: 'Entidad Pública' } as Record<string, string>)[tipo] ?? tipo;
  }

  nombreEstado(estado: string): string {
    return ({ PROSPECTO: 'Potencial', ACTIVO: 'Activo', INACTIVO: 'Inactivo', ANTIGUO: 'Antiguo' } as Record<string, string>)[estado] ?? estado;
  }

  iniciales(nombre: string): string {
    return nombre.split(/\s+/).filter(Boolean).slice(0, 2).map(x => x[0]).join('').toUpperCase();
  }

  ubicacionCliente(): string {
    if (!this.ficha) return '—';
    return [this.ficha.cliente.distrito, this.ficha.cliente.provincia, this.ficha.cliente.departamento]
      .filter((x): x is string => !!x)
      .join(', ') || '—';
  }

  origenConflicto(c: CoincidenciaConflicto): string {
    return ({
      CLIENTE: 'Cliente registrado', CONTACTO_CLIENTE: 'Representante / contacto',
      CONTRAPARTE: 'Contraparte', VINCULADO_CLIENTE: 'Persona / empresa vinculada'
    } as Record<string, string>)[c.origen] ?? c.origen;
  }

  private persistirCreacion(): void {
    this.guardando = true;
    this.service.crear(this.form).subscribe({
      next: r => {
        this.guardando = false;
        this.modalCliente = false;
        this.mostrarExito('Cliente registrado correctamente.');
        this.refrescarTrasGuardar(r.uuid);
        this.actualizarVista();
      },
      error: e => { this.guardando = false; this.mostrarError(this.mensajeError(e)); this.actualizarVista(); }
    });
  }

  private persistirEdicion(): void {
    if (!this.uuidEdicion) return;
    this.guardando = true;
    this.service.actualizar(this.uuidEdicion, this.form).subscribe({
      next: () => {
        const uuid = this.uuidEdicion!;
        this.guardando = false;
        this.modalCliente = false;
        this.mostrarExito('Ficha del cliente actualizada.');
        this.refrescarTrasGuardar(uuid);
        this.actualizarVista();
      },
      error: e => { this.guardando = false; this.mostrarError(this.mensajeError(e)); this.actualizarVista(); }
    });
  }

  private refrescarTrasGuardar(uuid: string): void {
    forkJoin({ pagina: this.service.listar(this.filtros, this.pagina, this.tamanoPagina), resumen: this.service.resumen() }).subscribe({
      next: r => {
        this.clientes = r.pagina.contenido;
        this.totalPaginas = r.pagina.totalPaginas;
        this.totalElementos = r.pagina.totalElementos;
        this.resumen = r.resumen;
        const item = this.clientes.find(c => c.uuid === uuid);
        if (item) this.seleccionar(item);
        else this.service.ficha(uuid).subscribe(f => { this.ficha = f; this.actualizarVista(); });
        this.actualizarVista();
      }
    });
  }

  private cargarLista(reseleccionar = true): void {
    this.cargando = true;
    this.service.listar(this.filtros, this.pagina, this.tamanoPagina).subscribe({
      next: r => {
        this.clientes = r.contenido;
        this.totalPaginas = r.totalPaginas;
        this.totalElementos = r.totalElementos;
        this.cargando = false;
        if (reseleccionar) {
          if (this.clientes.length) this.seleccionar(this.clientes[0]);
          else this.limpiarSeleccion();
        }
        this.actualizarVista();
      },
      error: e => { this.cargando = false; this.mostrarError(this.mensajeError(e)); this.actualizarVista(); }
    });
  }

  private payloadConflictoDesdeFormulario() {
    return {
      nombre: this.nombreFormulario(),
      numeroDocumento: this.form.numeroDocumento,
      relacionados: [
        ...this.form.contactos.map(c => ({ nombre: c.nombreCompleto, numeroDocumento: c.numeroDocumento ?? '' })),
        ...this.form.vinculados.map(v => ({ nombre: v.nombre, numeroDocumento: v.numeroDocumento ?? '' }))
      ]
    };
  }

  private nombreFormulario(): string {
    return this.form.tipoPersona === 'NATURAL'
      ? [this.form.apellidoPaterno, this.form.apellidoMaterno, this.form.nombres].filter(Boolean).join(' ')
      : this.form.razonSocial;
  }

  private validarFormulario(): string | null {
    if (!this.form.numeroDocumento.trim()) return 'El número de documento es obligatorio.';
    if (this.form.tipoDocumento === 'DNI' && !/^\d{8}$/.test(this.form.numeroDocumento)) return 'El DNI debe tener 8 dígitos.';
    if (this.form.tipoDocumento === 'RUC' && !/^\d{11}$/.test(this.form.numeroDocumento)) return 'El RUC debe tener 11 dígitos.';
    if (this.form.tipoPersona === 'NATURAL' && (!this.form.nombres.trim() || !this.form.apellidoPaterno.trim())) {
      return 'Nombres y apellido paterno son obligatorios para una persona natural.';
    }
    if (this.form.tipoPersona !== 'NATURAL' && !this.form.razonSocial.trim()) return 'La razón social es obligatoria.';
    if (this.form.contactos.some(c => !c.nombreCompleto.trim())) return 'Todos los contactos deben tener nombre.';
    if (this.form.vinculados.some(v => !v.nombre.trim())) return 'Todas las personas o empresas vinculadas deben tener nombre.';
    return null;
  }

  private nuevoCliente(): ClienteGuardar {
    return {
      tipoPersona: 'NATURAL', tipoDocumento: 'DNI', numeroDocumento: '', nombres: '', apellidoPaterno: '',
      apellidoMaterno: '', razonSocial: '', nombreComercial: '', email: '', telefonoPrincipal: '',
      telefonoSecundario: '', direccion: '', distrito: '', provincia: '', departamento: '', estado: 'PROSPECTO',
      origenCaptacion: '', situacionFinanciera: '', abogadoResponsableUuid: '', observaciones: '', contactos: [], vinculados: []
    };
  }

  private nuevoContacto(): ContactoCliente {
    return { nombreCompleto: '', tipoDocumento: 'DNI', numeroDocumento: '', cargo: '', email: '', telefono: '', representanteLegal: false, puedeRecibirDocumentos: false, numeroPoder: '' };
  }

  private nuevoVinculo(): VinculoCliente {
    return { tipoPersona: 'NATURAL' as TipoPersona, nombre: '', tipoDocumento: 'DNI', numeroDocumento: '', relacion: '', observaciones: '' };
  }

  private nuevaComunicacion(): RegistrarComunicacion {
    return { tipo: 'LLAMADA', asunto: '', detalle: '', canal: 'TELEFONO', fecha: null };
  }

  private mostrarExito(mensaje: string): void {
    this.exito = mensaje;
    this.error = '';
    this.actualizarVista();
    setTimeout(() => {
      this.exito = '';
      this.actualizarVista();
    }, 4500);
  }

  private mostrarError(mensaje: string): void {
    this.error = mensaje;
    this.exito = '';
    this.actualizarVista();
    setTimeout(() => {
      this.error = '';
      this.actualizarVista();
    }, 6500);
  }

  private actualizarVista(): void {
    // Angular 21 funciona en modo zoneless. Las respuestas HTTP actualizan las
    // propiedades en memoria, pero no siempre repintan el template hasta el
    // siguiente evento del usuario. Documentos y Expedientes ya resuelven el
    // mismo caso forzando la detección de cambios al terminar cada petición.
    try {
      this.cdr.detectChanges();
    } catch {
      // El componente pudo destruirse mientras terminaba una petición.
    }
  }

  private mensajeError(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      return error.error?.mensaje || (error.status === 0
        ? 'No se pudo conectar con el backend.'
        : `No se pudo completar la operación (${error.status}).`);
    }
    return 'Ocurrió un error inesperado.';
  }
}
