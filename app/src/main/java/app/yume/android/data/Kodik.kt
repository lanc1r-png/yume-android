package app.yume.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject
import java.net.URI
import android.util.Base64

class KodikException(msg: String) : Exception(msg)

/** Прямое получение ссылок Kodik (без веб-плеера). Повторяет логику iOS-версии. */
object Kodik {
    const val UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/124.0.0.0 Mobile Safari/537.36"

    /** UA из iOS-версии: на нём прямой плеер работает; пробуем его первым, потом Android. */
    private const val UA_IOS = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 " +
        "(KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"

    /** Куки живут в памяти на время одного запроса: страница плеера ставит их, /ftor может их ждать. */
    private class MemoryJar : okhttp3.CookieJar {
        private val store = HashMap<String, MutableList<okhttp3.Cookie>>()
        @Synchronized
        override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
            val list = store.getOrPut(url.host) { ArrayList() }
            for (c in cookies) {
                list.removeAll { it.name == c.name }
                list.add(c)
            }
        }
        @Synchronized
        override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> = store[url.host]?.toList().orEmpty()
    }

    private fun match(pattern: String, s: String): String? =
        Regex(pattern, RegexOption.DOT_MATCHES_ALL).find(s)?.groupValues?.getOrNull(1)

    private suspend fun fetch(client: okhttp3.OkHttpClient, ua: String, url: String, referer: String): String =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url).header("Referer", referer).header("User-Agent", ua).build()
            val t0 = System.currentTimeMillis()
            client.newCall(req).execute().use {
                RequestLog.add("GET", "kodik " + url.substringBefore("?").takeLast(60) + " [" + ua.takeLast(12) + "]", it.code, System.currentTimeMillis() - t0)
                it.body?.string() ?: throw KodikException("не открылась страница плеера")
            }
        }

    @Volatile
    private var appContext: android.content.Context? = null

    fun init(context: android.content.Context) {
        appContext = context.applicationContext
    }

    suspend fun resolve(pageUrl: String): List<Pair<Int, String>> {
        var last: KodikException? = null
        for (ua in listOf(UA_IOS, UA)) {
            try {
                return resolveOnce(pageUrl, ua)
            } catch (e: KodikException) {
                last = e
            }
        }
        // Запрос «изнутри» настоящей страницы плеера: те же куки, TLS и подписи, что у браузера.
        try {
            return resolveViaWebView(pageUrl)
        } catch (e: KodikException) {
            throw KodikException((last?.message ?: "") + "; в браузере: " + e.message)
        }
    }

    private const val WEB_JS = """
(function(){
 function out(s){ yume.done(s); }
 var tries=0;
 function go(){
  try{
   if(typeof urlParams==='undefined'||typeof videoInfo==='undefined'){
     if(tries++<40){ setTimeout(go,250); return; }
     out('ERR|нет urlParams/videoInfo'); return;
   }
   var p = (typeof urlParams==='string') ? JSON.parse(urlParams) : urlParams;
   var path='/ftor';
   var sc=document.querySelector('script[src*="app.player"]');
   var pre = sc ? fetch(sc.src).then(function(r){return r.text();}).then(function(js){
       var m=js.match(/url:\s*atob\(\s*["']([^"']+)["']\s*\)/);
       if(m){ var d=atob(m[1]); if(d.charAt(0)==='/') path=d; }
     }).catch(function(){}) : Promise.resolve();
   pre.then(function(){
     var b=new URLSearchParams();
     ['d','d_sign','pd','pd_sign','ref','ref_sign'].forEach(function(k){ b.append(k, p[k]||''); });
     b.append('bad_user','true'); b.append('cdn_is_working','true');
     b.append('type',videoInfo.type); b.append('hash',videoInfo.hash); b.append('id',videoInfo.id); b.append('info','{}');
     return fetch(path,{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded; charset=UTF-8','X-Requested-With':'XMLHttpRequest'},body:b.toString()});
   }).then(function(r){ return r.text().then(function(t){ out(r.status+'|'+t); }); })
   .catch(function(e){ out('ERR|'+e); });
  }catch(e){ out('ERR|'+e); }
 }
 go();
})();
"""

    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    private suspend fun resolveViaWebView(pageUrl: String): List<Pair<Int, String>> {
        val ctx = appContext ?: throw KodikException("нет контекста")
        val raw = withContext(Dispatchers.Main) {
            kotlinx.coroutines.suspendCancellableCoroutine<String> { cont ->
                val handler = android.os.Handler(android.os.Looper.getMainLooper())
                val wv = android.webkit.WebView(ctx)
                var finished = false
                var started = false
                fun finish(res: Result<String>) {
                    if (finished) return
                    finished = true
                    handler.removeCallbacksAndMessages(null)
                    wv.stopLoading()
                    wv.destroy()
                    if (cont.isActive) cont.resumeWith(res)
                }
                wv.settings.javaScriptEnabled = true
                wv.settings.domStorageEnabled = true
                wv.settings.userAgentString = UA
                wv.addJavascriptInterface(object {
                    @android.webkit.JavascriptInterface
                    fun done(s: String) {
                        handler.post { finish(Result.success(s)) }
                    }
                }, "yume")
                wv.webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: android.webkit.WebView, url: String?) {
                        if (started) return
                        started = true
                        view.evaluateJavascript(WEB_JS, null)
                    }
                }
                handler.postDelayed({ finish(Result.failure(KodikException("таймаут"))) }, 25000)
                cont.invokeOnCancellation { handler.post { if (!finished) { finished = true; wv.destroy() } } }
                wv.loadUrl(pageUrl, mapOf("Referer" to "https://animelib.org/"))
            }
        }
        val code = raw.substringBefore("|")
        val text = raw.substringAfter("|", "")
        if (code == "ERR") throw KodikException(text.take(100))
        return linksFrom("[$code] $text")
    }

    private suspend fun resolveOnce(pageUrl: String, ua: String): List<Pair<Int, String>> {
        val client = Api.httpClient.newBuilder().cookieJar(MemoryJar()).build()
        val html = fetch(client, ua, pageUrl, "https://animelib.org/")
        val params = HashMap<String, String>()
        for (k in listOf("d", "d_sign", "pd", "pd_sign", "ref", "ref_sign")) {
            // значение может содержать экранированные слэши (https:\/\/site\/), их нельзя обрезать
            match("\\\\?[\"']" + k + "\\\\?[\"']\\s*:\\s*\\\\?[\"']((?:[^\"'\\\\]|\\\\/)*)", html)
                ?.let { params[k] = it.replace("\\/", "/") }
        }
        if (params["d_sign"] == null || params["pd_sign"] == null) throw KodikException("ответ Kodik: нет параметров плеера")

        val uri = URI(pageUrl)
        val host = uri.host ?: "kodikplayer.com"
        val parts = uri.path.split("/").filter { it.isNotEmpty() }
        val type = match("videoInfo\\.type\\s*=\\s*'([^']+)'", html) ?: parts.getOrNull(0) ?: ""
        val id = match("videoInfo\\.id\\s*=\\s*'?([0-9]+)'?", html) ?: parts.getOrNull(1) ?: ""
        val hash = match("videoInfo\\.hash\\s*=\\s*'([^']+)'", html) ?: parts.getOrNull(2) ?: ""
        if (type.isEmpty() || id.isEmpty() || hash.isEmpty()) throw KodikException("не найдены параметры плеера")

        var path = "/ftor"
        try {
            val script = match("<script[^>]+src=\"(/assets/js/app\\.player[^\"]+)\"", html)
            if (script != null) {
                val js = fetch(client, ua, "https://$host$script", pageUrl)
                val b64 = match("url:\\s*atob\\(\\s*[\"']([^\"']+)[\"']\\s*\\)", js)
                if (b64 != null) {
                    val p = String(Base64.decode(b64, Base64.DEFAULT))
                    if (p.startsWith("/")) path = p
                }
            }
        } catch (e: Exception) {
            // остаёмся на /ftor
        }

        val body = FormBody.Builder()
        for (k in listOf("d", "d_sign", "pd", "pd_sign", "ref", "ref_sign")) body.add(k, params[k] ?: "")
        body.add("bad_user", "true").add("cdn_is_working", "true")
            .add("type", type).add("hash", hash).add("id", id).add("info", "{}")

        val text = withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("https://$host$path")
                .post(body.build())
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Origin", "https://$host")
                .header("Referer", pageUrl)
                .header("User-Agent", ua)
                .build()
            val t0 = System.currentTimeMillis()
            client.newCall(req).execute().use {
                RequestLog.add("POST", "kodik $host$path [" + ua.takeLast(12) + "]", it.code, System.currentTimeMillis() - t0)
                "[" + it.code + "] " + it.body?.string().orEmpty()
            }
        }
        return linksFrom(text)
    }

    /** Разбирает ответ «[код] json» и расшифровывает ссылки. */
    private fun linksFrom(text: String): List<Pair<Int, String>> {
        val links = try {
            JSONObject(text.substringAfter("] ")).optJSONObject("links")
        } catch (e: Exception) {
            null
        } ?: throw KodikException("ответ Kodik " + text.replace("\n", " ").take(100))

        val result = ArrayList<Pair<Int, String>>()
        val it = links.keys()
        while (it.hasNext()) {
            val q = it.next()
            val quality = q.toIntOrNull() ?: continue
            val src = links.optJSONArray(q)?.optJSONObject(0)?.optString("src") ?: continue
            var dec = decode(src) ?: throw KodikException("не удалось расшифровать ссылку")
            if (dec.startsWith("//")) dec = "https:$dec"
            result.add(quality to dec)
        }
        if (result.isEmpty()) throw KodikException("плеер не вернул ссылки на видео")
        return result.sortedByDescending { it.first }
    }

    /** Ссылка зашифрована сдвигом букв (ROT-N) и base64; перебираем сдвиг. */
    fun decode(s: String): String? {
        if (s.startsWith("http") || s.startsWith("//")) return s
        for (shift in 0 until 26) {
            val out = StringBuilder()
            for (c in s) {
                when (c) {
                    in 'A'..'Z' -> out.append('A' + (c - 'A' + shift) % 26)
                    in 'a'..'z' -> out.append('a' + (c - 'a' + shift) % 26)
                    else -> out.append(c)
                }
            }
            var b = out.toString().replace('-', '+').replace('_', '/')
            while (b.length % 4 != 0) b += "="
            try {
                val t = String(Base64.decode(b, Base64.DEFAULT))
                if (t.startsWith("http") || t.startsWith("//")) return t
            } catch (e: IllegalArgumentException) {
                // не тот сдвиг
            }
        }
        return null
    }
}
