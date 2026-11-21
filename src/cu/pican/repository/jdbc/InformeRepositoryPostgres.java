package cu.pican.repository.jdbc;

import cu.pican.model.Informe;
import cu.pican.persistence.JdbcConnectionManager;
import cu.pican.persistence.JdbcRepositorySupport;
import cu.pican.persistence.JdbcSupport;
import cu.pican.repository.InformeRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class InformeRepositoryPostgres extends JdbcRepositorySupport<Informe>
        implements InformeRepository {

    public InformeRepositoryPostgres(JdbcConnectionManager conexiones) {
        super(conexiones);
    }

    @Override
    protected String tabla() {
        return "informe";
    }

    @Override
    protected String columnaId() {
        return "id_informe";
    }

    @Override
    protected UUID idDe(Informe entidad) {
        return entidad.getIdInforme();
    }

    @Override
    protected Informe mapear(ResultSet rs) throws SQLException {
        Informe informe = new Informe();
        informe.setIdInforme(JdbcSupport.uuid(rs, "id_informe"));
        informe.setIdInformeRaiz(JdbcSupport.uuid(rs, "id_informe_raiz"));
        informe.setIdInformeAnterior(JdbcSupport.uuid(rs, "id_informe_anterior"));
        informe.setNumeroInforme(rs.getString("numero_informe"));
        informe.setTitulo(rs.getString("titulo"));
        informe.setPeriodoDesde(JdbcSupport.fecha(rs, "periodo_desde"));
        informe.setPeriodoHasta(JdbcSupport.fecha(rs, "periodo_hasta"));
        informe.setFechaElaboracion(JdbcSupport.fecha(rs, "fecha_elaboracion"));
        informe.setEspecialistaResponsable(rs.getString("especialista_responsable"));
        informe.setIdUsuario(JdbcSupport.uuid(rs, "id_usuario"));
        informe.setContenidoResumen(rs.getString("contenido_resumen"));
        informe.setEstado(rs.getString("estado"));
        informe.setFechaCreacion(JdbcSupport.fechaHora(rs, "creado_en"));
        informe.setVersion(JdbcSupport.largo(rs, "version"));
        informe.setNumeroRevision(JdbcSupport.entero(rs, "numero_revision"));
        return informe;
    }

    @Override
    protected void escribirInsercion(PreparedStatement ps, Informe entidad) throws SQLException {
        JdbcSupport.setUuid(ps, 1, entidad.getIdInforme());
        escribirDatos(ps, entidad, 2);
        ps.setLong(15, 1L);
    }

    @Override
    protected void escribirActualizacion(PreparedStatement ps, Informe entidad) throws SQLException {
        escribirDatos(ps, entidad, 1);
    }

    private void escribirDatos(PreparedStatement ps, Informe entidad, int i) throws SQLException {
        JdbcSupport.setUuid(ps, i, entidad.getIdInformeRaiz());
        JdbcSupport.setUuid(ps, i + 1, entidad.getIdInformeAnterior());
        JdbcSupport.setString(ps, i + 2, entidad.getNumeroInforme());
        JdbcSupport.setString(ps, i + 3, entidad.getTitulo());
        JdbcSupport.setFecha(ps, i + 4, entidad.getPeriodoDesde());
        JdbcSupport.setFecha(ps, i + 5, entidad.getPeriodoHasta());
        JdbcSupport.setFecha(ps, i + 6, entidad.getFechaElaboracion());
        JdbcSupport.setString(ps, i + 7, entidad.getEspecialistaResponsable());
        JdbcSupport.setUuid(ps, i + 8, entidad.getIdUsuario());
        JdbcSupport.setString(ps, i + 9, entidad.getContenidoResumen());
        JdbcSupport.setString(ps, i + 10, entidad.getEstado());
        JdbcSupport.setEntero(ps, i + 11, entidad.getNumeroRevision());
        JdbcSupport.setFechaHora(ps, i + 12,
                entidad.getFechaCreacion() == null ? LocalDateTime.now() : entidad.getFechaCreacion());
    }

    @Override
    protected String columnasInsercion() {
        return "id_informe, id_informe_raiz, id_informe_anterior, numero_informe, titulo, "
                + "periodo_desde, periodo_hasta, fecha_elaboracion, especialista_responsable, "
                + "id_usuario, contenido_resumen, estado, numero_revision, creado_en, version";
    }

    @Override
    protected String marcadoresInsercion() {
        return "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?";
    }

    @Override
    protected String columnasActualizacion() {
        return "id_informe_raiz = ?, id_informe_anterior = ?, numero_informe = ?, titulo = ?, "
                + "periodo_desde = ?, periodo_hasta = ?, fecha_elaboracion = ?, "
                + "especialista_responsable = ?, id_usuario = ?, contenido_resumen = ?, estado = ?, "
                + "numero_revision = ?, creado_en = ?, version = version + 1";
    }

    @Override
    protected int marcadoresActualizacion() {
        return 13;
    }

    @Override
    public List<Informe> findByEstado(String estado) {
        if (estado == null) {
            return List.of();
        }
        return listarConFiltro("estado = ?", List.of(estado), "creado_en");
    }

    @Override
    public List<Informe> findByIdInformeRaiz(UUID idInformeRaiz) {
        if (idInformeRaiz == null) {
            return List.of();
        }
        return listarConFiltro("id_informe_raiz = ?", List.of(idInformeRaiz), "numero_revision, creado_en");
    }

    @Override
    public Optional<Informe> findById(UUID id) {
        return super.findById(id);
    }
}
