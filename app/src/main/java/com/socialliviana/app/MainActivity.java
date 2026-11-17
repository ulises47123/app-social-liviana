package com.socialliviana.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.webkit.JavascriptInterface;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

/**
 * Visor liviano de la versión web móvil de Facebook (m.facebook.com).
 * Pensado para celulares de 1 GB: sin bibliotecas extra, sin servicios en segundo plano.
 */
public class MainActivity extends Activity {

    private static final String INICIO = "https://m.facebook.com/";
    private static final String MENSAJES = "https://m.facebook.com/messages/";
    private static final int PEDIDO_ARCHIVO = 10;

    private FrameLayout raiz;
    private WebView web;
    private ProgressBar barra;
    private ValueCallback<Uri[]> esperaArchivo;
    private View vistaPantallaCompleta;
    private WebChromeClient.CustomViewCallback cierrePantallaCompleta;
    private SharedPreferences prefs;
    /** Fotos/videos recibidos desde la galería, esperando a que Facebook pida un archivo. */
    private Uri[] pendientes;

    /** Hace que el botón "Compartir" de Facebook abra el menú de Android (WhatsApp, etc.). */
    private static final String PUENTE_JS = "(function(){if(window.__sl)return;window.__sl=1;"
            + "navigator.share=function(d){d=d||{};SocialLiviano.compartir(String(d.title||''),"
            + "String(d.text||''),String(d.url||location.href));return Promise.resolve();};"
            + "navigator.canShare=function(){return true;};"
            // Bloquea el zoom aunque la página lo permita.
            + "var vp=document.querySelector('meta[name=viewport]');"
            + "if(!vp){vp=document.createElement('meta');vp.name='viewport';(document.head||document.documentElement).appendChild(vp);}"
            + "vp.content='width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no';"
            // Oculta el cartel "Abrir app" (y variantes), también cuando Facebook lo vuelve a poner.
            + "var T=['abrir app','abrir la app','abrir aplicación','abrir la aplicación','abrir en la app',"
            + "'usar la app','obtener la app','descargar la app','descarga la app','descargar facebook','descarga facebook',"
            + "'instalar la app','instalar facebook','instala la app','open app','open in app','get the app','use app',"
            + "'este navegador no es compatible'];"
            + "var X=\"//*[not(self::script)][text()[\"+T.map(function(t){return \"normalize-space(translate(.,"
            + "'ABCDEFGHIJKLMNOPQRSTUVWXYZÁ','abcdefghijklmnopqrstuvwxyzá'))='\"+t+\"'\";}).join(' or ')+\"]]\";"
            + "function ocultar(){try{var r=document.evaluate(X,document.body,null,7,null);"
            + "for(var i=0;i<r.snapshotLength;i++){var e=r.snapshotItem(i),n=e,ok=false;"
            + "for(var k=0;k<12&&n&&n!==document.body;k++){var p=getComputedStyle(n).position;"
            + "if(p==='fixed'||p==='sticky'){n.style.setProperty('display','none','important');ok=true;break;}n=n.parentElement;}"
            + "if(!ok){var b=e.closest('a,button,[role=button]')||e;b.style.setProperty('display','none','important');}}}catch(x){}}"
            + "ocultar();new MutationObserver(function(){if(!window.__slt)window.__slt=setTimeout(function(){window.__slt=0;ocultar();},250);})"
            + ".observe(document.documentElement,{childList:true,subtree:true});})();";

    @Override
    protected void onCreate(Bundle estado) {
        super.onCreate(estado);
        prefs = getSharedPreferences("ajustes", MODE_PRIVATE);

        raiz = new FrameLayout(this);
        raiz.setBackgroundColor(Color.WHITE);
        setContentView(raiz);

        crearWeb();

        barra = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        barra.setMax(100);
        FrameLayout.LayoutParams lpBarra = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(4), Gravity.TOP);
        lpBarra.topMargin = -dp(1);
        raiz.addView(barra, lpBarra);

        if (estado != null && web.restoreState(estado) != null) {
            return;
        }
        if (!recibirCompartido(getIntent())) {
            String enlace = enlaceDe(getIntent());
            web.loadUrl(enlace != null ? enlace : INICIO);
        }
    }

    // ---------- Recibir desde la galería u otras apps ----------

    private static final Pattern URL_EN_TEXTO = Pattern.compile("https?://\\S+");

    /** Devuelve true si el intent era un "Compartir" y ya se cargó la página que corresponde. */
    private boolean recibirCompartido(Intent i) {
        if (i == null) return false;
        String accion = i.getAction();
        String tipo = i.getType() == null ? "" : i.getType();

        if (Intent.ACTION_SEND.equals(accion) && tipo.startsWith("text/")) {
            String texto = i.getStringExtra(Intent.EXTRA_TEXT);
            if (texto == null) texto = "";
            Matcher m = URL_EN_TEXTO.matcher(texto);
            if (m.find()) {
                // Diálogo oficial de Facebook para publicar un enlace.
                web.loadUrl("https://m.facebook.com/sharer.php?u=" + Uri.encode(m.group()));
            } else {
                copiar(texto);
                web.loadUrl(INICIO);
                Toast.makeText(this, "Texto copiado: mantené apretado y pegalo en tu publicación",
                        Toast.LENGTH_LONG).show();
            }
            return true;
        }

        ArrayList<Uri> lista = new ArrayList<>();
        if (Intent.ACTION_SEND.equals(accion)) {
            Uri u = i.getParcelableExtra(Intent.EXTRA_STREAM);
            if (u != null) lista.add(u);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(accion)) {
            ArrayList<Uri> varios = i.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (varios != null) lista.addAll(varios);
        } else {
            return false;
        }
        if (lista.isEmpty()) return false;

        pendientes = lista.toArray(new Uri[0]);
        web.loadUrl(INICIO);
        new AlertDialog.Builder(this)
                .setTitle(lista.size() == 1 ? "Foto lista para publicar" : lista.size() + " archivos listos para publicar")
                .setMessage("Tocá \"Foto\" (arriba, junto a \"¿Qué estás pensando?\") y se adjunta sola. "
                        + "No hace falta buscarla en la galería.")
                .setPositiveButton("Entendido", null)
                .show();
        return true;
    }

    private void crearWeb() {
        if (web != null) {
            raiz.removeView(web);
            web.destroy();
        }
        web = new WebView(this);
        raiz.addView(web, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        // Sin zoom: ni pellizco ni doble toque.
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setAllowFileAccess(false);
        s.setTextZoom(100);
        s.setBlockNetworkImage(prefs.getBoolean("sin_imagenes", false));
        // Sin la marca "wv" Facebook entrega la versión móvil completa, como en Chrome.
        s.setUserAgentString(s.getUserAgentString().replace("; wv", "").replace("Version/4.0 ", ""));

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new Cliente());
        web.setWebChromeClient(new Cromo());
        web.setDownloadListener((url, agente, disposicion, tipo, largo) -> descargar(url, agente, disposicion, tipo));
        web.addJavascriptInterface(new Puente(), "SocialLiviano");
        web.setOnLongClickListener(v -> alMantenerApretado());
    }

    // ---------- Compartir hacia WhatsApp y otras apps ----------

    /** Llamado desde el JavaScript de la página (navigator.share). */
    private class Puente {
        @JavascriptInterface
        public void compartir(String titulo, String texto, String url) {
            StringBuilder sb = new StringBuilder();
            if (texto != null && !texto.isEmpty() && !texto.equals(url)) sb.append(texto).append('\n');
            sb.append(url);
            runOnUiThread(() -> compartirTexto(sb.toString().trim()));
        }
    }

    private void compartirTexto(String texto) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, texto);
        startActivity(Intent.createChooser(i, "Compartir con…"));
    }

    private void copiar(String texto) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("texto", texto));
    }

    private boolean alMantenerApretado() {
        WebView.HitTestResult r = web.getHitTestResult();
        final int tipo = r.getType();
        final String extra = r.getExtra();
        if (extra == null) return false;

        if (tipo == WebView.HitTestResult.SRC_ANCHOR_TYPE) {
            menuEnlace(extra);
            return true;
        }
        if (tipo == WebView.HitTestResult.IMAGE_TYPE) {
            menuImagen(extra, null);
            return true;
        }
        if (tipo == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
            // La imagen está dentro de un enlace: pedimos también la dirección del enlace.
            Handler h = new Handler(Looper.getMainLooper()) {
                @Override public void handleMessage(Message m) {
                    menuImagen(extra, m.getData().getString("url"));
                }
            };
            web.requestFocusNodeHref(h.obtainMessage());
            return true;
        }
        return false;   // texto: se deja la selección normal
    }

    private String limpiarEnlace(String url) {
        Uri u = Uri.parse(url);
        if (("l.facebook.com".equals(u.getHost()) || "lm.facebook.com".equals(u.getHost()))
                && u.getQueryParameter("u") != null) {
            return u.getQueryParameter("u");
        }
        return url;
    }

    private void menuEnlace(String url) {
        final String enlace = limpiarEnlace(url);
        new AlertDialog.Builder(this)
                .setTitle(enlace)
                .setItems(new String[]{"Compartir enlace (WhatsApp…)", "Copiar enlace", "Abrir"}, (d, cual) -> {
                    if (cual == 0) compartirTexto(enlace);
                    else if (cual == 1) { copiar(enlace); Toast.makeText(this, "Enlace copiado", Toast.LENGTH_SHORT).show(); }
                    else web.loadUrl(enlace);
                })
                .show();
    }

    private void menuImagen(String imagen, String enlace) {
        ArrayList<String> op = new ArrayList<>();
        op.add("Compartir imagen (WhatsApp…)");
        op.add("Guardar imagen");
        if (enlace != null) { op.add("Compartir enlace"); op.add("Copiar enlace"); }
        final String link = enlace == null ? null : limpiarEnlace(enlace);
        new AlertDialog.Builder(this)
                .setItems(op.toArray(new String[0]), (d, cual) -> {
                    if (cual == 0) compartirImagen(imagen);
                    else if (cual == 1) descargar(imagen, web.getSettings().getUserAgentString(), null, "image/jpeg");
                    else if (cual == 2) compartirTexto(link);
                    else { copiar(link); Toast.makeText(this, "Enlace copiado", Toast.LENGTH_SHORT).show(); }
                })
                .show();
    }

    /** Baja la imagen a la caché y la manda con un permiso de lectura temporal. */
    private void compartirImagen(String url) {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            Toast.makeText(this, "Esta imagen no se puede compartir", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Preparando imagen…", Toast.LENGTH_SHORT).show();
        final String cookies = CookieManager.getInstance().getCookie(url);
        final String agente = web.getSettings().getUserAgentString();
        new Thread(() -> {
            try {
                File dir = Archivos.carpeta(this);
                File[] viejos = dir.listFiles();
                if (viejos != null) for (File f : viejos) //noinspection ResultOfMethodCallIgnored
                    f.delete();
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                if (cookies != null) c.setRequestProperty("Cookie", cookies);
                c.setRequestProperty("User-Agent", agente);
                c.setConnectTimeout(15000);
                c.setReadTimeout(30000);
                String tipo = c.getContentType() == null ? "image/jpeg" : c.getContentType().split(";")[0];
                String ext = tipo.endsWith("png") ? ".png" : tipo.endsWith("webp") ? ".webp" : tipo.endsWith("gif") ? ".gif" : ".jpg";
                File f = new File(dir, "imagen_" + System.currentTimeMillis() + ext);
                try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(f)) {
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
                runOnUiThread(() -> {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType(tipo);
                    Uri u = Archivos.uriDe(f);
                    i.putExtra(Intent.EXTRA_STREAM, u);
                    i.setClipData(ClipData.newRawUri("imagen", u));
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(i, "Compartir imagen con…"));
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "No se pudo bajar la imagen", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // ---------- Navegación ----------

    private static boolean esDeFacebook(Uri u) {
        String h = u.getHost();
        if (h == null) return false;
        h = h.toLowerCase();
        return h.equals("facebook.com") || h.endsWith(".facebook.com")
                || h.endsWith("fbcdn.net") || h.endsWith("fbsbx.com")
                || h.equals("fb.com") || h.endsWith(".fb.com") || h.equals("fb.watch")
                || h.endsWith("facebook.net") || h.equals("fb.me");
    }

    private class Cliente extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest pedido) {
            Uri u = pedido.getUrl();
            String esquema = u.getScheme() == null ? "" : u.getScheme().toLowerCase();

            if (esquema.equals("http") || esquema.equals("https")) {
                // Enlaces externos que Facebook envuelve en l.facebook.com/l.php?u=...
                if ("l.facebook.com".equals(u.getHost()) || "lm.facebook.com".equals(u.getHost())) {
                    String destino = u.getQueryParameter("u");
                    if (destino != null) { abrirFuera(Uri.parse(destino)); return true; }
                }
                if (esDeFacebook(u)) return false;   // se queda en la app
                abrirFuera(u);
                return true;
            }
            if (esquema.equals("intent")) {
                try {
                    Intent i = Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME);
                    String respaldo = i.getStringExtra("browser_fallback_url");
                    if (respaldo != null) { v.loadUrl(respaldo); return true; }
                    startActivity(i);
                } catch (Exception ignorado) { }
                return true;
            }
            // fb://, mailto:, tel:, whatsapp:, etc.
            abrirFuera(u);
            return true;
        }

        @Override
        public void onPageStarted(WebView v, String url, Bitmap icono) {
            barra.setVisibility(View.VISIBLE);
        }

        @Override
        public void onPageFinished(WebView v, String url) {
            barra.setVisibility(View.GONE);
            v.evaluateJavascript(PUENTE_JS, null);
            CookieManager.getInstance().flush();   // la sesión sobrevive aunque Android cierre la app
        }

        @Override
        public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail detalle) {
            // Con 1 GB de RAM Android puede matar el motor web: lo recreamos en vez de cerrarnos.
            String ultima = v.getUrl();
            crearWeb();
            web.loadUrl(ultima != null ? ultima : INICIO);
            Toast.makeText(MainActivity.this, "Se recargó por falta de memoria", Toast.LENGTH_SHORT).show();
            return true;
        }
    }

    private void abrirFuera(Uri u) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, u);
            i.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            if ("fb".equals(u.getScheme())) return;     // esquema de la app oficial: lo ignoramos
            Toast.makeText(this, "No hay una app para abrir este enlace", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------- Archivos, videos y descargas ----------

    private class Cromo extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView v, int progreso) {
            barra.setProgress(progreso);
            barra.setVisibility(progreso < 100 ? View.VISIBLE : View.GONE);
            if (progreso >= 30) v.evaluateJavascript(PUENTE_JS, null);
        }

        @Override
        public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> callback, FileChooserParams params) {
            if (pendientes != null) {
                // Lo que llegó desde la galería se adjunta sin abrir el selector.
                Uri[] entregar = params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE
                        ? pendientes : new Uri[]{pendientes[0]};
                pendientes = null;
                callback.onReceiveValue(entregar);
                return true;
            }
            if (esperaArchivo != null) esperaArchivo.onReceiveValue(null);
            esperaArchivo = callback;
            Intent elegir = params.createIntent();
            elegir.addCategory(Intent.CATEGORY_OPENABLE);
            if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                elegir.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            }
            try {
                startActivityForResult(Intent.createChooser(elegir, "Elegir archivo"), PEDIDO_ARCHIVO);
            } catch (ActivityNotFoundException e) {
                esperaArchivo = null;
                Toast.makeText(MainActivity.this, "No hay una app para elegir archivos", Toast.LENGTH_SHORT).show();
                return false;
            }
            return true;
        }

        @Override
        public void onShowCustomView(View vista, CustomViewCallback cierre) {
            if (vistaPantallaCompleta != null) { cierre.onCustomViewHidden(); return; }
            vistaPantallaCompleta = vista;
            cierrePantallaCompleta = cierre;
            vista.setBackgroundColor(Color.BLACK);
            raiz.addView(vista, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }

        @Override
        public void onHideCustomView() {
            if (vistaPantallaCompleta == null) return;
            raiz.removeView(vistaPantallaCompleta);
            vistaPantallaCompleta = null;
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            if (cierrePantallaCompleta != null) cierrePantallaCompleta.onCustomViewHidden();
            cierrePantallaCompleta = null;
        }
    }

    @Override
    protected void onActivityResult(int pedido, int resultado, Intent datos) {
        if (pedido == PEDIDO_ARCHIVO && esperaArchivo != null) {
            Uri[] elegidos = null;
            if (resultado == RESULT_OK && datos != null) {
                if (datos.getClipData() != null) {
                    int n = datos.getClipData().getItemCount();
                    elegidos = new Uri[n];
                    for (int i = 0; i < n; i++) elegidos[i] = datos.getClipData().getItemAt(i).getUri();
                } else if (datos.getData() != null) {
                    elegidos = new Uri[]{datos.getData()};
                }
            }
            esperaArchivo.onReceiveValue(elegidos);
            esperaArchivo = null;
            return;
        }
        super.onActivityResult(pedido, resultado, datos);
    }

    private void descargar(String url, String agente, String disposicion, String tipo) {
        try {
            String nombre = URLUtil.guessFileName(url, disposicion, tipo);
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
            r.setMimeType(tipo);
            r.addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url));
            r.addRequestHeader("User-Agent", agente);
            r.setTitle(nombre);
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, nombre);
            ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(r);
            Toast.makeText(this, "Descargando " + nombre, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo descargar", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------- Menú (botón Atrás en la página de inicio) ----------

    @Override
    public void onBackPressed() {
        if (vistaPantallaCompleta != null) { new Cromo().onHideCustomView(); return; }
        if (web.canGoBack()) { web.goBack(); return; }
        mostrarMenu();
    }

    private void mostrarMenu() {
        final boolean sinImagenes = prefs.getBoolean("sin_imagenes", false);
        String[] opciones = {
                "Inicio",
                "Mensajes",
                "Recargar",
                sinImagenes ? "Mostrar imágenes" : "Ahorrar datos (sin imágenes)",
                "Compartir esta página",
                "Salir"
        };
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.app_name))
                .setItems(opciones, (d, cual) -> {
                    switch (cual) {
                        case 0: web.loadUrl(INICIO); break;
                        case 1: web.loadUrl(MENSAJES); break;
                        case 2: web.reload(); break;
                        case 3:
                            prefs.edit().putBoolean("sin_imagenes", !sinImagenes).apply();
                            web.getSettings().setBlockNetworkImage(!sinImagenes);
                            web.reload();
                            break;
                        case 4:
                            String url = web.getUrl();
                            if (url != null) compartirTexto(limpiarEnlace(url));
                            break;
                        default: finish();
                    }
                })
                .show();
    }

    // ---------- Ciclo de vida ----------

    private static String enlaceDe(Intent i) {
        if (i != null && Intent.ACTION_VIEW.equals(i.getAction()) && i.getData() != null) {
            return i.getData().toString();
        }
        return null;
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);   // mantiene el permiso de lectura de las fotos recibidas
        if (recibirCompartido(i)) return;
        String enlace = enlaceDe(i);
        if (enlace != null) web.loadUrl(enlace);
    }

    @Override
    protected void onSaveInstanceState(Bundle estado) {
        super.onSaveInstanceState(estado);
        web.saveState(estado);
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        web.resumeTimers();
    }

    @Override
    protected void onPause() {
        // Sin pantalla no gasta CPU ni batería.
        web.onPause();
        web.pauseTimers();
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
