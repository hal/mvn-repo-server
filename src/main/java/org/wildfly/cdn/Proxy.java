package org.wildfly.cdn;

import com.github.zafarkhaja.semver.Version;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
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
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static spark.Spark.get;
import static spark.SparkBase.*;

/**
 * @author Heiko Braun
 * @since 24/02/15
 */
public class Proxy {


    private final static String RELEASES_URL = "https://repository.jboss.org/nexus/service/local/repositories/releases/content/org/jboss/hal/release-stream/";
    private final static String WORK_DIR = System.getProperty("java.io.tmpdir");

    private static Long lastMetadataUpdate = null;
    private static long EXPIRY_MS = 3600000; // one our
    private static String latestVersion;

    public static void main(String[] args) throws Exception {

        /**
         * Openshift settings
         */
        String ip = System.getenv("OPENSHIFT_INTERNAL_IP");
        if(ip == null) {
            ip = "localhost";
        }
        String ports = System.getenv("OPENSHIFT_INTERNAL_PORT");
        if(ports == null) {
            ports = "8080";
        }

        /**
         * configuration
         */
        ipAddress(ip);
        port(Integer.valueOf(ports));


        /**
         * filesystem setup
         */

        final File wwwDir = new File(WORK_DIR, "public_html");
        if(!wwwDir.exists())
            wwwDir.mkdir();

        System.out.println("public_html: "+ wwwDir.getAbsolutePath());
        externalStaticFileLocation(wwwDir.getAbsolutePath());

        /**
         * Locks (some operations require it)
         */
        final ReentrantLock artefactLock = new ReentrantLock();
        final ReentrantLock metaDataLock = new ReentrantLock();

        /**
         * retrieve the homepage (/index.html)
         */
        get("/", (request, response) -> {
            InputStream input = Proxy.class.getResourceAsStream("/index.html");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            pipe(input, out);
            return new String(out.toByteArray());
        });

        /**
         * retrieve the name of the latest version
         */
        get("/latest", (request, response) -> {

            boolean aquired = metaDataLock.tryLock(5, TimeUnit.SECONDS);
            if(aquired) {

                try {
                    if(metaDataExpired()) {
                        URL url = new URL(RELEASES_URL);
                        URLConnection connection = url.openConnection();

                        Document doc = parseXML(connection.getInputStream());
                        NodeList data = doc.getElementsByTagName("data");

                        List<VersionedResource> versions = new LinkedList<>();
                        for (int i = 0; i < data.getLength(); i++) {
                            Element dataEl = (Element) data.item(i);
                            List<Element> contentItems = DomUtils.getChildElementsByTagName(dataEl, "content-item");
                            for (Element contentItem : contentItems) {

                                Element leaf = DomUtils.getChildElementByTagName(contentItem, "leaf");
                                if (Boolean.valueOf(DomUtils.getTextValue(leaf)) == false) {
                                    Element text = DomUtils.getChildElementByTagName(contentItem, "text");
                                    String resourceName = DomUtils.getTextValue(text);
                                    Version version = null;
                                    try {
                                        int defaultIndex = ordinalIndexOf(resourceName, ".", 3);
                                        if (INDEX_NOT_FOUND == defaultIndex)
                                            defaultIndex = resourceName.length();

                                        version = Version.valueOf(resourceName.substring(0, defaultIndex));
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        System.out.println(resourceName);
                                    }
                                    versions.add(new VersionedResource(version, resourceName));
                                }
                            }

                        }

                        Collections.sort(versions);

                        lastMetadataUpdate = System.currentTimeMillis();
                        latestVersion = versions.get(0).getResourceName();
                    }
                } finally {
                    metaDataLock.unlock();
                }

                return latestVersion;

            }
            else
            {
                response.status(408);
                return "Request timeout. Unable to retrieve latest version";
            }
        });

        /**
         * retrieve a particular version and serve it
         */
        get("/builds/:version", (request, response) -> {

            String version = request.params(":version");
            String fileURL = RELEASES_URL + version + "/release-stream-" + version + "-resources.jar";
            String destinationDir = wwwDir.getAbsolutePath() + File.separator + version;

            boolean success = false;

            if(!new File(destinationDir).exists()) {

                boolean aquired = artefactLock.tryLock(5, TimeUnit.SECONDS);
                if(aquired)
                {
                    try {
                        System.out.println("Download artefacts for "+ version);
                        String fileLocation = downloadFile(fileURL, WORK_DIR);
                        if (fileLocation != null) {
                            success = unzipJar(destinationDir, fileLocation);
                            if(success) {
                                response.status(200);
                            }
                            else {
                                response.status(500);
                            }

                        }
                        else
                        {
                            response.status(404);
                        }
                    } finally {
                        artefactLock.unlock();
                    }
                }
                else
                {
                    response.status(408);
                    System.out.println("Request timeout. Failed to quire lock on version "+version);
                }
            }
            else
            {
                System.out.println("Serving cached version : "+version);
                success = true;
            }

            if(success)
                response.redirect("/"+version);

            return success ? fileURL : version + " can not be found";
        });
    }

    private static boolean metaDataExpired() {

        return (null == lastMetadataUpdate)
                || (System.currentTimeMillis()-lastMetadataUpdate > EXPIRY_MS);
    }


    // ------------
    // Helper

    private static final int INDEX_NOT_FOUND = -1;

    private static int ordinalIndexOf(final CharSequence str, final CharSequence searchStr, final int ordinal) {
        return ordinalIndexOf(str, searchStr, ordinal, false);
    }

    private static int ordinalIndexOf(final CharSequence str, final CharSequence searchStr, final int ordinal, final boolean lastIndex) {
        if (str == null || searchStr == null || ordinal <= 0) {
            return INDEX_NOT_FOUND;
        }
        if (searchStr.length() == 0) {
            return lastIndex ? str.length() : 0;
        }
        int found = 0;
        int index = lastIndex ? str.length() : INDEX_NOT_FOUND;
        do {
            if (lastIndex) {
                index = lastIndexOf(str, searchStr, index - 1);
            } else {
                index = indexOf(str, searchStr, index + 1);
            }
            if (index < 0) {
                return index;
            }
            found++;
        } while (found < ordinal);
        return index;
    }

    private static int lastIndexOf(final CharSequence cs, final CharSequence searchChar, final int start) {
        return cs.toString().lastIndexOf(searchChar.toString(), start);
        //        if (cs instanceof String && searchChar instanceof String) {
        //            // TODO: Do we assume searchChar is usually relatively small;
        //            //       If so then calling toString() on it is better than reverting to
        //            //       the green implementation in the else block
        //            return ((String) cs).lastIndexOf((String) searchChar, start);
        //        } else {
        //            // TODO: Implement rather than convert to String
        //            return cs.toString().lastIndexOf(searchChar.toString(), start);
        //        }
    }

    private static int indexOf(final CharSequence cs, final CharSequence searchChar, final int start) {
        return cs.toString().indexOf(searchChar.toString(), start);
        //        if (cs instanceof String && searchChar instanceof String) {
        //            // TODO: Do we assume searchChar is usually relatively small;
        //            //       If so then calling toString() on it is better than reverting to
        //            //       the green implementation in the else block
        //            return ((String) cs).indexOf((String) searchChar, start);
        //        } else {
        //            // TODO: Implement rather than convert to String
        //            return cs.toString().indexOf(searchChar.toString(), start);
        //        }
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

    private static boolean unzipJar(String destPath, String jarPath) {
        try {

            JarFile jarFile = new JarFile(new File(jarPath));
            Enumeration<JarEntry> enums = jarFile.entries();
            while (enums.hasMoreElements()) {
                JarEntry entry = enums.nextElement();

                File toWrite = new File(destPath + File.separator + entry.getName());
                if (entry.isDirectory()) {
                    toWrite.mkdirs();
                    continue;
                }
                InputStream in = new BufferedInputStream(jarFile.getInputStream(entry));
                OutputStream out = new BufferedOutputStream(new FileOutputStream(toWrite));
                byte[] buffer = new byte[2048];
                for (; ; ) {
                    int nBytes = in.read(buffer);
                    if (nBytes <= 0) {
                        break;
                    }
                    out.write(buffer, 0, nBytes);
                }
                out.flush();
                out.close();
                in.close();


            }
        } catch (IOException ex) {
            ex.printStackTrace();
            return false;
        }
        return true;
    }

    private static final int BUFFER_SIZE = 4096;

    /**
     * Downloads a file from a URL
     * @param fileURL HTTP URL of the file to be downloaded
     * @param saveDir path of the directory to save the file
     * @throws IOException
     */
    public static String downloadFile(String fileURL, String saveDir)
            throws IOException {
        URL url = new URL(fileURL);
        HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
        int responseCode = httpConn.getResponseCode();

        String saveFilePath = null;

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
            saveFilePath = saveDir + File.separator + fileName;

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
        return saveFilePath;
    }
}
