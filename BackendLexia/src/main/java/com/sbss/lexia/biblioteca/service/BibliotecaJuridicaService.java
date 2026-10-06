package com.sbss.lexia.biblioteca.service;

import java.nio.file.Path;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.sbss.lexia.biblioteca.dto.BibliotecaEstadisticasResponse;
import com.sbss.lexia.biblioteca.dto.RecursoCrearRequest;
import com.sbss.lexia.biblioteca.dto.RecursoDetalleResponse;
import com.sbss.lexia.biblioteca.dto.RecursoFiltroRequest;
import com.sbss.lexia.biblioteca.dto.RecursoListaResponse;
import com.sbss.lexia.biblioteca.entity.RecursoJuridico;
import com.sbss.lexia.biblioteca.repository.RecursoJuridicoRepository;
import com.sbss.lexia.biblioteca.repository.RecursoJuridicoRepository.EstadisticasProjection;
import com.sbss.lexia.documental.service.AlmacenamientoDocumentoService.ArchivoGuardado;
import com.sbss.lexia.shared.dto.PaginaDto;
import com.sbss.lexia.shared.security.SesionActual;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BibliotecaJuridicaService {

    private static final Set<Integer> TAMANOS_PERMITIDOS = Set.of(10, 20, 40, 100);

    private final RecursoJuridicoRepository repository;
    private final AlmacenamientoBibliotecaService almacenamientoService;
    private final ExportacionBibliotecaService exportacionBibliotecaService;
    private final VistaPreviaBibliotecaService vistaPreviaService;

    // =========================================================
    // ESTADÍSTICAS: UNA SOLA CONSULTA AGREGADA
    // =========================================================

    @Transactional(readOnly = true)
    public BibliotecaEstadisticasResponse obtenerEstadisticas() {
        EstadisticasProjection p = repository.estadisticas(SesionActual.estudioId());

        return BibliotecaEstadisticasResponse.builder()
                .recursosTotales(valor(p.getRecursosTotales()))
                .recursosActivos(valor(p.getRecursosActivos()))
                .conArchivo(valor(p.getConArchivo()))
                .totalCategorias(valor(p.getTotalCategorias()))
                .build();
    }

    // =========================================================
    // LISTADO PAGINADO + FILTROS EN BD + DTO LIGERO
    // =========================================================

    @Transactional(readOnly = true)
    public PaginaDto<RecursoListaResponse> listar(
            RecursoFiltroRequest filtro,
            Pageable pageable) {

        RecursoFiltroRequest f = filtro == null ? new RecursoFiltroRequest() : filtro;
        Pageable paginaSegura = paginaSegura(pageable);

        Page<RecursoListaResponse> pagina = repository.buscar(
                SesionActual.estudioId(),
                texto(f.getBusqueda()),
                codigo(f.getTipo()),
                codigo(f.getCategoria()),
                codigo(f.getEstado()),
                f.getConArchivo(),
                f.getAnio(),
                paginaSegura);

        return PaginaDto.desde(pagina);
    }

    // =========================================================
    // DETALLE BAJO DEMANDA
    // =========================================================

    @Transactional(readOnly = true)
    public RecursoDetalleResponse obtener(String uuid) {
        return aDetalle(buscar(uuid));
    }

    // =========================================================
    // CREAR
    // =========================================================

    @Transactional
    public RecursoDetalleResponse crear(
            RecursoCrearRequest request,
            MultipartFile archivo) {

        validarRequest(request);
        validarArchivoNuevo(archivo);

        RecursoJuridico recurso = RecursoJuridico.builder()
                .estudioId(SesionActual.estudioId())
                .titulo(request.getTitulo().trim())
                .subtituloDescripcion(texto(request.getSubtituloDescripcion()))
                .tipo(codigo(request.getTipo()))
                .categoria(codigo(request.getCategoria()))
                .anio(request.getAnio())
                .expedienteCodigo(texto(request.getExpedienteCodigo()))
                .estado(codigo(request.getEstado()) == null ? "ACTIVO" : codigo(request.getEstado()))
                .build();

        recurso = repository.saveAndFlush(recurso);

        // El archivo es obligatorio al crear. Se llama siempre al almacenamiento
        // para que también se rechacen archivos vacíos (MultipartFile.isEmpty()).
        // Antes, un PHP de 0 bytes podía saltarse la validación y crear el registro.
        aplicarArchivo(recurso, archivo, request.getExpedienteCodigo());
        recurso = repository.saveAndFlush(recurso);

        return aDetalle(recurso);
    }

    // =========================================================
    // ACTUALIZAR
    // =========================================================

    @Transactional
    public RecursoDetalleResponse actualizar(
            String uuid,
            RecursoCrearRequest request,
            MultipartFile archivo) {

        validarRequest(request);

        RecursoJuridico recurso = buscar(uuid);
        recurso.setTitulo(request.getTitulo().trim());
        recurso.setSubtituloDescripcion(texto(request.getSubtituloDescripcion()));
        recurso.setTipo(codigo(request.getTipo()));
        recurso.setCategoria(codigo(request.getCategoria()));
        recurso.setAnio(request.getAnio());
        recurso.setExpedienteCodigo(texto(request.getExpedienteCodigo()));

        if (codigo(request.getEstado()) != null) {
            recurso.setEstado(codigo(request.getEstado()));
        }

        if (archivo != null) {
            String rutaAnterior = recurso.getRutaArchivo();
            aplicarArchivo(recurso, archivo, request.getExpedienteCodigo());

            // El nuevo archivo se guarda primero. Solo después intentamos borrar
            // el anterior, evitando dejar el recurso sin archivo ante un fallo.
            if (rutaAnterior != null && !rutaAnterior.isBlank()
                    && !rutaAnterior.equals(recurso.getRutaArchivo())) {
                try {
                    almacenamientoService.eliminar(rutaAnterior);
                } catch (Exception ignored) {
                    // El registro ya apunta al archivo nuevo. Un residuo físico
                    // puede limpiarse después sin romper la biblioteca.
                }
            }
        }

        return aDetalle(repository.saveAndFlush(recurso));
    }

    // =========================================================
    // ARCHIVOS
    // =========================================================

    /**
     * Devuelve siempre un PDF apto para vista previa.
     *
     * Los nuevos DOC/DOCX se normalizan a PDF al momento de subirlos, igual
     * que en Panel Documentos. Si existiera un Word antiguo, el servicio de
     * vista previa lo convierte con el mismo ExportacionDocumentoService.
     */
    @Transactional(readOnly = true)
    public Path obtenerRutaVistaPreviaPdf(String uuid) {
        RecursoJuridico recurso = buscar(uuid);

        if (recurso.getRutaArchivo() == null || recurso.getRutaArchivo().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "El recurso no tiene archivo adjunto");
        }

        Path original = almacenamientoService.resolver(recurso.getRutaArchivo());

        // Conservamos la validación de seguridad antes de convertir o servir.
        almacenamientoService.validarArchivoExistente(
                original,
                recurso.getNombreArchivo(),
                recurso.getExtension());

        return vistaPreviaService.obtenerPdf(
                original,
                recurso.getExtension(),
                recurso.getNombreArchivo());
    }

    @Transactional(readOnly = true)
    public Path obtenerRutaArchivo(String uuid) {
        RecursoJuridico recurso = buscar(uuid);

        if (recurso.getRutaArchivo() == null || recurso.getRutaArchivo().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "El recurso no tiene archivo adjunto");
        }

        Path ruta = almacenamientoService.resolver(recurso.getRutaArchivo());

        // También se validan archivos antiguos antes de servirlos. Esto evita que
        // un recurso inseguro cargado antes del parche pueda descargarse/abrirse.
        almacenamientoService.validarArchivoExistente(
                ruta,
                recurso.getNombreArchivo(),
                recurso.getExtension());

        return ruta;
    }

    // =========================================================
    // BORRADO LÓGICO
    // =========================================================

    @Transactional
    public void eliminar(String uuid) {
        RecursoJuridico recurso = buscar(uuid);

        // En un sistema jurídico el registro y el archivo se conservan para
        // trazabilidad. Se retiran del catálogo mediante activo = false.
        recurso.setActivo(false);
        repository.saveAndFlush(recurso);
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private RecursoJuridico buscar(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            throw new EntityNotFoundException("Recurso jurídico no encontrado");
        }

        return repository.findByUuidPublicoAndEstudioIdAndActivoTrue(
                        uuid.trim(),
                        SesionActual.estudioId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Recurso jurídico no encontrado"));
    }

    private void validarArchivoNuevo(MultipartFile archivo) {
        if (archivo == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Debes adjuntar un documento. Solo se aceptan PDF, DOC y DOCX");
        }
    }

    private void aplicarArchivo(
            RecursoJuridico recurso,
            MultipartFile archivo,
            String expedienteCodigo) {

        String codigoExpediente = texto(expedienteCodigo);
        if (codigoExpediente == null) {
            codigoExpediente = "BIBLIOTECA";
        }

        // Igual que Panel Documentos:
        // - PDF se conserva.
        // - DOC/DOCX se convierten con ExportacionDocumentoService y se
        //   almacenan ya normalizados como PDF.
        MultipartFile archivoNormalizado = exportacionBibliotecaService.normalizarAPdf(archivo);

        ArchivoGuardado guardado = almacenamientoService.guardar(
                archivoNormalizado,
                codigoExpediente,
                recurso.getId(),
                1);

        recurso.setNombreArchivo(guardado.nombreOriginal());
        recurso.setRutaArchivo(guardado.rutaRelativa());
        recurso.setTamanioBytes(guardado.tamanoBytes());
        recurso.setExtension(guardado.extension());
    }

    private RecursoDetalleResponse aDetalle(RecursoJuridico r) {
        return new RecursoDetalleResponse(
                r.getUuidPublico(),
                r.getTitulo(),
                r.getSubtituloDescripcion(),
                r.getTipo(),
                r.getCategoria(),
                r.getAnio(),
                r.getExpedienteCodigo(),
                r.getNombreArchivo(),
                r.getExtension(),
                r.getTamanioBytes(),
                r.getEstado(),
                r.getFechaCreacion(),
                r.getFechaModificacion());
    }

    private Pageable paginaSegura(Pageable pageable) {
        int pagina = pageable == null ? 0 : Math.max(pageable.getPageNumber(), 0);
        int solicitado = pageable == null ? 20 : pageable.getPageSize();
        int tamano = TAMANOS_PERMITIDOS.contains(solicitado) ? solicitado : 20;

        // Orden estable para que un registro no salte entre páginas cuando hay
        // varias altas con la misma fecha.
        Sort orden = Sort.by(
                Sort.Order.desc("fechaCreacion"),
                Sort.Order.desc("id"));

        return PageRequest.of(pagina, tamano, orden);
    }

    private void validarRequest(RecursoCrearRequest request) {
        if (request == null || request.getTitulo() == null || request.getTitulo().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El título es obligatorio");
        }
        if (codigo(request.getTipo()) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El tipo es obligatorio");
        }
        if (codigo(request.getCategoria()) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La categoría es obligatoria");
        }
    }

    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static String codigo(String valor) {
        String limpio = texto(valor);
        return limpio == null ? null : limpio.toUpperCase();
    }

    private static long valor(Long valor) {
        return valor == null ? 0L : valor;
    }
}
