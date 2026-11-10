# ecos CLI — bash 补全（由 `ecos --completion bash` 输出；也可直接 source 本文件）
_ecos_completion() {
    local cur prev opts
    COMPREPLY=()
    cur="${COMP_WORDS[COMP_CWORD]}"
    prev="${COMP_WORDS[COMP_CWORD-1]}"

    local commands="doctor cognition capabilities help version"
    local global_opts="--output --base-url --token --token-file --profile --trace-id --timeout --dry-run --no-retry --all --page-size --max-pages --idempotency-key --follow --show-config --help --version"

    if [[ ${COMP_CWORD} -eq 1 ]]; then
        COMPREPLY=( $(compgen -W "${commands} ${global_opts}" -- "${cur}") )
        return 0
    fi

    case "${prev}" in
        --output)      COMPREPLY=( $(compgen -W "json table" -- "${cur}") ); return 0 ;;
        --profile)     COMPREPLY=( $(compgen -W "dev test prod" -- "${cur}") ); return 0 ;;
        --token-file)  COMPREPLY=( $(compgen -f -- "${cur}") ); return 0 ;;
        --base-url)    return 0 ;;
    esac

    COMPREPLY=( $(compgen -W "${global_opts}" -- "${cur}") )
    return 0
}
complete -F _ecos_completion ecos
