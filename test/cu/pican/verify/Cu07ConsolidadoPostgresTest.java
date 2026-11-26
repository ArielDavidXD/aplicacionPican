package cu.pican.verify;

import cu.pican.controller.app.AppContext;
import cu.pican.controller.service.ConsolidadoUEBService;
import cu.pican.controller.service.ValidacionException;
import cu.pican.model.AclaracionOperativa;
import cu.pican.model.ConsolidadoUEB;
import cu.pican.model.Nave;
import cu.pican.model.ParteNave;
import cu.pican.model.ParteUnidadAsociada;
import cu.pican.model.ProduccionCarne;
import cu.pican.model.UEB;
import cu.pican.model.UnidadAsociada;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class Cu07ConsolidadoPostgresTest {

    private static final String CLAVE_ADMIN = "Cu07Admin123!";
    private static final LocalDate FECHA = LocalDate.of(2026, 10, 12);

    public static void main(String[] args) {
        int salida;
        try {
            configurar();
            new Cu07ConsolidadoPostgresTest().ejecutar();
            salida = 0;
        } catch (Throwable fallo) {
            fallo.printStackTrace(System.out);
            salida = 1;
        }
        System.exit(salida);
    }

    private static void configurar() {
        String url = requerir("pican.test.url");
        String base = url.split("\\?", 2)[0];
        base = base.substring(base.lastIndexOf('/') + 1);
        if (!base.toLowerCase(Locale.ROOT).contains("test")) {
            throw new IllegalArgumentException("La base no es exclusiva de pruebas: " + base);
        }
        System.setProperty("pican.persistence", "postgresql");
        System.setProperty("pican.db.url", url);
        System.setProperty("pican.db.user", requerir("pican.test.user"));
        System.setProperty("pican.db.password", requerir("pican.test.password"));
        System.setProperty("pican.db.name", base);
        System.setProperty("pican.db.migrate", "true");
        System.setProperty("pican.admin.password", CLAVE_ADMIN);
    }

    private void ejecutar() throws Exception {
        AppContext contexto = new AppContext();
        contexto.getAuthService().login("admin", CLAVE_ADMIN);
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        UUID idUEB = UUID.randomUUID();
        UUID idNave = UUID.randomUUID();
        UUID idNaveSinParte = UUID.randomUUID();
        UUID idUnidad = UUID.randomUUID();
        contexto.getUebService().registrar(new UEB(idUEB, "UEB CU-07", "CU07-" + sufijo, true));
        contexto.getNaveService().registrar(new Nave(idNave, idUEB, "Nave oficial", "CU07-N1-" + sufijo, true));
        contexto.getNaveService().registrar(new Nave(idNaveSinParte, idUEB, "Nave sin parte", "CU07-N2-" + sufijo, true));
        contexto.getUnidadAsociadaService().registrar(new UnidadAsociada(
                idUnidad, "Unidad CU-07", "CU07-UA-" + sufijo, true));

        verificarBorradoresYRegistro(contexto, idNave);
        verificarAvisoPersistido(contexto, idNave);
        verificarCarne(contexto, idUEB);
        verificarConstraints(contexto, idUEB, idNave);

        ParteNave oficial = parteOficial(idNave, 1000, 5000);
        contexto.getParteNaveService().registrar(oficial);

        ParteUnidadAsociada unidad = new ParteUnidadAsociada();
        unidad.setIdParteAsociado(UUID.randomUUID());
        unidad.setIdUnidad(idUnidad);
        unidad.setIdUEB(idUEB);
        unidad.setFechaProduccion(FECHA);
        unidad.setEstado(ParteUnidadAsociada.ESTADO_REGISTRADO);
        unidad.setReferenciaPapel("PAPEL-CU07");
        contexto.getParteUnidadAsociadaService().registrar(unidad);

        ProduccionCarne carne = carne(idUEB, "2.500");
        contexto.getProduccionCarneService().registrar(carne);

        UUID idConsolidado = UUID.randomUUID();
        ConsolidadoUEB consolidado = consolidado(idConsolidado, idUEB, null);
        contexto.getConsolidadoUEBService().registrar(consolidado);
        verificar(ConsolidadoUEB.ESTADO_COMPLETO.equals(consolidado.getEstado()), "El consolidado completo no quedó COMPLETO");
        verificar(consolidado.getTotalHuevos().compareTo(new BigDecimal("1000")) == 0, "Total de huevos incorrecto");
        verificar(consolidado.getTotalHuevosRotos().compareTo(new BigDecimal("10")) == 0, "Total de huevos rotos incorrecto");
        verificar(consolidado.getTotalGallinasVivas() == 5000, "Total de gallinas vivas incorrecto");
        verificar(consolidado.getTotalPiensoKg().compareTo(new BigDecimal("12.50")) == 0, "Total de pienso incorrecto");
        verificar(consolidado.getTotalCarne().compareTo(new BigDecimal("2.500")) == 0, "Total de carne incorrecto");
        verificar(consolidado.getPartesNaveIncluidos() == 1, "No vinculó la parte de nave");
        verificar(consolidado.getPartesUnidadIncluidos() == 1, "No vinculó la parte de unidad");
        verificar(consolidado.getRegistrosCarneIncluidos() == 1, "No vinculó la carne");
        verificar(consolidado.getNavesSinParte() == 1, "La cobertura de naves sin parte es incorrecta");
        ConsolidadoUEBService.DetalleConsolidado detalle = contexto.getConsolidadoUEBService()
                .obtenerDetalle(idConsolidado).orElseThrow();
        verificar(detalle.partesNave().equals(List.of(oficial.getIdParte())), "El vínculo persistido de nave no coincide");
        verificar(detalle.partesUnidad().equals(List.of(unidad.getIdParteAsociado())), "El vínculo persistido de unidad no coincide");
        verificar(detalle.produccionesCarne().equals(List.of(carne.getIdProduccion())), "El vínculo persistido de carne no coincide");

        ParteNave borrador = new ParteNave();
        borrador.setIdParte(UUID.randomUUID());
        borrador.setIdNave(idNaveSinParte);
        borrador.setFechaProduccion(FECHA);
        contexto.getParteNaveService().guardarBorrador(borrador);
        esperarValidacion(() -> contexto.getConsolidadoUEBService().actualizar(consolidado),
                "El consolidado parcial debe exigir motivo");
        consolidado.setMotivoParcial("Quedó un borrador pendiente de oficializar");
        contexto.getConsolidadoUEBService().actualizar(consolidado);
        verificar(ConsolidadoUEB.ESTADO_PARCIAL.equals(consolidado.getEstado()), "El consolidado con borrador no quedó PARCIAL");
        verificar(consolidado.getPartesNaveExcluidos() == 1, "El borrador no quedó excluido");
        verificar(consolidado.getTotalHuevos().compareTo(new BigDecimal("1000")) == 0, "El borrador alteró el total");

        AclaracionOperativa aclaracion = contexto.getConsolidadoUEBService().solicitarAclaracion(
                AclaracionOperativa.PARTE_NAVE, oficial.getIdParte(), "Revisar cifra del papel");
        esperarValidacion(() -> contexto.getConsolidadoUEBService().actualizar(consolidado),
                "Una aclaración pendiente debe bloquear la consolidación");
        contexto.getConsolidadoUEBService().resolverAclaracion(aclaracion.getIdAclaracion(), "Cifra cotejada y correcta");
        contexto.getConsolidadoUEBService().actualizar(consolidado);
        verificar(AclaracionOperativa.RESUELTA.equals(contexto.getConsolidadoUEBService()
                .listarAclaraciones(AclaracionOperativa.PARTE_NAVE, oficial.getIdParte()).get(0).getEstado()),
                "La aclaración no persistió como resuelta");

        AppContext reconstruido = new AppContext();
        reconstruido.getAuthService().login("admin", CLAVE_ADMIN);
        ConsolidadoUEB persistido = reconstruido.getConsolidadoUEBService().buscarPorId(idConsolidado).orElseThrow();
        verificar(persistido.getTotalHuevos().compareTo(new BigDecimal("1000")) == 0, "El total no sobrevivió a reconstruir AppContext");
        verificar(persistido.getPartesNaveExcluidos() == 1, "La exclusión no sobrevivió a reconstruir AppContext");
        verificar(reconstruido.getConsolidadoUEBService().obtenerDetalle(idConsolidado).orElseThrow()
                .partesUnidad().size() == 1, "Los vínculos no sobrevivieron a reconstruir AppContext");
        System.out.println("[RESUMEN] CU-05, CU-06 y CU-07 verificados en PostgreSQL real.");
    }

    private static void verificarBorradoresYRegistro(AppContext contexto, UUID idNave) {
        ParteNave borrador = new ParteNave();
        borrador.setIdParte(UUID.randomUUID());
        borrador.setIdNave(idNave);
        borrador.setFechaProduccion(FECHA.minusDays(1));
        contexto.getParteNaveService().guardarBorrador(borrador);
        ParteNave leido = contexto.getParteNaveService().buscarPorId(borrador.getIdParte()).orElseThrow();
        verificar(ParteNave.ESTADO_BORRADOR.equals(leido.getEstado()), "El borrador no persistió como BORRADOR");
        verificar(leido.getHuevosProducidos() == null, "El borrador parcial recibió valores inventados");

        ParteNave incompleto = new ParteNave();
        incompleto.setIdParte(UUID.randomUUID());
        incompleto.setIdNave(idNave);
        incompleto.setFechaProduccion(FECHA.minusDays(1));
        esperarValidacion(() -> contexto.getParteNaveService().registrar(incompleto),
                "Un registro oficial incompleto debe rechazarse");
    }

    private static void verificarAvisoPersistido(AppContext contexto, UUID idNave) {
        ParteNave aviso = parteOficial(idNave, 200, 100);
        aviso.setFechaProduccion(FECHA);
        aviso.setAdvertenciaRevisada(false);
        esperarValidacion(() -> contexto.getParteNaveService().registrar(aviso),
                "El aviso huevos/gallinas debe confirmarse");
        aviso.setAdvertenciaRevisada(true);
        contexto.getParteNaveService().registrar(aviso);
        verificar(contexto.getParteNaveService().buscarPorId(aviso.getIdParte()).orElseThrow().isAdvertenciaRevisada(),
                "La revisión del aviso no persistió");
    }

    private static void verificarCarne(AppContext contexto, UUID idUEB) {
        esperarValidacion(() -> contexto.getProduccionCarneService().registrar(carne(idUEB, "0")),
                "La carne en cero debe rechazarse");
        esperarValidacion(() -> contexto.getProduccionCarneService().registrar(carne(idUEB, "-1")),
                "La carne negativa debe rechazarse");
        ProduccionCarne futura = carne(idUEB, "1");
        futura.setFechaProduccion(LocalDate.now().plusDays(1));
        esperarValidacion(() -> contexto.getProduccionCarneService().registrar(futura),
                "La carne futura debe rechazarse");
    }

    private static void verificarConstraints(AppContext contexto, UUID idUEB, UUID idNave) throws Exception {
        try (Connection c = contexto.getConexiones().obtenerConexion()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO produccion_carne (id_produccion, id_ueb, fecha_produccion, cantidad_toneladas, version) "
                            + "VALUES (?, ?, ?, ?, 1)")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, idUEB);
                ps.setDate(3, java.sql.Date.valueOf(FECHA));
                ps.setBigDecimal(4, BigDecimal.ZERO);
                try {
                    ps.executeUpdate();
                    throw new AssertionError("PostgreSQL aceptó carne en cero sin pasar por el servicio");
                } catch (SQLException esperado) {
                    verificar(esperado.getMessage().contains("ck_produccion_carne_positiva"),
                            "Falló una restricción distinta de la carne positiva: " + esperado.getMessage());
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO parte_nave (id_parte, id_nave, fecha_produccion, estado, version) VALUES (?, ?, ?, ?, 1)")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, idNave);
                ps.setDate(3, java.sql.Date.valueOf(FECHA.minusDays(1)));
                ps.setString(4, ParteNave.ESTADO_REGISTRADO);
                try {
                    ps.executeUpdate();
                    throw new AssertionError("PostgreSQL aceptó un parte oficial incompleto");
                } catch (SQLException esperado) {
                    verificar(esperado.getMessage().contains("ck_parte_nave_registro_completo"),
                            "Falló una restricción distinta del parte completo: " + esperado.getMessage());
                }
            }
        }
    }

    private static ParteNave parteOficial(UUID idNave, int huevos, int vivas) {
        ParteNave parte = new ParteNave();
        parte.setIdParte(UUID.randomUUID());
        parte.setIdNave(idNave);
        parte.setFechaProduccion(FECHA);
        parte.setGallinasVivas(vivas);
        parte.setGallinasMuertas(1);
        parte.setGallinasEnfermas(2);
        parte.setHuevosProducidos(huevos);
        parte.setHuevosRotos(10);
        parte.setPiensoKg(new BigDecimal("12.50"));
        return parte;
    }

    private static ProduccionCarne carne(UUID idUEB, String cantidad) {
        ProduccionCarne carne = new ProduccionCarne();
        carne.setIdProduccion(UUID.randomUUID());
        carne.setIdUEB(idUEB);
        carne.setFechaProduccion(FECHA);
        carne.setCantidadToneladas(new BigDecimal(cantidad));
        return carne;
    }

    private static ConsolidadoUEB consolidado(UUID id, UUID idUEB, String motivo) {
        ConsolidadoUEB consolidado = new ConsolidadoUEB();
        consolidado.setIdConsolidado(id);
        consolidado.setIdUEB(idUEB);
        consolidado.setFechaInicio(FECHA.minusDays(2));
        consolidado.setFechaFin(FECHA.plusDays(2));
        consolidado.setMotivoParcial(motivo);
        return consolidado;
    }

    private static void esperarValidacion(Runnable accion, String mensaje) {
        try {
            accion.run();
            throw new AssertionError("Se esperaba ValidacionException: " + mensaje);
        } catch (ValidacionException esperado) {
            verificar(esperado.getMessage() != null && !esperado.getMessage().isBlank(), "Validación sin mensaje");
        }
    }

    private static String requerir(String propiedad) {
        String valor = System.getProperty(propiedad);
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Falta -D" + propiedad);
        }
        return valor;
    }

    private static void verificar(boolean condicion, String mensaje) {
        if (!condicion) throw new AssertionError(mensaje);
        System.out.println("  OK - " + mensaje);
    }
}
