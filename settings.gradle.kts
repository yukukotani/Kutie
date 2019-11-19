rootProject.name = "kutie"

include(
    "cli",
    "doc",
    "example"
)
findProject(":cli")?.name = "cli"
findProject(":doc")?.name = "doc"
findProject(":example")?.name = "example"

