package com.sbss.lexia.escritosia.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sbss.lexia.escritosia.dto.EscritosIaDtos.EscritoGenerado;
import com.sbss.lexia.escritosia.dto.EscritosIaDtos.GenerarRequest;
import com.sbss.lexia.escritosia.dto.EscritosIaDtos.TipoEscrito;
import com.sbss.lexia.escritosia.service.EscritosIaService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/escritos-ia")
@RequiredArgsConstructor
public class EscritosIaController {

    private final EscritosIaService service;

    @GetMapping("/tipos")
    @PreAuthorize("hasAuthority('DOCUMENTO_CREAR')")
    public List<TipoEscrito> tipos() {
        return service.tipos();
    }

    @PostMapping("/generar")
    @PreAuthorize("hasAuthority('DOCUMENTO_CREAR')")
    public ResponseEntity<EscritoGenerado> generar(@Valid @RequestBody GenerarRequest request) {
        return ResponseEntity.ok(service.generar(request));
    }
}