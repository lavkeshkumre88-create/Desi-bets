package com.example.config

/**
 * Central configuration for Desi Beats language tabs and AdMob ad unit IDs.
 * Keeps Google TEST Ad Unit IDs active during development; swap USE_TEST_ADS = false
 * and fill in your production IDs before Play Store release.
 */
object AppConfig {
    val LANGUAGE_TABS: List<String> = listOf(
        "Hindi",
        "Punjabi",
        "Haryanvi",
        "Telugu",
        "Rajasthani",
        "All",
    )

    const val INTERSTITIAL_EVERY_N_SONGS = 5
}

object AdConfig {
    const val USE_TEST_ADS = true

    // Official Google AdMob Test Ad Unit IDs
    private const val TEST_BANNER_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    private const val TEST_INTERSTITIAL_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

    // Replace with your real AdMob Ad Unit IDs before production release
    private const val PROD_BANNER_UNIT_ID = "ca-app-pub-XXXXXXXXXXXXXXXX/YYYYYYYYYY"
    private const val PROD_INTERSTITIAL_UNIT_ID = "ca-app-pub-XXXXXXXXXXXXXXXX/ZZZZZZZZZZ"

    val bannerAdUnitId: String
        get() = if (USE_TEST_ADS) TEST_BANNER_UNIT_ID else PROD_BANNER_UNIT_ID

    val interstitialAdUnitId: String
        get() = if (USE_TEST_ADS) TEST_INTERSTITIAL_UNIT_ID else PROD_INTERSTITIAL_UNIT_ID
}
