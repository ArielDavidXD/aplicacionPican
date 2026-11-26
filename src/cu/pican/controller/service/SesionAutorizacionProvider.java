package cu.pican.controller.service;

import cu.pican.model.Usuario;

/**
 * Vista de la sesión que necesita la autorización.
 * {@code AuthService} ya expone estos datos; esta interfaz evita que los servicios
 * dependan de la clase concreta ni de componentes Swing.
 */
public interface SesionAutorizacionProvider extends SessionUserIdProvider {

    /** Usuario autenticado, o {@code null} si no hay sesión. */
    Usuario getUsuarioAutenticado();
}
