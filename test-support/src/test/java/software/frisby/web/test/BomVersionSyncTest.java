package software.frisby.web.test;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that the hardcoded dependency versions in {@code bom/pom.xml} are in sync
 * with the corresponding version properties in the root {@code pom.xml}.
 *
 * <p>The {@code flatten-maven-plugin} strips the {@code <properties>} block from the
 * published BOM, leaving any {@code ${...}} expressions unresolvable for consumers.
 * The BOM therefore uses literal version strings for its three imported BOMs, which must
 * be updated whenever the matching root POM properties change.  This test enforces that
 * invariant so that a missed update is caught at build time rather than surfaced as a
 * version conflict in a downstream consumer.
 */
class BomVersionSyncTest {
    private static final String VERSION_MISMATCH =
            "bom/pom.xml version for %s:%s does not match root POM property '%s'";

    private static Document rootPom;
    private static Document bomPom;
    private static XPath xpath;

    @BeforeAll
    static void parsePoms() throws Exception {
        File baseDir = new File(System.getProperty("project.basedir"));

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();

        rootPom = builder.parse(new File(baseDir, "../pom.xml"));
        bomPom = builder.parse(new File(baseDir, "../bom/pom.xml"));

        xpath = XPathFactory.newInstance().newXPath();
    }

    @Nested
    class BomImports {
        @Test
        void frisbyCoreVersion_matchesRootPomProperty() throws Exception {
            assertVersionInSync(
                    "software.frisby.core",
                    "bom",
                    "frisby-core.version"
            );
        }

        @Test
        void jettyVersion_matchesRootPomProperty() throws Exception {
            assertVersionInSync(
                    "org.eclipse.jetty",
                    "jetty-bom",
                    "jetty.version"
            );
        }

        @Test
        void jerseyVersion_matchesRootPomProperty() throws Exception {
            assertVersionInSync(
                    "org.glassfish.jersey",
                    "jersey-bom",
                    "jersey.version"
            );
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Asserts that the version declared for {@code groupId:artifactId} in
     * {@code bom/pom.xml}'s {@code <dependencyManagement>} equals the value of
     * {@code rootProperty} in the root POM's {@code <properties>} block.
     */
    private static void assertVersionInSync(
            String groupId,
            String artifactId,
            String rootProperty
    ) throws Exception {
        // local-name() is used here because 'frisby-core.version' contains a hyphen,
        // which is not valid in an unquoted XPath name token.
        String expected = xpath.evaluate(
                "/project/properties/*[local-name()='" + rootProperty + "']",
                rootPom
        );

        String actual = xpath.evaluate(
                "/project/dependencyManagement/dependencies/dependency"
                        + "[groupId='" + groupId + "' and artifactId='" + artifactId + "']/version",
                bomPom
        );

        assertEquals(
                expected,
                actual,
                String.format(VERSION_MISMATCH, groupId, artifactId, rootProperty)
        );
    }
}

