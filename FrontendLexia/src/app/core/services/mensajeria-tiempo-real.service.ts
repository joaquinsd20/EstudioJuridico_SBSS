import { Injectable, inject } from '@angular/core';
import { AuthService } from './auth.service';
import { environment } from '../../../environments/environment';

/** SSE autenticado con Authorization; nunca se envía el JWT por URL.
 * Sin servicio persistente/compartido en producción, mantener deshabilitado.
 */
@Injectable({ providedIn: 'root' })
export class MensajeriaTiempoRealService {
  private readonly auth = inject(AuthService);
  private controller: AbortController | null = null;
  private activo = false;

  iniciar(actualizar: () => void): void {
    if (this.activo || !environment.mensajeriaEventos) return;
    this.activo = true;
    void this.conectar(actualizar);
  }

  detener(): void { this.activo = false; this.controller?.abort(); this.controller = null; }

  private async conectar(actualizar: () => void): Promise<void> {
    let intento = 0;
    while (this.activo) {
      const token = this.auth.token;
      if (!token) { this.detener(); return; }
      const ctrl = new AbortController();
      this.controller = ctrl;
      try {
        const res = await fetch(`${environment.apiUrl}/mensajeria/eventos`, {
          headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' },
          signal: ctrl.signal, cache: 'no-store',
        });
        if (res.status === 401 || res.status === 403 || res.status === 503) { this.detener(); return; }
        if (!res.ok || !res.body) throw new Error('Canal no disponible');
        intento = 0;
        const reader = res.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        while (this.activo) {
          const { value, done } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });
          const partes = buffer.split(/\r?\n\r?\n/);
          buffer = partes.pop() ?? '';
          for (const evento of partes) if (evento.split(/\r?\n/).some(l => l.trim() === 'event:mensajeria')) actualizar();
          if (buffer.length > 16384) buffer = '';
        }
      } catch { /* HTTP polling sigue funcionando */ }
      if (!this.activo) break;
      const espera = Math.min(30000, 1000 * 2 ** Math.min(intento++, 5));
      await new Promise<void>(resolve => setTimeout(resolve, espera));
    }
  }
}
