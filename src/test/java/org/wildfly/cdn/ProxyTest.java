package org.wildfly.cdn;

import com.github.zafarkhaja.semver.Version;
import org.junit.Test;

import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

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
        assertNotNull("Failed to read maven-metadata.xml", in);

        Xml.parseMetadata(
                in,
                (VersionedResource resource) -> {
                    assertNotNull(resource);
                });
    }

    @Test
    public void testSnapshotXmlParsing() {
        InputStream in = ProxyTest.class.getResourceAsStream("/snapshot-metadata.xml");
        assertNotNull("Failed to read snapshot-metadata.xml", in);

        VersionedResource snapshotResource = Xml.parseSnapshotMetadata(in, "2.4.0-SNAPSHOT");
        assertEquals(
                "https://repository.jboss.org/nexus/service/local/repositories/snapshots/content/org/jboss/as/jboss-as-console/2.4.0-SNAPSHOT/jboss-as-console-2.4.0-20140820.084413-6-resources.jar",
                snapshotResource.getArtefactUrl()
        );
    }

    @Test
    public void testDateParser() throws Exception {
        String dateString = "2014-08-20 08:44:15.0 UTC";
        SimpleDateFormat parser =new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date date = parser.parse(dateString);
        assertNotNull(date);

        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        int year = cal.get(Calendar.YEAR);
        assertEquals(2014, year);
    }
}
