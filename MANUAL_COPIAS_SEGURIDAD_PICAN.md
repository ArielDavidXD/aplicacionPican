# Manual de copias de seguridad PICAN — CU-19

Público: **ADMINISTRADOR** de una instalación `aplicacionPican`.  
Etapa: 4.18.

## 1. Dónde está

Menú **Administración → pestaña «Copias de seguridad»**.

Si la sesión no es de ADMINISTRADOR, los botones están deshabilitados y el servicio rechaza cualquier llamada directa. No hay superusuario ni roles nuevos.

## 2. Requisitos

- PostgreSQL accesible con el usuario configurado. El modo MEMORY no realiza copias reales y lo indica explícitamente.
- `pg_dump` y `pg_restore` disponibles. Se buscan en `PATH`, en instalaciones habituales de PostgreSQL y, si hace falta, en:
  - `-Dpican.backup.pg.bin=<directorio bin>`
  - o variable de entorno `PICAN_PG_BIN`.
- Permisos de escritura y espacio suficiente en la carpeta de destino.
- Contraseña de base solo por `-Dpican.db.password=...` o `PICAN_DB_PASSWORD`. Nunca pegarla en el manifiesto, en un documento ni en un argumento visible de `pg_dump`/`pg_restore`.

## 3. Crear una copia manual

1. Seleccionar la carpeta de destino con **Examinar...** en «Carpeta de destino de la copia».
2. Pulsar **Crear copia manual**.
3. El sistema ejecuta `pg_dump`, construye el ZIP, calcula SHA-256, lo reinspecciona y lo publica sin sobrescribir copias anteriores.
4. Revisar en el panel la ruta publicada, el tamaño y el hash.

Archivo generado:

```
pican-backup-<base>_<yyyyMMdd_HHmmss>.zip
├── BACKUP.dump
├── MANIFEST.json
└── INTEGRIDAD.sha256
```

No hay copia automática diaria. La periodicidad y la retención las define la organización; el sistema no inventa una política.

## 4. Verificar una copia

1. Seleccionar el `.zip` con **Examinar...** en «Archivo de copia (.zip)».
2. Pulsar **Verificar copia**.

Se comprueba integridad SHA-256, formato `PICAN-BACKUP` v1, tamaño, hash del manifiesto, migraciones conocidas con checksum idéntico, versión de aplicación no superior y esquema no superior al local. Esta operación no modifica la base.

## 5. Restaurar una copia

**Advertencia: la restauración sustituye la base actual.** Solo debe hacerse sobre una instalación de prueba o con una copia de seguridad verificada.

1. Seleccionar el `.zip`.
2. Pulsar **Verificar copia** y confirmar que diga que es íntegra y compatible.
3. Pulsar **Restaurar copia**.
4. Primera confirmación: aceptar el diálogo de advertencia.
5. Segunda confirmación: escribir exactamente `RESTAURAR`.
6. Esperar a que termine; las operaciones largas se ejecutan en segundo plano.

Durante el proceso el sistema:

- bloquea nuevas conexiones y cierra las sesiones ajenas de la base activa;
- crea una base de preparación y restaura allí primero;
- verifica estructura y migraciones antes de sustituir;
- crea una base de seguridad a partir de la activa;
- intercambia los nombres de forma controlada;
- aplica la migración V3 si la copia venía de V1+V2;
- verifica la base definitiva antes de declarar éxito;
- solo entonces elimina las bases temporales de seguridad y anterior.

Si algo falla después del intercambio, se reactiva la base anterior, se conserva una base `*_fall_*` para diagnóstico y se registra el fallo. Nunca se elimina la copia anterior de archivo.

## 6. Después de restaurar en una UEB (`LOCAL`)

Queda activo el bloqueo SIN-13:

- no se pueden emitir vales de entrega;
- no se pueden generar paquetes de sincronización.

El propósito es evitar que la UEB vuelva a operar con contadores locales desactualizados antes de recibir del central la bajada completa con `ultimo_vale_conocido` y la última secuencia central.

Esa bajada **no está disponible en PICAN-SYNC 1.0**, por lo que el sistema no ofrece un desbloqueo inventado. La reanudación debe acordarse con el responsable del protocolo central. No se cambió el formato de paquetes ni se agregó un procedimiento alternativo.

En una instalación `CENTRAL`, el mismo control no bloquea estas operaciones.

## 7. Auditoría

El botón **Ver auditoría** muestra el historial completo. Cada evento registra:

- usuario;
- operación (`CREACION` o `RESTAURACION`);
- archivo;
- fecha y hora;
- resultado (`EXITOSO`, `FALLIDO` o `CANCELADO`);
- motivo de fallo cuando aplica;
- tamaño, SHA-256, versión de aplicación/esquema e instalación.

El historial no se elimina.

## 8. Buenas prácticas

- Verificar siempre la copia antes de restaurarla.
- Probar primero la restauración en una base temporal con `test` en el nombre.
- Conservar varias copias en ubicaciones independientes.
- No editar `MANIFEST.json`, `INTEGRIDAD.sha256` ni `BACKUP.dump` dentro del ZIP.
- No compartir la contraseña de base por correo ni incluirla en actas o tickets.
- Si la restauración falla, no borrar la base `*_fall_*` hasta diagnosticar el motivo.

## 9. Limitaciones

- No hay cifrado autenticado ni firma digital del ZIP; solo SHA-256. La protección del archivo depende de la ubicación y permisos del sistema de archivos.
- No hay copia automática ni retención automática.
- La bajada completa del central exigida por SIN-13 está pendiente por decisión de no modificar PICAN-SYNC 1.0 sin aprobación.
- Las pruebas manuales GUI no fueron ejecutadas en esta etapa.
