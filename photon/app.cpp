#include <Wire.h>
#include <stdlib.h>
#include <pb_decode.h>
#include <pb_encode.h>
#include "array-list.h"
#include "cat_feeder.pb.h"
#include "http-client.h"
#include "servo-info.h"

// This file should #define BACKEND_DOMAIN. Defining a separate file allows me
// to hide my domain from source control via .gitignore.
#include "backend-info.h"

////////////////////////////////////////////////////////////////////////////////
// CONSTANTS.

#define MINIMUM_SCOOPS_TO_FEED 1

#define JAM_PREVENTION_JIGGLE_COUNT 3

////////////////////////////////////////////////////////////////////////////////
// METHOD DECLARATIONS.

// Feeds the cat by running the servo.
void feed();

// Moves the servo between its retracted position and the position specified by
// the duty cycle and delay. This oscillation is repeated N times, where
// N = cycles.
void cycle_servo(
    uint32_t cycles, uint32_t extend_duty_cycle, uint32_t move_delay_ms);

// Checks in with the server and updates variables.
void check_in();

// Sends a request to the server and returns the response.
// Inner method for check_in().
catfeeder_api_EmbeddedResponse sendRequest();

// Waits the given amount of time and advances the time-tracking variables.
void delayAndUpdateVariables(uint64_t delay_time_ms);

// Advances the time-tracking variables by the given length of time.
void updateVariables(uint64_t time_passed_ms);

// Wraps the given ArrayList in a pb_ostream_t, allowing for ArrayList output
// from proto decoding operations.
pb_ostream_t arrayListToOstream(ArrayList<pb_byte_t>* list);

// Callback used by arrayListToOstream().
static bool arrayListOstreamWrite(pb_ostream_t *stream, const pb_byte_t *buf, size_t count);

////////////////////////////////////////////////////////////////////////////////
// VARIABLES.

// Set up the HTTP client for communication with the backend, getting info on
// how and when the cat should be fed.
HttpClient httpClient(BACKEND_DOMAIN, "/photon", 80);

// How long to wait until doing the next feeding.
// When this is decremented to 0, feed_now should be set.
uint64_t delay_before_next_feeding_ms;
bool feed_now;

// How long to wait until doing the next server check in.
// When this is decremented to 0, check_in_now should be set.
uint64_t delay_before_next_check_in_ms;
bool check_in_now = true; // Immediately check in after a restart.

// How long we've waited since the last feeding. Only means anything if has_fed
// is set.
uint64_t time_since_last_feeding_ms;
bool has_fed;

// How many scoops to serve per feeding. Should not be set less than
// MINIMUM_SCOOPS_TO_FEED.
uint32_t scoops_to_feed = MINIMUM_SCOOPS_TO_FEED;

////////////////////////////////////////////////////////////////////////////////
// MAIN CODE.

void setup() {
  RGB.control(true);
  RGB.brightness(255);
  pinMode(SERVO_PIN, OUTPUT);

  // Retract the servo in case the device reset while the motor was running.
  analogWrite(
      /* pin = */ SERVO_PIN,
      /* value = */ SERVO_RETRACT_DUTY_CYCLE,
      /* frequency = */ SERVO_PWM_FREQ);
  delay(SERVO_MOVE_DELAY_MS);
  // Disable servo to prevent motor whine.
  analogWrite(
      /* pin = */ SERVO_PIN,
      /* value = */ SERVO_DISABLE_DUTY_CYCLE,
      /* frequency = */ SERVO_PWM_FREQ);
}

void loop() {
  if (feed_now) {
    feed_now = false;
    feed();
    time_since_last_feeding_ms = 0;
    has_fed = true;
    check_in_now = true;
  } else if (check_in_now) {
    check_in_now = false;
    int time_before_check_in_s = Time.now();
    check_in();
    int time_to_check_in_s = max(Time.now() - time_before_check_in_s, 0);
    updateVariables(/* time_passed_ms= */ time_to_check_in_s * 1000);
  } else {
    // If we're not feeding or checking in, we're waiting.
    if (delay_before_next_feeding_ms > 0
        && delay_before_next_feeding_ms < delay_before_next_check_in_ms) {
      delayAndUpdateVariables(delay_before_next_feeding_ms);
    } else if (delay_before_next_check_in_ms > 0) {
      delayAndUpdateVariables(delay_before_next_check_in_ms);
    } else {
      // We're in a bad state, so do an immediate check-in to fix it.
      // Waiting for 0ms won't advance the time counters.
      check_in_now = true;
    }
  }
}

void feed() {
  // Jiggle.
  cycle_servo(
      /* cycles = */ JAM_PREVENTION_JIGGLE_COUNT,
      /* extend_duty_cycle = */ SERVO_JIGGLE_EXTEND_DUTY_CYCLE,
      /* move_delay_ms = */ SERVO_JIGGLE_MOVE_DELAY_MS);

  // Feed.
  cycle_servo(
      /* cycles = */ scoops_to_feed,
      /* extend_duty_cycle = */ SERVO_EXTEND_DUTY_CYCLE,
      /* move_delay_ms = */ SERVO_MOVE_DELAY_MS);

  // Jiggle.
  cycle_servo(
      /* cycles = */ JAM_PREVENTION_JIGGLE_COUNT,
      /* extend_duty_cycle = */ SERVO_JIGGLE_EXTEND_DUTY_CYCLE,
      /* move_delay_ms = */ SERVO_JIGGLE_MOVE_DELAY_MS);

  // Disable servo to prevent motor whine.
  analogWrite(
      /* pin = */ SERVO_PIN,
      /* value = */ SERVO_DISABLE_DUTY_CYCLE,
      /* frequency = */ SERVO_PWM_FREQ);
}

void cycle_servo(
    uint32_t cycles, uint32_t extend_duty_cycle, uint32_t move_delay_ms) {
  for (uint32_t i = 0; i < cycles; i++) {
    analogWrite(
        /* pin = */ SERVO_PIN,
        /* value = */ extend_duty_cycle,
        /* frequency = */ SERVO_PWM_FREQ);
    delayAndUpdateVariables(move_delay_ms);

    analogWrite(
        /* pin = */ SERVO_PIN,
        /* value = */ SERVO_RETRACT_DUTY_CYCLE,
        /* frequency = */ SERVO_PWM_FREQ);
    delayAndUpdateVariables(move_delay_ms);
  }
}

void check_in() {
  catfeeder_api_EmbeddedResponse response = sendRequest();

  scoops_to_feed = max(response.scoops_to_feed, MINIMUM_SCOOPS_TO_FEED);
  delay_before_next_check_in_ms = response.delay_until_next_check_in_ms;
  if (delay_before_next_check_in_ms == 0) {
    check_in_now = true;
  }
  if (response.has_delay_until_next_feeding_ms) {
    delay_before_next_feeding_ms = response.delay_until_next_feeding_ms;
    if (response.delay_until_next_feeding_ms == 0) {
      feed_now = true;
    }
  } else {
    delay_before_next_feeding_ms = 0;
    feed_now = false;
  }
  if (response.last_feeding_time_consumed) {
    // Forget about the last feeding so we don't tell the server again.
    time_since_last_feeding_ms = 0;
    has_fed = false;
  }

  // TODO [V2]: get schedule from server, use RTC to keep feeding even if server has
  //            extended outage.

  // TODO [V2]: persist last schedule to nonvolatile storage so we can
  //            keep feeding in case of server outage & device reset.
}

catfeeder_api_EmbeddedResponse sendRequest() {
  // Red while connecting.
  RGB.color(/* red = */ 255, /* green = */ 0, /* blue = */ 0);
  if (!httpClient.connect()) {
    // Could not connect to backend.
    httpClient.disconnect();
    return catfeeder_api_EmbeddedResponse_init_default;
  }

  // Orange while building and encoding request message into `requestBuffer`.
  RGB.color(/* red = */ 252, /* green = */ 60, /* blue = */ 3);
  catfeeder_api_EmbeddedRequest request = catfeeder_api_EmbeddedRequest_init_default;
  if (has_fed) {
    request.has_time_since_last_feeding_ms = true;
    request.time_since_last_feeding_ms = time_since_last_feeding_ms;
  }

  ArrayList<uint8_t> requestBuffer;
  pb_ostream_t requestOstream = arrayListToOstream(&requestBuffer);
  if (!pb_encode(&requestOstream, catfeeder_api_EmbeddedRequest_fields, &request)) {
    // Could not encode request.
    httpClient.disconnect();
    return catfeeder_api_EmbeddedResponse_init_default;
  }

  // Yellow while sending request.
  RGB.color(/* red = */ 255, /* green = */ 250, /* blue = */ 0);
  httpClient.sendRequest(&requestBuffer);

  // Green while getting response.
  RGB.color(/* red = */ 0, /* green = */ 255, /* blue = */ 0);
  ArrayList<uint8_t> responseBuffer;
  Status status = httpClient.getResponse(&responseBuffer);
  httpClient.disconnect();

  // Blue while reading the response.
  RGB.color(/* red = */ 0, /* green = */ 0, /* blue = */ 255);
  if (status != HTTP_STATUS_OK) {
    return catfeeder_api_EmbeddedResponse_init_default;
  }

  // Decode response into `response`.
  pb_istream_t stream = pb_istream_from_buffer(
      responseBuffer.data, responseBuffer.length);
  catfeeder_api_EmbeddedResponse response = catfeeder_api_EmbeddedResponse_init_default;
  if (!pb_decode(&stream, catfeeder_api_EmbeddedResponse_fields, &response)) {
    // Got successful response from the server, but the body was malformed.
    return catfeeder_api_EmbeddedResponse_init_default;
  }

  // Purple when fetch & decode were both successful.
  RGB.color(/* red = */ 255, /* green = */ 0, /* blue = */ 255);
  return response;
}

void delayAndUpdateVariables(uint64_t delay_time_ms) {
  delay(delay_time_ms);
  updateVariables(delay_time_ms);
}

void updateVariables(uint64_t time_passed_ms) {
  if (delay_before_next_feeding_ms > time_passed_ms) {
    delay_before_next_feeding_ms -= time_passed_ms;
  } else if (delay_before_next_feeding_ms > 0) {
    delay_before_next_feeding_ms = 0;
    feed_now = true;
  }

  if (delay_before_next_check_in_ms > time_passed_ms) {
    delay_before_next_check_in_ms -= time_passed_ms;
  } else {
    delay_before_next_check_in_ms = 0;
    check_in_now = true;
  }

  if (has_fed) {
    time_since_last_feeding_ms += time_passed_ms;
  }
}

static bool arrayListOstreamWrite(pb_ostream_t *stream, const pb_byte_t *buf, size_t count) {
  // Add the provided bytes to the ArrayList.
  ArrayList<pb_byte_t>* list = (ArrayList<pb_byte_t>*)stream->state;
  for (size_t i = 0; i < count; i++) {
    if (!list->add(buf[i])) {
      return false;
    }
  }

  return true;
}

pb_ostream_t arrayListToOstream(ArrayList<pb_byte_t>* list) {
    pb_ostream_t stream;
    stream.state = list;
    stream.callback = &arrayListOstreamWrite;
    stream.bytes_written = 0;
    // Approx. maximum of int, which is the theoretical max capacity of ArrayLists.
    stream.max_size = 2000000000;
    return stream;
}
