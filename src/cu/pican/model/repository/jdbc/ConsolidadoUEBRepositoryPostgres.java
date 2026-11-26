package cu.pican.model.repository.jdbc;

import cu.pican.model.ConsolidadoUEB;
import cu.pican.model.persistence.JdbcConnectionManager;
import cu.pican.model.persistence.JdbcRepositorySupport;
import cu.pican.model.persistence.JdbcSupport;
import cu.pican.model.persistence.PersistenciaException;
import cu.pican.model.repository.ConsolidadoUEBRepository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ConsolidadoUEBRepositoryPostgres extends JdbcRepositorySupport<ConsolidadoUEB>
        implements ConsolidadoUEBRepository {

    public ConsolidadoUEBRepositoryPostgres(JdbcConnectionManager conexiones) {
        super(conexiones);
    }

    @Override
    protected String tabla() {
        return "consolidado_ueb";
    }

    @Override
    protected String columnaId() {
        return "id_consolidado";
    }

    @Override
    protected UUID idDe(ConsolidadoUEB entidad) {
        return entidad.getIdConsolidado();
    }

    @Override
    protected ConsolidadoUEB mapear(ResultSet rs) throws SQLException {
        ConsolidadoUEB c = new ConsolidadoUEB();
        c.setIdConsolidado(JdbcSupport.uuid(rs, "id_consolidado"));
        c.setIdUEB(JdbcSupport.uuid(rs, "id_ueb"));
        c.setFechaInicio(JdbcSupport.fecha(rs, "fecha_inicio"));
        c.setFechaFin(JdbcSupport.fecha(rs, "fecha_fin"));
        c.setEstado(rs.getString("estado"));
        c.setMotivoParcial(rs.getString("motivo_parcial"));
        c.setIdUsuario(JdbcSupport.uuid(rs, "id_usuario"));
        c.setCreadoEn(JdbcSupport.fechaHora(rs, "creado_en"));
        c.setVersion(JdbcSupport.largo(rs, "version"));
        c.setTotalHuevos(decimal(rs, "total_huevos"));
        c.setTotalHuevosRotos(decimal(rs, "total_huevos_rotos"));
        c.setTotalGallinasVivas(rs.getLong("total_gallinas_vivas"));
        c.setTotalGallinasMuertas(rs.getLong("total_gallinas_muertas"));
        c.setTotalGallinasEnfermas(rs.getLong("total_gallinas_enfermas"));
        c.setTotalPiensoKg(decimal(rs, "total_pienso_kg"));
        c.setTotalCarne(decimal(rs, "total_carne"));
        c.setPartesNaveIncluidos(rs.getInt("partes_nave_incluidos"));
        c.setPartesNaveExcluidos(rs.getInt("partes_nave_excluidos"));
        c.setPartesUnidadIncluidos(rs.getInt("partes_unidad_incluidos"));
        c.setPartesUnidadExcluidos(rs.getInt("partes_unidad_excluidos"));
        c.setRegistrosCarneIncluidos(rs.getInt("registros_carne_incluidos"));
        c.setNavesSinParte(rs.getInt("naves_sin_parte"));
        c.setUnidadesSinParte(rs.getInt("unidades_sin_parte"));
        c.setAclaracionesPendientes(rs.getInt("aclaraciones_pendientes"));
        return c;
    }

    private BigDecimal decimal(ResultSet rs, String columna) throws SQLException {
        BigDecimal valor = rs.getBigDecimal(columna);
        return valor == null ? BigDecimal.ZERO : valor;
    }

    @Override
    protected void escribirInsercion(PreparedStatement ps, ConsolidadoUEB entidad) throws SQLException {
        JdbcSupport.setUuid(ps, 1, entidad.getIdConsolidado());
        escribirDatos(ps, entidad, 2);
        ps.setLong(24, 1L);
    }

    @Override
    protected void escribirActualizacion(PreparedStatement ps, ConsolidadoUEB entidad) throws SQLException {
        escribirDatos(ps, entidad, 1);
    }

    private void escribirDatos(PreparedStatement ps, ConsolidadoUEB entidad, int i) throws SQLException {
        JdbcSupport.setUuid(ps, i, entidad.getIdUEB());
        JdbcSupport.setFecha(ps, i + 1, entidad.getFechaInicio());
        JdbcSupport.setFecha(ps, i + 2, entidad.getFechaFin());
        JdbcSupport.setString(ps, i + 3, entidad.getEstado());
        JdbcSupport.setString(ps, i + 4, entidad.getMotivoParcial());
        JdbcSupport.setUuid(ps, i + 5, entidad.getIdUsuario());
        JdbcSupport.setFechaHora(ps, i + 6,
                entidad.getCreadoEn() == null ? LocalDateTime.now() : entidad.getCreadoEn());
        JdbcSupport.setDecimal(ps, i + 7, entidad.getTotalHuevos());
        JdbcSupport.setDecimal(ps, i + 8, entidad.getTotalHuevosRotos());
        JdbcSupport.setLong(ps, i + 9, entidad.getTotalGallinasVivas());
        JdbcSupport.setLong(ps, i + 10, entidad.getTotalGallinasMuertas());
        JdbcSupport.setLong(ps, i + 11, entidad.getTotalGallinasEnfermas());
        JdbcSupport.setDecimal(ps, i + 12, entidad.getTotalPiensoKg());
        JdbcSupport.setDecimal(ps, i + 13, entidad.getTotalCarne());
        JdbcSupport.setEntero(ps, i + 14, entidad.getPartesNaveIncluidos());
        JdbcSupport.setEntero(ps, i + 15, entidad.getPartesNaveExcluidos());
        JdbcSupport.setEntero(ps, i + 16, entidad.getPartesUnidadIncluidos());
        JdbcSupport.setEntero(ps, i + 17, entidad.getPartesUnidadExcluidos());
        JdbcSupport.setEntero(ps, i + 18, entidad.getRegistrosCarneIncluidos());
        JdbcSupport.setEntero(ps, i + 19, entidad.getNavesSinParte());
        JdbcSupport.setEntero(ps, i + 20, entidad.getUnidadesSinParte());
        JdbcSupport.setEntero(ps, i + 21, entidad.getAclaracionesPendientes());
    }

    @Override
    protected String columnasInsercion() {
        return "id_consolidado, id_ueb, fecha_inicio, fecha_fin, estado, motivo_parcial, id_usuario, creado_en, "
                + "total_huevos, total_huevos_rotos, total_gallinas_vivas, total_gallinas_muertas, "
                + "total_gallinas_enfermas, total_pienso_kg, total_carne, partes_nave_incluidos, "
                + "partes_nave_excluidos, partes_unidad_incluidos, partes_unidad_excluidos, "
                + "registros_carne_incluidos, naves_sin_parte, unidades_sin_parte, aclaraciones_pendientes, version";
    }

    @Override
    protected String marcadoresInsercion() {
        return "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?";
    }

    @Override
    protected String columnasActualizacion() {
        return "id_ueb = ?, fecha_inicio = ?, fecha_fin = ?, estado = ?, motivo_parcial = ?, "
                + "id_usuario = ?, creado_en = ?, total_huevos = ?, total_huevos_rotos = ?, "
                + "total_gallinas_vivas = ?, total_gallinas_muertas = ?, total_gallinas_enfermas = ?, "
                + "total_pienso_kg = ?, total_carne = ?, partes_nave_incluidos = ?, partes_nave_excluidos = ?, "
                + "partes_unidad_incluidos = ?, partes_unidad_excluidos = ?, registros_carne_incluidos = ?, "
                + "naves_sin_parte = ?, unidades_sin_parte = ?, aclaraciones_pendientes = ?, version = version + 1";
    }

    @Override
    protected int marcadoresActualizacion() {
        return 22;
    }

    @Override
    public List<ConsolidadoUEB> findByUEB(UUID idUEB) {
        return listarConFiltro("id_ueb = ?", List.of(idUEB), "fecha_inicio, creado_en");
    }

    @Override
    public void guardarRelaciones(UUID idConsolidado, UUID idUEB,
                                  List<UUID> partesNave, List<UUID> partesUnidad, List<UUID> carnes) {
        Connection c = abrir();
        boolean propia = !JdbcSupport.hayConexionActiva();
        boolean autocommitAnterior = true;
        try {
            if (propia) {
                autocommitAnterior = c.getAutoCommit();
                c.setAutoCommit(false);
            }
            eliminarRelaciones(c, "consolidado_parte_nave", idConsolidado);
            eliminarRelaciones(c, "consolidado_parte_unidad", idConsolidado);
            eliminarRelaciones(c, "consolidado_produccion_carne", idConsolidado);
            insertarRelaciones(c, "consolidado_parte_nave", "id_parte", idConsolidado, idUEB, partesNave);
            insertarRelaciones(c, "consolidado_parte_unidad", "id_parte_asociado", idConsolidado, idUEB, partesUnidad);
            insertarRelaciones(c, "consolidado_produccion_carne", "id_produccion", idConsolidado, idUEB, carnes);
            if (propia) {
                c.commit();
            }
        } catch (SQLException ex) {
            if (propia) {
                try {
                    c.rollback();
                } catch (SQLException rollback) {
                    ex.addSuppressed(rollback);
                }
            }
            throw new PersistenciaException("No se pudieron guardar las relaciones del consolidado: "
                    + JdbcConnectionManager.describirError(ex), ex);
        } finally {
            if (propia) {
                try {
                    c.setAutoCommit(autocommitAnterior);
                } catch (SQLException ignored) {
                    // La conexión se cierra a continuación.
                }
            }
            cerrar(c);
        }
    }

    private void eliminarRelaciones(Connection c, String tabla, UUID idConsolidado) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + tabla + " WHERE id_consolidado = ?")) {
            JdbcSupport.setUuid(ps, 1, idConsolidado);
            ps.executeUpdate();
        }
    }

    private void insertarRelaciones(Connection c, String tabla, String columnaOrigen,
                                    UUID idConsolidado, UUID idUEB, List<UUID> ids) throws SQLException {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO " + tabla
                + " (id_vinculo, id_consolidado, " + columnaOrigen + ", id_ueb_origen, version) "
                + "VALUES (?, ?, ?, ?, 1)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (UUID id : ids) {
                JdbcSupport.setUuid(ps, 1, UUID.randomUUID());
                JdbcSupport.setUuid(ps, 2, idConsolidado);
                JdbcSupport.setUuid(ps, 3, id);
                JdbcSupport.setUuid(ps, 4, idUEB);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    @Override
    public List<UUID> findPartesNave(UUID idConsolidado) {
        return listarIds("consolidado_parte_nave", "id_parte", idConsolidado);
    }

    @Override
    public List<UUID> findPartesUnidad(UUID idConsolidado) {
        return listarIds("consolidado_parte_unidad", "id_parte_asociado", idConsolidado);
    }

    @Override
    public List<UUID> findProduccionesCarne(UUID idConsolidado) {
        return listarIds("consolidado_produccion_carne", "id_produccion", idConsolidado);
    }

    private List<UUID> listarIds(String tabla, String columna, UUID idConsolidado) {
        Connection c = abrir();
        String sql = "SELECT " + columna + " FROM " + tabla
                + " WHERE id_consolidado = ? AND eliminado_logico = FALSE ORDER BY creado_en, id_vinculo";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            JdbcSupport.setUuid(ps, 1, idConsolidado);
            try (ResultSet rs = ps.executeQuery()) {
                List<UUID> ids = new ArrayList<>();
                while (rs.next()) {
                    ids.add(JdbcSupport.uuid(rs, columna));
                }
                return ids;
            }
        } catch (SQLException ex) {
            throw new PersistenciaException("No se pudo consultar " + tabla + ": "
                    + JdbcConnectionManager.describirError(ex), ex);
        } finally {
            cerrar(c);
        }
    }
}
