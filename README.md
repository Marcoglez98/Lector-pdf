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

## Novedades Hojas 1.2 — lectura inmersiva y cintas rojas
- **Pantalla completa**: toca una vez sobre el área de lectura para ocultar las barras y aprovechar toda la pantalla; da **dos toques** para recuperarlas. También puedes entrar con el botón ⛶ superior. El botón Atrás muestra los controles antes de salir del libro.
- **Cintas rojas**: pulsa «＋ Cinta» debajo de las opciones para guardar la página actual, escribir una nota o vincular una frase ya resaltada.
- Las cintas se dibujan como pequeñas tiras de tela color vino, con punta en V, sombra y costuras decorativas. Puedes verlas en la bandeja inferior y, si una página tiene cintas, en su margen superior.
- Toca una cinta de la bandeja para regresar a la página. Mantén pulsado el marcador para editar su nota o retirarlo. Las cintas se almacenan **por PDF** en el teléfono.
- La pantalla completa mantiene la navegación entre páginas. El modo marcador/borrador toma prioridad sobre los gestos para no modificar accidentalmente una frase; usa ⛶ para entrar directamente en lectura inmersiva.

## APK
El workflow de GitHub Actions genera dos variantes:
- **Hojas-1.1-Actualizacion** (identificador original, APK principal, ahora con versión 1.2.0).
- **Hojas-1.1-Narrador-Prueba** (APK de prueba independiente que puedes instalar junto a tu Hojas anterior).

La firma de las APK de depuración puede variar entre ejecuciones. Antes de eliminar versiones anteriores, conserva tus datos; un APK con firma diferente no se instala como actualización directa.

## Versión 1.3 — Libro físico, lupa y transiciones de lectura
- **Libro realista:** vista Libro con fondo de papel, lomo sombreado, capas de hojas, luz en los bordes y la animación habitual al pasar páginas.
- **Pantalla completa:** un toque oculta herramientas y decoración mediante una transición de 410 ms; doble toque devuelve los controles. El PDF vuelve a presentarse sin bordes de libro, centrado y ajustado a la pantalla.
- **Lupa en modo Libro:** mantén pulsado el texto y aparece una lupa redonda con ampliación 2.35×; arrastra para explorar las palabras. Al levantar el dedo se desvanece.
- **Lupa en texto ajustable:** utiliza la lupa nativa de Android 9+ al mantener pulsada una palabra y conserva la selección de texto.
- **Cinta larga:** aparece sobre el centro de la hoja, con textura de satén, costuras y punta en V; toca la cinta para gestionar el punto de lectura. En pantalla completa queda oculta.
- Las cintas siguen siendo por libro y compatibles con las guardadas en la versión anterior.
- La versión de prueba usa identificador de aplicación `com.lecturas.hojas.libroreal` para que pueda instalarse junto a las versiones anteriores.
