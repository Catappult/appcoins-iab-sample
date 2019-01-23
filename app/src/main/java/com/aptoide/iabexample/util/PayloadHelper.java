package com.aptoide.iabexample.util;

import android.net.Uri;
import android.support.annotation.Nullable;
import com.appcoins.billing.AppcoinsBilling;

/**
 * Intent payload helper class that provide a way to send the developers wallet address together
 * with an already existent developers payload when using {@link AppcoinsBilling#getBuyIntent}.
 *
 * The use of this helper is mandatory even if there is no  existing payload, because it allows for
 * a payment to be delivered to the developers ethereum address.
 *
 * This class must be imported to your project and used without any changes to be compatible with
 * the Appcoins billing process.
 */
public class PayloadHelper {
  private static final String SCHEME = "appcoins";
  private static final String PAYLOAD_PARAMETER = "payload";
  private static final String ORDER_PARAMETER = "order_reference";
  private static final String ORIGIN_PARAMETER = "origin";

  /**
   * Method to build the payload required on the {@link AppcoinsBilling#getBuyIntent} method.
   *
   * @param developerPayload The additional payload to be sent
   * @param orderReference a reference that allows the developers to identify this order in
   * server-to-server communication
   *
   * @return The final developers payload to be sent
   */
  public static String buildIntentPayload(@Nullable String orderReference,
      @Nullable String developerPayload, @Nullable String origin) {
    Uri.Builder builder = new Uri.Builder();
    builder.scheme(SCHEME)
        .authority("appcoins.io");
    if (developerPayload != null) {
      builder.appendQueryParameter(PAYLOAD_PARAMETER, developerPayload);
    }
    if (orderReference != null) {
      builder.appendQueryParameter(ORDER_PARAMETER, orderReference);
    }
    if (orderReference != null) {
      builder.appendQueryParameter(ORIGIN_PARAMETER, origin);
    }
    return builder.toString();
  }

  /**
   * Given a uri string validate if it is part of the expected scheme and if so return the
   * addition payload content.
   *
   * @param uriString The payload uri content
   *
   * @return The additional payload content
   */
  public static String getPayload(String uriString) {
    Uri uri = checkRequirements(uriString);
    if (uri == null) return null;
    return uri.getQueryParameter(PAYLOAD_PARAMETER);
  }

  /**
   * Given a uri string validate if it is part of the expected scheme and if so return the
   * order reference content.
   *
   * @param uriString The payload uri content
   *
   * @return The oder reference content
   */
  public static String getOrderReference(String uriString) {
    Uri uri = checkRequirements(uriString);
    if (uri == null) return null;
    return uri.getQueryParameter(ORDER_PARAMETER);
  }

  /**
   * Given a uri string validate if it is part of the expected scheme and if so return the
   * payment origin content.
   *
   * @param uriString The payload uri content
   *
   * @return The payment origin content
   */
  public static String getOrigin(String uriString) {
    Uri uri = checkRequirements(uriString);
    if (uri == null) return null;
    return uri.getQueryParameter(ORIGIN_PARAMETER);
  }

  /**
   * Given a uri string validate if its content is valid and return the URI
   *
   * @param uriString The payload uri content
   *
   * @return The converted URI
   */
  @Nullable private static Uri checkRequirements(String uriString) {
    if (uriString == null) {
      return null;
    }
    Uri uri = Uri.parse(uriString);
    if (!uri.getScheme()
        .equalsIgnoreCase(SCHEME)) {
      throw new IllegalArgumentException();
    }
    return uri;
  }
}
