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
import com.witherboymcw.superbattery.settings.Notch
import com.witherboymcw.superbattery.settings.Permissions
import com.witherboymcw.superbattery.settings.ThickChin
import com.witherboymcw.superbattery.settings.HardwarePreferences
import com.witherboymcw.superbattery.templates.DrawerScreen

internal class SettingsViewController : DrawerScreen(
    containerId = R.id.settings_container,
    slideDirection = SlideDirection.FROM_RIGHT,
    actionButtonId = R.id.settings_button
) {
    var permissionsCard: Permissions? = null
        private set
    var thickChinCard: ThickChin? = null
        private set
    var notchCard: Notch? = null
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

        com.witherboymcw.superbattery.OledThemeAllInOne.applyThemeToAllCards(activity.findViewById(com.witherboymcw.superbattery.R.id.settings_container), activity)

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
                    dashboardViews.btnSettings?.rotation = progress * 90f
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
                        dashboardViews.btnSettings?.animate()
                            ?.rotation(90f)
                            ?.setDuration(200L)
                            ?.setInterpolator(FastOutSlowInInterpolator())
                            ?.start()
                    }
                }
            }

            // Initialize remaining Component Cards
            notchCard?.unbind()
            val pCard = Permissions(this).also { permissionsCard = it }
            val hwCard = HardwarePreferences(this)
            val chinCard = ThickChin(this).also { thickChinCard = it }
            val nCard = Notch(this).also { notchCard = it }

            // Bind each component card
            pCard.bind(savedInstanceState)
            hwCard.bind(savedInstanceState) {
                updateDashboard()
                refreshWidgets()
            }
            chinCard.bind(savedInstanceState)
            nCard.bind(savedInstanceState)

            // Setup 7 simple option cards data-driven
            com.witherboymcw.superbattery.settings.optionCards.forEach { spec ->
                bindOptionCard(this, savedInstanceState, spec)
            }

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
            val settingsDetailTitle = findViewById<TextView>(R.id.settings_detail_title)

            if (settingsDetailTitle != null) {
                val sections = com.witherboymcw.superbattery.settings.SettingsSection.entries

                fun selectSection(section: com.witherboymcw.superbattery.settings.SettingsSection) {
                    chinCard.flushCommit()
                    sections.forEach { s ->
                        findViewById<View>(s.dropdownId)?.visibility = View.GONE
                        findViewById<ImageView>(s.arrowId)?.rotation = 0f
                    }
                    findViewById<View>(section.dropdownId)?.visibility = View.VISIBLE
                    findViewById<ImageView>(section.arrowId)?.rotation = 180f
                    settingsDetailTitle.setText(section.titleRes)
                }

                sections.forEach { section ->
                    findViewById<View>(section.headerId)?.setOnClickListener {
                        selectSection(section)
                    }
                }

                val restoredSection = sections.firstOrNull { section ->
                    savedInstanceState?.getBoolean(section.stateKey) == true
                } ?: com.witherboymcw.superbattery.settings.SettingsSection.LANGUAGE

                selectSection(restoredSection)
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

    private fun <T> bindOptionCard(
        activity: MainActivity,
        savedInstanceState: Bundle?,
        spec: com.witherboymcw.superbattery.settings.OptionCardSpec<T>
    ) {
        val headerView = activity.findViewById<View>(spec.section.headerId)
        val dropdownView = activity.findViewById<View>(spec.section.dropdownId)
        val arrowView = activity.findViewById<ImageView>(spec.section.arrowId)
        val selectedTextView = activity.findViewById<TextView>(spec.selectedTextId)
        val radioGroup = activity.findViewById<android.widget.RadioGroup>(spec.radioGroupId)
        
        val currentValue = spec.read(activity)
        val options = spec.options.map { option ->
            com.witherboymcw.superbattery.templates.OptionItem(option.radioId, option.value, activity.getString(option.labelRes))
        }

        com.witherboymcw.superbattery.templates.SimpleOptionCard.bind(
            headerView = headerView,
            dropdownView = dropdownView,
            arrowView = arrowView,
            selectedTextView = selectedTextView,
            radioGroup = radioGroup,
            savedInstanceStateKey = spec.section.stateKey,
            savedInstanceState = savedInstanceState,
            currentValue = currentValue,
            options = options
        ) { newValue ->
            val activeValue = spec.read(activity)
            if (activeValue != newValue) {
                spec.write(activity, newValue)
            }
        }
    }

    companion object {
        const val KEY_SETTINGS_OPEN = "key_settings_open"
        const val KEY_SETTINGS_SCROLL_Y = "key_settings_scroll_y"
        const val KEY_SETTINGS_SCROLL_RIGHT_Y = "key_settings_scroll_right_y"
    }
}
