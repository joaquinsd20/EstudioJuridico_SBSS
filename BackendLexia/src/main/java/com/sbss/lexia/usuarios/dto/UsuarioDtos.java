package com.sbss.lexia.usuarios.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Contrato del modulo de usuarios. Records, y siempre `uuidPublico`: el id
 * BIGINT no sale del backend.
 */
public final class UsuarioDtos {

    private UsuarioDtos() {
    }

    /** Fila del listado. */
    public record UsuarioLista(
            String uuidPublico,
            String codigoUsuario,
            String nombreCompleto,
            String email,
            String cargo,
            String estado,
            boolean activo,
            boolean debeCambiarPassword,
            LocalDateTime ultimoAcceso,
            List<String> roles) {
    }

    /**
     * Ficha completa. `cargaTrabajo` dice de que es responsable esta persona:
     * es lo que hay que reasignar antes de darla de baja.
     */
    public record UsuarioDetalle(
            String uuidPublico,
            String codigoUsuario,
            String nombres,
            String apellidos,
            String email,
            String cargo,
            String telefono,
            String tipoDocumento,
            String numeroDocumento,
            String numeroColegiatura,
            String estado,
            boolean activo,
            boolean debeCambiarPassword,
            LocalDateTime ultimoAcceso,
            LocalDateTime fechaCreacion,
            List<String> roles,
            CargaTrabajo cargaTrabajo) {
    }

    /**
     * Lo que quedaria a nombre de alguien al desactivarlo.
     *
     * Las cinco primeras BLOQUEAN la baja: son compromisos vivos. Dejarlos a
     * nombre de una cuenta inactiva es exactamente como se pierde un plazo, o
     * como un cliente se queda con un abogado que ya no existe. Eventos y
     * versiones de documento son historia y solo se informan.
     */
    public record CargaTrabajo(
            int expedientes,
            int plazosAbiertos,
            int tareasAbiertas,
            int clientes,
            int equiposApoyo,
            int eventosAgenda,
            int versionesDocumento) {

        public boolean bloqueaBaja() {
            return expedientes > 0 || plazosAbiertos > 0 || tareasAbiertas > 0
                    || clientes > 0 || equiposApoyo > 0;
        }
    }

    // ---- Reasignacion de trabajo ------------------------------------------------

    public record ExpedienteCarga(String uuidPublico, String codigo, String cliente, boolean confidencial) {
    }

    public record PlazoCarga(String descripcion, java.time.LocalDate vencimiento, String estado,
                             String expedienteCodigo) {
    }

    public record TareaCarga(String titulo, String estado, LocalDateTime fechaLimite, String expedienteCodigo) {
    }

    public record ClienteCarga(String uuidPublico, String nombre) {
    }

    public record EquipoCarga(String expedienteCodigo, String rol) {
    }

    /**
     * Quien puede recibir el trabajo. `esAbogado` decide si puede tomar
     * expedientes y clientes: mismo criterio que el selector de abogados del
     * modulo de clientes (Administrador, Socio, Abogado, Abogado auxiliar).
     */
    public record CandidatoReasignacion(String uuidPublico, String nombreCompleto, String cargo, boolean esAbogado) {
    }

    /** Lo concreto que hay que mover, y a quien se le puede dar. */
    public record CargaDetallada(
            List<ExpedienteCarga> expedientes,
            List<PlazoCarga> plazos,
            List<TareaCarga> tareas,
            List<ClienteCarga> clientes,
            List<EquipoCarga> equipos,
            List<CandidatoReasignacion> candidatos) {
    }

    /**
     * Un destino por categoria. Los plazos NO tienen destino propio: siguen al
     * nuevo responsable de su expediente. El apoyo en equipos no se traspasa:
     * se retira. Un destino solo es obligatorio si hay algo de esa categoria.
     */
    public record ReasignarRequest(String expedientesA, String tareasA, String clientesA) {
    }

    public record ResultadoReasignacion(
            int expedientes, int plazos, int alertas, int tareas, int clientes, int equiposRetirados) {
    }

    /** Un rol asignable, para el selector del formulario. */
    public record RolOpcion(String codigo, String nombre, int nivelAcceso, boolean esPortalCliente) {
    }

    /** Alta y edicion. En la edicion, el email no se puede cambiar (ver el servicio). */
    public record GuardarUsuarioRequest(
            @NotBlank(message = "Los nombres son obligatorios")
            @Size(max = 100)
            String nombres,

            @NotBlank(message = "Los apellidos son obligatorios")
            @Size(max = 100)
            String apellidos,

            @NotBlank(message = "El correo es obligatorio")
            @Email(message = "El correo no tiene un formato valido")
            @Size(max = 150)
            String email,

            @Size(max = 100)
            String cargo,

            @Size(max = 20)
            String telefono,

            @Size(max = 20)
            String tipoDocumento,

            @Size(max = 20)
            String numeroDocumento,

            @Size(max = 30)
            String numeroColegiatura,

            @NotEmpty(message = "Asigna al menos un rol")
            List<String> roles) {
    }

    /**
     * Contrasena temporal recien generada. Se devuelve UNA sola vez, en la
     * respuesta del alta o del restablecimiento: no se guarda en claro ni se
     * escribe en el log. Si se pierde, se restablece otra vez.
     */
    public record CredencialTemporal(
            String uuidPublico,
            String email,
            String passwordTemporal,
            String mensaje) {
    }
}
