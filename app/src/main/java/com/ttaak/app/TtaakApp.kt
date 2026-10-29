package com.ttaak.app

import android.app.Application
import com.ttaak.app.data.TaskStore
import com.ttaak.app.lock.Alarms
import com.ttaak.app.lock.LockCard
import com.ttaak.app.lock.LockPageService
import com.ttaak.app.widget.TaskWidgetProvider

class TtaakApp : Application() {
    override fun onCreate() {
        super.onCreate()
        TaskStore.init(this)
        LockCard.createChannel(this)
        Alarms.createChannel(this)
        TaskStore.onChanged = { refreshSurfaces() }
        refreshSurfaces()
    }

    /** 교대가 바뀌었으면 프리셋을 불러오고, 잠금화면 카드, 홈 위젯, 자정·교대 갱신 알람을 현재 데이터로 다시 그리고, 맞춰 둔 알람을 다시 맞춥니다(부팅 뒤 등). */
    fun refreshSurfaces() {
        TaskStore.refreshPreset()
        LockCard.update(this)
        TaskWidgetProvider.refresh(this)
        LockCard.scheduleRefresh(this)
        LockPageService.sync(this)
        Alarms.scheduleAll(this)
    }
}
