package io.github.sunwei.webforge

import java.net.URI
import java.net.URISyntaxException
import java.util.Collections

/** 请求头按 HTTP(S) 的 scheme、host 和 port 作用域保存。  */
internal object PageHeaders {
    @JvmStatic
    fun origin(url: String?): String? {
        val uri: URI?
        try {
            uri = URI(url)
        } catch (malformed: URISyntaxException) {
            return null
        }
        var scheme = uri.getScheme()
        if (scheme == null) {
            return null
        }
        scheme = scheme.lowercase()
        if ("https" != scheme && "http" != scheme) {
            return null
        }
        val host = uri.getHost()
        if (host == null || host.isEmpty() || uri.getUserInfo() != null) {
            return null
        }
        var port = uri.getPort()
        if (port == (if ("https" == scheme) 443 else 80)) {
            port = -1
        }
        return scheme + "://" + host.lowercase() + (if (port == -1) "" else ":" + port)
    }

    fun copy(headers: MutableMap<String?, String?>): MutableMap<String?, String?> {
        if (headers.isEmpty()) {
            return mutableMapOf<String?, String?>()
        }
        val copy: MutableMap<String?, String?> = HashMap<String?, String?>(headers.size)
        for (entry in headers.entries) {
            val name: String = entry.key!!
            val value: String = entry.value!!
            require(
                !(name == null || name.isEmpty() || value == null || name.indexOf(':') >= 0 || name.indexOf('\r') >= 0 || name.indexOf('\n') >= 0 || value.indexOf('\r') >= 0 || value.indexOf(
                    '\n'
                ) >= 0)
            ) { "Invalid HTTP header" }
            copy.put(name, value)
        }
        return Collections.unmodifiableMap<String?, String?>(copy)
    }
}
