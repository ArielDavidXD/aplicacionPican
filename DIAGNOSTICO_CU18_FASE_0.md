# DIAGNÓSTICO FASE 0 — CU-18 (Importar datos desde Excel)

Fecha: 2026-11-20 · Etapa 4.17 · **Antes de programar**. Estado: **Fase 0 completada; sin cambios de código**.

---

## 1. Fuentes consultadas y autoridad documental

| Fuente | Ubicación | Uso |
|---|---|---|
| `DCUS_PICAN_objetivos_empresariales(1).docx` | raíz de `aplicacionPican` | Narrativa CU-18 (datos generales, autorización, campos, flujo, validaciones VAL-94…VAL-97, almacenamiento y resultados). |
| `Ing Software 3 Bioproductos.pptx` | raíz del repositorio | Presentación de necesidades. Las cuatro tablas oficiales están incorporadas como **imágenes**, no como hojas de cálculo editables. |
| `ReferenciasHistoricasInitializer.java` | `src/cu/pican/app` | Transcripción ya aprobada en el código de las cuatro tablas oficiales: indicadores, años, unidades y valores. |
| `TablaHistorica.java`, `IndicadorHistorico.java`, `MetaEstrategica.java` | `src/cu/pican/model` | Entidades y columnas existentes. No se crearán nuevas. |
| `V1__esquema_inicial.sql` | `sql/migrations` | Columnas reales de `indicador_historico` y `meta_estrategica`. |

No se encontraron en disco los archivos `Problematica.pptx`, `DCUS_PICAN_USB.docx` ni `Informe_PICAN_corregido(4).docx` citados en el material documental. Tampoco hay `.xlsx`/`.xls` en el repositorio. **No se afirma haber validado plantillas físicas de PICAN.**

---

## 2. Reglas CU-18 extraídas del DCUS

- Actor principal: **Administrador del sistema**. Precondición: sesión iniciada, cuenta activa y archivo `.xlsx`.
- Ruta: Inicio → Administración → Importar datos.
- Tipos admitidos: las **cuatro tablas oficiales** de la presentación. Los "tipos de datos operativos" se mencionan como categoría, pero **no hay ningún tipo operativo expresamente definido**; por tanto no se implementará ninguno.
- Columnas deben coincidir con la plantilla del tipo seleccionado (VAL-95). Errores estructurales detienen la importación sin escribir.
- Filas válidas se cargan; filas inválidas se rechazan con fila y motivo (VAL-96); filas repetidas se omiten y se cuentan (VAL-97).
- No se transforman valores para hacerlos coincidir con otra fuente.
- Contenido diferente con la misma clave: **no sobrescribir**; el documento dice que debe acordarse con PICAN. Se tratará como fila rechazada con motivo explícito, sin inventar versión nueva.
- La lectura previa y la confirmación deben comprobar de nuevo que la cuenta siga activa y conserva el rol de Administrador.
- No se persiste nada hasta la confirmación.
- Se registra usuario, archivo, tipo, fecha/hora y cantidades de válidas, rechazadas y repetidas.
- Reglas: VAL-94 (tipo y archivo obligatorios, extensión `.xlsx`), VAL-95 (columnas), VAL-96 (filas), VAL-97 (repetidas).

---

## 3. Estructura oficial disponible (sin inventar)

### Tablas 1 a 3 — `indicador_historico`

Columnas existentes: `id`, `tabla`, `indicador`, `anio`, `valor`, `unidad_medida`, `fuente`.

Valores permitidos: los ya cargados por `ReferenciasHistoricasInitializer` para:

1. `INFRAESTRUCTURA` — 5 indicadores × años 2015–2019.
2. `EFICIENCIA_PRODUCTIVA` — 7 indicadores × años 2015, 2017 y 2019.
3. `DESTINOS_PROYECCIONES` — 7 renglones × años 2021, 2025 y 2030.

Clave natural existente: `(tabla, indicador, anio)`. El `id` técnico ya sigue el formato `T1|T2|T3-<anio>-<índice>-<hashCode>`, que se reutiliza; no se crea una clave nueva.

### Tabla 4 — `meta_estrategica`

Columnas existentes: `id`, `indicador`, `valor_real_2019`, `valor_estimado_2030`, `incremento_porcentual`, `unidad_medida`, `fuente`.

Valores permitidos: los dos renglones oficiales ya cargados (`T4-HUEVOS`, `T4-CARNE`), con los incrementos recalculados aprobados `13.92` y `731.56`. Clave natural existente: `indicador` (o `id` técnico ya definido).

**No se agregarán columnas, entidades, tablas ni claves.** La "plantilla" en código será una vista normalized de estas estructuras ya aprobadas, no un diseño nuevo.

---

## 4. Decisión de biblioteca: sin Apache POI

| Opción | Evaluación |
|---|---|
| Apache POI (`poi-ooxml`) | No está en `lib/`; exigiría añadir varias dependencias transitivas y descargas externas. La compilación actual solo usa `javac -cp lib\*`. No es compatible con el requisito de ejecutar en equipos modestos y sin infraestructura costosa sin justificación adicional. |
| **JDK 21 estándar (`java.util.zip` + StAX)** | **Seleccionada.** Java 21.0.6 ya está instalado. Permite leer `.xlsx` como ZIP + XML sin franquicias ni dependencias nuevas. Cubre celdas compartidas, celdas en línea, números y fechas seriales del rango oficial. |

**Justificación:** no se añade ninguna dependencia; el lector queda auditable y determinista; se mantiene `lib/` con un solo jar (PostgreSQL). Si PICAN entrega plantillas con características avanzadas no cubiertas, se reevaluará POI con una justificación explícita.

---

## 5. Seguridad del archivo

El lector aplicará, antes de interpretar celdas:

1. Extensión exacta `.xlsx` (case-insensitive), rechazando `.xls`, `.xlsm` y sin extensión.
2. Tamaño máximo configurado y número máximo de entradas/celdas para evitar bombas de descompresión.
3. Nombres de entradas ZIP sin `..`, sin barra inicial y restringidos al conjunto OOXML esperado.
4. `XMLInputFactory` con `SUPPORT_DTD=false` y `IS_SUPPORTING_EXTERNAL_ENTITIES=false`; nunca se resuelven entidades externas.
5. Sin ejecución de macros, fórmulas ni contenido activo: solo se leen valores calculados o literales.
6. El archivo se lee desde la ruta elegida por el Administrador; no se copia a ubicaciones compartidas ni se guarda su contenido.

Errores estructurales (archivo ilegible, ZIP inválido, hoja ausente, cabeceras incorrectas) detienen el proceso **antes de cualquier escritura**.

---

## 6. Arquitectura por capas prevista

| Capa | Responsabilidad | Paquete/clase prevista |
|---|---|---|
| Lectura | Validar archivo y devolver filas crudas | `cu.pican.importacion.LectorXlsx` |
| Plantilla | Mapear tipo seleccionado a columnas/valores oficiales | `TipoImportacion` + `PlantillaReferencias` |
| Validación | Clasificar filas en válida / rechazada / repetida | `ValidadorImportacion` |
| Servicio | Autorizar CU-18, orquestar vista previa y confirmación, recomprobar sesión | `ImportacionService extends ServicioAutorizado` |
| Persistencia | Insertar solo si la clave natural no existe, en MEMORY y PostgreSQL | `ImportacionReferenciasRepository` + implementaciones |
| Interfaz | Selección de tipo/archivo, vista previa, confirmación y resumen | `ImportacionAdminPanel` (pestaña nueva en `AdminModulePanel`) |

`PICAN-SYNC 1.0` (`cu.pican.sync`) **no se modifica**.

---

## 7. Alcance y límites de esta Fase 0

- No se escribió código de importación todavía.
- No se declara compatibilidad con archivos Excel reales de PICAN porque **no se entregaron plantillas físicas**.
- No se implementarán tipos operativos: el DCUS no los define.
- No se sobrescribirán registros existentes; las diferencias con la misma clave se rechazan con motivo.
- No se tocarán catálogos, vales, recepciones, informes ni sincronización.
- DIRECTIVO sigue sin cuenta; PRODUCCIÓN, JEFE y ESTADÍSTICA no podrán ni leer para importar ni confirmar.

---

## 8. Condición para pasar a la Fase 1

Se considera cumplida la Fase 0 porque ya están identificadas: reglas documentales, estructura oficial existente, ausencia de plantillas físicas, decisión de dependencias, diseño por capas, seguridad de archivo y criterios de prueba. Se puede iniciar la implementación sin inventar reglas empresariales.

---

## 9. Nota posterior al cierre

La implementación derivada de este diagnóstico quedó completada y verificada en la Etapa 4.17. Consultar `PROGRESO_ETAPA_4.17.md`, `PRUEBAS_ETAPA_4.17.md` e `INFORME_FINAL_ETAPA_4.17.md`.Este diagnóstico conserva el estado \"antes de programar\" como registro de la decisión original.
