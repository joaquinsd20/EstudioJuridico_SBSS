package com.sbss.lexia.clientes.controller;

import com.sbss.lexia.clientes.dto.ClienteDetalleDto;
import com.sbss.lexia.clientes.dto.ClienteFiltro;
import com.sbss.lexia.clientes.dto.ClienteResumenDto;
import com.sbss.lexia.clientes.dto.ClientesResumenDto;
import com.sbss.lexia.clientes.service.ClienteService;
import com.sbss.lexia.shared.dto.PaginaDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lectura de clientes. El alta (POST) y la verificacion de conflicto de
 * intereses (RF-04) llegan en un modulo aparte: no dependen de este y no
 * conviene mezclarlos en el mismo cambio.
 */
@RestController
@RequestMapping("/api/v1/clientes")
@RequiredArgsConstructor
public class ClienteController {

    private final ClienteService clienteService;

    @GetMapping
    @PreAuthorize("hasAuthority('CLIENTE_LEER')")
    public ResponseEntity<PaginaDto<ClienteResumenDto>> listar(
            @RequestParam(required = false) String documento,
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String tipoPersona,
            @RequestParam(required = false) String estado,
            @PageableDefault(size = 20, sort = "nombreBusqueda") Pageable pageable) {

        ClienteFiltro filtro = new ClienteFiltro(documento, nombre, tipoPersona, estado);
        return ResponseEntity.ok(clienteService.listar(filtro, pageable));
    }

    /** KPIs de la cabecera. Ruta estatica antes de la de path variable a
     * proposito para que se lea como lo que es: Spring MVC ya resuelve la mas
     * especifica primero sin importar el orden de declaracion, pero declarada
     * asi no da lugar a dudas al leerla. */
    @GetMapping("/resumen")
    @PreAuthorize("hasAuthority('CLIENTE_LEER')")
    public ResponseEntity<ClientesResumenDto> resumen() {
        return ResponseEntity.ok(clienteService.resumen());
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('CLIENTE_LEER')")
    public ResponseEntity<ClienteDetalleDto> obtener(@PathVariable String uuid) {
        return ResponseEntity.ok(clienteService.obtener(uuid));
    }
}
