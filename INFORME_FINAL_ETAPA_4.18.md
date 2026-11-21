# Informe Final — Etapa 4.18 / CU-19

Fecha: 2026-11-20  
Proyecto: `aplicacionPican`  
Resultado: **aprobado con limitación documentada en SIN-13**.

## 1. Cumplimiento

CU-19 quedó implementado como una capacidad exclusiva de `ADMINISTRADOR`: generación manual de copias reales de PostgreSQL, inspección sin escribir, restauración segura, auditoría persistente y bloqueo local posterior a restauración.

No se modificó el HEAD ni el historial Git; no hubo commits, push, reset ni operaciones destructivas. `aplicacionPican/` continúa sin seguimiento.

## 2. Entregables

| Archivo | Propósito |
|---|---|
| `DIAGNOSTICO_CU19_FASE_0.md` | Diagnóstico previo a programar |
| `DISENO_CU19.md` | Arquitectura, formato y decisiones |
| `PROGRESO_ETAPA_4.18.md` | Avance por fases |
| `PRUEBAS_ETAPA_4.18.md` | Comandos y resultados |
| `MANUAL_COPIAS_SEGURIDAD_PICAN.md` | Operación segura para el administrador |
| `sql/migrations/V3__backup_auditoria.sql` | Auditoría y control SIN-13 |
| `src/cu/pican/backup/` | Motor, formato ZIP, validación y operaciones PostgreSQL |
| `src/cu/pican/service/BackupService.java` | Orquestador de CU-19 |
| `src/cu/pican/view/modules/CopiasSeguridadPanel.java` | Pestaña de administración |

## 3. Decisiones respetadas

- Sin superusuario, roles nuevos ni relación usuario↔UEB.
- Sin UEB predeterminada.
- Sin copia automática diaria ni retención inventada.
- Matriz CU-01…CU-18 intacta; CU-19 agregado a la pestaña `administracion` sin nuevo `moduleId`.
- V1 y V2 intactos; V3 es una migración nueva.
- PICAN-SYNC 1.0 y el formato de paquetes sin cambios.
- Contraseñas solo por `-D`/env; nunca en argumentos de `pg_dump`/`pg_restore`, manifiestos, logs ni documentos.
- Pruebas solo en bases temporales con `test` en el nombre; ninguna restauración sobre bases reales.

## 4. Verificación

- Compilación: 228 fuentes.
- CU-19: 15/15 MEMORY y 23/23 POSTGRES.
- Regresiones afectadas: Permisos 6/6, Autorización 21/21 MEMORY y 21/21 POSTGRES, AppContext 12/12.
- Total: 98 comprobaciones aprobadas.
- Base temporal de restauración eliminada al finalizar.

La restauración real incluyó `pg_dump` custom, ZIP con SHA-256, verificación antes de sustituir, base de seguridad, renombrado controlado, aplicación de V3 y verificación final. El caso de fallo tras el intercambio se ejerció con operaciones falsas: la base anterior se reactivó, la base fallida se conservó y el evento quedó registrado como `FALLIDO`.

## 5. Limitación SIN-13

El bloqueo local exigido por SIN-13 sí quedó implementado: tras una restauración en `LOCAL`, `ValeEntregaService` y `SyncService.generarExportacion` rechazan la operación hasta que el control indique lo contrario.

No se implementó el procedimiento completo de reanudación porque PICAN-SYNC 1.0 no define el paquete de bajada del central, ni `ultimo_vale_conocido`, ni la última secuencia central. Cambiar el protocolo o inventar un procedimiento alternativo queda fuera de la decisión aprobada. Por ello no existe un botón de desbloqueo automático.

## 6. No ejecutado

- Pruebas manuales GUI.
- Arneses funcionales no afectados de la Etapa 4.15.
- Reejecución de etapas cerradas 4.16 y 4.17 más allá de las regresiones afectadas.

## 7. Conclusión

La Etapa 4.18 cumple el alcance aprobado de copias manuales reales, restauración segura, auditoría y autorización. La única reserva es la parte de SIN-13 dependiente de un contrato de sincronización que hoy no existe; está documentada y no se simuló.
