package com.aptoide.iabexample;

import com.asf.appcoins.sdk.AppCoinsSdk;
import com.asf.appcoins.sdk.AppCoinsSdkBuilder;
import com.asf.appcoins.sdk.entity.SKU;
import java.math.BigDecimal;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by neuro on 12-03-2018.
 */

public class Application extends android.app.Application {

  public static AppCoinsSdk appCoinsSdk;
  private final String developerAddress = "0x4fbcc5ce88493c3d9903701c143af65f54481119";

  @Override public void onCreate() {
    super.onCreate();

    appCoinsSdk = new AppCoinsSdkBuilder(developerAddress).withSkus(buildSkus())
        .withDebug(true)
        .createAppCoinsSdk();
  }

  private List<SKU> buildSkus() {
    List<SKU> skus = new LinkedList<>();

    skus.add(new SKU(Skus.SKU_GAS_LABEL, Skus.SKU_GAS_ID, BigDecimal.valueOf(1)));
    skus.add(new SKU(Skus.SKU_PREMIUM_LABEL, Skus.SKU_PREMIUM_ID,
        BigDecimal.valueOf(2)));

    return skus;
  }
}