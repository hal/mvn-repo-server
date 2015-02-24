package org.wildfly.cdn;

/**
 * @author Heiko Braun
 * @since 24/02/15
 */


import com.github.zafarkhaja.semver.Version;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static spark.Spark.get;
import static spark.Spark.setPort;

public class Proxy {


    private final static String RELEASES_URL = "https://repository.jboss.org/nexus/service/local/repositories/releases/content/org/jboss/hal/release-stream/";
    private final static String WORK_DIR = System.getProperty("java.io.tmpdir");


    public static void main(String[] args) {

        setPort(9090);

        get("/", (request, response) -> {
            InputStream input = Proxy.class.getResourceAsStream("/index.html");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            pipe(input, out);
            return  new String(out.toByteArray());
        });

        get("/latest", (request, response) -> {

            URL url = new URL(RELEASES_URL);
            URLConnection connection = url.openConnection();

            Document doc = parseXML(connection.getInputStream());
            NodeList data = doc.getElementsByTagName("data");

            StringBuffer sb = new StringBuffer();
            List<VersionedResource> versions = new LinkedList<VersionedResource>();
            for(int i=0; i<data.getLength();i++)
            {
                Element dataEl = (Element) data.item(i);
                List<Element> contentItems = DomUtils.getChildElementsByTagName(dataEl, "content-item");
                for (Element contentItem : contentItems) {

                    Element leaf = DomUtils.getChildElementByTagName(contentItem, "leaf");
                    if(Boolean.valueOf(DomUtils.getTextValue(leaf)) == false)
                    {
                        Element text = DomUtils.getChildElementByTagName(contentItem, "text");
                        String resourceName = DomUtils.getTextValue(text);
                        Version version = Version.valueOf(resourceName.substring(0, 5));
                        versions.add(new VersionedResource(version, resourceName));
                    }
                }

            }

            Collections.sort(versions);

            return versions.get(0).getResourceName();
        });

        get("/builds/:version", (request, response) -> {

            // 1.5.4.Final/release-stream-1.5.4.Final-resources.jar
            String version = request.params(":version");
            String fileURL = RELEASES_URL + version + "/release-stream-" + version + "-resources.jar";
            //downloadFile(fileURL, WORK_DIR);
            return fileURL;
        });
    }

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

    private static void pipe(InputStream is, OutputStream os) throws IOException {
        int n;
        byte[] buffer = new byte[1024];
        while ((n = is.read(buffer)) > -1) {
            os.write(buffer, 0, n);
        }
        os.close();
    }

    private static final int BUFFER_SIZE = 4096;

    /**
     * Downloads a file from a URL
     * @param fileURL HTTP URL of the file to be downloaded
     * @param saveDir path of the directory to save the file
     * @throws IOException
     */
    public static void downloadFile(String fileURL, String saveDir)
            throws IOException {
        URL url = new URL(fileURL);
        HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
        int responseCode = httpConn.getResponseCode();

        // always check HTTP response code first
        if (responseCode == HttpURLConnection.HTTP_OK) {
            String fileName = "";
            String disposition = httpConn.getHeaderField("Content-Disposition");
            String contentType = httpConn.getContentType();
            int contentLength = httpConn.getContentLength();

            if (disposition != null) {
                // extracts file name from header field
                int index = disposition.indexOf("filename=");
                if (index > 0) {
                    fileName = disposition.substring(index + 10,
                            disposition.length() - 1);
                }
            } else {
                // extracts file name from URL
                fileName = fileURL.substring(fileURL.lastIndexOf("/") + 1,
                        fileURL.length());
            }

            System.out.println("Content-Type = " + contentType);
            System.out.println("Content-Disposition = " + disposition);
            System.out.println("Content-Length = " + contentLength);
            System.out.println("fileName = " + fileName);

            // opens input stream from the HTTP connection
            InputStream inputStream = httpConn.getInputStream();
            String saveFilePath = saveDir + File.separator + fileName;

            // opens an output stream to save into file
            FileOutputStream outputStream = new FileOutputStream(saveFilePath);

            int bytesRead = -1;
            byte[] buffer = new byte[BUFFER_SIZE];
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            outputStream.close();
            inputStream.close();

            System.out.println("File downloaded");
        } else {
            System.out.println("No file to download. Server replied HTTP code: " + responseCode);
        }
        httpConn.disconnect();
    }
}
