package com.ticaretkoruma.app

import android.app.*
import android.content.*
import android.graphics.*
import android.graphics.drawable.*
import android.os.*
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.net.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("TKPrefs", 0) }
    private val tr = Locale("tr", "TR")
    private val handler = Handler(Looper.getMainLooper())
    private var monthCal = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
    private var selCal = Calendar.getInstance()

    private lateinit var tvGreet: TextView
    private lateinit var tvLoc: TextView
    private lateinit var tvTemp: TextView
    private lateinit var tvHero: TextView
    private lateinit var tvTimer: TextView
    private lateinit var tvMonth: TextView
    private lateinit var grid: GridLayout
    private lateinit var tvStat: TextView

    private val shifts = listOf(
        arrayOf("GUNDUZ", "Gündüz", "GÜNDÜZ", "☀", "#2563EB", "08:00 - 20:00"),
        arrayOf("AKSAM", "Akşam", "AKŞAM", "🌇", "#9333EA", "16:00 - 00:00"),
        arrayOf("GECE", "Gece", "GECE", "☾", "#4F46E5", "20:00 - 08:00"),
        arrayOf("ISTIRAHAT", "Görev İstirahati", "Grv. İst.", "🛏", "#16A34A", "Dinlenme"),
        arrayOf("YILLIK", "Yıllık İzin", "İZİN", "✈", "#8B5CF6", "Dinlenme"),
        arrayOf("MAZERET", "Mazeret", "MAZERET", "★", "#D97706", "Dinlenme"),
        arrayOf("RAPOR", "Sağlık Raporu", "RAPOR", "✚", "#EF4444", "Dinlenme")
    )

    private val hols = mapOf(
        "01-01" to "Yılbaşı", "04-23" to "Ulusal Egemenlik", "05-01" to "İşçi Bayramı",
        "05-19" to "Atatürk'ü Anma", "07-15" to "Demokrasi Günü", "08-30" to "Zafer", "10-29" to "Cumhuriyet",
        "2026-03-20" to "Ramazan Bayramı Arf.", "2026-03-21" to "Ramazan Bayramı 1. Gün", "2026-03-22" to "Ramazan Bayramı 2. Gün", "2026-03-23" to "Ramazan Bayramı 3. Gün",
        "2026-05-26" to "Kurban Bayramı Arf.", "2026-05-27" to "Kurban Bayramı 1. Gün", "2026-05-28" to "Kurban Bayramı 2. Gün", "2026-05-29" to "Kurban Bayramı 3. Gün", "2026-05-30" to "Kurban Bayramı 4. Gün",
        "2027-03-09" to "Ramazan Bayramı Arf.", "2027-03-10" to "Ramazan Bayramı 1. Gün", "2027-03-11" to "Ramazan Bayramı 2. Gün", "2027-03-12" to "Ramazan Bayramı 3. Gün",
        "2027-05-16" to "Kurban Bayramı Arf.", "2027-05-17" to "Kurban Bayramı 1. Gün", "2027-05-18" to "Kurban Bayramı 2. Gün", "2027-05-19" to "Kurban Bayramı 3. Gün", "2027-05-20" to "Kurban Bayramı 4. Gün"
    )

    private fun px(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun bg(hex: String, r: Int, st: String? = null, stW: Int = 1): GradientDrawable = GradientDrawable().apply {
        cornerRadius = px(r).toFloat()
        setColor(Color.parseColor(hex))
        if (st != null) setStroke(px(stW), Color.parseColor(st))
    }
    private fun kOf(c: Calendar): String = String.format(Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))

    private val ticker = object : Runnable { override fun run() { updHero(); handler.postDelayed(this, 1000L) } }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        supportActionBar?.hide()
        val mLay = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#090E1A")) }
        val scr = ScrollView(this).apply { isFillViewport = true; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f) }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px(14), px(16), px(14), px(20)) }

        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val tCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        tvGreet = TextView(this).apply { text = prefs.getString("greet", "Hoşgeldin Devrem 👋"); textSize = 20f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD) }
        tCol.addView(tvGreet)
        tCol.addView(TextView(this).apply { text = SimpleDateFormat("EEEE, d MMMM", tr).format(Date()); textSize = 12f; setTextColor(Color.parseColor("#94A3B8")) })
        top.addView(tCol)
        
        top.addView(Button(this).apply { text = "🔄"; textSize = 14f; setTextColor(Color.WHITE); background = bg("#1E293B", 10); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = px(6) }; setPadding(px(10), px(6), px(10), px(6)); setOnClickListener { loadWeather(); drawCal(); updHero(); Toast.makeText(context, "Yenilendi", Toast.LENGTH_SHORT).show() } })
        top.addView(Button(this).apply { text = "Döngü Kur"; textSize = 12f; setTextColor(Color.WHITE); background = bg("#1E293B", 10); setPadding(px(12), px(6), px(12), px(6)); setOnClickListener { setCyc() } })
        root.addView(top)

        val hero = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = bg("#1E3A8A", 16, "#3B82F6"); setPadding(px(16), px(14), px(16), px(14)); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = px(12) } }
        val h1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        tvLoc = TextView(this).apply { text = "📍 Etimesgut"; textSize = 12f; setTextColor(Color.parseColor("#BAE6FD")); setTypeface(null, Typeface.BOLD); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        tvTemp = TextView(this).apply { text = "--°"; textSize = 22f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD) }
        h1.addView(tvLoc); h1.addView(tvTemp); hero.addView(h1)

        val h2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, px(6), 0, px(4)) }
        tvHero = TextView(this).apply { textSize = 15f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f) }
        tvTimer = TextView(this).apply { textSize = 14f; setTextColor(Color.parseColor("#FDE047")); setTypeface(null, Typeface.BOLD); gravity = Gravity.END; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        h2.addView(tvHero); h2.addView(tvTimer); hero.addView(h2)
        
        hero.addView(TextView(this).apply { text = "☕ Hava sakin, vukuatsız nöbetler."; textSize = 12f; setTextColor(Color.parseColor("#E0F2FE")) })
        root.addView(hero)

        val calBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = bg("#111827", 16, "#1F2937"); setPadding(px(10), px(12), px(10), px(12)); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = px(12) } }
        
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 != null) {
                    val diffX = e2.x - e1.x
                    if (Math.abs(diffX) > 50 && Math.abs(velocityX) > 100) {
                        if (diffX > 0) monthCal.add(Calendar.MONTH, -1) else monthCal.add(Calendar.MONTH, 1)
                        drawCal()
                        return true
                    }
                }
                return false
            }
        })
        calBox.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event); true }

        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, px(8)) }
        nav.addView(Button(this).apply { text = "◀"; setTextColor(Color.WHITE); background = bg("#1E293B", 8); setOnClickListener { monthCal.add(Calendar.MONTH, -1); drawCal() } })
        tvMonth = TextView(this).apply { textSize = 16f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        nav.addView(tvMonth)
        nav.addView(Button(this).apply { text = "▶"; setTextColor(Color.WHITE); background = bg("#1E293B", 8); setOnClickListener { monthCal.add(Calendar.MONTH, 1); drawCal() } })
        calBox.addView(nav)

        val wk = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, px(6)) }
        for (d in listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")) {
            wk.addView(TextView(this).apply { text = d; textSize = 11f; setTextColor(Color.parseColor("#94A3B8")); gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        }
        calBox.addView(wk)
        grid = GridLayout(this).apply { columnCount = 7; rowCount = 6 }
        calBox.addView(grid); root.addView(calBox)

        tvStat = TextView(this).apply { textSize = 12f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER; background = bg("#111827", 14, "#1F2937"); setPadding(px(12), px(12), px(12), px(12)); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = px(10) } }
        root.addView(tvStat)
        scr.addView(root); mLay.addView(scr)

        val bNav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; background = bg("#0F172A", 0); setPadding(0, px(8), 0, px(8)); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT) }
        fun nBtn(ic: String, t: String, a: () -> Unit): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(this@MainActivity).apply { text = ic; textSize = 20f; gravity = Gravity.CENTER })
            addView(TextView(this@MainActivity).apply { text = t; textSize = 10f; setTextColor(Color.GRAY); gravity = Gravity.CENTER })
            setOnClickListener { a() }
        }
        bNav.addView(nBtn("📅", "Takvim") {})
        bNav.addView(nBtn("📈", "Sayaçlar") {})
        bNav.addView(nBtn("⚙️", "Ayarlar") { openSettings() })
        bNav.addView(nBtn("💬", "İletişim") {})
        mLay.addView(bNav)

        setContentView(mLay); drawCal(); updHero(); loadWeather()
    }

    override fun onResume() { super.onResume(); handler.post(ticker) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(ticker) }

    private fun shFor(c: Calendar): Array<String> {
        val ov = prefs.getString("ov_${kOf(c)}", null)
        if (ov != null) return shifts.find { it[0] == ov } ?: shifts[0]
        val anc = prefs.getLong("anc", -1L)
        if (anc == -1L) return shifts[3]
        val a = Calendar.getInstance().apply { timeInMillis = anc; set(11, 0); set(12, 0); set(13, 0); set(14, 0) }
        val t = (c.clone() as Calendar).apply { set(11, 0); set(12, 0); set(13, 0); set(14, 0) }
        val diff = TimeUnit.MILLISECONDS.toDays(t.timeInMillis - a.timeInMillis).toInt()
        val pos = (((diff + prefs.getInt("st", 0)) % 4) + 4) % 4
        return when (pos) { 0 -> shifts[0]; 1 -> shifts[2]; else -> shifts[3] }
    }

    private fun getHol(c: Calendar): String? = hols[kOf(c)] ?: hols[String.format(Locale.US, "%02d-%02d", c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))]

    private fun drawCal() {
        tvMonth.text = SimpleDateFormat("MMMM yyyy", tr).format(monthCal.time).uppercase(tr)
        grid.removeAllViews()
        val c = (monthCal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
        val off = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val max = c.getActualMaximum(Calendar.DAY_OF_MONTH)
        val now = Calendar.getInstance()
        val w = (resources.displayMetrics.widthPixels - px(48)) / 7
        var tG = 0; var tN = 0; var tI = 0; var pG = 0; var pN = 0; var pI = 0

        for (i in 0 until off) {
            grid.addView(View(this).apply { layoutParams = GridLayout.LayoutParams().apply { width = w; height = px(56) } })
        }
        for (d in 1..max) {
            val dc = (monthCal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, d) }
            val k = kOf(dc); val sh = shFor(dc); val past = !dc.after(now); val hol = getHol(dc)
            if (sh[0] == "GUNDUZ") { tG++; if (past) pG++ } else if (sh[0] == "GECE") { tN++; if (past) pN++ } else { tI++; if (past) pI++ }

            val cell = FrameLayout(this).apply {
                var bC = sh[4]; var sW = 1
                if (k == kOf(now)) { bC = "#FFFFFF"; sW = 2 } else if (k == kOf(selCal)) { bC = "#38BDF8"; sW = 2 }
                val fill = when (sh[0]) { "GUNDUZ" -> "#172554"; "GECE" -> "#1E1B4B"; "ISTIRAHAT" -> "#052E16"; else -> "#1E293B" }
                background = bg(fill, 9, bC, sW)
                layoutParams = GridLayout.LayoutParams().apply { width = w - px(4); height = px(56); setMargins(px(2), px(2), px(2), px(2)) }
                setOnClickListener { selCal = dc; drawCal(); openMenu(dc) }
            }
            
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT) }
            box.addView(TextView(this).apply { text = "$d"; textSize = 13f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER })
            box.addView(TextView(this).apply { text = sh[2]; textSize = 8.5f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER })
            box.addView(TextView(this).apply { text = sh[3]; textSize = 9f; setTextColor(Color.parseColor("#CBD5E1")); gravity = Gravity.CENTER })
            cell.addView(box)
            
            if (hol != null) {
                cell.addView(View(this).apply { background = bg("#EF4444", 4); layoutParams = FrameLayout.LayoutParams(px(8), px(8)).apply { gravity = Gravity.TOP or Gravity.START; topMargin = px(4); leftMargin = px(4) } })
            }
            if (!prefs.getString("nt_$k", "").isNullOrBlank()) {
                cell.addView(View(this).apply { background = bg("#FDE047", 4); layoutParams = FrameLayout.LayoutParams(px(6), px(6)).apply { gravity = Gravity.TOP or Gravity.END; topMargin = px(4); rightMargin = px(4) } })
            }
            grid.addView(cell)
        }
        tvStat.text = "📅 Geçen / Toplam:\n☀ $pG/$tG Gündüz  •  ☾ $pN/$tN Gece  •  🛏 $pI/$tI İst."
    }

    private fun updHero() {
        val now = Calendar.getInstance(); val ts = shFor(now)
        tvHero.text = "${ts[3]} ${ts[1]}"
        for (i in 0..7) {
            val cc = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) }; val sh = shFor(cc)
            if (sh[0] == "GUNDUZ" || sh[0] == "GECE") {
                val st = (cc.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, if (sh[0] == "GUNDUZ") 8 else 20); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
                val en = (st.clone() as Calendar).apply { add(Calendar.HOUR_OF_DAY, 12) }
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

    private fun openMenu(c: Calendar) {
        val d = Dialog(this); d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        d.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        d.window?.setDimAmount(0.7f)
        
        val sRoot = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.BOTTOM; layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT); setOnClickListener { d.dismiss() } }
        val sheet = ScrollView(this).apply { background = bg("#0B1120", 24); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = px(60) } }
        val inr = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px(16), px(12), px(16), px(24)); setOnClickListener {} }
        
        inr.addView(View(this).apply { background = bg("#475569", 4); layoutParams = LinearLayout.LayoutParams(px(32), px(4)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = px(12) } })
        
        val k = kOf(c); val sh = shFor(c); val hol = getHol(c)
        if (hol != null) {
            inr.addView(TextView(this).apply { text = hol; setTextColor(Color.WHITE); background = bg("#EF4444", 12, "#7F1D1D"); setPadding(px(12), px(4), px(12), px(4)); gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = px(8) } })
        }
        
        inr.addView(TextView(this).apply { text = SimpleDateFormat("EEEE, d MMMM yyyy", tr).format(c.time); textSize = 13f; setTextColor(Color.parseColor("#94A3B8")); gravity = Gravity.CENTER })
        inr.addView(TextView(this).apply { text = sh[2]; textSize = 26f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); setPadding(0, px(4), 0, px(16)) })
        
        val tBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = bg("#1E293B", 12); setPadding(px(16), px(12), px(16), px(12)) }
        tBar.addView(TextView(this).apply { text = "🕒  ${sh[5]}"; textSize = 14f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        tBar.addView(TextView(this).apply { text = "Varsayılan"; textSize = 11f; setTextColor(Color.parseColor("#94A3B8")); background = bg("#0F172A", 8); setPadding(px(8), px(4), px(8), px(4)) })
        inr.addView(tBar)

        fun mkLbl(t: String): TextView = TextView(this).apply { text = t.uppercase(tr); textSize = 10f; setTextColor(Color.parseColor("#64748B")); setTypeface(null, Typeface.BOLD); setPadding(0, px(20), 0, px(8)) }
        fun setOv(code: String) { prefs.edit().putString("ov_$k", code).apply(); d.dismiss(); drawCal(); updHero(); openMenu(c) }

        inr.addView(mkLbl("Vardiya Durumunu Değiştir"))
        val r1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (i in 0..2) {
            r1.addView(Button(this).apply { text = "${shifts[i][3]} ${shifts[i][1]}"; textSize = 11f; setTextColor(Color.WHITE); background = bg(shifts[i][4], 12); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = px(4) }; setPadding(px(4), 0, px(4), 0); setOnClickListener { setOv(shifts[i][0]) } })
        }
        inr.addView(r1
