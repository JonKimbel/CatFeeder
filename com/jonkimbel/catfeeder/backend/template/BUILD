package(default_visibility = ["//com/jonkimbel/catfeeder/backend:__subpackages__"])

java_library(
    name = "template",
    srcs = [
        "TemplateFiller.java",
        "Template.java",
    ],
    deps = [
        ":token_finder",
        "//com/jonkimbel/catfeeder/backend/template/data",
    ],
)

java_library(
    name = "token_finder",
    srcs = [
        "TokenFinder.java",
    ],
)