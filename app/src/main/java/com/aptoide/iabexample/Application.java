package com.aptoide.iabexample;

/**
 * Created by neuro on 12-03-2018.
 */

public class Application extends android.app.Application {
  private final String developerAddress = "0xda99070eb09ab6ab7e49866c390b01d3bca9d516";

  @Override public void onCreate() {
    super.onCreate();
  }

  public String getDeveloperAddress() {
    return developerAddress;
  }
}
