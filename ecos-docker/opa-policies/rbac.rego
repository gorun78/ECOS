package ecos.rbac

default allow = false

allow {
    input.action == "pipeline.execute"
    input.role == "system"
}

allow {
    input.action == "read"
    input.role != ""
}

allow {
    input.action == "list"
    input.role != ""
}

allow {
    input.action == "get"
    input.role != ""
}
