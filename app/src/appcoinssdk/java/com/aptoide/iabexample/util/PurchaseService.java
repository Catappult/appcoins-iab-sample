package com.aptoide.iabexample.util;

import android.util.Log;
import com.google.gson.Gson;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

public class PurchaseService {
  private static final String TAG = PurchaseService.class.getSimpleName();

  private final String baseHost;
  private final String applicationPackageName;
  private final PurchaseValidatorListener listener;
  private final Gson gson;

  public PurchaseService(String baseHost, String applicationPackageName,
      PurchaseValidatorListener listener, Gson gson) {
    this.baseHost = baseHost;
    this.applicationPackageName = applicationPackageName;
    this.listener = listener;
    this.gson = gson;
  }

  public void verifyPurchase(String sku, String token) {
    Thread thread = new Thread(() -> {
      HttpURLConnection conn = null;
      try {
        URL url = new URL(baseHost + "/purchase/" + applicationPackageName + "/check");
        conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);
        conn.setDoInput(true);
        JSONObject jsonParam = new JSONObject();
        jsonParam.put("token", token);
        jsonParam.put("product", sku);
        Log.i("JSON", jsonParam.toString());
        DataOutputStream os = new DataOutputStream(conn.getOutputStream());
        os.writeBytes(jsonParam.toString());
        os.flush();
        os.close();

        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
          PurchaseVerificationResponse purchaseVerificationResponse =
              gson.fromJson(new InputStreamReader(conn.getInputStream()),
                  PurchaseVerificationResponse.class);
          Log.i(TAG, purchaseVerificationResponse.toString());
          listener.onPurchaseValidationResult(sku, token, purchaseVerificationResponse.getStatus()
              == PurchaseVerificationResponse.Status.SUCCESS);
        } else {
          listener.onPurchaseValidationError(sku, token, new Exception(
              "Response code: " + responseCode + "\n" + "Message: " + conn.getResponseMessage()));
        }
      } catch (Exception e) {
        listener.onPurchaseValidationError(sku, token, e);
        e.printStackTrace();
      } finally {
        if (conn != null) {
          conn.disconnect();
        }
      }
    });
    thread.start();
  }

  public interface PurchaseValidatorListener {
    void onPurchaseValidationResult(String sku, String token, boolean isValid);

    void onPurchaseValidationError(String sku, String token, Throwable error);
  }
}
