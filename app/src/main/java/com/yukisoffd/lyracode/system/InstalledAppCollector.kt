package com.yukisoffd.lyracode.system

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

object InstalledAppCollector {
    fun collect(
        context: Context,
        scope: String,
        query: String,
        offset: Int,
        limit: Int,
    ): String {
        val packageManager = context.packageManager
        val packages = installedPackages(packageManager)
            .asSequence()
            .mapNotNull { info -> appJson(packageManager, info) }
            .filter { item ->
                when (scope.lowercase()) {
                    "user" -> !item.optBoolean("system_app")
                    "system" -> item.optBoolean("system_app")
                    else -> true
                }
            }
            .filter { item ->
                query.isBlank() ||
                    item.optString("name").contains(query, ignoreCase = true) ||
                    item.optString("package_name").contains(query, ignoreCase = true)
            }
            .sortedBy { it.optString("name").lowercase() }
            .toList()
        val safeOffset = offset.coerceAtLeast(0).coerceAtMost(packages.size)
        val safeLimit = limit.coerceIn(1, 500)
        val page = packages.drop(safeOffset).take(safeLimit)
        return JSONObject()
            .put("schema", "lyra_installed_apps_v1")
            .put("scope", scope.ifBlank { "all" })
            .put("query", query)
            .put("total", packages.size)
            .put("offset", safeOffset)
            .put("returned", page.size)
            .put("has_more", safeOffset + page.size < packages.size)
            .put("apps", JSONArray(page))
            .toString()
    }

    private fun installedPackages(packageManager: PackageManager): List<PackageInfo> {
        val flags = PackageManager.GET_SIGNING_CERTIFICATES.toLong()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledPackages(flags.toInt())
        }
    }

    private fun appJson(packageManager: PackageManager, info: PackageInfo): JSONObject? {
        val appInfo = info.applicationInfo ?: return null
        val systemApp = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0 ||
            appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
        val apkSize = buildList {
            add(appInfo.sourceDir)
            addAll(appInfo.splitSourceDirs.orEmpty())
        }.sumOf { path -> runCatching { File(path).length() }.getOrDefault(0L) }
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners.orEmpty().map { signature -> sha256(signature.toByteArray()) }
        } else {
            @Suppress("DEPRECATION")
            info.signatures.orEmpty().map { signature -> sha256(signature.toByteArray()) }
        }.distinct()
        return JSONObject()
            .put("name", packageManager.getApplicationLabel(appInfo).toString())
            .put("package_name", info.packageName)
            .put("version_name", info.versionName.orEmpty())
            .put("version_code", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            })
            .put("system_app", systemApp)
            .put("apk_size_bytes", apkSize)
            .put("signature_sha256", JSONArray(signatures))
    }

    private fun sha256(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(":") { "%02X".format(it) }
    }
}
