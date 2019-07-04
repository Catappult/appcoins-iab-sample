package com.aptoide.iabexample.utilssdk;

public interface PurchaseFinishedListener {
  void onPurchaseFinished(int responseCode, String token,String sku);
}
