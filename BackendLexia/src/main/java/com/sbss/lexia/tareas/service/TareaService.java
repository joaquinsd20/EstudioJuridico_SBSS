package com.sbss.lexia.tareas.service;

import com.sbss.lexia.notificaciones.service.GeneradorNotificaciones;
import com.sbss.lexia.shared.exception.ParametroInvalidoException;
import com.sbss.lexia.shared.exception.RecursoNoEncontradoException;
import com.sbss.lexia.shared.security.SesionActual;
import com.sbss.lexia.tareas.dto.TareaDtos.*;
import com.sbss.lexia.tareas.repository.TareaRepositoryJdbc;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TareaService {
    private static final Set<String> ESTADOS=Set.of("PENDIENTE","EN_PROCESO","EN_REVISION","OBSERVADA","APROBADA","PRESENTADA","COMPLETADA","CANCELADA","VENCIDA");
    private static final Set<String> PRIORIDADES=Set.of("BAJA","MEDIA","ALTA","URGENTE");
    private static final List<String> FLUJO=List.of("ELABORAR","REVISAR","CORREGIR","APROBAR","FIRMAR","PRESENTAR","CARGAR_CARGO","CERRAR");
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_EXPORT = 5000;
    private final TareaRepositoryJdbc repository;
    private final GeneradorNotificaciones notificaciones;

    @Transactional
    public Pagina<TareaLista> listar(String texto,String estado,String prioridad,Long responsableId,Long expedienteId,
                                     LocalDateTime desde,LocalDateTime hasta,int page,int size){
        repository.marcarVencidas();
        Filtros f=validarFiltros(texto,estado,prioridad,responsableId,expedienteId,desde,hasta);
        int pagina=Math.max(0,page);
        int tamano=Math.min(MAX_PAGE_SIZE,Math.max(1,size));
        long total=repository.contar(f.texto(),f.estado(),f.prioridad(),f.responsableId(),f.expedienteId(),f.desde(),f.hasta());
        int totalPaginas=total==0?0:(int)Math.ceil((double)total/tamano);
        if(totalPaginas>0 && pagina>=totalPaginas) pagina=totalPaginas-1;
        List<TareaLista> contenido=repository.listar(f.texto(),f.estado(),f.prioridad(),f.responsableId(),f.expedienteId(),f.desde(),f.hasta(),pagina,tamano);
        return new Pagina<>(contenido,pagina,tamano,total,totalPaginas,pagina==0,totalPaginas==0||pagina>=totalPaginas-1);
    }

    @Transactional
    public List<ExportacionFila> exportacion(String texto,String estado,String prioridad,Long responsableId,Long expedienteId,
                                        LocalDateTime desde,LocalDateTime hasta){
        repository.marcarVencidas();
        Filtros f=validarFiltros(texto,estado,prioridad,responsableId,expedienteId,desde,hasta);
        return repository.listarExportacion(f.texto(),f.estado(),f.prioridad(),f.responsableId(),f.expedienteId(),f.desde(),f.hasta(),MAX_EXPORT);
    }

    @Transactional public Resumen resumen(){repository.marcarVencidas();return repository.resumen();}
    @Transactional(readOnly=true) public Catalogos catalogos(){return repository.catalogos();}

    @Transactional(readOnly=true)
    public TareaDetalle detalle(String uuid){
        Long id=id(uuid);
        TareaInfo t=repository.tareaInfo(uuid);
        return new TareaDetalle(t,repository.subtareas(id),repository.comentarios(id),repository.adjuntos(id),repository.historial(id));
    }

    @Transactional
    public TareaDetalle crear(GuardarRequest r){
        validar(r);
        Long id=repository.insertar(normalizar(r));
        repository.reemplazarSubtareas(id,r.subtareas());
        repository.cambiarFlujo(id,"PENDIENTE","ELABORAR","Tarea creada");
        notificaciones.tareaAsignada(id,r.responsableId(),SesionActual.usuarioId());
        return detalle(repository.uuidPorId(id));
    }

    @Transactional
    public TareaDetalle actualizar(String uuid,GuardarRequest r){
        Long id=id(uuid);validar(r);
        Long anterior=notificaciones.responsableDe(id);
        repository.actualizar(id,normalizar(r));repository.reemplazarSubtareas(id,r.subtareas());
        // Solo avisa si la tarea CAMBIO de manos: editar el titulo no es una asignacion nueva.
        if(anterior==null||!anterior.equals(r.responsableId()))notificaciones.tareaAsignada(id,r.responsableId(),SesionActual.usuarioId());
        return detalle(uuid);
    }

    @Transactional
    public TareaDetalle cambiarFlujo(String uuid,CambiarFlujoRequest r){
        Long id=id(uuid); TareaInfo actual=repository.tareaInfo(uuid); String etapa=norm(r.etapa());
        if(!FLUJO.contains(etapa)) throw new ParametroInvalidoException("Etapa de flujo inválida");
        int actualIdx=FLUJO.indexOf(actual.etapaFlujo()); int nuevoIdx=FLUJO.indexOf(etapa);
        boolean observando="CORREGIR".equals(etapa)&&"REVISAR".equals(actual.etapaFlujo());
        if(!observando && nuevoIdx!=actualIdx+1 && nuevoIdx!=actualIdx) throw new ParametroInvalidoException("El flujo debe avanzar en orden: Elaborar → Revisar → Corregir → Aprobar → Firmar → Presentar → Cargar cargo → Cerrar");
        if(("APROBAR".equals(etapa)||"FIRMAR".equals(etapa))&&!SesionActual.tieneAutoridad("TAREA_APROBAR")) throw new ParametroInvalidoException("Se requiere permiso de aprobación para esta etapa");
        if("APROBAR".equals(etapa)&&actual.supervisorId()==null) throw new ParametroInvalidoException("La tarea debe tener un supervisor antes de aprobarse");
        String estado=switch(etapa){case "ELABORAR"->"EN_PROCESO";case "REVISAR"->"EN_REVISION";case "CORREGIR"->"OBSERVADA";case "APROBAR","FIRMAR"->"APROBADA";case "PRESENTAR","CARGAR_CARGO"->"PRESENTADA";case "CERRAR"->"COMPLETADA";default->actual.estado();};
        repository.cambiarFlujo(id,estado,etapa,r.observacion()); return detalle(uuid);
    }

    @Transactional public TareaDetalle cambiarSubtarea(String uuid,Long sid){Long id=id(uuid);if(!repository.toggleSubtarea(id,sid))throw new RecursoNoEncontradoException("Subtarea no encontrada");return detalle(uuid);}
    @Transactional public TareaDetalle comentar(String uuid,ComentarioRequest r){Long id=id(uuid);repository.comentar(id,r.texto());return detalle(uuid);}
    @Transactional public TareaDetalle registrarTiempo(String uuid,TiempoRequest r){Long id=id(uuid);repository.registrarTiempo(id,r.tiempoRealMinutos());return detalle(uuid);}

    private Filtros validarFiltros(String texto,String estado,String prioridad,Long responsableId,Long expedienteId,LocalDateTime desde,LocalDateTime hasta){
        String e=normOpt(estado),p=normOpt(prioridad);
        if(e!=null&&!ESTADOS.contains(e)) throw new ParametroInvalidoException("Estado de tarea inválido");
        if(p!=null&&!PRIORIDADES.contains(p)) throw new ParametroInvalidoException("Prioridad inválida");
        if(desde!=null&&hasta!=null&&hasta.isBefore(desde)) throw new ParametroInvalidoException("La fecha hasta no puede ser anterior a la fecha desde");
        String q=texto==null||texto.isBlank()?null:texto.trim();
        return new Filtros(q,e,p,responsableId,expedienteId,desde,hasta);
    }

    private void validar(GuardarRequest r){if(!repository.expedienteValido(r.expedienteId()))throw new ParametroInvalidoException("Expediente no válido");if(!repository.usuarioValido(r.responsableId()))throw new ParametroInvalidoException("Responsable no válido");if(!repository.usuarioValido(r.supervisorId()))throw new ParametroInvalidoException("Supervisor no válido");String p=norm(r.prioridad());if(!PRIORIDADES.contains(p))throw new ParametroInvalidoException("Prioridad inválida");if(r.fechaLimite()==null)throw new ParametroInvalidoException("La fecha límite es obligatoria");if(r.subtareas()!=null)for(SubtareaRequest s:r.subtareas())if(s!=null&&!repository.usuarioValido(s.responsableId()))throw new ParametroInvalidoException("Responsable de subtarea no válido");}
    private GuardarRequest normalizar(GuardarRequest r){return new GuardarRequest(r.expedienteId(),r.titulo(),r.descripcion(),r.responsableId(),r.supervisorId(),norm(r.prioridad()),r.fechaLimite(),r.tiempoEstimadoMinutos(),r.subtareas());}
    private Long id(String uuid){Long id=repository.idPorUuid(uuid);if(id==null)throw new RecursoNoEncontradoException("Tarea no encontrada");return id;}
    private String norm(String s){return s==null?null:s.trim().toUpperCase(Locale.ROOT).replace(' ','_');}
    private String normOpt(String s){return s==null||s.isBlank()?null:norm(s);}

    private record Filtros(String texto,String estado,String prioridad,Long responsableId,Long expedienteId,LocalDateTime desde,LocalDateTime hasta) {}
}
