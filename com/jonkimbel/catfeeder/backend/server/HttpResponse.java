package com.jonkimbel.catfeeder.backend.server;

// TODO [CLEANUP]: Make @AutoValue.

public class HttpResponse {
  private final byte[] byteBody;
  private final String printBody;
  private final Http.ResponseCode responseCode;

  public boolean isBodyPrint() {
    return printBody.length() > byteBody.length;
  }

  public String getPrintBody() {
    return printBody;
  }

  public byte[] getByteBody() {
    return byteBody;
  }

  public Http.ResponseCode getResponseCode() {
    return responseCode;
  }

  public static Builder builder() {
    return new Builder();
  }

  private HttpResponse(String printBody, byte[] byteBody, Http.ResponseCode responseCode) {
    this.printBody = printBody;
    this.byteBody = byteBody;
    this.responseCode = responseCode;
  }

  public static class Builder {
    private String printBody = "";
    private byte[] byteBody = new byte[0];
    private Http.ResponseCode responseCode = Http.ResponseCode.NOT_IMPLEMENTED;

    public Builder setPrintBody(String printBody) {
      this.printBody = printBody;
      this.byteBody = new byte[0];
      return this;
    }

    public Builder setByteBody(byte[] byteBody) {
      this.byteBody = byteBody;
      this.printBody = "";
      return this;
    }

    public Builder setResponseCode(Http.ResponseCode responseCode) {
      this.responseCode = responseCode;
      return this;
    }

    public HttpResponse build() {
      return new HttpResponse(printBody, byteBody, responseCode);
    }

    private Builder() {}
  }
}
