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
    ],
)

java_library(
    name = "query_parser",
    srcs = [
        "MapParser.java",
    ],
)