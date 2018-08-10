package com.aptoide.iabexample;

/**
 * Created by neuro on 12-03-2018.
 */

public class Application extends android.app.Application {
  private final String developerAddress = "0xd133fab7abc4711fd328c4aefbf04256a79b1b40";

  @Override public void onCreate() {
    super.onCreate();
  }

  public String getDeveloperAddress() {
    return developerAddress;
  }
}
