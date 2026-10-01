package com.sbss.lexia.biblioteca.service;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.sbss.lexia.biblioteca.dto.BibliotecaEstadisticasResponse;
import com.sbss.lexia.biblioteca.dto.RecursoCrearRequest;
import com.sbss.lexia.biblioteca.dto.RecursoFiltroRequest;
import com.sbss.lexia.biblioteca.entity.RecursoJuridico;
import com.sbss.lexia.biblioteca.repository.RecursoJuridicoRepository;
import com.sbss.lexia.documental.service.AlmacenamientoDocumentoService.ArchivoGuardado;
import com.sbss.lexia.shared.security.SesionActual;

import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class BibliotecaJuridicaService {

    private final RecursoJuridicoRepository repository;
    private final AlmacenamientoBibliotecaService almacenamientoService;

    // =========================================================
    // ESTADÍSTICAS
    // =========================================================
    public BibliotecaEstadisticasResponse obtenerEstadisticas() {
        Integer estudioId = SesionActual.estudioId();
        return BibliotecaEstadisticasResponse.builder()
                .recursosTotales(repository.countByEstudioId(estudioId))
                .recursosActivos(repository.countByEstudioIdAndEstado(estudioId, "ACTIVO"))
                .conArchivo(repository.countConArchivoDelEstudio(estudioId))
                .totalCategorias(repository.countCategoriasUnicasDelEstudio(estudioId))
                .build();
    }

    // =========================================================
    // LISTAR RECURSOS
    // =========================================================
    public Page<RecursoJuridico> listar(
            RecursoFiltroRequest filtro,
            Pageable pageable) {
        Integer estudioId = SesionActual.estudioId();
        Specification<RecursoJuridico> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // Obligatorio y primero: la frontera entre estudios. Sin esto, los
            // demas filtros consultarian la biblioteca de todos los estudios.
            predicates.add(cb.equal(root.get("estudioId"), estudioId));
            if (filtro.getBusqueda() != null && !filtro.getBusqueda().isBlank()) {
                String likePattern = "%" + filtro.getBusqueda().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("titulo")), likePattern),
                        cb.like(cb.lower(root.get("subtituloDescripcion")), likePattern),
                        cb.like(cb.lower(root.get("expedienteCodigo")), likePattern)
                ));
            }
            if (filtro.getTipo() != null && !filtro.getTipo().isBlank()) {
                predicates.add(cb.equal(root.get("tipo"), filtro.getTipo()));
            }
            if (filtro.getCategoria() != null && !filtro.getCategoria().isBlank()) {
                predicates.add(cb.equal(root.get("categoria"), filtro.getCategoria()));
            }
            if (filtro.getEstado() != null && !filtro.getEstado().isBlank()) {
                predicates.add(cb.equal(root.get("estado"), filtro.getEstado()));
            }
            if (filtro.getConArchivo() != null) {
                if (filtro.getConArchivo()) {
                    predicates.add(cb.isNotNull(root.get("nombreArchivo")));
                } else {
                    predicates.add(cb.isNull(root.get("nombreArchivo")));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return repository.findAll(spec, pageable);
    }

    // =========================================================
    // OBTENER POR ID
    // =========================================================
    public RecursoJuridico obtenerPorId(Long id) {
        // findByIdAndEstudioId y no findById: un recurso de otro estudio debe
        // verse igual que uno inexistente.
        return repository.findByIdAndEstudioId(id, SesionActual.estudioId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Recurso jurídico no encontrado con ID: " + id
                ));
    }

    // =========================================================
    // CREAR RECURSO
    // =========================================================
    public RecursoJuridico crear(
            RecursoCrearRequest request,
            MultipartFile archivo) {
        RecursoJuridico recurso = RecursoJuridico.builder()
                .estudioId(SesionActual.estudioId())
                .titulo(request.getTitulo())
                .subtituloDescripcion(request.getSubtituloDescripcion())
                .tipo(request.getTipo())
                .categoria(request.getCategoria())
                .anio(request.getAnio())
                .expedienteCodigo(request.getExpedienteCodigo())
                .estado(request.getEstado() != null ? request.getEstado() : "ACTIVO")
                .build();

        recurso = repository.save(recurso);

        if (archivo != null && !archivo.isEmpty()) {
            // Toma el código enviado en la Request o usa "BIBLIOTECA" por defecto si viene nulo
            String codigoExpediente = (request.getExpedienteCodigo() != null && !request.getExpedienteCodigo().isBlank()) 
                    ? request.getExpedienteCodigo() 
                    : "BIBLIOTECA";

            ArchivoGuardado guardado = almacenamientoService.guardar(
                    archivo,
                    codigoExpediente, // <-- Ahora coincide con el String expedienteCodigo
                    recurso.getId(),
                    1
            );
            recurso.setNombreArchivo(guardado.nombreOriginal());
            recurso.setRutaArchivo(guardado.rutaRelativa());
            recurso.setTamanioBytes(guardado.tamanoBytes());
            recurso.setExtension(guardado.extension());

            recurso = repository.save(recurso);
        }
        return recurso;
    }

    // =========================================================
    // ACTUALIZAR RECURSO
    // =========================================================
    public RecursoJuridico actualizar(
            Long id,
            RecursoCrearRequest request,
            MultipartFile archivo) {
        RecursoJuridico recurso = obtenerPorId(id);
        recurso.setTitulo(request.getTitulo());
        recurso.setSubtituloDescripcion(request.getSubtituloDescripcion());
        recurso.setTipo(request.getTipo());
        recurso.setCategoria(request.getCategoria());
        recurso.setAnio(request.getAnio());
        recurso.setExpedienteCodigo(request.getExpedienteCodigo());

        if (request.getEstado() != null) {
            recurso.setEstado(request.getEstado());
        }

        if (archivo != null && !archivo.isEmpty()) {
            if (recurso.getRutaArchivo() != null && !recurso.getRutaArchivo().isBlank()) {
                try {
                    almacenamientoService.eliminar(recurso.getRutaArchivo());
                } catch (Exception e) {
                    log.warn("No se pudo eliminar el archivo anterior en actualización: {}", e.getMessage());
                }
            }

            String codigoExpediente = (request.getExpedienteCodigo() != null && !request.getExpedienteCodigo().isBlank()) 
                    ? request.getExpedienteCodigo() 
                    : "BIBLIOTECA";

            ArchivoGuardado guardado = almacenamientoService.guardar(
                    archivo,
                    codigoExpediente, // <-- Cambio a String
                    recurso.getId(),
                    1
            );

            recurso.setNombreArchivo(guardado.nombreOriginal());
            recurso.setRutaArchivo(guardado.rutaRelativa());
            recurso.setTamanioBytes(guardado.tamanoBytes());
            recurso.setExtension(guardado.extension());
        }
        return repository.save(recurso);
    }

    // =========================================================
    // OBTENER RECURSO FÍSICO
    // =========================================================
    public Resource obtenerArchivoResource(Long id) {
        RecursoJuridico recurso = obtenerPorId(id);

        if (recurso.getRutaArchivo() == null || recurso.getRutaArchivo().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "El recurso no tiene archivo adjunto"
            );
        }

        Path rutaFisica = almacenamientoService.resolver(recurso.getRutaArchivo());

        try {
            Resource resource = new UrlResource(rutaFisica.toUri());

            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se pudo leer el archivo físico en la ruta: " + rutaFisica.toAbsolutePath()
                );
            }
        } catch (MalformedURLException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error al generar la ruta del archivo",
                    e
            );
        }
    }

    // =========================================================
    // OBTENER RUTA FÍSICA
    // =========================================================
    public Path obtenerRutaArchivo(Long id) {
        RecursoJuridico recurso = obtenerPorId(id);
        if (recurso.getRutaArchivo() == null || recurso.getRutaArchivo().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "El recurso no tiene archivo adjunto"
            );
        }
        return almacenamientoService.resolver(recurso.getRutaArchivo());
    }

    // =========================================================
    // DESCARGAR
    // =========================================================
    public Resource descargarArchivo(Long id) {
        return obtenerArchivoResource(id);
    }

    // =========================================================
    // ELIMINAR (BLINDADO)
    // =========================================================
    @Transactional
    public void eliminar(Long id) {
        RecursoJuridico recurso = obtenerPorId(id);

        // 1. Intento seguro de borrado físico
        if (recurso.getRutaArchivo() != null && !recurso.getRutaArchivo().isBlank()) {
            try {
                almacenamientoService.eliminar(recurso.getRutaArchivo());
            } catch (Throwable e) {
                log.error("Excepción ignorada al eliminar archivo en disco para el recurso {}: {}", id, e.getMessage());
            }
        }

        // 2. Borrado garantizado en Base de Datos
        try {
            repository.delete(recurso);
            repository.flush();
        } catch (Exception e) {
            log.error("Error al eliminar el registro en BD: {}", e.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "No se puede eliminar el recurso porque está referenciado en otras tablas de la base de datos.",
                    e
            );
        }
    }
}