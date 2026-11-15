package com.witherboymcw.superbattery.ui

import android.widget.RadioGroup
import android.widget.TextView
import org.junit.Test
import org.junit.Assert.*
import org.mockito.kotlin.*
import org.mockito.ArgumentCaptor

class SimpleOptionCardTest {

    @Test
    fun testSimpleOptionCardBindAndSelectionBehavior() {
        val selectedTextView = mock<TextView>()
        val radioGroup = mock<RadioGroup>()
        val options = listOf(
            OptionItem(id = 101, value = "opt1", displayText = "Option 1"),
            OptionItem(id = 102, value = "opt2", displayText = "Option 2")
        )

        var selectionCallbackCount = 0
        var lastSelectedValue: String? = null

        SimpleOptionCard.bind(
            headerView = null,
            dropdownView = null,
            arrowView = null,
            selectedTextView = selectedTextView,
            radioGroup = radioGroup,
            currentValue = "opt1",
            options = options,
            onSelectionChanged = { value ->
                selectionCallbackCount++
                lastSelectedValue = value
            }
        )

        // Verify initial setup: check(101) called, selectedTextView text set
        verify(radioGroup).check(101)
        verify(selectedTextView).text = "Option 1"

        // Verify that setting initial check did NOT trigger selection callback (preventing false invocation on bind)
        assertEquals(0, selectionCallbackCount)

        // Capture setOnCheckedChangeListener calls (first call is null, second call is the listener)
        val captor = ArgumentCaptor.forClass(RadioGroup.OnCheckedChangeListener::class.java)
        verify(radioGroup, times(2)).setOnCheckedChangeListener(captor.capture())
        val allValues = captor.allValues
        assertNull(allValues.first())
        val listener = allValues.last()!!

        // Simulate user selecting Option 2 (id = 102)
        listener.onCheckedChanged(radioGroup, 102)
        assertEquals(1, selectionCallbackCount)
        assertEquals("opt2", lastSelectedValue)
        verify(selectedTextView).text = "Option 2"

        // Simulate user re-selecting Option 1 (id = 101)
        listener.onCheckedChanged(radioGroup, 101)
        assertEquals(2, selectionCallbackCount)
        assertEquals("opt1", lastSelectedValue)
        verify(selectedTextView, times(2)).text = "Option 1"
    }

    @Test
    fun testSimpleOptionCardRebindBehavior() {
        val selectedTextView = mock<TextView>()
        val radioGroup = mock<RadioGroup>()
        val options = listOf(
            OptionItem(id = 101, value = "opt1", displayText = "Option 1"),
            OptionItem(id = 102, value = "opt2", displayText = "Option 2")
        )

        var firstBindCallbackCount = 0
        var secondBindCallbackCount = 0
        var currentListener: RadioGroup.OnCheckedChangeListener? = null

        doAnswer { invocation ->
            currentListener = invocation.getArgument(0)
            null
        }.whenever(radioGroup).setOnCheckedChangeListener(anyOrNull())

        doAnswer { invocation ->
            val id = invocation.getArgument<Int>(0)
            currentListener?.onCheckedChanged(radioGroup, id)
            null
        }.whenever(radioGroup).check(any())

        // First bind
        SimpleOptionCard.bind(
            headerView = null,
            dropdownView = null,
            arrowView = null,
            selectedTextView = selectedTextView,
            radioGroup = radioGroup,
            currentValue = "opt1",
            options = options,
            onSelectionChanged = {
                firstBindCallbackCount++
            }
        )

        assertEquals(0, firstBindCallbackCount)

        // Second bind (re-bind) with currentValue = "opt2"
        // Thanks to setOnCheckedChangeListener(null) inside bind(), the old listener from first bind
        // is cleared before check(102) is invoked, preventing false callback triggers on re-bind.
        SimpleOptionCard.bind(
            headerView = null,
            dropdownView = null,
            arrowView = null,
            selectedTextView = selectedTextView,
            radioGroup = radioGroup,
            currentValue = "opt2",
            options = options,
            onSelectionChanged = {
                secondBindCallbackCount++
            }
        )

        assertEquals(0, firstBindCallbackCount)
        assertEquals(0, secondBindCallbackCount)
        verify(selectedTextView).text = "Option 2"
    }
}
