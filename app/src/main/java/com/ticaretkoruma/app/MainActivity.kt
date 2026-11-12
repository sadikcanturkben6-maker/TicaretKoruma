package com.ticaretkoruma.app

import android.app.*
import android.content.*
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.provider.*
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("TKPrefs", 0) }
    private val tr = Locale("tr", "TR")
    private val handler = Handler(Looper.getMainLooper())
    private var monthCal = Calendar.getInstance().apply { set(5, 1) }

    private lateinit var tvGreet: TextView
    private lateinit var tvLoc: TextView
    private lateinit var tvTemp: TextView
    private lateinit var tvHero: TextView
    private lateinit var tvTimer: TextView
    private lateinit var tvN1: TextView
    private lateinit var tvN2: TextView
    private lateinit var tvN3: TextView
    private lateinit var tvMonth: TextView
    private lateinit var grid: GridLayout
    private lateinit var tvStat: TextView

    private val shifts = listOf(
        arrayOf("GUNDUZ", "Gündüz (08:00 - 20:00)", "GÜNDÜZ", "☀", "#2563EB", "6", "30"),
        arrayOf("GECE", "Gece (20:00 - 08:00)", "GECE", "☾", "#4F46E5", "18", "30"),
        arrayOf("ISTIRAHAT", "Görev İstirahati", "Grv. İst.", "🛏", "#16A34A", "9", "0"),
        arrayOf("YILLIK", "Yıllık İzin", "İZİN", "✈", "#8B5CF6", "9", "0"),
        arrayOf("RAPOR", "Sağlık Raporu", "RAPOR", "✚", "#EF4444", "9", "0"),
        arrayOf("RESMI", "Resmi İzin", "RESMİ", "★", "#06B6D4", "9", "0")
    )

    private val holidays = mapOf(
        "01-01" to "Yılbaşı", "04-23" to "Ulusal Egemenlik", "05-01" to "İşçi Bayramı",
        "05-19" to "Atatürk'ü Anma", "07-15" to "Demokrasi Günü", "08-30" to "Zafer Bayramı",
        "10-29" to "Cumhuriyet Bayramı",
        // 2026 Dini Tatiller (Tahmini)
        "2026-03-20" to "Ramazan Bayramı Arf.", "2026-03-21" to "Ramazan Bayramı 1",
        "2026-05-26" to "Kurban Bayramı Arf.", "2026-05-27" to "Kurban Bayramı 1",
        // 2027 Dini Tatiller (Tahmini)
        "2027-03-09" to "Ramazan Bayramı Arf.", "2027-03-10" to "Ramazan Bayramı 1",
        "2027-05-16" to "Kurban Bayramı Arf.", "2027-05-17" to "Kurban Bayramı 1"
    )

    private fun px(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun bg(hex: String, r: Int, st: String? = null, stW: Int = 1) = GradientDrawable().apply {
        cornerRadius = px(r).toFloat(); setColor(Color.parseColor(hex))
        if (st != null) setStroke(px(stW), Color.parseColor(st))
    }
    private fun keyOf(c: Calendar) = String.format(Locale.US, "%04d-%02d-%02d", c.get(1), c.get(2) + 1, c.get(5))
    private fun mmdd(c: Calendar) = String.format(Locale.US, "%02d-%02d", c.get(2) + 1, c.get(5))

    private fun getHoliday(c: Calendar): String? {
        val k = keyOf(c); val m = mmdd(c)
        return holidays[k] ?: holidays[m]
    }

    private val ticker = object : Runnable {
        override fun run() { updateTop(); handler.postDelayed(this, 1000L) }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        supportActionBar?.hide()
        
        val mainLayout = LinearLayout(this).apply { orientation = 1; setBackgroundColor(Color.parseColor("#090E1A")) }
        val scroll = ScrollView(this).apply { isFillViewport = true; layoutParams = LinearLayout.LayoutParams(-1, 0, 1f) }
        val root = LinearLayout(this).apply { orientation = 1; setPadding(px(14), px(16), px(14), px(16)) }

        // Üst Başlık
        val top = LinearLayout(this).apply { orientation = 0; gravity = 16 }
        val tCol = LinearLayout(this).apply { orientation = 1; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        tvGreet = TextView(this).apply { text = prefs.getString("greet", "Hoşgeldin Devrem 👋"); textSize = 20f; setTextColor(-1); setTypeface(null, 1) }
        val tvDate = TextView(this).apply { text = SimpleDateFormat("EEEE, d MMMM", tr).format(Date()); textSize = 12f; setTextColor(Color.parseColor("#94A3B8")) }
        tCol.addView(tvGreet); tCol.addView(tvDate); top.addView(tCol)

        val bRef = Button(this).apply {
            text = "🔄"; textSize = 14f; setTextColor(-1); background = bg("#1E293B", 10)
            layoutParams = LinearLayout.LayoutParams(-2, -2); setPadding(px(10), px(6), px(10), px(6))
            setOnClickListener { loadWeather(); drawCal(); updateTop(); Toast.makeText(this@MainActivity, "Yenilendi", 0).show() }
        }
        top.addView(bRef); root.addView(top)

        // Hero Kart
        val hero = LinearLayout(this).apply {
            orientation = 1; background = bg("#1E3A8A", 16, "#3B82F6"); setPadding(px(16), px(14), px(16), px(14))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(12) }
        }
        val h1 = LinearLayout(this).apply { orientation = 0; gravity = 16 }
        tvLoc = TextView(this).apply { text = "📍 Etimesgut"; textSize = 12f; setTextColor(Color.parseColor("#BAE6FD")); setTypeface(null, 1); layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        tvTemp = TextView(this).apply { text = "--°"; textSize = 22f; setTextColor(-1); setTypeface(null, 1) }
        h1.addView(tvLoc); h1.addView(tvTemp); hero.addView(h1)
        val h2 = LinearLayout(this).apply { orientation = 0; gravity = 16; setPadding(0, px(6), 0, px(4)) }
        tvHero = TextView(this).apply { textSize = 16f; setTextColor(-1); setTypeface(null, 1); layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        tvTimer = TextView(this).apply { textSize = 16f; setTextColor(Color.parseColor("#FDE047")); setTypeface(null, 1) }
        h2.addView(tvHero); h2.addView(tvTimer); hero.addView(h2)
        hero.addView(TextView(this).apply { text = "☕ Hava sakin, vukuatsız nöbetler."; textSize = 12f; setTextColor(Color.parseColor("#E0F2FE")) })
        root.addView(hero)

        // Hızlı Özet
        val nRow = LinearLayout(this).apply { orientation = 0; layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(10) } }
        fun box(m: Int) = TextView(this).apply { textSize = 11f; setTextColor(-1); background = bg("#111827", 12, "#1F2937"); setPadding(px(10), px(8), px(10), px(8)); layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = px(m) } }
        tvN1 = box(6); tvN2 = box(6); tvN3 = box(0)
        nRow.addView(tvN1); nRow.addView(tvN2); nRow.addView(tvN3); root.addView(nRow)

        // Takvim
        val calBox = LinearLayout(this).apply {
            orientation = 1; background = bg("#111827", 16, "#1F2937"); setPadding(px(10), px(12), px(10), px(12))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(12) }
        }
        val nav = LinearLayout(this).apply { orientation = 0; gravity = 16; setPadding(0, 0, 0, px(8)) }
        val bP = Button(this).apply { text = "◀"; setTextColor(-1); background = bg("#1E293B", 8); setOnClickListener { monthCal.add(2, -1); drawCal() } }
        tvMonth = TextView(this).apply { textSize = 16f; setTextColor(-1); setTypeface(null, 1); gravity = 17; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        val bN = Button(this).apply { text = "▶"; setTextColor(-1); background = bg("#1E293B", 8); setOnClickListener { monthCal.add(2, 1); drawCal() } }
        nav.addView(bP); nav.addView(tvMonth); nav.addView(bN); calBox.addView(nav)

        val wk = LinearLayout(this).apply { orientation = 0; setPadding(0, 0, 0, px(6)) }
        for (d in listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")) { wk.addView(TextView(this).apply { text = d; textSize = 11f; setTextColor(Color.parseColor("#94A3B8")); gravity = 17; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }) }
        calBox.addView(wk)
        grid = GridLayout(this).apply { columnCount = 7; rowCount = 6 }
        calBox.addView(grid); root.addView(calBox)

        // İstatistik
        tvStat = TextView(this).apply {
            textSize = 12f; setTextColor(-1); setTypeface(null, 1); gravity = 17
            background = bg("#111827", 14, "#1F2937"); setPadding(px(12), px(12), px(12), px(12))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(10) }
        }
        root.addView(tvStat)
        scroll.addView(root); mainLayout.addView(scroll)

        // Alt Menü (Bottom Navigation)
        val btmNav = LinearLayout(this).apply {
            orientation = 0; background = bg("#0F172A", 0); setPadding(0, px(12), 0, px(12))
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }
        fun navBtn(icon: String, title: String, action: () -> Unit): LinearLayout {
            return LinearLayout(this).apply {
                orientation = 1; gravity = 17; layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                addView(TextView(this@MainActivity).apply { text = icon; textSize = 20f; gravity = 17 })
                addView(TextView(this@MainActivity).apply { text = title; textSize = 10f; setTextColor(-1); gravity = 17 })
                setOnClickListener { action() }
            }
        }
        btmNav.addView(navBtn("📅", "Takvim") { Toast.makeText(this, "Takvimdesiniz", 0).show() })
        btmNav.addView(navBtn("📈", "Sayaçlar") { Toast.makeText(this, "Sayaçlar eklenecek", 0).show() })
        btmNav.addView(navBtn("⚙", "Ayarlar") { openSettings() })
        btmNav.addView(navBtn("💬", "İletişim") { Toast.makeText(this, "İletişim Modülü", 0).show() })
        mainLayout.addView(btmNav)

        setContentView(mainLayout)
        drawCal(); updateTop(); loadWeather()
    }

    override fun onResume() { super.onResume(); handler.post(ticker) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(ticker) }

    private fun shiftFor(c: Calendar): Array<String> {
        val ov = prefs.getString("ov_${keyOf(c)}", null)
        if (ov != null) return shifts.find { it[0] == ov } ?: shifts[0]
        val anc = prefs.getLong("anc", -1L)
        if (anc == -1L) return shifts[2]
        val a = Calendar.getInstance().apply { timeInMillis = anc; set(11, 0); set(12, 0); set(13, 0); set(14, 0) }
        val t = (c.clone() as Calendar).apply { set(11, 0); set(12, 0); set(13, 0); set(14, 0) }
        val diff = TimeUnit.MILLISECONDS.toDays(t.timeInMillis - a.timeInMillis).toInt()
        val pos = (((diff + prefs.getInt("step", 0)) % 4) + 4) % 4
        return when (pos) { 0 -> shifts[0]; 1 -> shifts[1]; else -> shifts[2] }
    }

    private fun drawCal() {
        tvMonth.text = SimpleDateFormat("MMMM yyyy", tr).format(monthCal.time).uppercase(tr)
        grid.removeAllViews()
        val c = (monthCal.clone() as Calendar).apply { set(5, 1) }
        val off = (c.get(7) + 5) % 7
        val maxD = c.getActualMaximum(5)
        val now = Calendar.getInstance()
        val w = (resources.displayMetrics.widthPixels - px(48)) / 7
        var tG = 0; var tN = 0; var tI = 0; var pG = 0; var pN = 0; var pI = 0

        for (i in 0 until off) grid.addView(View(this).apply { layoutParams = GridLayout.LayoutParams().apply { width = w; height = px(54) } })
        for (d in 1..maxD) {
            val dc = (monthCal.clone() as Calendar).apply { set(5, d) }
            val k = keyOf(dc); val sh = shiftFor(dc); val past = !dc.after(now)
            val hol = getHoliday(dc)
            if (sh[0] == "GUNDUZ") { tG++; if (past) pG++ } else if (sh[0] == "GECE") { tN++; if (past) pN++ } else { tI++; if (past) pI++ }

            val cell = LinearLayout(this).apply {
                orientation = 1; gravity = 17; setPadding(px(2), px(3), px(2), px(3))
                val brdColor = if (k == keyOf(now)) "#FFFFFF" else sh[4]
                val stWidth = if (k == keyOf(now)) 2 else 1
                val fill = when (sh[0]) { "GUNDUZ" -> "#172554"; "GECE" -> "#1E1B4B"; "ISTIRAHAT" -> "#052E16"; else -> "#1E293B" }
                background = bg(fill, 9, brdColor, stWidth)
                layoutParams = GridLayout.LayoutParams().apply { width = w - px(4); height = px(54); setMargins(px(2), px(2), px(2), px(2)) }
                setOnClickListener { openDayMenu(dc) } // Gün seçilince menü açılır
            }
            
            // Eğer tatilse numaranın yanına kırmızı nokta koy
            val dot = if (hol != null) "🔴" else if (!prefs.getString("nt_$k", "").isNullOrBlank()) "•" else ""
            cell.addView(TextView(this).apply { text = "$d$dot"; textSize = 13f; setTextColor(-1); setTypeface(null, 1); gravity = 17 })
            cell.addView(TextView(this).apply { text = sh[2]; textSize = 8.5f; setTextColor(-1); setTypeface(null, 1); gravity = 17 })
            cell.addView(TextView(this).apply { text = sh[3]; textSize = 9f; setTextColor(Color.parseColor("#CBD5E1")); gravity = 17 })
            grid.addView(cell)
        }
        tvStat.text = "📅 Geçen / Toplam:\n☀ $pG/$tG Gündüz  •  ☾ $pN/$tN Gece  •  🛏 $pI/$tI İst."
    }

    private fun updateTop() {
        val now = Calendar.getInstance()
        val ts = shiftFor(now)
        tvHero.text = "${ts[3]} ${ts[1]}"
        fun nxt(add: Int, lbl: String): String {
            val d = (now.clone() as Calendar).apply { add(6, add) }; val s = shiftFor(d)
            val l = lbl.ifEmpty { SimpleDateFormat("d MMM", tr).format(d.time).uppercase(tr) }
            val h = if (s[0] == "GUNDUZ") "08:00-20:00" else if (s[0] == "GECE") "20:00-08:00" else "İstirahat"
            return "$l\n${s[3]} ${s[2]}\n$h"
        }
        tvN1.text = nxt(1, "Yarın"); tvN2.text = nxt(2, "Öbür gün"); tvN3.text = nxt(3, "")
        for (i in 0..7) {
            val cc = (now.clone() as Calendar).apply { add(6, i) }; val sh = shiftFor(cc)
            if (sh[0] == "GUNDUZ" || sh[0] == "GECE") {
                val st = (cc.clone() as Calendar).apply { set(11, if (sh[0] == "GUNDUZ") 8 else 20); set(12, 0); set(13, 0) }
                val en = (st.clone() as Calendar).apply { add(11, 12) }
                val tg = if (now.before(st)) st else if (now.before(en)) en else null
                if (tg != null) {
                    val sec = (tg.timeInMillis - now.timeInMillis) / 1000
                    tvTimer.text = String.format(Locale.US, "%s%02ds %02ddk", if (now.before(st)) "NÖBETE " else "BİTİME ", sec / 3600, (sec % 3600) / 60)
                    return
                }
            }
        }
        tvTimer.text = "--:--"
    }

    // Ayarlar Menüsü
    private fun openSettings() {
        val opts = arrayOf("1. Döngü Kurulumu Yap", "2. Karşılama Mesajını Değiştir")
        AlertDialog.Builder(this).setTitle("Ayarlar").setItems(opts) { _, s ->
            if (s == 0) {
                val cyc = arrayOf("1. Gün: 12s Gündüz", "2. Gün: 12s Gece", "3. Gün: Grv. İst. 1", "4. Gün: Grv. İst. 2")
                AlertDialog.Builder(this).setTitle("Döngü Kur").setItems(cyc) { _, i ->
                    val c = Calendar.getInstance()
                    DatePickerDialog(this, { _, y, m, d ->
                        val ch = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0) }
                        prefs.edit().putLong("anc", ch.timeInMillis).putInt("step", i).apply()
                        drawCal(); updateTop()
                    }, c.get(1), c.get(2), c.get(5)).show()
                }.show()
            } else {
                val ed = EditText(this).apply { setText(prefs.getString("greet", "Hoşgeldin Devrem 👋")) }
                AlertDialog.Builder(this).setTitle("Karşılama").setView(ed).setPositiveButton("Kaydet") { _, _ ->
                    prefs.edit().putString("greet", ed.text.toString()).apply(); tvGreet.text = ed.text.toString()
                }.show()
            }
        }.show()
    }

    // Detaylı Açılır Menü (Popup Dialog)
    private fun openDayMenu(c: Calendar) {
        val d = Dialog(this); d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val root = LinearLayout(this).apply { orientation = 1; setBackgroundColor(Color.parseColor("#0F172A")); setPadding(px(16), px(16), px(16), px(16)) }
        val k = keyOf(c); val sh = shiftFor(c); val hol = getHoliday(c)
        
        if (hol != null) {
            root.addView(TextView(this).apply { text = hol; setTextColor(-1); background = bg("#EF4444", 8); setPadding(px(12), px(4), px(12), px(4)); gravity = 17; layoutParams = LinearLayout.LayoutParams(-2, -2).apply { gravity = 1 } })
        }
        root.addView(TextView(this).apply { text = SimpleDateFormat("EEEE, d MMM yyyy", tr).format(c.time); textSize = 14f; setTextColor(Color.parseColor("#94A3B8")); gravity = 17; setPadding(0, px(8), 0, 0) })
        root.addView(TextView(this).apply { text = sh[0]; textSize = 24f; setTextColor(-1); setTypeface(null, 1); gravity = 17; setPadding(0, 0, 0, px(16)) })

        val v = LinearLayout(this).apply { orientation = 0; gravity = 17 }
        fun btn(title: String, shiftCode: String, color: String) {
            v.addView(Button(this).apply { text = title; setTextColor(-1); background = bg(color, 12); layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = px(4) }
                setOnClickListener { prefs.edit().putString("ov_$k", shiftCode).apply(); d.dismiss(); drawCal(); updateTop(); openDayMenu(c) }
            })
        }
        btn("Gündüz", "GUNDUZ", "#2563EB"); btn("Gece", "GECE", "#4F46E5")
        root.addView(v)

        val v2 = LinearLayout(this).apply { orientation = 0; gravity = 17; setPadding(0, px(8), 0, 0) }
        fun btn2(title: String, code: String, color: String) {
            v2.addView(Button(this).apply { text = title; textSize = 10f; setTextColor(-1); background = bg(color, 12); layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = px(4) }
                setOnClickListener { prefs.edit().putString("ov_$k", code).apply(); d.dismiss(); drawCal(); updateTop(); openDayMenu(c) }
            })
        }
        btn2("Yıllık İzin", "YILLIK", "#8B5CF6"); btn2("Mazeret", "RESMI", "#D97706"); btn2("Rapor", "RAPOR", "#EF4444"); btn2("Grv İst", "ISTIRAHAT", "#16A34A")
        root.addView(v2)

        val ed = EditText(this).apply { hint = "Bu nöbete not ekle..."; setTextColor(-1); setHintTextColor(Color.GRAY); setText(prefs.getString("nt_$k", "")); setPadding(0, px(16), 0, px(16)) }
        root.addView(ed)
        
        val bBox = LinearLayout(this).apply { orientation = 0 }
        bBox.addView(Button(this).apply { text = "Sıfırla"; setTextColor(-1); background = bg("#475569", 8); layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = px(4) }; setOnClickListener { prefs.edit().remove("ov_$k").remove("nt_$k").apply(); d.dismiss(); drawCal(); updateTop() } })
        bBox.addView(Button(this).apply { text = "Kaydet"; setTextColor(-1); background = bg("#059669", 8); layoutParams = LinearLayout.LayoutParams(0, -2, 1f); setOnClickListener { prefs.edit().putString("nt_$k", ed.text.toString().trim()).apply(); d.dismiss(); drawCal() } })
        root.addView(bBox)

        d.setContentView(root); d.window?.setLayout(-1, -2); d.show()
    }

    private fun loadWeather() {
        Thread {
            try {
                val url = URL("https://api.open-meteo.com/v1/forecast?latitude=39.95&longitude=32.68&current_weather=true")
                val con = url.openConnection() as HttpURLConnection
                con.setRequestProperty("User-Agent", "Mozilla/5.0")
                val res = con.inputStream.bufferedReader().readText()
                val t = Regex("\"temperature\":([-0-9.]+)").find(res)?.groupValues?.get(1)?.substringBefore(".") ?: "--"
                runOnUiThread { tvLoc.text = "📍 Etimesgut • GÜNCEL"; tvTemp.text = "☁ ${t}°" }
            } catch (e: Exception) { runOnUiThread { tvLoc.text = "📍 Etimesgut • ÇEVRİMDIŞI" } }
        }.start()
    }
}
