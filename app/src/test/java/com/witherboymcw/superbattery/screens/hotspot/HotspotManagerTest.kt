package com.witherboymcw.superbattery.screens.hotspot

import android.content.Context
import android.content.SharedPreferences
import com.witherboymcw.superbattery.settings.AppPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

class HotspotManagerTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    @Before
    fun setup() {
        context = mock(Context::class.java)
        val appContext = mock(Context::class.java)
        prefs = mock(SharedPreferences::class.java)
        editor = mock(SharedPreferences.Editor::class.java)
        
        `when`(context.applicationContext).thenReturn(appContext)
        `when`(appContext.applicationContext).thenReturn(appContext)
        
        // Mock AppPreferences
        `when`(appContext.getSharedPreferences(AppPreferences.PREFS_NAME, Context.MODE_PRIVATE)).thenReturn(prefs)
        
        // Mock HotspotManager prefs
        `when`(appContext.getSharedPreferences("hotspot_manager_prefs", Context.MODE_PRIVATE)).thenReturn(prefs)
        
        `when`(prefs.edit()).thenReturn(editor)
        `when`(editor.putString(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(editor)
        `when`(editor.putBoolean(anyString(), anyBoolean())).thenReturn(editor)
        `when`(editor.apply()).then {}
        `when`(prefs.getString(anyString(), org.mockito.ArgumentMatchers.nullable(String::class.java))).thenReturn(null)
        `when`(prefs.getBoolean(AppPreferences.KEY_ROOT_HOTSPOT_MONITOR, false)).thenReturn(true)
        
        // Clear static state for isolation
        val field = HotspotManager::class.java.getDeclaredField("clientMap")
        field.isAccessible = true
        (field.get(HotspotManager) as MutableMap<*, *>).clear()
    }

    @Test
    fun testStateMergingPreservesAliasAndDenied() {
        try {
            val mac = "AA:BB:CC:11:22:33"
            HotspotManager.onClientConnected(context, mac, "192.168.43.100", "My Device")
            
            var clients = HotspotManager.clientsState.value
            assertEquals(1, clients.size)
            assertEquals("My Device", clients[0].deviceName)
            
            HotspotManager.updateClientAlias(context, mac, "Custom Alias")
            
            clients = HotspotManager.clientsState.value
            assertEquals("Custom Alias", clients[0].customAlias)
            
            // Re-connect
            HotspotManager.onClientConnected(context, mac, "192.168.43.101", "New Hostname")
            clients = HotspotManager.clientsState.value
            
            assertEquals(1, clients.size)
            assertEquals("192.168.43.101", clients[0].ipAddress)
            assertEquals("Custom Alias", clients[0].customAlias)
        } catch(e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
}
