package com.ttaak.app.lock

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService

/** 빠른 설정 타일 — 잠금화면에서 상단바를 내려 바로 할 일을 추가합니다. */
class AddTileService : TileService() {
    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(LockCard.addIntent(this))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(
                Intent(this, QuickAddActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
