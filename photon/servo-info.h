#ifndef SERVO_INFO_H
#define SERVO_INFO_H

// A PWM-capable pin for controlling the servo.
#define SERVO_PIN D0

// 400Hz = 2500us wavelength.
// Chosen to exceed the MG996R's 2100us max pulse length.
#define SERVO_PWM_FREQ 400

// Max servo pulse range is 900us - 2100us, but we don't need that much.
// These values should be tuned by hand to fit the assembled product as servos
// and gear orientations will vary.
#define SERVO_EXTEND_DUTY_CYCLE 115
#define SERVO_JIGGLE_EXTEND_DUTY_CYCLE 190
#define SERVO_RETRACT_DUTY_CYCLE 211
#define SERVO_DISABLE_DUTY_CYCLE 0

// Set through testing, a bit high but guaranteed to be long enough for the
// servo to move
#define SERVO_MOVE_DELAY_MS 1500

// Set arbitrarily. It's OK if we cut off the jiggle partway through a movement,
// in fact it might impart more vibration to the hopper and loosen up the food
// more.
#define SERVO_JIGGLE_MOVE_DELAY_MS 100

#endif // SERVO_INFO_H
