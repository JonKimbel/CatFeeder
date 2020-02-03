# This package contains API for storing and fetching information in a non-volatile way.

package(default_visibility = ["//com/jonkimbel/catfeeder/backend:__subpackages__"])

java_library(
    name = "api",
    srcs = [
        "PreferencesStorage.java",
        "PasswordStorage.java",
    ],
    deps = [
        "//com/jonkimbel/catfeeder/backend/storage",
        "//com/jonkimbel/catfeeder/backend/proto:preferences_java_proto",
        "@maven//:org_checkerframework_checker_qual",
    ]
)