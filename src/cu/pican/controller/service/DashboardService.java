package cu.pican.controller.service;

import cu.pican.model.BackupAuditoria;
import cu.pican.model.ConsolidadoUEB;
import cu.pican.model.Destino;
import cu.pican.model.Incidencia;
import cu.pican.model.IndicadorHistorico;
import cu.pican.model.Informe;
import cu.pican.model.MetaEstrategica;
import cu.pican.model.Nave;
import cu.pican.model.ParteNave;
import cu.pican.model.ProduccionCarne;
import cu.pican.model.TablaHistorica;
import cu.pican.model.UEB;
import cu.pican.model.Usuario;
import cu.pican.model.ValeEntrega;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class DashboardService extends ServicioAutorizado {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final int LIMITE_DETALLE = 50;

    private final AnalisisService analisisService;
    private final ParteNaveService parteNaveService;
    private final ProduccionCarneService produccionCarneService;
    private final ConsolidadoUEBService consolidadoService;
    private final ValeEntregaService valeEntregaService;
    private final IncidenciaService incidenciaService;
    private final InformeService informeService;
    private final UsuarioService usuarioService;
    private final UEBService uebService;
    private final NaveService naveService;
    private final ProductoService productoService;
    private final DestinoService destinoService;
    private final BackupService backupService;

    public DashboardService(AnalisisService analisisService,
                            ParteNaveService parteNaveService,
                            ProduccionCarneService produccionCarneService,
                            ConsolidadoUEBService consolidadoService,
                            ValeEntregaService valeEntregaService,
                            IncidenciaService incidenciaService,
                            InformeService informeService,
                            UsuarioService usuarioService,
                            UEBService uebService,
                            NaveService naveService,
                            ProductoService productoService,
                            DestinoService destinoService,
                            BackupService backupService) {
        this.analisisService = analisisService;
        this.parteNaveService = parteNaveService;
        this.produccionCarneService = produccionCarneService;
        this.consolidadoService = consolidadoService;
        this.valeEntregaService = valeEntregaService;
        this.incidenciaService = incidenciaService;
        this.informeService = informeService;
        this.usuarioService = usuarioService;
        this.uebService = uebService;
        this.naveService = naveService;
        this.productoService = productoService;
        this.destinoService = destinoService;
        this.backupService = backupService;
    }

    public Resumen resumir(int dias) {
        if (dias < 0) {
            throw new ValidacionException("El período del Dashboard no puede ser negativo.");
        }
        autorizacion().requerirLecturaCatalogo();
        LocalDate hoy = LocalDate.now();
        LocalDate desde = dias == 0 ? null : hoy.minusDays(dias - 1L);
        if (autorizacion().puedeAcceder(CasoUso.CU_14)) {
            return resumenEstadistica(desde, hoy);
        }
        if (autorizacion().puedeAcceder(CasoUso.CU_05)) {
            return resumenJefe(desde, hoy);
        }
        if (autorizacion().puedeAcceder(CasoUso.CU_16)) {
            return resumenAdministrador();
        }
        if (autorizacion().puedeAcceder(CasoUso.CU_01, CasoUso.CU_02, CasoUso.CU_03)) {
            return resumenProduccion();
        }
        throw new AutorizacionException("No hay un resumen de Dashboard definido para el rol de esta sesión.");
    }

    private Resumen resumenJefe(LocalDate desde, LocalDate hoy) {
        List<ParteNave> partes = parteNaveService.listarTodos();
        List<ParteNave> registrados = partes.stream()
                .filter(p -> ParteNave.ESTADO_REGISTRADO.equals(p.getEstado()))
                .filter(p -> enPeriodo(p.getFechaProduccion(), desde, hoy))
                .toList();
        List<ProduccionCarne> carnes = produccionCarneService.listarTodas().stream()
                .filter(p -> enPeriodo(p.getFechaProduccion(), desde, hoy))
                .toList();
        List<ValeEntrega> vales = valeEntregaService.listarTodos();
        List<Incidencia> incidencias = incidenciaService.listarTodas();
        String periodo = textoPeriodo(desde, hoy);

        long borradores = partes.stream()
                .filter(p -> ParteNave.ESTADO_BORRADOR.equals(p.getEstado()))
                .count();
        long porRecibir = vales.stream()
                .filter(v -> ValeEntrega.ESTADO_EMITIDO.equals(v.getEstado()))
                .count();
        long abiertas = incidencias.stream()
                .filter(i -> Incidencia.ESTADO_ABIERTA.equals(i.getEstado()))
                .count();
        long emitidos = vales.stream()
                .filter(v -> enPeriodo(v.getFechaHoraEmision(), desde, hoy))
                .count();

        Map<UUID, String> naves = nombresNaves();
        Map<UUID, String> destinos = nombresDestinos();
        Map<UUID, String> valesPorId = new HashMap<>();
        for (ValeEntrega vale : vales) {
            valesPorId.put(vale.getIdVale(), texto(vale.getNumeroVale()));
        }
        List<FilaDetalle> detalle = new ArrayList<>();
        for (ParteNave parte : partes) {
            if (ParteNave.ESTADO_BORRADOR.equals(parte.getEstado())) {
                detalle.add(new FilaDetalle(fechaComoHora(parte.getFechaProduccion()), new Object[]{
                        "Parte en borrador", naves.getOrDefault(parte.getIdNave(), "Nave no encontrada"),
                        fecha(parte.getFechaProduccion()), ParteNave.ESTADO_BORRADOR,
                        cantidad(parte.getHuevosProducidos(), "huevos")}));
            }
        }
        for (ValeEntrega vale : vales) {
            if (ValeEntrega.ESTADO_EMITIDO.equals(vale.getEstado())) {
                detalle.add(new FilaDetalle(vale.getFechaHoraEmision(), new Object[]{
                        "Vale sin recepción", texto(vale.getNumeroVale()), fechaHora(vale.getFechaHoraEmision()),
                        ValeEntrega.ESTADO_EMITIDO,
                        destinos.getOrDefault(vale.getIdDestino(), "Destino no encontrado")
                                + " · " + cantidad(vale.getCantidadDespachada(), texto(vale.getUnidadMedida()))}));
            }
        }
        for (Incidencia incidencia : incidencias) {
            if (Incidencia.ESTADO_ABIERTA.equals(incidencia.getEstado())) {
                String vale = valesPorId.getOrDefault(incidencia.getIdVale(), "Vale no encontrado");
                String detalleIncidencia = incidencia.getTipo() == null && incidencia.getMedidaAcordada() == null
                        ? "Pendiente de completar"
                        : texto(incidencia.getTipo()) + " · " + texto(incidencia.getMedidaAcordada());
                detalle.add(new FilaDetalle(incidencia.getFechaHoraRegistro(), new Object[]{
                        "Incidencia abierta", vale, fechaHora(incidencia.getFechaHoraRegistro()),
                        Incidencia.ESTADO_ABIERTA, detalleIncidencia}));
            }
        }

        return new Resumen(
                "Resumen operativo de la UEB",
                periodo + ". Los pendientes incluyen todo el historial.",
                List.of(
                        indicador("Huevos registrados", total(sumarHuevos(registrados), "U"), periodo),
                        indicador("Carne registrada", total(sumarCarne(carnes), "t"), periodo),
                        indicador("Partes en borrador", borradores, "Pendientes de oficializar"),
                        indicador("Vales por recibir", porRecibir, "Estado EMITIDO"),
                        indicador("Incidencias abiertas", abiertas, "Requieren seguimiento"),
                        indicador("Vales emitidos", emitidos, periodo)),
                "Pendientes operativos",
                new String[]{"Tipo", "Referencia", "Fecha", "Estado", "Detalle"},
                limitar(detalle, 20),
                "La producción suma únicamente partes REGISTRADOS; los borradores no cuentan como producción oficial. "
                        + "Los vales e incidencias pendientes no se limitan al período seleccionado.");
    }

    private Resumen resumenEstadistica(LocalDate desde, LocalDate hoy) {
        List<ParteNave> registrados = parteNaveService.listarTodos().stream()
                .filter(p -> ParteNave.ESTADO_REGISTRADO.equals(p.getEstado()))
                .filter(p -> enPeriodo(p.getFechaProduccion(), desde, hoy))
                .toList();
        List<ProduccionCarne> carnes = produccionCarneService.listarTodas().stream()
                .filter(p -> enPeriodo(p.getFechaProduccion(), desde, hoy))
                .toList();
        List<Nave> naves = naveService.listarTodas();
        Map<UUID, UUID> uebPorNave = new HashMap<>();
        for (Nave nave : naves) {
            uebPorNave.put(nave.getIdNave(), nave.getIdUEB());
        }
        Map<UUID, BigDecimal> huevosPorUeb = new HashMap<>();
        Map<UUID, BigDecimal> carnePorUeb = new HashMap<>();
        Map<UUID, LocalDate> ultimoHuevo = new HashMap<>();
        Map<UUID, LocalDate> ultimaCarne = new HashMap<>();
        Set<UUID> conReporte = new HashSet<>();
        for (ParteNave parte : registrados) {
            UUID idUeb = uebPorNave.get(parte.getIdNave());
            if (idUeb == null) {
                continue;
            }
            conReporte.add(idUeb);
            sumarEnMapa(huevosPorUeb, idUeb, parte.getHuevosProducidos());
            ultimoHuevo.merge(idUeb, parte.getFechaProduccion(), this::fechaMasReciente);
        }
        for (ProduccionCarne carne : carnes) {
            conReporte.add(carne.getIdUEB());
            sumarEnMapa(carnePorUeb, carne.getIdUEB(), carne.getCantidadToneladas());
            ultimaCarne.merge(carne.getIdUEB(), carne.getFechaProduccion(), this::fechaMasReciente);
        }

        List<UEB> activas = uebService.listarTodas().stream()
                .filter(UEB::isActiva)
                .sorted(Comparator.comparing(UEB::getNombre, Comparator.nullsLast(String::compareTo)))
                .toList();
        long sinReporte = activas.stream().filter(u -> !conReporte.contains(u.getIdUEB())).count();
        long consolidaciones = consolidadoService.listarTodos().stream()
                .filter(c -> seSolapa(c, desde, hoy))
                .count();
        long enRevision = informeService.listarInformes().stream()
                .filter(i -> Informe.ESTADO_EN_REVISION.equals(i.getEstado()))
                .count();
        String periodo = textoPeriodo(desde, hoy);

        List<Object[]> detalle = new ArrayList<>();
        for (UEB ueb : activas) {
            UUID id = ueb.getIdUEB();
            detalle.add(new Object[]{
                    texto(ueb.getNombre()) + " (" + texto(ueb.getCodigo()) + ")",
                    total(huevosPorUeb.get(id), "U"),
                    total(carnePorUeb.get(id), "t"),
                    fecha(ultimoHuevo.get(id)),
                    fecha(ultimaCarne.get(id)),
                    conReporte.contains(id) ? "Con reporte" : "Sin reporte"});
        }

        return new Resumen(
                "Resumen estadístico de producción",
                periodo + ". Producción registrada y cobertura por UEB.",
                List.of(
                        indicador("Huevos registrados", total(sumarHuevos(registrados), "U"), periodo),
                        indicador("Carne registrada", total(sumarCarne(carnes), "t"), periodo),
                        indicador("UEB activas", activas.size(), "Catálogo operativo"),
                        indicador("UEB sin reporte", sinReporte, periodo),
                        indicador("Consolidaciones", consolidaciones, "Períodos que se solapan"),
                        indicador("Informes en revisión", enRevision, "Pendientes de decisión")),
                "Cobertura de producción por UEB",
                new String[]{"UEB", "Huevos", "Carne", "Último parte", "Última carne", "Estado"},
                detalle,
                "Los totales suman partes REGISTRADOS y producción de carne; no añaden consolidaciones para evitar duplicar. "
                        + "Una UEB sin reporte no tiene producción registrada de huevos o carne en el período seleccionado.");
    }

    private Resumen resumenAdministrador() {
        List<Usuario> usuarios = usuarioService.listarTodos();
        long activos = usuarios.stream().filter(Usuario::isActivo).count();
        long inactivos = usuarios.size() - activos;
        long uebs = uebService.listarTodas().stream().filter(UEB::isActiva).count();
        long naves = naveService.listarTodas().stream().filter(Nave::isActiva).count();
        long productos = productoService.listarTodos().stream().filter(cu.pican.model.Producto::isActiva).count();
        BackupAuditoria ultimaCopia = backupService.historial().stream()
                .filter(a -> "CREACION".equals(a.getOperacion()) && "EXITOSO".equals(a.getResultado()))
                .max(Comparator.comparing(BackupAuditoria::getFechaHora,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);

        List<Usuario> recientes = new ArrayList<>(usuarios);
        recientes.sort(Comparator.comparing(Usuario::getFechaCreacion,
                Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Usuario::getNombreUsuario, Comparator.nullsLast(String::compareToIgnoreCase)));
        List<Object[]> detalle = new ArrayList<>();
        for (Usuario usuario : recientes) {
            detalle.add(new Object[]{
                    texto(usuario.getNombreUsuario()), texto(usuario.getNombreCompleto()),
                    usuario.getRol() == null ? "Sin rol" : texto(usuario.getRol().getNombre()),
                    usuario.isActivo() ? "Activo" : "Inactivo", fechaHora(usuario.getFechaCreacion()),
                    usuario.isCambioContrasenaRequerido() ? "Sí" : "No"});
        }

        return new Resumen(
                "Resumen administrativo",
                "Cuentas, catálogos activos y estado de copias de seguridad.",
                List.of(
                        indicador("Usuarios activos", activos, "Pueden iniciar sesión"),
                        indicador("Usuarios inactivos", inactivos, "Baja lógica"),
                        indicador("UEB activas", uebs, "Catálogo"),
                        indicador("Naves activas", naves, "Catálogo"),
                        indicador("Productos activos", productos, "Catálogo"),
                        indicador("Última copia exitosa", ultimaCopia == null ? "Sin copias" : fechaHora(ultimaCopia.getFechaHora()),
                                ultimaCopia == null ? "No hay creación exitosa registrada" : acortar(texto(ultimaCopia.getArchivo()), 70))),
                "Cuentas de usuario",
                new String[]{"Usuario", "Nombre completo", "Rol", "Estado", "Creada", "Cambio de clave"},
                limitarFilas(detalle, LIMITE_DETALLE),
                "La última copia corresponde a una operación CREACION con resultado EXITOSO. "
                        + "Las copias son manuales; el sistema no aplica retención automática. "
                        + (detalle.size() > LIMITE_DETALLE
                        ? "Se muestran las " + LIMITE_DETALLE + " cuentas más recientes de " + detalle.size() + "."
                        : "Se muestran todas las cuentas registradas."));
    }

    private Resumen resumenProduccion() {
        List<IndicadorHistorico> referencias = analisisService.consultarTodasLasReferencias();
        List<MetaEstrategica> metas = analisisService.consultarMetas();
        Integer ultimoAnio = referencias.stream().map(IndicadorHistorico::getAnio).max(Integer::compareTo).orElse(null);
        long infraestructura = referencias.stream()
                .filter(r -> r.getTabla() == TablaHistorica.INFRAESTRUCTURA)
                .count();
        long uebs = uebService.listarTodas().stream().filter(UEB::isActiva).count();
        long naves = naveService.listarTodas().stream().filter(Nave::isActiva).count();

        List<IndicadorHistorico> ordenadas = new ArrayList<>(referencias);
        ordenadas.sort(Comparator.comparing(IndicadorHistorico::getAnio, Comparator.reverseOrder())
                .thenComparing(IndicadorHistorico::getIndicador, Comparator.nullsLast(String::compareToIgnoreCase)));
        List<Object[]> detalle = new ArrayList<>();
        for (IndicadorHistorico referencia : ordenadas) {
            detalle.add(new Object[]{
                    etiquetaTabla(referencia.getTabla()), texto(referencia.getIndicador()),
                    texto(referencia.getAnio()), numero(referencia.getValor()),
                    texto(referencia.getUnidadMedida()), acortar(texto(referencia.getFuente()), 80)});
        }
        for (MetaEstrategica meta : metas) {
            detalle.add(new Object[]{
                    "Meta 2030", texto(meta.getIndicador()), "2019 → 2030", numero(meta.getValorEstimado2030()),
                    texto(meta.getUnidadMedida()),
                    acortar(texto(meta.getFuente()) + " · Incremento aprobado: " + numero(meta.getIncrementoPorcentual()) + "%", 80)});
        }

        return new Resumen(
                "Resumen de referencias oficiales",
                "Información histórica y metas estratégicas de solo lectura.",
                List.of(
                        indicador("Referencias históricas", referencias.size(), "Cuatro tablas oficiales"),
                        indicador("Metas estratégicas", metas.size(), "Horizonte 2030"),
                        indicador("Indicadores de infraestructura", infraestructura, "Tabla 1"),
                        indicador("Año más reciente", ultimoAnio == null ? "Sin datos" : ultimoAnio, "Referencias históricas"),
                        indicador("UEB activas", uebs, "Catálogo"),
                        indicador("Naves activas", naves, "Catálogo")),
                "Referencias y metas oficiales",
                new String[]{"Tipo", "Indicador", "Período", "Valor", "Unidad", "Fuente"},
                detalle,
                "Las referencias históricas son de solo lectura. Las metas 2030 no son cuotas anuales ni por UEB; "
                        + "su actualización se realiza mediante la importación administrativa de Excel.");
    }

    private Map<UUID, String> nombresNaves() {
        Map<UUID, String> nombres = new HashMap<>();
        for (Nave nave : naveService.listarTodas()) {
            nombres.put(nave.getIdNave(), texto(nave.getNombre()) + " (" + texto(nave.getCodigo()) + ")");
        }
        return nombres;
    }

    private Map<UUID, String> nombresDestinos() {
        Map<UUID, String> nombres = new HashMap<>();
        for (Destino destino : destinoService.listarTodos()) {
            nombres.put(destino.getIdDestino(), texto(destino.getNombre()) + " (" + texto(destino.getCodigo()) + ")");
        }
        return nombres;
    }

    private static BigDecimal sumarHuevos(List<ParteNave> partes) {
        BigDecimal total = null;
        for (ParteNave parte : partes) {
            total = sumar(total, parte.getHuevosProducidos());
        }
        return total;
    }

    private static BigDecimal sumarCarne(List<ProduccionCarne> carnes) {
        BigDecimal total = null;
        for (ProduccionCarne carne : carnes) {
            total = sumar(total, carne.getCantidadToneladas());
        }
        return total;
    }

    private static BigDecimal sumar(BigDecimal total, Number valor) {
        if (valor == null) {
            return total;
        }
        BigDecimal cantidad = valor instanceof BigDecimal ? (BigDecimal) valor : new BigDecimal(valor.toString());
        return total == null ? cantidad : total.add(cantidad);
    }

    private static void sumarEnMapa(Map<UUID, BigDecimal> totales, UUID id, Number valor) {
        if (valor != null) {
            totales.merge(id, sumar(null, valor), BigDecimal::add);
        }
    }

    private LocalDate fechaMasReciente(LocalDate izquierda, LocalDate derecha) {
        if (izquierda == null) {
            return derecha;
        }
        if (derecha == null) {
            return izquierda;
        }
        return izquierda.isAfter(derecha) ? izquierda : derecha;
    }

    private static boolean enPeriodo(LocalDate fecha, LocalDate desde, LocalDate hasta) {
        return fecha != null && (desde == null || !fecha.isBefore(desde)) && !fecha.isAfter(hasta);
    }

    private static boolean enPeriodo(LocalDateTime fecha, LocalDate desde, LocalDate hasta) {
        return fecha != null && enPeriodo(fecha.toLocalDate(), desde, hasta);
    }

    private static boolean seSolapa(ConsolidadoUEB consolidado, LocalDate desde, LocalDate hasta) {
        if (consolidado.getFechaInicio() == null || consolidado.getFechaFin() == null) {
            return false;
        }
        return (desde == null || !consolidado.getFechaFin().isBefore(desde))
                && !consolidado.getFechaInicio().isAfter(hasta);
    }

    private static Indicador indicador(String titulo, Object valor, String detalle) {
        return new Indicador(titulo, texto(valor), detalle);
    }

    private static String total(BigDecimal valor, String unidad) {
        return valor == null ? "Sin datos" : numero(valor) + " " + unidad;
    }

    private static String cantidad(Number valor, String unidad) {
        if (valor == null) {
            return "Sin datos";
        }
        String sufijo = unidad == null || unidad.isBlank() ? "" : " " + unidad;
        return numero(valor) + sufijo;
    }

    private static String numero(BigDecimal valor) {
        return valor == null ? "Sin datos" : valor.stripTrailingZeros().toPlainString();
    }

    private static String numero(Number valor) {
        if (valor == null) {
            return "Sin datos";
        }
        return valor instanceof BigDecimal decimal ? numero(decimal) : String.valueOf(valor);
    }

    private static String textoPeriodo(LocalDate desde, LocalDate hasta) {
        return desde == null ? "Todo el historial" : "Del " + fecha(desde) + " al " + fecha(hasta);
    }

    private static LocalDateTime fechaComoHora(LocalDate fecha) {
        return fecha == null ? null : fecha.atStartOfDay();
    }

    private static String fecha(LocalDate fecha) {
        return fecha == null ? "Sin registro" : FECHA.format(fecha);
    }

    private static String fechaHora(LocalDateTime fecha) {
        return fecha == null ? "Sin registro" : FECHA_HORA.format(fecha);
    }

    private static String etiquetaTabla(TablaHistorica tabla) {
        if (tabla == null) {
            return "Referencia";
        }
        return switch (tabla) {
            case INFRAESTRUCTURA -> "Infraestructura";
            case EFICIENCIA_PRODUCTIVA -> "Eficiencia productiva";
            case DESTINOS_PROYECCIONES -> "Destinos y proyecciones";
            case METAS_ESTRATEGICAS -> "Metas estratégicas";
        };
    }

    private static String texto(Object valor) {
        return valor == null ? "-" : String.valueOf(valor);
    }

    private static String acortar(String texto, int limite) {
        return texto.length() <= limite ? texto : texto.substring(0, limite - 1) + "…";
    }

    private static List<Object[]> limitar(List<FilaDetalle> filas, int limite) {
        filas.sort(Comparator.comparing(FilaDetalle::fecha,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return filas.stream().limit(limite).map(FilaDetalle::valores).toList();
    }

    private static List<Object[]> limitarFilas(List<Object[]> filas, int limite) {
        return new ArrayList<>(filas.subList(0, Math.min(filas.size(), limite)));
    }

    public record Indicador(String titulo, String valor, String detalle) {
    }

    public record Resumen(String titulo, String subtitulo, List<Indicador> indicadores,
                          String tituloDetalle, String[] columnas, List<Object[]> detalle, String nota) {
        public Resumen {
            indicadores = List.copyOf(indicadores);
            columnas = columnas.clone();
            detalle = detalle.stream().map(fila -> fila.clone()).toList();
        }
    }

    private record FilaDetalle(LocalDateTime fecha, Object[] valores) {
    }
}
