package cu.pican.app;

import cu.pican.model.ConsolidadoUEB;
import cu.pican.model.Nave;
import cu.pican.model.ParteNave;
import cu.pican.model.ProduccionCarne;
import cu.pican.model.UEB;
import cu.pican.service.AutorizacionService;
import cu.pican.service.ConsolidadoUEBService;
import cu.pican.service.NaveService;
import cu.pican.service.ParteNaveService;
import cu.pican.service.ProduccionCarneService;
import cu.pican.service.UEBService;
import cu.pican.service.ValidacionException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Inicializa datos de demostración en memoria para facilitar pruebas de la interfaz.
 * Los registros creados se identifican claramente como demostración y no representan información oficial de PICAN.
 * No se inventan cifras históricas ni metas estratégicas ni relaciones UEB-unidad asociada no confirmadas.
 */
public final class DemoDataInitializer {

    private DemoDataInitializer() {
    }

    public static void initialize(AppContext context) {
        // Excepción controlada: estos datos no son oficiales y se siembran antes del login.
        // Se restaura la autoridad real al terminar para no dejar una política permisiva activa.
        AutorizacionService original = context.getAutorizacionService();
        context.usarAutorizacion(AutorizacionDemo.politicaTodosLosRoles());
        try {
            seedUEBs(context.getUebService());
            seedNaves(context.getNaveService(), context.getUebService());
            seedPartesNave(context.getParteNaveService(), context.getNaveService());
            seedProduccionCarne(context.getProduccionCarneService(), context.getUebService());
            seedConsolidados(context.getConsolidadoUEBService(), context.getUebService());
        } finally {
            context.usarAutorizacion(original);
        }
    }

    private static void seedUEBs(UEBService uebService) {
        try {
            if (!uebService.listarTodas().isEmpty()) {
                return;
            }
            uebService.registrar(new UEB(UUID.randomUUID(), "UEB Demostración Norte", "DEMO-UEB-01", true));
            uebService.registrar(new UEB(UUID.randomUUID(), "UEB Demostración Sur", "DEMO-UEB-02", true));
        } catch (ValidacionException ex) {
            // Si fallara la siembra de demostración, la app puede continuar con datos vacíos.
        }
    }

    private static void seedNaves(NaveService naveService, UEBService uebService) {
        try {
            if (!naveService.listarTodas().isEmpty()) {
                return;
            }
            List<UEB> uebs = uebService.listarTodas();
            int index = 1;
            for (UEB ueb : uebs) {
                if (ueb.getCodigo() != null && ueb.getCodigo().startsWith("DEMO-")) {
                    naveService.registrar(new Nave(
                            UUID.randomUUID(),
                            ueb.getIdUEB(),
                            "Nave Demostración " + index,
                            "DEMO-NAV-" + String.format("%02d", index),
                            true
                    ));
                    index++;
                }
            }
        } catch (ValidacionException ex) {
            // Si fallara la siembra de demostración, la app puede continuar con datos vacíos.
        }
    }

    private static void seedPartesNave(ParteNaveService parteNaveService, NaveService naveService) {
        try {
            if (!parteNaveService.listarTodos().isEmpty()) {
                return;
            }
            List<Nave> naves = naveService.listarTodas();
            int count = 0;
            for (Nave nave : naves) {
                if (nave.getCodigo() != null && nave.getCodigo().startsWith("DEMO-NAV-")) {
                    ParteNave parte = new ParteNave();
                    parte.setIdParte(UUID.randomUUID());
                    parte.setIdNave(nave.getIdNave());
                    parte.setFechaProduccion(LocalDate.now().minusDays(count + 1));
                    parte.setGallinasVivas(100);
                    parte.setGallinasMuertas(1);
                    parte.setGallinasEnfermas(2);
                    parte.setHuevosProducidos(80);
                    parte.setHuevosRotos(2);
                    parte.setPiensoKg(new BigDecimal("12.5"));
                    parte.setObservaciones("Parte de demostración (no oficial).");
                    parte.setCreadoEn(LocalDateTime.now());
                    parte.setVersion(1L);
                    parteNaveService.registrar(parte);
                    count++;
                    if (count >= 2) {
                        break;
                    }
                }
            }
        } catch (ValidacionException ex) {
            // Si fallara la siembra de demostración, la app puede continuar con datos vacíos.
        }
    }

    private static void seedProduccionCarne(ProduccionCarneService produccionCarneService, UEBService uebService) {
        try {
            if (!produccionCarneService.listarTodas().isEmpty()) {
                return;
            }
            List<UEB> uebs = uebService.listarTodas();
            int count = 0;
            for (UEB ueb : uebs) {
                if (ueb.getCodigo() != null && ueb.getCodigo().startsWith("DEMO-")) {
                    ProduccionCarne prod = new ProduccionCarne();
                    prod.setIdProduccion(UUID.randomUUID());
                    prod.setIdUEB(ueb.getIdUEB());
                    prod.setFechaProduccion(LocalDate.now().minusDays(count + 1));
                    prod.setCantidadToneladas(new BigDecimal(count == 0 ? "1.250" : "0.800"));
                    prod.setObservaciones("Producción de carne de demostración (no oficial).");
                    prod.setCreadoEn(LocalDateTime.now());
                    prod.setVersion(1L);
                    produccionCarneService.registrar(prod);
                    count++;
                    if (count >= 2) {
                        break;
                    }
                }
            }
        } catch (ValidacionException ex) {
            // Si fallara la siembra de demostración, la app puede continuar con datos vacíos.
        }
    }

    private static void seedConsolidados(ConsolidadoUEBService consolidadoService, UEBService uebService) {
        try {
            if (!consolidadoService.listarTodos().isEmpty()) {
                return;
            }
            List<UEB> uebs = uebService.listarTodas();
            for (UEB ueb : uebs) {
                if (ueb.getCodigo() != null && ueb.getCodigo().startsWith("DEMO-")) {
                    ConsolidadoUEB c = new ConsolidadoUEB();
                    c.setIdConsolidado(UUID.randomUUID());
                    c.setIdUEB(ueb.getIdUEB());
                    // Período de demostración que comprende los días recientes sembrados
                    c.setFechaInicio(LocalDate.now().minusDays(7));
                    c.setFechaFin(LocalDate.now());
                    c.setEstado("Demostración (no oficial)");
                    c.setMotivoParcial(null);
                    c.setCreadoEn(LocalDateTime.now());
                    c.setVersion(1L);
                    consolidadoService.registrar(c);
                    break; // un consolidado de demostración es suficiente
                }
            }
        } catch (ValidacionException ex) {
            // Si fallara la siembra de demostración, la app puede continuar con datos vacíos.
        }
    }
}
