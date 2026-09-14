package com.osamaalek.kiosklauncher.services

import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.osamaalek.kiosklauncher.ui.MainActivity

class KioskWatchdogService : Service() {

    private val TAG = "KioskWatchdog"
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private val checkInterval = 1000L // 1 segundo

    // Variável para evitar loops infinitos caso precisemos abrir algo intencionalmente
    companion object {
        var isKioskTemporarilyDisabled = false
    }

    private val watchdogRunnable = object : Runnable {
        override fun run() {
            checkForegroundApp()
            handler.postDelayed(this, checkInterval)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Watchdog Service Iniciado")
        
        // 1. Cria a sobreposição invisível para bloquear a barra de notificações
        setupStatusBarOverlay()
        
        // 2. Inicia o monitoramento de evasão
        handler.postDelayed(watchdogRunnable, checkInterval)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Watchdog Service Destruído")
        handler.removeCallbacks(watchdogRunnable)
        removeStatusBarOverlay()
    }

    /**
     * ESTRATÉGIA 3 (Fully Kiosk): 
     * Desenha um retângulo transparente no topo da tela, bem onde fica a barra de status.
     * Isso impede fisicamente que o usuário consiga arrastar o dedo de cima para baixo.
     */
    private fun setupStatusBarOverlay() {
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            
            overlayView = View(this).apply {
                // Deixa invisível, mas consome os toques
                setBackgroundColor(0x00000000)
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                100, // Altura estimada da barra de status
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP

            windowManager?.addView(overlayView, params)
            Log.d(TAG, "Sobreposição da barra de status criada com sucesso.")
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao criar sobreposição (Permissão concedida?): ${e.message}")
        }
    }

    private fun removeStatusBarOverlay() {
        try {
            overlayView?.let {
                windowManager?.removeView(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao remover sobreposição: ${e.message}")
        }
    }

    /**
     * ESTRATÉGIA 5 (Fully Kiosk):
     * Verifica constantemente qual é o pacote que está no topo da tela.
     * Se for diferente do nosso (E não estivermos pausados para atualização), ele reabre nosso app.
     */
    private fun checkForegroundApp() {
        if (isKioskTemporarilyDisabled) return

        val usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 10000 // Últimos 10 segundos

        val usageStatsList = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        
        var topPackageName = ""
        var lastTimeUsed = 0L

        if (!usageStatsList.isNullOrEmpty()) {
            for (usageStats in usageStatsList) {
                if (usageStats.lastTimeUsed > lastTimeUsed) {
                    topPackageName = usageStats.packageName
                    lastTimeUsed = usageStats.lastTimeUsed
                }
            }
        }

        if (topPackageName.isNotEmpty() && topPackageName != packageName) {
            Log.w(TAG, "Fuga detectada! O pacote $topPackageName está na frente. Forçando retorno.")
            
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(intent)
        }
    }
}