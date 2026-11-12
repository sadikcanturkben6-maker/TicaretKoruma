package com.ticaretkoruma.app

import android.app.*
import android.content.*
import android.graphics.*
import android.graphics.drawable.*
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
        "2026-03-20" to "Arife (Ramazan)", "2026-03-21" to "Ramazan Bayramı", "2026-05-26" to "Arife (Kurban)",
        "2027-03-09" to "Arife (Ramazan)", "2027-03-10" to "Ramazan Bayramı", "2027-05-16" to "Arife (Kurban)"
    )

    private fun px(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun bg(hex: String, r: Int, st: String? = null, stW: Int = 1) = GradientDrawable().apply {
        cornerRadius = px(r).toFloat(); setColor(Color.parseColor(hex))
        if (st != null) setStroke(px(stW), Color.parseColor(st))
    }
    private fun kOf(c: Calendar) = String.format(Locale.US, "%04d-%02d-%02d", c.get(1), c.get(2) + 1, c.get(5))

    private val ticker = object : Runnable { override fun run() { updHero(); handler.postDelayed(this, 1000L) } }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        supportActionBar?.hide()
        val mLay = LinearLayout(this).apply { orientation = 1; setBackgroundColor(Color.parseColor("#090E1A")) }
        val scr = ScrollView(this).apply { isFillViewport = true; layoutParams = LinearLayout.LayoutParams(-1, 0, 1f) }
        val root = LinearLayout(this).apply { orientation = 1; setPadding(px(14), px(16), px(14), px(20)) }

        val top = LinearLayout(this).apply { orientation = 0; gravity = 16 }
        val tCol = LinearLayout(this).apply { orientation = 1; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        tvGreet = TextView(this).apply { text = prefs.getString("greet", "Hoşgeldin Devrem 👋"); textSize = 20f; setTextColor(-1); setTypeface(null, 1) }
        tCol.addView(tvGreet)
        tCol.addView(TextView(this).apply { text = SimpleDateFormat("EEEE, d MMMM", tr).format(Date()); textSize = 12f; setTextColor(Color.parseColor("#94A3B8")) })
        top.addView(tCol)
        
        top.addView(Button(this).apply { text = "🔄"; textSize = 14f; setTextColor(-1); background = bg("#1E293B", 10); layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = px(6) }; setPadding(px(10), px(6), px(10), px(6)); setOnClickListener { loadW(); drawCal(); updHero(); Toast.makeText(context, "Yenilendi", 0).show() } })
        top.addView(Button(this).apply { text = "Döngü Kur"; textSize = 12f; setTextColor(-1); background = bg("#1E293B", 10); setPadding(px(12), px(6), px(12), px(6)); setOnClickListener { setCyc() } })
        root.addView(top)

        val hero = LinearLayout(this).apply { orientation = 1; background = bg("#1E3A8A", 16, "#3B82F6"); setPadding(px(16), px(14), px(16), px(14)); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(12) } }
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

        val calBox = LinearLayout(this).apply { orientation = 1; background = bg("#111827", 16, "#1F2937"); setPadding(px(10), px(12), px(10), px(12)); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(12) } }
        val nav = LinearLayout(this).apply { orientation = 0; gravity = 16; setPadding(0, 0, 0, px(8)) }
        nav.addView(Button(this).apply { text = "◀"; setTextColor(-1); background = bg("#1E293B", 8); setOnClickListener { monthCal.add(2, -1); drawCal() } })
        tvMonth = TextView(this).apply { textSize = 16f; setTextColor(-1); setTypeface(null, 1); gravity = 17; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        nav.addView(tvMonth)
        nav.addView(Button(this).apply { text = "▶"; setTextColor(-1); background = bg("#1E293B", 8); setOnClickListener { monthCal.add(2, 1); drawCal() } })
        calBox.addView(nav)

        val wk = LinearLayout(this).apply { orientation = 0; setPadding(0, 0, 0, px(6)) }
        for (d in listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")) wk.addView(TextView(this).apply { text = d; textSize = 11f; setTextColor(Color.parseColor("#94A3B8")); gravity = 17; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        calBox.addView(wk)
        grid = GridLayout(this).apply { columnCount = 7; rowCount = 6 }
        calBox.addView(grid); root.addView(calBox)

        tvStat = TextView(this).apply { textSize = 12f; setTextColor(-1); setTypeface(null, 1); gravity = 17; background = bg("#111827", 14, "#1F2937"); setPadding(px(12), px(12), px(12), px(12)); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(10) } }
        root.addView(tvStat)
        scr.addView(root); mLay.addView(scr)

        val bNav = LinearLayout(this).apply { orientation = 0; background = bg("#0F172A", 0); setPadding(0, px(8), 0, px(8)); layoutParams = LinearLayout.LayoutParams(-1, -2) }
        fun nBtn(ic: String, t: String, a: () -> Unit) = LinearLayout(this).apply { orientation = 1; gravity = 17; layoutParams = LinearLayout.LayoutParams(0, -2, 1f); addView(TextView(this@MainActivity).apply { text = ic; textSize = 20f; gravity = 17 }); addView(TextView(this@MainActivity).apply { text = t; textSize = 10f; setTextColor(Color.GRAY); gravity = 17 }); setOnClickListener { a() } }
        bNav.addView(nBtn("📅", "Takvim") {})
        bNav.addView(nBtn("📈", "Sayaçlar") {})
        bNav.addView(nBtn("⚙️", "Ayarlar") { openSet() })
        bNav.addView(nBtn("💬", "İletişim") {})
        mLay.addView(bNav)

        setContentView(mLay); drawCal(); updHero(); loadW()
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

    private fun getHol(c: Calendar) = hols[kOf(c)] ?: hols[String.format(Locale.US, "%02d-%02d", c.get(2) + 1, c.get(5))]

    private fun drawCal() {
        tvMonth.text = SimpleDateFormat("MMMM yyyy", tr).format(monthCal.time).uppercase(tr)
        grid.removeAllViews()
        val c = (monthCal.clone() as Calendar).apply { set(5, 1) }
        val off = (c.get(7) + 5) % 7
        val max = c.getActualMaximum(5)
        val now = Calendar.getInstance(); val w = (resources.displayMetrics.widthPixels - px(48)) / 7
        var tG = 0; var tN = 0; var tI = 0; var pG = 0; var pN = 0; var pI = 0

        for (i in 0 until off) grid.addView(View(this).apply { layoutParams = GridLayout.LayoutParams().apply { width = w; height = px(56) } })
        for (d in 1..max) {
            val dc = (monthCal.clone() as Calendar).apply { set(5, d) }
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
            
            val box = LinearLayout(this).apply { orientation = 1; gravity = 17; layoutParams = FrameLayout.LayoutParams(-1, -1) }
            box.addView(TextView(this).apply { text = "$d"; textSize = 13f; setTextColor(-1); setTypeface(null, 1); gravity = 17 })
            box.addView(TextView(this).apply { text = sh[2]; textSize = 8.5f; setTextColor(-1); setTypeface(null, 1); gravity = 17 })
            box.addView(TextView(this).apply { text = sh[3]; textSize = 9f; setTextColor(Color.parseColor("#CBD5E1")); gravity = 17 })
            cell.addView(box)
            
            if (hol != null) cell.addView(View(this).apply { background = bg("#EF4444", 4); layoutParams = FrameLayout.LayoutParams(px(8), px(8)).apply { gravity = 51; topMargin = px(4); leftMargin = px(4) } })
            if (!prefs.getString("nt_$k", "").isNullOrBlank()) cell.addView(View(this).apply { background = bg("#FDE047", 4); layoutParams = FrameLayout.LayoutParams(px(6), px(6)).apply { gravity = 53; topMargin = px(4); rightMargin = px(4) } })
            
            grid.addView(cell)
        }
        tvStat.text = "📅 Geçen / Toplam:\n☀ $pG/$tG Gündüz  •  ☾ $pN/$tN Gece  •  🛏 $pI/$tI İst."
    }

    private fun updHero() {
        val now = Calendar.getInstance(); val ts = shFor(now)
        tvHero.text = "${ts[3]} ${ts[1]}"
        for (i in 0..7) {
            val cc = (now.clone() as Calendar).apply { add(6, i) }; val sh = shFor(cc)
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

    private fun openMenu(c: Calendar) {
        val d = Dialog(this); d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        d.window?.setLayout(-1, -1); d.window?.setDimAmount(0.7f)
        
        val sRoot = LinearLayout(this).apply { orientation = 1; gravity = 80; layoutParams = ViewGroup.LayoutParams(-1, -1); setOnClickListener { d.dismiss() } }
        val sheet = ScrollView(this).apply { background = bg("#0B1120", 24); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(60) } }
        val inr = LinearLayout(this).apply { orientation = 1; setPadding(px(16), px(12), px(16), px(24)); setOnClickListener {} }
        
        inr.addView(View(this).apply { background = bg("#475569", 4); layoutParams = LinearLayout.LayoutParams(px(32), px(4)).apply { gravity = 1; bottomMargin = px(12) } })
        
        val k = kOf(c); val sh = shFor(c); val hol = getHol(c)
        if (hol != null) inr.addView(TextView(this).apply { text = hol; setTextColor(-1); background = bg("#EF4444", 12, "#7F1D1D"); setPadding(px(12), px(4), px(12), px(4)); gravity = 17; layoutParams = LinearLayout.LayoutParams(-2, -2).apply { gravity = 1; bottomMargin = px(8) } })
        
        inr.addView(TextView(this).apply { text = SimpleDateFormat("EEEE, d MMMM yyyy", tr).format(c.time); textSize = 13f; setTextColor(Color.parseColor("#94A3B8")); gravity = 17 })
        inr.addView(TextView(this).apply { text = sh[2]; textSize = 26f; setTextColor(-1); setTypeface(null, 1); gravity = 17; setPadding(0, px(4), 0, px(16)) })
        
        val tBar = LinearLayout(this).apply { orientation = 0; gravity = 16; background = bg("#1E293B", 12); setPadding(px(16), px(12), px(16), px(12)) }
        tBar.addView(TextView(this).apply { text = "🕒  ${sh[5]}"; textSize = 14f; setTextColor(-1); setTypeface(null, 1); layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        tBar.addView(TextView(this).apply { text = "Varsayılan"; textSize = 11f; setTextColor(Color.parseColor("#94A3B8")); background = bg("#0F172A", 8); setPadding(px(8), px(4), px(8), px(4)) })
        inr.addView(tBar)

        fun mkLbl(t: String) = TextView(this).apply { text = t.uppercase(tr); textSize = 10f; setTextColor(Color.parseColor("#64748B")); setTypeface(null, 1); setPadding(0, px(20), 0, px(8)) }
        fun setOv(code: String) { prefs.edit().putString("ov_$k", code).apply(); d.dismiss(); drawCal(); updHero(); openMenu(c) }

        inr.addView(mkLbl("Vardiya Durumunu Değiştir"))
        val r1 = LinearLayout(this).apply { orientation = 0 }
        for (i in 0..2) r1.addView(Button(this).apply { text = "${shifts[i][3]} ${shifts[i][1]}"; textSize = 11f; setTextColor(-1); background = bg(shifts[i][4], 12); layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = px(4) }; setPadding(px(4), 0, px(4), 0); setOnClickListener { setOv(shifts[i][0]) } })
        inr.addView(r1)

        inr.addView(mkLbl("İzin ve Görev Türleri"))
        val scrLv = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val r2 = LinearLayout(this).apply { orientation = 0 }
        for (i in 3..6) r2.addView(Button(this).apply { text = shifts[i][1]; textSize = 11f; setTextColor(-1); background = bg(shifts[i][4], 12); layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = px(6) }; setPadding(px(16), 0, px(16), 0); setOnClickListener { setOv(shifts[i][0]) } })
        scrLv.addView(r2); inr.addView(scrLv)

        inr.addView(mkLbl("Ekip"))
        inr.addView(TextView(this).apply { text = "Bu nöbete kişi eklenmedi."; textSize = 13f; setTextColor(Color.GRAY); background = bg("#0F172A", 12, "#1E293B"); setPadding(px(16), px(16), px(16), px(16)) })
        
        inr.addView(mkLbl("Araç & KM"))
        inr.addView(EditText(this).apply { hint = "Plaka / Araç"; setTextColor(-1); setHintTextColor(Color.GRAY); background = bg("#0F172A", 12, "#1E293B"); setPadding(px(16), px(16), px(16), px(16)); textSize = 13f })

        inr.addView(mkLbl("Ek Görevler ve Notlar"))
        val edN = EditText(this).apply { hint = "Bu nöbete özel not yaz..."; setTextColor(-1); setHintTextColor(Color.GRAY); background = bg("#0F172A", 12, "#1E293B"); setPadding(px(16), px(16), px(16), px(16)); textSize = 13f; setText(prefs.getString("nt_$k", "")) }
        inr.addView(edN)

        val rBtn = LinearLayout(this).apply { orientation = 0; setPadding(0, px(20), 0, 0) }
        rBtn.addView(Button(this).apply { text = "Sıfırla"; setTextColor(-1); background = bg("#334155", 12); layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = px(6) }; setOnClickListener { prefs.edit().remove("ov_$k").remove("nt_$k").apply(); d.dismiss(); drawCal(); updHero() } })
        rBtn.addView(Button(this).apply { text = "Kaydet"; setTextColor(-1); background = bg("#059669", 12); layoutParams = LinearLayout.LayoutParams(0, -2, 1f); setOnClickListener { prefs.edit().putString("nt_$k", edN.text.toString().trim()).apply(); d.dismiss(); drawCal() } })
        inr.addView(rBtn)

        sheet.addView(inr); sRoot.addView(sheet); d.setContentView(sRoot); d.show()
    }

    private fun setCyc() {
        val opts = arrayOf("1. Gün: 12s Gündüz", "2. Gün: 12s Gece", "3. Gün: Grv. İstirahati", "4. Gün: Grv. İstirahati")
        AlertDialog.Builder(this).setTitle("12/24 - 12/48 Döngü").setItems(opts) { _, s ->
            val c = Calendar.getInstance()
            DatePickerDialog(this, { _, y, m, d ->
                val ch = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0) }
                prefs.edit().putLong("anc", ch.timeInMillis).putInt("st", s).apply(); drawCal(); updHero()
            }, c.get(1), c.get(2), c.get(5)).show()
        }.show()
    }

    private fun openSet() {
        val ed = EditText(this).apply { setText(prefs.getString("greet", "Hoşgeldin Devrem 👋")) }
        AlertDialog.Builder(this).setTitle("Karşılama Mesajı").setView(ed).setPositiveButton("Kaydet") { _, _ -> prefs.edit().putString("greet", ed.text.toString()).apply(); tvGreet.text = ed.text.toString() }.show()
    }

    private fun loadW() {
        Thread {
            try {
                val url = URL("https://api.open-meteo.com/v1/forecast?latitude=39.95&longitude=32.68&current_weather=true")
                val con = url.openConnection() as HttpURLConnection
                con.connectTimeout = 4000
                con.readTimeout = 4000
                con.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
                val text = con.inputStream.bufferedReader().readText()
                val t = Regex("\"temperature\":([-0-9.]+)").find(text)?.groupValues?.get(1)?.substringBefore(".") ?: "--"
                runOnUiThread { tvLoc.text = "📍 Etimesgut • GÜNCEL"; tvTemp.text = "☁ ${t}°" }
            } catch (e: Exception) {
                runOnUiThread { tvLoc.text = "📍 Etimesgut • ÇEVRİMDIŞI" }
            }
        }.start()
    }
}
