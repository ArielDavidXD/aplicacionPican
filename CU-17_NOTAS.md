# CU-17 — Notas de implementación y fuentes pendientes

## Fuentes revisadas

- `DCUS_PICAN_objetivos_empresariales(1).docx`: indica que PICAN mantiene relación con **28 unidades no estatales asociadas** y describe los catálogos que debe administrar CU-17. No incluye el listado de códigos ni denominaciones de esas 28 unidades.
- `Ing Software 3 Bioproductos.pptx` y `Modelo Caso de uso de Negocio Presentacion.pptx`: revisados; no contienen el listado nominal de las 28 unidades.
- `Modelo Caso de uso de Negocio.docx` / `MCUS-Bioproductos-LABIOFAM.docx`: corresponden a LABIOFAM bioproductos; no se mezclan con PICAN sin aprobación.

## Pendiente documental

- Obtener de PICAN la fuente oficial con **códigos y denominaciones** de las 28 unidades no estatales asociadas. Hasta entonces el catálogo `UnidadAsociada` queda vacío en el arranque y preparado para registrar sólo datos confirmados.
- Definir y aprobar el **vínculo Unidad Asociada – UEB**. No se asignan unidades a UEB por suposición.
- Confirmar si la **unidad de medida** del producto será obligatoria. Hoy es opcional, según decisión previa.
- La restricción de unicidad de códigos equivalente a la comparación en memoria ya está aplicada en V1 (`uq_unidad_asociada_codigo`, `lower(btrim(codigo))`); no requiere nueva migración.

## Decisiones aplicadas

- Los destinos históricos de la Tabla clave 3 son referencia histórica; no se convierten en asignaciones operativas por UEB.
- Las relaciones producto-destino usadas por vales no se eliminan ni cambian de producto/destino; pueden desactivarse.
- Un elemento desactivado no puede elegirse en nuevas operaciones. Las consultas históricas no se bloquean.
- La baja física sólo se permite cuando el registro no tiene operaciones asociadas.
