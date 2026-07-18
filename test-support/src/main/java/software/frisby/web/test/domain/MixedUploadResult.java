package software.frisby.web.test.domain;

/**
 * Response body returned by {@code POST /upload/mixed} confirming all parts of a mixed
 * multipart request were received.
 *
 * @param fileName The filename of the file part.
 * @param fileSize The number of bytes in the file part.
 * @param metadata The raw JSON string of the {@code metadata} part.
 * @param category The text value of the {@code category} part.
 */
public record MixedUploadResult(String fileName, long fileSize, String metadata, String category) {
}

