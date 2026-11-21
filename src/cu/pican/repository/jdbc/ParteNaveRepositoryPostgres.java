package cu.pican.repository.jdbc;

import cu.pican.model.ParteNave;
import cu.pican.persistence.JdbcConnectionManager;
import cu.pican.persistence.JdbcRepositorySupport;
import cu.pican.persistence.JdbcSupport;
import cu.pican.repository.ParteNaveRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class ParteNaveRepositoryPostgres extends JdbcRepositorySupport<ParteNave>
        implements ParteNaveRepository {

    public ParteNaveRepositoryPostgres(JdbcConnectionManager conexiones) {
        super(conexiones);
    }

    @Override
    protected String tabla() {
        return "parte_nave";
    }

    @Override
    protected String columnaId() {
        return "id_parte";
    }

    @Override
    protected UUID idDe(ParteNave entidad) {
        return entidad.getIdParte();
    }

    @Override
    protected ParteNave mapear(ResultSet rs) throws SQLException {
        ParteNave parte = new ParteNave();
        parte.setIdParte(JdbcSupport.uuid(rs, "id_parte"));
        parte.setIdNave(JdbcSupport.uuid(rs, "id_nave"));
        parte.setFechaProduccion(JdbcSupport.fecha(rs, "fecha_produccion"));
        parte.setGallinasVivas(JdbcSupport.entero(rs, "gallinas_vivas"));
        parte.setGallinasMuertas(JdbcSupport.entero(rs, "gallinas_muertas"));
        parte.setGallinasEnfermas(JdbcSupport.entero(rs, "gallinas_enfermas"));
        parte.setHuevosProducidos(JdbcSupport.entero(rs, "huevos_producidos"));
        parte.setHuevosRotos(JdbcSupport.entero(rs, "huevos_rotos"));
        parte.setPiensoKg(JdbcSupport.decimal(rs, "pienso_kg"));
        parte.setObservaciones(rs.getString("observaciones"));
        parte.setEstado(rs.getString("estado"));
        parte.setIdUsuario(JdbcSupport.uuid(rs, "id_usuario"));
        parte.setCreadoEn(JdbcSupport.fechaHora(rs, "creado_en"));
        parte.setVersion(JdbcSupport.largo(rs, "version"));
        return parte;
    }

    @Override
    protected void escribirInsercion(PreparedStatement ps, ParteNave entidad) throws SQLException {
        JdbcSupport.setUuid(ps, 1, entidad.getIdParte());
        escribirDatos(ps, entidad, 2);
        ps.setLong(14, 1L);
    }

    @Override
    protected void escribirActualizacion(PreparedStatement ps, ParteNave entidad) throws SQLException {
        escribirDatos(ps, entidad, 1);
    }

    private void escribirDatos(PreparedStatement ps, ParteNave entidad, int i) throws SQLException {
        JdbcSupport.setUuid(ps, i, entidad.getIdNave());
        JdbcSupport.setFecha(ps, i + 1, entidad.getFechaProduccion());
        JdbcSupport.setEntero(ps, i + 2, entidad.getGallinasVivas());
        JdbcSupport.setEntero(ps, i + 3, entidad.getGallinasMuertas());
        JdbcSupport.setEntero(ps, i + 4, entidad.getGallinasEnfermas());
        JdbcSupport.setEntero(ps, i + 5, entidad.getHuevosProducidos());
        JdbcSupport.setEntero(ps, i + 6, entidad.getHuevosRotos());
        JdbcSupport.setDecimal(ps, i + 7, entidad.getPiensoKg());
        JdbcSupport.setString(ps, i + 8, entidad.getObservaciones());
        JdbcSupport.setString(ps, i + 9, entidad.getEstado());
        JdbcSupport.setUuid(ps, i + 10, entidad.getIdUsuario());
        JdbcSupport.setFechaHora(ps, i + 11,
                entidad.getCreadoEn() == null ? LocalDateTime.now() : entidad.getCreadoEn());
    }

    @Override
    protected String columnasInsercion() {
        return "id_parte, id_nave, fecha_produccion, gallinas_vivas, gallinas_muertas, "
                + "gallinas_enfermas, huevos_producidos, huevos_rotos, pienso_kg, observaciones, "
                + "estado, id_usuario, creado_en, version";
    }

    @Override
    protected String marcadoresInsercion() {
        return "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?";
    }

    @Override
    protected String columnasActualizacion() {
        return "id_nave = ?, fecha_produccion = ?, gallinas_vivas = ?, gallinas_muertas = ?, "
                + "gallinas_enfermas = ?, huevos_producidos = ?, huevos_rotos = ?, pienso_kg = ?, "
                + "observaciones = ?, estado = ?, id_usuario = ?, creado_en = ?, version = version + 1";
    }

    @Override
    protected int marcadoresActualizacion() {
        return 12;
    }

    @Override
    public List<ParteNave> findByNave(UUID idNave) {
        return listarConFiltro("id_nave = ?", List.of(idNave), "fecha_produccion, creado_en");
    }

    @Override
    public List<ParteNave> findByFecha(LocalDate fechaProduccion) {
        return listarConFiltro("fecha_produccion = ?", List.of(fechaProduccion), "creado_en");
    }
}
