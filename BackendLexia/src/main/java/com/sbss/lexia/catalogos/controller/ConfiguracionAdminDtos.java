package com.sbss.lexia.catalogos.controller;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Contratos públicos de configuración del estudio. No exponen credenciales ni secretos. */
public final class ConfiguracionAdminDtos {

    private ConfiguracionAdminDtos() {
    }

    public record EstudioResponse(
            Integer id,
            String razonSocial,
            String nombreComercial,
            String ruc,
            String direccion,
            String telefono,
            String email,
            String logoUrl,
            String zonaHoraria,
            String monedaBase) {
    }

    public record ActualizarEstudioRequest(
            @NotBlank @Size(max = 200) String razonSocial,
            @Size(max = 150) String nombreComercial,
            @NotBlank @Pattern(regexp = "^[0-9]{11}$", message = "El RUC debe contener 11 dígitos") String ruc,
            @Size(max = 250) String direccion,
            @Size(max = 30) String telefono,
            @Email @Size(max = 150) String email,
            @NotBlank @Size(max = 50) String zonaHoraria,
            @NotBlank @Pattern(regexp = "^(PEN|USD|EUR)$", message = "La moneda debe ser PEN, USD o EUR") String monedaBase) {
    }

    public record ParametrosResponse(
            String dominioCorreoInterno,
            int intentosLoginMaximos,
            int plazoColchonInternoDias,
            List<Integer> alertasDiasPrevios) {
    }

    public record ActualizarParametrosRequest(
            @NotBlank
            @Pattern(regexp = "(?i)^(?=.{1,253}$)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$",
                    message = "Introduce un dominio válido, por ejemplo estudio.pe")
            @Size(max = 253) String dominioCorreoInterno,
            @NotNull @Min(3) @Max(10) Integer intentosLoginMaximos,
            @NotNull @Min(0) @Max(30) Integer plazoColchonInternoDias,
            @NotEmpty List<@NotNull @Min(0) @Max(365) Integer> alertasDiasPrevios) {
    }
}
