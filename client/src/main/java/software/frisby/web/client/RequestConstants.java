package software.frisby.web.client;

final class RequestConstants {
    static final String RESPONSE_TYPE_ARGUMENT_NAME = "responseType";
    static final String BODY_ARGUMENT_NAME = "body";
    static final String COMPRESSOR_ARGUMENT_NAME = "compressor";

    static final String COMPRESS_WITH_FORM_ERROR =
            "The 'compress' value is invalid.  Compression is only supported for JSON entity bodies.";

    private RequestConstants() {
    }
}
