package com.rafsan.torterm

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var out: TextView
    private lateinit var scroll: ScrollView
    private val orbot = "org.torproject.android"
    private val warpPkg = "com.cloudflare.onedotonedotonedotone"
    private val green = Color.parseColor("#33FF66")
    private val dim = Color.parseColor("#1F9E40")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.BLACK)
        root.setPadding(24, 64, 24, 24)

        scroll = ScrollView(this)
        out = TextView(this)
        out.setTextColor(green)
        out.typeface = Typeface.MONOSPACE
        out.textSize = 13f
        out.setTextIsSelectable(true)
        scroll.addView(out)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val input = EditText(this)
        input.setTextColor(green)
        input.setHintTextColor(dim)
        input.hint = "torterm$ type help"
        input.typeface = Typeface.MONOSPACE
        input.setSingleLine(true)
        input.imeOptions = EditorInfo.IME_ACTION_SEND
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        input.setBackgroundColor(Color.parseColor("#001A08"))
        input.setPadding(24, 24, 24, 24)
        root.addView(input, LinearLayout.LayoutParams(-1, -2))

        setContentView(root)

        input.setOnEditorActionListener { v, _, _ ->
            val cmd = v.text.toString().trim()
            v.text = ""
            if (cmd.isNotEmpty()) {
                out.append("$ $cmd\n")
                handle(cmd)
            }
            true
        }

        out.append("TORTERM v1.0  //  tor + 1.1.1.1 terminal\n")
        out.append("--------------------------------------\n")
        help()
    }

    private fun ts(): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

    private fun log(msg: String) {
        runOnUiThread {
            out.append("[${ts()}] $msg\n")
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun help() {
        out.append(
            "commands:\n" +
                "  connect      start Orbot (Tor), then pick VPN mode + exit country in Orbot\n" +
                "  status       show current exit IP / country / Tor check\n" +
                "  dns <name>   look up a domain via 1.1.1.1 (through Tor if running)\n" +
                "  warp         open the 1.1.1.1 WARP app\n" +
                "  clear        clear screen\n" +
                "  exit         close\n"
        )
    }

    private fun handle(cmd: String) {
        val p = cmd.split(" ").filter { it.isNotEmpty() }
        when (p[0].lowercase()) {
            "help" -> help()
            "clear" -> { out.text = "" }
            "connect" -> connect()
            "status" -> status()
            "dns" -> { if (p.size > 1) dns(p[1]) else log("usage: dns <domain>") }
            "warp" -> openApp(warpPkg)
            "exit" -> finish()
            else -> log("unknown command: ${p[0]} (type help)")
        }
    }

    private fun installed(pkg: String): Boolean {
        return try {
            packageManager.getPackageInfo(pkg, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun openStore(pkg: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))
        } catch (e: ActivityNotFoundException) {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
            )
        }
    }

    private fun openApp(pkg: String) {
        val launch = packageManager.getLaunchIntentForPackage(pkg)
        if (installed(pkg) && launch != null) {
            log("opening $pkg")
            startActivity(launch)
        } else {
            log("$pkg is not installed. Opening store...")
            openStore(pkg)
        }
    }

    private fun connect() {
        if (!installed(orbot)) {
            log("Orbot is not installed. Opening store (F-Droid also has it)...")
            openStore(orbot)
            return
        }
        log("asking Orbot to start Tor...")
        val i = Intent("org.torproject.android.intent.action.START")
        i.setPackage(orbot)
        i.putExtra("org.torproject.android.intent.extra.PACKAGE_NAME", packageName)
        sendBroadcast(i)
        log("opening Orbot. Tap Start, turn on VPN mode, choose exit country.")
        log("come back here and type: status")
        packageManager.getLaunchIntentForPackage(orbot)?.let { startActivity(it) }
    }

    private fun fetch(url: String, proxy: Boolean, accept: String = "*/*"): String {
        val conn = if (proxy) {
            URL(url).openConnection(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 9050)))
        } else {
            URL(url).openConnection()
        }
        val h = conn as HttpURLConnection
        h.connectTimeout = 20000
        h.readTimeout = 25000
        h.setRequestProperty("Accept", accept)
        return h.inputStream.bufferedReader().use { it.readText() }
    }

    private fun status() {
        log("checking exit node through Orbot (127.0.0.1:9050)...")
        Thread {
            try {
                val j = JSONObject(fetch("https://check.torproject.org/api/ip", true))
                val ip = j.getString("IP")
                val isTor = j.getBoolean("IsTor")
                val cc = try {
                    fetch("https://ipinfo.io/$ip/country", true).trim()
                } catch (e: Exception) {
                    "?"
                }
                log("exit ip: $ip   country: $cc   tor: $isTor")
                if (!isTor) log("WARN: this traffic is not using Tor")
            } catch (e: Exception) {
                log("ERR: cannot reach Tor. Is Orbot running? (type connect)")
            }
        }.start()
    }

    private fun dns(domain: String) {
        log("querying 1.1.1.1 for $domain ...")
        Thread {
            val dnsType = "application/dns-json"
            val url = "https://1.1.1.1/dns-query?name=$domain&type=A"
            val body = try {
                fetch(url, true, dnsType)
            } catch (e: Exception) {
                try {
                    log("Tor proxy unavailable, querying directly")
                    fetch(url, false, dnsType)
                } catch (e2: Exception) {
                    null
                }
            }
            if (body == null) {
                log("ERR: DNS lookup failed")
                return@Thread
            }
            val answers = JSONObject(body).optJSONArray("Answer")
            var found = false
            if (answers != null) {
                for (n in 0 until answers.length()) {
                    val a = answers.getJSONObject(n)
                    if (a.optInt("type") == 1) {
                        log("$domain -> ${a.getString("data")}")
                        found = true
                    }
                }
            }
            if (!found) log("$domain: no A records")
        }.start()
    }
}
