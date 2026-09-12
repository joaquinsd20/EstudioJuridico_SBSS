package com.sbss.lexia.catalogos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Parametro clave/valor del estudio. NO se usa para catalogos (estados, tipos):
 * esos tienen sus propias tablas con color e integridad referencial. Esta es
 * para ajustes sueltos como `intentos_login_maximos` o `sesion_inactividad_minutos`.
 *
 * `valor` siempre se guarda como texto; `tipoDato` le dice a quien lo lee como
 * interpretarlo. Ver ConfiguracionService para la lectura tipada.
 */
@Entity
@Table(name = "configuracion")
@Getter
@Setter
public class Configuracion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Short id;

    @Column(name = "estudio_id", nullable = false)
    private Integer estudioId;

    @Column(nullable = false, length = 100)
    private String clave;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String valor;

    @Column(name = "tipo_dato", nullable = false, length = 15)
    private String tipoDato = "TEXTO";

    @Column(length = 250)
    private String descripcion;

    @Column(name = "editable_por_usuario", nullable = false)
    private Boolean editablePorUsuario = true;
}
