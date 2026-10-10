package com.phototok.domain

import android.content.pm.PackageManager
import com.photoselector.core.Requirement
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.net.URL

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LegalLinksTest {

    @Test
    @Requirement("REQ-TOK-ARCH.20")
    fun `privacy policy and impressum are valid https URLs`() {
        assertTrue(LegalLinks.PRIVACY_POLICY.startsWith("https://"))
        assertTrue(LegalLinks.IMPRESSUM.startsWith("https://"))

        // Should parse without malformed URL exception
        val privacyUrl = URL(LegalLinks.PRIVACY_POLICY)
        val impressumUrl = URL(LegalLinks.IMPRESSUM)

        assertTrue(privacyUrl.host.isNotEmpty())
        assertTrue(impressumUrl.host.isNotEmpty())
    }

    @Test
    @Requirement("REQ-TOK-ARCH.19")
    fun `manifest does not request INTERNET permission`() {
        val context = RuntimeEnvironment.getApplication()
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )
        val permissions = packageInfo.requestedPermissions ?: emptyArray()

        assertFalse(
            "PhotoTok must not request INTERNET permission (SAF-only requirement)",
            permissions.contains("android.permission.INTERNET"),
        )
    }
}
