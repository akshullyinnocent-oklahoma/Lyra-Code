package com.yukisoffd.lyracode

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.util.DisplayMetrics
import android.view.WindowManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

data class DeviceInfoItem(
    val label: String,
    val value: String,
)

data class DeviceInfoSection(
    val title: String,
    val items: List<DeviceInfoItem>,
)

data class DeviceInfoSnapshot(
    val sections: List<DeviceInfoSection>,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("schema", "lyra_device_info_v1")
        put(
            "sections",
            JSONArray().also { sectionArray ->
                sections.forEach { section ->
                    sectionArray.put(
                        JSONObject()
                            .put("title", section.title)
                            .put(
                                "items",
                                JSONArray().also { itemArray ->
                                    section.items.forEach { item ->
                                        itemArray.put(JSONObject().put("label", item.label).put("value", item.value))
                                    }
                                },
                            ),
                    )
                }
            },
        )
    }
}

object DeviceInfoCollector {
    fun collect(context: Context): DeviceInfoSnapshot {
        return DeviceInfoSnapshot(
            listOf(
                systemSection(),
                hardwareSection(context),
                storageSection(context),
                networkSection(context),
                batterySection(context),
            ),
        )
    }

    fun collectJson(context: Context): String = collect(context).toJson().toString(2)

    private fun systemSection(): DeviceInfoSection = DeviceInfoSection(
        "系统",
        listOf(
            DeviceInfoItem("厂商", Build.MANUFACTURER.orUnknown()),
            DeviceInfoItem("品牌", Build.BRAND.orUnknown()),
            DeviceInfoItem("型号", Build.MODEL.orUnknown()),
            DeviceInfoItem("设备代号", Build.DEVICE.orUnknown()),
            DeviceInfoItem("Android 版本", Build.VERSION.RELEASE.orUnknown()),
            DeviceInfoItem("Android SDK", Build.VERSION.SDK_INT.toString()),
            DeviceInfoItem("构建版本", Build.DISPLAY.orUnknown()),
            DeviceInfoItem("安全补丁", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH.orUnknown() else "不支持"),
        ),
    )

    private fun hardwareSection(context: Context): DeviceInfoSection {
        val display = displayInfo(context)
        return DeviceInfoSection(
            "硬件",
            listOf(
                DeviceInfoItem("CPU", cpuInfo()),
                DeviceInfoItem("CPU 核心数", Runtime.getRuntime().availableProcessors().toString()),
                DeviceInfoItem("ABI", Build.SUPPORTED_ABIS.joinToString(", ").ifBlank { "未知" }),
                DeviceInfoItem("内存", memoryInfo(context)),
                DeviceInfoItem("分辨率", display.first),
                DeviceInfoItem("屏幕密度", display.second),
            ),
        )
    }

    private fun storageSection(context: Context): DeviceInfoSection {
        val items = mutableListOf<DeviceInfoItem>()
        items += DeviceInfoItem("内部存储", storageText(Environment.getDataDirectory()))
        val primaryExternal = Environment.getExternalStorageDirectory()
        if (primaryExternal.exists()) {
            items += DeviceInfoItem("共享存储", storageText(primaryExternal))
        }
        val storageManager = context.getSystemService(StorageManager::class.java)
        runCatching {
            storageManager.storageVolumes.forEachIndexed { index, volume ->
                val directory = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) volume.directory else null
                val label = buildString {
                    append(
                        when {
                            volume.isPrimary -> "主存储"
                            volume.isRemovable -> "外部存储"
                            else -> "存储卷"
                        },
                    )
                    append(" ${index + 1}")
                    val description = runCatching { volume.getDescription(context) }.getOrNull().orEmpty()
                    if (description.isNotBlank()) append(" · ").append(description)
                }
                val value = if (directory != null && directory.exists()) {
                    storageText(directory)
                } else {
                    "路径不可直接读取；uuid=${volume.uuid ?: "无"}，removable=${volume.isRemovable}"
                }
                if (items.none { it.value == value }) {
                    items += DeviceInfoItem(label, value)
                }
            }
        }
        return DeviceInfoSection("存储", items)
    }

    private fun networkSection(context: Context): DeviceInfoSection {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val caps = connectivity?.getNetworkCapabilities(connectivity.activeNetwork)
        val networkType = when {
            caps == null -> "未连接或无网络状态权限"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "移动网络"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "以太网"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "蓝牙网络"
            else -> "其他"
        }
        val bluetooth = runCatching {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            when {
                adapter == null -> "设备不支持蓝牙"
                adapter.isEnabled -> "已开启"
                else -> "未开启"
            }
        }.getOrElse { "无法读取：${it.javaClass.simpleName}" }
        return DeviceInfoSection(
            "连接",
            listOf(
                DeviceInfoItem("网络", networkType),
                DeviceInfoItem("蓝牙", bluetooth),
            ),
        )
    }

    private fun batterySection(context: Context): DeviceInfoSection {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) "${level * 100 / scale}%" else "未知"
        val status = when (battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "充电中"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "放电中"
            BatteryManager.BATTERY_STATUS_FULL -> "已充满"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "未充电"
            else -> "未知"
        }
        val plugged = when (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "无线"
            else -> "未连接"
        }
        return DeviceInfoSection(
            "电池",
            listOf(
                DeviceInfoItem("电量", percent),
                DeviceInfoItem("状态", status),
                DeviceInfoItem("供电", plugged),
            ),
        )
    }

    private fun displayInfo(context: Context): Pair<String, String> {
        val metrics = DisplayMetrics()
        val windowManager = context.getSystemService(WindowManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && windowManager != null) {
            val bounds = windowManager.currentWindowMetrics.bounds
            context.display?.getRealMetrics(metrics)
            return "${bounds.width()} × ${bounds.height()}" to "${metrics.densityDpi} dpi / ${String.format(Locale.US, "%.2f", metrics.density)}x"
        }
        @Suppress("DEPRECATION")
        windowManager?.defaultDisplay?.getRealMetrics(metrics)
        return "${metrics.widthPixels} × ${metrics.heightPixels}" to "${metrics.densityDpi} dpi / ${String.format(Locale.US, "%.2f", metrics.density)}x"
    }

    private fun memoryInfo(context: Context): String {
        val activityManager = context.getSystemService(android.app.ActivityManager::class.java)
        val info = android.app.ActivityManager.MemoryInfo()
        return runCatching {
            activityManager.getMemoryInfo(info)
            "${formatBytes(info.availMem)} 可用 / ${formatBytes(info.totalMem)} 总计"
        }.getOrDefault("未知")
    }

    private fun cpuInfo(): String {
        val text = runCatching { File("/proc/cpuinfo").readText().lineSequence().take(80).joinToString("\n") }.getOrDefault("")
        val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL.cleanCpuValue() else null
        val socManufacturer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MANUFACTURER.cleanCpuValue() else null
        val hardware = cpuInfoField(text, "Hardware")
        val modelName = cpuInfoField(text, "model name")
        val processor = cpuInfoField(text, "Processor")?.takeUnless { it.toIntOrNull() != null }
        val buildHardware = Build.HARDWARE.cleanCpuValue()
        val primaryId = listOfNotNull(socModel, hardware, buildHardware, modelName, processor).firstOrNull()
        val friendlyName = primaryId?.let(::friendlyChipName)
        return listOfNotNull(
            friendlyName,
            socManufacturer?.takeUnless { friendlyName?.contains(it, ignoreCase = true) == true },
            primaryId?.takeUnless { it.equals(friendlyName, ignoreCase = true) },
        )
            .distinct()
            .joinToString(" · ")
            .ifBlank { buildHardware.orUnknown() }
    }

    private fun cpuInfoField(text: String, key: String): String? {
        return Regex("""(?im)^${Regex.escape(key)}\s*:\s*(.+)$""")
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.cleanCpuValue()
    }

    private fun friendlyChipName(raw: String): String {
        val normalized = raw.lowercase(Locale.US)
            .replace("""[\s_-]+""".toRegex(), "")
        return when {
            normalized.contains("sm8750") || normalized.contains("volcano") -> "Snapdragon 8 Elite"
            normalized.contains("sm8650") || normalized.contains("pineapple") -> "Snapdragon 8 Gen 3"
            normalized.contains("sm8550") || normalized.contains("kalama") -> "Snapdragon 8 Gen 2"
            normalized.contains("sm8475") -> "Snapdragon 8+ Gen 1"
            normalized.contains("sm8450") || normalized.contains("taro") -> "Snapdragon 8 Gen 1"
            normalized.contains("sm8350") || normalized.contains("lahaina") -> "Snapdragon 888"
            normalized.contains("sm8250") || normalized.contains("kona") -> "Snapdragon 865"
            normalized.contains("mt6991") -> "Dimensity 9400"
            normalized.contains("mt6989") -> "Dimensity 9300"
            normalized.contains("mt6985") -> "Dimensity 9200"
            normalized.contains("mt6983") -> "Dimensity 9000"
            normalized.contains("mt6897") -> "Dimensity 8300"
            normalized.contains("mt6896") -> "Dimensity 8200"
            normalized.contains("mt6895") -> "Dimensity 8100/8000"
            normalized.contains("exynos2400") || normalized.contains("s5e9945") -> "Exynos 2400"
            normalized.contains("exynos2200") || normalized.contains("s5e9925") -> "Exynos 2200"
            normalized.contains("gs101") -> "Google Tensor"
            normalized.contains("gs201") -> "Google Tensor G2"
            normalized.contains("zuma") || normalized.contains("gs301") -> "Google Tensor G3"
            normalized.contains("zumapro") || normalized.contains("gs401") -> "Google Tensor G4"
            else -> raw
        }
    }

    private fun storageText(path: File): String {
        return runCatching {
            val stat = StatFs(path.absolutePath)
            val total = stat.blockSizeLong * stat.blockCountLong
            val available = stat.blockSizeLong * stat.availableBlocksLong
            "${formatBytes(available)} 可用 / ${formatBytes(total)} 总计 · ${path.absolutePath}"
        }.getOrElse { "无法读取：${path.absolutePath}" }
    }

    private fun String?.orUnknown(): String = this?.takeIf { it.isNotBlank() } ?: "未知"

    private fun String?.cleanCpuValue(): String? {
        return this
            ?.trim()
            ?.trim('\u0000')
            ?.takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }
    }
}
