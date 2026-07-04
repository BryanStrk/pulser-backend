package com.bryanstrk.pulser.evento;

import org.springframework.data.jpa.domain.Specification;

/**
 * Filtros opcionales combinables para el listado de eventos.
 * Cada spec devuelve un predicado null cuando su filtro no aplica (Spring lo ignora al componer).
 */
public final class EventoSpecifications {

    private EventoSpecifications() {
    }

    public static Specification<Evento> conEstado(EstadoEvento estado) {
        return (root, query, cb) -> estado == null ? null : cb.equal(root.get("estado"), estado);
    }

    public static Specification<Evento> conCategoria(CategoriaEvento categoria) {
        return (root, query, cb) -> categoria == null ? null : cb.equal(root.get("categoria"), categoria);
    }

    public static Specification<Evento> conCiudad(String ciudad) {
        return (root, query, cb) -> (ciudad == null || ciudad.isBlank())
                ? null
                : cb.equal(cb.lower(root.get("ciudad")), ciudad.toLowerCase());
    }

    public static Specification<Evento> nombreContiene(String texto) {
        return (root, query, cb) -> (texto == null || texto.isBlank())
                ? null
                : cb.like(cb.lower(root.get("nombre")), "%" + texto.toLowerCase() + "%");
    }

    public static Specification<Evento> conOrganizador(Long organizadorId) {
        return (root, query, cb) -> organizadorId == null
                ? null
                : cb.equal(root.get("organizador").get("id"), organizadorId);
    }
}
