# Diseño CU-19 — Copias de seguridad y restauración

Fecha: 2026-11-20  
Fase: 1 (diseño previo a la implementación)  
Proyecto: `aplicacionPican`

## 1. Principios

- CU-19 es exclusivo de `ADMINISTRADOR`. La autoridad central `CasoUso.CU_19` y una comprobación explícita de sesión/rol en `BackupService`.
- No se crea superusuario, rol nuevo, relación usuario↔UEB ni UEB predeterminada.
- No se modifica la matriz CU-01…CU-18. Se agrega CU-19 a la pestaña existente `administracion`, sin nuevo `moduleId`.
- Toda generación/restauración es manual y explícita. No hay copia diaria automática ni retención inventada.
- Ningún resultado de éxito se publica antes de verificar la estructura final.

## 2. Archivo de copia

Formato propio y reconocible:

```
pican-backup-<base>_<yyyyMMdd_HHmmss>.zip
├── BACKUP.dump       volcado real de pg_dump --format=custom
├── MANIFEST.json     metadatos y checksum del volcado
└── INTEGRIDAD.sha256 SHA-256 de BACKUP.dump y MANIFEST.json
```

El manifiesto contiene: `formato=PICAN-BACKUP`, `versionFormato=1`, versión de aplicación, versión de esquema, lista de migraciones con checksum, fecha, base de origen, id de instalación, modo, usuario generador, tamaño y SHA-256 del volcado. No contiene URL, usuario de base ni contraseña.

`BackupArchivo` aplica whitelist de entradas, rechaza rutas relativas, entradas duplicadas y tamaños desproporcionados. No extrae entradas al disco.

Se reutilizan `SyncJson.escribir/parsearObjeto` y `SyncArchivo.sha256/hashCoincide` sin modificar `cu.pican.sync`.

## 3. Compatibilidad (VAL-100)

`ValidadorCopia.inspeccionar` comprueba, sin modificar la base:

1. Que el ZIP contenga exactamente las tres entradas esperadas.
2. Que `INTEGRIDAD.sha256` coincida con el contenido real.
3. Que el manifiesto declare el formato y versión soportados.
4. Que el tamaño y SHA-256 del manifiesto coincidan con el volcado.
5. Que las migraciones declaradas sean conocidas localmente y tengan el mismo checksum.
6. Que la versión de aplicación de la copia no sea superior a la actual.
7. Que el esquema de la copia no supere al local.
8. Si hay conexión PostgreSQL, que esas migraciones estén aplicadas en la base activa con el mismo checksum.

Copias V1+V2 pueden restaurarse: se restaura el volcado y después se aplica V3. No se aceptan migraciones desconocidas ni checksums alterados.

## 4. Generación manual (VAL-98)

1. Verificar rol ADMINISTRADOR y sesión activa.
2. Verificar que el directorio existe, es escribible y dispone de espacio mínimo.
3. Ejecutar `pg_dump` real con `ProcessBuilder` en lista de argumentos, `PGPASSWORD` únicamente en el entorno del hijo, `--no-password`, `PGCONNECT_TIMEOUT`, timeout y salida saneada.
4. Verificar espacio restante antes de publicar.
5. Calcular SHA-256, construir manifiesto y ZIP en un temporal dentro de la carpeta elegida.
6. Reinspeccionar el ZIP con `ValidadorCopia`.
7. Publicar con `ATOMIC_MOVE` (o movimiento simple si el sistema no lo soporta), sin sobrescribir copias anteriores.
8. Registrar auditoría `CREACION/EXITOSO` con archivo, fecha, tamaño, hash, versión e instalación.

## 5. Restauración segura (VAL-99)

1. Revalidar ADMINISTRADOR.
2. Inspeccionar la copia antes de pedir confirmaciones.
3. Solicitar dos confirmaciones independientes en la UI: diálogo de aceptación y escritura literal de `RESTAURAR`. El servicio exige ambos booleanos.
4. Identificar la base activa con `getCatalog()`.
5. Bloquear conexiones y terminar las demás sesiones de la base activa.
6. Crear base de preparación, restaurar con `pg_restore --single-transaction --exit-on-error` y verificar estructura y migraciones.
7. Crear base de seguridad copiando la activa con `CREATE DATABASE ... TEMPLATE ...`.
8. Intercambiar nombres de forma controlada: activa→antigua, preparación→activa.
9. Habilitar conexiones, verificar la nueva base, aplicar V3 si falta y volver a verificar.
10. Activar bloqueo SIN-13 si el modo es `LOCAL`, registrar auditoría y solo entonces borrar seguridad y antigua.

Si falla cualquier paso posterior al intercambio, se revierte: activa→fallida, antigua→activa, conexiones habilitadas y verificación final. La base fallida se conserva para diagnóstico; nunca se elimina la antigua ni la seguridad antes de verificar la nueva.

No se simula rollback JDBC de `pg_restore`: se usa transacción de `pg_restore` y bases temporales aisladas.

## 6. Auditoría

Migración nueva `V3__backup_auditoria.sql`:

- `pican_backup_auditoria`: id, usuario, operación, archivo, fecha, resultado, motivo de fallo, tamaño, SHA-256, versión de aplicación/esquema, instalación.
- `pican_restauracion_control`: estado de bloqueo SIN-13, motivo, fecha, id de auditoría e instalación.

El historial nunca se elimina. No existe auditoría general previa reutilizable.

## 7. SIN-13

Se implementa solo el bloqueo local posterior a restauración en instalaciones `LOCAL`:

- `ValeEntregaService.emitir` y `generarNumeroVale(UUID)` rechazan con `ValidacionException`.
- `SyncService.generarExportacion` rechaza con `SyncException`.
- El proveedor se inyecta desde `AppContext` y consulta `pican_restauracion_control` solo si el modo es `LOCAL`.

No se cambia el formato de paquetes PICAN-SYNC 1.0. Queda pendiente la bajada completa del central con `ultimo_vale_conocido` y última secuencia; no se inventa un procedimiento alternativo ni desbloqueo automático.

## 8. UI

Nueva pestaña `Copias de seguridad` dentro de `AdminModulePanel`. Usa `SwingWorker` para no bloquear el EDT. Dos confirmaciones independientes antes de restaurar. Muestra integridad, compatibilidad, manifiesto, auditoría y mensajes saneados.

## 9. Seguridad de credenciales

Las contraseñas solo se aceptan por `-D`/env. Nunca se escriben en argumentos del proceso hijo, manifiestos, logs, excepciones, auditoría ni documentos. `sanitizar` reemplaza la contraseña configurada y cualquier `PGPASSWORD=...` por `***`.
