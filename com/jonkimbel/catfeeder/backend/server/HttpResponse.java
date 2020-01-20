package com.jonkimbel.catfeeder.backend.server;

// TODO [CLEANUP]: Make @AutoValue.

public class HttpResponse {
  private final byte[] protobufBody;
  private final String htmlBody;
  private final Http.ResponseCode responseCode;

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

  public static Builder builder() {
    return new Builder();
  }

  private HttpResponse(String htmlBody, byte[] protobufBody, Http.ResponseCode responseCode) {
    this.htmlBody = htmlBody;
    this.protobufBody = protobufBody;
    this.responseCode = responseCode;
  }

  public static class Builder {
    private String htmlBody = "";
    private byte[] protobufBody = new byte[0];
    private Http.ResponseCode responseCode = Http.ResponseCode.NOT_IMPLEMENTED;

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

    public HttpResponse build() {
      return new HttpResponse(htmlBody, protobufBody, responseCode);
    }

    private Builder() {}
  }
}
