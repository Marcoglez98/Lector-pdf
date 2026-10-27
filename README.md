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
