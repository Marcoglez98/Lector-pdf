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

## Hojas 1.4 — Cinta Viva (efectos de libro físico)
- Una cinta estrecha de tela roja está sujeta al borde superior, cerca del lomo, y llega hasta la parte inferior de la hoja sin tapar las líneas centrales del texto.
- Al colocar una cinta nueva, se despliega desde arriba como si cayera por gravedad; se balancea y rebota suavemente antes de quedar reposando sobre el papel.
- Al seleccionar una cinta de la bandeja, el visor muestra una apertura de varias hojas con sombras y un abanico progresivo que se desplaza hacia el lado izquierdo si avanzas o al lado derecho si retrocedes.
- La apertura se simula con entre 5 y 15 hojas para mantener fluidez en Android sin procesar decenas o centenares de páginas. El destino sí es la página real del PDF.
- La cinta oscila al asentarse después de abrir el separador. Las notas, favoritos y posición siguen guardados por libro; en pantalla completa no se muestran adornos.
- La compilación de prueba tiene el identificador independiente `com.lecturas.hojas.cintaviva` para no interferir con las versiones anteriores.

## Hojas Estable 1.5 — firma permanente para actualizaciones

**Diagnóstico:** las versiones 1.0–1.4 generadas anteriormente en GitHub Actions estaban firmadas con claves de depuración distintas. Android exige el mismo identificador de aplicación, la misma firma y un versionCode adecuado para poder actualizar en sitio. Las claves privadas antiguas no fueron conservadas en el repositorio y no se pueden recuperar a partir de los APK; por ello no es posible actualizar directamente una instalación antigua sin su clave de origen.

**Solución:** la variante `estable` tiene el identificador permanente `com.lecturas.hojas.estable`, y desde 1.5 se firma con un certificado RSA-3072 fijo (SHA-256: `cb849c052bd37a589d37e23cb580185c249fce172cf4ed7797c828ead347163a`). Se puede instalar junto a las anteriores. **A partir de esta instalación, las versiones firmadas con la misma clave y un versionCode mayor sí podrán actualizarse conservando sus datos.**

### Habilitar la compilación firmada en GitHub (solo una vez)

1. Guarda de forma privada el respaldo del almacén de claves `hojas-stable-signing.p12` y el archivo `CLAVE-HOJAS-FIRMA-IMPORTANTE.txt` entregados por el asistente. Nunca los subas al repositorio público ni compartas la contraseña.
2. Abre el repositorio en GitHub → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**.
3. Nombre: `HOJAS_SIGNING_PASSWORD`. Valor: la contraseña de firma que aparece en tu archivo privado.
4. Después ve a **Actions** → **Hojas APK estable firmado (actualizable)** → **Run workflow**. Obtendrás el archivo `Hojas-Estable-APK-Firmado` en *Artifacts*.

El fichero `signing/hojas-stable-signing.enc.b64` que se conserva en GitHub es **un respaldo CIFRADO**, no la clave en texto claro. El flujo lo descifra temporalmente únicamente en el ejecutor, usa la contraseña como variable secreta y la borra al terminar. La clave de la variante estable no se coloca en el repositorio público.

No confundas los archivos del workflow de **depuración** con los APK de Hojas Estable. Estos primeros NO sirven como actualización de la versión estable.

**Almacenamiento:** las versiones anteriores conservan sus favoritos y cintas en sus respectivas instalaciones; esos datos no se migran automáticamente entre aplicaciones con identificadores diferentes. No las desinstales sin considerar la pérdida de sus datos.

## Hojas 1.6 — Neón Literario
- **Tema Neón Literario**: fondo carbón violeta, magenta y cian luminosos, tipografía de biblioteca renovada, botones con degradados y cinta magenta.
- **Selector de estilos**: desde «✦» o Ajustes > Cambiar apariencia, alterna entre «Neón Literario» y «Clásico». Los otros estilos del catálogo visual podrán añadirse posteriormente.
- **Biblioteca Neón funcional**: muestra PDF abiertos, miniatura generada localmente desde su primera página, barra de progreso, búsqueda por título y filtros Leyendo y Favoritos.
- **Quitar cinta**: aparece «− Quitar» junto a «＋ Cinta»; también se puede tocar/mantener pulsada una cinta para abrir su menú, confirmar que se retira y guardar el cambio.
- Se conservan narrador TTS, modo libro, lupa, marcador, borrador, cintas animadas, lectura sin distracciones y favoritos.
- La variante `neon` usa `com.lecturas.hojas.neon` para probar sin reemplazar instalaciones anteriores. Sus archivos y preferencias serán independientes.
- La variante `estable` usa el identificador `com.lecturas.hojas.estable` ya empleado en la versión estable 1.5; requiere **el mismo certificado de firma permanente** para actualizar sin borrar datos.

## Hojas 1.7 — Cinco apariencias y eliminación de cintas
- Selector **Temas** con cinco estilos: Clásico (original), Neón Literario, Aurora Literaria, Jardín Bohemio y Talavera Pop. Cada tarjeta muestra su paleta de colores y se aplica al seleccionarla.
- Bibliotecas para los cuatro estilos nuevos: fondos, tarjetas y encabezados tematizados, portadas reales extraídas de tus PDF, búsqueda, progreso, favoritos y lectura offline.
- El PDF en modo Libro conserva sus páginas; el estilo colorea el marco, la barra de herramientas, los botones y la cinta sin tapar el texto.
- **Corrección de cintas:** «− Quitar» muestra todas las cintas del libro (no solo la de la página actual), pide confirmación y la elimina realmente de preferencias, de la hoja y de la bandeja.
- Menú de acciones de la cinta arreglado: al tocar la cinta sobre el papel puedes abrirla, editar su nota o retirarla; mantener pulsada la cinta muestra la opción de eliminar.
- La versión de prueba `temas` usa identificador `com.lecturas.hojas.temas` para instalarse junto con las anteriores.
- La variante `estable` se actualiza sobre Hojas Estable 1.5/1.6 si se firma con el certificado permanente existente; conserva los datos guardados en ese identificador de aplicación.
