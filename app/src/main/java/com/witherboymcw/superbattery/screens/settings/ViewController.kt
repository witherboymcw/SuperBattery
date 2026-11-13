package com.witherboymcw.superbattery.screens.settings

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.witherboymcw.superbattery.MainActivity
import com.witherboymcw.superbattery.R
import com.witherboymcw.superbattery.SwipeBackFrameLayout
import com.witherboymcw.superbattery.awareness.ChinAwareness
import com.witherboymcw.superbattery.awareness.NotchAwareness
import com.witherboymcw.superbattery.settings.Frequency
import com.witherboymcw.superbattery.settings.Language
import com.witherboymcw.superbattery.settings.Notch
import com.witherboymcw.superbattery.settings.Oled
import com.witherboymcw.superbattery.settings.Permissions
import com.witherboymcw.superbattery.settings.UnitRAM
import com.witherboymcw.superbattery.settings.UnitTEMP
import com.witherboymcw.superbattery.settings.Theme
import com.witherboymcw.superbattery.settings.ThickChin
import com.witherboymcw.superbattery.settings.UseSysBatt
import com.witherboymcw.superbattery.settings.UnitVOLT
import com.witherboymcw.superbattery.templates.DrawerScreen

internal class SettingsViewController : DrawerScreen(
    containerId = R.id.settings_container,
    slideDirection = SlideDirection.FROM_RIGHT,
    actionButtonId = R.id.settings_button
) {
    var permissionsCard: Permissions? = null
        private set
    var languageCard: Language? = null
        private set
    var frequencyCard: Frequency? = null
        private set
    var themeCard: Theme? = null
        private set
    var useSysBattCard: UseSysBatt? = null
        private set
    var unitVOLTCard: UnitVOLT? = null
        private set
    var unitRAMCard: UnitRAM? = null
        private set
    var unitTEMPCard: UnitTEMP? = null
        private set
    var thickChinCard: ThickChin? = null
        private set
    var notchCard: Notch? = null
        private set
    var oledCard: Oled? = null
        private set

    override fun onPreClose() {
        thickChinCard?.flushCommit()
    }

    override fun onPostOpen(activity: MainActivity) {
        NotchAwareness.updateSettingsHeaderLayout(activity)
    }

    fun flushThickChinCommit() {
        thickChinCard?.flushCommit()
    }

    override fun unbind() {
        notchCard?.unbind()
        notchCard = null
        thickChinCard?.flushCommit()
        thickChinCard = null
        super.unbind()
    }

    fun bind(activity: MainActivity, savedInstanceState: Bundle? = null) {
        this.activityRef = activity
        activity.onBackPressedDispatcher.addCallback(activity, backCallback)

        activity.applySettingsCardTheme()

        with(activity) {
            ChinAwareness.checkFoldableChinVisibility(this)

            val swipeContainer = findViewById<android.view.ViewGroup>(R.id.settings_container) ?: return
            swipeContainer.isClickable = true
            swipeContainer.isFocusable = true

            // Top-left back button
            findViewById<View>(R.id.settings_back_button)?.setOnClickListener {
                closeScreen(this)
            }

            // Swipe right callback from SwipeBackFrameLayout
            (swipeContainer as? SwipeBackFrameLayout)?.swipeListener = object : SwipeBackFrameLayout.SwipeListener {
                override fun onSwipeProgress(translationX: Float) {
                    swipeContainer.translationX = translationX
                    val widthVal = swipeContainer.width.toFloat().takeIf { it > 0 } ?: 1080f
                    val progress = (1f - (translationX / widthVal)).coerceIn(0f, 1f)
                    btnSettings?.rotation = progress * 90f
                }

                override fun onSwipeEnd(shouldClose: Boolean) {
                    if (shouldClose) {
                        closeScreen(this@with)
                    } else {
                        swipeContainer.animate()
                            .translationX(0f)
                            .setDuration(200L)
                            .setInterpolator(FastOutSlowInInterpolator())
                            .withLayer()
                            .start()
                        btnSettings?.animate()
                            ?.rotation(90f)
                            ?.setDuration(200L)
                            ?.setInterpolator(FastOutSlowInInterpolator())
                            ?.start()
                    }
                }
            }

            // Initialize 11 Component Cards
            notchCard?.unbind()
            val pCard = Permissions(this).also { permissionsCard = it }
            val lCard = Language(this).also { languageCard = it }
            val fCard = Frequency(this).also { frequencyCard = it }
            val tCard = Theme(this).also { themeCard = it }
            val uCard = UseSysBatt(this).also { useSysBattCard = it }
            val vCard = UnitVOLT(this).also { unitVOLTCard = it }
            val rCard = UnitRAM(this).also { unitRAMCard = it }
            val tempCard = UnitTEMP(this).also { unitTEMPCard = it }
            val chinCard = ThickChin(this).also { thickChinCard = it }
            val nCard = Notch(this).also { notchCard = it }
            val oCard = Oled(this).also { oledCard = it }

            // Bind each card
            pCard.bind(savedInstanceState)
            lCard.bind(savedInstanceState) { targetLang ->
                setAppLanguage(targetLang)
            }
            fCard.bind(
                savedInstanceState,
                refreshIntervalMs
            ) { selectedMs ->
                refreshIntervalMs = selectedMs
            }
            tCard.bind(savedInstanceState)
            uCard.bind {
                updateDashboard()
                refreshWidgets()
            }
            vCard.bind(savedInstanceState) {
                updateDashboard()
                refreshWidgets()
            }
            rCard.bind(savedInstanceState) {
                updateDashboard()
                refreshWidgets()
            }
            tempCard.bind(savedInstanceState) {
                updateDashboard()
                refreshWidgets()
            }
            chinCard.bind(savedInstanceState)
            nCard.bind(savedInstanceState)
            oCard.bind(savedInstanceState)

            // Setup scroll flush for ThickChin
            val settingsScrollView = findViewById<android.widget.ScrollView>(R.id.settings_scroll_view)
            val settingsScrollLeft = findViewById<android.widget.ScrollView>(R.id.settings_scroll_left)
            val settingsScrollRight = findViewById<android.widget.ScrollView>(R.id.settings_scroll_right)

            fun setupScrollFlush(view: View?) {
                view ?: return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    view.setOnScrollChangeListener { _, _, _, _, _ ->
                        chinCard.flushCommit()
                    }
                } else {
                    view.viewTreeObserver?.addOnScrollChangedListener {
                        chinCard.flushCommit()
                    }
                }
            }

            setupScrollFlush(settingsScrollView)
            setupScrollFlush(settingsScrollLeft)
            setupScrollFlush(settingsScrollRight)

            // Landscape two-column settings setup
            val headerLanguage = findViewById<View>(R.id.header_language)
            val dropdownLanguage = findViewById<View>(R.id.dropdown_language)
            val arrowLanguage = findViewById<ImageView>(R.id.arrow_language)

            val headerSuperPermissions = findViewById<View>(R.id.header_super_permissions)
            val dropdownSuperPermissions = findViewById<View>(R.id.dropdown_super_permissions)
            val arrowSuperPermissions = findViewById<ImageView>(R.id.arrow_super_permissions)

            val headerFrequency = findViewById<View>(R.id.header_update_frequency)
            val dropdownFrequency = findViewById<View>(R.id.dropdown_update_frequency)
            val arrowFrequency = findViewById<ImageView>(R.id.arrow_update_frequency)

            val headerTheme = findViewById<View>(R.id.header_theme)
            val dropdownTheme = findViewById<View>(R.id.dropdown_theme)
            val arrowTheme = findViewById<ImageView>(R.id.arrow_theme)

            val headerVoltageUnit = findViewById<View>(R.id.header_voltage_unit)
            val dropdownVoltageUnit = findViewById<View>(R.id.dropdown_voltage_unit)
            val arrowVoltageUnit = findViewById<ImageView>(R.id.arrow_voltage_unit)

            val headerRamUnit = findViewById<View>(R.id.header_ram_unit)
            val dropdownRamUnit = findViewById<View>(R.id.dropdown_ram_unit)
            val arrowRamUnit = findViewById<ImageView>(R.id.arrow_ram_unit)

            val headerTempUnit = findViewById<View>(R.id.header_temp_unit)
            val dropdownTempUnit = findViewById<View>(R.id.dropdown_temp_unit)
            val arrowTempUnit = findViewById<ImageView>(R.id.arrow_temp_unit)

            val headerThickChin = findViewById<View>(R.id.header_thick_chin)
            val dropdownThickChin = findViewById<View>(R.id.dropdown_thick_chin)
            val arrowThickChin = findViewById<ImageView>(R.id.arrow_thick_chin)

            val headerNotch = findViewById<View>(R.id.header_notch)
            val dropdownNotch = findViewById<View>(R.id.dropdown_notch)
            val arrowNotch = findViewById<ImageView>(R.id.arrow_notch)

            val headerAssumeOled = findViewById<View>(R.id.header_assume_oled)
            val dropdownAssumeOled = findViewById<View>(R.id.dropdown_assume_oled)
            val arrowAssumeOled = findViewById<ImageView>(R.id.arrow_assume_oled)

            val settingsDetailTitle = findViewById<TextView>(R.id.settings_detail_title)

            if (settingsDetailTitle != null) {
                val dropdowns = listOf(
                    dropdownLanguage,
                    dropdownSuperPermissions,
                    dropdownFrequency,
                    dropdownTheme,
                    dropdownVoltageUnit,
                    dropdownRamUnit,
                    dropdownTempUnit,
                    dropdownThickChin,
                    dropdownNotch,
                    dropdownAssumeOled
                )
                val arrows = listOf(
                    arrowLanguage,
                    arrowSuperPermissions,
                    arrowFrequency,
                    arrowTheme,
                    arrowVoltageUnit,
                    arrowRamUnit,
                    arrowTempUnit,
                    arrowThickChin,
                    arrowNotch,
                    arrowAssumeOled
                )

                fun selectSection(activeDropdown: View?, activeArrow: ImageView?, titleRes: Int) {
                    chinCard.flushCommit()
                    dropdowns.forEach { it?.visibility = View.GONE }
                    arrows.forEach { it?.rotation = 0f }
                    activeDropdown?.visibility = View.VISIBLE
                    activeArrow?.rotation = 180f
                    settingsDetailTitle.setText(titleRes)
                }

                headerLanguage?.setOnClickListener {
                    selectSection(dropdownLanguage, arrowLanguage, R.string.language)
                }
                headerSuperPermissions?.setOnClickListener {
                    selectSection(dropdownSuperPermissions, arrowSuperPermissions, R.string.super_permissions_header)
                }
                headerFrequency?.setOnClickListener {
                    selectSection(dropdownFrequency, arrowFrequency, R.string.update_frequency)
                }
                headerTheme?.setOnClickListener {
                    selectSection(dropdownTheme, arrowTheme, R.string.theme_header)
                }
                headerVoltageUnit?.setOnClickListener {
                    selectSection(dropdownVoltageUnit, arrowVoltageUnit, R.string.voltage_unit_header)
                }
                headerRamUnit?.setOnClickListener {
                    selectSection(dropdownRamUnit, arrowRamUnit, R.string.ram_unit_header)
                }
                headerTempUnit?.setOnClickListener {
                    selectSection(dropdownTempUnit, arrowTempUnit, R.string.temp_unit_header)
                }
                headerThickChin?.setOnClickListener {
                    selectSection(dropdownThickChin, arrowThickChin, R.string.thick_chin_header)
                }
                headerNotch?.setOnClickListener {
                    selectSection(dropdownNotch, arrowNotch, R.string.notch_header)
                }
                headerAssumeOled?.setOnClickListener {
                    selectSection(dropdownAssumeOled, arrowAssumeOled, R.string.assume_oled_header)
                }

                val restoredSection = when {
                    savedInstanceState?.getBoolean(Permissions.KEY_SUPER_PERMISSIONS_DROPDOWN_OPEN) == true ->
                        Triple(dropdownSuperPermissions, arrowSuperPermissions, R.string.super_permissions_header)
                    savedInstanceState?.getBoolean(Frequency.KEY_FREQUENCY_DROPDOWN_OPEN) == true ->
                        Triple(dropdownFrequency, arrowFrequency, R.string.update_frequency)
                    savedInstanceState?.getBoolean(Theme.KEY_THEME_DROPDOWN_OPEN) == true ->
                        Triple(dropdownTheme, arrowTheme, R.string.theme_header)
                    savedInstanceState?.getBoolean(UnitVOLT.KEY_VOLTAGE_UNIT_DROPDOWN_OPEN) == true ->
                        Triple(dropdownVoltageUnit, arrowVoltageUnit, R.string.voltage_unit_header)
                    savedInstanceState?.getBoolean(UnitRAM.KEY_RAM_UNIT_DROPDOWN_OPEN) == true ->
                        Triple(dropdownRamUnit, arrowRamUnit, R.string.ram_unit_header)
                    savedInstanceState?.getBoolean(UnitTEMP.KEY_TEMP_UNIT_DROPDOWN_OPEN) == true ->
                        Triple(dropdownTempUnit, arrowTempUnit, R.string.temp_unit_header)
                    savedInstanceState?.getBoolean(ChinAwareness.KEY_THICK_CHIN_DROPDOWN_OPEN) == true ->
                        Triple(dropdownThickChin, arrowThickChin, R.string.thick_chin_header)
                    savedInstanceState?.getBoolean(Notch.KEY_NOTCH_DROPDOWN_OPEN) == true ->
                        Triple(dropdownNotch, arrowNotch, R.string.notch_header)
                    savedInstanceState?.getBoolean(Oled.KEY_ASSUME_OLED_DROPDOWN_OPEN) == true ->
                        Triple(dropdownAssumeOled, arrowAssumeOled, R.string.assume_oled_header)
                    else ->
                        Triple(dropdownLanguage, arrowLanguage, R.string.language)
                }
                selectSection(restoredSection.first, restoredSection.second, restoredSection.third)
            }

            val savedScrollY = savedInstanceState?.getInt(KEY_SETTINGS_SCROLL_Y, -1) ?: -1
            if (savedScrollY >= 0) {
                val scrollView = findViewById<android.widget.ScrollView>(R.id.settings_scroll_view)
                    ?: findViewById<android.widget.ScrollView>(R.id.settings_scroll_left)
                scrollView?.post {
                    scrollView.scrollTo(0, savedScrollY)
                }
            }
            val savedRightScrollY = savedInstanceState?.getInt(KEY_SETTINGS_SCROLL_RIGHT_Y, -1) ?: -1
            if (savedRightScrollY >= 0) {
                val rightScrollView = findViewById<android.widget.ScrollView>(R.id.settings_scroll_right)
                rightScrollView?.post {
                    rightScrollView.scrollTo(0, savedRightScrollY)
                }
            }
        }
    }

    fun updatePermissionStatuses() {
        permissionsCard?.updateStatuses()
    }

    companion object {
        const val KEY_SETTINGS_OPEN = "key_settings_open"
        const val KEY_LANGUAGE_DROPDOWN_OPEN = Language.KEY_LANGUAGE_DROPDOWN_OPEN
        const val KEY_FREQUENCY_DROPDOWN_OPEN = Frequency.KEY_FREQUENCY_DROPDOWN_OPEN
        const val KEY_THEME_DROPDOWN_OPEN = Theme.KEY_THEME_DROPDOWN_OPEN
        const val KEY_TEMP_UNIT_DROPDOWN_OPEN = UnitTEMP.KEY_TEMP_UNIT_DROPDOWN_OPEN
        const val KEY_VOLTAGE_UNIT_DROPDOWN_OPEN = UnitVOLT.KEY_VOLTAGE_UNIT_DROPDOWN_OPEN
        const val KEY_RAM_UNIT_DROPDOWN_OPEN = UnitRAM.KEY_RAM_UNIT_DROPDOWN_OPEN
        const val KEY_NOTCH_DROPDOWN_OPEN = Notch.KEY_NOTCH_DROPDOWN_OPEN
        const val KEY_ASSUME_OLED_DROPDOWN_OPEN = Oled.KEY_ASSUME_OLED_DROPDOWN_OPEN
        const val KEY_SETTINGS_SCROLL_Y = "key_settings_scroll_y"
        const val KEY_SETTINGS_SCROLL_RIGHT_Y = "key_settings_scroll_right_y"
        const val REFRESH_INTERVAL_KEY = "refresh_interval_ms"
        const val APP_THEME_KEY = Theme.APP_THEME_KEY
    }
}
