package com.sbss.lexia.busqueda.controller;

import com.sbss.lexia.busqueda.dto.BusquedaDtos.Respuesta;
import com.sbss.lexia.busqueda.dto.BusquedaDtos.Resultado;
import com.sbss.lexia.busqueda.service.BusquedaService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Buscador general del estudio.
 *
 * Requiere `DASHBOARD_LEER` y no un permiso por entidad, a proposito: el
 * buscador devuelve SIEMPRE menos que el modulo. Un expediente sale solo si
 * `VisibilidadExpedientes` lo deja ver, y cada consulta se acota al estudio de
 * quien pregunta. Ademas el front oculta los tipos que el usuario no puede
 * navegar. Quien tenga un permiso y necesite el listado completo lo tiene en su
 * propio modulo, con sus filtros.
 */
@RestController
@RequestMapping("/api/v1/busqueda")
@RequiredArgsConstructor
public class BusquedaController {

    private final BusquedaService service;

    @GetMapping
    @PreAuthorize("hasAuthority('DASHBOARD_LEER')")
    public Respuesta buscar(@RequestParam String texto) {
        return service.buscar(texto);
    }

    @GetMapping("/tipo")
    @PreAuthorize("hasAuthority('DASHBOARD_LEER')")
    public List<Resultado> porTipo(@RequestParam String texto, @RequestParam String tipo) {
        return service.porTipo(texto, tipo);
    }
}
