package software.frisby.web.client;

import software.frisby.core.validation.Sequences;

import java.util.List;

/**
 * The request body for a {@code multipart/form-data} HTTP request.
 * <p>
 * A {@link FormData} is an ordered collection of {@link FormPart} instances.  Parts
 * are transmitted in the order they were provided; callers control the ordering.
 * <p>
 * Create an instance via {@link #of(FormPart...)} or {@link #of(List)}.
 *
 * <pre>{@code
 * // File only
 * FormData formData = FormData.of(
 *         FormPart.file("file", stream, "report.pdf")
 * );
 *
 * // File with a JSON metadata entity and a plain-text scalar field
 * FormData formData = FormData.of(
 *         FormPart.file("file", stream, "report.pdf", fileSize),
 *         FormPart.json("metadata", documentMetadata),
 *         FormPart.text("category", "invoices")
 * );
 * }</pre>
 *
 * @see FormPart
 * @see PostSpec#body(FormData)
 * @see PutSpec#body(FormData)
 */
public final class FormData {
    private static final String PARTS = "parts";

    private final List<FormPart> parts;

    private FormData(List<FormPart> parts) {
        this.parts = parts;
    }

    /**
     * Returns a new {@link FormData} containing the provided parts in the given order.
     * At least one part is required.
     *
     * @param parts The form parts; must not be empty.
     * @return A new {@link FormData} instance.
     * @throws software.frisby.core.validation.NullValueException       if {@code parts} is null.
     * @throws software.frisby.core.validation.MissingElementsException if {@code parts} is empty.
     * @throws software.frisby.core.validation.NullElementException     if any element of {@code parts} is null.
     */
    public static FormData of(FormPart... parts) {
        Sequences.notEmpty(PARTS, parts);

        return new FormData(List.of(parts));
    }

    /**
     * Returns a new {@link FormData} containing the provided parts in the given order.
     * At least one part is required.
     *
     * @param parts The form parts; must not be empty.
     * @return A new {@link FormData} instance.
     * @throws software.frisby.core.validation.NullValueException       if {@code parts} is null.
     * @throws software.frisby.core.validation.MissingElementsException if {@code parts} is empty.
     * @throws software.frisby.core.validation.NullElementException     if any element of {@code parts} is null.
     */
    public static FormData of(List<FormPart> parts) {
        Sequences.notEmpty(PARTS, parts);

        return new FormData(List.copyOf(parts));
    }

    /**
     * Returns the ordered list of parts that make up this multipart request body.
     *
     * @return An unmodifiable ordered list of {@link FormPart} instances.
     */
    public List<FormPart> parts() {
        return parts;
    }
}
