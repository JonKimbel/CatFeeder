package(default_visibility = ["//com/jonkimbel/catfeeder:__subpackages__"])

java_library(
    name = "storage",
    srcs = [
        "Storage.java",
    ],
    deps = [
        "//com/jonkimbel/catfeeder/storage/api",
        "//com/jonkimbel/catfeeder/storage/parsers",
        "@maven//:org_xerial_sqlite_jdbc",
        "@maven//:org_checkerframework_checker_qual",
    ],
)