package cu.pican.controller.app;

import cu.pican.model.ConsolidadoUEB;
import cu.pican.model.Nave;
import cu.pican.model.ParteNave;
import cu.pican.model.ParteUnidadAsociada;
import cu.pican.model.ProduccionCarne;
import cu.pican.model.UEB;
import cu.pican.model.UnidadAsociada;
import cu.pican.model.repository.ConsolidadoUEBRepository;
import cu.pican.model.repository.NaveRepository;
import cu.pican.model.repository.ParteNaveRepository;
import cu.pican.model.repository.ParteUnidadAsociadaRepository;
import cu.pican.model.repository.ProduccionCarneRepository;
import cu.pican.model.repository.UEBRepository;
import cu.pican.model.repository.UnidadAsociadaRepository;
import cu.pican.model.repository.memory.ConsolidadoUEBRepositoryMemory;
import cu.pican.model.repository.memory.NaveRepositoryMemory;
import cu.pican.model.repository.memory.ParteNaveRepositoryMemory;
import cu.pican.model.repository.memory.ParteUnidadAsociadaRepositoryMemory;
import cu.pican.model.repository.memory.ProduccionCarneRepositoryMemory;
import cu.pican.model.repository.memory.UEBRepositoryMemory;
import cu.pican.model.repository.memory.UnidadAsociadaRepositoryMemory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Demostración/prueba manual de repositorios en memoria (Etapa 2).
 * Ejecutar: java -cp aplicacionPican\out cu.pican.controller.app.RepositoryDemo
 */
public class RepositoryDemo {

    public static void main(String[] args) {
        cu.pican.controller.app.AutorizacionDemo.instalarTodosLosRoles();
        probarUEB();
        probarNavePorUEB();
        probarPartesProduccion();
        probarProduccionCarneYConsolidado();
        probarAislamientoInstanciasYColecciones();
        System.out.println("\n==> Todas las comprobaciones de la Etapa 2 finalizaron correctamente.");
    }

    private static void probarUEB() {
        System.out.println("[UEB] guardar, buscar, actualizar, listar, eliminar");
        UEBRepository repo = new UEBRepositoryMemory();
        UUID id = UUID.randomUUID();
        UEB ueb = new UEB(id, "UEB Pinar 1", "UEB-01", true);

        repo.save(ueb);
        check(repo.findById(id).isPresent(), "UEB debe encontrarse por UUID tras guardar");
        check("UEB Pinar 1".equals(repo.findById(id).get().getNombre()), "Nombre inicial incorrecto");

        ueb.setNombre("UEB Pinar 1 Actualizada");
        repo.update(ueb);
        check("UEB Pinar 1 Actualizada".equals(repo.findById(id).get().getNombre()), "UEB no se actualizó");

        List<UEB> todas = repo.findAll();
        check(todas.size() == 1, "Debe existir 1 UEB en listado");

        repo.deleteById(id);
        check(!repo.findById(id).isPresent(), "UEB debe eliminarse");
        check(repo.findAll().isEmpty(), "Listado debe quedar vacío tras eliminar");
    }

    private static void probarNavePorUEB() {
        System.out.println("[Nave] guardar nave asociada a UEB y consultarla");
        UEBRepository uebRepo = new UEBRepositoryMemory();
        NaveRepository naveRepo = new NaveRepositoryMemory();
        UUID idUEB = UUID.randomUUID();
        UUID idUEB2 = UUID.randomUUID();
        UUID idNave = UUID.randomUUID();

        uebRepo.save(new UEB(idUEB, "UEB A", "A", true));
        Nave nave = new Nave(idNave, idUEB, "Nave 1", "N-01", true);
        naveRepo.save(nave);
        naveRepo.save(new Nave(UUID.randomUUID(), idUEB2, "Nave Otra", "N-99", true));

        check(naveRepo.findById(idNave).isPresent(), "Nave debe encontrarse por UUID");
        List<Nave> porUEB = naveRepo.findByUEB(idUEB);
        check(porUEB.size() == 1, "Debe haber 1 nave asociada a la UEB consultada");
        check(idNave.equals(porUEB.get(0).getIdNave()), "La nave consultada no coincide");
    }

    private static void probarPartesProduccion() {
        System.out.println("[Partes] guardar y recuperar partes de producción");
        ParteNaveRepository parteNaveRepo = new ParteNaveRepositoryMemory();
        ParteUnidadAsociadaRepository parteAsocRepo = new ParteUnidadAsociadaRepositoryMemory();
        UUID idNave = UUID.randomUUID();
        UUID idUnidad = UUID.randomUUID();
        LocalDate fecha = LocalDate.of(2026, 11, 10);

        ParteNave pn = new ParteNave();
        pn.setIdParte(UUID.randomUUID());
        pn.setIdNave(idNave);
        pn.setFechaProduccion(fecha);
        pn.setHuevosProducidos(1000);
        pn.setHuevosRotos(20);
        pn.setGallinasVivas(500);
        pn.setPiensoKg(new BigDecimal("120.50"));
        pn.setEstado("REGISTRADO");
        pn.setCreadoEn(LocalDateTime.now());
        pn.setVersion(1L);
        parteNaveRepo.save(pn);

        check(parteNaveRepo.findById(pn.getIdParte()).isPresent(), "ParteNave debe recuperarse por UUID");
        check(parteNaveRepo.findByNave(idNave).size() == 1, "ParteNave debe encontrarse por nave");
        check(parteNaveRepo.findByFecha(fecha).size() == 1, "ParteNave debe encontrarse por fecha");

        ParteUnidadAsociada pa = new ParteUnidadAsociada();
        pa.setIdParteAsociado(UUID.randomUUID());
        pa.setIdUnidad(idUnidad);
        pa.setFechaProduccion(fecha);
        pa.setReferenciaPapel("DOC-001");
        pa.setObservaciones("Registro desde formulario en papel (datos ficticios de prueba)");
        pa.setEstado("REGISTRADO");
        pa.setCreadoEn(LocalDateTime.now());
        pa.setVersion(1L);
        parteAsocRepo.save(pa);

        check(parteAsocRepo.findById(pa.getIdParteAsociado()).isPresent(), "ParteUnidadAsociada debe recuperarse por UUID");
        check(parteAsocRepo.findByUnidad(idUnidad).size() == 1, "ParteUnidadAsociada debe encontrarse por unidad");
        check(parteAsocRepo.findByFecha(fecha).size() == 1, "ParteUnidadAsociada debe encontrarse por fecha");
    }

    private static void probarProduccionCarneYConsolidado() {
        System.out.println("[Carne/Consolidado] consultas específicas por UEB y fecha");
        ProduccionCarneRepository carneRepo = new ProduccionCarneRepositoryMemory();
        ConsolidadoUEBRepository consRepo = new ConsolidadoUEBRepositoryMemory();
        UUID idUEB = UUID.randomUUID();
        LocalDate fecha = LocalDate.of(2026, 11, 10);

        ProduccionCarne carne = new ProduccionCarne();
        carne.setIdProduccion(UUID.randomUUID());
        carne.setIdUEB(idUEB);
        carne.setFechaProduccion(fecha);
        carne.setCantidadToneladas(new BigDecimal("1.250"));
        carne.setVersion(1L);
        carneRepo.save(carne);
        check(carneRepo.findByUEB(idUEB).size() == 1, "ProduccionCarne debe encontrarse por UEB");
        check(carneRepo.findByFecha(fecha).size() == 1, "ProduccionCarne debe encontrarse por fecha");

        ConsolidadoUEB c = new ConsolidadoUEB();
        c.setIdConsolidado(UUID.randomUUID());
        c.setIdUEB(idUEB);
        c.setFechaInicio(fecha);
        c.setFechaFin(fecha);
        c.setEstado("BORRADOR");
        c.setMotivoParcial(null); // consolidaciones parciales: condiciones pendientes/configurables
        c.setVersion(1L);
        consRepo.save(c);
        check(consRepo.findByUEB(idUEB).size() == 1, "ConsolidadoUEB debe encontrarse por UEB");

        // Independencia entre tipos de repositorios
        UnidadAsociadaRepository uaRepo = new UnidadAsociadaRepositoryMemory();
        check(uaRepo.findAll().isEmpty(), "Repositorios de entidades distintas no deben compartir datos");
    }

    private static void probarAislamientoInstanciasYColecciones() {
        System.out.println("[Aislamiento] instancias independientes y colecciones no expuestas");
        UEBRepository repo1 = new UEBRepositoryMemory();
        UEBRepository repo2 = new UEBRepositoryMemory();
        UUID id = UUID.randomUUID();
        repo1.save(new UEB(id, "Solo repo1", "R1", true));
        check(repo2.findAll().isEmpty(), "Instancias de repositorio no deben compartir almacenamiento");
        check(!repo2.findById(id).isPresent(), "repo2 no debe ver datos de repo1");

        List<UEB> lista = repo1.findAll();
        lista.clear(); // modificar copia devuelta
        check(repo1.findAll().size() == 1, "findAll debe devolver copia; la colección interna no debe verse afectada");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Fallo de comprobación: " + message);
        }
        System.out.println("  OK - " + message);
    }
}
