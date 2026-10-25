package com.ttaak.app.lock

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBuild

/** 삼성 잠금화면 알림 스타일 감지. 값이 없는 공장 초기 상태도 '아이콘'으로 봐야 카드가 안 보인다고 알려 줄 수 있습니다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SetupTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun samsungWithoutValueMeansIcons() {
        ShadowBuild.setManufacturer("samsung")
        assertEquals(1, Setup.samsungLockStyle(context))
        Settings.System.putInt(context.contentResolver, "lockscreen_minimizing_notification", 0)
        assertEquals(0, Setup.samsungLockStyle(context))
    }

    @Test
    fun otherPhonesAreNotChecked() {
        ShadowBuild.setManufacturer("Google")
        assertNull(Setup.samsungLockStyle(context))
    }
}
