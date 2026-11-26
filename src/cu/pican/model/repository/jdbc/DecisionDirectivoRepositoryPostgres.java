package cu.pican.model.repository.jdbc;

import cu.pican.model.DecisionDirectivo;
import cu.pican.model.persistence.JdbcConnectionManager;
import cu.pican.model.persistence.JdbcRepositorySupport;
import cu.pican.model.persistence.JdbcSupport;
import cu.pican.model.repository.DecisionDirectivoRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public class DecisionDirectivoRepositoryPostgres extends JdbcRepositorySupport<DecisionDirectivo>
        implements DecisionDirectivoRepository {

    public DecisionDirectivoRepositoryPostgres(JdbcConnectionManager conexiones) {
        super(conexiones);
    }

    @Override
    protected String tabla() {
        return "decision_directivo";
    }

    @Override
    protected String columnaId() {
        return "id_decision";
    }

    @Override
    protected UUID idDe(DecisionDirectivo entidad) {
        return entidad.getIdDecision();
    }

    @Override
    protected DecisionDirectivo mapear(ResultSet rs) throws SQLException {
        return new DecisionDirectivo(
                JdbcSupport.uuid(rs, "id_decision"),
                JdbcSupport.uuid(rs, "id_informe"),
                rs.getString("nombre_directivo"),
                rs.getString("decision"),
                JdbcSupport.fecha(rs, "fecha_decision"),
                rs.getString("observacion"),
                JdbcSupport.fechaHora(rs, "fecha_registro"),
                JdbcSupport.entero(rs, "numero_decision"));
    }

    @Override
    protected void escribirInsercion(PreparedStatement ps, DecisionDirectivo entidad) throws SQLException {
        JdbcSupport.setUuid(ps, 1, entidad.getIdDecision());
        escribirDatos(ps, entidad, 2);
        ps.setLong(9, 1L);
    }

    @Override
    protected void escribirActualizacion(PreparedStatement ps, DecisionDirectivo entidad) throws SQLException {
        escribirDatos(ps, entidad, 1);
    }

    private void escribirDatos(PreparedStatement ps, DecisionDirectivo entidad, int i) throws SQLException {
        JdbcSupport.setUuid(ps, i, entidad.getIdInforme());
        JdbcSupport.setString(ps, i + 1, entidad.getNombreDirectivo());
        JdbcSupport.setString(ps, i + 2, entidad.getDecision());
        JdbcSupport.setFecha(ps, i + 3, entidad.getFechaDecision());
        JdbcSupport.setString(ps, i + 4, entidad.getObservacion());
        JdbcSupport.setFechaHora(ps, i + 5, entidad.getFechaRegistro());
        JdbcSupport.setEntero(ps, i + 6, entidad.getNumeroDecision());
    }

    @Override
    protected String columnasInsercion() {
        return "id_decision, id_informe, nombre_directivo, decision, fecha_decision, observacion, "
                + "fecha_registro, numero_decision, version";
    }

    @Override
    protected String marcadoresInsercion() {
        return "?, ?, ?, ?, ?, ?, ?, ?, ?";
    }

    @Override
    protected String columnasActualizacion() {
        return "id_informe = ?, nombre_directivo = ?, decision = ?, fecha_decision = ?, observacion = ?, "
                + "fecha_registro = ?, numero_decision = ?, version = version + 1";
    }

    @Override
    protected int marcadoresActualizacion() {
        return 7;
    }

    @Override
    public List<DecisionDirectivo> findByIdInforme(UUID idInforme) {
        if (idInforme == null) {
            return List.of();
        }
        return listarConFiltro("id_informe = ?", List.of(idInforme), "numero_decision, creado_en");
    }
}
