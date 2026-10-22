package com.ttaak.app.lock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ttaak.app.TtaakApp
import com.ttaak.app.data.TaskStore

class LockActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TaskStore.init(context)
        when (intent.action) {
            ACTION_TOGGLE -> {
                val id = intent.getLongExtra(EXTRA_ID, -1)
                if (id >= 0) TaskStore.toggle(id) // onChanged 가 카드/위젯을 다시 그립니다.
            }
            // 사용자가 카드를 밀어 지웠거나(안드로이드 14+), 부팅/날짜 변경 등
            else -> (context.applicationContext as TtaakApp).refreshSurfaces()
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.ttaak.app.TOGGLE"
        const val ACTION_REPOST = "com.ttaak.app.REPOST"
        const val ACTION_REFRESH = "com.ttaak.app.REFRESH"
        const val EXTRA_ID = "id"
    }
}
