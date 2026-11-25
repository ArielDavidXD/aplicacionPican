package cu.pican.service;

import cu.pican.model.ParteNave;
import cu.pican.repository.NaveRepository;
import cu.pican.repository.ParteNaveRepository;
import cu.pican.repository.UsuarioRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio de negocio para partes de producción de huevos por nave.
 * Unicidad nave+fecha (equivale a UEB+nave+fecha porque la nave pertenece a una única UEB).
 * Estados: BORRADOR (guardado sin oficializar) y REGISTRADO (parte oficial).
 */
public class ParteNaveService extends ServicioAutorizado {

    private static final int MAX_OBSERVACIONES = 250;
    private static final BigDecimal CENTESIMA = new BigDecimal("0.01");

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

    /** Registra el parte como REGISTRADO (oficial). */
    public ParteNave registrar(ParteNave parte) {
        return guardar(parte, ParteNave.ESTADO_REGISTRADO);
    }

    /** Guarda el parte en estado BORRADOR; puede oficializarse luego con registrar(). */
    public ParteNave guardarBorrador(ParteNave parte) {
        return guardar(parte, ParteNave.ESTADO_BORRADOR);
    }

    private ParteNave guardar(ParteNave parte, String estado) {
        autorizacion().requerir(CasoUso.CU_05);
        if (parte != null) {
            parte.setIdUsuario(UsuarioSesionSupport.resolver(proveedorSesion, parte.getIdUsuario()));
            parte.setEstado(estado);
            validarNaveActiva(parte.getIdNave());
        }
        validar(parte);
        validarUnicidadNaveFecha(parte, null);
        return parteNaveRepository.save(parte);
    }

    public ParteNave actualizar(ParteNave parte) {
        autorizacion().requerir(CasoUso.CU_05);
        if (parte != null) {
            parte.setIdUsuario(UsuarioSesionSupport.resolver(proveedorSesion, parte.getIdUsuario()));
        }
        validar(parte);
        validarUnicidadNaveFecha(parte, parte.getIdParte());
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
        if (parte.getFechaProduccion().isAfter(LocalDate.now())) {
            throw new ValidacionException("La fecha de producción no puede ser futura.");
        }

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

        // Cantidad opcional: no negativa cuando se informa y con máximo 2 decimales.
        ValidationUtils.requireNonNegative(parte.getPiensoKg(), "piensoKg");
        if (parte.getPiensoKg() != null
                && parte.getPiensoKg().remainder(CENTESIMA).compareTo(BigDecimal.ZERO) != 0) {
            throw new ValidacionException("El pienso (kg) no puede tener más de 2 decimales.");
        }

        // Observaciones: máximo 250 caracteres.
        if (parte.getObservaciones() != null && parte.getObservaciones().length() > MAX_OBSERVACIONES) {
            throw new ValidacionException("Las observaciones no pueden superar los " + MAX_OBSERVACIONES + " caracteres.");
        }

        // Estado: solo BORRADOR o REGISTRADO.
        if (parte.getEstado() == null
                || (!ParteNave.ESTADO_BORRADOR.equals(parte.getEstado())
                    && !ParteNave.ESTADO_REGISTRADO.equals(parte.getEstado()))) {
            throw new ValidacionException("El estado del parte debe ser BORRADOR o REGISTRADO.");
        }

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

    /** Unicidad nave+fecha: un solo parte por nave y fecha (excluye el propio registro al actualizar). */
    private void validarUnicidadNaveFecha(ParteNave parte, UUID idParteExcluido) {
        for (ParteNave existente : parteNaveRepository.findByNaveYFecha(parte.getIdNave(), parte.getFechaProduccion())) {
            if (!existente.getIdParte().equals(idParteExcluido)) {
                throw new ValidacionException("Ya existe un parte para esa nave y fecha. "
                        + "Selecciónelo en la tabla para editarlo o registrarlo.");
            }
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
