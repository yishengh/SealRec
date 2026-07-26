package com.yishenghuang.sealrec

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflinePermissionInstrumentedTest {

    @Test
    fun mergedManifest_hasNoInternetPermission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )
        val requested = info.requestedPermissions?.toList().orEmpty()
        assertFalse(
            "INTERNET must not be present for offline SealRec",
            requested.contains(android.Manifest.permission.INTERNET),
        )
        assertFalse(
            "ACCESS_NETWORK_STATE must not be present",
            requested.contains(android.Manifest.permission.ACCESS_NETWORK_STATE),
        )
    }
}
