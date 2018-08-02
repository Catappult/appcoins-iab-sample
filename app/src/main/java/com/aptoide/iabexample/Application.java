package com.aptoide.iabexample;

import com.asf.appcoins.sdk.ads.AppCoinsAds;
import com.asf.appcoins.sdk.ads.AppCoinsAdsBuilder;
import com.asf.appcoins.sdk.iab.AppCoinsIab;
import com.asf.appcoins.sdk.iab.AppCoinsIabBuilder;
import com.asf.appcoins.sdk.iab.entity.SKU;
import java.math.BigDecimal;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by neuro on 12-03-2018.
 */

public class Application extends android.app.Application {

  public static AppCoinsIab appCoinsIab;

  private static AppCoinsAds adsSdk;

  private final String developerAddress = "0x4fbcc5ce88493c3d9903701c143af65f54481119";

  @Override public void onCreate() {
    super.onCreate();

    appCoinsIab = new AppCoinsIabBuilder(developerAddress).withSkus(buildSkus())
        .withDebug(BuildConfig.TEST_NETWORK)
        .createAppCoinsIab();

    //adsSdk = new AppCoinsAdsBuilder().withDebug(BuildConfig.TEST_NETWORK)
    //    .createAdvertisementSdk(this);
    //adsSdk.init(this);
  }

  private List<SKU> buildSkus() {
    List<SKU> skus = new LinkedList<>();

    skus.add(new SKU(Skus.SKU_GAS_LABEL, Skus.SKU_GAS_ID, BigDecimal.valueOf(1)));
    skus.add(new SKU(Skus.SKU_PREMIUM_LABEL, Skus.SKU_PREMIUM_ID,
        BigDecimal.valueOf(2)));

    return skus;
  }

  public String getDeveloperAddress() {
    return developerAddress;
  }
}
