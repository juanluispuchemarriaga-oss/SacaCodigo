package com.sacacodigo.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.ApplicationInfo
import android.content.res.Resources
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.*
import jadx.api.JadxArgs
import jadx.api.JadxDecompiler
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.zip.ZipFile
import kotlin.concurrent.thread

class A(val info: ApplicationInfo, val name: String)
class E(val label: String, val load: () -> String)

class MainActivity : Activity() {
    private var back: (() -> Unit)? = null
    private var dec: JadxDecompiler? = null
    private val bg = 0xFF1E1E1E.toInt()

    inner class Lines(val sz: Float, val n: () -> Int, val g: (Int) -> String) : BaseAdapter() {
        override fun getCount() = n()
        override fun getItem(i: Int): Any = i
        override fun getItemId(i: Int) = i.toLong()
        override fun getView(i: Int, v: View?, p: ViewGroup?): View {
            val t = (v as? TextView) ?: TextView(this@MainActivity).apply {
                typeface = Typeface.MONOSPACE
                setTextColor(0xFFDDDDDD.toInt())
                setPadding(20, 12, 20, 12)
            }
            t.textSize = sz
            t.text = g(i)
            return t
        }
    }

    override fun onCreate(s: Bundle?) { super.onCreate(s); showApps() }
    override fun onBackPressed() { back?.invoke() ?: super.onBackPressed() }

    private fun page(title: String, vararg tools: View): LinearLayout {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        root.addView(TextView(this).apply { text = title; textSize = 15f; setTextColor(-1); setPadding(24, 24, 24, 24) })
        tools.forEach { root.addView(it) }
        setContentView(root)
        return root
    }

    private fun list(root: LinearLayout, a: BaseAdapter, click: (Int) -> Unit) {
        val lv = ListView(this)
        lv.setBackgroundColor(bg)
        lv.adapter = a
        lv.setOnItemClickListener { _, _, i, _ -> click(i) }
        root.addView(lv, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    private fun btn(t: String, f: () -> Unit) = Button(this).apply { text = t; setOnClickListener { f() } }

    private fun search(h: String, f: (String) -> Unit) = EditText(this).apply {
        hint = h
        setSingleLine()
        addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { f(s.toString().lowercase()) }
            override fun beforeTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
            override fun onTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
        })
    }

    // ---------- Pantalla 1: elegir app ----------
    private fun showApps() {
        back = null
        val pm = packageManager
        val all = pm.getInstalledApplications(0)
            .map { A(it, pm.getApplicationLabel(it).toString()) }
            .sortedBy { it.name.lowercase() }
        var shown = all
        lateinit var ad: Lines
        val q = search("Buscar app o juego…") { k ->
            shown = all.filter { it.name.lowercase().contains(k) || it.info.packageName.contains(k) }
            ad.notifyDataSetChanged()
        }
        val root = page("Elige una app o juego (${all.size})", q)
        ad = Lines(17f, { shown.size }) { shown[it].name + "\n" + shown[it].info.packageName }
        list(root, ad) { openApp(shown[it]) }
    }

    // ---------- Pantalla 2: extraer ----------
    private fun openApp(a: A) {
        back = { showApps() }
        page("${a.name}\nExtrayendo y descompilando… (puede tardar)")
        thread {
            val apks = listOf(a.info.sourceDir) + (a.info.splitSourceDirs?.toList() ?: emptyList())
            val res = try { packageManager.getResourcesForApplication(a.info) } catch (e: Exception) { null }
            val files = ArrayList<E>()
            val cls = ArrayList<E>()
            val withDex = ArrayList<File>()
            var err = ""
            for (apk in apks) try {
                ZipFile(apk).use { z ->
                    for (en in z.entries().toList()) {
                        if (en.isDirectory) continue
                        val n = en.name
                        if (n.endsWith(".dex")) {
                            if (File(apk) !in withDex) withDex.add(File(apk))
                            continue
                        }
                        files.add(E("📄 $n (${en.size} B)") { readEntry(apk, n, res) })
                    }
                }
            } catch (e: Exception) { err += "$e\n" }

            if (withDex.isNotEmpty()) try {
                dec?.close()
                val args = JadxArgs()
                args.setInputFiles(withDex)
                args.setOutDir(File(cacheDir, "jadx"))
                args.setSkipResources(true)
                args.setShowInconsistentCode(true)
                args.setThreadsCount(2)
                val j = JadxDecompiler(args)
                j.load()
                dec = j
                for (c in j.classes) cls.add(E("☕ ${c.fullName}") { c.code })
            } catch (t: Throwable) { err += "JADX: $t\n" }

            val m = files.filter { it.label.contains("AndroidManifest.xml") }
            val all = m + cls + files.filter { it !in m }.sortedBy { it.label }
            runOnUiThread { showEntries(a, all, err) }
        }
    }

    // ---------- Pantalla 3: lista de clases y archivos ----------
    private fun showEntries(a: A, all: List<E>, err: String) {
        back = { showApps() }
        var shown = all
        lateinit var ad: Lines
        val q = search("Filtrar clases / archivos…") { k ->
            shown = all.filter { it.label.lowercase().contains(k) }
            ad.notifyDataSetChanged()
        }
        val root = page("${a.name} • ${all.size} elementos  (☕ código, 📄 archivos)" +
                if (err.isEmpty()) "" else "\n⚠ $err", q)
        ad = Lines(12f, { shown.size }) { shown[it].label }
        list(root, ad) { openCode(a, shown[it], all) }
    }

    // ---------- Pantalla 4: código con líneas ----------
    private fun openCode(a: A, e: E, all: List<E>) {
        back = { showEntries(a, all, "") }
        val root = page(e.label)
        Toast.makeText(this, "Cargando…", Toast.LENGTH_SHORT).show()
        thread {
            val text = try { e.load() } catch (t: Throwable) { "Error: $t" }
            val lines = text.lines()
            runOnUiThread {
                val bar = LinearLayout(this)
                bar.addView(btn("Copiar todo") { copy(text) }, LinearLayout.LayoutParams(0, -2, 1f))
                bar.addView(btn("Copiar líneas") { range(lines) }, LinearLayout.LayoutParams(0, -2, 1f))
                root.addView(bar)
                val w = lines.size.toString().length
                list(root, Lines(11f, { lines.size }) { (it + 1).toString().padStart(w) + " │ " + lines[it] }) {
                    copy(lines[it])
                }
            }
        }
    }

    private fun copy(t: String) {
        val s = if (t.length > 300_000) t.substring(0, 300_000) else t
        try {
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("codigo", s))
            Toast.makeText(
                this,
                if (s.length < t.length) "Copiado parcial (300.000 caracteres). Usa 'Copiar líneas' para el resto" else "Copiado ✔",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Muy grande: usa 'Copiar líneas'", Toast.LENGTH_LONG).show()
        }
    }

    private fun range(lines: List<String>) {
        val a = EditText(this).apply { hint = "Desde línea (1)"; inputType = InputType.TYPE_CLASS_NUMBER }
        val b = EditText(this).apply { hint = "Hasta línea (${lines.size})"; inputType = InputType.TYPE_CLASS_NUMBER }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 0)
            addView(a); addView(b)
        }
        AlertDialog.Builder(this).setTitle("Copiar rango").setView(box)
            .setPositiveButton("Copiar") { _, _ ->
                val f = (a.text.toString().toIntOrNull() ?: 1).coerceIn(1, lines.size)
                val t = (b.text.toString().toIntOrNull() ?: lines.size).coerceIn(f, lines.size)
                copy(lines.subList(f - 1, t).joinToString("\n"))
            }.show()
    }

    // ---------- Lectura de archivos del APK ----------
    private fun readEntry(apk: String, name: String, res: Resources?): String {
        ZipFile(apk).use { z ->
            val en = z.getEntry(name)
            if (en.size > 30_000_000) return "[Archivo demasiado grande: ${en.size} bytes]"
            val bytes = z.getInputStream(en).use { it.readBytes() }
            val axml = bytes.size > 4 && bytes[0] == 3.toByte() && bytes[1] == 0.toByte() &&
                    bytes[2] == 8.toByte() && bytes[3] == 0.toByte() && name.endsWith(".xml")
            if (axml) {
                return try { xml(res!!.assets.openXmlResourceParser(name)) }
                catch (t: Exception) { "[XML binario sin decodificar: $t]" }
            }
            if (bytes.take(2048).any { it == 0.toByte() })
                return "[Archivo binario: ${bytes.size} bytes — no es código de texto]"
            return String(bytes, Charsets.UTF_8)
        }
    }

    private fun xml(p: XmlPullParser): String {
        val sb = StringBuilder()
        var d = 0
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> {
                    sb.append("  ".repeat(d)).append('<').append(p.name)
                    for (i in 0 until p.attributeCount) {
                        val pre = if ((p.getAttributeNamespace(i) ?: "").contains("android")) "android:" else ""
                        sb.append(' ').append(pre).append(p.getAttributeName(i))
                            .append("=\"").append(p.getAttributeValue(i)).append('"')
                    }
                    sb.append(">\n")
                    d++
                }
                XmlPullParser.END_TAG -> {
                    d = maxOf(d - 1, 0)
                    sb.append("  ".repeat(d)).append("</").append(p.name).append(">\n")
                }
                XmlPullParser.TEXT -> if (p.text.isNotBlank()) sb.append("  ".repeat(d)).append(p.text.trim()).append('\n')
            }
            ev = p.next()
        }
        return sb.toString()
    }
}
