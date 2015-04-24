package org.wildfly.cdn;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static java.util.stream.Collectors.joining;
import static spark.Spark.get;
import static spark.SparkBase.*;

/**
 * @author Heiko Braun
 * @since 24/02/15
 */
public class Proxy {

    private final static String DEFAULT_REPO =
            "https://repository.jboss.org/nexus/service/local/repositories/releases/content/org/jboss/hal/release-stream/";

    private final static String SNAPSHOT_REPO =
            "https://repository.jboss.org/nexus/service/local/repositories/snapshots/content/org/jboss/as/jboss-as-console/";

    private final static com.github.zafarkhaja.semver.Version CORS_SUPPORT_START = com.github.zafarkhaja.semver.Version.valueOf("2.6.5");

    private final static String WORK_DIR = System.getProperty("java.io.tmpdir");

    private static Long lastMetadataUpdate = null;
    private static long EXPIRY_MS = 3600000; // one our
    private static String latestVersion;

    public static void main(String[] args) throws Exception {

        /**
         * Openshift settings
         */
        String ip = System.getenv("OPENSHIFT_INTERNAL_IP");
        if (ip == null) {
            ip = "localhost";
        }
        String ports = System.getenv("OPENSHIFT_INTERNAL_PORT");
        if (ports == null) {
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
        final ReentrantLock snapshotLock = new ReentrantLock();

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
                        URL url = new URL(DEFAULT_REPO);
                        URLConnection connection = url.openConnection();

                        List<VersionedResource> versions = new LinkedList<>();
                        Xml.parseMetadata(connection.getInputStream(), versions::add);

                        Collections.sort(versions);

                        lastMetadataUpdate = System.currentTimeMillis();
                        latestVersion = versions.get(0).getResourceName();
                    }
                } finally {
                    metaDataLock.unlock();
                }

                response.type("text/plain");
                response.header("Access-Control-Allow-Origin", "*");
                response.header("Access-Control-Request-Method", "GET");
                return latestVersion;

            }
            else
            {
                response.status(408);
                return "Request timeout. Unable to retrieve latest version";
            }
        });

        get("/releases", (request, response) -> {
            boolean acquired = metaDataLock.tryLock(5, TimeUnit.SECONDS);
            if (acquired) {
                try {
                    URL url = new URL(DEFAULT_REPO);
                    URLConnection connection = url.openConnection();

                    List<VersionedResource> versions = new LinkedList<>();
                    Xml.parseMetadata(connection.getInputStream(), versions::add);

                    String currentUrl = request.url().substring(0, request.url().indexOf("/releases"));
                    String releases = versions.stream()
                            .filter(version -> version.getVersion().greaterThanOrEqualTo(CORS_SUPPORT_START))
                            .sorted(Comparator.<VersionedResource>reverseOrder())
                            .map(version -> currentUrl + "/release/" + version.getResourceName())
                            .collect(joining("\n"));
                    response.type("text/plain");
                    response.status(200);
                    return releases;
                } finally {
                    metaDataLock.unlock();
                }
            } else {
                response.status(408);
                return "Request timeout. Unable to retrieve releases";
            }
        });

        /**
         * retrieve a particular version and serve it
         */
        get("/release/:version", (request, response) -> {

            String version = request.params(":version");
            String fileURL = DEFAULT_REPO + version + "/release-stream-" + version + "-resources.jar";
            String destinationDir = wwwDir.getAbsolutePath() + File.separator + version;

            boolean success = false;

            if(!new File(destinationDir).exists()) {

                boolean aquired = artefactLock.tryLock(5, TimeUnit.SECONDS);
                if(aquired)
                {
                    try {
                        System.out.println("Download artefacts for "+ version);
                        Optional<String> fileLocation = Files.downloadFile(fileURL, WORK_DIR);
                        if (fileLocation.isPresent()) {
                            success = Files.unzipJar(destinationDir, fileLocation.get());
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


        /**
         * retrieve a particular snapshot and serve it
         */
        get("/snapshot/:version", (request, response) -> {

            String version = request.params(":version");
            String xmlURL = SNAPSHOT_REPO + version;

            String destinationDir = wwwDir.getAbsolutePath() + File.separator + version;

            // retrieve snapshot meta data and identify latest binary
            URL url = new URL(xmlURL);
            URLConnection connection = url.openConnection();

            VersionedResource snapshotResource;
            try {
                snapshotResource = Xml.parseSnapshotMetadata(connection.getInputStream(), version);
            } catch(Exception e) {
                response.status(404);
                return version + " can not be found";
            }

            File snapshotDir = new File(destinationDir);
            File marker = new File(snapshotDir, snapshotResource.getResourceName());
            boolean success = false;

            if(!marker.exists()) // new versions get their own marker based on the specific snapshot name
            {

                boolean aquired = snapshotLock.tryLock(5, TimeUnit.SECONDS);
                if(aquired)
                {
                    try {
                        // a newer snapshot supersedes the current one
                        if(snapshotDir.exists()) {
                            Files.deleteRecursive(snapshotDir);
                        }

                        // download and unpack new version
                        Optional<String> fileLocation = Files.downloadFile(snapshotResource.getArtefactUrl(), WORK_DIR);

                        if (fileLocation.isPresent()) {

                            success = Files.unzipJar(destinationDir, fileLocation.get());

                            if(success) {
                                response.status(200);
                                // create marker
                                marker.createNewFile();

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
                        snapshotLock.unlock();
                    }
                }
            }
            else
            {
                System.out.println("Serving cached version : "+version);
                success = true;
            }

            if(success)
                response.redirect("/"+version);

            return success ? snapshotResource.getArtefactUrl() : version + " can not be found";

        });
    }

    private static boolean metaDataExpired() {
        return (null == lastMetadataUpdate)
                || (System.currentTimeMillis()-lastMetadataUpdate > EXPIRY_MS);
    }
}
