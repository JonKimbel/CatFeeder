# This package contains a simple HTTP server implementation.

package(default_visibility = ["//com/jonkimbel/catfeeder/backend:__subpackages__"])

java_library(
    name = "server",
    srcs = [
        "Http.java",
        "HttpServer.java",
        "HttpHeader.java",
        "HttpResponse.java",
    ],
    deps = [
        "@maven//:org_checkerframework_checker_qual",
        ":map_parser",
    ],
)

java_library(
    name = "map_parser",
    srcs = [
        "MapParser.java",
    ],
)