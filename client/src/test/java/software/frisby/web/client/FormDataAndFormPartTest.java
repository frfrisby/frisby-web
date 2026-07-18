package software.frisby.web.client;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.core.validation.MissingElementsException;
import software.frisby.core.validation.NullValueException;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FormDataAndFormPartTest {
    private static final String NULL_PARTS_MSG = "The 'parts' value is invalid. The value must not be null.";
    private static final String EMPTY_PARTS_MSG = "The 'parts' value is invalid. The value must not be empty.";
    private static final String NULL_NAME_MSG = "The 'name' value is invalid. The value must not be null.";
    private static final String NULL_STREAM_MSG = "The 'stream' value is invalid. The value must not be null.";
    private static final String NULL_CONTENT_TYPE_MSG = "The 'contentType' value is invalid. The value must not be null.";
    private static final String NULL_BODY_MSG = "The 'body' value is invalid. The value must not be null.";
    private static final String NULL_MEDIA_TYPE_MSG = "The 'mediaType' value is invalid. The value must not be null.";

    // -------------------------------------------------------------------------
    // FormData.of(FormPart...)
    // -------------------------------------------------------------------------

    @Nested
    class FormDataVarargs {
        @Test
        void nullArray_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> FormData.of((FormPart[]) null)
            );

            assertEquals(NULL_PARTS_MSG, ex.getMessage());
        }

        @Test
        void emptyArray_throwsMissingElementsException() {
            MissingElementsException ex = assertThrows(
                    MissingElementsException.class,
                    () -> FormData.of(new FormPart[]{})
            );

            assertEquals(EMPTY_PARTS_MSG, ex.getMessage());
        }

        @Test
        void singlePart_isStored() {
            FormPart part = FormPart.text("field", "value");
            FormData data = FormData.of(part);

            assertEquals(1, data.parts().size());
            assertEquals(part, data.parts().get(0));
        }

        @Test
        void multipleParts_orderIsPreserved() {
            FormPart first = FormPart.text("first", "a");
            FormPart second = FormPart.text("second", "b");
            FormPart third = FormPart.text("third", "c");

            FormData data = FormData.of(first, second, third);

            assertEquals(List.of(first, second, third), data.parts());
        }
    }

    // -------------------------------------------------------------------------
    // FormData.of(List<FormPart>)
    // -------------------------------------------------------------------------

    @Nested
    class FormDataList {
        @Test
        void nullList_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> FormData.of((List<FormPart>) null)
            );

            assertEquals(NULL_PARTS_MSG, ex.getMessage());
        }

        @Test
        void emptyList_throwsMissingElementsException() {
            MissingElementsException ex = assertThrows(
                    MissingElementsException.class,
                    () -> FormData.of(List.of())
            );

            assertEquals(EMPTY_PARTS_MSG, ex.getMessage());
        }

        @Test
        void list_preservesOrder() {
            FormPart a = FormPart.text("a", "1");
            FormPart b = FormPart.text("b", "2");

            FormData data = FormData.of(List.of(a, b));

            assertEquals(List.of(a, b), data.parts());
        }
    }

    // -------------------------------------------------------------------------
    // FormPart.file
    // -------------------------------------------------------------------------

    @Nested
    class FilePartFactory {
        @Test
        void file_noContentType_createsFilePart() {
            var stream = new ByteArrayInputStream(new byte[]{1, 2, 3});
            FormPart part = FormPart.file("upload", stream, "report.pdf");

            assertInstanceOf(FormPart.FilePart.class, part);
            assertEquals("upload", part.name());
        }

        @Test
        void file_withContentType_contentTypeIsPresent() {
            var stream = new ByteArrayInputStream(new byte[]{1});
            FormPart part = FormPart.file("upload", stream, "data.json", MediaType.APPLICATION_JSON);

            assertInstanceOf(FormPart.FilePart.class, part);
            assertTrue(((FormPart.FilePart) part).contentType().isPresent());
            assertEquals(MediaType.APPLICATION_JSON, ((FormPart.FilePart) part).contentType().get());
        }

        @Test
        void file_withoutContentType_contentTypeIsEmpty() {
            var stream = new ByteArrayInputStream(new byte[]{1});
            FormPart part = FormPart.file("upload", stream, "file.bin");

            assertFalse(((FormPart.FilePart) part).contentType().isPresent());
        }

        @Test
        void file_nullName_throwsException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> FormPart.file(null, new ByteArrayInputStream(new byte[]{}), "file.bin")
            );

            assertEquals(NULL_NAME_MSG, ex.getMessage());
        }

        @Test
        void file_nullStream_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> FormPart.file("upload", null, "file.bin")
            );

            assertEquals(NULL_STREAM_MSG, ex.getMessage());
        }

        @Test
        void file_nullContentType_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> FormPart.file("upload", new ByteArrayInputStream(new byte[]{}), "file.bin", null)
            );

            assertEquals(NULL_CONTENT_TYPE_MSG, ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // FormPart.json
    // -------------------------------------------------------------------------

    @Nested
    class JsonPartFactory {
        @Test
        void json_createsJsonPart() {
            Object body = new Object();
            FormPart part = FormPart.json("metadata", body);

            assertInstanceOf(FormPart.JsonPart.class, part);
            assertEquals("metadata", part.name());
            assertEquals(body, ((FormPart.JsonPart) part).body());
        }

        @Test
        void json_nullBody_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> FormPart.json("metadata", null)
            );

            assertEquals(NULL_BODY_MSG, ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // FormPart.text
    // -------------------------------------------------------------------------

    @Nested
    class TextPartFactory {
        @Test
        void text_createsContentPartWithTextPlainMediaType() {
            FormPart part = FormPart.text("category", "invoices");

            assertInstanceOf(FormPart.ContentPart.class, part);
            assertEquals("category", part.name());
            assertEquals("invoices", ((FormPart.ContentPart) part).content());
            assertEquals(MediaType.TEXT_PLAIN, ((FormPart.ContentPart) part).mediaType());
        }

        @Test
        void text_nullName_throwsException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> FormPart.text(null, "value")
            );

            assertEquals(NULL_NAME_MSG, ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // FormPart.entity
    // -------------------------------------------------------------------------

    @Nested
    class EntityPartFactory {
        @Test
        void entity_createsContentPartWithGivenMediaType() {
            FormPart part = FormPart.entity("data", "<root/>", MediaType.of("application/xml"));

            assertInstanceOf(FormPart.ContentPart.class, part);
            assertEquals("data", part.name());
            assertEquals("<root/>", ((FormPart.ContentPart) part).content());
            assertEquals(MediaType.of("application/xml"), ((FormPart.ContentPart) part).mediaType());
        }

        @Test
        void entity_nullMediaType_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> FormPart.entity("data", "content", null)
            );

            assertEquals(NULL_MEDIA_TYPE_MSG, ex.getMessage());
        }
    }
}
