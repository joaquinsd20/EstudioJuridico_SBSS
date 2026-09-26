package com.sbss.lexia.shared.dto;

/**
 * La cota de los listados que devuelven un array JSON.
 *
 * Existe porque tres listados no tenian ninguna: medido contra una base con
 * 5.010 expedientes, 50.014 actuaciones, 30.009 documentos, 15.009 plazos y
 * 20.008 eventos, {@code GET /api/v1/plazos} devolvia las 15.009 filas en una
 * sola respuesta de <b>16.884 KB</b> y 723 ms, mientras que el unico modulo que
 * ya paginaba, clientes, contestaba en 36 ms con 2 KB. El problema no era el
 * plan de la consulta sino el tamano de la respuesta: lo que el navegador tiene
 * que descargar, parsear y pintar.
 *
 * <p>La forma del JSON <b>no cambia</b>: sigue siendo un array. Se acota con
 * {@code limite} y se avanza con {@code desplazamiento}, de modo que las
 * pantallas que ya existen siguen funcionando sin tocarlas y pueden añadir un
 * "cargar mas" cuando su dueño las toque.
 *
 * <p>Para saber si queda mas, el cliente compara: si recibio exactamente
 * {@code limite} filas, probablemente hay otra pagina. Es la convencion barata
 * y no obliga a una consulta de conteo en cada peticion.
 *
 * <p>{@link #POR_DEFECTO} es 250 a proposito: es lo que ya devolvian
 * actuaciones y expedientes, asi que ninguna pantalla existente pasa a ver
 * menos de lo que veia.
 */
public final class Limites {

    /** Filas por peticion cuando el cliente no pide un limite. */
    public static final int POR_DEFECTO = 250;

    /** Tope duro: aunque pidan 100.000, se devuelven 500. */
    public static final int TOPE = 500;

    /**
     * Marca de "sin cota", solo para llamadas internas del backend que
     * necesitan el conjunto completo porque van a contarlo o agregarlo, no a
     * devolverlo. Nunca llega desde un parametro HTTP: el controller siempre
     * pasa un entero del cliente, y {@link #limite(int)} lo acota.
     *
     * <p>El caso real es {@code AgendaService.resumen()}, que trae los eventos
     * del mes y los cuenta en Java. Acotarlo a 500 daria un resumen falso.
     * Contar en SQL seria mejor, pero eso es otra cosa y no toca aqui.
     */
    public static final int SIN_COTA = -1;

    private Limites() {
    }

    /** Acota lo que pidio el cliente al rango [1, {@link #TOPE}]. */
    public static int limite(int pedido) {
        return Math.max(1, Math.min(pedido, TOPE));
    }

    /** true si hay que aplicar LIMIT. False solo para {@link #SIN_COTA}. */
    public static boolean acota(int pedido) {
        return pedido != SIN_COTA;
    }

    /**
     * Un selector con autocompletado es otra cosa que un listado: 20 opciones
     * por defecto y 50 como tope, porque nadie lee mas de eso en un
     * desplegable. Medido: devolver los 5.010 expedientes visibles costaba
     * 345 ms por consulta y ~1 MB; acotado a 20 son 6 ms.
     */
    public static final int SELECTOR_POR_DEFECTO = 20;

    public static final int SELECTOR_TOPE = 50;

    /** Acota lo que pidio un selector al rango [1, {@link #SELECTOR_TOPE}]. */
    public static int limiteSelector(int pedido) {
        return Math.max(1, Math.min(pedido, SELECTOR_TOPE));
    }

    /** Un desplazamiento negativo no significa nada: se trata como 0. */
    public static int desplazamiento(int pedido) {
        return Math.max(0, pedido);
    }
}
