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
        app.yume.android.data.AuthStore.init(this)
        app.yume.android.data.FriendsModel.init()
        app.yume.android.data.ProfileLook.init(this)
        app.yume.android.data.FolderStyles.load()
        app.yume.android.data.OfflineStore.init(this)
        app.yume.android.data.DownloadManager.init(this)
    }

    /** Картинки идут через клиент с Referer сайта: без него страницы манги не загружаются. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { Api.imageClient }))
                if (android.os.Build.VERSION.SDK_INT >= 28) add(coil3.gif.AnimatedImageDecoder.Factory())
                else add(coil3.gif.GifDecoder.Factory())
            }
            .crossfade(true)
            .build()
}
