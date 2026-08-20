package ch.leftclick.piflash.domain.pios

import ch.leftclick.piflash.domain.model.PiConfiguration
import ch.leftclick.piflash.domain.model.SshAuthMode
import ch.leftclick.piflash.domain.model.WifiSecurity
import java.security.MessageDigest
import java.security.SecureRandom

class PiOsConfigurator {

    fun buildBootFiles(config: PiConfiguration): Map<String, ByteArray> {
        require(config.username.isNotBlank()) { "Username is required" }
        require(config.password.isNotBlank() || config.sshPublicKey.isNotBlank()) {
            "Provide a password or an SSH public key"
        }
        val files = linkedMapOf<String, ByteArray>()
        val hash = sha512Crypt(config.password.ifBlank { randomPassword() })
        files["userconf"] = "${config.username}:$hash\n".toByteArray()
        files["userconf.txt"] = files["userconf"]!!

        if (config.enableSsh) {
            files["ssh"] = ByteArray(0)
            files["ssh.txt"] = ByteArray(0)
        }
        if (config.enableWifi && config.wifiSsid.isNotBlank()) {
            files["wpa_supplicant.conf"] = wpaConf(config).toByteArray()
        }
        files["firstrun.sh"] = firstRun(config).toByteArray()
        return files
    }

    private fun wpaConf(c: PiConfiguration): String {
        val proto = when (c.wifiSecurity) {
            WifiSecurity.OPEN -> ""
            WifiSecurity.WPA3 -> "key_mgmt=SAE\n    ieee80211w=2"
            WifiSecurity.WPA2 -> "key_mgmt=WPA-PSK"
        }
        val psk = if (c.wifiSecurity == WifiSecurity.OPEN) "" else "    psk=\"${escape(c.wifiPassword)}\"\n"
        return buildString {
            appendLine("ctrl_interface=DIR=/var/run/wpa_supplicant GROUP=netdev")
            appendLine("update_config=1")
            appendLine("country=${c.country}")
            appendLine()
            appendLine("network={")
            appendLine("    ssid=\"${escape(c.wifiSsid)}\"")
            if (c.wifiHidden) appendLine("    scan_ssid=1")
            if (proto.isNotBlank()) appendLine("    $proto")
            append(psk)
            appendLine("}")
        }
    }

    private fun firstRun(c: PiConfiguration): String = buildString {
        appendLine("#!/bin/bash")
        appendLine("set +e")
        appendLine("LOG=/var/log/piflash-firstboot.log")
        appendLine("exec > >(tee -a \"\$LOG\") 2>&1")
        appendLine("echo \"[piflash] first-boot $(date -Is)\"")
        appendLine("hostnamectl set-hostname '${escape(c.hostname)}'")
        appendLine("echo '${escape(c.hostname)}' > /etc/hostname")
        appendLine("timedatectl set-timezone '${escape(c.timezone)}' || true")
        if (c.enableSsh) {
            appendLine("systemctl enable ssh")
            appendLine("systemctl start ssh")
            if ((c.sshAuthMode == SshAuthMode.KEY || c.sshAuthMode == SshAuthMode.BOTH) && c.sshPublicKey.isNotBlank()) {
                appendLine("install -d -m 700 -o ${c.username} -g ${c.username} /home/${c.username}/.ssh")
                appendLine("echo '${escape(c.sshPublicKey.trim())}' >> /home/${c.username}/.ssh/authorized_keys")
                appendLine("chown ${c.username}:${c.username} /home/${c.username}/.ssh/authorized_keys")
                appendLine("chmod 600 /home/${c.username}/.ssh/authorized_keys")
            }
            if (c.sshAuthMode == SshAuthMode.KEY) {
                appendLine("sed -i 's/^#*PasswordAuthentication.*/PasswordAuthentication no/' /etc/ssh/sshd_config")
                appendLine("systemctl reload ssh || systemctl restart ssh || true")
            }
        }

        val needsNetwork = c.aptUpdateUpgrade || c.installCoolify
        if (needsNetwork) {
            appendLine("mkdir -p /usr/local/bin /var/lib/piflash")
            appendLine("cat > /usr/local/bin/piflash-postboot.sh << 'PIFLASH_EOF'")
            appendLine("#!/bin/bash")
            appendLine("set +e")
            appendLine("LOG=/var/log/piflash-firstboot.log")
            appendLine("exec >>\"\$LOG\" 2>&1")
            appendLine("echo \"[piflash] post-boot started $(date -Is)\"")
            appendLine("# Wait for network (up to ~5 minutes)")
            appendLine("for i in \$(seq 1 60); do")
            appendLine("  if ping -c1 -W2 1.1.1.1 >/dev/null 2>&1 || ping -c1 -W2 8.8.8.8 >/dev/null 2>&1; then")
            appendLine("    echo \"[piflash] network is up\"")
            appendLine("    break")
            appendLine("  fi")
            appendLine("  sleep 5")
            appendLine("done")
            if (c.aptUpdateUpgrade) {
                appendLine("echo \"[piflash] apt update && upgrade\"")
                appendLine("export DEBIAN_FRONTEND=noninteractive")
                appendLine("apt-get update -y")
                appendLine("apt-get -y -o Dpkg::Options::=--force-confdef -o Dpkg::Options::=--force-confold upgrade")
                appendLine("echo \"[piflash] apt upgrade finished\"")
            }
            if (c.installCoolify) {
                appendLine("echo \"[piflash] installing Coolify\"")
                appendLine("# Official installer: installs Docker if needed, then Coolify")
                appendLine("curl -fsSL https://cdn.coollabs.io/coolify/install.sh | bash")
                appendLine("echo \"[piflash] Coolify install finished (UI on :8000)\"")
            }
            appendLine("rm -f /var/lib/piflash/postboot-pending")
            appendLine("systemctl disable piflash-postboot.service 2>/dev/null || true")
            appendLine("echo \"[piflash] post-boot done $(date -Is)\"")
            appendLine("PIFLASH_EOF")
            appendLine("chmod +x /usr/local/bin/piflash-postboot.sh")
            appendLine("touch /var/lib/piflash/postboot-pending")
            appendLine("cat > /etc/systemd/system/piflash-postboot.service << 'UNIT_EOF'")
            appendLine("[Unit]")
            appendLine("Description=PiFlash first-boot extras (apt / Coolify)")
            appendLine("After=network-online.target")
            appendLine("Wants=network-online.target")
            appendLine("ConditionPathExists=/var/lib/piflash/postboot-pending")
            appendLine("")
            appendLine("[Service]")
            appendLine("Type=oneshot")
            appendLine("ExecStart=/usr/local/bin/piflash-postboot.sh")
            appendLine("TimeoutStartSec=0")
            appendLine("RemainAfterExit=yes")
            appendLine("")
            appendLine("[Install]")
            appendLine("WantedBy=multi-user.target")
            appendLine("UNIT_EOF")
            appendLine("systemctl daemon-reload")
            appendLine("systemctl enable piflash-postboot.service")
            appendLine("# Kick it off if network is already up; otherwise it runs after next multi-user reach")
            appendLine("systemctl start piflash-postboot.service || true")
        }

        appendLine("rm -f /boot/firstrun.sh /boot/firmware/firstrun.sh")
        appendLine("echo \"[piflash] firstrun complete\"")
        appendLine("exit 0")
    }

    private fun escape(s: String) = s.replace("'", "'\\''").replace("\"", "\\\"")

    /** SHA-512 crypt ($6$) compatible enough for Raspberry Pi userconf. */
    fun sha512Crypt(password: String, salt: String = randomSalt()): String {
        val md = MessageDigest.getInstance("SHA-512")
        val pw = password.toByteArray()
        val sa = salt.toByteArray()
        md.update(pw)
        md.update(sa)
        md.update(pw)
        var digest = md.digest()
        repeat(5000) {
            md.reset()
            if (it % 2 == 0) md.update(pw) else md.update(digest)
            if (it % 3 != 0) md.update(sa)
            if (it % 7 != 0) md.update(pw)
            if (it % 2 == 0) md.update(digest) else md.update(pw)
            digest = md.digest()
        }
        return "\$6\$$salt\$${b64(digest)}"
    }

    private fun randomSalt(): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789./"
        val rnd = SecureRandom()
        return CharArray(16) { alphabet[rnd.nextInt(alphabet.length)] }.concatToString()
    }

    private fun randomPassword(): String {
        val rnd = SecureRandom()
        val alphabet = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return CharArray(20) { alphabet[rnd.nextInt(alphabet.length)] }.concatToString()
    }

    private fun b64(bytes: ByteArray): String {
        val table = "./0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
        val sb = StringBuilder()
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else 0
            val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else 0
            sb.append(table[b0 shr 2])
            sb.append(table[((b0 and 0x3) shl 4) or (b1 shr 4)])
            if (i + 1 < bytes.size) sb.append(table[((b1 and 0xf) shl 2) or (b2 shr 6)])
            if (i + 2 < bytes.size) sb.append(table[b2 and 0x3f])
            i += 3
        }
        return sb.toString()
    }
}
