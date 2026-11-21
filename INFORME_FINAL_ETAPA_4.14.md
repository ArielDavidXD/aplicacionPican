# INFORME FINAL — ETAPA 4.14 (PICAN)

**Estado: COMPLETADA.** Integración real de PostgreSQL ejecutada por fases, sin repetir el trabajo aprobado previamente.

## Alcance verificado

| Fase | Resultado |
|---|---|
| 0 · Inspección | Scripts oficiales, grafo de servicios y clúster de pruebas identificados. Sin cambios de Git destructivos. |
| 1 · Transacciones JDBC | 10/10 pruebas nuevas + 24/24 del trabajo previo (no repetidas). |
| 2 · AppContext | 12/12 comprobaciones: modo dual, 20 repositorios, persistencia tras reinicio, sin fallback silencioso. |
| 3 · Servicios | 5/5 pruebas; `UsuarioService` hecho transaccional; rollback demostrado con fallo en el segundo write. |
| 4 · Funcional PostgreSQL | 8/8 pruebas + 2 demos reales (catálogos 14/14 secciones y CU-16). |
| 5 · Regresión MEMORY | 14/14 demos automatizadas correctas. |
| 6 · Documentación | Este informe y `PROGRESO_ETAPA_4.14.md`. |

**Total ejecutado en esta tanda: 35/35 comprobaciones de arnés + 16 ejecuciones de demos correctas.**

## Cambios de código relevantes

1. `UsuarioService` acepta `TransactionManager` (constructor sobrecargado) y ejecuta sus 8 escrituras dentro de él. `AppContext` inyecta el gestor JDBC en modo PostgreSQL; el constructor antiguo conserva `NoOpTransactionManager` para demos.
2. `CatalogosOperativosStageDemo` siembra datos de demostración únicamente en modo memoria.
3. Cuatro demos recibieron datos de prueba válidos para la validación heredada de `ParteNave`; `AnalysisStageDemo` conserva el caso `null` insertando directamente en el repositorio; `CatalogosGestionIntegralStageDemo` es consciente del modo de persistencia en su comprobación final.

No se modificaron reglas de negocio, migraciones, esquema, repositorios JDBC validados ni el modo MEMORY de demostración.

## Evidencia

- `test-results/RESULTADOS_TRANSACCIONES.txt`
- `test-results/RESULTADOS_APPCONTEXT.txt`
- `test-results/RESULTADOS_SERVICIOS.txt`
- `test-results/RESULTADOS_FUNCIONAL.txt`
- `test-results/final-*.log` y `test-results/fase4-*.log`, `test-results/fase5-*.log`

## Seguridad y entorno

- Base de pruebas exclusiva, nunca el servicio PostgreSQL instalado en el puerto 5432.
- Las contraseñas temporales generadas por el inicializador fueron eliminadas de los logs.
- `config/pican.properties` no existe en el repositorio; solo la plantilla de ejemplo.

## Limitaciones conocidas

- No se hizo prueba manual de GUI; no fue bloqueante.
- Fuera de alcance: CU-18, CU-19, CU-04, USB, camiones, choferes e inventario.
- Los resultados dependen del clúster temporal de pruebas en `127.0.0.1:55432`; los comandos de reproducción están en `PROGRESO_ETAPA_4.14.md`.
