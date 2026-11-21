package cu.pican.model;

/**
 * Representa un usuario activo en la sesión.
 * Modelo provisional para la etapa de interfaz (sin autenticación real).
 */
public class User {
    private final String username;
    private final String displayName;
    private final String role;

    public User(String username, String displayName, String role) {
        this.username = username;
        this.displayName = displayName;
        this.role = role;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getRole() {
        return role;
    }
}
