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

data class ShiftPreset(
    val id: String,
    val title: String,
    val shortCode: String,
    val colorHex: String,
    val isWork: Boolean,
    val defaultHour: Int,
    val defaultMinute: Int
)

class MainActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("TicaretKorumaPrefs", Context.MODE_PRIVATE) }
    private val trLocale = Locale("tr", "TR")
    private val handler = Handler(Looper.getMainLooper())

    private var currentMonthCal: Calendar = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
    private var selectedDateCal: Calendar = Calendar.getInstance()

    private lateinit var tvAppName: TextView
    private lateinit var tvCountdownTitle: TextView
    private lateinit var tvCountdownTimer: TextView
    private lateinit var tvCountdownSub: TextView
    private lateinit var tvWeatherBadge: TextView
    private lateinit var tvMonthHeader: TextView
    private lateinit var gridCalendar: GridLayout
    private lateinit var tvSelectedDateTitle: TextView
    private lateinit var tvSelectedShiftBadge: TextView
    private lateinit var tvSelectedNote: TextView
    private lateinit var btnAlarmSet: Button
    private lateinit var btnCalendarSync: Button

    private val presets = listOf(
        ShiftPreset("GUNDUZ", "Gündüz Vardiyası (12s)", "GÜNDÜZ", "#F59E0B", true, 6, 30),
        ShiftPreset("GECE", "Gece Vardiyası (12s)", "GECE", "#3B82F6", true, 18, 30),
        ShiftPreset("ISTIRAHAT", "İstirahat / Tatil", "TATİL", "#10B981", false, 9, 0),
        ShiftPreset("YILLIK", "Yıllık İzin", "İZİN", "#8B5CF6", false, 9, 0),
        ShiftPreset("RAPOR", "Sağlık Raporu", "RAPOR", "#EF4444", false, 9, 0),
        ShiftPreset("RESMI", "Resmi Tatil / İdari", "RESMİ", "#06B6D4", false, 9, 0)
    )

    private val countdownRunnable = object : Runnable {
        override fun run() {
            updateLiveCountdown()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        buildUI()
        refreshAll()
        fetchAnkaraWeather()
    }

    override fun onResume() {
        super.onResume()
        handler.post(countdownRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(countdownRunnable)
    }

    private fun buildUI() {
        val rootScroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B1120"))
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(28))
        }

        // Üst Başlık ve Ayarlar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        tvAppName = TextView(this).apply {
            text = prefs.getString("app_title", "Ticaret Koruma")
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { showEditAppNameDialog() }
        }

        val btnCycleSetup = Button(this).apply {
            text = "Döngü Kur"
            textSize = 12f
            setTextColor(Color.WHITE)
            background = roundedBg("#1E293B", 10)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener { showCycleSetupDialog() }
        }

        val btnGreetingEdit = Button(this).apply {
            text = "Karşılama"
            textSize = 12f
            setTextColor(Color.parseColor("#93C5FD"))
            background = roundedBg("#1E293B", 10)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.marginStart = dp(8)
            layoutParams = lp
            setOnClickListener { showEditGreetingDialog() }
        }

        topBar.addView(tvAppName)
        topBar.addView(btnCycleSetup)
        topBar.addView(btnGreetingEdit)
        root.addView(topBar)

        // Hava Durumu Satırı
        tvWeatherBadge = TextView(this).apply {
            text = "Ankara hava durumu yükleniyor..."
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, dp(4), 0, dp(14))
        }
        root.addView(tvWeatherBadge)

        // Canlı Geri Sayım Kartı
        val countdownCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg("#131C31", 16, "#1E293B")
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        tvCountdownTitle = TextView(this).apply {
            text = "SONRAKİ NÖBETE KALAN SÜRE"
            textSize = 11f
            setTextColor(Color.parseColor("#38BDF8"))
            setTypeface(null, Typeface.BOLD)
        }

        tvCountdownTimer = TextView(this).apply {
            text = "--:--:--"
            textSize = 30f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(4), 0, dp(2))
        }

        tvCountdownSub = TextView(this).apply {
            text = "Döngü başlangıcı için sağ üstten 'Döngü Kur' seçin"
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
        }

        countdownCard.addView(tvCountdownTitle)
        countdownCard.addView(tvCountdownTimer)
        countdownCard.addView(tvCountdownSub)
        root.addView(countdownCard)

        // Ay Geçiş Çubuğu
        val monthNav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(18), 0, dp(10))
        }

        val btnPrevMonth = Button(this).apply {
            text = "◀"
            setTextColor(Color.WHITE)
            background = roundedBg("#1E293B", 10)
            setOnClickListener {
                currentMonthCal.add(Calendar.MONTH, -1)
                renderCalendar()
            }
        }

        tvMonthHeader = TextView(this).apply {
            textSize = 17f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnNextMonth = Button(this).apply {
            text = "▶"
            setTextColor(Color.WHITE)
            background = roundedBg("#1E293B", 10)
            setOnClickListener {
                currentMonthCal.add(Calendar.MONTH, 1)
                renderCalendar()
            }
        }

        monthNav.addView(btnPrevMonth)
        monthNav.addView(tvMonthHeader)
        monthNav.addView(btnNextMonth)
        root.addView(monthNav)

        // Haftanın Günleri Başlığı
        val weekHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(6))
        }
        val daysOfWeek = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")
        for (d in daysOfWeek) {
            val tv = TextView(this).apply {
                text = d
                textSize = 12f
                setTextColor(Color.parseColor("#94A3B8"))
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            weekHeader.addView(tv)
        }
        root.addView(weekHeader)

        // Takvim Izgarası
        gridCalendar = GridLayout(this).apply {
            columnCount = 7
            rowCount = 6
        }
        root.addView(gridCalendar)

        // Seçili Gün Detay Kartı
        val detailCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg("#131C31", 16, "#1E293B")
            setPadding(dp(16), dp(16), dp(16), dp(16))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(16)
            layoutParams = lp
        }

        val detailTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        tvSelectedDateTitle = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        tvSelectedShiftBadge = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(10), dp(4), dp(10), dp(4))
        }

        detailTop.addView(tvSelectedDateTitle)
        detailTop.addView(tvSelectedShiftBadge)
        detailCard.addView(detailTop)

        tvSelectedNote = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(0, dp(8), 0, dp(12))
        }
        detailCard.addView(tvSelectedNote)

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val btnChangeShift = Button(this).apply {
            text = "Düzenle / Not"
            textSize = 12f
            setTextColor(Color.WHITE)
            background = roundedBg("#2563EB", 10)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
            setOnClickListener { showDayOverrideDialog() }
        }

        btnAlarmSet = Button(this).apply {
            text = "Alarm Kur"
            textSize = 12f
            setTextColor(Color.WHITE)
            background = roundedBg("#059669", 10)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
            setOnClickListener { openAlarmSetup() }
        }

        btnCalendarSync = Button(this).apply {
            text = "Takvime At"
            textSize = 12f
            setTextColor(Color.WHITE)
            background = roundedBg("#475569", 10)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { exportToPhoneCalendar() }
        }

        actionRow.addView(btnChangeShift)
        actionRow.addView(btnAlarmSet)
        actionRow.addView(btnCalendarSync)
        detailCard.addView(actionRow)

        root.addView(detailCard)
        rootScroll.addView(root)
        setContentView(rootScroll)
    }

    private fun refreshAll() {
        renderCalendar()
        updateSelectedDayPanel()
        updateLiveCountdown()
    }

    private fun renderCalendar() {
        val monthFormat = SimpleDateFormat("MMMM yyyy", trLocale)
        tvMonthHeader.text = monthFormat.format(currentMonthCal.time).replaceFirstChar { it.titlecase(trLocale) }

        gridCalendar.removeAllViews()

        val cal = currentMonthCal.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val firstDayOfWeek = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val todayKey = dateKey(Calendar.getInstance())
        val selectedKey = dateKey(selectedDateCal)
        val screenWidth = resources.displayMetrics.widthPixels - dp(32)
        val cellWidth = screenWidth / 7

        var cellIndex = 0
        for (i in 0 until firstDayOfWeek) {
            val emptyView = View(this).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = cellWidth
                    height = dp(52)
                }
            }
            gridCalendar.addView(emptyView)
            cellIndex++
        }

        for (day in 1..daysInMonth) {
            val dayCal = currentMonthCal.clone() as Calendar
            dayCal.set(Calendar.DAY_OF_MONTH, day)
            val key = dateKey(dayCal)
            val preset = getShiftForDate(dayCal)
            val hasNote = !prefs.getString("note_$key", "").isNullOrBlank()

            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(4), dp(2), dp(4))
                val borderHex = when (key) {
                    selectedKey -> "#FFFFFF"
                    todayKey -> "#38BDF8"
                    else -> "#1E293B"
                }
                val bgHex = if (key == selectedKey) "#1E293B" else "#0F172A"
                background = roundedBg(bgHex, 10, borderHex)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = cellWidth - dp(4)
                    height = dp(52)
                    setMargins(dp(2), dp(2), dp(2), dp(2))
                }
                setOnClickListener {
                    selectedDateCal = dayCal
                    renderCalendar()
                    updateSelectedDayPanel()
                }
            }

            val tvDayNum = TextView(this).apply {
                text = if (hasNote) "$day•" else "$day"
                textSize = 13f
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
            }

            val tvTag = TextView(this).apply {
                text = preset.shortCode
                textSize = 9f
                setTextColor(Color.parseColor(preset.colorHex))
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
            }

            cell.addView(tvDayNum)
            cell.addView(tvTag)
            gridCalendar.addView(cell)
            cellIndex++
        }
    }

    private fun updateSelectedDayPanel() {
        val fmt = SimpleDateFormat("d MMMM yyyy, EEEE", trLocale)
        tvSelectedDateTitle.text = fmt.format(selectedDateCal.time)

        val preset = getShiftForDate(selectedDateCal)
        tvSelectedShiftBadge.text = preset.shortCode
        tvSelectedShiftBadge.background = roundedBg(preset.colorHex, 8)

        val key = dateKey(selectedDateCal)
        val note = prefs.getString("note_$key", "") ?: ""
        tvSelectedNote.text = if (note.isNotBlank()) "Not: $note" else "${preset.title} • Not eklemek için Düzenle'ye dokunun."
    }

    private fun getShiftForDate(targetCal: Calendar): ShiftPreset {
        val key = dateKey(targetCal)
        val overrideId = prefs.getString("override_$key", null)
        if (overrideId != null) {
            return presets.find { it.id == overrideId } ?: presets[0]
        }

        val anchorMillis = prefs.getLong("cycle_anchor_millis", -1L)
        if (anchorMillis == -1L) {
            return presets[2]
        }

        val anchorCal = Calendar.getInstance().apply {
            timeInMillis = anchorMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val cleanTarget = (targetCal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val diffDays = TimeUnit.MILLISECONDS.toDays(cleanTarget.timeInMillis - anchorCal.timeInMillis).toInt()
        val startStep = prefs.getInt("cycle_start_step", 0)
        val cyclePos = (((diffDays + startStep) % 4) + 4) % 4

        return when (cyclePos) {
            0 -> presets[0] // 12s Gündüz
            1 -> presets[2] // 24s İstirahat
            2 -> presets[1] // 12s Gece
            else -> presets[2] // 48s İstirahat
        }
    }

    private fun updateLiveCountdown() {
        val now = Calendar.getInstance()
        val gHour = prefs.getInt("gunduz_start_hour", 8)
        val gMin = prefs.getInt("gunduz_start_min", 0)
        val nHour = prefs.getInt("gece_start_hour", 20)
        val nMin = prefs.getInt("gece_start_min", 0)

        for (offset in 0..7) {
            val checkCal = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
            val shift = getShiftForDate(checkCal)
            if (shift.id == "GUNDUZ" || shift.id == "GECE") {
                val shiftStart = (checkCal.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, if (shift.id == "GUNDUZ") gHour else nHour)
                    set(Calendar.MINUTE, if (shift.id == "GUNDUZ") gMin else nMin)
                    set(Calendar.SECOND, 0)
                }
                val shiftEnd = (shiftStart.clone() as Calendar).apply { add(Calendar.HOUR_OF_DAY, 12) }

                if (now.before(shiftStart)) {
                    val diff = shiftStart.timeInMillis - now.timeInMillis
                    tvCountdownTitle.text = "SONRAKİ NÖBET: ${shift.shortCode}"
                    tvCountdownTimer.text = formatDuration(diff)
                    val fmt = SimpleDateFormat("d MMM HH:mm", trLocale)
                    tvCountdownSub.text = "Başlangıç: ${fmt.format(shiftStart.time)}"
                    return
                } else if (now.before(shiftEnd)) {
                    val diff = shiftEnd.timeInMillis - now.timeInMillis
                    tvCountdownTitle.text = "AKTİF NÖBET BİTİMİNE KALAN"
                    tvCountdownTimer.text = formatDuration(diff)
                    val fmt = SimpleDateFormat("HH:mm", trLocale)
                    tvCountdownSub.text = "${shift.title} • Bitiş: ${fmt.format(shiftEnd.time)}"
                    return
                }
            }
        }
        tvCountdownTimer.text = "--:--:--"
    }

    private fun showCycleSetupDialog() {
        val steps = arrayOf(
            "1. Gün: 12s Gündüz Nöbeti",
            "2. Gün: 24s İstirahat (Gündüz Sonrası)",
            "3. Gün: 12s Gece Nöbeti",
            "4. Gün: 48s İstirahat (Gece Sonrası)"
        )
        AlertDialog.Builder(this)
            .setTitle("12/24 - 12/48 Döngü Kurulumu")
            .setItems(steps) { _, selectedStep ->
                val cal = Calendar.getInstance()
                DatePickerDialog(this, { _, y, m, d ->
                    val chosen = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0) }
                    prefs.edit()
                        .putLong("cycle_anchor_millis", chosen.timeInMillis)
                        .putInt("cycle_start_step", selectedStep)
                        .apply()
                    refreshAll()
                    Toast.makeText(this, "Vardiya döngüsü güncellendi!", Toast.LENGTH_SHORT).show()
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }
            .show()
    }

    private fun showDayOverrideDialog() {
        val key = dateKey(selectedDateCal)
        val items = preset
