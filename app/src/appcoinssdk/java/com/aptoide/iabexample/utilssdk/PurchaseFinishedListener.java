package com.aptoide.iabexample.utilssdk;

public interface PurchaseFinishedListener {
  void onPurchaseFinished(int responseCode,String message,String token, String sku);
}
