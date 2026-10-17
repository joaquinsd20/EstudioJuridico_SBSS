/**
 * Configuracion de DESARROLLO.
 *
 * La URL de la API no se escribe dentro de los servicios: cuando el backend deje
 * de estar en localhost (despliegue de pruebas, produccion) se cambia aqui y no
 * hay que tocar codigo.
 *
 * En desarrollo la URL es RELATIVA a proposito: el dev-server de Angular reenvia
 * /api/** al backend en http://localhost:8080 (proxy.conf.js, cargado desde
 * angular.json -> serve.options.proxyConfig). Al ser mismo origen:
 *
 *   - no hace falta configurar CORS en el backend por cada origen nuevo,
 *   - se puede abrir la app desde el celular (IP local o tunel) sin cambiar
 *     'localhost:8080', que en el celular apuntaria al propio telefono.
 *
 * Si se sirve con 'ng serve' SIN proxyConfig, las llamadas a la API fallan.
 */
export const environment = {
  produccion: false,
  apiUrl: '/api/v1',
  mensajeriaEventos: false,
};
