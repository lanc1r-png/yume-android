package app.yume.android.data

import app.yume.android.Prefs

/** Сайт сети Lib, с которым сейчас работает приложение. */
enum class Site(
    val key: String,
    val id: String,
    val referer: String,
    val origin: String,
    val apiPath: String,
    val postType: String,
    val title: String,
    val titleUrlBase: String,
) {
    ANIME(
        "anime", "5", "https://v5.animelib.org/", "https://v5.animelib.org", "/anime", "anime", "Аниме",
        "https://animelib.org/ru/anime/",
    ),
    RANOBE(
        "ranobe", "3", "https://ranobelib.me/", "https://ranobelib.me", "/manga", "manga", "Ранобэ",
        "https://ranobelib.me/ru/book/",
    ),
    MANGA(
        "manga", "1", "https://mangalib.me/", "https://mangalib.me", "/manga", "manga", "Манга",
        "https://mangalib.me/ru/manga/",
    );

    val isAnime: Boolean get() = this == ANIME
    val isRanobe: Boolean get() = this == RANOBE
    val isManga: Boolean get() = this == MANGA

    /** Ранобэ и манга устроены одинаково: тайтл, тома и главы. */
    val isBook: Boolean get() = this != ANIME

    companion object {
        fun of(key: String?): Site = entries.firstOrNull { it.key == key } ?: MANGA

        val current: Site get() = of(Prefs.platform)
    }
}
