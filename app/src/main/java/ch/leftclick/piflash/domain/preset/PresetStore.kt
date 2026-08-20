package ch.leftclick.piflash.domain.preset

import ch.leftclick.piflash.domain.model.ConfigPreset
import ch.leftclick.piflash.domain.model.PiConfiguration
import ch.leftclick.piflash.domain.model.SshAuthMode
import ch.leftclick.piflash.domain.model.WifiSecurity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class PresetStore(filesDir: File) {
    private val file = File(filesDir, "presets.json")

    @Synchronized
    fun load(): Pair<List<ConfigPreset>, PiConfiguration?> {
        if (!file.exists()) return emptyList<ConfigPreset>() to null
        return runCatching {
            val root = JSONObject(file.readText())
            val presets = root.optJSONArray("presets") ?: JSONArray()
            val list = buildList {
                for (i in 0 until presets.length()) {
                    add(presetFrom(presets.getJSONObject(i)))
                }
            }
            val last = if (root.has("lastConfig") && !root.isNull("lastConfig")) {
                configFrom(root.getJSONObject("lastConfig"))
            } else {
                null
            }
            list.sortedBy { it.name.lowercase() } to last
        }.getOrElse { emptyList<ConfigPreset>() to null }
    }

    @Synchronized
    fun save(presets: List<ConfigPreset>, lastConfig: PiConfiguration?) {
        val root = JSONObject()
        val arr = JSONArray()
        presets.filter { !it.builtIn }.forEach { arr.put(presetTo(it)) }
        root.put("presets", arr)
        if (lastConfig != null) root.put("lastConfig", configTo(lastConfig))
        file.parentFile?.mkdirs()
        file.writeText(root.toString(2))
    }

    private fun presetTo(p: ConfigPreset) = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        put("savedAt", p.savedAt)
        put("config", configTo(p.config))
    }

    private fun presetFrom(o: JSONObject) = ConfigPreset(
        id = o.getString("id"),
        name = o.getString("name"),
        savedAt = o.optLong("savedAt", 0L),
        builtIn = false,
        config = configFrom(o.getJSONObject("config"))
    )

    private fun configTo(c: PiConfiguration) = JSONObject().apply {
        put("hostname", c.hostname)
        put("username", c.username)
        put("password", c.password)
        put("enableSsh", c.enableSsh)
        put("sshAuthMode", c.sshAuthMode.name)
        put("sshPublicKey", c.sshPublicKey)
        put("enableWifi", c.enableWifi)
        put("wifiSsid", c.wifiSsid)
        put("wifiPassword", c.wifiPassword)
        put("wifiHidden", c.wifiHidden)
        put("wifiSecurity", c.wifiSecurity.name)
        put("country", c.country)
        put("timezone", c.timezone)
        put("locale", c.locale)
        put("keyboardLayout", c.keyboardLayout)
        put("aptUpdateUpgrade", c.aptUpdateUpgrade)
        put("installCoolify", c.installCoolify)
    }

    private fun configFrom(o: JSONObject) = PiConfiguration(
        hostname = o.optString("hostname", "raspberrypi"),
        username = o.optString("username", "pi"),
        password = o.optString("password", ""),
        enableSsh = o.optBoolean("enableSsh", true),
        sshAuthMode = runCatching {
            SshAuthMode.valueOf(o.optString("sshAuthMode", SshAuthMode.PASSWORD.name))
        }.getOrDefault(SshAuthMode.PASSWORD),
        sshPublicKey = o.optString("sshPublicKey", ""),
        enableWifi = o.optBoolean("enableWifi", true),
        wifiSsid = o.optString("wifiSsid", ""),
        wifiPassword = o.optString("wifiPassword", ""),
        wifiHidden = o.optBoolean("wifiHidden", false),
        wifiSecurity = runCatching {
            WifiSecurity.valueOf(o.optString("wifiSecurity", WifiSecurity.WPA2.name))
        }.getOrDefault(WifiSecurity.WPA2),
        country = o.optString("country", "CH"),
        timezone = o.optString("timezone", "Europe/Zurich"),
        locale = o.optString("locale", "en_GB.UTF-8"),
        keyboardLayout = o.optString("keyboardLayout", "ch"),
        aptUpdateUpgrade = o.optBoolean("aptUpdateUpgrade", false),
        installCoolify = o.optBoolean("installCoolify", false)
    )
}
