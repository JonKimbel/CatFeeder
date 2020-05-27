# This package contains code for alerting the server admin about CatFeeder
# issues.

package(default_visibility = ["//com/jonkimbel/catfeeder/backend:__subpackages__"])

java_library(
    name = "outage_notifier",
    srcs = [
        "OutageNotifier.java",
        "TwilioInfo.java"
    ],
    deps = [
        "@maven//:org_checkerframework_checker_qual",
        "@maven//:com_twilio_sdk_twilio",
    ],
)
