/**
 * Configuracion de PRODUCCION. angular.json la sustituye por la de desarrollo
 * cuando se compila con la configuracion 'production'.
 *
 * La URL real del backend se define cuando el equipo decida donde se despliega
 * (ver la conversacion sobre ubicacion de datos: el backend y la base con
 * expedientes reales no deberian vivir en cualquier proveedor).
 */
export const environment = {
  produccion: true,
  apiUrl: '/api/v1',
};
