package org.wildfly.cdn;

import com.github.zafarkhaja.semver.Version;
import org.junit.Test;

import java.io.InputStream;

import static org.junit.Assert.*;
import static org.wildfly.cdn.Versions.parseVersion;

/**
 * @author Heiko Braun
 * @since 25/02/15
 */
public class ProxyTest {

    @Test
    public void testVersionParsing() {

        String[] versions = new String[] {
                "2.6.1.Final",
                "2.5.3-Beta1",
                "2.5.2",
                "2.5.20.Final",
                "2.5.10"
        };

        for (String version : versions) {
            Version v = parseVersion(version);
            assertTrue(version+ " major failed", v.getMajorVersion() >0);
            assertTrue(version+ " minor failed", v.getMinorVersion() >0);
            assertTrue(version+ " patch failed", v.getPatchVersion() >0);
        }

    }

    @Test
    public void testXmlParsing() {
        InputStream in = ProxyTest.class.getResourceAsStream("/maven-metadata.xml");
        assertNotNull("Failed to read maven-metadat.xml", in);

        Xml.parseMetadata(
                in,
                (VersionedResource resource) -> {
                    assertNotNull(resource);
                });
    }
}
