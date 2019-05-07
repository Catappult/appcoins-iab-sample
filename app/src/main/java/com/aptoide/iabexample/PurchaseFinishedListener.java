package com.aptoide.iabexample;

import com.aptoide.iabexample.util.Purchase;

public interface PurchaseFinishedListener {
  void onPurchaseFinished(int responseCode, String token);
}
