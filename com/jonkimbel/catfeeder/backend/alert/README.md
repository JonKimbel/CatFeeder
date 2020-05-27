Before this code will compile, you will need to add a file named
`TwilioInfo.java` in this directory containing the following:

```java
package com.jonkimbel.catfeeder.backend.alert;

private final class TwilioInfo {
    // Find your Account Sid and Auth Token at twilio.com/console
    public static final String ACCOUNT_SID = "your_account_sid";
    public static final String AUTH_TOKEN = "your_auth_token";
    public static final String TWILIO_PHONE_NUMBER = "+11234567890";

    public static final String PHONE_NUMBER_TO_ALERT = "+11234567890";
}
```
