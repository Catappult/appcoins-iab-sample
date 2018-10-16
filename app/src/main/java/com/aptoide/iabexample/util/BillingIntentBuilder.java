package com.aptoide.iabexample.util;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import com.asf.appcoins.sdk.contractproxy.AppCoinsAddressProxyBuilder;
import com.asf.appcoins.sdk.contractproxy.AppCoinsAddressProxySdk;
import com.google.gson.Gson;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.util.Formatter;
import org.spongycastle.util.encoders.Hex;

public class BillingIntentBuilder {
  private static final int MAIN_NETWORK_ID = 1;
  private static final int ROPSTEN_NETWORK_ID = 3;

  public static PendingIntent buildBuyIntent(Context context, String skuId, String value,
      String developerAddress, String packageName, String paymentType, String payload,
      boolean debug) {
    AppCoinsAddressProxySdk proxySdk = new AppCoinsAddressProxyBuilder().createAddressProxySdk();
    int networkId = debug ? ROPSTEN_NETWORK_ID : MAIN_NETWORK_ID;

    Single<String> getTokenContractAddress = proxySdk.getAppCoinsAddress(networkId)
        .subscribeOn(Schedulers.io());
    Single<String> getIabContractAddress = proxySdk.getIabAddress(networkId)
        .subscribeOn(Schedulers.io());

    return Single.zip(getTokenContractAddress, getIabContractAddress,
        (tokenContractAddress, iabContractAddress) -> buildPaymentIntent(context, networkId, skuId,
            value, tokenContractAddress, iabContractAddress, developerAddress, packageName,
            paymentType, payload))
        .blockingGet();
  }

  private static PendingIntent buildPaymentIntent(Context context, int networkId, String skuId,
      String value, String tokenContractAddress, String iabContractAddress, String developerAddress,
      String packageName, String paymentType, String payload) {

    BigDecimal amount = new BigDecimal(value);
    amount = amount.multiply(BigDecimal.TEN.pow(18));

    Intent intent = new Intent(Intent.ACTION_VIEW);
    Uri data = Uri.parse(
        buildUriString(tokenContractAddress, iabContractAddress, amount, developerAddress, skuId,
            networkId, packageName, paymentType, payload));
    intent.setData(data);

    return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
  }

  private static String buildUriString(String tokenContractAddress, String iabContractAddress,
      BigDecimal amount, String developerAddress, String skuId, int networkId, String packageName,
      String paymentType, String payload) {

    StringBuilder stringBuilder = new StringBuilder(4);
    try {
      Formatter formatter = new Formatter(stringBuilder);
      formatter.format(
          "ethereum:%s@%d/buy?uint256=%s&address=%s&data=%s&iabContractAddress=%s",
          tokenContractAddress, networkId, amount.toString(), developerAddress,
          buildUriData(skuId, packageName, paymentType, payload), iabContractAddress);
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("UTF-8 not supported!", e);
    }

    return stringBuilder.toString();
  }

  private static String buildUriData(String skuId, String packageName, String paymentType,
      String payload) throws UnsupportedEncodingException {
    return "0x" + Hex.toHexString(
        new Gson().toJson(new TransactionData(paymentType, packageName, skuId, payload))
            .getBytes("UTF-8"));
  }

  public static class TransactionData {
    public static final String TYPE_INAPP = "INAPP";
    public static final String TYPE_DONATION = "DONATION";

    String type;
    String domain;
    String skuId;
    String payload;

    public TransactionData(String type, String domain, String skuId, String payload) {
      this.type = type;
      this.domain = domain;
      this.skuId = skuId;
      this.payload = payload;
    }
  }
}
