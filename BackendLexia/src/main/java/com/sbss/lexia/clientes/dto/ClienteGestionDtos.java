package com.sbss.lexia.clientes.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Contratos de escritura, ficha ampliada y conflicto de intereses del módulo Clientes. */
public final class ClienteGestionDtos {

    private ClienteGestionDtos() {
    }

    public record ContactoInput(
            @NotBlank @Size(max = 200) String nombreCompleto,
            @Size(max = 20) String tipoDocumento,
            @Size(max = 20) String numeroDocumento,
            @Size(max = 100) String cargo,
            @Email @Size(max = 150) String email,
            @Size(max = 30) String telefono,
            boolean representanteLegal,
            boolean puedeRecibirDocumentos,
            @Size(max = 60) String numeroPoder) {
    }

    public record VinculoInput(
            @NotBlank String tipoPersona,
            @NotBlank @Size(max = 200) String nombre,
            @Size(max = 20) String tipoDocumento,
            @Size(max = 20) String numeroDocumento,
            @Size(max = 80) String relacion,
            @Size(max = 2000) String observaciones) {
    }

    public record GuardarClienteRequest(
            @NotBlank String tipoPersona,
            @NotBlank String tipoDocumento,
            @NotBlank @Size(max = 20) String numeroDocumento,
            @Size(max = 100) String nombres,
            @Size(max = 80) String apellidoPaterno,
            @Size(max = 80) String apellidoMaterno,
            @Size(max = 200) String razonSocial,
            @Size(max = 200) String nombreComercial,
            @Email @Size(max = 150) String email,
            @Size(max = 30) String telefonoPrincipal,
            @Size(max = 30) String telefonoSecundario,
            @Size(max = 250) String direccion,
            @Size(max = 80) String distrito,
            @Size(max = 80) String provincia,
            @Size(max = 80) String departamento,
            @NotBlank String estado,
            @Size(max = 50) String origenCaptacion,
            String situacionFinanciera,
            String abogadoResponsableUuid,
            @Size(max = 5000) String observaciones,
            @Valid List<ContactoInput> contactos,
            @Valid List<VinculoInput> vinculados) {
    }

    public record Contacto(
            String nombreCompleto,
            String tipoDocumento,
            String numeroDocumento,
            String cargo,
            String email,
            String telefono,
            boolean representanteLegal,
            boolean puedeRecibirDocumentos,
            String numeroPoder) {
    }

    public record Vinculo(
            String tipoPersona,
            String nombre,
            String tipoDocumento,
            String numeroDocumento,
            String relacion,
            String observaciones) {
    }

    public record ExpedienteVinculado(
            String uuid,
            String codigoInterno,
            String numeroExpediente,
            String materia,
            String estado,
            String estadoColor,
            String responsable,
            LocalDate fechaInicio,
            LocalDateTime ultimaActuacion) {
    }

    public record Comunicacion(
            String uuid,
            String tipo,
            String asunto,
            String detalle,
            String canal,
            LocalDateTime fecha,
            String registradoPor) {
    }

    public record RegistrarComunicacionRequest(
            @NotBlank String tipo,
            @NotBlank @Size(max = 200) String asunto,
            @Size(max = 5000) String detalle,
            @Size(max = 40) String canal,
            LocalDateTime fecha) {
    }

    public record FichaCliente(
            ClienteDetalleDto cliente,
            List<Contacto> contactos,
            List<Vinculo> vinculados,
            List<ExpedienteVinculado> expedientes,
            List<Comunicacion> comunicaciones) {
    }

    public record AbogadoOpcion(String uuid, String nombre, String cargo) {
    }

    public record Catalogos(List<AbogadoOpcion> abogados) {
    }

    public record RelacionConflicto(
            @Size(max = 200) String nombre,
            @Size(max = 20) String numeroDocumento) {
    }

    public record VerificarConflictoRequest(
            @Size(max = 200) String nombre,
            @Size(max = 20) String numeroDocumento,
            @Valid List<RelacionConflicto> relacionados,
            String excluirClienteUuid) {
    }

    public record CoincidenciaConflicto(
            String origen,
            String referenciaUuid,
            String nombre,
            String documento,
            String relacion,
            String tipoCoincidencia,
            String terminoBuscado) {
    }

    public record VerificarConflictoResponse(
            boolean posibleConflicto,
            int totalCoincidencias,
            List<CoincidenciaConflicto> coincidencias) {
    }

    /** Datos de un DNI (RENIEC) o RUC (SUNAT) para autocompletar la ficha. */
    public record DatosDocumento(
            String tipoDocumento,
            String numeroDocumento,
            String nombres,
            String apellidoPaterno,
            String apellidoMaterno,
            String razonSocial,
            String direccion,
            String distrito,
            String provincia,
            String departamento,
            String estadoContribuyente,
            String condicionContribuyente) {
    }
}
