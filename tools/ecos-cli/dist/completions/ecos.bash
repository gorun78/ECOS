# ecos CLI — bash 补全（由 `ecos --completion bash` 生成，请勿手改）
_ecos_completion() {
    local cur prev
    COMPREPLY=()
    cur="${COMP_WORDS[COMP_CWORD]}"
    prev="${COMP_WORDS[COMP_CWORD-1]}"

    if [[ ${COMP_CWORD} -eq 1 ]]; then
        COMPREPLY=( $(compgen -W "doctor cognition capabilities agent ontology knowledge action model runtime help version --output --base-url --token --token-file --profile --trace-id --timeout --dry-run --no-retry --all --page-size --max-pages --idempotency-key --approval-token --body-json --follow --last-event-id --show-config --preflight --help --version --completion" -- "${cur}") )
        return 0
    fi

    case "${COMP_WORDS[1]}" in
        cognition) COMPREPLY=( $(compgen -W "models list model validate run" -- "${cur}") ); return 0 ;;
        capabilities) COMPREPLY=( $(compgen -W "list" -- "${cur}") ); return 0 ;;
        agent) COMPREPLY=( $(compgen -W "run inspect stop readiness" -- "${cur}") ); return 0 ;;
        ontology) COMPREPLY=( $(compgen -W "validate publish" -- "${cur}") ); return 0 ;;
        knowledge) COMPREPLY=( $(compgen -W "extract publish" -- "${cur}") ); return 0 ;;
        action) COMPREPLY=( $(compgen -W "commit" -- "${cur}") ); return 0 ;;
        model) COMPREPLY=( $(compgen -W "list route" -- "${cur}") ); return 0 ;;
        runtime) COMPREPLY=( $(compgen -W "doctor" -- "${cur}") ); return 0 ;;
    esac

    case "${prev}" in
        --output)  COMPREPLY=( $(compgen -W "json table" -- "${cur}") ); return 0 ;;
        --token-file|--body-json) COMPREPLY=( $(compgen -f -- "${cur}") ); return 0 ;;
    esac

    COMPREPLY=( $(compgen -W "--output --base-url --token --token-file --profile --trace-id --timeout --dry-run --no-retry --all --page-size --max-pages --idempotency-key --approval-token --body-json --follow --last-event-id --show-config --preflight --help --version --completion" -- "${cur}") )
    return 0
}
complete -F _ecos_completion ecos
