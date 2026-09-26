package com.sbss.lexia.tareas.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public final class TareaDtos {
    private TareaDtos() {}

    public record Resumen(long pendientes, long proceso, long revision, long vencidas, long completadas, long total) {}
    public record Pagina<T>(List<T> contenido, int pagina, int tamano, long totalElementos, int totalPaginas,
                            boolean primera, boolean ultima) {}
    public record ExpedienteOpcion(Long id, String codigo, String numeroExpediente, String cliente) {}
    public record UsuarioOpcion(Long id, String nombre, String cargo) {}
    public record Catalogos(List<ExpedienteOpcion> expedientes, List<UsuarioOpcion> usuarios) {}

    public record TareaLista(
            Long id, String uuid, String titulo, Long expedienteId, String expedienteCodigo, String numeroExpediente, String cliente,
            Long responsableId, String responsable, Long supervisorId, String supervisor,
            String prioridad, String estado, String etapaFlujo, LocalDateTime fechaLimite,
            int porcentajeAvance, long subtareasTotal, long subtareasCompletadas, long adjuntos, long comentarios) {}

    public record TareaInfo(
            Long id, String uuid, String titulo, String descripcion, Long expedienteId, String expedienteCodigo, String numeroExpediente, String cliente,
            Long responsableId, String responsable, Long supervisorId, String supervisor,
            String prioridad, String estado, String etapaFlujo, LocalDateTime fechaAsignacion, LocalDateTime fechaLimite,
            LocalDateTime fechaInicioReal, LocalDateTime fechaCompletado, Integer tiempoEstimadoMinutos, Integer tiempoRealMinutos,
            int porcentajeAvance, long subtareasTotal, long subtareasCompletadas, long adjuntos, long comentarios) {}

    public record ExportacionFila(String titulo, String expedienteCodigo, String responsable, String supervisor,
                                  String prioridad, String estado, String etapaFlujo, LocalDateTime fechaLimite) {}

    public record Subtarea(Long id, String titulo, boolean completada, int orden, Long responsableId,
                           String responsable, LocalDateTime fechaCompletado) {}
    public record Comentario(Long id, String usuario, String texto, LocalDateTime fechaCreacion) {}
    public record Adjunto(String uuid, String nombre, String categoria, String estado, LocalDateTime fechaCreacion) {}
    public record Historial(Long id, String usuario, String estadoAnterior, String estadoNuevo,
                            String etapaAnterior, String etapaNueva, String observacion, LocalDateTime fechaCambio) {}
    public record TareaDetalle(TareaInfo tarea, List<Subtarea> subtareas, List<Comentario> comentarios,
                               List<Adjunto> adjuntos, List<Historial> historial) {}

    public record SubtareaRequest(@NotBlank @Size(max=200) String titulo, Long responsableId) {}
    public record GuardarRequest(
            @NotNull Long expedienteId,
            @NotBlank @Size(max=200) String titulo,
            @Size(max=10000) String descripcion,
            @NotNull Long responsableId,
            Long supervisorId,
            @NotBlank @Size(max=10) String prioridad,
            @NotNull LocalDateTime fechaLimite,
            @Min(0) Integer tiempoEstimadoMinutos,
            List<SubtareaRequest> subtareas) {}

    public record CambiarFlujoRequest(@NotBlank String etapa, @Size(max=500) String observacion) {}
    public record ComentarioRequest(@NotBlank @Size(max=10000) String texto) {}
    public record TiempoRequest(@Min(0) Integer tiempoRealMinutos) {}
}
