package com.sbss.lexia.clientes.controller;

import com.sbss.lexia.clientes.dto.ClienteDetalleDto;
import com.sbss.lexia.clientes.dto.ClienteFiltro;
import com.sbss.lexia.clientes.dto.ClienteGestionDtos.Catalogos;
import com.sbss.lexia.clientes.dto.ClienteGestionDtos.DatosDocumento;
import com.sbss.lexia.clientes.dto.ClienteGestionDtos.FichaCliente;
import com.sbss.lexia.clientes.dto.ClienteGestionDtos.GuardarClienteRequest;
import com.sbss.lexia.clientes.dto.ClienteGestionDtos.RegistrarComunicacionRequest;
import com.sbss.lexia.clientes.dto.ClienteGestionDtos.VerificarConflictoRequest;
import com.sbss.lexia.clientes.dto.ClienteGestionDtos.VerificarConflictoResponse;
import com.sbss.lexia.clientes.dto.ClienteResumenDto;
import com.sbss.lexia.clientes.dto.ClientesResumenDto;
import com.sbss.lexia.clientes.service.ClienteService;
import com.sbss.lexia.clientes.service.ConsultaDocumentoService;
import com.sbss.lexia.clientes.service.ConsultaDocumentoService.ConsultaDocumentoException;
import com.sbss.lexia.shared.exception.ErrorRespuesta;
import com.sbss.lexia.shared.dto.PaginaDto;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/clientes")
@RequiredArgsConstructor
public class ClienteController {

    private final ClienteService clienteService;
    private final ConsultaDocumentoService consultaDocumentoService;

    @GetMapping
    @PreAuthorize("hasAuthority('CLIENTE_LEER')")
    public ResponseEntity<PaginaDto<ClienteResumenDto>> listar(
            @RequestParam(required = false) String documento,
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String tipoPersona,
            @RequestParam(required = false) String estado,
            @PageableDefault(size = 20, sort = "nombreBusqueda") Pageable pageable) {
        return ResponseEntity.ok(clienteService.listar(
                new ClienteFiltro(documento, nombre, tipoPersona, estado), pageable));
    }

    @GetMapping("/resumen")
    @PreAuthorize("hasAuthority('CLIENTE_LEER')")
    public ResponseEntity<ClientesResumenDto> resumen() {
        return ResponseEntity.ok(clienteService.resumen());
    }

    @GetMapping("/catalogos")
    @PreAuthorize("hasAuthority('CLIENTE_LEER')")
    public Catalogos catalogos() {
        return clienteService.catalogos();
    }

    @PostMapping("/conflictos/verificar")
    @PreAuthorize("hasAuthority('CLIENTE_CREAR')")
    public VerificarConflictoResponse verificarConflicto(@Valid @RequestBody VerificarConflictoRequest request) {
        return clienteService.verificarConflicto(request);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CLIENTE_CREAR')")
    public ResponseEntity<Map<String, String>> crear(@Valid @RequestBody GuardarClienteRequest request) {
        String uuid = clienteService.crear(request);
        return ResponseEntity.created(URI.create("/api/v1/clientes/" + uuid)).body(Map.of("uuid", uuid));
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasAuthority('CLIENTE_EDITAR')")
    public ResponseEntity<Void> actualizar(
            @PathVariable String uuid, @Valid @RequestBody GuardarClienteRequest request) {
        clienteService.actualizar(uuid, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{uuid}/comunicaciones")
    @PreAuthorize("hasAuthority('CLIENTE_EDITAR')")
    public ResponseEntity<Map<String, String>> registrarComunicacion(
            @PathVariable String uuid, @Valid @RequestBody RegistrarComunicacionRequest request) {
        String comunicacionUuid = clienteService.registrarComunicacion(uuid, request);
        return ResponseEntity.ok(Map.of("uuid", comunicacionUuid));
    }

    @GetMapping("/{uuid}/ficha")
    @PreAuthorize("hasAuthority('CLIENTE_LEER')")
    public FichaCliente ficha(@PathVariable String uuid) {
        return clienteService.ficha(uuid);
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('CLIENTE_LEER')")
    public ResponseEntity<ClienteDetalleDto> obtener(@PathVariable String uuid) {
        return ResponseEntity.ok(clienteService.obtener(uuid));
    }

    /**
     * Datos de un DNI o RUC para autocompletar el formulario de cliente. Lo
     * pueden usar quienes crean o editan clientes; cada consulta gasta una del
     * cupo mensual del proveedor, por eso no se expone a la busqueda general.
     */
    @GetMapping("/consulta-documento")
    @PreAuthorize("hasAuthority('CLIENTE_CREAR') or hasAuthority('CLIENTE_EDITAR')")
    public DatosDocumento consultarDocumento(@RequestParam String tipo, @RequestParam String numero) {
        return consultaDocumentoService.consultar(tipo, numero);
    }

    @ExceptionHandler(ConsultaDocumentoException.class)
    public ResponseEntity<ErrorRespuesta> errorConsulta(ConsultaDocumentoException e) {
        return ResponseEntity.status(e.getEstado())
                .body(ErrorRespuesta.de(e.getEstado().value(), e.getCodigo(), e.getMessage()));
    }
}
