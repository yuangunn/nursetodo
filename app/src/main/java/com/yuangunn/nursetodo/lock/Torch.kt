package com.yuangunn.nursetodo.lock

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 손전등(카메라 플래시) 켜고 끄기. setTorchMode 는 카메라 권한이 필요 없습니다(6.0+).
 * [on] 은 시스템 빠른 설정에서 켜고 꺼도 따라 바뀝니다([watch] 의 TorchCallback). 잠금을 풀거나 화면이 꺼져도
 * 시스템 손전등처럼 켜진 채로 둡니다.
 */
object Torch {
    private val _on = MutableStateFlow(false)
    val on: StateFlow<Boolean> = _on
    private var watching = false

    private fun manager(context: Context) = context.getSystemService(CameraManager::class.java)

    /** 플래시가 있는 첫 카메라. 없으면 null(태블릿·일부 기기). */
    private fun cameraId(context: Context): String? = runCatching {
        val cm = manager(context)
        cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
    }.getOrNull()

    fun available(context: Context): Boolean = cameraId(context) != null

    /** 켜짐 상태를 따라가기 시작(한 번만). */
    fun watch(context: Context) {
        if (watching) return
        val app = context.applicationContext
        val id = cameraId(app) ?: return
        runCatching {
            manager(app).registerTorchCallback(
                object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                        if (cameraId == id) _on.value = enabled
                    }

                    override fun onTorchModeUnavailable(cameraId: String) {
                        if (cameraId == id) _on.value = false
                    }
                },
                Handler(Looper.getMainLooper()),
            )
            watching = true
        }
    }

    /** 켜져 있으면 끄고 꺼져 있으면 켭니다. 다른 앱이 카메라를 쓰는 중이거나 플래시가 없으면 false. */
    fun toggle(context: Context): Boolean {
        val id = cameraId(context) ?: return false
        return runCatching { manager(context).setTorchMode(id, !_on.value) }.isSuccess
    }
}
