package okhttp3;

/**
 * Compile-only stub for the host's okhttp3.Request.
 * 只声明 integrations 实际用到的成员, 签名必须与宿主一致。
 */
public class Request {

    public HttpUrl url() {
        return null;
    }

    public Headers headers() {
        return null;
    }

    public Builder newBuilder() {
        return null;
    }

    public static class Builder {

        public Builder headers(Headers headers) {
            return this;
        }

        public Request build() {
            return null;
        }
    }
}
