package cu.pican.model.repository.jdbc;

import cu.pican.model.Producto;
import cu.pican.model.persistence.JdbcConnectionManager;
import cu.pican.model.persistence.JdbcRepositorySupport;
import cu.pican.model.persistence.JdbcSupport;
import cu.pican.model.repository.ProductoRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public class ProductoRepositoryPostgres extends JdbcRepositorySupport<Producto> implements ProductoRepository {

    public ProductoRepositoryPostgres(JdbcConnectionManager conexiones) {
        super(conexiones);
    }

    @Override
    protected String tabla() {
        return "producto";
    }

    @Override
    protected String columnaId() {
        return "id_producto";
    }

    @Override
    protected UUID idDe(Producto entidad) {
        return entidad.getIdProducto();
    }

    @Override
    protected Producto mapear(ResultSet rs) throws SQLException {
        Producto producto = new Producto();
        producto.setIdProducto(JdbcSupport.uuid(rs, "id_producto"));
        producto.setCodigo(rs.getString("codigo"));
        producto.setNombre(rs.getString("nombre"));
        producto.setActiva(rs.getBoolean("activa"));
        producto.setUnidadMedida(rs.getString("unidad_medida"));
        return producto;
    }

    @Override
    protected void escribirInsercion(PreparedStatement ps, Producto entidad) throws SQLException {
        JdbcSupport.setUuid(ps, 1, entidad.getIdProducto());
        JdbcSupport.setString(ps, 2, entidad.getCodigo());
        JdbcSupport.setString(ps, 3, entidad.getNombre());
        JdbcSupport.setBoolean(ps, 4, entidad.isActiva());
        JdbcSupport.setString(ps, 5, entidad.getUnidadMedida());
        ps.setLong(6, 1L);
    }

    @Override
    protected void escribirActualizacion(PreparedStatement ps, Producto entidad) throws SQLException {
        JdbcSupport.setString(ps, 1, entidad.getCodigo());
        JdbcSupport.setString(ps, 2, entidad.getNombre());
        JdbcSupport.setBoolean(ps, 3, entidad.isActiva());
        JdbcSupport.setString(ps, 4, entidad.getUnidadMedida());
    }

    @Override
    protected String columnasInsercion() {
        return "id_producto, codigo, nombre, activa, unidad_medida, version";
    }

    @Override
    protected String marcadoresInsercion() {
        return "?, ?, ?, ?, ?, ?";
    }

    @Override
    protected String columnasActualizacion() {
        return "codigo = ?, nombre = ?, activa = ?, unidad_medida = ?, version = version + 1";
    }

    @Override
    protected int marcadoresActualizacion() {
        return 4;
    }

    @Override
    public Optional<Producto> findByCodigo(String codigo) {
        if (codigo == null || codigo.trim().isEmpty()) {
            return Optional.empty();
        }
        return buscarUno("lower(btrim(codigo)) = lower(btrim(?))", codigo);
    }
}
