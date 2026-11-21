# PENDIENTES PICAN — ESTADO AL CIERRE DE LA ETAPA 4.19

Clasificación: **PENDIENTE EXTERNO** (requiere decisión o recurso ajeno), **OBSERVACIÓN TÉCNICA** (conocida y documentada), **NO PROBADO** (sin ejecución real), **BLOQUEADO** (no se puede completar sin lo anterior).

## 1. Pendientes externos

| ID | Asunto | Estado | Qué se necesita |
|---|---|---|---|
| D-21 | SIN-13 completo: bajada central con `ultimo_vale_conocido` y última secuencia para desbloquear una UEB restaurada. | **BLOQUEADO** | Definición formal de un nuevo mensaje en PICAN-SYNC 1.0 o aprobación de un protocolo compatible. No se simuló. |
| D-22 | Plantillas XLSX oficiales de CU-18. | **PENDIENTE EXTERNO** | Archivos físicos de PICAN. CU-18 se probó contra la estructura normalizada ya aprobada; no se inventaron plantillas. |
| D-23.a | Relación usuario↔UEB. | **PENDIENTE EXTERNO** | Decisión empresarial. No se creó relación ni UEB predeterminada. |
| D-23.b | Reglas de precedencia y resolución de conflictos. | **PENDIENTE EXTERNO** | Definición funcional. Hoy los conflictos se registran, nunca se resuelven automáticamente. |
| D-23.c | Autoridad de escritura por tabla (-central→UEB o UEB→central). | **PENDIENTE EXTERNO** | Matriz de autoridad. |
| D-23.d | Distribución central→UEB y frecuencia de intercambio. | **PENDIENTE EXTERNO** | Diseño y aprobación. Solo existe la dirección UEB→central. |
| D-23.e | CU-04 (camiones y choferes). | **PENDIENTE EXTERNO** | Fuera de alcance aprobado. No se implementó. |
| D-23.f | Listado oficial de las 28 unidades no estatales asociadas. | **PENDIENTE EXTERNO** | Fuente oficial de PICAN. El catálogo queda vacío en el arranque. |
| D-25 | Indicadores reales del Dashboard. | **PENDIENTE EXTERNO** | Definición de métricas. El panel está etiquetado como ficticio; no se inventaron indicadores. |
| D-24 | Pruebas manuales de Swing. | **NO PROBADO** | Ejecución humana siguiendo `PRUEBAS_MANUALES_GUI_ETAPA_4.19.md`. |

## 2. Observaciones técnicas documentadas

| ID | Asunto | Estado | Decisión en 4.19 |
|---|---|---|---|
| D-04 | Cambio obligatorio de contraseña solo se exige en `LoginDialog`. | **OBSERVACIÓN TÉCNICA** | No se reforzó en servicios: invalidaría arneses aprobados de 4.16. Queda pendiente de decisión de defensa en profundidad. |
| D-05 | `UsuarioSesionSupport.resolver` prefiere el `idUsuario` del llamador sobre el de sesión. | **OBSERVACIÓN TÉCNICA** | No hay uso indebido demostrado; no se cambiaron firmas de servicios. |
| D-08 | `listarPendientes` no compara `version > version_confirmada` y `registrarItem` guarda `version = NULL`. | **OBSERVACIÓN TÉCNICA / DESVIACIÓN** | Cambiar la regla alteraría la evidencia 4.15. Requiere decisión técnica antes de tocar la semántica de reexportación. |
| D-09 | Compensaciones MEMORY que ignoran fallos en `RecepcionService` e `IncidenciaService`. | **OBSERVACIÓN TÉCNICA** | Solo afecta demostraciones; PostgreSQL usa transacciones reales. No se cambiaron reglas de negocio. |
| D-10 | El ZIP se escribe antes de la transacción de metadatos de `sync_paquete`. | **OBSERVACIÓN TÉCNICA** | No se simula rollback de sistema de archivos. |
| D-11 | Numeración de vales volátil y consumida antes de validar/guardar. | **OBSERVACIÓN TÉCNICA** | Requiere decidir formato y secuencia persistente. No se inventó. |
| D-12 | `PermisosInterfaz` es permisivo con rol `null`, mientras `NavigationController` falla cerrado. | **OBSERVACIÓN TÉCNICA** | `PermisosInterfazTest:42-43` aprueba explícitamente ese comportamiento. Alinearlo implicaría retractar evidencia aprobada. |
| D-17 | `BackupArchivo` valida 1 GB en lectura pero no en escritura. | **OBSERVACIÓN TÉCNICA** | No se añadió la comprobación simétrica en esta etapa. |
| D-18 | `ToolLocator` no verifica ejecutabilidad y descubre solo PostgreSQL 12–17. | **OBSERVACIÓN TÉCNICA** | Requisitos de rutas y versión ya documentados en `MANUAL_COPIAS_SEGURIDAD_PICAN.md` §2. |
| D-20 | Literal histórico de contraseña en `test/cu/pican/verify/AppContextIntegrationTest.java:33`. | **OBSERVACIÓN TÉCNICA** | Se conserva para no modificar ni repetir una prueba aprobada. No aparece en logs. |

## 3. Elementos fuera del alcance permitido

| Asunto | Estado | Acción |
|---|---|---|
| `aplicacionPican.rar` y `Modelo Caso de uso de Negocio/aplicacionPican/` | **APROBADO CON OBSERVACIONES** | Copias antiguas sin seguimiento fuera del directorio permitido. No se eliminaron ni modificaron. Pueden confundir versiones al desplegar. |
| SIN-13 en instalaciones `CENTRAL` | **APROBADO CON OBSERVACIONES** | El control no bloquea en CENTRAL, según evidencia 4.18. La bajada central sigue pendiente (D-21). |

## 4. No pendiente: resuelto en 4.19

D-01, D-02, D-03, D-06, D-07, D-13, D-14, D-15, D-16, D-19, D-20 (documentos) y D-25 (documentación del panel) tienen corrección y evidencia en `PRUEBAS_ETAPA_4.19.md`.
