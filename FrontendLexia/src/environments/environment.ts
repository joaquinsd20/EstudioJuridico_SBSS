/**
 * Configuracion de DESARROLLO.
 *
 * La URL de la API no se escribe dentro de los servicios: cuando el backend deje
 * de estar en localhost (despliegue de pruebas, produccion) se cambia aqui y no
 * hay que tocar codigo.
 */
export const environment = {
  produccion: false,
  apiUrl: 'http://localhost:8080/api/v1',
};
