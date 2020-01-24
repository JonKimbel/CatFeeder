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

// TODO: add a test button/switch.

// TODO [CLEANUP]: redesign the food drop funnel vertical walls, put a slow
// slope throughout the top of the food tube so there's never a pinch.

#define SERVO_MOVE_DELAY_MS 1500

#define MINIMUM_SCOOPS_TO_FEED 1

////////////////////////////////////////////////////////////////////////////////
// METHOD DECLARATIONS.

void feed();
void check_in();
catfeeder_api_EmbeddedResponse sendRequest();
void delayAndUpdateVariables(uint64_t delay_time_ms);
void updateVariables(uint64_t time_passed_ms);
static bool arrayListOstreamWrite(pb_ostream_t *stream, const pb_byte_t *buf, size_t count);
pb_ostream_t arrayListToOstream(ArrayList<pb_byte_t>* list);

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
  pinMode(SERVO_PIN, OUTPUT);

  analogWrite(/* pin = */ SERVO_PIN, /* value = */ SERVO_RETRACT_DUTY_CYCLE, /* frequency = */ SERVO_PWM_FREQ);
}

void loop() {
  if (feed_now) {
    feed();
    time_since_last_feeding_ms = 0;
    has_fed = true;
    feed_now = false;
  } else if (check_in_now) {
    check_in();
    check_in_now = false;
  } else {
    // If we're not feeding or checking in, we're waiting.
    delayAndUpdateVariables(min(delay_before_next_feeding_ms, delay_before_next_check_in_ms));
  }
}

void feed() {
  // Dispense food.
  for (uint32_t i = 0; i < scoops_to_feed; i++) {
    // TODO: go back to running the servos before final deploy.
    // analogWrite(/* pin = */ SERVO_PIN, /* value = */ SERVO_EXTEND_DUTY_CYCLE, /* frequency = */ SERVO_PWM_FREQ);
    for (int blink = 0; blink < 3; blink++) {
      RGB.brightness(0);
      delayAndUpdateVariables(500);
      RGB.brightness(255);
      delayAndUpdateVariables(500);
    }
    delayAndUpdateVariables(SERVO_MOVE_DELAY_MS);
    // analogWrite(/* pin = */ SERVO_PIN, /* value = */ SERVO_RETRACT_DUTY_CYCLE, /* frequency = */ SERVO_PWM_FREQ);
    // delayAndUpdateVariables(SERVO_MOVE_DELAY_MS);
  }
}

void check_in() {
  catfeeder_api_EmbeddedResponse response = sendRequest();
  // Assume a request always takes 5s.
  updateVariables(/* time_passed_ms= */ 5000);

  scoops_to_feed = max(response.scoops_to_feed, MINIMUM_SCOOPS_TO_FEED);
  delay_before_next_check_in_ms = response.delay_until_next_check_in_ms;
  if (delay_before_next_check_in_ms == 0) {
    check_in_now = true;
  }
  if (response.has_delay_until_next_feeding_ms) {
    delay_before_next_feeding_ms = response.delay_until_next_feeding_ms;
  } else {
    delay_before_next_feeding_ms = 0;
    feed_now = false;
  }

  // TODO: get schedule from server, use RTC to keep feeding even if server has
  // extended outage.

  // TODO [CLEANUP]: persist last schedule to nonvolatile storage so we can
  // keep feeding in case of server outage & device reset.
}

catfeeder_api_EmbeddedResponse sendRequest() {
  catfeeder_api_EmbeddedResponse response = catfeeder_api_EmbeddedResponse_init_default;

  // Red while connecting.
  RGB.color(/* red = */ 255, /* green = */ 0, /* blue = */ 0);
  if (!httpClient.connect()) {
    // Could not connect to backend.
    httpClient.disconnect();
    return response; // Default response.
  }

  // Orange while building and encoding request message into `requestBuffer`.
  RGB.color(/* red = */ 252, /* green = */ 60, /* blue = */ 3);
  catfeeder_api_EmbeddedRequest request = catfeeder_api_EmbeddedRequest_init_default;
  // TODO: revert after debugging.
  request.has_time_since_last_feeding_ms = true;
  request.time_since_last_feeding_ms = time_since_last_feeding_ms;
//  if (has_fed) {
//    request.has_time_since_last_feeding_ms = true;
//    request.time_since_last_feeding_ms = time_since_last_feeding_ms;
//  }

  ArrayList<uint8_t> requestBuffer;
  pb_ostream_t requestOstream = arrayListToOstream(&requestBuffer);
  if (!pb_encode(&requestOstream, catfeeder_api_EmbeddedRequest_fields, &request)) {
    // Could not encode request.
    httpClient.disconnect();
    return response; // Default response.
  }

  // Yellow while sending request.
  RGB.color(/* red = */ 255, /* green = */ 250, /* blue = */ 0);
  httpClient.sendRequest(&requestBuffer);

  // Green while getting response.
  RGB.color(/* red = */ 0, /* green = */ 255, /* blue = */ 0);
  ArrayList<uint8_t> responseBuffer;
  Status status = httpClient.getResponse(&responseBuffer);
  httpClient.disconnect();

  // Blue while dealing with response.
  RGB.color(/* red = */ 0, /* green = */ 0, /* blue = */ 255);
  if (status != HTTP_STATUS_OK) {
    // Connected to backend but got bad response. Orange.
    RGB.color(/* red = */ 252, /* green = */ 60, /* blue = */ 3);
    return response; // Default response.
  }

  // Decode response into `response`.
  pb_istream_t stream = pb_istream_from_buffer(
      responseBuffer.data, responseBuffer.length);
  if (!pb_decode(&stream, catfeeder_api_EmbeddedResponse_fields, &response)) {
    // Got successful response from the server, but the body was malformed.
    return response; // Default response.
  }

  // Violet when fetch & decode were both successful.
  RGB.color(/* red = */ 255, /* green = */ 0, /* blue = */ 255);
  return response; // Actual response.
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
  } else if (delay_before_next_check_in_ms > 0) {
    delay_before_next_check_in_ms = 0;
    check_in_now = true;
  }

  if (has_fed) {
    time_since_last_feeding_ms += time_passed_ms;
  }
}

static bool arrayListOstreamWrite(pb_ostream_t *stream, const pb_byte_t *buf, size_t count) {
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
    stream.max_size = 2000000000;  // Approx. maximum of int.
    stream.bytes_written = 0;
    return stream;
}