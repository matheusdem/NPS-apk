package com.osamaalek.kiosklauncher

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object UpdateManager {

    private const val TAG = "UpdateManager"
    // URL do version.json gerado pelo GitHub Actions (Modifique para o nome do SEU usuário no GitHub)
    private const val VERSION_URL = "https://github.com/matheusdem/NPS-apk/releases/latest/download/version.json"
    
    /**
     * Verifica no GitHub se há uma versão mais nova. 
     * Se houver, baixa e instala silenciosamente (requer que o app seja Device Owner).
     */
    suspend fun checkAndUpdate(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Checando atualizações...")
                val (latestVersion, apkUrl) = fetchVersionInfo()
                
                if (latestVersion == null || apkUrl == null) {
                    Log.e(TAG, "Falha ao obter versão do servidor.")
                    return@withContext
                }

                val currentVersion = BuildConfig.VERSION_NAME
                Log.d(TAG, "Versão atual: $currentVersion | Versão no GitHub: $latestVersion")

                if (isNewerVersion(currentVersion, latestVersion)) {
                    Log.d(TAG, "Nova versão encontrada. Iniciando download...")
                    val apkFile = downloadApk(context, apkUrl)
                    if (apkFile != null) {
                        Log.d(TAG, "Download concluído. Iniciando instalação silenciosa...")
                        installApkSilently(context, apkFile)
                    }
                } else {
                    Log.d(TAG, "O aplicativo já está na versão mais recente.")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Erro durante o processo de atualização: ${e.message}")
            }
        }
    }

    private fun fetchVersionInfo(): Pair<String?, String?> {
        try {
            val url = URL(VERSION_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            
            // O GitHub pode fazer redirects ao baixar de "latest/download/..."
            connection.instanceFollowRedirects = true

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val jsonResponse = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(jsonResponse)
                val version = jsonObject.optString("version", null)
                val apkUrl = jsonObject.optString("apkUrl", null)
                return Pair(version, apkUrl)
            }
        } catch (e: Exception) {
             Log.e(TAG, "Erro ao buscar version.json: ${e.message}")
        }
        return Pair(null, null)
    }

    private fun downloadApk(context: Context, downloadUrl: String): File? {
        try {
            val url = URL(downloadUrl)
            var connection = url.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            
            // Tratamento extra para redirects do GitHub
            var redirect = false
            var status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) {
                if (status == HttpURLConnection.HTTP_MOVED_TEMP || 
                    status == HttpURLConnection.HTTP_MOVED_PERM || 
                    status == HttpURLConnection.HTTP_SEE_OTHER) {
                    redirect = true
                }
            }
            
            if (redirect) {
                val newUrl = connection.getHeaderField("Location")
                connection = URL(newUrl).openConnection() as HttpURLConnection
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val apkFile = File(context.cacheDir, "update.apk")
                
                connection.inputStream.use { input ->
                    FileOutputStream(apkFile).use { output ->
                        input.copyTo(output)
                    }
                }
                return apkFile
            } else {
                 Log.e(TAG, "Erro ao baixar APK. Código: ${connection.responseCode}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao fazer download do APK: ${e.message}")
        }
        return null
    }

    private fun installApkSilently(context: Context, apkFile: File) {
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        
        var sessionId = -1
        var session: PackageInstaller.Session? = null

        try {
            sessionId = packageInstaller.createSession(params)
            session = packageInstaller.openSession(sessionId)

            FileInputStream(apkFile).use { input ->
                session.openWrite("package", 0, apkFile.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }

            // O commit é o que dispara a instalação no Android
            val intent = Intent(context, context::class.java) // Intent "dummy", pois não precisamos avisar nenhuma Activity
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            session.commit(pendingIntent.intentSender)
            Log.d(TAG, "Sessão de instalação comitada. O Android reiniciará o app em breve.")
            
        } catch (e: Exception) {
            Log.e(TAG, "Falha na instalação silenciosa: ${e.message}")
            session?.abandon()
        } finally {
            session?.close()
        }
    }

    // Compara se a nova versão (ex: v1.0.1) é maior que a atual (ex: 1.0)
    private fun isNewerVersion(current: String, latest: String): Boolean {
        val cleanCurrent = current.replace(Regex("[^0-9.]"), "")
        val cleanLatest = latest.replace(Regex("[^0-9.]"), "")
        
        val currentParts = cleanCurrent.split(".").map { it.toIntOrNull() ?: 0 }
        val latestParts = cleanLatest.split(".").map { it.toIntOrNull() ?: 0 }

        val length = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until length) {
            val c = currentParts.getOrElse(i) { 0 }
            val l = latestParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (c > l) return false
        }
        return false
    }
}