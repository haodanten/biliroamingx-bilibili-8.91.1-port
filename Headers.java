package okhttp3;

/**
 * Compile-only stub for the host's okhttp3.Headers.
 * 只声明 integrations 实际用到的成员, 签名必须与宿主一致。
 */
public class Headers {

    public String get(String name) {
        return null;
    }

    public Builder newBuilder() {
        return null;
    }

    public static class Builder {

        public Builder set(String name, String value) {
            return this;
        }

        public Headers build() {
            return null;
        }
    }
}
