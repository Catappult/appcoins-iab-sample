package com.aptoide.sdk;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import com.appcoins.sdk.billing.ResponseCode;
import com.appcoins.sdk.billing.helpers.Utils;
import com.aptoide.util.PurchaseFinishedListener;
import com.aptoide.util.Security;
import org.json.JSONException;
import org.json.JSONObject;

public class AplicationUtils {

  private final static String DEBUG_TAG = "CatapultAppcoinsBilling";
  public static final String RESPONSE_INAPP_PURCHASE_DATA = "INAPP_PURCHASE_DATA";
  public static final String RESPONSE_INAPP_SIGNATURE = "INAPP_DATA_SIGNATURE";
  public static final String RESPONSE_INAPP_PURCHASE_ID = "INAPP_PURCHASE_ID";

  public static boolean handleActivityResult(String signature, int resultCode, Intent data,
      PurchaseFinishedListener purchaseFinishedListener) {

    if (data == null) {
      logError("Null data in IAB activity result.");
      //purchaseFinishedListener.onPurchaseFinished(resultCode, null);
      return false;
    }

    int responseCode = getResponseCodeFromIntent(data);
    String purchaseData = data.getStringExtra(RESPONSE_INAPP_PURCHASE_DATA);
    String dataSignature = data.getStringExtra(RESPONSE_INAPP_SIGNATURE);
    String id = data.getStringExtra(RESPONSE_INAPP_PURCHASE_ID);

    if (resultCode == Activity.RESULT_OK && responseCode == ResponseCode.OK.getValue()) {
      logDebug("Successful resultcode from purchase activity.");
      logDebug("Purchase data: " + purchaseData);
      logDebug("Data signature: " + dataSignature);
      logDebug("Extras: " + data.getExtras());

      if (purchaseData == null || dataSignature == null) {
        logError("BUG: either purchaseData or dataSignature is null.");
        logDebug("Extras: " + data.getExtras()
            .toString());
        return false;
      }

      if (verifySignature(signature, purchaseData, dataSignature)) {
        JSONObject purchaseDataJSON = null;
        try {
          purchaseDataJSON = new JSONObject(purchaseData);
          purchaseFinishedListener.onPurchaseFinished(responseCode,
              getTokenFromJSON(purchaseDataJSON), getSkuFromJSON(purchaseDataJSON));
        } catch (JSONException e) {
          e.printStackTrace();
          return false;
        }

        return true;
      } else {
        //purchaseFinishedListener.onPurchaseFinished(responseCode, null);
        return false;
      }

  } else if(resultCode ==Activity.RESULT_OK)

  {
    // result code was OK, but in-app billing response was not OK.
    logDebug("Result code was OK but in-app billing response was not OK: " + getResponseDesc(
        responseCode));
    //purchaseFinishedListener.onPurchaseFinished(resultCode, null);
  } else if(resultCode ==Activity.RESULT_CANCELED)

  {

    logDebug("Purchase canceled - Response: " + getResponseDesc(responseCode));
    //purchaseFinishedListener.onPurchaseFinished(resultCode, null);
  } else

  {
    logError("Purchase failed. Result code: "
        + Integer.toString(resultCode)
        + ". Response: "
        + getResponseDesc(responseCode));
    //purchaseFinishedListener.onPurchaseFinished(resultCode, null);
  }
    return true;
}

  static boolean verifySignature(String signature, String purchaseData, String dataSignature) {

    if (!Security.verifyPurchase(signature, purchaseData, dataSignature)) {
      logError("Purchase signature verification FAILED");
      return false;
    }
    return true;
  }

  static int getResponseCodeFromIntent(Intent i) {
    Object o = i.getExtras()
        .get(Utils.RESPONSE_CODE);
    if (o == null) {
      logError("Intent with no response code, assuming OK (known issue)");
      return ResponseCode.OK.getValue();
    } else if (o instanceof Integer) {
      return ((Integer) o).intValue();
    } else if (o instanceof Long) {
      return (int) ((Long) o).longValue();
    } else {
      logError("Unexpected type for intent response code.");
      logError(o.getClass()
          .getName());
      throw new RuntimeException("Unexpected type for intent response code: " + o.getClass()
          .getName());
    }
  }

  static void logDebug(String msg) {
    Log.d(DEBUG_TAG, msg);
  }

  static void logError(String msg) {
    Log.e(DEBUG_TAG, "In-app billing error: " + msg);
  }

  static String getTokenFromJSON(JSONObject data) {
    return data.optString("purchaseToken");
  }

  static String getSkuFromJSON(JSONObject data) {
    return data.optString("productId");
  }

  public static String getResponseDesc(int code) {
    String[] iab_msgs = ("0:OK/1:User Canceled/2:Unknown/"
        + "3:Billing Unavailable/4:Item unavailable/"
        + "5:Developer Error/6:Error/7:Item Already Owned/"
        + "8:Item not owned").split("/");
    String[] iabhelper_msgs = ("0:OK/-1001:Remote exception during initialization/"
        + "-1002:Bad response received/"
        + "-1003:Purchase signature verification failed/"
        + "-1004:Send intent failed/"
        + "-1005:User cancelled/"
        + "-1006:Unknown purchase response/"
        + "-1007:Missing token/"
        + "-1008:Unknown error/"
        + "-1009:Subscriptions not available/"
        + "-1010:Invalid consumption attempt").split("/");

    if (code <= -1000) {
      int index = -1000 - code;
      if (index >= 0 && index < iabhelper_msgs.length) {
        return iabhelper_msgs[index];
      } else {
        return String.valueOf(code) + ":Unknown IAB Helper Error";
      }
    } else if (code < 0 || code >= iab_msgs.length) {
      return String.valueOf(code) + ":Unknown";
    } else {
      return iab_msgs[code];
    }
  }
}
