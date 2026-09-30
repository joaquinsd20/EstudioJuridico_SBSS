import { Injectable, computed, signal } from '@angular/core';

export interface MensajeChat {
  id: string;
  texto: string;
  fechaHora: string;
  esMio: boolean;
}

export interface ContactoChat {
  id: string;
  nombre: string;
  rol: string;
  avatar: string;
  enLinea: boolean;
  noLeidos: number;
  ultimoMensaje: string;
  mensajes: MensajeChat[];
}

@Injectable({ providedIn: 'root' })
export class MensajesService {
  readonly contactos = signal<ContactoChat[]>([
    {
      id: '1',
      nombre: 'María Torres',
      rol: 'Abogada',
      avatar: 'MT',
      enLinea: true,
      noLeidos: 2,
      ultimoMensaje: 'Revisé el expediente pendiente.',
      mensajes: [
        {
          id: '1-1',
          texto: 'Buenos días, revisé el expediente pendiente.',
          fechaHora: '09:10',
          esMio: false
        },
        {
          id: '1-2',
          texto: 'Te dejé las observaciones para la revisión.',
          fechaHora: '09:12',
          esMio: false
        }
      ]
    },
    {
      id: '2',
      nombre: 'Carlos Mendoza',
      rol: 'Asistente legal',
      avatar: 'CM',
      enLinea: true,
      noLeidos: 0,
      ultimoMensaje: 'Documento cargado correctamente.',
      mensajes: [
        {
          id: '2-1',
          texto: 'Documento cargado correctamente.',
          fechaHora: '08:45',
          esMio: false
        }
      ]
    },
    {
      id: '3',
      nombre: 'Lucía Ramos',
      rol: 'Administración',
      avatar: 'LR',
      enLinea: false,
      noLeidos: 1,
      ultimoMensaje: 'Quedó registrada la reunión.',
      mensajes: [
        {
          id: '3-1',
          texto: 'Quedó registrada la reunión de esta tarde.',
          fechaHora: 'Ayer',
          esMio: false
        }
      ]
    }
  ]);

  private readonly contactoSeleccionadoId = signal<string | null>('1');

  readonly contactoSeleccionado = computed<ContactoChat | null>(() => {
    const id = this.contactoSeleccionadoId();
    if (!id) {
      return null;
    }
    return this.contactos().find(contacto => contacto.id === id) ?? null;
  });

  readonly totalNoLeidos = computed(() =>
    this.contactos().reduce((total, contacto) => total + contacto.noLeidos, 0)
  );

  seleccionarContacto(id: string): void {
    const existe = this.contactos().some(contacto => contacto.id === id);
    if (!existe) {
      return;
    }

    this.contactoSeleccionadoId.set(id);
    this.contactos.update(contactos =>
      contactos.map(contacto =>
        contacto.id === id && contacto.noLeidos > 0
          ? { ...contacto, noLeidos: 0 }
          : contacto
      )
    );
  }

  enviarMensaje(contactoId: string, texto: string): void {
    const contenido = texto.trim();
    if (!contenido) {
      return;
    }

    const ahora = new Date();
    const mensaje: MensajeChat = {
      id: `${contactoId}-${ahora.getTime()}`,
      texto: contenido,
      fechaHora: ahora.toLocaleTimeString('es-PE', {
        hour: '2-digit',
        minute: '2-digit'
      }),
      esMio: true
    };

    this.contactos.update(contactos =>
      contactos.map(contacto =>
        contacto.id === contactoId
          ? {
              ...contacto,
              ultimoMensaje: contenido,
              mensajes: [...contacto.mensajes, mensaje]
            }
          : contacto
      )
    );
  }
}
