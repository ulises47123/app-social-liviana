# Face (app social liviana)

Visor Android ultraliviano (Java puro, sin dependencias, APK de unos 23 KB) de la versión web móvil de Facebook
(`m.facebook.com`), pensado para celulares con poca memoria. No es una app oficial de Facebook ni está afiliada a Meta:
es un WebView que abre el sitio público.

## Funciones

- Abre `m.facebook.com` con sesión guardada por las cookies del WebView.
- Mensajes: Facebook bloquea los chats en navegadores de celular, así que se abren dentro de la app con la versión
  de computadora (`www.facebook.com/messages`), y al salir se vuelve a la versión de celular. Desde el menú se puede
  elegir abrirlos en Messenger.
- Página propia de "Sin conexión" con botón Reintentar.
- Oculta los carteles de "Abrir la app / Descargar la app".
- Recibe fotos y videos compartidos desde la galería para publicarlos, y enlaces para compartir con el diálogo
  oficial `sharer.php`.
- El botón "Compartir" abre el menú de Android (WhatsApp, etc.).

## Descargar e instalar

**[Descargar Face.apk](https://github.com/ulises47123/app-social-liviana/releases/latest/download/Face.apk)** (versión 1.1.1, unos 23 KB, Android 7 o superior)

1. Abrí el enlace desde el celular y descargá `Face.apk`.
2. Tocá el archivo descargado (queda en la carpeta Descargas).
3. Si Android avisa que no permite instalar apps de esa fuente, entrá en **Configuración** en ese mismo cartel,
   activá **Permitir desde esta fuente** y volvé atrás.
4. Tocá **Instalar** y después **Abrir**.
5. Iniciá sesión con tu cuenta de Facebook. La sesión queda guardada en la app.

Para actualizar, instalá el APK nuevo encima: no se pierde la sesión.

Desde una PC con el celular conectado por adb:

```bash
adb install -r Face.apk
```

Uso: el botón **Atrás** en la pantalla de inicio abre el menú (Inicio, Mensajes, Recargar, Ahorrar datos,
Compartir esta página, Salir). La primera vez que se abren los mensajes tardan en cargar en celulares lentos,
porque se usa la versión de computadora de Facebook.

## Compilar

Requisitos: JDK 17 y Android SDK (minSdk 24, targetSdk 35).

```bash
bash tools/compilar.sh            # debug  -> app/build/outputs/apk/debug/app-debug.apk (usa --offline)
bash tools/compilar.sh release
```

`tools/env.sh` toma el JDK y el SDK de la carpeta indicada en la variable `HERRAMIENTAS` (con `jdk/` y `android-sdk/`).
`local.properties` no se sube: lo crea Android Studio o se escribe a mano con `sdk.dir=<ruta al Android SDK>`.
