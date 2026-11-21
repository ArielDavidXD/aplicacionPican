package cu.pican.repository.jdbc;

import cu.pican.model.ProductoDestino;
import cu.pican.persistence.JdbcConnectionManager;
import cu.pican.persistence.JdbcRepositorySupport;
import cu.pican.persistence.JdbcSupport;
import cu.pican.repository.ProductoDestinoRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ProductoDestinoRepositoryPostgres extends JdbcRepositorySupport<ProductoDestino>
        implements ProductoDestinoRepository {

    public ProductoDestinoRepositoryPostgres(JdbcConnectionManager conexiones) {
        super(conexiones);
    }

    @Override
    protected String tabla() {
        return "producto_destino";
    }

    @Override
    protected String columnaId() {
        return "id_relacion";
    }

    @Override
    protected UUID idDe(ProductoDestino entidad) {
        return entidad.getIdRelacion();
    }

    @Override
    protected ProductoDestino mapear(ResultSet rs) throws SQLException {
        ProductoDestino relacion = new ProductoDestino();
        relacion.setIdRelacion(JdbcSupport.uuid(rs, "id_relacion"));
        relacion.setIdProducto(JdbcSupport.uuid(rs, "id_producto"));
        relacion.setIdDestino(JdbcSupport.uuid(rs, "id_destino"));
        relacion.setActiva(rs.getBoolean("activa"));
        return relacion;
    }

    @Override
    protected void escribirInsercion(PreparedStatement ps, ProductoDestino entidad) throws SQLException {
        JdbcSupport.setUuid(ps, 1, entidad.getIdRelacion());
        JdbcSupport.setUuid(ps, 2, entidad.getIdProducto());
        JdbcSupport.setUuid(ps, 3, entidad.getIdDestino());
        JdbcSupport.setBoolean(ps, 4, entidad.isActiva());
        ps.setLong(5, 1L);
    }

    @Override
    protected void escribirActualizacion(PreparedStatement ps, ProductoDestino entidad) throws SQLException {
        JdbcSupport.setUuid(ps, 1, entidad.getIdProducto());
        JdbcSupport.setUuid(ps, 2, entidad.getIdDestino());
        JdbcSupport.setBoolean(ps, 3, entidad.isActiva());
    }

    @Override
    protected String columnasInsercion() {
        return "id_relacion, id_producto, id_destino, activa, version";
    }

    @Override
    protected String marcadoresInsercion() {
        return "?, ?, ?, ?, ?";
    }

    @Override
    protected String columnasActualizacion() {
        return "id_producto = ?, id_destino = ?, activa = ?, version = version + 1";
    }

    @Override
    protected int marcadoresActualizacion() {
        return 3;
    }

    @Override
    public List<ProductoDestino> findByProducto(UUID idProducto) {
        return listarConFiltro("id_producto = ?", List.of(idProducto), "creado_en");
    }

    @Override
    public List<ProductoDestino> findByDestino(UUID idDestino) {
        return listarConFiltro("id_destino = ?", List.of(idDestino), "creado_en");
    }

    @Override
    public Optional<ProductoDestino> findByProductoAndDestino(UUID idProducto, UUID idDestino) {
        return listarConFiltro("id_producto = ? AND id_destino = ?",
                List.of(idProducto, idDestino), null).stream().findFirst();
    }
}
