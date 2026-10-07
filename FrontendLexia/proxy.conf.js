/**
 * Proxy de DESARROLLO de LEXIA — se carga desde angular.json
 * (serve.options.proxyConfig) y reenvia /api al backend en localhost:8080.
 *
 * Por que un .js y no un .json:
 *
 *   El dev-server reescribe el header Host al del destino (changeOrigin), pero
 *   el navegador conserva su propio header Origin (https://xxx.trycloudflare.com,
 *   http://192.168.x.x:4200, http://localhost:4200...). Spring compara Origin con
 *   Host y, al verlos distintos, aplica CORS y responde 403 porque ese origen no
 *   esta en lexia.cors.origenes.
 *
 *   Como app y API van siempre por el MISMO origen (lo que sirve es este
 *   dev-server), el navegador no exige cabeceras CORS: basta con no reenviarle
 *   Origin al backend para que las dos partes coincidan. Asi funciona con el
 *   tunel, con la IP local y con localhost sin tocar el backend ni reiniciarlo
 *   por cada URL nueva.
 *
 *   (PowerShell/curl no envian Origin, por eso esas pruebas nunca fallaron.)
 *
 * Si se cambia la forma del objeto hay que reiniciar 'npm start'.
 */
'use strict';

const configuracion = {
  '/api': {
    target: 'http://localhost:8080',
    secure: false,
    // Reescribe Host al del backend; el Origin lo quitamos en proxyReq.
    changeOrigin: true,
    logLevel: 'debug',
    configure(proxy) {
      proxy.on('proxyReq', (peticionReenviada) => {
        peticionReenviada.removeHeader('origin');
      });
    },
  },
};

module.exports = configuracion;
