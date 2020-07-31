package com.aptoide.iabexample;

import android.content.pm.PackageManager;
import com.asf.appcoins.sdk.ads.AppCoinsAds;
import com.asf.appcoins.sdk.ads.AppCoinsAdsBuilder;

/**
 * Created by neuro on 12-03-2018.
 */

public class Application extends android.app.Application {
  private final String developerAddress = BuildConfig.IAB_WALLET_ADDR;

  private static AppCoinsAds adsSdk;

  @Override public void onCreate() {
    super.onCreate();
    //Removed until new SDK version
    /*adsSdk = new AppCoinsAdsBuilder().withDebug(BuildConfig.TEST_NETWORK)
        .createAdvertisementSdk(this);
    try {
      adsSdk.init(this);
    } catch (PackageManager.NameNotFoundException e) {
      e.printStackTrace();
    }*/
  }

  public String getDeveloperAddress() {
    return developerAddress;
  }
}
