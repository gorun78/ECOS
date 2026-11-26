package sandbox;

/**
 * 沙盒业务错误：携带显式 code，绝不被当成成功。
 *
 * <p>独立成包级类而非 {@code SandboxServer} 的嵌套类，是为了让
 * {@link CodeExecutor} 等协作类能直接抛出；早期做嵌套类导致 javac 报
 * 「找不到符号」。</p>
 */
final class SandboxError extends RuntimeException {

    final int code;

    SandboxError(int code, String message) {
        super(message);
        this.code = code;
    }
}
