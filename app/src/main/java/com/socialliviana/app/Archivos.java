package com.socialliviana.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Proveedor mínimo (sin AndroidX) que deja leer, solo con permiso temporal,
 * las imágenes que la app guarda en cache/compartir para mandarlas a WhatsApp u otras apps.
 */
public class Archivos extends ContentProvider {

    static final String AUTORIDAD = "com.socialliviana.app.archivos";

    static File carpeta(android.content.Context c) {
        File d = new File(c.getCacheDir(), "compartir");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    static Uri uriDe(File f) {
        return new Uri.Builder().scheme("content").authority(AUTORIDAD).appendPath(f.getName()).build();
    }

    private File archivo(Uri uri) throws FileNotFoundException {
        String nombre = uri.getLastPathSegment();
        if (nombre == null || nombre.contains("/") || nombre.contains("..")) throw new FileNotFoundException();
        File f = new File(carpeta(getContext()), nombre);
        if (!f.isFile()) throw new FileNotFoundException(nombre);
        return f;
    }

    @Override public boolean onCreate() { return true; }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String modo) throws FileNotFoundException {
        return ParcelFileDescriptor.open(archivo(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        String n = uri.getLastPathSegment() == null ? "" : uri.getLastPathSegment().toLowerCase();
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".mp4")) return "video/mp4";
        return "image/jpeg";
    }

    @Override
    public Cursor query(Uri uri, String[] columnas, String sel, String[] args, String orden) {
        MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        try {
            File f = archivo(uri);
            c.addRow(new Object[]{f.getName(), f.length()});
        } catch (FileNotFoundException ignorado) { }
        return c;
    }

    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
