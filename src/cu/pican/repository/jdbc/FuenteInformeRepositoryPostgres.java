package cu.pican.repository.jdbc;

import cu.pican.model.FuenteInforme;
import cu.pican.model.TipoFuenteInforme;
import cu.pican.persistence.JdbcConnectionManager;
import cu.pican.persistence.JdbcRepositorySupport;
import cu.pican.persistence.JdbcSupport;
import cu.pican.repository.FuenteInformeRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public class FuenteInformeRepositoryPostgres extends JdbcRepositorySupport<FuenteInforme>
        implements FuenteInformeRepository {

    public FuenteInformeRepositoryPostgres(JdbcConnectionManager conexiones) {
        super(conexiones);
    }

    @Override
    protected String tabla() {
        return "fuente_informe";
    }

    @Override
    protected String columnaId() {
        return "id_fuente";
    }

    @Override
    protected UUID idDe(FuenteInforme entidad) {
        return entidad.getIdFuente();
    }

    @Override
    protected FuenteInforme mapear(ResultSet rs) throws SQLException {
        return new FuenteInforme(
                JdbcSupport.uuid(rs, "id_fuente"),
                JdbcSupport.uuid(rs, "id_informe"),
                TipoFuenteInforme.valueOf(rs.getString("tipo")),
                rs.getString("id_registro_origen"),
                rs.getString("descripcion"),
                rs.getString("fuente"),
                rs.getString("detalle_consulta"));
    }

    @Override
    protected void escribirInsercion(PreparedStatement ps, FuenteInforme entidad) throws SQLException {
        JdbcSupport.setUuid(ps, 1, entidad.getIdFuente());
        escribirDatos(ps, entidad, 2);
        ps.setLong(8, 1L);
    }

    @Override
    protected void escribirActualizacion(PreparedStatement ps, FuenteInforme entidad) throws SQLException {
        escribirDatos(ps, entidad, 1);
    }

    private void escribirDatos(PreparedStatement ps, FuenteInforme entidad, int i) throws SQLException {
        JdbcSupport.setUuid(ps, i, entidad.getIdInforme());
        JdbcSupport.setString(ps, i + 1, entidad.getTipo().name());
        JdbcSupport.setString(ps, i + 2, entidad.getIdRegistroOrigen());
        JdbcSupport.setString(ps, i + 3, entidad.getDescripcion());
        JdbcSupport.setString(ps, i + 4, entidad.getFuente());
        JdbcSupport.setString(ps, i + 5, entidad.getDetalleConsulta());
    }

    @Override
    protected String columnasInsercion() {
        return "id_fuente, id_informe, tipo, id_registro_origen, descripcion, fuente, detalle_consulta, version";
    }

    @Override
    protected String marcadoresInsercion() {
        return "?, ?, ?, ?, ?, ?, ?, ?";
    }

    @Override
    protected String columnasActualizacion() {
        return "id_informe = ?, tipo = ?, id_registro_origen = ?, descripcion = ?, fuente = ?, "
                + "detalle_consulta = ?, version = version + 1";
    }

    @Override
    protected int marcadoresActualizacion() {
        return 6;
    }

    @Override
    public List<FuenteInforme> findByIdInforme(UUID idInforme) {
        if (idInforme == null) {
            return List.of();
        }
        return listarConFiltro("id_informe = ?", List.of(idInforme), "creado_en");
    }
}
