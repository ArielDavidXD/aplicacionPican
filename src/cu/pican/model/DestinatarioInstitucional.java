package cu.pican.model;

/** Únicos destinatarios institucionales contemplados para la entrega de informes. */
public enum DestinatarioInstitucional {
    GEGAN,
    MINAG;

    public static DestinatarioInstitucional desdeTexto(String texto) {
        if (texto == null) {
            throw new IllegalArgumentException("El destinatario es obligatorio.");
        }
        String valor = texto.trim().toUpperCase();
        for (DestinatarioInstitucional d : values()) {
            if (d.name().equals(valor)) {
                return d;
            }
        }
        throw new IllegalArgumentException("Destinatario no contemplado: " + texto);
    }
}