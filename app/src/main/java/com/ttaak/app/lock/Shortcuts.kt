package com.ttaak.app.lock

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.ttaak.app.R
import com.ttaak.app.data.Shortcut

/** 딱 화면 바로가기의 이름·아이콘·여는 법. 앱은 런처에 보이는 앱만 고를 수 있습니다. */
object Shortcuts {
    fun component(s: Shortcut): ComponentName? =
        s.id.takeIf { it.startsWith("app:") }?.let { ComponentName.unflattenFromString(it.removePrefix("app:")) }

    /** 앱이 지워졌으면 null. */
    fun defaultName(context: Context, s: Shortcut): String? = when (s.id) {
        Shortcut.CALC -> "항암계산기"
        Shortcut.ALARM -> "알람"
        Shortcut.ADD -> "할 일 추가"
        Shortcut.CALENDAR -> "캘린더"
        else -> component(s)?.let { c ->
            runCatching { context.packageManager.getActivityInfo(c, 0).loadLabel(context.packageManager).toString() }.getOrNull()
        }
    }

    fun name(context: Context, s: Shortcut): String = s.name.ifBlank { defaultName(context, s) ?: "지워진 앱" }

    /** 계산기·알람·할 일 추가·캘린더 버튼의 아이콘 (앱이 아닐 때). */
    fun builtInIcon(s: Shortcut): Int = when (s.id) {
        Shortcut.ALARM -> R.drawable.ic_alarm
        Shortcut.ADD -> R.drawable.ic_memo
        Shortcut.CALENDAR -> R.drawable.ic_calendar
        else -> R.drawable.ic_calc
    }

    /** 앱 아이콘. 계산기·알람·할 일 추가거나 지워진 앱이면 null. */
    fun icon(context: Context, s: Shortcut): ImageBitmap? = component(s)?.let { c ->
        runCatching { context.packageManager.getActivityIcon(c).toBitmap(96, 96).asImageBitmap() }.getOrNull()
    }

    fun launchIntent(s: Shortcut): Intent? = component(s)?.let {
        Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(it)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }

    /** 고를 수 있는 앱 (런처 앱, 딱 자신 빼고) — (바로가기, 이름) 을 이름순으로. */
    fun installedApps(context: Context): List<Pair<Shortcut, String>> {
        val pm = context.packageManager
        return pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            PackageManager.MATCH_ALL,
        )
            .filter { it.activityInfo.packageName != context.packageName }
            .map { Shortcut.app(ComponentName(it.activityInfo.packageName, it.activityInfo.name).flattenToString()) to it.loadLabel(pm).toString() }
            .distinctBy { it.first.id }
            .sortedBy { it.second }
    }
}
