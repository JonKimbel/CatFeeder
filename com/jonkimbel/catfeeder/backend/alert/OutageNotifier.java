package com.jonkimbel.catfeeder.backend.alert;

import org.checkerframework.checker.nullness.qual.Nullable;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import java.util.Timer;
import java.util.TimerTask;

public class OutageNotifier {
  public static final OutageNotifier INSTANCE = new OutageNotifier();

  @Nullable private TimerTask ongoingTimeout;

  private final Timer timer = new Timer(
      "OutageNotifierTimer", /* isDaemon = */ true);

  private OutageNotifier() {
    Twilio.init(TwilioInfo.ACCOUNT_SID, TwilioInfo.AUTH_TOKEN);
  }

  /*
   * After this message is called, if it isn't called again within
   * {@code delayMs}, an SMS message will be sent to alert the admin of an
   * outage.
   */
  public synchronized void alertIfNotCalledWithin(
      long delayMs, String message) {
    ongoingTimeout.cancel();

    ongoingTimeout = onTimeout(() -> alert(message));

    timer.schedule(ongoingTimeout, delayMs);
  }

  /* Calling this method will send an SMS message to the admin. */
  public synchronized void alert(String message) {
    Message
        .creator(
            /* to = */ new PhoneNumber(TwilioInfo.PHONE_NUMBER_TO_ALERT),
            /* from = */ new PhoneNumber(TwilioInfo.TWILIO_PHONE_NUMBER),
            /* message = */ message)
        .create();
  }

  private static TimerTask onTimeout(Runnable timeoutRunnable) {
    return new TimerTask() {
      @Override
      public void run() {
        timeoutRunnable.run();
      }
    };
  }
}
