#include <Wire.h>
#include <stdlib.h>
#include <pb_decode.h>
#include "array-list.h"
#include "cat_feeder.pb.h"
#include "http-client.h"
#include "servo-info.h"

// This file should #define BACKEND_DOMAIN. Defining a separate file allows me
// to hide my domain from source control via .gitignore.
#include "backend-info.h"

////////////////////////////////////////////////////////////////////////////////
// CONSTANTS.

// TODO: add a big cap across the 5V line to prevent brownouts or get a bigger
// power supply.

// TODO [CLEANUP]: redesign the food drop funnel vertical walls, put a slow
// slope throughout the top of the food tube so there's never a pinch.

// How long to wait after a failed fetch before retrying. This must be
// significantly longer than it takes for a failed fetch to occur because we
// don't increment our "to_delay" variables to account for the fetch timeout.
// Keeping this value high keeps the error there low.
// TODO [CLEANUP]: Account for fetch timeout and reduce this value.
#define FAILURE_RETRY_DELAY_MS 60000

#define SERVO_MOVE_DELAY_MS 1500

#define MINIMUM_SCOOPS_TO_FEED 1

////////////////////////////////////////////////////////////////////////////////
// METHOD DECLARATIONS.

void feed();
void check_in();
void delayAndUpdateVariables(uint64_t delay_time_ms);

////////////////////////////////////////////////////////////////////////////////
// VARIABLES.

// Set up the HTTP client for communication with the backend, getting info on
// how and when the cat should be fed.
HttpClient httpClient(BACKEND_DOMAIN, "/photon", 80);

// Buffer used to hold raw data from the server.
ArrayList<uint8_t> responseBuffer;

// How long to wait until doing the next feeding.
// When this is decremented to 0, feed_now should be set.
uint64_t delay_before_next_feeding_ms;
bool feed_now;

// How long to wait until doing the next server check in.
// When this is decremented to 0, check_in_now should be set.
uint64_t delay_before_next_check_in_ms;
bool check_in_now = true; // Immediately check in after a restart.

// How many scoops to serve per feeding. Setting this less than
// MINIMUM_SCOOPS_TO_FEED will result in MINIMUM_SCOOPS_TO_FEED scoops being
// fed.
uint32_t scoops_to_feed;

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
  for (uint32_t i = 0; i < max(scoops_to_feed, MINIMUM_SCOOPS_TO_FEED); i++) {
    analogWrite(/* pin = */ SERVO_PIN, /* value = */ SERVO_EXTEND_DUTY_CYCLE, /* frequency = */ SERVO_PWM_FREQ);
    delayAndUpdateVariables(SERVO_MOVE_DELAY_MS);
    analogWrite(/* pin = */ SERVO_PIN, /* value = */ SERVO_RETRACT_DUTY_CYCLE, /* frequency = */ SERVO_PWM_FREQ);
    delayAndUpdateVariables(SERVO_MOVE_DELAY_MS);
  }
}

void check_in() {
  // Dim LED while attempting to make connection.
  RGB.brightness(64);

  if (!httpClient.connect()) {
    // Couldn't connect to backend. Red.
    RGB.color(/* red = */ 255, /* green = */ 0, /* blue = */ 0);
    RGB.brightness(255);
    delayAndUpdateVariables(FAILURE_RETRY_DELAY_MS);
    return;
  }

  // TODO: send up EmbeddedResponse.
  responseBuffer.clear();
  httpClient.sendRequest();
  Status status = httpClient.getResponse(&responseBuffer);

  if (status != HTTP_STATUS_OK) {
    // Connected to backend but got bad response. Orange.
    RGB.color(/* red = */ 252, /* green = */ 60, /* blue = */ 3);
    RGB.brightness(255);
    delayAndUpdateVariables(FAILURE_RETRY_DELAY_MS);
    return;
  }

  // Got successful response from backend. Green.
  RGB.color(/* red = */ 0, /* green = */ 255, /* blue = */ 0);
  RGB.brightness(255);

  // Decode response.
  catfeeder_api_EmbeddedResponse response = catfeeder_api_EmbeddedResponse_init_default;
  pb_istream_t stream = pb_istream_from_buffer(
      responseBuffer.data, responseBuffer.length);
  if (!pb_decode(&stream, catfeeder_api_EmbeddedResponse_fields, &response)) {
    // Got successful response from the server, but the body was malformed.
    // Purple.
    RGB.color(/* red = */ 255, /* green = */ 0, /* blue = */ 255);
    RGB.brightness(255);
    delayAndUpdateVariables(FAILURE_RETRY_DELAY_MS);
    return;
  }

  // Fetch & decode were both successful.

  scoops_to_feed = response.scoops_to_feed;
  delay_before_next_check_in_ms = response.delay_until_next_check_in_ms;
  if (response.has_delay_until_next_feeding_ms) {
    delay_before_next_feeding_ms = response.delay_until_next_feeding_ms;
  } else {
    delay_before_next_feeding_ms = 0;
    feed_now = false;
  }

  // TODO: get schedule from server, use RTC to keep feeding even if server has
  // extended outage.

  // TODO [CLEANUP]: persist these variables to nonvolatile storage so we can
  // keep feeding in case of server outage & device reset.
}

void delayAndUpdateVariables(uint64_t delay_time_ms) {
  delay(delay_time_ms);

  if (delay_before_next_feeding_ms > delay_time_ms) {
    delay_before_next_feeding_ms -= delay_time_ms;
  } else if (delay_before_next_feeding_ms > 0) {
    delay_before_next_feeding_ms = 0;
    feed_now = true;
  }

  if (delay_before_next_check_in_ms > delay_time_ms) {
    delay_before_next_check_in_ms -= delay_time_ms;
  } else if (delay_before_next_check_in_ms > 0) {
    delay_before_next_check_in_ms = 0;
    check_in_now = true;
  }
}
