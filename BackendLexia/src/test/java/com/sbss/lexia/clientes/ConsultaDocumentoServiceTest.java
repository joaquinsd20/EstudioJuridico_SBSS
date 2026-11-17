package com.sbss.lexia.clientes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sbss.lexia.clientes.dto.ClienteGestionDtos.DatosDocumento;
import com.sbss.lexia.clientes.service.ConsultaDocumentoService;
import com.sbss.lexia.clientes.service.ConsultaDocumentoService.ConsultaDocumentoException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Prueba el cliente de Decolecta contra un servidor HTTP falso en local. */
class ConsultaDocumentoServiceTest {

    private HttpServer servidor;
    private final AtomicReference<String> autorizacion = new AtomicReference<>();
    private final ObjectMapper mapper = JsonMapper.builder().build();

    @BeforeEach
    void iniciar() throws Exception {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/reniec/dni", ex -> {
            autorizacion.set(ex.getRequestHeaders().getFirst("Authorization"));
            boolean existe = ex.getRequestURI().getQuery().equals("numero=46027897");
            responder(ex, existe ? 200 : 400, existe
                    ? "{\"first_name\":\"ROXANA KARINA\",\"first_last_name\":\"DELGADO\",\"second_last_name\":\"HUAMANI\",\"full_name\":\"DELGADO HUAMANI ROXANA KARINA\",\"document_number\":\"46027897\"}"
                    : "{\"error\":\"Invalid request\"}");
        });
        servidor.createContext("/sunat/ruc", ex -> responder(ex, 200,
                "{\"razon_social\":\"REXTIE S.A.C.\",\"numero_documento\":\"20601030013\",\"estado\":\"ACTIVO\",\"condicion\":\"HABIDO\",\"direccion\":\"AV. JOSE GALVEZ BARRENECHEA NRO 566  INT. 101 \",\"distrito\":\"SAN ISIDRO\",\"provincia\":\"LIMA\",\"departamento\":\"LIMA\",\"lote\":\"-\"}"));
        servidor.start();
    }

    @AfterEach
    void detener() {
        servidor.stop(0);
    }

    private static void responder(com.sun.net.httpserver.HttpExchange ex, int estado, String cuerpo) throws java.io.IOException {
        byte[] b = cuerpo.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(estado, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }

    private ConsultaDocumentoService servicio(String token) {
        return new ConsultaDocumentoService(token,
                "http://127.0.0.1:" + servidor.getAddress().getPort() + "/", mapper);
    }

    @Test
    void dniDevuelveNombresYEnviaElToken() {
        DatosDocumento d = servicio("tok-prueba").consultar("dni", " 46027897 ");
        assertThat(d.nombres()).isEqualTo("ROXANA KARINA");
        assertThat(d.apellidoPaterno()).isEqualTo("DELGADO");
        assertThat(d.apellidoMaterno()).isEqualTo("HUAMANI");
        assertThat(autorizacion.get()).isEqualTo("Bearer tok-prueba");
    }

    @Test
    void rucDevuelveRazonSocialYDomicilioLimpio() {
        DatosDocumento d = servicio("t").consultar("RUC", "20601030013");
        assertThat(d.razonSocial()).isEqualTo("REXTIE S.A.C.");
        assertThat(d.direccion()).isEqualTo("AV. JOSE GALVEZ BARRENECHEA NRO 566 INT. 101");
        assertThat(d.distrito()).isEqualTo("SAN ISIDRO");
        assertThat(d.estadoContribuyente()).isEqualTo("ACTIVO");
    }

    @Test
    void dniInexistenteEs404() {
        assertThatThrownBy(() -> servicio("t").consultar("DNI", "12345678"))
                .isInstanceOfSatisfying(ConsultaDocumentoException.class,
                        e -> assertThat(e.getEstado()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void formatoInvalidoEs400SinLlamarAlProveedor() {
        assertThatThrownBy(() -> servicio("t").consultar("DNI", "123"))
                .isInstanceOfSatisfying(ConsultaDocumentoException.class,
                        e -> assertThat(e.getEstado()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> servicio("t").consultar("RUC", "30601030013"))
                .isInstanceOfSatisfying(ConsultaDocumentoException.class,
                        e -> assertThat(e.getEstado()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(autorizacion.get()).isNull();
    }

    @Test
    void sinTokenEs503() {
        assertThatThrownBy(() -> servicio(" ").consultar("DNI", "46027897"))
                .isInstanceOfSatisfying(ConsultaDocumentoException.class,
                        e -> assertThat(e.getEstado()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }
}
