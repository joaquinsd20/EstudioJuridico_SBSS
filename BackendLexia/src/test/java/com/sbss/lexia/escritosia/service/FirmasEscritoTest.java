package com.sbss.lexia.escritosia.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class FirmasEscritoTest {

    private static final String FECHA = "Trujillo, [PENDIENTE DE COMPLETAR/VERIFICAR]\n";
    private static final List<String> ESPERADO = List.of(
            "BANCO DEL PERÚ S.A.A.\nRepresentante legal: [PENDIENTE DE COMPLETAR/VERIFICAR]",
            "ABOGADO PATROCINANTE\n[PENDIENTE DE COMPLETAR/VERIFICAR]\nColegiatura N.º: [PENDIENTE DE COMPLETAR/VERIFICAR]");

    private List<String> firmas(String texto) {
        String[] lineas = texto.split("\n");
        int inicio = FirmasEscrito.inicioZona(lineas);
        assertTrue(inicio >= 0);
        return FirmasEscrito.bloques(lineas, inicio);
    }

    @Test
    void formatoOrdenado_queda_igual() {
        assertEquals(ESPERADO, firmas(FECHA + String.join("\n",
                "BANCO DEL PERÚ S.A.A.", "Representante legal: [PENDIENTE DE COMPLETAR/VERIFICAR]",
                "ABOGADO PATROCINANTE", "[PENDIENTE DE COMPLETAR/VERIFICAR]",
                "Colegiatura N.º: [PENDIENTE DE COMPLETAR/VERIFICAR]")));
    }

    @Test
    void formatoDesordenado_se_normaliza_al_mismo_resultado() {
        assertEquals(ESPERADO, firmas(FECHA
                + "_ [Firma del Representante Legal] Banco del Perú S.A.A.\n"
                + "_ [Firma del Abogado] Abogado: [PENDIENTE DE COMPLETAR/VERIFICAR] "
                + "Colegiatura N.º: [PENDIENTE DE COMPLETAR/VERIFICAR]"));
    }

    @Test
    void normalizar_es_idempotente() {
        for (String f : ESPERADO) assertEquals(f, FirmasEscrito.normalizar(f));
    }

    @Test
    void cuerpo_tras_la_fecha_no_se_toca() {
        assertEquals(-1, FirmasEscrito.inicioZona(new String[] {"Trujillo, 1 de mayo", "OTROSÍ DIGO: que adjunto..."}));
    }
}
