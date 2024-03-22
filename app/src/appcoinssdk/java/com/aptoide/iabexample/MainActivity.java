package com.aptoide.iabexample;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import com.appcoins.sdk.billing.AppcoinsBillingClient;
import com.appcoins.sdk.billing.BillingFlowParams;
import com.appcoins.sdk.billing.Purchase;
import com.appcoins.sdk.billing.PurchasesResult;
import com.appcoins.sdk.billing.PurchasesUpdatedListener;
import com.appcoins.sdk.billing.ResponseCode;
import com.appcoins.sdk.billing.SkuDetails;
import com.appcoins.sdk.billing.SkuDetailsParams;
import com.appcoins.sdk.billing.helpers.CatapultBillingAppCoinsFactory;
import com.appcoins.sdk.billing.listeners.AppCoinsBillingStateListener;
import com.appcoins.sdk.billing.listeners.ConsumeResponseListener;
import com.appcoins.sdk.billing.listeners.SkuDetailsResponseListener;
import com.appcoins.sdk.billing.types.SkuType;
import com.aptoide.iabexample.util.GenericPaymentIntentBuilder;
import com.aptoide.iabexample.util.IabBroadcastReceiver;
import com.aptoide.iabexample.util.PurchaseService;
import com.aptoide.iabexample.util.Skus;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.aptoide.iabexample.util.IabHelper.ITEM_TYPE_SUBS;
import static com.aptoide.iabexample.util.IabHelper.ONE_WEEK;
import static com.aptoide.iabexample.util.IabHelper.TWO_MINUTES;

/**
 * Example game using in-app billing version 4.
 *
 * Before attempting to run this sample, please read the README file. It
 * contains important information on how to set up this project.
 *
 * All the game-specific logic is implemented here in MainActivity, while the
 * general-purpose boilerplate that can be reused in any app is provided in the
 * classes in the util/ subdirectory. When implementing your own application,
 * you can copy over util/*.java to make use of those utility classes.
 *
 * This game is a simple "driving" game where the player can buy gas
 * and drive. The car has a tank which stores gas. When the player purchases
 * gas, the tank fills up (1/4 tank at a time). When the player drives, the gas
 * in the tank diminishes (also 1/4 tank at a time).
 *
 * The user can also purchase a "premium upgrade" that gives them a red car
 * instead of the standard blue one (exciting!).
 *
 * The user can also purchase a subscription ("infinite gas") that allows them
 * to drive without using up any gas while that subscription is active.
 *
 * It's important to note the consumption mechanics for each item.
 *
 * PREMIUM: the item is purchased and NEVER consumed. So, after the original
 * purchase, the player will always own that item. The application knows to
 * display the red car instead of the blue one because it queries whether
 * the premium "item" is owned or not.
 *
 * INFINITE GAS: this is a subscription, and subscriptions can't be consumed.
 *
 * GAS: when gas is purchased, the "gas" item is then owned. We consume it
 * when we apply that item's effects to our app's world, which to us means
 * filling up 1/4 of the tank. This happens immediately after purchase!
 * It's at this point (and not when the user drives) that the "gas"
 * item is CONSUMED. Consumption should always happen when your game
 * world was safely updated to apply the effect of the purchase. So,
 * in an example scenario:
 *
 * BEFORE:      tank at 1/2
 * ON PURCHASE: tank at 1/2, "gas" item is owned
 * IMMEDIATELY: "gas" is consumed, tank goes to 3/4
 * AFTER:       tank at 3/4, "gas" item NOT owned any more
 *
 * Another important point to notice is that it may so happen that
 * the application crashed (or anything else happened) after the user
 * purchased the "gas" item, but before it was consumed. That's why,
 * on startup, we check if we own the "gas" item, and, if so,
 * we have to apply its effects to our world and consume it. This
 * is also very important!
 */
public class MainActivity extends Activity
    implements IabBroadcastReceiver.IabBroadcastListener, OnClickListener {

  // Debug tag, for logging
  static final String TAG = "TrivialDrive";
  // How many units (1/4 tank is our unit) fill in the tank.
  static final int TANK_MAX = 4;
  // (arbitrary) request code for the purchase flow
  static final int RC_REQUEST = 10001;
  static final int RC_DONATE = 10002;
  static final int RC_ONE_STEP = 10003;
  // Graphics for the gas gauge
  static int[] TANK_RES_IDS = {
      R.drawable.gas0, R.drawable.gas1, R.drawable.gas2, R.drawable.gas3, R.drawable.gas4
  };
  // Does the user have the premium upgrade?
  boolean mIsPremium = false;
  // Does the user have an active subscription?
  boolean mSubscribedToGasReserve = false;
  // Will the subscription auto-renew?
  boolean mAutoRenewEnabled = false;
  String mSubscriptionPurchaseToken = "";
  String mSelectedSubscriptionPeriod = "";
  // Current amount of gas in tank, in units
  int mTank;
  // Provides purchase notification while this app is running
  Handler handler;
  PurchaseService purchasesService;
  private AppcoinsBillingClient cab;
  ConsumeResponseListener consumeResponseListener = new ConsumeResponseListener() {
    @Override public void onConsumeResponse(int responseCode, String purchaseToken) {
      Log.d(TAG, "Consumption finished. Purchase: " + purchaseToken + ", result: " + responseCode);

      if (responseCode == ResponseCode.OK.getValue()) {

        Log.d(TAG, "Consumption successful. Provisioning.");

        mTank = mTank == TANK_MAX ? TANK_MAX : mTank + 1;
        String message = "You filled 1/4 tank. Your tank is now " + mTank + "/4 full!";
        //If subscribed to reserve then you are entitled to one more gas for each normal gas
        // purchase
        if (mSubscribedToGasReserve) {
          if (checkForActiveSubscription()) {
            mTank = mTank == TANK_MAX ? TANK_MAX : mTank + 1;
            message =
                "You filled 2/4 tank. You got one extra gas item since you're subscribed to our gas"
                    + " reserve. Your tank is now "
                    + mTank
                    + "/4 full!";
          }
        }
        if (purchaseToken.equals(mSubscriptionPurchaseToken)) {
          mSubscribedToGasReserve = true;
        }
        mSubscriptionPurchaseToken = "";
        saveData();
        alert(message);
      } else {
        complain("Error while consuming token: " + purchaseToken);
      }
      handler.post(() -> updateUi());
      setWaitScreen(false);
      Log.d(TAG, "End consumption flow.");
    }
  };
  SkuDetailsResponseListener skuDetailsResponseListener = new SkuDetailsResponseListener() {
    @Override public void onSkuDetailsResponse(int responseCode, List<SkuDetails> skuDetailsList) {
      Log.d(TAG, "Query inventory finished.");

      // Is it a failure?
      if (responseCode != ResponseCode.OK.getValue()) {
        complain("Failed to query inventory: " + responseCode);
        return;
      }

      Log.d(TAG, "Query inventory was successful.");

      // Do we have the premium upgrade?
      PurchasesResult purchasesResult = cab.queryPurchases(SkuType.inapp.toString());
      List<Purchase> purchases = purchasesResult.getPurchases();
      mIsPremium = checkSkuExists(purchases, Skus.SKU_PREMIUM2_ID);
      Log.d(TAG, "User is " + (mIsPremium ? "PREMIUM" : "NOT PREMIUM"));

      // Check for gas delivery -- if we own gas, we should fill up the tank immediately
      if (checkSkuExists(purchases, Skus.SKU_GAS_ID)) {
        Log.d(TAG, "We have gas. Consuming it.");
        List<Purchase> gasList = getSku(purchases, Skus.SKU_GAS_ID);
        if (gasList.size() > 0) {
          Purchase purchase = gasList.get(0);
          purchasesService.verifyPurchase(purchase.getSku(), purchase.getToken());
        }
      }

      handler.post(() -> updateUi());
    }
  };
  AppCoinsBillingStateListener appCoinsBillingStateListener = new AppCoinsBillingStateListener() {
    @Override public void onBillingSetupFinished(int responseCode) {
      if (responseCode != ResponseCode.OK.getValue()) {
        complain("Problem setting up in-app billing: " + responseCode);
        return;
      }
      callSkuDetails();
      updateUi();

      Log.d(TAG, "Setup successful. Querying inventory.");
    }

    @Override public void onBillingServiceDisconnected() {
      Log.d("Message: ", "Disconnected");
    }
  };

  private boolean checkSkuExists(List<Purchase> purchases, String sku) {
    for (Purchase purchase : purchases) {
      if (purchase.getSku()
          .equals(sku)) {
        return true;
      }
    }
    return false;
  }

  private List<Purchase> getSku(List<Purchase> purchases, String sku) {
    ArrayList<Purchase> listPurchase = new ArrayList<Purchase>();
    for (Purchase purchase : purchases) {
      if (purchase.getSku()
          .equals(sku)) {
        listPurchase.add(purchase);
      }
    }
    return listPurchase;
  }

  void complain(String message) {
    Log.e(TAG, "**** TrivialDrive Error: " + message);
    alert("Error: " + message);
  }

  void setWaitScreen(boolean set) {
    runOnUiThread(() -> {
      findViewById(R.id.screen_main).setVisibility(set ? View.GONE : View.VISIBLE);
      findViewById(R.id.screen_wait).setVisibility(set ? View.VISIBLE : View.GONE);
    });
  }

  void alert(String message) {
    AlertDialog.Builder bld = new AlertDialog.Builder(this);
    bld.setMessage(message);
    bld.setNeutralButton("OK", null);
    Log.d(TAG, "Showing alert dialog: " + message);
    new Handler(Looper.getMainLooper()).post(() -> bld.create()
        .show());
  }

  public void updateUi() {
    // update the car color to reflect premium status or lack thereof
    ((ImageView) findViewById(R.id.free_or_premium)).setImageResource(
        mIsPremium ? R.drawable.premium : R.drawable.free);

    // "Upgrade" button is only visible if the user is not premium
    findViewById(R.id.upgrade_button).setVisibility(mIsPremium ? View.GONE : View.VISIBLE);

    // update gas gauge to reflect tank status
    if (mSubscribedToGasReserve) {
      findViewById(R.id.subscription_image).setVisibility(View.VISIBLE);
    } else {
      findViewById(R.id.subscription_image).setVisibility(View.GONE);
    }
    int index = mTank >= TANK_RES_IDS.length ? TANK_RES_IDS.length - 1 : mTank;
    ((ImageView) findViewById(R.id.gas_gauge)).setImageResource(TANK_RES_IDS[index]);

  }

  @Override public void onCreate(Bundle savedInstanceState) {
    Log.d("MainActivity", "MAIN ACTIVITY SDK IAB");
    super.onCreate(savedInstanceState);
    handler = new Handler();
    PurchaseService.PurchaseValidatorListener purchaseValidatorListener =
        new PurchaseService.PurchaseValidatorListener() {
          @Override
          public void onPurchaseValidationResult(String sku, String token, boolean isValid) {
            Log.d(TAG, "Purchase is gas. Starting gas consumption.");
            if (isValid) {
              switch (sku) {
                case Skus.SKU_GAS_ID:
                  Log.d(TAG, "Purchase is gas. Starting gas consumption.");
                  cab.consumeAsync(token, consumeResponseListener);
                  break;
                case Skus.SKU_PREMIUM2_ID:
                  Log.d(TAG, "Purchase is premium upgrade. Congratulating user.");
                  MainActivity.this.alert("Thank you for upgrading to premium!");
                  mIsPremium = true;
                  MainActivity.this.updateUi();
                  break;
                case Skus.SKU_GAS_WEEKLY_ID:
                  Log.d(TAG, "Gas reserved subscription");
                  mSubscriptionPurchaseToken = token;
                  cab.consumeAsync(token, consumeResponseListener);
                  mAutoRenewEnabled = false; //TODO Change later
                  break;
              }
            } else {
              MainActivity.this.complain(
                  "Invalid purchase for sku " + sku + " purchase token: " + token);
            }
          }

          @Override
          public void onPurchaseValidationError(String sku, String token, Throwable error) {
            MainActivity.this.complain(error.getMessage());
          }
        };
    String baseHost;
    if (BuildConfig.DEBUG) {
      baseHost = "https://validators-dev.catappult.io";
    } else {
      baseHost = "https://validators.catappult.io";
    }
    purchasesService =
        new PurchaseService(baseHost, BuildConfig.APPLICATION_ID, purchaseValidatorListener,
            new Gson());
    setContentView(R.layout.activity_main);
    loadData();
    if (mSubscribedToGasReserve) {
      findViewById(R.id.subscription_image).setVisibility(View.VISIBLE);
    } else {
      findViewById(R.id.subscription_image).setVisibility(View.GONE);
    }
    String base64EncodedPublicKey = BuildConfig.IAB_KEY;
    PurchasesUpdatedListener purchasesUpdatedListener = (responseCode, purchases) -> {
      if (responseCode == ResponseCode.OK.getValue()
          || responseCode == ResponseCode.ITEM_ALREADY_OWNED.getValue()) {
        String sku;
        for (Purchase purchase : purchases) {
          sku = purchase.getSku();
          purchasesService.verifyPurchase(sku, purchase.getToken());
        }
      } else {
        MainActivity.this.complain(
            "Error purchasing: " + String.format(Locale.ENGLISH, "response code: %d -> %s",
                responseCode, ResponseCode.values()[responseCode].name()));
        return;
      }
    };
    cab = CatapultBillingAppCoinsFactory.BuildAppcoinsBilling(this, base64EncodedPublicKey,
        purchasesUpdatedListener);
    startConnection();
  }

  @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    Log.d(TAG, "onActivityResult(" + requestCode + "," + resultCode + "," + data);
    setWaitScreen(false);

    if (requestCode == RC_DONATE) {
      int msg = resultCode == Activity.RESULT_OK ? R.string.dialog_donation_success_msg
          : R.string.dialog_donation_fail_msg;
      alert(getString(msg));
    } else if (requestCode == RC_ONE_STEP) {
      if (resultCode == Activity.RESULT_OK) {
        mTank = mTank == TANK_MAX ? TANK_MAX : mTank + 1;
        saveData();
        alert("You filled 1/4 tank. Your tank is now " + mTank + "/4 full!");
        updateUi();
      } else {
        cab.onActivityResult(requestCode, resultCode, data);
      }
    } else if (cab.onActivityResult(requestCode, resultCode, data)) {
      super.onActivityResult(requestCode, resultCode, data);
    } else {
      Log.d(TAG, "onActivityResult handled ");
    }
  }

  void startConnection() {
    cab.startConnection(appCoinsBillingStateListener);
  }

  void loadData() {
    SharedPreferences sp = getPreferences(MODE_PRIVATE);
    mTank = sp.getInt("tank", 2);
    mIsPremium = sp.getBoolean("mIsPremium", mIsPremium);
    mSubscribedToGasReserve = sp.getBoolean("isSubscribed", mSubscribedToGasReserve);
    Log.d(TAG, "Loaded data: tank = " + mTank);
  }

  @Override public void receivedBroadcast() {
    // Received a broadcast notification that the inventory of items has changed
    Log.d(TAG, "Received broadcast notification. Querying inventory.");
    callSkuDetails();
  }

  @Override public void onClick(DialogInterface dialog, int id) {

    if (id == DialogInterface.BUTTON_POSITIVE) {

      setWaitScreen(true);
      Log.d(TAG, "Launching purchase flow for gas subscription.");
      BillingFlowParams billingFlowParams =
          new BillingFlowParams(mSelectedSubscriptionPeriod, SkuType.inapp.toString(), null, null,
              null);

      if (!cab.isReady()) {
        startConnection();
      }

      ResponseListener responseListener = responseCode -> {
        if (responseCode != ResponseCode.OK.getValue()) {
          setWaitScreen(false);
          complain("Error purchasing with response code : " + responseCode);
        }

        mSelectedSubscriptionPeriod = "";
      };

      Activity activity = this;
      AsyncTask<Object, Object, Integer> asyncTask = new AsyncTask<Object, Object, Integer>() {
        @Override protected Integer doInBackground(Object[] objects) {
          return cab.launchBillingFlow(activity, billingFlowParams);
        }

        @Override protected void onPostExecute(Integer integer) {
          responseListener.onResponse(integer);
        }
      };
      asyncTask.execute();
    } else if (id != DialogInterface.BUTTON_NEGATIVE) {
      // There are only four buttons, this should not happen
      Log.e(TAG, "Unknown button clicked in subscription dialog: " + id);
    }
  }

  void saveData() {
    SharedPreferences.Editor spe = getPreferences(MODE_PRIVATE).edit();
    spe.putBoolean("mIsPremium", mIsPremium);
    spe.putInt("tank", mTank);
    spe.putBoolean("isSubscribed", mSubscribedToGasReserve);
    spe.apply();
    Log.d(TAG, "Saved data: tank = " + mTank);
  }

  void callSkuDetails() {
    ArrayList<String> skus = new ArrayList<String>();

    SkuDetailsParams skuDetailsParams = new SkuDetailsParams();
    skuDetailsParams.setItemType(SkuType.inapp.toString());

    skus.add(Skus.SKU_GAS_ID);
    skus.add(Skus.SKU_PREMIUM2_ID);

    skuDetailsParams.setMoreItemSkus(skus);

    if (!cab.isReady()) {
      startConnection();
    }
    Thread subscriptionThread = new Thread(this::checkForActiveSubscription);
    subscriptionThread.start();
    cab.querySkuDetailsAsync(skuDetailsParams, skuDetailsResponseListener);
  }

  public void onDriveButtonClicked(View arg0) {
    Log.d(TAG, "Drive button clicked.");
    if (mTank <= 0) {
      alert("Oh, no! You are out of gas! Try buying some!");
    } else {
      --mTank;
      saveData();
      alert("Vroooom, you drove a few miles.");
      updateUi();
      Log.d(TAG, "Vrooom. Tank is now " + mTank);
    }
  }

  public void onBuyGasButtonClicked(View arg0) {
    onBuySetup();

    BillingFlowParams billingFlowParams =
        new BillingFlowParams(Skus.SKU_GAS_ID, SkuType.inapp.toString(),
            "orderId=" + System.currentTimeMillis(), "PAYLOAD TESTING", null);

    if (!cab.isReady()) {
      startConnection();
    }

    ResponseListener responseListener = responseCode -> {
      if (responseCode != ResponseCode.OK.getValue()) {
        setWaitScreen(false);
        complain("Error purchasing with response code : " + responseCode);
      }

      mSelectedSubscriptionPeriod = "";
    };

    Activity activity = this;
    Thread thread = new Thread(
        () -> responseListener.onResponse(cab.launchBillingFlow(activity, billingFlowParams)));
    thread.start();
  }

  public void onBuyOilButtonClicked(View arg0) {
    onBuySetup();

    String url =
        BuildConfig.BACKEND_HOST + "transaction/inapp?product=oil&value=0.05&currency=USD"
            + "&callback_url=https%3A%2F%2Fapi.dev.catappult.io%2Fbroker%2F8.20200101%2Fmock%2Fcallback"
            + "&domain=" + getPackageName();
        //BuildConfig.BACKEND_HOST + "transaction/inapp?product=oil&domain=" + getPackageName();

    if (BuildConfig.TEST_NETWORK) {
      url += "&signature=570c49c27cb916c595744e73d0aca61faf8ebae16603d90504ed8677ac5d4504";
      //url += "&signature=cb2a0bf9eb470f1540207596c840672760fc05cbd69329ad86526ba141f8d9b4";
    } else {
      url += "&signature=231c9185134f9c3ae2d525fef24c3d8234c159f0c05e22842b836ff72f38f08c";
      //url += "&signature=644604f3c01373ac193e91ebda34f50f1354b7f933dffe0c8e6f1d5ecbb727ab";
    }

    startOneStepPayment(url);
  }

  public void onBuyAntiFreezeButtonClicked(View arg0) {
    onBuySetup();
    //https://apichain.catappult.io/transaction/inapp?product=antifreeze2&domain=com.appcoins.trivialdrivesample.test&callback_url=https%3A%2F%2Fapi.dev.catappult.io%2Fbroker%2F8.20200101%2Fmock%2Fcallback&signature=5ddde3d5485bfa146d424e92a810d2fab878ab502ed224227a842aedb3c92a36

    // old osp:
    //String url = BuildConfig.BACKEND_HOST
    //    + "transaction/inapp?product=antifreeze&value=1.5&currency=USD"
    //    + "&callback_url=https%3A%2F%2Fapi.dev.catappult.io%2Fbroker%2F8.20200101%2Fmock%2Fcallback"
    //    + "&domain="
    //    + getPackageName();
    //if (BuildConfig.TEST_NETWORK) {
    //  url += "&signature=7878cb314b82ad2684ad4865cf84ab33e2905d2b6c7f9c3a368f6f70917e1364";
    //} else {
    //  url += "&signature=f43bb044808622581147a157c68bcb581a93e8766574ef908f7f5a3579b4451a";
    //}

    String url = "";
    if (BuildConfig.TEST_NETWORK) {
      url += BuildConfig.BACKEND_HOST
        + "transaction/inapp?product=antifreeze2"
        + "&value=3"
        + "&currency=USD"
        + "&callback_url=https%3A%2F%2Fapi.dev.catappult.io%2Fbroker%2F8.20200101%2Fmock%2Fcallback"
        + "&domain=" + getPackageName()
        + "&signature=f4d18fd461533d3d8dd9832bd08b14f8466fb612c160c2bcadb86de30259ad51";
    } else {
      url += BuildConfig.BACKEND_HOST
        + "transaction/inapp?product=antifreeze"
        + "&value=1.5"
        + "&currency=USD"
        + "&callback_url=https%3A%2F%2Fapi.dev.catappult.io%2Fbroker%2F8.20200101%2Fmock%2Fcallback"
        + "&domain=" + getPackageName()
        + "&signature=f43bb044808622581147a157c68bcb581a93e8766574ef908f7f5a3579b4451a";
    }

    startOneStepPayment(url);
  }

/**
 * This method starts the intent with the provided One Step URL to target the
 * AppCoins Wallet.
 * @param url The url that is generated by following the One Step payment rules
 */
public void startOneStepPayment(String url) {
  Intent intent = new Intent(Intent.ACTION_VIEW);
  intent.setData(Uri.parse(url));

  // If AppCoins Wallet is installed then start the Billing flow
  // else if GH is installed then start the Billing flow in GH
  // Otherwise open the URL with default action to install the Wallet
  if (isPackageInstalled(BuildConfig.WALLET_PACKAGE)) {
    intent.setPackage(BuildConfig.WALLET_PACKAGE);
  } else if (isPackageInstalled(BuildConfig.GAMESHUB_PACKAGE)) {
    intent.setPackage(BuildConfig.GAMESHUB_PACKAGE);
  }
  startActivityForResult(intent, RC_ONE_STEP);
}

private Boolean isPackageInstalled(String packageName) {
  PackageManager packageManager = getApplicationContext().getPackageManager();
  Intent intentForCheck = new Intent(Intent.ACTION_VIEW);
  if (intentForCheck.resolveActivity(packageManager) != null) {
    try {
      packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
      return true;
    } catch (PackageManager.NameNotFoundException e) {
      return false;
    }
  }
  return false;
}

  //Subs managed
  public void onBuyGasReserveButtonClicked(View view) {
    if (mSubscribedToGasReserve) {
      complain("You already have gas reserve subscription");
      Thread thread = new Thread(this::checkForActiveSubscription);
      thread.start();
      return;
    }

    onBuySetup();

    BillingFlowParams billingFlowParams =
        new BillingFlowParams(Skus.SKU_GAS_WEEKLY_ID, ITEM_TYPE_SUBS,
            "orderId=" + System.currentTimeMillis(), null, null);

    if (!cab.isReady()) {
      startConnection();
    }

    ResponseListener responseListener = responseCode -> {
      if (responseCode != ResponseCode.OK.getValue()) {
        setWaitScreen(false);
        complain("Error purchasing with response code : " + responseCode);
      }
    };

    Activity activity = this;
    Thread thread = new Thread(
        () -> responseListener.onResponse(cab.launchBillingFlow(activity, billingFlowParams)));
    thread.start();
  }

  public void onDonateButtonClicked(View arg0) {
    setWaitScreen(true);
    PendingIntent intent = GenericPaymentIntentBuilder.buildBuyIntent(this, "donation", "1.3",
        ((Application) getApplication()).getDeveloperAddress(), getPackageName(),
        GenericPaymentIntentBuilder.TransactionData.TYPE_DONATION, "Tester",
        BuildConfig.TEST_NETWORK);
    try {
      startIntentSenderForResult(intent.getIntentSender(), RC_DONATE, new Intent(), 0, 0, 0);
    } catch (IntentSender.SendIntentException e) {
      e.printStackTrace();
    }
  }

  // User clicked the "Upgrade to Premium" button.
  public void onUpgradeAppButtonClicked(View arg0) {
    Log.d(TAG, "Upgrade button clicked; launching purchase flow for upgrade.");
    setWaitScreen(true);

    Log.d(TAG, "Launching purchase flow for gas.");

    BillingFlowParams billingFlowParams =
        new BillingFlowParams(Skus.SKU_PREMIUM2_ID, SkuType.inapp.toString(), null, null, null);

    if (!cab.isReady()) {
      startConnection();
    }

    int response = cab.launchBillingFlow(this, billingFlowParams);

    if (response != ResponseCode.OK.getValue()) {
      setWaitScreen(false);
      complain("Error purchasing with response code : " + response);
    }
  }

  private void onBuySetup() {
    if (mTank >= TANK_MAX) {
      complain("Your tank is full. Drive around a bit!");
      return;
    }
    // launch the gas purchase UI flow.
    // We will be notified of completion via mPurchaseFinishedListener
    setWaitScreen(true);
    Log.d(TAG, "Launching purchase flow for gas.");
  }

  private boolean checkForActiveSubscription() {
    PurchasesResult subsPurchasesResult = cab.queryPurchases(ITEM_TYPE_SUBS);
    List<Purchase> subPurchases = subsPurchasesResult.getPurchases();
    List<Purchase> gasWeeklyList = getSku(subPurchases, Skus.SKU_GAS_WEEKLY_ID);

    Purchase gasWeekly = null;
    if (gasWeeklyList.size() > 0) {
      gasWeekly = gasWeeklyList.get(0);
    } else {
      mSubscribedToGasReserve = false;
      saveData();
      handler.post(() -> updateUi());
    }
    if (gasWeekly != null) {
      mAutoRenewEnabled = gasWeekly.isAutoRenewing();
      long subscriptionDuration = ONE_WEEK;
      if (BuildConfig.DEBUG) {
        // For test purposes we use 2 minutes
        subscriptionDuration = TWO_MINUTES;
      }
      if (System.currentTimeMillis() - gasWeekly.getPurchaseTime() >= subscriptionDuration) {
        mSubscribedToGasReserve = false;
        saveData();
        handler.post(() -> updateUi());
      }
    }
    //TODO Remove after the following is solved. At the moment we are unable to identify which
    // purchases are not consumed for subscriptions, since the endpoint returns all purchases.
    //Uncomment this method if you're stuck with unconsumed purchases
    // checkForUnconsumedSubscriptions(subPurchases);
    return mSubscribedToGasReserve;
  }

  private void checkForUnconsumedSubscriptions(List<Purchase> subPurchases) {
    for (Purchase purchase : subPurchases) {
      mSubscriptionPurchaseToken = purchase.getToken();
      cab.consumeAsync(purchase.getToken(), consumeResponseListener);
    }
  }

  public interface ResponseListener {
    void onResponse(int responseCode);
  }
}
