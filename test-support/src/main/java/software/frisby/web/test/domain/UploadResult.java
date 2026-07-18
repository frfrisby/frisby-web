package software.frisby.web.test.domain;

/**
 * Response body returned by {@code POST /upload} confirming a file was received.
 *
 * @param fileName    The filename extracted from the {@code Content-Disposition} header of the file part.
 * @param size        The number of bytes received.
 * @param contentType The {@code Content-Type} of the file part as received by the server
 *                    (e.g. {@code "application/octet-stream"}).
 */
public record UploadResult(String fileName, long size, String contentType) {
}

