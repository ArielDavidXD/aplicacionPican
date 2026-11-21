# INFORME FINAL — ETAPA 4.16 (PICAN)

**Fecha:** 2026-11-20  
**Ubicación:** `C:\Users\Ariel David\Desktop\bioproductos\aplicacionPican\`  
**Objetivo:** completar la autorización integral por roles sin rehacer funcionalidades aprobadas en la Etapa 4.15.

---

## 1. Conclusión ejecutiva

La Etapa 4.16 quedó **completada y verificada**. La autorización dejó de estar solo en el menú y en `SyncService`: ahora una autoridad central decide en cada servicio de negocio, con la misma matriz que filtra la interfaz y bloquea la navegación directa.

Se respetaron los roles documentados y se reforzó que `ADMINISTRADOR` **no es superusuario**. No se crearon tablas, roles nuevos, vínculos usuario↔UEB, UEB predeterminadas ni reglas empresariales no aprobadas.

**CU-18 no se declara implementado.** Queda reservado al ADMINISTRADOR en la matriz y se aborda en la Etapa 4.17.

---

## 2. Cumplimiento por fases

| Fase | Contenido | Estado |
|---|---|---|
| 0 | Diagnóstico previo y matriz real `Módulo/CU | Rol | UI | Servicio | Estado` | Completada |
| 1 | Autoridad central: `CasoUso`, `AutorizacionService`, `AutorizacionException`, proveedor de sesión | Completada |
| 2 | Protección de servicios de catálogos, producción, vales, recepciones, incidencias, análisis, informes y usuarios | Completada |
| 3 | Matriz UI derivada, navegación segura y módulo de referencias | Completada |
| 4 | Arnés AUT-01…AUT-21 en MEMORY y POSTGRES | Completada |
| 5 | Regresiones 4.15 afectadas (12/12, 20/20, 14/14, 5/5, 6/6) | Completada |
| 6 | Documentación: matriz, pruebas y progreso | Completada |
| 7 | Pruebas manuales GUI dejadas para su ejecución posterior | No ejecutadas |
| 8 | Informe final y cierre | Completada |

---

## 3. Entregables

### Autoridad y servicios

- `src/cu/pican/service/CasoUso.java` — catálogo CU → roles; CU-18 reservado, CU-04/CU-19 deshabilitados.
- `src/cu/pican/service/AutorizacionService.java`, `AutorizacionException.java`, `SesionAutorizacionProvider.java`.
- `src/cu/pican/service/ServicioAutorizado.java` — autoridad por contexto, sin global compartida entre contextos.
- `src/cu/pican/app/AutorizacionDemo.java` — política de demostración explícita, no permisiva por defecto.
- `src/cu/pican/app/AppContext.java` — instala y reinyecta la autoridad real; `DemoDataInitializer` la sustituye solo temporalmente y la restaura.
- `src/cu/pican/app/UsuarioInicialInitializer.java` — bootstrap sin sesión solo si no existe administrador.
- `src/cu/pican/service/UsuarioService.java` — CU-16, bootstrap y cambio propio de contraseña; la generación de contraseña temporal no exige sesión.
- `src/cu/pican/sync/SyncService.java` — delega en la autoridad central y conserva `SyncException`.

### Interfaz

- `src/cu/pican/view/PermisosInterfaz.java` — matriz derivada de `CasoUso`.
- `src/cu/pican/view/SideMenuPanel.java`, `MainFrame.java` — filtrado y construcción lazy de paneles.
- `src/cu/pican/view/modules/ReferenciasModulePanel.java` — consulta de referencias para PRODUCCIÓN.
- `src/cu/pican/controller/NavigationController.java` — fallo cerrado en navegación directa.

### Arneces y evidencias

- `test/cu/pican/verify/AutorizacionRolesTest.java` — 21 comprobaciones en MEMORY y POSTGRES.
- `test/cu/pican/verify/PermisosInterfazTest.java` — 6 comprobaciones.
- Regresiones 4.15 adaptadas solo donde 4.16 cambió el punto de entrada: `AppContextIntegrationTest`, `ValidacionIntegralPostgresTest`, `SincronizacionPostgresTest` + `SyncTestSupport`, `ServiciosTransaccionalesTest`.
- `test-results/*4.16*.log` — salidas reales sin credenciales.
- `MATRIZ_PERMISOS_PICAN.md`, `PRUEBAS_ETAPA_4.16.md`, `PROGRESO_ETAPA_4.16.md`.

---

## 4. Resultados verificados

| Prueba | Resultado |
|---|---|
| Compilación oficial | 189 fuentes compiladas |
| AUT MEMORY | 21/21 |
| AUT POSTGRES | 21/21 |
| Permisos de interfaz | 6/6 |
| Regresión AppContext | 12/12 |
| Regresión validación integral | 20/20 |
| Regresión sincronización | 14/14 |
| Regresión servicios transaccionales | 5/5 |

Detalle de comandos y cobertura: `PRUEBAS_ETAPA_4.16.md`.

---

## 5. Decisiones y límites

- La sesión de `AuthService` sigue siendo la única identidad; no se introdujo Swing en servicios ni frameworks nuevos.
- Fail closed: sin política instalada, una operación protegida se rechaza.
- Las demos/arneses históricos instalan o restauran explícitamente una política de demostración; la aplicación real no la usa.
- La lectura de catálogos por cualquier cuenta activa es soporte técnico de su CU, no un permiso empresarial nuevo. Las escrituras exigen CU-17.
- No se implementó la restricción territorial del JEFE_DE_UEB: no existe relación usuario↔UEB y no se inventó.
- DIRECTIVO continúa sin cuenta ni rol constante.
- `SyncException` se conserva para la interfaz y los arneses 4.15.
- CU-18, CU-19 y CU-04 no se implementan en esta etapa.

---

## 6. Estado de Git y continuidad

- HEAD sin cambios: `33bdf0d13d9fefe9cdfb36c5377e15e1db9ed0a6`.
- `aplicacionPican/` continúa sin seguimiento. No se hicieron commits, push, reset ni cambios destructivos.
- Copia de seguridad previa disponible en `C:\Users\Ariel David\AppData\Local\Temp\opencode\pican-etapa-4.16-backup-20261120-152720`.

---

## 7. Cierre y siguiente etapa

Con las evidencias anteriores se declara cerrada la **Etapa 4.16**.

La **Etapa 4.17** comenzará con su propia Fase 0: verificar documentación oficial de CU-18, ausencia de plantillas operativas y compatibilidad de Apache POI antes de escribir código. El importador será solo para ADMINISTRADOR, con comprobación en servicio, por capas, sin inventar plantillas, entidades, columnas ni claves. PICAN-SYNC 1.0 no se modificará.
