package org.wildfly.cdn;

import com.github.zafarkhaja.semver.Version;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.List;

/**
 * @author Heiko Braun
 * @since 25/02/15
 */
public class Xml {

    private static Document parseXML(InputStream stream)
            throws Exception
    {
        DocumentBuilderFactory objDocumentBuilderFactory = null;
        DocumentBuilder objDocumentBuilder = null;
        Document doc = null;
        try
        {
            objDocumentBuilderFactory = DocumentBuilderFactory.newInstance();
            objDocumentBuilder = objDocumentBuilderFactory.newDocumentBuilder();

            doc = objDocumentBuilder.parse(stream);
        }
        catch(Exception ex)
        {
            throw ex;
        }

        return doc;
    }

    public interface HandleVersion {
        void handle(VersionedResource resource);
    }

    public static void parseMetadata(InputStream in, HandleVersion handler) {

        try {
            Document doc = parseXML(in);
            NodeList data = doc.getElementsByTagName("data");

            for (int i = 0; i < data.getLength(); i++) {
                Element dataEl = (Element) data.item(i);
                List<Element> contentItems = DomUtils.getChildElementsByTagName(dataEl, "content-item");
                for (Element contentItem : contentItems) {

                    Element leaf = DomUtils.getChildElementByTagName(contentItem, "leaf");
                    if (Boolean.valueOf(DomUtils.getTextValue(leaf)) == false) {
                        Element text = DomUtils.getChildElementByTagName(contentItem, "text");
                        String resourceName = DomUtils.getTextValue(text);
                        Version version = Versions.parseVersion(resourceName);
                        handler.handle(new VersionedResource(version, resourceName));
                    }
                }

            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public static VersionedResource parseSnapshotMetadata(InputStream in, final String versionString) {

        try {
            Document doc = parseXML(in);
            NodeList data = doc.getElementsByTagName("data");
            VersionedResource snapshotResource = null;
            for (int i = 0; i < data.getLength(); i++) {
                Element dataEl = (Element) data.item(i);
                List<Element> contentItems = DomUtils.getChildElementsByTagName(dataEl, "content-item");
                for (Element contentItem : contentItems) {

                    Element leaf = DomUtils.getChildElementByTagName(contentItem, "leaf");
                    if (Boolean.valueOf(DomUtils.getTextValue(leaf)) == true) {
                        Element text = DomUtils.getChildElementByTagName(contentItem, "text");
                        String resourceName = DomUtils.getTextValue(text);

                        if(resourceName.endsWith("-resources.jar"))
                        {
                            Element resourceURI = DomUtils.getChildElementByTagName(contentItem, "resourceURI");
                            String artefactUrl = DomUtils.getTextValue(resourceURI);

                            Version version = Versions.parseVersion(versionString);
                            snapshotResource = new VersionedResource(version, resourceName, artefactUrl);
                            break;
                        }

                    }
                }
            }

            return snapshotResource;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }
}
