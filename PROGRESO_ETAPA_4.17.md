# PROGRESO — ETAPA 4.17 (PICAN · CU-18 Importar datos desde Excel)

Fuente de verdad: `C:\Users\Ariel David\Desktop\bioproductos\aplicacionPican\`  
Fecha de cierre: 2026-11-20.

> Registro vivo. La Etapa 4.16 quedó cerrada antes de iniciar esta.

---

## Estado general

- **Etapa 4.17: CERRADA.**
- CU-18 implementado, protegido por `CasoUso.CU_18` y verificado en MEMORY y POSTGRES real.
- **15/15** comprobaciones nuevas por modo; **21/21** autorización y **12/12** AppContext como regresiones afectadas.
- Compilación oficial final: **OK · 204 fuentes compiladas en `out`**.
- Sin dependencias nuevas: Apache POI descartado con justificación en `DIAGNOSTICO_CU18_FASE_0.md`.
- `PICAN-SYNC 1.0` (`cu.pican.sync`) **no se modificó**.
- Git: HEAD sin cambios `33bdf0d`; `aplicacionPican/` sin seguimiento. Sin commits, push ni operaciones destructivas.
- Pruebas manuales GUI: **no ejecutadas**; no se afirma lo contrario.

---

## Fase 0 — Diagnóstico (COMPLETADA)

Entregable: `DIAGNOSTICO_CU18_FASE_0.md`.

- CU-18 existe en `CasoUso` con rol `ADMINISTRADOR` y en la matriz de UI del módulo `administracion`.
- El DCUS define actor, autorización, campos, flujo, VAL-94…VAL-97, almacenamiento y resumen.
- No hay archivos `.xlsx` en el repositorio; las cuatro tablas oficiales están como imágenes en la presentación y como datos ya aprobados en `ReferenciasHistoricasInitializer`.
- No hay ningún tipo operativo expresamente definido; no se implementó ninguno.
- Las entidades y columnas ya existen; no se crearon nuevas.
- Java 21.0.6 disponible; se usa `java.util.zip` + StAX sin franquicias nuevas.

---

## Fase 1 — Lector seguro (COMPLETADA)

- `cu.pican.importacion.LectorXlsx` lee la primera hoja de un `.xlsx` con ZIP + XML del JDK.
- Valida extensión exacta `.xlsx`, tamaño máximo, número de entradas/celdas/filas, nombres ZIP sin `..` y entidades XML externas desactivadas.
- Lee cadenas compartidas, celdas en línea y valores numéricos; no ejecuta macros ni fórmulas.
- Errores estructurales lanzan `ImportacionException` antes de cualquier escritura.

---

## Fase 2 — Plantillas y validación (COMPLETADA)

- `TipoImportacion` expone solo las cuatro tablas oficiales: T1 infraestructura, T2 eficiencia, T3 destinos/proyecciones y T4 metas.
- Las cabeceras son los campos existentes de `IndicadorHistorico` / `MetaEstrategica`; no se añadieron columnas.
- `PlantillaOficial` reutiliza los valores ya aprobados por `ReferenciasHistoricasInitializer` como referencia, sin recálculo.
- `ValidadorImportacion` clasifica cada fila como válida, rechazada o repetida, con número de fila y motivo.
- Contenido distinto con la misma clave se rechaza sin sobrescribir; se respeta la regla pendiente de acuerdo con PICAN documentada en el DCUS.
- Se acepta coma o punto decimal como representación; el valor numérico no se altera.

---

## Fase 3 — Servicio y persistencia (COMPLETADA)

- `ImportacionService extends ServicioAutorizado`: `autorizacion().requerir(CasoUso.CU_18)` antes de preparar y antes de confirmar.
- Recomprobación explícita de cuenta activa y rol `ADMINISTRADOR` en ambos métodos.
- `preparar` no escribe; `confirmar` usa el `TransactionManager` del contexto.
- `ImportacionReferenciasRepository` con implementaciones MEMORY y PostgreSQL; inserción condicional sobre las tablas existentes.
- Los repositorios de solo lectura usados por análisis e informes siguen sin exponer escritura.
- AppContext construye, cablea y expone `getImportacionService()`; `usarAutorizacion(...)` también actualiza este servicio.

---

## Fase 4 — Interfaz (COMPLETADA)

- Nueva pestaña **Importar datos** en `AdminModulePanel`.
- `ImportacionAdminPanel`: selección de tipo, examinador de `.xlsx`, vista previa por fila, confirmación y resumen.
- La interfaz solo llama al servicio; no contiene reglas de negocio ni decide permisos por sí misma.
- El módulo administrativo ya estaba filtrado por `PermisosInterfaz`; además el panel deshabilita sus controles si la sesión no es ADMINISTRADOR.

---

## Fase 5 — Pruebas (COMPLETADA)

Arnés: `test/cu/pican/verify/ImportacionExcelTest.java`.

- MEMORY: **15/15**.
- POSTGRES real (`pican_importacion_417_test`): **15/15**.
- Fixtures `.xlsx` generados en la propia prueba con JDK ZIP/XML; sin Apache POI ni archivos binarios versionados.
- Cobertura IMP-01…IMP-15: matriz, sin sesión, JEFE rechazado, errores estructurales, extensión, ZIP inválido, `../`, válidas/rechazadas/repetidas, sin escrituras en vista previa, confirmación real, coma decimal, sesión cerrada y fallo cerrado.
- Regresiones: `PermisosInterfazTest` 6/6, `AutorizacionRolesTest` 21/21 MEMORY y 21/21 POSTGRES, `AppContextIntegrationTest` 12/12 MEMORY+POSTGRES.

---

## Fase 6 — Documentación (COMPLETADA)

- `DIAGNOSTICO_CU18_FASE_0.md`
- `PRUEBAS_ETAPA_4.17.md`
- `INFORME_FINAL_ETAPA_4.17.md`
- `MATRIZ_PERMISOS_PICAN.md` actualizada con CU-18 implementado.
- Logs reales en `test-results/*4.17*.log`; sin credenciales detectadas.

---

## Fase 7 — Cierre (COMPLETADA)

Cierre formal en `INFORME_FINAL_ETAPA_4.17.md`. No se realizan commits, push, reset ni cambios destructivos en Git.
