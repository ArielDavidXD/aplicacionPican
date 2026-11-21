package cu.pican.service;

import cu.pican.model.ParteNave;
import cu.pican.repository.NaveRepository;
import cu.pican.repository.ParteNaveRepository;
import cu.pican.repository.UsuarioRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio de negocio para partes de producción de huevos por nave.
 * No impone unicidad nave+fecha (pendiente de revisión/justificación documental).
 */
public class ParteNaveService extends ServicioAutorizado {

    private final ParteNaveRepository parteNaveRepository;
    private final NaveRepository naveRepository;
    private final UsuarioRepository usuarioRepository;
    private SessionUserIdProvider proveedorSesion;

    public ParteNaveService(ParteNaveRepository parteNaveRepository,
                            NaveRepository naveRepository,
                            UsuarioRepository usuarioRepository) {
        this.parteNaveRepository = parteNaveRepository;
        this.naveRepository = naveRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /** Inyectado desde AppContext; no crea sesión propia ni cambia las reglas de negocio. */
    public void setProveedorSesion(SessionUserIdProvider proveedorSesion) {
        this.proveedorSesion = proveedorSesion;
    }

    public ParteNave registrar(ParteNave parte) {
        autorizacion().requerir(CasoUso.CU_05);
        if (parte != null) {
            parte.setIdUsuario(UsuarioSesionSupport.resolver(proveedorSesion, parte.getIdUsuario()));
            validarNaveActiva(parte.getIdNave());
        }
        validar(parte);
        return parteNaveRepository.save(parte);
    }

    public ParteNave actualizar(ParteNave parte) {
        autorizacion().requerir(CasoUso.CU_05);
        if (parte != null) {
            parte.setIdUsuario(UsuarioSesionSupport.resolver(proveedorSesion, parte.getIdUsuario()));
        }
        validar(parte);
        return parteNaveRepository.update(parte);
    }

    public Optional<ParteNave> buscarPorId(UUID idParte) {
        autorizacion().requerir(CasoUso.CU_05, CasoUso.CU_14);
        return parteNaveRepository.findById(idParte);
    }

    public List<ParteNave> listarTodos() {
        autorizacion().requerir(CasoUso.CU_05, CasoUso.CU_14);
        return parteNaveRepository.findAll();
    }

    public List<ParteNave> buscarPorNave(UUID idNave) {
        autorizacion().requerir(CasoUso.CU_05, CasoUso.CU_14);
        return parteNaveRepository.findByNave(idNave);
    }

    public List<ParteNave> buscarPorFecha(LocalDate fecha) {
        autorizacion().requerir(CasoUso.CU_05, CasoUso.CU_14);
        return parteNaveRepository.findByFecha(fecha);
    }

    private void validar(ParteNave parte) {
        ValidationUtils.requireNonNull(parte, "El parte de nave es obligatorio.");
        ValidationUtils.requireUuid(parte.getIdParte(), "El identificador UUID del parte es obligatorio.");
        ValidationUtils.requireUuid(parte.getIdNave(), "La nave del parte es obligatoria.");
        if (!naveRepository.findById(parte.getIdNave()).isPresent()) {
            throw new ValidacionException("La nave indicada no existe.");
        }
        ValidationUtils.requireNonNull(parte.getFechaProduccion(), "La fecha de producción es obligatoria.");

        // Cantidades obligatorias: no nulas y no negativas.
        ValidationUtils.requireNonNull(parte.getGallinasVivas(), "El campo 'gallinasVivas' es obligatorio.");
        ValidationUtils.requireNonNull(parte.getGallinasMuertas(), "El campo 'gallinasMuertas' es obligatorio.");
        ValidationUtils.requireNonNull(parte.getGallinasEnfermas(), "El campo 'gallinasEnfermas' es obligatorio.");
        ValidationUtils.requireNonNull(parte.getHuevosProducidos(), "El campo 'huevosProducidos' es obligatorio.");
        ValidationUtils.requireNonNull(parte.getHuevosRotos(), "El campo 'huevosRotos' es obligatorio.");
        ValidationUtils.requireNonNegative(parte.getGallinasVivas(), "gallinasVivas");
        ValidationUtils.requireNonNegative(parte.getGallinasMuertas(), "gallinasMuertas");
        ValidationUtils.requireNonNegative(parte.getGallinasEnfermas(), "gallinasEnfermas");
        ValidationUtils.requireNonNegative(parte.getHuevosProducidos(), "huevosProducidos");
        ValidationUtils.requireNonNegative(parte.getHuevosRotos(), "huevosRotos");

        // Cantidad opcional: no negativa cuando se informa.
        ValidationUtils.requireNonNegative(parte.getPiensoKg(), "piensoKg");

        // Huevos rotos no pueden superar huevos producidos (si ambos están presentes).
        if (parte.getHuevosRotos() != null && parte.getHuevosProducidos() != null
                && parte.getHuevosRotos() > parte.getHuevosProducidos()) {
            throw new ValidacionException("Los huevos rotos no pueden superar los huevos producidos.");
        }

        // Gallinas muertas y enfermas no pueden superar las gallinas vivas (validación individual).
        // No se impone restricción de suma conjunta (muertas + enfermas) porque no está confirmada.
        if (parte.getGallinasVivas() != null) {
            if (parte.getGallinasMuertas() != null && parte.getGallinasMuertas() > parte.getGallinasVivas()) {
                throw new ValidacionException("Las gallinas muertas no pueden superar las gallinas vivas.");
            }
            if (parte.getGallinasEnfermas() != null && parte.getGallinasEnfermas() > parte.getGallinasVivas()) {
                throw new ValidacionException("Las gallinas enfermas no pueden superar las gallinas vivas.");
            }
        }

        // Usuario que registra el parte: si corresponde (se informa), debe existir.
        if (parte.getIdUsuario() != null && usuarioRepository != null
                && !usuarioRepository.findById(parte.getIdUsuario()).isPresent()) {
            throw new ValidacionException("El usuario que registra el parte no existe.");
        }
    }

    /** CU-17: una nave desactivada no puede elegirse en nuevas operaciones. */
    private void validarNaveActiva(UUID idNave) {
        ValidationUtils.requireUuid(idNave, "La nave del parte es obligatoria.");
        cu.pican.model.Nave nave = naveRepository.findById(idNave)
                .orElseThrow(() -> new ValidacionException("La nave indicada no existe."));
        if (!nave.isActiva()) {
            throw new ValidacionException("No se puede registrar un parte con una nave desactivada.");
        }
    }
}
