package app.yume.android

import android.app.Application
import app.yume.android.data.Api
import app.yume.android.data.KV
import app.yume.android.data.Stores
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade

class YumeApplication : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        KV.init(this)
        Prefs.reload()
        Stores.loadAll()
    }

    /** Картинки идут через клиент с Referer сайта: без него страницы манги не загружаются. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { Api.imageClient })) }
            .crossfade(true)
            .build()
}
