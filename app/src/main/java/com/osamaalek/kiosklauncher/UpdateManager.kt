package com.osamaalek.kiosklauncher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object UpdateManager {

    private const val TAG = "UpdateManager"
    // URL do version.json gerado pelo GitHub Actions (Modifique para o nome do SEU usuário no GitHub)
    private const val VERSION_URL = "https://github.com/matheusdem/NPS-apk/releases/latest/download/version.json"
    
    /**
     * Verifica no GitHub se há uma versão mais nova. 
     * Se houver, baixa e pede ao usuário para instalar via FileProvider.
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
                        Log.d(TAG, "Download concluído. Solicitando instalação ao usuário...")
                        promptInstall(context, apkFile)
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
            connection.instanceFollowRedirects = true

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val jsonResponse = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(jsonResponse)
                val version = jsonObject.optString("version")
                val apkUrl = jsonObject.optString("apkUrl")
                return Pair(version.takeIf { it.isNotEmpty() }, apkUrl.takeIf { it.isNotEmpty() })
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
            
            var redirect = false
            val status = connection.responseCode
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
                // Salvamos na pasta cache do aplicativo, que o FileProvider tem acesso
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

    /**
     * Utiliza um Intent com FileProvider para pedir ao Android que instale o APK.
     * Como não somos Device Owner, isso abrirá a tela do sistema pedindo confirmação.
     */
    private fun promptInstall(context: Context, apkFile: File) {
        try {
            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            context.startActivity(intent)
            Log.d(TAG, "Tela de instalação chamada com sucesso.")
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao chamar tela de instalação: ${e.message}")
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