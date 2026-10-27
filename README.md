# Hojas — Lector PDF para Android

Proyecto Android en Java para leer PDF como un libro físico, con transiciones suaves de página y modo de texto ajustable.

## Funciones
- **Modo libro:** visualiza el PDF original; desliza lateralmente para pasar o retroceder hojas.
- **Modo texto:** aumenta o reduce el tamaño de la letra y selecciona frases con una pulsación prolongada.
- **Resaltados y favoritos:** guarda frases por documento y página; compártelas o vuelve a ellas.
- **Comodidad:** modo noche, fondo papel cálido, zoom y continuación de la lectura.
- Lectura local sin anuncios ni conexión a Internet.

## Descargar la aplicación (APK)
1. Abre la pestaña **Actions**.
2. Entra en la ejecución más reciente **Compilar APK Android**.
3. Cuando termine correctamente, descarga el artefacto **Hojas-Lector-PDF-APK**.
4. Extrae el archivo `app-debug.apk` del ZIP y ábrelo desde tu teléfono.

## Código fuente
El proyecto completo está disponible directamente en `app/src/main/java/com/lecturas/hojas/`, con los archivos `MainActivity.java` y `PageCurlView.java`. Las carpetas `app/` y `.github/workflows/` permiten editar y recompilar desde GitHub.

## Notas
Los favoritos se guardan en la aplicación, no modifican el PDF original. Los PDF escaneados sin capa de texto requieren OCR para seleccionar frases. Esta versión aún debe verificarse con una compilación y pruebas en Android.

## Novedades 1.1
- Narración de páginas con **voz seleccionable** según el motor TTS del teléfono, avance continuo y control de velocidad.
- Botón **Descargar voces** que abre el instalador oficial de datos de voz del motor del sistema. Las voces disponibles y su calidad dependen del teléfono; algunas requieren conexión y no pueden descargarse.
- En **Modo libro**, activa **Marcador**, toca y arrastra desde la primera a la última palabra para subrayar con efecto de tinta. Las frases quedan en Favoritos.
- Activa **Borrador** y toca una marca para retirarla con animación; se elimina también de Favoritos.
- La detección de palabras en PDF escaneados sin texto, documentos rotados o con diseño complejo puede ser limitada.
- Atención: el APK de depuración compilado en distintos ejecutores de GitHub puede usar certificados de firma diferentes. Si Android rechaza instalarlo como actualización, conserva una copia de tus favoritos antes de desinstalar la versión anterior.
