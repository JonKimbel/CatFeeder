package com.jonkimbel.catfeeder.backend.server;

import org.checkerframework.checker.nullness.qual.Nullable;

// TODO [V3]: Make @AutoValue.

public class HttpResponse {
  private final byte[] protobufBody;
  private final String htmlBody;
  private final Http.ResponseCode responseCode;
  private final @Nullable String locationUrl;

  public boolean isBodyHtml() {
    return htmlBody.length() > protobufBody.length;
  }

  public String getHtmlBody() {
    return htmlBody;
  }

  public byte[] getProtobufBody() {
    return protobufBody;
  }

  public Http.ResponseCode getResponseCode() {
    return responseCode;
  }

  public @Nullable String getLocationUrl() {
    return locationUrl;
  }

  public static Builder builder() {
    return new Builder();
  }

  private HttpResponse(
      String htmlBody,
      byte[] protobufBody,
      Http.ResponseCode responseCode,
      @Nullable String locationUrl) {
    this.htmlBody = htmlBody;
    this.protobufBody = protobufBody;
    this.responseCode = responseCode;
    this.locationUrl = locationUrl;
  }

  public static class Builder {
    private String htmlBody = "";
    private byte[] protobufBody = new byte[0];
    private Http.ResponseCode responseCode = Http.ResponseCode.NOT_IMPLEMENTED;
    private @Nullable String locationUrl;

    public Builder setHtmlBody(String htmlBody) {
      this.htmlBody = htmlBody;
      this.protobufBody = new byte[0];
      return this;
    }

    public Builder setProtobufBody(byte[] protobufBody) {
      this.protobufBody = protobufBody;
      this.htmlBody = "";
      return this;
    }

    public Builder setResponseCode(Http.ResponseCode responseCode) {
      this.responseCode = responseCode;
      return this;
    }

    public Builder setLocation(String locationUrl) {
      this.locationUrl = locationUrl;
      return this;
    }

    public HttpResponse build() {
      return new HttpResponse(htmlBody, protobufBody, responseCode, locationUrl);
    }

    private Builder() {}
  }
}
