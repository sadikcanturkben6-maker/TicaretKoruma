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
    private val prefs by lazy { getSharedPreferences("TKPrefs", Context.MODE_PRIVATE) }
    private val tr = Locale("tr", "TR")
    private val handler = Handler(Looper.getMainLooper())
    private var monthCal = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
    private var selCal = Calendar.getInstance()

    private lateinit var tvGreet: TextView
    private lateinit var tvTodayDate: TextView
    private lateinit var tvLocWeather: TextView
    private lateinit var tvTempBig: TextView
    private lateinit var tvHeroShift: TextView
    private lateinit var tvHeroSub: TextView
    private lateinit var tvHeroTimer: TextView
    private lateinit var tvNext1: TextView
    private lateinit var tvNext2: TextView
    private lateinit var tvNext3: TextView
    private lateinit var tvMonth: TextView
    private lateinit var grid: GridLayout
    private lateinit var tvStatGunduz: TextView
    private lateinit var tvStatGece: TextView
    private lateinit var tvStatIst: TextView
    private lateinit var tvDayTitle: TextView
    private lateinit var tvDayBadge: TextView
    private lateinit var tvDayNote: TextView

    // [id, uzunAd, kisaAd, ikon, renkHex, alarmSaat, alarmDakika]
    private val shifts = listOf(
        arrayOf("GUNDUZ", "Gündüz (08:00 - 20:00)", "GÜNDÜZ", "☀", "#2563EB", "6", "30"),
        arrayOf("GECE", "Gece (20:00 - 08:00)", "GECE", "☾", "#4F46E5", "18", "30"),
        arrayOf("ISTIRAHAT", "Görev İstirahati", "Grv. İst.", "🛏", "#16A34A", "9", "0"),
        arrayOf("YILLIK", "Yıllık İzin", "İZİN", "✈", "#8B5CF6", "9", "0"),
        arrayOf("RAPOR", "Sağlık Raporu", "RAPOR", "✚", "#EF4444", "9", "0"),
        arrayOf("RESMI", "Resmi / İdari İzin", "RESMİ", "★", "#06B6D4", "9", "0")
    )

    private fun px(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun bg(hex: String, r: Int, stroke: String? = null) = GradientDrawable().apply {
        cornerRadius = px(r).toFloat()
        setColor(Color.parseColor(hex))
        if (stroke != null) setStroke(px(1), Color.parseColor(stroke))
    }
    private fun gradBg(c1: String, c2: String, r: Int) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR, intArrayOf(Color.parseColor(c1), Color.parseColor(c2))
    ).apply { cornerRadius = px(r).toFloat() }

    private fun keyOf(c: Calendar) = String.format(Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))

    private val ticker = object : Runnable {
        override fun run() {
            updateHeroAndNext()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#090E1A")); isFillViewport = true }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px(14), px(16), px(14), px(26)) }

        // Üst Karşılama ve Döngü Kur Butonu
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val titleCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        tvGreet = TextView(this).apply {
            text = prefs.getString("greet", "Hoşgeldin Devrem 👋"); textSize = 21f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
            setOnClickListener {
                val ed = EditText(this@MainActivity).apply { setText(text) }
                AlertDialog.Builder(this@MainActivity).setTitle("Karşılama Başlığı").setView(ed).setPositiveButton("Kaydet") { _, _ ->
                    val t = ed.text.toString().trim().ifEmpty { "Hoşgeldin Devrem 👋" }
                    prefs.edit().putString("greet", t).apply(); text = t
                }.setNegativeButton("İptal", null).show()
            }
        }
        tvTodayDate = TextView(this).apply {
            text = SimpleDateFormat("EEEE, d MMMM", tr).format(Calendar.getInstance().time).replaceFirstChar { it.titlecase(tr) }
            textSize = 13f; setTextColor(Color.parseColor("#94A3B8"))
        }
        titleCol.addView(tvGreet); titleCol.addView(tvTodayDate)

        val bCycle = Button(this).apply {
            text = "Döngü Kur"; textSize = 12f; setTextColor(Color.WHITE); background = bg("#1E293B", 10); setPadding(px(12), px(6), px(12), px(6))
            setOnClickListener { setupCycle() }
        }
        top.addView(titleCol); top.addView(bCycle); root.addView(top)

        // Mavi Hero Kartı (Etimesgut Hava + Vardiya + Geri Sayım)
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = gradBg("#1D4ED8", "#0F294A", 16); setPadding(px(16), px(14), px(16), px(14))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(12) }
        }
        val heroTop = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        tvLocWeather = TextView(this).apply { text = "📍 Etimesgut • YÜKLENİYOR"; textSize = 11f; setTextColor(Color.parseColor("#BAE6FD")); setTypeface(null, Typeface.BOLD); layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        tvTempBig = TextView(this).apply { text = "--°"; textSize = 24f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD) }
        heroTop.addView(tvLocWeather); heroTop.addView(tvTempBig); hero.addView(heroTop)

        val heroMid = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, px(4), 0, px(6)) }
        tvHeroShift = TextView(this).apply { text = "☀ GÜNDÜZ 08:00 - 20:00"; textSize = 17f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD); layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        tvHeroTimer = TextView(this).apply { text = "--:--:--"; textSize = 17f; setTextColor(Color.parseColor("#FDE047")); setTypeface(null, Typeface.BOLD) }
        heroMid.addView(tvHeroShift); heroMid.addView(tvHeroTimer); hero.addView(heroMid)

        tvHeroSub = TextView(this).apply { text = "☕ Hava sakin, vukuatsız nöbetler."; textSize = 12f; setTextColor(Color.parseColor("#E0F2FE")) }
        hero.addView(tvHeroSub); root.addView(hero)

        // 3'lü Hızlı Özet Kutuları (Yarın / Öbür Gün / Sonraki)
        val nextRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(10) }
        }
        fun makeMiniBox(marginEndDp: Int): TextView = TextView(this).apply {
            textSize = 11f; setTextColor(Color.WHITE); background = bg("#111827", 12, "#1F2937"); setPadding(px(10), px(8), px(10), px(8))
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = px(marginEndDp) }
        }
        tvNext1 = makeMiniBox(6); tvNext2 = makeMiniBox(6); tvNext3 = makeMiniBox(0)
        nextRow.addView(tvNext1); nextRow.addView(tvNext2); nextRow.addView(tvNext3); root.addView(nextRow)

        // Takvim Ana Çerçevesi
        val calBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = bg("#111827", 16, "#1F2937"); setPadding(px(10), px(12), px(10), px(12))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(12) }
        }
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, px(8)) }
        val bPrev = Button(this).apply { text = "◀"; setTextColor(Color.WHITE); background = bg("#1E293B", 8); setOnClickListener { monthCal.add(Calendar.MONTH, -1); drawCal() } }
        tvMonth = TextView(this).apply { textSize = 16f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        val bNext = Button(this).apply { text = "▶"; setTextColor(Color.WHITE); background = bg("#1E293B", 8); setOnClickListener { monthCal.add(Calendar.MONTH, 1); drawCal() } }
        nav.addView(bPrev); nav.addView(tvMonth); nav.addView(bNext); calBox.addView(nav)

        val wk = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, px(6)) }
        for (d in listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")) {
            wk.addView(TextView(this).apply { text = d; textSize = 11f; setTextColor(Color.parseColor("#94A3B8")); gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        }
        calBox.addView(wk)

        grid = GridLayout(this).apply { columnCount = 7; rowCount = 6 }
        calBox.addView(grid); root.addView(calBox)

        // Aylık İstatistik Çubuğu (Geçen gün / ayın toplamı)
        val statBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = bg("#111827", 14, "#1F2937"); setPadding(px(12), px(10), px(12), px(10))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(10) }
        }
        statBox.addView(TextView(this).apply { text = "📅 Geçen gün / ayın toplamı"; textSize = 11f; setTextColor(Color.parseColor("#94A3B8")); gravity = Gravity.CENTER; setPadding(0, 0, 0, px(6)) })
        val statRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tvStatGunduz = TextView(this).apply { textSize = 13f; setTextColor(Color.parseColor("#60A5FA")); setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        tvStatGece = TextView(this).apply { textSize = 13f; setTextColor(Color.parseColor("#A78BFA")); setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        tvStatIst = TextView(this).apply { textSize = 13f; setTextColor(Color.parseColor("#4ADE80")); setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        statRow.addView(tvStatGunduz); statRow.addView(tvStatGece); statRow.addView(tvStatIst); statBox.addView(statRow); root.addView(statBox)

        // Seçili Gün Detay ve İşlem Kartı
        val det = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = bg("#111827", 14, "#1F2937"); setPadding(px(14), px(12), px(14), px(12))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(10) }
        }
        val dTop = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        tvDayTitle = TextView(this).apply { textSize = 15f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD); layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        tvDayBadge = TextView(this).apply { textSize = 11f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD); setPadding(px(10), px(4), px(10), px(4)) }
        dTop.addView(tvDayTitle); dTop.addView(tvDayBadge); det.addView(dTop)

        tvDayNote = TextView(this).apply { textSize = 12f; setTextColor(Color.parseColor("#CBD5E1")); setPadding(0, px(6), 0, px(10)) }
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

        drawCal(); updatePanel(); updateHeroAndNext(); loadWeather()
    }

    override fun onResume() { super.onResume(); handler.post(ticker) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(ticker) }

    // 12/24 - 12/48 Doğru Döngü: 0=Gündüz, 1=Gece, 2=Grv. İst. (1.Gün), 3=Grv. İst. (2.Gün)
    private fun shiftFor(c: Calendar): Array<String> {
        val ov = prefs.getString("ov_${keyOf(c)}", null)
        if (ov != null) return shifts.find { it[0] == ov } ?: shifts[0]
        val anc = prefs.getLong("anc", -1L)
        if (anc == -1L) return shifts[2]
        val a = Calendar.getInstance().apply { timeInMillis = anc; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val t = (c.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val diff = TimeUnit.MILLISECONDS.toDays(t.timeInMillis - a.timeInMillis).toInt()
        val pos = (((diff + prefs.getInt("step", 0)) % 4) + 4) % 4
        return when (pos) {
            0 -> shifts[0] // Gündüz
            1 -> shifts[1] // Gece
            else -> shifts[2] // Grv. İst. (2 gün üst üste)
        }
    }

    private fun drawCal() {
        tvMonth.text = SimpleDateFormat("MMMM yyyy", tr).format(monthCal.time).uppercase(tr)
        grid.removeAllViews()
        val c = (monthCal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
        val offset = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val maxD = c.getActualMaximum(Calendar.DAY_OF_MONTH)
        val todayCal = Calendar.getInstance()
        val todayKey = keyOf(todayCal)
        val selKey = keyOf(selCal)
        val w = (resources.displayMetrics.widthPixels - px(48)) / 7

        var totG = 0; var totN = 0; var totI = 0
        var pasG = 0; var pasN = 0; var pasI = 0

        for (i in 0 until offset) grid.addView(View(this).apply { layoutParams = GridLayout.LayoutParams().apply { width = w; height = px(56) } })
        for (d in 1..maxD) {
            val dc = (monthCal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, d) }
            val k = keyOf(dc); val sh = shiftFor(dc)
            val isPastOrToday = !dc.after(todayCal)

            when (sh[0]) {
                "GUNDUZ" -> { totG++; if (isPastOrToday) pasG++ }
                "GECE" -> { totN++; if (isPastOrToday) pasN++ }
                else -> { totI++; if (isPastOrToday) pasI++ }
            }

            val noted = !prefs.getString("nt_$k", "").isNullOrBlank()
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(px(2), px(3), px(2), px(3))
                val brd = if (k == selKey) "#FFFFFF" else if (k == todayKey) "#38BDF8" else sh[4]
                val fillHex = when (sh[0]) {
                    "GUNDUZ" -> "#172554"
                    "GECE" -> "#1E1B4B"
                    "ISTIRAHAT" -> "#052E16"
                    else -> "#1E293B"
                }
                background = bg(fillHex, 9, brd)
                layoutParams = GridLayout.LayoutParams().apply { width = w - px(4); height = px(56); setMargins(px(2), px(2), px(2), px(2)) }
                setOnClickListener { selCal = dc; drawCal(); updatePanel() }
            }
            cell.addView(TextView(this).apply { text = if (noted) "$d•" else "$d"; textSize = 13f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER })
            cell.addView(TextView(this).apply { text = sh[2]; textSize = 8.5f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER })
            cell.addView(TextView(this).apply { text = sh[3]; textSize = 9f; setTextColor(Color.parseColor("#CBD5E1")); gravity = Gravity.CENTER })
            grid.addView(cell)
        }

        tvStatGunduz.text = "☀ $pasG / $totG\nGündüz"
        tvStatGece.text = "☾ $pasN / $totN\nGece"
        tvStatIst.text = "🛏 $pasI / $totI\nİst."
    }

    private fun updatePanel() {
        tvDayTitle.text = SimpleDateFormat("d MMMM yyyy, EEEE", tr).format(selCal.time)
        val sh = shiftFor(selCal)
        tvDayBadge.text = "${sh[3]} ${sh[2]}"; tvDayBadge.background = bg(sh[4], 8)
        val nt = prefs.getString("nt_${keyOf(selCal)}", "") ?: ""
        tvDayNote.text = if (nt.isNotBlank()) "Not: $nt" else "${sh[1]} • Not eklemek için Düzenle'ye dokunun."
    }

    private fun updateHeroAndNext() {
        val now = Calendar.getInstance()
        val todayShift = shiftFor(now)
        tvHeroShift.text = "${todayShift[3]} ${todayShift[1]}"

        // Yarın / Öbür gün / 3. gün özetleri
        val d1 = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        val d2 = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 2) }
        val d3 = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 3) }
        val s1 = shiftFor(d1); val s2 = shiftFor(d2); val s3 = shiftFor(d3)

        tvNext1.text = "Yarın\n${s1[3]} ${s1[2]}\n${if (s1[0]=="GECE") "20:00 - 08:00" else if (s1[0]=="GUNDUZ") "08:00 - 20:00" else "İstirahat"}"
        tvNext2.text = "Öbür gün\n${s2[3]} ${s2[2]}\n${if (s2[0]=="GECE") "20:00 - 08:00" else if (s2[0]=="GUNDUZ") "08:00 - 20:00" else "İstirahat"}"
        val d3Label = SimpleDateFormat("d MMM", tr).format(d3.time).uppercase(tr)
        tvNext3.text = "$d3Label\n${s3[3]} ${s3[2]}\n${if (s3[0]=="GECE") "20:00 - 08:00" else if (s3[0]=="GUNDUZ") "08:00 - 20:00" else "İstirahat"}"

        // Canlı Geri Sayım
        for (i in 0..7) {
            val cc = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) }
            val sh = shiftFor(cc)
            if (sh[0] == "GUNDUZ" || sh[0] == "GECE") {
                val st = (cc.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, if (sh[0] == "GUNDUZ") 8 else 20); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
                val en = (st.clone() as Calendar).apply { add(Calendar.HOUR_OF_DAY, 12) }
                val target = if (now.before(st)) st else if (now.before(en)) en else null
                if (target != null) {
                    val sec = (target.timeInMillis - now.timeInMillis) / 1000
                    val prefix = if (now.before(st)) "NÖBETE " else "BİTİME "
                    tvHeroTimer.text = String.format(Locale.US, "%s%02ds %02ddk", prefix, sec / 3600, (sec % 3600) / 60)
                    return
                }
            }
        }
        tvHeroTimer.text = "--:--"
    }

    private fun setupCycle() {
        val opts = arrayOf(
            "1. Gün: 12s Gündüz (08:00 - 20:00)",
            "2. Gün: 12s Gece (20:00 - 08:00)",
            "3. Gün: Görev İstirahati (1. Gün)",
            "4. Gün: Görev İstirahati (2. Gün)"
        )
        AlertDialog.Builder(this).setTitle("12/24 - 12/48 Döngü Kurulumu").setItems(opts) { _, s ->
            val c = Calendar.getInstance()
            DatePickerDialog(this, { _, y, m, d ->
                val ch = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0) }
                prefs.edit().putLong("anc", ch.timeInMill
