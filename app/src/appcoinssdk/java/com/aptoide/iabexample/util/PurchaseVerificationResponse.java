package com.aptoide.iabexample.util;

public class PurchaseVerificationResponse {
  private final Status status;

  public PurchaseVerificationResponse(Status status) {
    this.status = status;
  }

  public Status getStatus() {
    return status;
  }

  @Override public String toString() {
    return "PurchaseVerificationResponse{" + "status=" + status + '}';
  }

  public enum Status {
    SUCCESS, FAILED
  }
}
