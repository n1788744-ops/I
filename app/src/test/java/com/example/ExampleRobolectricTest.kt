package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.RouterProfile
import com.example.network.QrCodeGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `app name and default router profile match requirements`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("4G Router", appName)

    val profile = RouterProfile()
    assertEquals("MyHotspot", profile.ssid)
    assertEquals("sw22vmi55xr11", profile.password)
    assertEquals("WPA2-PSK", profile.securityProtocol)
  }

  @Test
  fun `wifi qr payload and bitmap generate cleanly`() {
    val payload = QrCodeGenerator.buildWifiPayload("MyHotspot", "sw22vmi55xr11")
    assertEquals("WIFI:T:WPA;S:MyHotspot;P:sw22vmi55xr11;;", payload)
    val bitmap = QrCodeGenerator.generateQrBitmap(payload, sizePx = 256)
    assertNotNull(bitmap)
  }
}
