package org.wildfly.cdn;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static spark.Spark.get;
import static spark.SparkBase.*;

/**
 * @author Heiko Braun
 * @since 24/02/15
 */
public class Proxy {


    private final static String DEFAULT_REPO =
            "https://repository.jboss.org/nexus/service/local/repositories/releases/content/org/jboss/hal/release-stream/";

    private final static String WORK_DIR = System.getProperty("java.io.tmpdir");

    private static Long lastMetadataUpdate = null;
    private static long EXPIRY_MS = 3600000; // one our
    private static String latestVersion;

    public static void main(String[] args) throws Exception {


        final String repo = System.getenv("M2_REPO") !=null ?
                System.getenv("M2_REPO") : DEFAULT_REPO;

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
            Files.pipe(input, out);

            response.header("x-server-version", Version.VERSION);

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
                        URL url = new URL(repo);
                        URLConnection connection = url.openConnection();

                        List<VersionedResource> versions = new LinkedList<>();
                        Xml.parseMetadata(
                                connection.getInputStream(),
                                (VersionedResource resource) -> {
                                    versions.add(resource);
                                });

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
            String fileURL = repo + version + "/release-stream-" + version + "-resources.jar";
            String destinationDir = wwwDir.getAbsolutePath() + File.separator + version;

            boolean success = false;

            if(!new File(destinationDir).exists()) {

                boolean aquired = artefactLock.tryLock(5, TimeUnit.SECONDS);
                if(aquired)
                {
                    try {
                        System.out.println("Download artefacts for "+ version);
                        String fileLocation = Files.downloadFile(fileURL, WORK_DIR);
                        if (fileLocation != null) {
                            success = Files.unzipJar(destinationDir, fileLocation);
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

}
