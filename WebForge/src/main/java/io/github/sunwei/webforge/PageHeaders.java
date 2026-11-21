package io.github.sunwei.webforge;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** 请求头按 HTTP(S) 的 scheme、host 和 port 作用域保存。 */
final class PageHeaders {
    private PageHeaders() {}

    static String origin(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException malformed) {
            return null;
        }
        String scheme = uri.getScheme();
        if (scheme == null) {
            return null;
        }
        scheme = scheme.toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme) && !"http".equals(scheme)) {
            return null;
        }
        String host = uri.getHost();
        if (host == null || host.isEmpty() || uri.getUserInfo() != null) {
            return null;
        }
        int port = uri.getPort();
        if (port == ("https".equals(scheme) ? 443 : 80)) {
            port = -1;
        }
        return scheme + "://" + host.toLowerCase(Locale.ROOT) + (port == -1 ? "" : ":" + port);
    }

    static Map<String, String> copy(Map<String, String> headers) {
        if (headers.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> copy = new HashMap<>(headers.size());
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            if (name == null || name.isEmpty() || value == null
                    || name.indexOf(':') >= 0 || name.indexOf('\r') >= 0 || name.indexOf('\n') >= 0
                    || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
                throw new IllegalArgumentException("Invalid HTTP header");
            }
            copy.put(name, value);
        }
        return Collections.unmodifiableMap(copy);
    }
}
