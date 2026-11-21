# INFORME FINAL — ETAPA 4.17

**Proyecto:** PICAN · `aplicacionPican`  
**Caso de uso:** CU-18 · Importar datos desde Excel  
**Fecha:** 2026-11-20  
**Estado:** **CERRADA / APROBADA**

---

## 1. Objetivo

Implementar CU-18 exclusivamente para `ADMINISTRADOR`, continuando la autorización integral de la Etapa 4.16 sin rehacer funcionalidades aprobadas ni introducir reglas empresariales inventadas.

---

## 2. Antecedente inmediato

La Etapa 4.16 había cerrado con autoridad central (`CasoUso`, `AutorizacionService`, `AutorizacionException`, `ServicioAutorizado`), UI derivada de la misma matriz y 99 comprobaciones automatizadas aprobadas. CU-18 figuraba como reservado al ADMINISTRADOR, sin implementar.

Antes de programar se completó la Fase 0 documental; el diagnóstico quedó registrado en `DIAGNOSTICO_CU18_FASE_0.md`.

---

## 3. Hechos documentales respetados

- Actor: **Administrador del sistema**. Ruta: Administración → Importar datos.
- Archivo obligatorio: **Excel `.xlsx`** (VAL-94).
- Columnas deben coincidir con la plantilla del tipo seleccionado (VAL-95).
- Filas válidas se cargan; inválidas se rechazan con fila y motivo (VAL-96); repetidas se omiten y se cuentan (VAL-97).
- No se transforman valores para hacerlos coincidir con otra fuente.
- Contenido distinto con la misma clave: **no sobrescribir**; queda pendiente de acuerdo con PICAN.
- Lectura previa y confirmación comprueban de nuevo que la cuenta siga activa y conserva el rol.
- No se persiste nada hasta la confirmación.
- Los únicos tipos definidos son las cuatro tablas oficiales de la presentación. No existe ningún \"tipo operativo\" definido documentalmente; por tanto no se implementó ninguno.

No se encontraron plantillas `.xlsx` físicas en el repositorio. Las cuatro tablas están como imágenes en la presentación y como datos ya aprobados en `ReferenciasHistoricasInitializer`. La \"plantilla\" usada es la vista normalizada de columnas y valores ya existentes, no un diseño nuevo.

---

## 4. Implementación por capas

| Capa | Clases | Responsabilidad |
|---|---|---|
| Lectura | `cu.pican.importacion.LectorXlsx`, `ArchivoExcel`, `FilaExcel` | Validar el archivo y devolver filas crudas con número original. |
| Plantilla | `TipoImportacion`, `PlantillaOficial` | Cuatro tipos oficiales, cabeceras existentes, comparación contra filas aprobadas. |
| Validación | `ValidadorImportacion`, `ResultadoFila`, `ResultadoImportacion`, `EstadoFila` | Clasificar filas; errores estructurales detienen antes de escribir. |
| Servicio | `cu.pican.service.ImportacionService` | Autorizar CU-18, orquestar vista previa/confirmación, recomprobar sesión y rol. |
| Persistencia | `ImportacionReferenciasRepository` + implementaciones Memory/JDBC | Insertar solo si la clave natural no existe, usando tablas y columnas existentes. |
| Interfaz | `ImportacionAdminPanel`, pestaña en `AdminModulePanel` | Selección de archivo/tipo, vista previa, confirmación y resumen. Sin lógica de negocio en Swing. |

### Decisiones técnicas

1. **Sin Apache POI.** Se usa `java.util.zip` + StAX del JDK 21. No hay dependencias nuevas; `lib/` sigue con un solo jar. POI habría exigido varias librerías transitivas y descargas externas sin una necesidad demostrada.
2. **Sin entidades, columnas ni claves nuevas.** Se reutilizan `indicador_historico` y `meta_estrategica`, sus columnas y sus identificadores técnicos oficiales.
3. **Sin modificar repositorios de solo lectura.** Los repositorios de análisis e informes no exponen escritura; se creó un repositorio de escritura restringida a CU-18.
4. **Sin modificar PICAN-SYNC 1.0.** El paquete `cu.pican.sync` no se tocó.
5. **Sin UEB inventada.** CU-18 no crea relación usuario↔UEB ni cambia la UEB de la sesión.

---

## 5. Seguridad y defensa en profundidad

- Extensión exacta `.xlsx`; se rechazan `.xls`, `.xlsm` y sin extensión.
- Límites de tamaño, entradas ZIP, filas y celdas para evitar bombas de descompresión.
- Nombres ZIP sin `..`, sin barra inicial y sin contrabarras; se rechazan `externalLinks`.
- `XMLInputFactory` con DTD y entidades externas desactivados.
- No se ejecutan macros ni fórmulas: solo se leen valores.
- Autorización en servicio en `preparar` y `confirmar`, además del filtro de menú.
- `ADMINISTRADOR` sigue sin producción, vales, recepciones, incidencias ni análisis.
- DIRECTIVO continúa sin cuenta ni rol constante.

---

## 6. Verificación

| Evidencia | Resultado |
|---|---|
| `ImportacionExcelTest` MEMORY | **15/15** |
| `ImportacionExcelTest` POSTGRES real | **15/15** |
| `AutorizacionRolesTest` MEMORY / POSTGRES | **21/21 / 21/21** |
| `AppContextIntegrationTest` MEMORY+POSTGRES | **12/12** |
| `PermisosInterfazTest` | **6/6** |
| Compilación oficial | **OK · 204 fuentes compiladas en `out`** |
| Compilación de pruebas | **OK · 10 fuentes** |

Detalle de comandos, bases, logs y cobertura: `PRUEBAS_ETAPA_4.17.md`.

No se detectaron credenciales en los logs `test-results/*4.17*`.

---

## 7. Límites y precisiones

- No se ejecutaron pruebas manuales GUI; quedan para la revisión posterior prevista.
- No se declara compatibilidad con plantillas `.xlsx` reales de PICAN porque no fueron proporcionadas. Si difieren de la estructura normalizada actual, hará falta un adaptador validado documentalmente.
- No se implementaron tipos operativos: el DCUS no los define.
- No se sobrescriben registros existentes. El acuerdo pendiente con PICAN sobre contenido distinto con igual clave sigue documentado como tal.
- No se usaron datos empresariales reales; solo datos oficiales ya presentes en el código y bases temporales `test`.

---

## 8. Estado de Git y cierre

- HEAD sin cambios: `33bdf0d13d9fefe9cdfb36c5377e15e1db9ed0a6`.
- `aplicacionPican/` permanece sin seguimiento.
- No se hicieron commits, push, reset ni cambios destructivos en Git.
- Etapa 4.16 cerrada y Etapa 4.17 cerrada. CU-18 queda **implementado, autorizado y verificado** para los cuatro tipos oficiales disponibles.
