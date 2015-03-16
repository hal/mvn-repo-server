# Maven Repository Proxy

A simple proxy for HAL artifacts that reside with the JBoss maven repository.

## Run the server locally

        mvn clean package
        java -jar target/server-jar-with-dependencies.jar

## Run it on OpenShift

        java -jar target/server-jar-with-dependencies.jar -Djava.io.tmpdir=<tmp.dir>

## Supported Parameters

Set as system properties before starting the server

* java.io.tmpdir
* OPENSHIFT_INTERNAL_IP
* OPENSHIFT_INTERNAL_PORT
