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
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("TKPrefs", 0) }
    private val tr = Locale("tr", "TR")
    private val handler = Handler(Looper.getMainLooper())
    private var monthCal = Calendar.getInstance().apply { set(5, 1) }
    private var selCal = Calendar.getInstance()

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
    private lateinit var tvDayTitle: TextView
    private lateinit var tvDayBadge: TextView
    private lateinit var tvDayNote: TextView

    private val shifts = listOf(
        arrayOf("GUNDUZ", "Gündüz (08:00-20:00)", "GÜNDÜZ", "☀", "#2563EB", "6", "30"),
        arrayOf("GECE", "Gece (20:00-08:00)", "GECE", "☾", "#4F46E5", "18", "30"),
        arrayOf("ISTIRAHAT", "Görev İstirahati", "Grv. İst.", "🛏", "#16A34A", "9", "0"),
        arrayOf("YILLIK", "Yıllık İzin", "İZİN", "✈", "#8B5CF6", "9", "0"),
        arrayOf("RAPOR", "Sağlık Raporu", "RAPOR", "✚", "#EF4444", "9", "0"),
        arrayOf("RESMI", "Resmi İzin", "RESMİ", "★", "#06B6D4", "9", "0")
    )

    private fun px(v: Int) = (v * resources.displayMetrics.density).toInt()
    
    // Kenarlık kalınlığı (stW) eklendi
    private fun bg(hex: String, r: Int, st: String? = null, stW: Int = 1) = GradientDrawable().apply {
        cornerRadius = px(r).toFloat(); setColor(Color.parseColor(hex))
        if (st != null) setStroke(px(stW), Color.parseColor(st))
    }
    private fun keyOf(c: Calendar) = String.format(Locale.US, "%04d-%02d-%02d", c.get(1), c.get(2) + 1, c.get(5))

    private val ticker = object : Runnable {
        override fun run() { updateTop(); handler.postDelayed(this, 1000L) }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        supportActionBar?.hide()
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#090E1A")); isFillViewport = true }
        val root = LinearLayout(this).apply { orientation = 1; setPadding(px(14), px(16), px(14), px(24)) }

        val top = LinearLayout(this).apply { orientation = 0; gravity = 16 }
        val tCol = LinearLayout(this).apply { orientation = 1; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        tvGreet = TextView(this).apply {
            text = prefs.getString("greet", "Hoşgeldin Devrem 👋"); textSize = 20f; setTextColor(-1); setTypeface(null, 1)
            setOnClickListener {
                val ed = EditText(this@MainActivity).apply { setText(text) }
                AlertDialog.Builder(this@MainActivity).setTitle("Başlık").setView(ed).setPositiveButton("Kaydet") { _, _ ->
                    val t = ed.text.toString().trim().ifEmpty { "Hoşgeldin 👋" }
                    prefs.edit().putString("greet", t).apply(); text = t
                }.show()
            }
        }
        val tvDate = TextView(this).apply { text = SimpleDateFormat("EEEE, d MMMM", tr).format(Date()); textSize = 12f; setTextColor(Color.parseColor("#94A3B8")) }
        tCol.addView(tvGreet); tCol.addView(tvDate)

        // Yenileme Butonu (Sayfa Yenileme Olayı)
        val bRef = Button(this).apply {
            text = "🔄"; textSize = 14f; setTextColor(-1); background = bg("#1E293B", 10)
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = px(6) }
            setPadding(px(10), px(6), px(10), px(6))
            setOnClickListener { 
                loadWeather(); drawCal(); updatePanel(); updateTop()
                Toast.makeText(this@MainActivity, "Sayfa Yenilendi", 0).show()
            }
        }

        val bCycle = Button(this).apply {
            text = "Döngü Kur"; textSize = 12f; setTextColor(-1); background = bg("#1E293B", 10); setPadding(px(12), px(6), px(12), px(6))
            setOnClickListener { setupCycle() }
        }
        top.addView(tCol); top.addView(bRef); top.addView(bCycle); root.addView(top)

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

        val nRow = LinearLayout(this).apply { orientation = 0; layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(10) } }
        fun box(m: Int) = TextView(this).apply {
            textSize = 11f; setTextColor(-1); background = bg("#111827", 12, "#1F2937"); setPadding(px(10), px(8), px(10), px(8))
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = px(m) }
        }
        tvN1 = box(6); tvN2 = box(6); tvN3 = box(0)
        nRow.addView(tvN1); nRow.addView(tvN2); nRow.addView(tvN3); root.addView(nRow)

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
        for (d in listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")) {
            wk.addView(TextView(this).apply { text = d; textSize = 11f; setTextColor(Color.parseColor("#94A3B8")); gravity = 17; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        }
        calBox.addView(wk)
        grid = GridLayout(this).apply { columnCount = 7; rowCount = 6 }
        calBox.addView(grid); root.addView(calBox)

        tvStat = TextView(this).apply {
            textSize = 12f; setTextColor(-1); setTypeface(null, 1); gravity = 17
            background = bg("#111827", 14, "#1F2937"); setPadding(px(12), px(12), px(12), px(12))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(10) }
        }
        root.addView(tvStat)

        val det = LinearLayout(this).apply {
            orientation = 1; background = bg("#111827", 14, "#1F2937"); setPadding(px(14), px(12), px(14), px(12))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(10) }
        }
        val dTop = LinearLayout(this).apply { orientation = 0; gravity = 16 }
        tvDayTitle = TextView(this).apply { textSize = 15f; setTextColor(-1); setTypeface(null, 1); layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        tvDayBadge = TextView(this).apply { textSize = 11f; setTextColor(-1); setTypeface(null, 1); setPadding(px(10), px(4), px(10), px(4)) }
        dTop.addView(tvDayTitle); dTop.addView(tvDayBadge); det.addView(dTop)

        tvDayNote = TextView(this).apply { textSize = 12f; setTextColor(Color.parseColor("#CBD5E1")); setPadding(0, px(6), 0, px(10)) }
        det.addView(tvDayNote)

        val row = LinearLayout(this).apply { orientation = 0 }
        val bEdit = Button(this).apply {
            text = "Düzenle"; textSize = 12f; setTextColor(-1); background = bg("#2563EB", 10)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = px(6) }; setOnClickListener { editDay() }
        }
        val bAlarm = Button(this).apply {
            text = "Alarm"; textSize = 12f; setTextColor(-1); background = bg("#059669", 10)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = px(6) }; setOnClickListener { setAlarm() }
        }
        val bCal = Button(this).apply {
            text = "Takvim"; textSize = 12f; setTextColor(-1); background = bg("#475569", 10)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f); setOnClickListener { addCal() }
        }
        row.addView(bEdit); row.addView(bAlarm); row.addView(bCal); det.addView(row)
        root.addView(det); scroll.addView(root); setContentView(scroll)

        drawCal(); updatePanel(); updateTop(); loadWeather()
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
            if (sh[0] == "GUNDUZ") { tG++; if (past) pG++ } else if (sh[0] == "GECE") { tN++; if (past) pN++ } else { tI++; if (past) pI++ }

            val cell = LinearLayout(this).apply {
                orientation = 1; gravity = 17; setPadding(px(2), px(3), px(2), px(3))
                
                // BUGÜNÜN ve SEÇİLİ GÜNÜN ÇERÇEVELERİ
                var brdColor = sh[4]
                var stWidth = 1
                if (k == keyOf(now)) {
                    brdColor = "#FFFFFF" // BUGÜN İÇİN BEYAZ ÇERÇEVE
                    stWidth = 2
                } else if (k == keyOf(selCal)) {
                    brdColor = "#38BDF8" // Seçili gün için mavi
                    stWidth = 2
                }

                val fill = when (sh[0]) { "GUNDUZ" -> "#172554"; "GECE" -> "#1E1B4B"; "ISTIRAHAT" -> "#052E16"; else -> "#1E293B" }
                background = bg(fill, 9, brdColor, stWidth)
                layoutParams = GridLayout.LayoutParams().apply { width = w - px(4); height = px(54); setMargins(px(2), px(2), px(2), px(2)) }
                setOnClickListener { selCal = dc; drawCal(); updatePanel() }
            }
            val dot = if (!prefs.getString("nt_$k", "").isNullOrBlank()) "•" else ""
            cell.addView(TextView(this).apply { text = "$d$dot"; textSize = 13f; setTextColor(-1); setTypeface(null, 1); gravity = 17 })
            cell.addView(TextView(this).apply { text = sh[2]; textSize = 8.5f; setTextColor(-1); setTypeface(null, 1); gravity = 17 })
            cell.addView(TextView(this).apply { text = sh[3]; textSize = 9f; setTextColor(Color.parseColor("#CBD5E1")); gravity = 17 })
            grid.addView(cell)
        }
        tvStat.text = "📅 Geçen / Toplam:\n☀ $pG/$tG Gündüz  •  ☾ $pN/$tN Gece  •  🛏 $pI/$tI İst."
    }

    private fun updatePanel() {
        tvDayTitle.text = SimpleDateFormat("d MMMM yyyy, EEEE", tr).format(selCal.time)
        val sh = shiftFor(selCal)
        tvDayBadge.text = "${sh[3]} ${sh[2]}"; tvDayBadge.background = bg(sh[4], 8)
        val nt = prefs.getString("nt_${keyOf(selCal)}", "") ?: ""
        tvDayNote.text = if (nt.isNotBlank()) "Not: $nt" else "${sh[1]} • Not için Düzenle'ye basın."
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

    private fun setupCycle() {
        val opts = arrayOf("1. Gün: 12s Gündüz", "2. Gün: 12s Gece", "3. Gün: Grv. İstirahati 1", "4. Gün: Grv. İstirahati 2")
        AlertDialog.Builder(this).setTitle("12/24 - 12/48 Döngü").setItems(opts) { _, s ->
            val c = Calendar.getInstance()
            DatePickerDialog(this, { _, y, m, d ->
                val ch = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0) }
                prefs.edit().putLong("anc", ch.timeInMillis).putInt("step", s).apply()
                drawCal(); updatePanel(); updateTop()
            }, c.get(1), c.get(2), c.get(5)).show()
        }.show()
    }

    private fun editDay() {
        val k = keyOf(selCal)
        val items = (shifts.map { "${it[3]} ${it[1]}" } + "Otomatik Döngüye Sıfırla").toTypedArray()
        AlertDialog.Builder(this).setTitle("Vardiya Seç").setItems(items) { _, i ->
            if (i < shifts.size) prefs.edit().putString("ov_$k", shifts[i][0]).apply() else prefs.edit().remove("ov_$k").apply()
            val ed = EditText(this).apply { hint = "Not yaz..."; setText(prefs.getString("nt_$k", "")) }
            AlertDialog.Builder(this).setTitle("Günlük Not").setView(ed).setPositiveButton("Kaydet") { _, _ ->
                prefs.edit().putString("nt_$k", ed.text.toString().trim()).apply(); drawCal(); updatePanel(); updateTop()
            }.setNegativeButton("Kapat") { _, _ -> drawCal(); updatePanel(); updateTop() }.show()
        }.show()
    }

    private fun setAlarm() {
        val sh = shiftFor(selCal)
        TimePickerDialog(this, { _, h, m ->
            try { startActivity(Intent(AlarmClock.ACTION_SET_ALARM).apply { putExtra(AlarmClock.EXTRA_HOUR, h); putExtra(AlarmClock.EXTRA_MINUTES, m); putExtra(AlarmClock.EXTRA_MESSAGE, "Ticaret Koruma - ${sh[1]}") }) }
            catch (_: Exception) { Toast.makeText(this, "Saat açılamadı", 0).show() }
        }, sh[5].toInt(), sh[6].toInt(), true).show()
    }

    private fun addCal() {
        val sh = shiftFor(selCal)
        val st = (selCal.clone() as Calendar).apply { set(11, if (sh[0] == "GECE") 20 else 8); set(12, 0) }
        try { startActivity(Intent(Intent.ACTION_INSERT).apply { data = CalendarContract.Events.CONTENT_URI; putExtra("title", "Ticaret Koruma: ${sh[1]}"); putExtra("beginTime", st.timeInMillis); putExtra("endTime", st.timeInMillis + 43200000L) }) }
        catch (_: Exception) { Toast.makeText(this, "Takvim bulunamadı", 0).show() }
    }

    private fun loadWeather() {
        Thread {
            try {
                val res = URL("https://api.open-meteo.com/v1/forecast?latitude=39.95&longitude=32.68&current_weather=true").readText()
                val t = Regex("\"temperature\":([-0-9.]+)").find(res)?.groupValues?.get(1)?.substringBefore(".") ?: "--"
                runOnUiThread { tvLoc.text = "📍 Etimesgut • PARÇALI BULUTLU"; tvTemp.text = "☁ ${t}°" }
            } catch (_: Exception) { runOnUiThread { tvLoc.text = "📍 Etimesgut • ÇEVRİMDIŞI" } }
        }.start()
    }
}
