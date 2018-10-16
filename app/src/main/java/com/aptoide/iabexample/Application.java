package com.aptoide.iabexample;

import android.content.pm.PackageManager;
import com.asf.appcoins.sdk.ads.AppCoinsAds;
import com.asf.appcoins.sdk.ads.AppCoinsAdsBuilder;

/**
 * Created by neuro on 12-03-2018.
 */

public class Application extends android.app.Application {
  private final String developerAddress = "0xda99070eb09ab6ab7e49866c390b01d3bca9d516";

  private static AppCoinsAds adsSdk;

  @Override public void onCreate() {
    super.onCreate();
    adsSdk = new AppCoinsAdsBuilder().withDebug(BuildConfig.TEST_NETWORK)
        .createAdvertisementSdk(this);
    //try {
    //  adsSdk.init(this);
    //} catch (PackageManager.NameNotFoundException e) {
    //  e.printStackTrace();
    //}
  }

  public String getDeveloperAddress() {
    return developerAddress;
  }
}
