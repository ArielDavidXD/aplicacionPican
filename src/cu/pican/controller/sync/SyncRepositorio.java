package cu.pican.controller.sync;

import cu.pican.model.Rol;
import cu.pican.model.persistence.JdbcConnectionManager;
import cu.pican.model.persistence.JdbcSupport;
import cu.pican.controller.sync.SyncEsquema.Fk;
import cu.pican.controller.sync.SyncEsquema.Tipo;
import cu.pican.controller.sync.SyncModelo.Conflicto;
import cu.pican.controller.sync.SyncModelo.ItemResultado;
import cu.pican.controller.sync.SyncModelo.Registro;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Acceso JDBC al historial de sincronización y a las filas operativas transportadas. */
public final class SyncRepositorio {

    private final JdbcConnectionManager conexiones;

    public SyncRepositorio(JdbcConnectionManager conexiones) {
        if (conexiones == null) {
            throw new SyncException("La sincronización requiere persistencia PostgreSQL; el modo MEMORY no la soporta.");
        }
        this.conexiones = conexiones;
    }

    // -------------------------------------------------------------------------
    // Historial de paquetes
    // -------------------------------------------------------------------------

    public void registrarPaquete(Map<String, Object> manifestacion, String estado, String mensaje, UUID idUsuario) {
        String sql = "INSERT INTO sync_paquete (id_paquete, tipo, id_ueb_origen, id_destino, generado_en, "
                + "version_protocolo, version_esquema, id_lote, cantidad_registros, hash_integridad, "
                + "estado, mensaje, id_usuario, secuencia) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        ejecutar(sql, ps -> {
            ps.setObject(1, uuid(texto(manifestacion.get("idPaquete"))), Types.OTHER);
            ps.setString(2, texto(manifestacion.get("tipo")));
            uuidNullable(ps, 3, texto(manifestacion.get("idUebOrigen")));
            ps.setString(4, texto(manifestacion.get("idDestino")));
            fechaHora(ps, 5, texto(manifestacion.get("generadoEn")));
            ps.setString(6, texto(manifestacion.get("versionProtocolo")));
            ps.setString(7, texto(manifestacion.get("versionEsquema")));
            ps.setObject(8, uuid(texto(manifestacion.get("idLote"))), Types.OTHER);
            ps.setInt(9, Integer.parseInt(texto(manifestacion.get("cantidadRegistros"), "0")));
            JdbcSupport.setString(ps, 10, texto(manifestacion.get("payloadSha256")));
            ps.setString(11, estado);
            JdbcSupport.setTexto(ps, 12, mensaje);
            uuidNullable(ps, 13, idUsuario == null ? texto(manifestacion.get("generadoPor")) : idUsuario.toString());
            Long secuencia = parseLongNullable(texto(manifestacion.get("secuencia")));
            if (secuencia == null) {
                ps.setNull(14, Types.BIGINT);
            } else {
                ps.setLong(14, secuencia);
            }
        });
    }

    public Map<String, Object> consultarPaquete(UUID idPaquete) {
        return unaFila("SELECT * FROM sync_paquete WHERE id_paquete = ?", ps ->
                ps.setObject(1, idPaquete.toString(), Types.OTHER));
    }

    public boolean existePaquete(UUID idPaquete) {
        return consultarPaquete(idPaquete) != null;
    }

    public boolean paqueteYaProcesado(UUID idPaquete) {
        Map<String, Object> paquete = consultarPaquete(idPaquete);
        if (paquete == null) {
            return false;
        }
        String estado = texto(paquete.get("estado"));
        return "IMPORTADO".equals(estado) || "PARCIAL".equals(estado)
                || "RECHAZADO".equals(estado) || "CONFIRMADO".equals(estado)
                || "APLICADO".equals(estado);
    }

    public void actualizarEstadoPaquete(UUID idPaquete, String estado, String mensaje) {
        ejecutar("UPDATE sync_paquete SET estado = ?, procesado_en = CURRENT_TIMESTAMP, mensaje = ? "
                + "WHERE id_paquete = ?", ps -> {
            ps.setString(1, estado);
            JdbcSupport.setTexto(ps, 2, mensaje);
            ps.setObject(3, idPaquete.toString(), Types.OTHER);
        });
    }

    public void registrarItem(UUID idPaquete, ItemResultado item) {
        String sql = "INSERT INTO sync_item (id_item, id_paquete, tabla, id_registro, accion, estado, detalle, version) "
                + "VALUES (?,?,?,?,?,?,?,?) ON CONFLICT (id_paquete, tabla, id_registro) DO UPDATE SET "
                + "estado = EXCLUDED.estado, detalle = EXCLUDED.detalle";
        ejecutar(sql, ps -> {
            ps.setObject(1, UUID.randomUUID().toString(), Types.OTHER);
            ps.setObject(2, idPaquete.toString(), Types.OTHER);
            ps.setString(3, item.tabla);
            ps.setString(4, item.idRegistro);
            ps.setString(5, "UPSERT");
            ps.setString(6, item.estado);
            JdbcSupport.setTexto(ps, 7, item.detalle);
            ps.setNull(8, Types.BIGINT);
        });
    }

    public List<Map<String, Object>> consultarItems(UUID idPaquete) {
        return filas("SELECT * FROM sync_item WHERE id_paquete = ? ORDER BY creado_en, tabla, id_registro",
                ps -> ps.setObject(1, idPaquete.toString(), Types.OTHER));
    }

    public List<Map<String, Object>> listarPaquetes(int limite) {
        return filas("SELECT * FROM sync_paquete ORDER BY generado_en DESC LIMIT ?",
                ps -> ps.setInt(1, limite));
    }

    public void registrarConflicto(Conflicto conflicto, UUID idPaquete,
                                   String payloadRecibido, String payloadLocal) {
        ejecutar("INSERT INTO sync_conflicto (id_conflicto, tabla, id_registro, id_paquete, "
                + "version_recibida, version_local, payload_recibido, payload_local, estado) "
                + "VALUES (?,?,?,?,?,?,?,?,'PENDIENTE')", ps -> {
            ps.setObject(1, UUID.randomUUID().toString(), Types.OTHER);
            ps.setString(2, conflicto.tabla);
            ps.setString(3, conflicto.idRegistro);
            uuidNullable(ps, 4, idPaquete == null ? null : idPaquete.toString());
            ps.setLong(5, conflicto.versionRecibida);
            ps.setLong(6, conflicto.versionLocal);
            JdbcSupport.setTexto(ps, 7, payloadRecibido);
            JdbcSupport.setTexto(ps, 8, payloadLocal);
        });
    }

    public List<Map<String, Object>> listarConflictos(String estado) {
        return filas("SELECT * FROM sync_conflicto WHERE estado = ? ORDER BY detectado_en DESC",
                ps -> ps.setString(1, estado));
    }

    public List<Map<String, Object>> listarConflictosPorPaquete(String estado, UUID idPaquete) {
        return filas("SELECT * FROM sync_conflicto WHERE estado = ? AND id_paquete = ? "
                + "ORDER BY detectado_en DESC", ps -> {
            ps.setString(1, estado);
            uuidNullable(ps, 2, idPaquete.toString());
        });
    }

    public boolean existeConflictoPendiente(String tabla, String idRegistro) {
        return unaFila("SELECT 1 AS presente FROM sync_conflicto WHERE tabla = ? AND id_registro = ? "
                + "AND estado = 'PENDIENTE'", ps -> {
            ps.setString(1, tabla);
            ps.setString(2, idRegistro);
        }) != null;
    }

    // -------------------------------------------------------------------------
    // Control de exportaciones confirmadas
    // -------------------------------------------------------------------------

    public long versionConfirmada(String tabla, String idRegistro) {
        Map<String, Object> fila = unaFila(
                "SELECT version_confirmada FROM sync_exportacion WHERE tabla = ? AND id_registro = ?", ps -> {
                    ps.setString(1, tabla);
                    ps.setString(2, idRegistro);
                });
        return fila == null ? 0L : Long.parseLong(texto(fila.get("version_confirmada"), "0"));
    }

    public void registrarExportacion(String tabla, String idRegistro, long version, UUID idLote) {
        ejecutar("INSERT INTO sync_exportacion (tabla, id_registro, version_confirmada, confirmado_en, id_lote) "
                + "VALUES (?,?,?,CURRENT_TIMESTAMP,?) ON CONFLICT (tabla, id_registro) DO UPDATE SET "
                + "version_confirmada = EXCLUDED.version_confirmada, "
                + "confirmado_en = CURRENT_TIMESTAMP, id_lote = EXCLUDED.id_lote", ps -> {
            ps.setString(1, tabla);
            ps.setString(2, idRegistro);
            ps.setLong(3, version);
            ps.setObject(4, idLote.toString(), Types.OTHER);
        });
    }

    /** Aplica la confirmación recibida en la UEB de origen. */
    public void confirmarPaquete(UUID idPaqueteOrigen, UUID idLote, LocalDateTime fecha) {
        List<Map<String, Object>> items = consultarItems(idPaqueteOrigen);
        for (Map<String, Object> item : items) {
            String estado = texto(item.get("estado"));
            // En la UEB de origen los ítems quedan como GENERADO al preparar el paquete;
            // en la central quedan como APLICADO/OMITIDO/CONFLICTO al importarlo.
            if (!"APLICADO".equals(estado) && !"OMITIDO".equals(estado) && !"GENERADO".equals(estado)) {
                continue;
            }
            String tabla = texto(item.get("tabla"));
            if (!SyncEsquema.esExportable(tabla)) {
                continue;
            }
            String idRegistro = texto(item.get("id_registro"));
            String pk = SyncEsquema.clavePrimaria(tabla);
            ejecutar("UPDATE " + tabla + " SET sincronizado_en = ?, id_lote_sincronizacion = ? WHERE " + pk + " = ?",
                    ps -> {
                        ps.setTimestamp(1, java.sql.Timestamp.valueOf(fecha));
                        ps.setObject(2, idLote.toString(), Types.OTHER);
                        ps.setObject(3, idRegistro, Types.OTHER);
                    });
            long version = item.get("version") == null ? 0L
                    : Long.parseLong(texto(item.get("version"), "0"));
            registrarExportacion(tabla, idRegistro, version, idLote);
            ejecutar("UPDATE sync_item SET estado = 'CONFIRMADO' WHERE id_paquete = ? AND tabla = ? "
                    + "AND id_registro = ?", ps -> {
                ps.setObject(1, idPaqueteOrigen.toString(), Types.OTHER);
                ps.setString(2, tabla);
                ps.setString(3, idRegistro);
            });
        }
    }

    // -------------------------------------------------------------------------
    // Lectura y escritura de registros operativos
    // -------------------------------------------------------------------------

    public List<Registro> listarPendientes(UUID idUebOrigen) {
        List<Registro> resultado = new ArrayList<>();
        for (String tabla : SyncEsquema.tablasEnOrden()) {
            boolean tieneActiva = SyncEsquema.columnas(tabla).containsKey("activa");
            String where = "sincronizado_en IS NULL OR eliminado_logico = TRUE";
            if (tieneActiva) {
                where += " OR activa = FALSE";
            }
            String sql = "SELECT * FROM " + tabla + " WHERE " + where + " ORDER BY creado_en, actualizado_en";
            for (Map<String, Object> fila : filas(sql, ps -> { })) {
                String id = texto(fila.get(SyncEsquema.clavePrimaria(tabla)));
                if (id == null) {
                    continue;
                }
                etiquetarOrigen(tabla, id, idUebOrigen);
                Map<String, Object> actualizada = unaFila(
                        "SELECT * FROM " + tabla + " WHERE " + SyncEsquema.clavePrimaria(tabla) + " = ?",
                        ps -> ps.setObject(1, id, Types.OTHER));
                if (actualizada == null) {
                    continue;
                }
                resultado.add(aRegistro(tabla, actualizada));
            }
        }
        return resultado;
    }

    private Registro aRegistro(String tabla, Map<String, Object> fila) {
        Registro registro = new Registro();
        registro.tabla = tabla;
        registro.accion = "UPSERT";
        registro.idRegistro = texto(fila.get(SyncEsquema.clavePrimaria(tabla)));
        registro.version = Long.parseLong(texto(fila.get("version"), "1"));
        registro.idUebOrigen = texto(fila.get("id_ueb_origen"));
        registro.creadoEn = texto(fila.get("creado_en"));
        registro.actualizadoEn = texto(fila.get("actualizado_en"));
        registro.eliminadoLogico = Boolean.parseBoolean(texto(fila.get("eliminado_logico"), "false"));
        for (Map.Entry<String, Tipo> columna : SyncEsquema.columnas(tabla).entrySet()) {
            registro.datos.put(columna.getKey(), texto(fila.get(columna.getKey())));
        }
        return registro;
    }

    private void etiquetarOrigen(String tabla, String id, UUID idUebOrigen) {
        if (idUebOrigen == null) {
            return;
        }
        ejecutar("UPDATE " + tabla + " SET id_ueb_origen = ? WHERE "
                + SyncEsquema.clavePrimaria(tabla) + " = ? AND id_ueb_origen IS NULL", ps -> {
            ps.setObject(1, idUebOrigen.toString(), Types.OTHER);
            ps.setObject(2, id, Types.OTHER);
        });
    }

    public boolean existeFila(String tabla, String idRegistro) {
        String pk = SyncEsquema.clavePrimaria(tabla);
        return unaFila("SELECT 1 AS presente FROM " + tabla + " WHERE " + pk + " = ?",
                ps -> ps.setObject(1, idRegistro, Types.OTHER)) != null;
    }

    public boolean existeEnTabla(String tabla, String columna, String valor) {
        return unaFila("SELECT 1 AS presente FROM " + tabla + " WHERE " + columna + " = ?",
                ps -> ps.setObject(1, valor, Types.OTHER)) != null;
    }

    public long versionFila(String tabla, String idRegistro) {
        Map<String, Object> fila = unaFila(
                "SELECT version FROM " + tabla + " WHERE " + SyncEsquema.clavePrimaria(tabla) + " = ?",
                ps -> ps.setObject(1, idRegistro, Types.OTHER));
        return fila == null ? -1L : Long.parseLong(texto(fila.get("version"), "0"));
    }

    public Map<String, Object> leerFila(String tabla, String idRegistro) {
        return unaFila(
                "SELECT * FROM " + tabla + " WHERE " + SyncEsquema.clavePrimaria(tabla) + " = ?",
                ps -> ps.setObject(1, idRegistro, Types.OTHER));
    }

    public String validarDependencias(Registro registro) {
        for (Fk dependencia : SyncEsquema.dependencias(registro.tabla)) {
            String valor = registro.datos.get(dependencia.columna);
            if (valor == null) {
                continue;
            }
            String tablaDestino = dependencia.tabla;
            String columnaDestino = "usuario".equals(tablaDestino)
                    ? "id_usuario" : SyncEsquema.clavePrimaria(tablaDestino);
            if (!existeEnTabla(tablaDestino, columnaDestino, valor)) {
                return "Dependencia inexistente: " + dependencia.columna + " -> " + tablaDestino;
            }
        }
        return null;
    }

    /** Inserta o actualiza una fila aplicando siempre bajas lógicas, nunca borrado físico. */
    public void aplicarRegistro(Registro registro, UUID idLote) {
        String tabla = registro.tabla;
        Map<String, Tipo> columnas = SyncEsquema.columnas(tabla);
        LinkedHashMap<String, String> datos = new LinkedHashMap<>(registro.datos);
        if ("parte_nave".equals(tabla) && !datos.containsKey("advertencia_revisada")) {
            datos.put("advertencia_revisada", "false");
        }
        datos.put("version", Long.toString(registro.version));
        datos.put("id_ueb_origen", registro.idUebOrigen);
        datos.put("creado_en", registro.creadoEn);
        datos.put("actualizado_en", registro.actualizadoEn == null
                ? LocalDateTime.now().toString() : registro.actualizadoEn);
        datos.put("eliminado_logico", Boolean.toString(registro.eliminadoLogico));
        datos.put("id_lote_sincronizacion", idLote.toString());
        datos.remove("sincronizado_en");

        if (!existeFila(tabla, registro.idRegistro)) {
            StringBuilder sql = new StringBuilder("INSERT INTO " + tabla + " (");
            for (String columna : columnas.keySet()) {
                if (sql.charAt(sql.length() - 1) != '(') {
                    sql.append(',');
                }
                sql.append(columna);
            }
            sql.append(") VALUES (");
            boolean primero = true;
            for (String columna : columnas.keySet()) {
                if (!primero) {
                    sql.append(',');
                }
                primero = false;
                sql.append('?');
            }
            sql.append(')');
            ejecutar(sql.toString(), ps -> {
                int i = 1;
                for (String columna : columnas.keySet()) {
                    SyncEsquema.asignar(ps, i++, columnas.get(columna), datos.get(columna));
                }
            });
            return;
        }

        LinkedHashMap<String, Tipo> actualizables = new LinkedHashMap<>();
        for (Map.Entry<String, Tipo> columna : columnas.entrySet()) {
            if ("sincronizado_en".equals(columna.getKey()) || "creado_en".equals(columna.getKey())) {
                continue;
            }
            actualizables.put(columna.getKey(), columna.getValue());
        }
        StringBuilder sql = new StringBuilder("UPDATE " + tabla + " SET ");
        boolean primero = true;
        for (String columna : actualizables.keySet()) {
            if (!primero) {
                sql.append(',');
            }
            primero = false;
            sql.append(columna).append(" = ?");
        }
        sql.append(" WHERE ").append(SyncEsquema.clavePrimaria(tabla)).append(" = ?");
        ejecutar(sql.toString(), ps -> {
            int i = 1;
            for (Map.Entry<String, Tipo> columna : actualizables.entrySet()) {
                SyncEsquema.asignar(ps, i++, columna.getValue(), datos.get(columna.getKey()));
            }
            ps.setObject(i, registro.idRegistro, Types.OTHER);
        });
    }

    // -------------------------------------------------------------------------
    // Bajada (DN) y alta (AL)
    // -------------------------------------------------------------------------

    /** Catálogos y usuarios que la central envía a una UEB en una bajada (DN). */
    public List<Registro> listarBajada(UUID idUeb) {
        List<Registro> resultado = new ArrayList<>();
        for (String tabla : SyncEsquema.tablasBajadaEnOrden()) {
            List<Map<String, Object>> filas;
            if ("usuario".equals(tabla)) {
                filas = filas("SELECT * FROM usuario WHERE id_ueb_origen = ? "
                        + "ORDER BY creado_en, actualizado_en",
                        ps -> ps.setObject(1, idUeb.toString(), Types.OTHER));
            } else {
                filas = filas("SELECT * FROM " + tabla + " WHERE id_ueb_origen IS NULL "
                        + "ORDER BY creado_en, actualizado_en", ps -> { });
            }
            for (Map<String, Object> fila : filas) {
                resultado.add(aRegistro(tabla, fila));
            }
        }
        return resultado;
    }

    /** Contenido de un alta (AL): la fila de la UEB más todo lo que incluiría una bajada. */
    public List<Registro> listarAlta(UUID idUeb) {
        List<Registro> resultado = new ArrayList<>();
        Map<String, Object> fila = unaFila("SELECT * FROM ueb WHERE id_ueb = ?",
                ps -> ps.setObject(1, idUeb.toString(), Types.OTHER));
        if (fila != null) {
            resultado.add(aRegistro("ueb", fila));
        }
        resultado.addAll(listarBajada(idUeb));
        return resultado;
    }

    /**
     * Aplica un registro de bajada o alta en la instalación local. A diferencia del
     * flujo UP, la fila queda sellada con {@code sincronizado_en} para que nunca se
     * reexporte hacia el central.
     */
    public void aplicarRegistroDescarga(Registro registro, UUID idLote, LocalDateTime fecha) {
        String tabla = registro.tabla;
        Map<String, Tipo> columnas = SyncEsquema.columnas(tabla);
        LinkedHashMap<String, String> datos = new LinkedHashMap<>(registro.datos);
        datos.put("version", Long.toString(registro.version));
        datos.put("id_ueb_origen", registro.idUebOrigen);
        datos.put("creado_en", registro.creadoEn);
        datos.put("actualizado_en", registro.actualizadoEn == null
                ? LocalDateTime.now().toString() : registro.actualizadoEn);
        datos.put("eliminado_logico", Boolean.toString(registro.eliminadoLogico));
        datos.put("id_lote_sincronizacion", idLote.toString());
        datos.put("sincronizado_en", fecha.toString());

        if (!existeFila(tabla, registro.idRegistro)) {
            StringBuilder sql = new StringBuilder("INSERT INTO " + tabla + " (");
            boolean primero = true;
            for (String columna : columnas.keySet()) {
                if (!primero) {
                    sql.append(',');
                }
                primero = false;
                sql.append(columna);
            }
            sql.append(") VALUES (");
            for (int i = 0; i < columnas.size(); i++) {
                if (i > 0) {
                    sql.append(',');
                }
                sql.append('?');
            }
            sql.append(')');
            ejecutar(sql.toString(), ps -> {
                int i = 1;
                for (String columna : columnas.keySet()) {
                    SyncEsquema.asignar(ps, i++, columnas.get(columna), datos.get(columna));
                }
            });
            return;
        }

        LinkedHashMap<String, Tipo> actualizables = new LinkedHashMap<>();
        for (Map.Entry<String, Tipo> columna : columnas.entrySet()) {
            if ("creado_en".equals(columna.getKey())) {
                continue;
            }
            actualizables.put(columna.getKey(), columna.getValue());
        }
        StringBuilder sql = new StringBuilder("UPDATE " + tabla + " SET ");
        boolean primero = true;
        for (String columna : actualizables.keySet()) {
            if (!primero) {
                sql.append(',');
            }
            primero = false;
            sql.append(columna).append(" = ?");
        }
        sql.append(" WHERE ").append(SyncEsquema.clavePrimaria(tabla)).append(" = ?");
        ejecutar(sql.toString(), ps -> {
            int i = 1;
            for (Map.Entry<String, Tipo> columna : actualizables.entrySet()) {
                SyncEsquema.asignar(ps, i++, columna.getValue(), datos.get(columna.getKey()));
            }
            ps.setObject(i, registro.idRegistro, Types.OTHER);
        });
    }

    /** Garantiza que los roles referenciados por los usuarios del paquete existan localmente. */
    public void asegurarRoles(List<Registro> registros) {
        for (Registro registro : registros) {
            if (!"usuario".equals(registro.tabla)) {
                continue;
            }
            String idRol = registro.datos.get("id_rol");
            if (idRol == null) {
                continue;
            }
            Rol rol = Rol.desdeId(UUID.fromString(idRol));
            if (rol == null) {
                continue;
            }
            ejecutar("INSERT INTO rol (id_rol, nombre, descripcion) VALUES (?,?,?) "
                    + "ON CONFLICT (id_rol) DO NOTHING", ps -> {
                ps.setObject(1, idRol, Types.OTHER);
                ps.setString(2, rol.getNombre());
                ps.setString(3, rol.getDescripcion());
            });
        }
    }

    /** Siguiente número de paquete por UEB y sentido (UP/DN). Los contadores son independientes. */
    public long siguienteSecuencia(UUID idUeb, String sentido) {
        Map<String, Object> fila = unaFila("INSERT INTO sync_secuencia (id_ueb, sentido, ultima) "
                + "VALUES (?, ?, 1) ON CONFLICT (id_ueb, sentido) DO UPDATE SET "
                + "ultima = sync_secuencia.ultima + 1 RETURNING ultima", ps -> {
            ps.setObject(1, idUeb.toString(), Types.OTHER);
            ps.setString(2, sentido);
        });
        return Long.parseLong(texto(fila.get("ultima"), "1"));
    }

    /** Última secuencia usada sin incrementarla; sirve para sugerir nombres de archivo. */
    public long ultimaSecuencia(UUID idUeb, String sentido) {
        Map<String, Object> fila = unaFila("SELECT ultima FROM sync_secuencia WHERE id_ueb = ? AND sentido = ?",
                ps -> {
                    ps.setObject(1, idUeb.toString(), Types.OTHER);
                    ps.setString(2, sentido);
                });
        return fila == null ? 0L : Long.parseLong(texto(fila.get("ultima"), "0"));
    }

    public String configuracion(String clave) {
        Map<String, Object> fila = unaFila("SELECT valor FROM sync_configuracion WHERE clave = ?",
                ps -> ps.setString(1, clave));
        return fila == null ? null : texto(fila.get("valor"));
    }

    public void guardarConfiguracion(String clave, String valor) {
        ejecutar("INSERT INTO sync_configuracion (clave, valor, actualizado_en) "
                + "VALUES (?, ?, CURRENT_TIMESTAMP) ON CONFLICT (clave) DO UPDATE SET "
                + "valor = EXCLUDED.valor, actualizado_en = CURRENT_TIMESTAMP", ps -> {
            ps.setString(1, clave);
            JdbcSupport.setTexto(ps, 2, valor);
        });
    }

    /** Último número de vale conocido para una UEB, o null si todavía no emitió ninguno. */
    public Long ultimoValeConocido(UUID idUeb) {
        Map<String, Object> fila = unaFila("SELECT numero_vale FROM vale_entrega WHERE id_ueb = ? "
                        + "ORDER BY numero_vale DESC LIMIT 1",
                ps -> ps.setObject(1, idUeb.toString(), Types.OTHER));
        if (fila == null) {
            return null;
        }
        String numero = texto(fila.get("numero_vale"));
        int guion = numero == null ? -1 : numero.lastIndexOf('-');
        if (guion < 0 || guion >= numero.length() - 1) {
            return null;
        }
        try {
            return Long.parseLong(numero.substring(guion + 1));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** Adelanta el contador local de vales sin retroceder nunca. */
    public void sincronizarValeSecuencia(UUID idUeb, long ultimo) {
        ejecutar("INSERT INTO vale_secuencia (id_ueb, secuencia) VALUES (?, ?) "
                + "ON CONFLICT (id_ueb) DO UPDATE SET secuencia = GREATEST(vale_secuencia.secuencia, EXCLUDED.secuencia)",
                ps -> {
                    ps.setObject(1, idUeb.toString(), Types.OTHER);
                    ps.setLong(2, ultimo);
                });
    }

    public String codigoUeb(UUID idUeb) {
        Map<String, Object> fila = unaFila("SELECT codigo FROM ueb WHERE id_ueb = ?",
                ps -> ps.setObject(1, idUeb.toString(), Types.OTHER));
        return fila == null ? null : texto(fila.get("codigo"));
    }

    /** Vincula de forma duradera un usuario central a su UEB para que viaje en las bajadas. */
    public void vincularUsuarioAUEB(UUID idUsuario, UUID idUeb) {
        ejecutar("UPDATE usuario SET id_ueb_origen = ?, version = version + 1, "
                + "actualizado_en = CURRENT_TIMESTAMP, sincronizado_en = NULL WHERE id_usuario = ?", ps -> {
            ps.setObject(1, idUeb.toString(), Types.OTHER);
            ps.setObject(2, idUsuario.toString(), Types.OTHER);
        });
    }

    public Map<String, Object> buscarUsuario(String nombreUsuario) {
        return unaFila("SELECT * FROM usuario WHERE lower(btrim(nombre_usuario)) = lower(btrim(?)) LIMIT 1",
                ps -> ps.setString(1, nombreUsuario));
    }

    public List<Map<String, Object>> listarRecibosUp(UUID idUeb, int limite) {
        return filas("SELECT id_paquete, generado_en, cantidad_registros, estado, secuencia "
                + "FROM sync_paquete WHERE tipo = 'UP' AND id_ueb_origen = ? "
                + "ORDER BY generado_en DESC LIMIT ?", ps -> {
            ps.setObject(1, idUeb.toString(), Types.OTHER);
            ps.setInt(2, limite);
        });
    }

    public List<Map<String, Object>> resumenItemsPorPaquete(UUID idUeb) {
        return filas("SELECT p.id_paquete, i.estado, COUNT(*) AS total "
                + "FROM sync_item i JOIN sync_paquete p ON p.id_paquete = i.id_paquete "
                + "WHERE p.tipo = 'UP' AND p.id_ueb_origen = ? "
                + "GROUP BY p.id_paquete, i.estado ORDER BY p.id_paquete, i.estado", ps ->
                ps.setObject(1, idUeb.toString(), Types.OTHER));
    }

    public List<Map<String, Object>> listarRechazos(UUID idUeb, int limite) {
        return filas("SELECT p.id_paquete, p.generado_en, i.tabla, i.id_registro, i.detalle "
                + "FROM sync_item i JOIN sync_paquete p ON p.id_paquete = i.id_paquete "
                + "WHERE p.tipo = 'UP' AND p.id_ueb_origen = ? AND i.estado = 'RECHAZADO' "
                + "ORDER BY p.generado_en DESC LIMIT ?", ps -> {
            ps.setObject(1, idUeb.toString(), Types.OTHER);
            ps.setInt(2, limite);
        });
    }

    // -------------------------------------------------------------------------
    // Utilidades JDBC
    // -------------------------------------------------------------------------

    private Connection conexion() {
        if (JdbcSupport.hayConexionActiva()) {
            return JdbcSupport.conexionActiva();
        }
        return conexiones.obtenerConexion();
    }

    private void cerrarSiPropio(Connection conexion) {
        if (conexion != JdbcSupport.conexionActiva()) {
            try {
                conexion.close();
            } catch (SQLException ignored) {
                // No se oculta el resultado de la operación.
            }
        }
    }

    private void ejecutar(String sql, Preparador preparador) {
        Connection conexion = conexion();
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            preparador.preparar(ps);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new SyncException("Fallo SQL durante la sincronización: " + ex.getMessage(), ex);
        } finally {
            cerrarSiPropio(conexion);
        }
    }

    private List<Map<String, Object>> filas(String sql, Preparador preparador) {
        Connection conexion = conexion();
        List<Map<String, Object>> filas = new ArrayList<>();
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            preparador.preparar(ps);
            try (ResultSet rs = ps.executeQuery()) {
                int columnas = rs.getMetaData().getColumnCount();
                while (rs.next()) {
                    LinkedHashMap<String, Object> fila = new LinkedHashMap<>();
                    for (int i = 1; i <= columnas; i++) {
                        String nombre = rs.getMetaData().getColumnLabel(i);
                        Object valor = rs.getObject(i);
                        fila.put(nombre, valor == null ? null : String.valueOf(valor));
                    }
                    filas.add(fila);
                }
            }
        } catch (SQLException ex) {
            throw new SyncException("Fallo SQL al consultar sincronización: " + ex.getMessage(), ex);
        } finally {
            cerrarSiPropio(conexion);
        }
        return filas;
    }

    private Map<String, Object> unaFila(String sql, Preparador preparador) {
        List<Map<String, Object>> filas = filas(sql, preparador);
        return filas.isEmpty() ? null : filas.get(0);
    }

    @FunctionalInterface
    private interface Preparador {
        void preparar(PreparedStatement ps) throws SQLException;
    }

    private static void uuidNullable(PreparedStatement ps, int indice, String valor) throws SQLException {
        if (valor == null || valor.trim().isEmpty()) {
            ps.setNull(indice, Types.OTHER);
        } else {
            ps.setObject(indice, valor, Types.OTHER);
        }
    }

    private static void fechaHora(PreparedStatement ps, int indice, String valor) throws SQLException {
        if (valor == null || valor.trim().isEmpty()) {
            ps.setNull(indice, Types.TIMESTAMP);
        } else {
            ps.setTimestamp(indice, java.sql.Timestamp.valueOf(
                    LocalDateTime.parse(valor.trim().replace(' ', 'T'))));
        }
    }

    private static UUID uuid(String valor) {
        return valor == null || valor.trim().isEmpty() ? null : UUID.fromString(valor);
    }

    private static String texto(Object valor) {
        return valor == null ? null : String.valueOf(valor);
    }

    private static String texto(Object valor, String porDefecto) {
        String resultado = texto(valor);
        return resultado == null || resultado.trim().isEmpty() ? porDefecto : resultado;
    }

    private static Long parseLongNullable(String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            return null;
        }
        return Long.parseLong(valor.trim());
    }
}
