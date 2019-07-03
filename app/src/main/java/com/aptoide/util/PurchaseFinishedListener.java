package com.aptoide.util;

public interface PurchaseFinishedListener {
  void onPurchaseFinished(int responseCode, String token,String sku);
}
