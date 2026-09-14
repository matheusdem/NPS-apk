package com.osamaalek.kiosklauncher.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.osamaalek.kiosklauncher.ui.MainActivity

class KioskAccessibilityService : AccessibilityService() {

    private val TAG = "KioskAccessibility"

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Não precisamos tratar eventos de mudança de janela aqui.
        // Faremos isso no Watchdog para garantir maior estabilidade.
    }

    override fun onInterrupt() {
        Log.d(TAG, "Serviço de Acessibilidade interrompido")
    }

    /**
     * Esta é a mágica: O Android manda os eventos de hardware (botões) para este serviço
     * ANTES de enviar para o sistema operacional.
     * Se retornarmos "true", nós dizemos ao Android: "Já cuidei disso, não faça nada".
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        val action = event.action
        val keyCode = event.keyCode

        if (action == KeyEvent.ACTION_DOWN) {
            when (keyCode) {
                // Intercepta e descarta botões que podem ser usados para fugir do App
                KeyEvent.KEYCODE_BACK, 
                KeyEvent.KEYCODE_APP_SWITCH, 
                KeyEvent.KEYCODE_HOME -> {
                    Log.d(TAG, "Botão interceptado e bloqueado: $keyCode")
                    
                    // Como medida extra, se o usuário tentar o botão Home, nós reabrimos 
                    // o MainActivity imediatamente caso tenha minimizado de alguma forma
                    if (keyCode == KeyEvent.KEYCODE_HOME || keyCode == KeyEvent.KEYCODE_APP_SWITCH) {
                        val intent = Intent(this, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        startActivity(intent)
                    }
                    
                    return true // Retorna true para bloquear a ação original do botão
                }
            }
        }
        
        return super.onKeyEvent(event)
    }
}