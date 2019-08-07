package com.aptoide.iabexample;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import com.aptoide.iabexample.util.GenericPaymentIntentBuilder;
import com.aptoide.iabexample.util.IabBroadcastReceiver;
import com.aptoide.iabexample.util.IabHelper;
import com.aptoide.iabexample.util.IabResult;
import com.aptoide.iabexample.util.Inventory;
import com.aptoide.iabexample.util.PayloadHelper;
import com.aptoide.iabexample.util.Purchase;
import com.aptoide.iabexample.util.Skus;
import java.util.ArrayList;
import java.util.List;

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
  // Graphics for the gas gauge
  static int[] TANK_RES_IDS = {
      R.drawable.gas0, R.drawable.gas1, R.drawable.gas2, R.drawable.gas3, R.drawable.gas4
  };
  // Does the user have the premium upgrade?
  boolean mIsPremium = false;
  // Does the user have an active subscription to the infinite gas plan?
  boolean mSubscribedToInfiniteGas = false;
  // Will the subscription auto-renew?
  boolean mAutoRenewEnabled = false;
  // Tracks the currently owned infinite gas SKU, and the options in the Manage dialog
  String mInfiniteGasSku = "";
  String mFirstChoiceSku = "";
  String mSecondChoiceSku = "";
  // Used to select between purchasing gas on a monthly or yearly basis
  String mSelectedSubscriptionPeriod = "";
  // Current amount of gas in tank, in units
  int mTank;
  // The helper object
  IabHelper mHelper;
  // Provides purchase notification while this app is running
  IabBroadcastReceiver mBroadcastReceiver;

  // (arbitrary) request code for the purchase flow
  static final int RC_REQUEST = 10001;
  static final int RC_DONATE = 10002;
  static final int RC_ONE_STEP = 10003;

  // Listener that's called when we finish querying the items and subscriptions we own
  IabHelper.QueryInventoryFinishedListener mGotInventoryListener =
      new IabHelper.QueryInventoryFinishedListener() {
        public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
          Log.d(TAG, "Query inventory finished.");

          // Have we been disposed of in the meantime? If so, quit.
          if (mHelper == null) return;

          // Is it a failure?
          if (result.isFailure()) {
            complain("Failed to query inventory: " + result);
            return;
          }

          Log.d(TAG, "Query inventory was successful.");

          /*
           * Check for items we own. Notice that for each purchase, we check
           * the developer payload to see if it's correct! See
           * verifyDeveloperPayload().
           */

          // Do we have the premium upgrade?
          Purchase premiumPurchase = inventory.getPurchase(Skus.SKU_PREMIUM_ID);
          mIsPremium = (premiumPurchase != null && verifyDeveloperPayload(premiumPurchase));
          Log.d(TAG, "User is " + (mIsPremium ? "PREMIUM" : "NOT PREMIUM"));

          // First find out which subscription is auto renewing
          Purchase gasMonthly = inventory.getPurchase(Skus.SKU_INFINITE_GAS_MONTHLY_ID);
          Purchase gasYearly = inventory.getPurchase(Skus.SKU_INFINITE_GAS_YEARLY_ID);
          if (gasMonthly != null && gasMonthly.isAutoRenewing()) {
            mInfiniteGasSku = Skus.SKU_INFINITE_GAS_MONTHLY_ID;
            mAutoRenewEnabled = true;
          } else if (gasYearly != null && gasYearly.isAutoRenewing()) {
            mInfiniteGasSku = Skus.SKU_INFINITE_GAS_YEARLY_ID;
            mAutoRenewEnabled = true;
          } else {
            mInfiniteGasSku = "";
            mAutoRenewEnabled = false;
          }

          // The user is subscribed if either subscription exists, even if neither is auto
          // renewing
          mSubscribedToInfiniteGas = (gasMonthly != null && verifyDeveloperPayload(gasMonthly)) || (
              gasYearly != null
                  && verifyDeveloperPayload(gasYearly));
          Log.d(TAG, "User "
              + (mSubscribedToInfiniteGas ? "HAS" : "DOES NOT HAVE")
              + " infinite gas subscription.");
          if (mSubscribedToInfiniteGas) mTank = TANK_MAX;

          // Check for gas delivery -- if we own gas, we should fill up the tank immediately
          Purchase gasPurchase = inventory.getPurchase(Skus.SKU_GAS_ID);
          if (gasPurchase != null && verifyDeveloperPayload(gasPurchase)) {
            Log.d(TAG, "We have gas. Consuming it.");
            try {
              mHelper.consumeAsync(inventory.getPurchase(Skus.SKU_GAS_ID),
                  mConsumeFinishedListener);
            } catch (IabHelper.IabAsyncInProgressException e) {
              complain("Error consuming gas. Another async operation in progress.");
            }
            return;
          }

          updateUi();
          setWaitScreen(false);
          Log.d(TAG, "Initial inventory query finished; enabling main UI.");
        }
      };

  // Callback for when a purchase is finished
  IabHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener =
      new IabHelper.OnIabPurchaseFinishedListener() {
        public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
          Log.d(TAG, "Purchase finished: " + result + ", purchase: " + purchase);

          // if we were disposed of in the meantime, quit.
          if (mHelper == null) return;

          if (result.isFailure()) {
            complain("Error purchasing: " + result);
            setWaitScreen(false);
            return;
          }
          if (!verifyDeveloperPayload(purchase)) {
            complain("Error purchasing. Authenticity verification failed.");
            setWaitScreen(false);
            return;
          }

          Log.d(TAG, "Purchase successful.");

          if (purchase.getSku()
              .equals(Skus.SKU_GAS_ID)) {
            // bought 1/4 tank of gas. So consume it.
            Log.d(TAG, "Purchase is gas. Starting gas consumption.");
            try {
              if (result.isItemAlreadyOwened()) {
                List<Purchase> purchases = new ArrayList<>();
                purchases.add(purchase);
                mHelper.consumeAsync(purchases, mConsumeFinishedListener);
                Log.d(TAG, "Consumed previously bought gas");
              } else {
                mHelper.consumeAsync(purchase, mConsumeFinishedListener);
              }
            } catch (IabHelper.IabAsyncInProgressException e) {
              complain("Error consuming gas. Another async operation in progress.");
              setWaitScreen(false);
              return;
            }
          } else if (purchase.getSku()
              .equals(Skus.SKU_PREMIUM_ID)) {
            // bought the premium upgrade!
            Log.d(TAG, "Purchase is premium upgrade. Congratulating user.");
            alert("Thank you for upgrading to premium!");
            mIsPremium = true;
            updateUi();
            setWaitScreen(false);
          } else if (purchase.getSku()
              .equals(Skus.SKU_INFINITE_GAS_MONTHLY_ID) || purchase.getSku()
              .equals(Skus.SKU_INFINITE_GAS_YEARLY_ID)) {
            // bought the infinite gas subscription
            Log.d(TAG, "Infinite gas subscription purchased.");
            alert("Thank you for subscribing to infinite gas!");
            mSubscribedToInfiniteGas = true;
            mAutoRenewEnabled = purchase.isAutoRenewing();
            mInfiniteGasSku = purchase.getSku();
            mTank = TANK_MAX;
            updateUi();
            setWaitScreen(false);
          }
        }
      };

  // Called when consumption is complete
  IabHelper.OnConsumeFinishedListener mConsumeFinishedListener =
      new IabHelper.OnConsumeFinishedListener() {
        public void onConsumeFinished(Purchase purchase, IabResult result) {
          Log.d(TAG, "Consumption finished. Purchase: " + purchase + ", result: " + result);

          // if we were disposed of in the meantime, quit.
          if (mHelper == null) return;

          // We know this is the "gas" sku because it's the only one we consume,
          // so we don't check which sku was consumed. If you have more than one
          // sku, you probably should check...
          if (result.isSuccess()) {
            // successfully consumed, so we apply the effects of the item in our
            // game world's logic, which in our case means filling the gas tank a bit
            Log.d(TAG, "Consumption successful. Provisioning.");
            mTank = mTank == TANK_MAX ? TANK_MAX : mTank + 1;
            saveData();
            alert("You filled 1/4 tank. Your tank is now " + mTank + "/4 full!");
          } else {
            complain("Error while consuming: " + result);
          }
          updateUi();
          setWaitScreen(false);
          Log.d(TAG, "End consumption flow.");
        }
      };

  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Log.d("MainActivity", "MAIN ACTIVITY APPCOINS IAB");
    setContentView(R.layout.activity_main);

    // load game data
    loadData();

    /* base64EncodedPublicKey should be YOUR APPLICATION'S PUBLIC KEY
     * (that you got from your Aptoide's back office). This is not your
     * developer public key, it's the *app-specific* public key.
     *
     * Instead of just storing the entire literal string here embedded in the
     * program,  construct the key at runtime from pieces or
     * use bit manipulation (for example, XOR with some other string) to hide
     * the actual key.  The key itself is not secret information, but we don't
     * want to make it easy for an attacker to replace the public key with one
     * of their own and then fake messages from the server.
     */
    String base64EncodedPublicKey = BuildConfig.IAB_KEY;

    // Some sanity checks to see if the developer (that's you!) really followed the
    // instructions to run this sample (don't put these checks on your app!)
    if (base64EncodedPublicKey.contains("CONSTRUCT_YOUR")) {
      throw new RuntimeException(
          "Please put your app's public key in MainActivity.java. See README.");
    }

    // Create the helper, passing it our context and the public key to verify signatures with
    Log.d(TAG, "Creating IAB helper.");
    mHelper = new IabHelper(this, base64EncodedPublicKey);

    // enable debug logging (for a production application, you should set this to false).
    mHelper.enableDebugLogging(true);

    // Start setup. This is asynchronous and the specified listener
    // will be called once setup completes.
    Log.d(TAG, "Starting setup.");
    mHelper.startSetup(result -> {
      Log.d(TAG, "Setup finished.");

      if (!result.isSuccess()) {
        // Oh noes, there was a problem.
        complain("Problem setting up in-app billing: " + result);
        return;
      }

      // Have we been disposed of in the meantime? If so, quit.
      if (mHelper == null) return;

      // Important: Dynamically register for broadcast messages about updated purchases.
      // We register the receiver here instead of as a <receiver> in the Manifest
      // because we always call getPurchases() at startup, so therefore we can ignore
      // any broadcasts sent while the app isn't running.
      // Note: registering this listener in an Activity is a bad idea, but is done here
      // because this is a SAMPLE. Regardless, the receiver must be registered after
      // IabHelper is setup, but before first call to getPurchases().
      // Verify the action for the broadcast receiver is empty meaning the feature is not
      // supported and there is no reason to create a listener.
      //if (!IabBroadcastReceiver.ACTION.isEmpty()) {
      //  mBroadcastReceiver = new IabBroadcastReceiver(MainActivity.this);
      //  IntentFilter broadcastFilter = new IntentFilter(IabBroadcastReceiver.ACTION);
      //  registerReceiver(mBroadcastReceiver, broadcastFilter);
      //}
      // IAB is fully set up. Now, let's get an inventory of stuff we own.
      Log.d(TAG, "Setup successful. Querying inventory.");
      try {
        mHelper.queryInventoryAsync(mGotInventoryListener);
      } catch (IabHelper.IabAsyncInProgressException e) {
        complain("Error querying inventory. Another async operation in progress.");
      }
    });

    updateUi();
  }

  @Override protected void onResume() {
    super.onResume();

    setWaitScreen(false);
  }

  @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    Log.d(TAG, "onActivityResult(" + requestCode + "," + resultCode + "," + data);
    if (mHelper == null) return;
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
      }
    } else if (!mHelper.handleActivityResult(requestCode, resultCode, data)) {
      // Pass on the activity result to the helper for handling
      // not handled, so handle it ourselves (here's where you'd
      // perform any handling of activity results not related to in-app
      // billing...
      super.onActivityResult(requestCode, resultCode, data);
    } else {
      Log.d(TAG, "onActivityResult handled by IABUtil.");
    }
  }

  @Override public void onClick(DialogInterface dialog, int id) {
    if (id == 0 /* First choice item */) {
      mSelectedSubscriptionPeriod = mFirstChoiceSku;
    } else if (id == 1 /* Second choice item */) {
      mSelectedSubscriptionPeriod = mSecondChoiceSku;
    } else if (id == DialogInterface.BUTTON_POSITIVE /* continue button */) {

      if (TextUtils.isEmpty(mSelectedSubscriptionPeriod)) {
        // The user has not changed from the default selection
        mSelectedSubscriptionPeriod = mFirstChoiceSku;
      }

      List<String> oldSkus = null;
      if (!TextUtils.isEmpty(mInfiniteGasSku) && !mInfiniteGasSku.equals(
          mSelectedSubscriptionPeriod)) {
        // The user currently has a valid subscription, any purchase action is going to
        // replace that subscription
        oldSkus = new ArrayList<>();
        oldSkus.add(mInfiniteGasSku);
      }

      /*
       * TODO: for security, generate your payload here for verification. See the comments on
       *        verifyDeveloperPayload() for more info. Since this is a SAMPLE, we just use
       *        an empty string, but on a production app you should carefully generate this.
       * TODO: On this payload the developer's wallet address must be added, or the purchase does
       * NOT work.
       */
      String payload = PayloadHelper.buildIntentPayload("orderId=" + System.currentTimeMillis(),
          "developer payload", null);

      setWaitScreen(true);
      Log.d(TAG, "Launching purchase flow for gas subscription.");
      try {
        mHelper.launchPurchaseFlow(this, mSelectedSubscriptionPeriod, IabHelper.ITEM_TYPE_SUBS,
            oldSkus, RC_REQUEST, mPurchaseFinishedListener, payload);
      } catch (IabHelper.IabAsyncInProgressException e) {
        complain("Error launching purchase flow. Another async operation in progress.");
        setWaitScreen(false);
      }      // Reset the dialog options
      mSelectedSubscriptionPeriod = "";
      mFirstChoiceSku = "";
      mSecondChoiceSku = "";
    } else if (id != DialogInterface.BUTTON_NEGATIVE) {
      // There are only four buttons, this should not happen
      Log.e(TAG, "Unknown button clicked in subscription dialog: " + id);
    }
  }

  @Override public void receivedBroadcast() {
    // Received a broadcast notification that the inventory of items has changed
    Log.d(TAG, "Received broadcast notification. Querying inventory.");
    try {
      mHelper.queryInventoryAsync(mGotInventoryListener);
    } catch (IabHelper.IabAsyncInProgressException e) {
      complain("Error querying inventory. Another async operation in progress.");
    }
  }

  // User clicked the "Buy Gas" button
  public void onBuyGasButtonClicked(View arg0) {
    Log.d(TAG, "Buy gas button clicked.");

    if (mSubscribedToInfiniteGas) {
      complain("No need! You're subscribed to infinite gas. Isn't that awesome?");
      return;
    }

    if (mTank >= TANK_MAX) {
      complain("Your tank is full. Drive around a bit!");
      return;
    }

    // launch the gas purchase UI flow.
    // We will be notified of completion via mPurchaseFinishedListener
    setWaitScreen(true);
    Log.d(TAG, "Launching purchase flow for gas.");

    /* TODO: for security, generate your payload here for verification. See the comments on
     *        verifyDeveloperPayload() for more info. Since this is a SAMPLE, we just use
     *        an empty string, but on a production app you should carefully generate this.
     * TODO: On this payload the developer's wallet address must be added, or the purchase does
     * NOT work.
     */
    String payload = PayloadHelper.buildIntentPayload("orderId=" + System.currentTimeMillis(),
        "developer payload: gas", null);
    try {
      mHelper.launchPurchaseFlow(this, Skus.SKU_GAS_ID, RC_REQUEST, mPurchaseFinishedListener,
          payload);
    } catch (IabHelper.IabAsyncInProgressException e) {
      complain("Error launching purchase flow. Another async operation in progress.");
      setWaitScreen(false);
    }
  }

  // User clicked the "Upgrade to Premium" button.
  public void onUpgradeAppButtonClicked(View arg0) {
    Log.d(TAG, "Upgrade button clicked; launching purchase flow for upgrade.");
    setWaitScreen(true);

    /* TODO: for security, generate your payload here for verification. See the comments on
     *        verifyDeveloperPayload() for more info. Since this is a SAMPLE, we just use
     *        an empty string, but on a production app you should carefully generate this.
     * TODO: On this payload the developer's wallet address must be added, or the purchase does
     * NOT work.
     */
    String payload = PayloadHelper.buildIntentPayload("orderId=" + System.currentTimeMillis(),
        "developer payload: premium", null);

    try {
      mHelper.launchPurchaseFlow(this, Skus.SKU_PREMIUM_ID, RC_REQUEST, mPurchaseFinishedListener,
          payload);
    } catch (IabHelper.IabAsyncInProgressException e) {
      complain("Error launching purchase flow. Another async operation in progress.");
      setWaitScreen(false);
    }
  }

  // "Subscribe to infinite gas" button clicked. Explain to user, then start purchase
  // flow for subscription.
  public void onDonateButtonClicked(View arg0) {
    setWaitScreen(true);
    PendingIntent intent = GenericPaymentIntentBuilder.buildBuyIntent(this, "donatio", "1.3",
        ((Application) getApplication()).getDeveloperAddress(), getPackageName(),
        GenericPaymentIntentBuilder.TransactionData.TYPE_DONATION, "Tester", BuildConfig.DEBUG);
    try {
      startIntentSenderForResult(intent.getIntentSender(), RC_DONATE, new Intent(), 0, 0, 0);
    } catch (IntentSender.SendIntentException e) {
      e.printStackTrace();
    }
  }

  // "Buy oil" button clicked. Explain to user, then start purchase
  // flow for subscription.
  public void onBuyOilButtonClicked(View arg0) {
    if (mSubscribedToInfiniteGas) {
      complain("No need! You're subscribed to infinite gas. Isn't that awesome?");
      return;
    }

    if (mTank >= TANK_MAX) {
      complain("Your tank is full. Drive around a bit!");
      return;
    }

    setWaitScreen(true);
    String url =
        "https://apichain-dev.blockchainds.com/transaction/inapp?value=5&currency=USD&domain=com.appcoins.trivialdrivesample.test";
    Intent i = new Intent(Intent.ACTION_VIEW);
    i.setData(Uri.parse(url));

    PendingIntent intent =
        PendingIntent.getActivity(getApplicationContext(), 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
    try {
      startIntentSenderForResult(intent.getIntentSender(), RC_ONE_STEP, new Intent(), 0, 0, 0);
    } catch (IntentSender.SendIntentException e) {
      e.printStackTrace();
    }
  }

  // "Subscribe to infinite gas" button clicked. Explain to user, then start purchase
  // flow for subscription.
  public void onBuyAntiFreezeButtonClicked(View arg0) {
    if (mSubscribedToInfiniteGas) {
      complain("No need! You're subscribed to infinite gas. Isn't that awesome?");
      return;
    }

    if (mTank >= TANK_MAX) {
      complain("Your tank is full. Drive around a bit!");
      return;
    }

    // launch the gas purchase UI flow.
    // We will be notified of completion via mPurchaseFinishedListener
    setWaitScreen(true);
    Log.d(TAG, "Launching purchase flow for gas.");

    /* TODO: for security, generate your payload here for verification. See the comments on
     *        verifyDeveloperPayload() for more info. Since this is a SAMPLE, we just use
     *        an empty string, but on a production app you should carefully generate this.
     * TODO: On this payload the developer's wallet address must be added, or the purchase does
     * NOT work.
     */
    String payload = PayloadHelper.buildIntentPayload("orderId=" + System.currentTimeMillis(),
        "developer payload: gas", "UNITY");
    try {
      mHelper.launchPurchaseFlow(this, Skus.SKU_GAS_ID, RC_REQUEST, mPurchaseFinishedListener,
          payload);
    } catch (IabHelper.IabAsyncInProgressException e) {
      complain("Error launching purchase flow. Another async operation in progress.");
      setWaitScreen(false);
    }
  }

  // Drive button clicked. Burn gas!
  public void onDriveButtonClicked(View arg0) {
    Log.d(TAG, "Drive button clicked.");
    if (!mSubscribedToInfiniteGas && mTank <= 0) {
      alert("Oh, no! You are out of gas! Try buying some!");
    } else {
      if (!mSubscribedToInfiniteGas) --mTank;
      saveData();
      alert("Vroooom, you drove a few miles.");
      updateUi();
      Log.d(TAG, "Vrooom. Tank is now " + mTank);
    }
  }

  // updates UI to reflect model
  public void updateUi() {
    // update the car color to reflect premium status or lack thereof
    ((ImageView) findViewById(R.id.free_or_premium)).setImageResource(
        mIsPremium ? R.drawable.premium : R.drawable.free);

    // "Upgrade" button is only visible if the user is not premium
    findViewById(R.id.upgrade_button).setVisibility(mIsPremium ? View.GONE : View.VISIBLE);

    // update gas gauge to reflect tank status
    if (mSubscribedToInfiniteGas) {
      ((ImageView) findViewById(R.id.gas_gauge)).setImageResource(R.drawable.gas_inf);
    } else {
      int index = mTank >= TANK_RES_IDS.length ? TANK_RES_IDS.length - 1 : mTank;
      ((ImageView) findViewById(R.id.gas_gauge)).setImageResource(TANK_RES_IDS[index]);
    }
  }

  // Enables or disables the "please wait" screen.
  void setWaitScreen(boolean set) {
    findViewById(R.id.screen_main).setVisibility(set ? View.GONE : View.VISIBLE);
    findViewById(R.id.screen_wait).setVisibility(set ? View.VISIBLE : View.GONE);
  }

  void complain(String message) {
    Log.e(TAG, "**** TrivialDrive Error: " + message);
    alert("Error: " + message);
  }

  void alert(String message) {
    AlertDialog.Builder bld = new AlertDialog.Builder(this);
    bld.setMessage(message);
    bld.setNeutralButton("OK", null);
    Log.d(TAG, "Showing alert dialog: " + message);
    bld.create()
        .show();
  }

  void saveData() {
    SharedPreferences.Editor spe = getPreferences(MODE_PRIVATE).edit();
    spe.putBoolean("mIsPremium", mIsPremium);
    spe.putInt("tank", mTank);
    spe.apply();
    Log.d(TAG, "Saved data: tank = " + mTank);
  }

  void loadData() {
    SharedPreferences sp = getPreferences(MODE_PRIVATE);
    mTank = sp.getInt("tank", 2);
    mIsPremium = sp.getBoolean("mIsPremium", mIsPremium);
    Log.d(TAG, "Loaded data: tank = " + mTank);
  }

  /** Verifies the developer payload of a purchase. */
  boolean verifyDeveloperPayload(Purchase p) {
    String payload = p.getDeveloperPayload();

    /*
     * TODO: verify that the developer payload of the purchase is correct. It will be
     * the same one that you sent when initiating the purchase.
     *
     * WARNING: Locally generating a random string when starting a purchase and
     * verifying it here might seem like a good approach, but this will fail in the
     * case where the user purchases an item on one device and then uses your app on
     * a different device, because on the other device you will not have access to the
     * random string you originally generated.
     *
     * So a good developer payload has these characteristics:
     *
     * 1. If two different users purchase an item, the payload is different between them,
     *    so that one user's purchase can't be replayed to another user.
     *
     * 2. The payload must be such that you can verify it even when the app wasn't the
     *    one who initiated the purchase flow (so that items purchased by the user on
     *    one device work on other devices owned by the user).
     *
     * Using your own server to store and verify developer payloads across app
     * installations is recommended.
     */

    return true;
  }
}
