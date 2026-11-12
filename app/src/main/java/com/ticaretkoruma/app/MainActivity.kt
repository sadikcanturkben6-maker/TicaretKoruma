package com.ticaretkoruma.app

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    data class ShiftPreset(
        val id: String,
        val title: String,
        val desc: String,
        val pattern: List<String>
    )

    private val shiftPresets = listOf(
        ShiftPreset("12_24_48", "12/24 - 12/48 Sistemi", "1 Gündüz • 1 Gece • 2 Gün İstirahat", listOf("GUNDUZ", "GECE", "IST", "IST")),
        ShiftPreset("12_36_ORJ", "Orijinal 12/36", "12 Saat Nöbet • 36 Saat İstirahat", listOf("GUNDUZ", "IST", "GECE", "IST")),
        ShiftPreset("12_36_3_3", "12/36 [3+3 Dönerli]", "3 Gündüz • 2 İst. • 3 Gece • 1 İst.", listOf("GUNDUZ", "GUNDUZ", "GUNDUZ", "IST", "IST", "GECE", "GECE", "GECE", "IST")),
        ShiftPreset("12_36_5", "12/36 [5 Gün Dönerli]", "5 Gündüz • 2 İst. • 5 Gece • 1 İst.", List(5) { "GUNDUZ" } + List(2) { "IST" } + List(5) { "GECE" } + listOf("IST")),
        ShiftPreset("12_36_7", "12/36 [7 Gün Dönerli]", "7 Gündüz • 2 İst. • 7 Gece • 1 İst.", List(7) { "GUNDUZ" } + List(2) { "IST" } + List(7) { "GECE" } + listOf("IST")),
        ShiftPreset("12_36_7_24", "12/36 [7 Gün 24/24]", "7 Gündüz • 1 İst. • 7 Gece • 1 İst.", List(7) { "GUNDUZ" } + listOf("IST") + List(7) { "GECE" } + listOf("IST")),
        ShiftPreset("12_36_7_48", "12/36 [7 Gün 48/48]", "7 Gündüz • 2 İst. • 7 Gece • 2 İst.", List(7) { "GUNDUZ" } + List(2) { "IST" } + List(7) { "GECE" } + List(2) { "IST" }),
        ShiftPreset("12_36_10", "12/36 [10 Gün Dönerli]", "10 Gündüz • 2 İst. • 10 Gece • 1 İst.", List(10) { "GUNDUZ" } + List(2) { "IST" } + List(10) { "GECE" } + listOf("IST")),
        ShiftPreset("CAKMA_3", "Çakma 12/36 (3 Gün)", "3 Gündüz + 1 Gece + 2 İstirahat", listOf("GUNDUZ", "GUNDUZ", "GUNDUZ", "GECE", "IST", "IST")),
        ShiftPreset("CAKMA_5", "Çakma 12/36 (5 Gün)", "5 Gündüz + 1 Gece + 2 İstirahat", List(5) { "GUNDUZ" } + listOf("GECE", "IST", "IST")),
        ShiftPreset("24_48", "24/48 Sistemi", "24 Saat Nöbet • 48 Saat İstirahat", listOf("NOBET24", "IST", "IST")),
        ShiftPreset("8_24", "8/24 (Sabah-Akşam-Gece)", "08-16 Sabah • 16-00 Akşam • 00-08 Gece • İst.", listOf("GUNDUZ", "AKSAM", "GECE", "IST")),
        ShiftPreset("3_VARDIYA_8", "3 Vardiya 8 Saat", "2 Sabah • 2 Akşam • 2 İst. • 2 Gece", listOf("GUNDUZ", "GUNDUZ", "AKSAM", "AKSAM", "IST", "IST", "GECE", "GECE")),
        ShiftPreset("1_1", "1+1 Sistemi", "1 Gün Çalış • 1 Gün İstirahat", listOf("GUNDUZ", "IST")),
        ShiftPreset("2_1", "2+1 Sistemi", "2 Gün Çalış • 1 Gün İstirahat", listOf("GUNDUZ", "GUNDUZ", "IST")),
        ShiftPreset("BURO", "Büro / Hafta İçi", "Pazartesi - Cuma Gündüz • Hafta Sonu İst.", emptyList()),
        ShiftPreset("OZEL", "Özel / Manuel", "Kendi döngünü oluştur", emptyList())
    )

    private val prefs by lazy { getSharedPreferences("TicaretKorumaPrefs", Context.MODE_PRIVATE) }
    private lateinit var contentContainer: FrameLayout
    private lateinit var topAppBarTitle: TextView
    private var currentTab = 0
    private var displayedCal: Calendar = Calendar.getInstance()
    private val timerHandler = Handler(Looper.getMainLooper())

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (currentTab == 0) renderTab()
            timerHandler.postDelayed(this, 60_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!prefs.contains("anchor_millis")) {
            val today = zeroTime(Calendar.getInstance())
            prefs.edit().putLong("anchor_millis", today.timeInMillis).apply()
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B1120"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Üst Program Başlığı (Dokunarak Değiştirilebilir)
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#111827"))
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setOnClickListener { showEditAppNameDialog() }
        }
        topAppBarTitle = TextView(this).apply {
            text = "🛡️ ${getAppName()}"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val editBadge = TextView(this).apply {
            text = "✏️ Adı Değiştir"
            textSize = 11f
            setTextColor(Color.parseColor("#93C5FD"))
            background = roundedBg("#1E293B", 8f)
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        topBar.addView(topAppBarTitle)
        topBar.addView(editBadge)

        contentContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val bottomNav = buildBottomNav()
        root.addView(topBar)
        root.addView(contentContainer)
        root.addView(bottomNav)
        setContentView(root)
        renderTab()
    }

    override fun onResume() {
        super.onResume()
        timerHandler.post(timerRunnable)
    }

    override fun onPause() {
        super.onPause()
        timerHandler.removeCallbacks(timerRunnable)
    }

    private fun getAppName(): String =
        prefs.getString("app_name", "Ticaret Koruma") ?: "Ticaret Koruma"

    private fun getActiveSystemId(): String =
        prefs.getString("shift_system", "12_24_48") ?: "12_24_48"

    private fun getActiveSystemTitle(): String =
        shiftPresets.find { it.id == getActiveSystemId() }?.title ?: "12/24 - 12/48 Sistemi"

    private fun buildBottomNav(): LinearLayout {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#111827"))
            setPadding(dp(8), dp(10), dp(8), dp(12))
        }
        val items = listOf("📅 Takvim", "📊 Sayaçlar", "⚙️ Ayarlar", "ℹ️ Bilgi")
        items.forEachIndexed { index, title ->
            val btn = TextView(this).apply {
                text = title
                textSize = 13f
                gravity = Gravity.CENTER
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                setPadding(dp(4), dp(8), dp(4), dp(8))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    currentTab = index
                    renderTab()
                }
            }
            nav.addView(btn)
        }
        return nav
    }

    private fun renderTab() {
        topAppBarTitle.text = "🛡️ ${getAppName()}"
        contentContainer.removeAllViews()
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        when (currentTab) {
            0 -> buildCalendarTab(body)
            1 -> buildCountersTab(body)
            2 -> buildSettingsTab(body)
            3 -> buildInfoTab(body)
        }
        scroll.addView(body)
        contentContainer.addView(scroll)
    }

    // 1. TAKVİM SEKMESİ
    private fun buildCalendarTab(body: LinearLayout) {
        val greeting = prefs.getString("greeting", "Hoşgeldin Devrem 👋") ?: "Hoşgeldin Devrem 👋"
        val titleTv = TextView(this).apply {
            text = greeting
            textSize = 21f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setOnClickListener { showEditGreetingDialog() }
        }
        body.addView(titleTv)

        val dateFormat = SimpleDateFormat("EEEE, d MMMM yyyy", Locale("tr", "TR"))
        val dateTv = TextView(this).apply {
            text = "${dateFormat.format(Date()).replaceFirstChar { it.uppercase() }} • ${getActiveSystemTitle()}"
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, dp(2), 0, dp(12))
        }
        body.addView(dateTv)

        val now = Calendar.getInstance()
        val todayShift = getShiftForDate(now)
        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(shiftColor(todayShift), 18f)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        val statusLabel = TextView(this).apply {
            text = "BUGÜN: ${shiftTitle(todayShift)}"
            textSize = 19f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        val countdownTv = TextView(this).apply {
            text = computeNextShiftCountdown(now)
            textSize = 14f
            setTextColor(Color.parseColor("#E2E8F0"))
            setPadding(0, dp(6), 0, 0)
        }
        statusCard.addView(statusLabel)
        statusCard.addView(countdownTv)
        body.addView(statusCard)

        // 3 Günlük Önizleme
        val previewRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, dp(12))
        }
        val labels = listOf("Yarın", "Öbür Gün", "3. Gün")
        for (i in 1..3) {
            val future = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, i) }
            val s = getShiftForDate(future)
            val mini = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = roundedBg("#151F32", 12f, shiftColor(s))
                setPadding(dp(10), dp(10), dp(10), dp(10))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (i < 3) marginEnd = dp(8)
                }
            }
            mini.addView(TextView(this).apply {
                text = labels[i - 1]
                textSize = 11f
                setTextColor(Color.parseColor("#94A3B8"))
            })
            mini.addView(TextView(this).apply {
                text = shiftShort(s)
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                setPadding(0, dp(2), 0, 0)
            })
            previewRow.addView(mini)
        }
        body.addView(previewRow)

        // Ay Seçici
        val monthCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg("#151F32", 16f)
            setPadding(dp(12), dp(14), dp(12), dp(14))
        }
        val monthHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(10))
        }
        val prevBtn = TextView(this).apply {
            text = "◀"
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener {
                displayedCal.add(Calendar.MONTH, -1)
                renderTab()
            }
        }
        val monthTitleFormat = SimpleDateFormat("MMMM yyyy", Locale("tr", "TR"))
        val monthTitle = TextView(this).apply {
            text = monthTitleFormat.format(displayedCal.time).uppercase(Locale("tr", "TR"))
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val nextBtn = TextView(this).apply {
            text = "▶"
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener {
                displayedCal.add(Calendar.MONTH, 1)
                renderTab()
            }
        }
        monthHeader.addView(prevBtn)
        monthHeader.addView(monthTitle)
        monthHeader.addView(nextBtn)
        monthCard.addView(monthHeader)

        // Haftanın Günleri
        val weekRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz").forEach { d ->
            weekRow.addView(TextView(this).apply {
                text = d
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#94A3B8"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
        }
        monthCard.addView(weekRow)

        // Takvim Hücreleri
        val tempCal = (displayedCal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
        val firstDayOfWeek = (tempCal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val maxDays = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH)

        var dayCounter = 1
        var workCount = 0
        var geceCount = 0
        var istCount = 0

        for (row in 0 until 6) {
            if (dayCounter > maxDays) break
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(6), 0, 0)
            }
            for (col in 0 until 7) {
                val cellIndex = row * 7 + col
                if (cellIndex < firstDayOfWeek || dayCounter > maxDays) {
                    rowLayout.addView(View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(0, dp(52), 1f)
                    })
                } else {
                    val currentDay = dayCounter
                    val cellCal = (displayedCal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, currentDay) }
                    val shift = getShiftForDate(cellCal)
                    when (shift) {
                        "GUNDUZ", "AKSAM", "NOBET24" -> workCount++
                        "GECE" -> geceCount++
                        "IST" -> istCount++
                    }
                    val cell = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER
                        background = roundedBg(shiftColor(shift), 10f)
                        setPadding(dp(2), dp(6), dp(2), dp(6))
                        layoutParams = LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                            marginStart = dp(2)
                            marginEnd = dp(2)
                        }
                        setOnClickListener { showDayOverrideDialog(cellCal) }
                    }
                    cell.addView(TextView(this).apply {
                        text = currentDay.toString()
                        textSize = 14f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.WHITE)
                        gravity = Gravity.CENTER
                    })
                    cell.addView(TextView(this).apply {
                        text = shiftMini(shift)
                        textSize = 9f
                        setTextColor(Color.WHITE)
                        gravity = Gravity.CENTER
                    })
                    rowLayout.addView(cell)
                    dayCounter++
                }
            }
            monthCard.addView(rowLayout)
        }
        body.addView(monthCard)

        // Ay Özeti
        val summaryCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = roundedBg("#151F32", 14f)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        }
        summaryCard.addView(TextView(this).apply {
            text = "Bu Ay:  ☀️ $workCount Gündüz/Nöbet   •   🌙 $geceCount Gece   •   🛌 $istCount İst."
            textSize = 13f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        body.addView(summaryCard)
    }

    // 2. SAYAÇLAR (ÇETELE) SEKMESİ
    private fun buildCountersTab(body: LinearLayout) {
        body.addView(TextView(this).apply {
            text = "Çetele & Sayaçlar"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(14))
        })

        val counterItems = listOf(
            Triple("kalan_izin", "🏖️ Kalan Yıllık İzin (Gün)", 20),
            Triple("mac_gorev", "⚽ Ek Görev: MAÇ", 0),
            Triple("sinav_gorev", "🎓 Ek Görev: SINAV", 0),
            Triple("taltif_sayisi", "⭐ Taltif Sayısı", 0),
            Triple("rapor_gun", "🏥 Rapor / Mazeret Günü", 0)
        )

        counterItems.forEach { (key, label, defaultVal) ->
            val currentVal = prefs.getInt(key, defaultVal)
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = roundedBg("#151F32", 14f)
                setPadding(dp(16), dp(14), dp(16), dp(14))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(10) }
            }
            val nameTv = TextView(this).apply {
                text = label
                textSize = 15f
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val minusBtn = Button(this).apply {
                text = "-"
                setTextColor(Color.WHITE)
                background = roundedBg("#1E293B", 10f)
                layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
                setOnClickListener {
                    if (currentVal > 0) {
                        prefs.edit().putInt(key, currentVal - 1).apply()
                        renderTab()
                    }
                }
            }
            val valTv = TextView(this).apply {
                text = currentVal.toString()
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#38BDF8"))
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dp(48), LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            val plusBtn = Button(this).apply {
                text = "+"
                setTextColor(Color.WHITE)
                backgroun
