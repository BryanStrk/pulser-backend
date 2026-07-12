package com.bryanstrk.pulser.evento;

/**
 * Proyeccion de interfaz para el agregado de ocupacion por evento (una fila por evento con al
 * menos un tipo de entrada). Alimenta la barra de aforo del listado. Los SUM sobre Integer
 * devuelven Long en Hibernate; el mapper los baja a long (default 0 para eventos sin tipos, que
 * no aparecen en el GROUP BY).
 */
public interface OcupacionEventoProjection {

    Long getEventoId();

    Long getAforoTotal();

    Long getEntradasVendidas();
}
