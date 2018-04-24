package com.aptoide.iabexample;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;
import com.asf.appcoins.sdk.iab.payment.PaymentDetails;
import com.asf.appcoins.sdk.iab.payment.PaymentStatus;
import java.util.ArrayList;
import java.util.List;

import static com.aptoide.iabexample.Application.appCoinsSdk;

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
public class MainActivity extends Activity implements OnClickListener {
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

  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.activity_main);

    // load game data
    loadData();

    updateUi();
  }

  @Override protected void onResume() {
    super.onResume();

    setWaitScreen(false);
  }

  @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    Log.d(TAG, "onActivityResult(" + requestCode + "," + resultCode + "," + data);
    super.onActivityResult(requestCode, resultCode, data);

    if (appCoinsSdk.onActivityResult(requestCode, requestCode, data)) {
      appCoinsSdk.getCurrentPayment()
          .subscribe(paymentDetails -> runOnUiThread(() -> handlePayment(paymentDetails)));
    }
  }

  private void handlePayment(PaymentDetails paymentDetails) {
    if (paymentDetails.getPaymentStatus() == PaymentStatus.SUCCESS) {
      String skuId = paymentDetails.getSkuId();
      appCoinsSdk.consume(skuId);

      // successfully consumed, so we apply the effects of the item in our
      // game world's logic, which in our case means filling the gas tank a bit
      if (Skus.SKU_GAS_ID.equals(skuId)) {
        Log.d(TAG, "Consumption successful. Provisioning.");
        mTank = mTank == TANK_MAX ? TANK_MAX : mTank + 1;
        saveData();
        alert("You filled 1/4 tank. Your tank is now " + String.valueOf(mTank) + "/4 full!");
      } else {
        if (Skus.SKU_PREMIUM_ID.equals(skuId)) {
          // bought the premium upgrade!
          Log.d(TAG, "Purchase is premium upgrade. Congratulating user.");
          alert("Thank you for upgrading to premium!");
          mIsPremium = true;
          saveData();
        }
      }

      updateUi();
      Log.d(TAG, "End consumption flow.");
    }
    setWaitScreen(false);
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

    appCoinsSdk.buy(Skus.SKU_GAS_ID, this);
  }

  // User clicked the "Upgrade to Premium" button.
  public void onUpgradeAppButtonClicked(View arg0) {
    Log.d(TAG, "Upgrade button clicked; launching purchase flow for upgrade.");
    setWaitScreen(true);

    appCoinsSdk.buy(Skus.SKU_PREMIUM_ID, this);
  }

  // "Subscribe to infinite gas" button clicked. Explain to user, then start purchase
  // flow for subscription.
  public void onInfiniteGasButtonClicked(View arg0) {
    if (true) {
      Toast.makeText(this, "Not Implemented", Toast.LENGTH_SHORT)
          .show();
      return;
    }

    CharSequence[] options;
    if (!mSubscribedToInfiniteGas || !mAutoRenewEnabled) {
      // Both subscription options should be available
      options = new CharSequence[2];
      options[0] = getString(R.string.subscription_period_monthly);
      options[1] = getString(R.string.subscription_period_yearly);
      mFirstChoiceSku = Skus.SKU_INFINITE_GAS_MONTHLY_ID;
      mSecondChoiceSku = Skus.SKU_INFINITE_GAS_YEARLY_ID;
    } else {
      // This is the subscription upgrade/downgrade path, so only one option is valid
      options = new CharSequence[1];
      if (mInfiniteGasSku.equals(Skus.SKU_INFINITE_GAS_MONTHLY_ID)) {
        // Give the option to upgrade to yearly
        options[0] = getString(R.string.subscription_period_yearly);
        mFirstChoiceSku = Skus.SKU_INFINITE_GAS_YEARLY_ID;
      } else {
        // Give the option to downgrade to monthly
        options[0] = getString(R.string.subscription_period_monthly);
        mFirstChoiceSku = Skus.SKU_INFINITE_GAS_MONTHLY_ID;
      }
      mSecondChoiceSku = "";
    }

    int titleResId;
    if (!mSubscribedToInfiniteGas) {
      titleResId = R.string.subscription_period_prompt;
    } else if (!mAutoRenewEnabled) {
      titleResId = R.string.subscription_resignup_prompt;
    } else {
      titleResId = R.string.subscription_update_prompt;
    }

    Builder builder = new Builder(this);
    builder.setTitle(titleResId)
        .setSingleChoiceItems(options, 0 /* checkedItem */, this)
        .setPositiveButton(R.string.subscription_prompt_continue, this)
        .setNegativeButton(R.string.subscription_prompt_cancel, this);
    AlertDialog dialog = builder.create();
    dialog.show();
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

      List<String> oldSkus;
      if (!TextUtils.isEmpty(mInfiniteGasSku) && !mInfiniteGasSku.equals(
          mSelectedSubscriptionPeriod)) {
        // The user currently has a valid subscription, any purchase action is going to
        // replace that subscription
        oldSkus = new ArrayList<>();
        oldSkus.add(mInfiniteGasSku);
      }

      setWaitScreen(true);
      Log.d(TAG, "Launching purchase flow for gas subscription.");
      // TODO: 14-03-2018 neuro subscription
      // Reset the dialog options
      mSelectedSubscriptionPeriod = "";
      mFirstChoiceSku = "";
      mSecondChoiceSku = "";
    } else if (id != DialogInterface.BUTTON_NEGATIVE) {
      // There are only four buttons, this should not happen
      Log.e(TAG, "Unknown button clicked in subscription dialog: " + id);
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

    ImageView infiniteGasButton = (ImageView) findViewById(R.id.infinite_gas_button);
    if (mSubscribedToInfiniteGas) {
      // If subscription is active, show "Manage Infinite Gas"
      infiniteGasButton.setImageResource(R.drawable.manage_infinite_gas);
    } else {
      // The user does not have infinite gas, show "Get Infinite Gas"
      infiniteGasButton.setImageResource(R.drawable.get_infinite_gas);
    }

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
    Log.d(TAG, "Saved data: tank = " + String.valueOf(mTank));
  }

  void loadData() {
    SharedPreferences sp = getPreferences(MODE_PRIVATE);
    mTank = sp.getInt("tank", 2);
    mIsPremium = sp.getBoolean("mIsPremium", mIsPremium);
    Log.d(TAG, "Loaded data: tank = " + String.valueOf(mTank));
  }
}
