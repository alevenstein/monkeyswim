package com.lionstone.monkeyrapids

import android.app.Activity
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * Wraps a single rewarded-ad slot using Google's official **test** rewarded
 * ad unit ID. Replace [REWARDED_AD_UNIT] (and the manifest meta-data
 * `com.google.android.gms.ads.APPLICATION_ID`) with real values before publishing.
 *
 * Test ad unit ID source: https://developers.google.com/admob/android/test-ads
 */
class AdMobController(private val activity: Activity) {

    companion object {
        private const val TAG = "AdMobController"
        // Google's official test rewarded ad unit ID:
        private val REWARDED_AD_UNIT = if (BuildConfig.DEBUG)
            "ca-app-pub-3940256099942544/5224354917"
        else
            "ca-app-pub-7133034697479472/2433596379"

    }

    private var rewardedAd: RewardedAd? = null
    private var loading: Boolean = false

    fun init(onReady: () -> Unit = {}) {
        MobileAds.initialize(activity) { onReady() }
        loadAd()
    }

    fun loadAd() {
        if (loading || rewardedAd != null) return
        loading = true
        val req = AdRequest.Builder().build()
        RewardedAd.load(
            activity,
            REWARDED_AD_UNIT,
            req,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    loading = false
                    Log.d(TAG, "Rewarded ad loaded.")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    loading = false
                    Log.w(TAG, "Rewarded ad failed: ${error.message}")
                }
            }
        )
    }

    val isReady: Boolean get() = rewardedAd != null

    /**
     * Shows the rewarded ad. [onReward] fires only if the user actually earns
     * the reward, and only *after* the ad has been dismissed — Google fires the
     * earned-reward listener while the ad is still on screen, so deferring to
     * dismissal keeps the game from resuming behind the still-open ad.
     * [onUnavailable] fires if no ad is loaded yet.
     */
    fun showRewarded(onReward: () -> Unit, onUnavailable: () -> Unit) {
        val ad = rewardedAd
        if (ad == null) {
            onUnavailable()
            loadAd()
            return
        }
        var earnedReward = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                loadAd()  // pre-load next
                if (earnedReward) onReward()
            }
            override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                rewardedAd = null
                loadAd()
                onUnavailable()
            }
        }
        ad.show(activity, OnUserEarnedRewardListener { _ -> earnedReward = true })
    }
}
