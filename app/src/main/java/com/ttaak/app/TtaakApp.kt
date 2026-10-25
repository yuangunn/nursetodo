package com.ttaak.app

import android.app.Application
import com.ttaak.app.data.TaskStore
import com.ttaak.app.lock.LockCard
import com.ttaak.app.lock.LockPageService
import com.ttaak.app.widget.TaskWidgetProvider

class TtaakApp : Application() {
    override fun onCreate() {
        super.onCreate()
        TaskStore.init(this)
        LockCard.createChannel(this)
        TaskStore.onChanged = { refreshSurfaces() }
        refreshSurfaces()
    }

    /** 잠금화면 카드, 홈 위젯, 자정 갱신 알람을 현재 데이터로 다시 그립니다. */
    fun refreshSurfaces() {
        LockCard.update(this)
        TaskWidgetProvider.refresh(this)
        LockCard.scheduleMidnight(this)
        LockPageService.sync(this)
    }
}
