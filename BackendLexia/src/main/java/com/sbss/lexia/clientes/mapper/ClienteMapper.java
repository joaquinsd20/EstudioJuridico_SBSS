package com.sbss.lexia.clientes.mapper;

import com.sbss.lexia.clientes.dto.ClienteDetalleDto;
import com.sbss.lexia.clientes.dto.ClienteResumenDto;
import com.sbss.lexia.clientes.entity.Cliente;
import com.sbss.lexia.seguridad.entity.Usuario;

/**
 * Entidad -> DTO. El conteo de expedientes no vive en la entidad (viene de una
 * consulta nativa aparte en ClienteRepository), asi que ambos metodos lo
 * reciben como parametro en vez de leerlo de {@code cliente}.
 */
public final class ClienteMapper {

    private ClienteMapper() {
    }

    public static ClienteResumenDto aResumenDto(Cliente cliente, long totalExpedientes) {
        return new ClienteResumenDto(
                cliente.getUuidPublico(),
                cliente.getTipoDocumento(),
                cliente.getNumeroDocumento(),
                cliente.getNombreBusqueda(),
                cliente.getTipoPersona(),
                cliente.getEstado(),
                cliente.getEmail(),
                cliente.getTelefonoPrincipal(),
                nombreAbogado(cliente.getAbogadoResponsable()),
                totalExpedientes);
    }

    public static ClienteDetalleDto aDetalleDto(Cliente cliente, long totalExpedientes) {
        Usuario abogado = cliente.getAbogadoResponsable();
        return new ClienteDetalleDto(
                cliente.getUuidPublico(),
                cliente.getTipoPersona(),
                cliente.getTipoDocumento(),
                cliente.getNumeroDocumento(),
                cliente.getNombres(),
                cliente.getApellidoPaterno(),
                cliente.getApellidoMaterno(),
                cliente.getRazonSocial(),
                cliente.getNombreComercial(),
                cliente.getNombreBusqueda(),
                cliente.getEmail(),
                cliente.getTelefonoPrincipal(),
                cliente.getTelefonoSecundario(),
                cliente.getDireccion(),
                cliente.getDistrito(),
                cliente.getProvincia(),
                cliente.getDepartamento(),
                cliente.getEstado(),
                cliente.getOrigenCaptacion(),
                cliente.getSituacionFinanciera(),
                nombreAbogado(abogado),
                abogado != null ? abogado.getUuidPublico() : null,
                cliente.getFechaUltimoContacto(),
                cliente.getObservaciones(),
                cliente.getFechaCreacion(),
                totalExpedientes);
    }

    private static String nombreAbogado(Usuario abogado) {
        return abogado != null ? abogado.getNombreCompleto() : null;
    }
}
