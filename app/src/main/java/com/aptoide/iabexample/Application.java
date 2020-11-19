package com.aptoide.iabexample;

import com.asf.appcoins.sdk.ads.AppCoinsAds;

/**
 * Created by neuro on 12-03-2018.
 */

public class Application extends android.app.Application {
  private static AppCoinsAds adsSdk;
  private final String developerAddress = BuildConfig.IAB_WALLET_ADDR;

  @Override public void onCreate() {
    super.onCreate();
    //Comment this if sdk version = 0.6
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
