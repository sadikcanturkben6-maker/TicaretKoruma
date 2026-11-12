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
import java.net.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("TKPrefs", Context.MODE_PRIVATE) }
    private val tr = Locale("tr", "TR")
    private val handler = Handler(Looper.getMainLooper())
    private var monthCal = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
    private var selCal = Calendar.getInstance()

    private lateinit var tvTitle: TextView
    private lateinit var tvTimerTitle: TextView
    private lateinit var tvTimer: TextView
    private lateinit var tvTimerSub: TextView
    private lateinit var tvWeather: TextView
    private lateinit var tvMonth: TextView
    private lateinit var grid: GridLayout
    private lateinit var tvDayTitle: TextView
    private lateinit var tvDayBadge: TextView
    private lateinit var tvDayNote: TextView

    private val shifts = listOf(
        arrayOf("GUNDUZ", "Gündüz Vardiyası (12s)", "GÜNDÜZ", "#F59E0B", "6", "30"),
        arrayOf("GECE", "Gece Vardiyası (12s)", "GECE", "#3B82F6", "18", "30"),
        arrayOf("ISTIRAHAT", "İstirahat / Tatil", "TATİL", "#10B981", "9", "0"),
        arrayOf("YILLIK", "Yıllık İzin", "İZİN", "#8B5CF6", "9", "0"),
        arrayOf("RAPOR", "Sağlık Raporu", "RAPOR", "#EF4444", "9", "0"),
        arrayOf("RESMI", "Resmi Tatil / İdari", "RESMİ", "#06B6D4", "9", "0")
    )

    private fun px(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun bg(hex: String, r: Int, stroke: String? = null) = GradientDrawable().apply {
        cornerRadius = px(r).toFloat()
        setColor(Color.parseColor(hex))
        if (stroke != null) setStroke(px(1), Color.parseColor(stroke))
    }
    private fun keyOf(c: Calendar) = String.format(Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))

    private val ticker = object : Runnable {
        override fun run() {
            updateTimer()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#0B1120")); isFillViewport = true }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px(16), px(18), px(16), px(28)) }

        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        tvTitle = TextView(this).apply {
            text = prefs.getString("title", "Ticaret Koruma"); textSize = 22f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            setOnClickListener {
                val ed = EditText(this@MainActivity).apply { setText(text) }
                AlertDialog.Builder(this@MainActivity).setTitle("Başlık").setView(ed).setPositiveButton("Kaydet") { _, _ ->
                    val t = ed.text.toString().trim().ifEmpty { "Ticaret Koruma" }
                    prefs.edit().putString("title", t).apply(); text = t
                }.setNegativeButton("İptal", null).show()
            }
        }
        val bCycle = Button(this).apply {
            text = "Döngü Kur"; textSize = 12f; setTextColor(Color.WHITE); background = bg("#1E293B", 10); setPadding(px(12), px(6), px(12), px(6))
            setOnClickListener { setupCycle() }
        }
        val bGreet = Button(this).apply {
            text = "Karşılama"; textSize = 12f; setTextColor(Color.parseColor("#93C5FD")); background = bg("#1E293B", 10); setPadding(px(12), px(6), px(12), px(6))
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginStart = px(8) }
            setOnClickListener {
                val ed = EditText(this@MainActivity).apply { setText(prefs.getString("greet", "Hayırlı nöbetler Sadık Can, kolaylıklar.")) }
                AlertDialog.Builder(this@MainActivity).setTitle("Karşılama Mesajı").setView(ed).setPositiveButton("Kaydet") { _, _ ->
                    val m = ed.text.toString().trim(); prefs.edit().putString("greet", m).apply(); Toast.makeText(this@MainActivity, m, Toast.LENGTH_LONG).show()
                }.setNegativeButton("İptal", null).show()
            }
        }
        top.addView(tvTitle); top.addView(bCycle); top.addView(bGreet); root.addView(top)

        tvWeather = TextView(this).apply { text = "Ankara hava durumu yükleniyor..."; textSize = 12f; setTextColor(Color.parseColor("#94A3B8")); setPadding(0, px(4), 0, px(14)) }
        root.addView(tvWeather)

        val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = bg("#131C31", 16, "#1E293B"); setPadding(px(16), px(16), px(16), px(16)) }
        tvTimerTitle = TextView(this).apply { text = "SONRAKİ NÖBET"; textSize = 11f; setTextColor(Color.parseColor("#38BDF8")); setTypeface(null, Typeface.BOLD) }
        tvTimer = TextView(this).apply { text = "--:--:--"; textSize = 30f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD); setPadding(0, px(4), 0, px(2)) }
        tvTimerSub = TextView(this).apply { text = "Başlamak için 'Döngü Kur' seçin"; textSize = 12f; setTextColor(Color.parseColor("#94A3B8")) }
        card.addView(tvTimerTitle); card.addView(tvTimer); card.addView(tvTimerSub); root.addView(card)

        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, px(18), 0, px(10)) }
        val bPrev = Button(this).apply { text = "◀"; setTextColor(Color.WHITE); background = bg("#1E293B", 10); setOnClickListener { monthCal.add(Calendar.MONTH, -1); drawCal() } }
        tvMonth = TextView(this).apply { textSize = 17f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        val bNext = Button(this).apply { text = "▶"; setTextColor(Color.WHITE); background = bg("#1E293B", 10); setOnClickListener { monthCal.add(Calendar.MONTH, 1); drawCal() } }
        nav.addView(bPrev); nav.addView(tvMonth); nav.addView(bNext); root.addView(nav)

        val wk = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, px(6)) }
        for (d in listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")) {
            wk.addView(TextView(this).apply { text = d; textSize = 12f; setTextColor(Color.parseColor("#94A3B8")); gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        }
        root.addView(wk)

        grid = GridLayout(this).apply { columnCount = 7; rowCount = 6 }
        root.addView(grid)

        val det = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = bg("#131C31", 16, "#1E293B"); setPadding(px(16), px(16), px(16), px(16))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(16) }
        }
        val dTop = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        tvDayTitle = TextView(this).apply { textSize = 16f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD); layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        tvDayBadge = TextView(this).apply { textSize = 12f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD); setPadding(px(10), px(4), px(10), px(4)) }
        dTop.addView(tvDayTitle); dTop.addView(tvDayBadge); det.addView(dTop)

        tvDayNote = TextView(this).apply { textSize = 13f; setTextColor(Color.parseColor("#CBD5E1")); setPadding(0, px(8), 0, px(12)) }
        det.addView(tvDayNote)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val bEdit = Button(this).apply {
            text = "Düzenle / Not"; textSize = 12f; setTextColor(Color.WHITE); background = bg("#2563EB", 10)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = px(6) }
            setOnClickListener { editDay() }
        }
        val bAlarm = Button(this).apply {
            text = "Alarm Kur"; textSize = 12f; setTextColor(Color.WHITE); background = bg("#059669", 10)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = px(6) }
            setOnClickListener { setAlarm() }
        }
        val bCal = Button(this).apply {
            text = "Takvime At"; textSize = 12f; setTextColor(Color.WHITE); background = bg("#475569", 10)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            setOnClickListener { addCal() }
        }
        row.addView(bEdit); row.addView(bAlarm); row.addView(bCal); det.addView(row)
        root.addView(det); scroll.addView(root); setContentView(scroll)

        drawCal(); updatePanel(); updateTimer(); loadWeather()
    }

    override fun onResume() { super.onResume(); handler.post(ticker) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(ticker) }

    private fun shiftFor(c: Calendar): Array<String> {
        val ov = prefs.getString("ov_${keyOf(c)}", null)
        if (ov != null) return shifts.find { it[0] == ov } ?: shifts[0]
        val anc = prefs.getLong("anc", -1L)
        if (anc == -1L) return shifts[2]
        val a = Calendar.getInstance().apply { timeInMillis = anc; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val t = (c.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val diff = TimeUnit.MILLISECONDS.toDays(t.timeInMillis - a.timeInMillis).toInt()
        val pos = (((diff + prefs.getInt("step", 0)) % 4) + 4) % 4
        return when (pos) { 0 -> shifts[0]; 1 -> shifts[2]; 2 -> shifts[1]; else -> shifts[2] }
    }

    private fun drawCal() {
        tvMonth.text = SimpleDateFormat("MMMM yyyy", tr).format(monthCal.time).replaceFirstChar { it.titlecase(tr) }
        grid.removeAllViews()
        val c = (monthCal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
        val offset = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val maxD = c.getActualMaximum(Calendar.DAY_OF_MONTH)
        val today = keyOf(Calendar.getInstance())
        val sel = keyOf(selCal)
        val w = (resources.displayMetrics.widthPixels - px(32)) / 7

        for (i in 0 until offset) grid.addView(View(this).apply { layoutParams = GridLayout.LayoutParams().apply { width = w; height = px(52) } })
        for (d in 1..maxD) {
            val dc = (monthCal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, d) }
            val k = keyOf(dc); val sh = shiftFor(dc)
            val noted = !prefs.getString("nt_$k", "").isNullOrBlank()
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(px(2), px(4), px(2), px(4))
                val brd = if (k == sel) "#FFFFFF" else if (k == today) "#38BDF8" else "#1E293B"
                background = bg(if (k == sel) "#1E293B" else "#0F172A", 10, brd)
                layoutParams = GridLayout.LayoutParams().apply { width = w - px(4); height = px(52); setMargins(px(2), px(2), px(2), px(2)) }
                setOnClickListener { selCal = dc; drawCal(); updatePanel() }
            }
            cell.addView(TextView(this).apply { text = if (noted) "$d•" else "$d"; textSize = 13f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER })
            cell.addView(TextView(this).apply { text = sh[2]; textSize = 9f; setTextColor(Color.parseColor(sh[3])); setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER })
            grid.addView(cell)
        }
    }

    private fun updatePanel() {
        tvDayTitle.text = SimpleDateFormat("d MMMM yyyy, EEEE", tr).format(selCal.time)
        val sh = shiftFor(selCal)
        tvDayBadge.text = sh[2]; tvDayBadge.background = bg(sh[3], 8)
        val nt = prefs.getString("nt_${keyOf(selCal)}", "") ?: ""
        tvDayNote.text = if (nt.isNotBlank()) "Not: $nt" else "${sh[1]} • Not eklemek için Düzenle'ye dokunun."
    }

    private fun updateTimer() {
        val now = Calendar.getInstance()
        for (i in 0..7) {
            val cc = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) }
            val sh = shiftFor(cc)
            if (sh[0] == "GUNDUZ" || sh[0] == "GECE") {
                val st = (cc.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, if (sh[0] == "GUNDUZ") 8 else 20); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
                val en = (st.clone() as Calendar).apply { add(Calendar.HOUR_OF_DAY, 12) }
                val target = if (now.before(st)) st else if (now.before(en)) en else null
                if (target != null) {
                    val sec = (target.timeInMillis - now.timeInMillis) / 1000
                    tvTimerTitle.text = if (now.before(st)) "SONRAKİ NÖBET: ${sh[2]}" else "AKTİF NÖBET BİTİMİNE"
                    tvTimer.text = String.format(Locale.US, "%02d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60)
                    tvTimerSub.text = (if (now.before(st)) "Başlangıç: " else "Bitiş: ") + SimpleDateFormat("d MMM HH:mm", tr).format(target.time)
                    return
                }
            }
        }
        tvTimer.text = "--:--:--"
    }

    private fun setupCycle() {
        val opts = arrayOf("1. Gün: 12s Gündüz Nöbeti", "2. Gün: 24s İstirahat", "3. Gün: 12s Gece Nöbeti", "4. Gün: 48s İstirahat")
        AlertDialog.Builder(this).setTitle("12/24 - 12/48 Döngü").setItems(opts) { _, s ->
            val c = Calendar.getInstance()
            DatePickerDialog(this, { _, y, m, d ->
                val ch = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0) }
                prefs.edit().putLong("anc", ch.timeInMillis).putInt("step", s).apply()
                drawCal(); updatePanel(); updateTimer()
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
        }.show()
    }

    private fun editDay() {
        val k = keyOf(selCal)
        val items = (shifts.map { it[1] } + "Otomatik Döngüye Sıfırla").toTypedArray()
        AlertDialog.Builder(this).setTitle("Vardiya Seçimi").setItems(items) { _, idx ->
            if (idx < shifts.size) prefs.edit().putString("ov_$k", shifts[idx][0]).apply() else prefs.edit().remove("ov_$k").apply()
            val ed = EditText(this).apply { hint = "Günlük not..."; setText(prefs.getString("nt_$k", "")) }
            AlertDialog.Builder(this).setTitle("Günlük Not").setView(ed).setPositiveButton("Kaydet") { _, _ ->
                prefs.edit().putString("nt_$k", ed.text.toString().trim()).apply(); drawCal(); updatePanel()
            }.setNegativeButton("Kapat") { _, _ -> drawCal(); updatePanel() }.show()
        }.show()
    }

    private fun setAlarm() {
        val sh = shiftFor(selCal)
        TimePickerDialog(this, { _, h, m ->
            try {
                startActivity(Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, h); putExtra(AlarmClock.EXTRA_MINUTES, m)
                    putExtra(AlarmClock.EXTRA_MESSAGE, "${tvTitle.text} - ${sh[1]}")
                })
            } catch (_: Exception) { Toast.makeText(this, "Saat uygulaması açılamadı", Toast.LENGTH_SHORT).show() }
        }, sh[4].toInt(), sh[5].toInt(), true).show()
    }

    private fun addCal() {
        val sh = shiftFor(selCal)
        val st = (selCal.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, if (sh[0] == "GECE") 20 else 8); set(Calendar.MINUTE, 0) }
        try {
            startActivity(Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, "${tvTitle.text}: ${sh[1]}")
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, st.timeInMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, st.timeInMillis + TimeUnit.HOURS.toMillis(12))
            })
        } catch (_: Exception) { Toast.makeText(this, "Takvim uygulaması bulunamadı", Toast.LENGTH_SHORT).show() }
    }

    private fun loadWeather() {
        Thread {
            try {
                val res = URL("https://api.open-meteo.com/v1/forecast?latitude=39.93&longitude=32.85&current_weather=true").readText()
                val t = Regex("\"temperature\":([-0-9.]+)").find(res)?.groupValues?.get(1) ?: "--"
                runOnUiThread { tvWeather.text = "Ankara: ${t}°C • Nöbet Takip Aktif" }
            } catch (_: Exception) { runOnUiThread { tvWeather.text = "Ankara • Çevrimdışı Mod" } }
        }.start()
    }
}
