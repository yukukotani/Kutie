rootProject.name = "kutie"

include(
    "cli",
    "doc",
    "printer-kotlin",
    "example"
)
findProject(":cli")?.name = "cli"
findProject(":doc")?.name = "doc"
findProject(":printer-kotlin")?.name = "printer-kotlin"
findProject(":example")?.name = "example"

