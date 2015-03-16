# Maven Repository Proxy

A simple proxy for HAL artifacts that reside with the JBoss maven repository.

## Run the server locally

        mvn clean package
        java -jar target/server-jar-with-dependencies.jar
        
By default the server runs on port 8080. If you want to use another port set `OPENSHIFT_INTERNAL_PORT` to another value:

        export OPENSHIFT_INTERNAL_PORT=8787
        java -jar target/server-jar-with-dependencies.jar

## Run it on OpenShift

        java -jar target/server-jar-with-dependencies.jar -Djava.io.tmpdir=<tmp.dir>

## Supported Parameters

Set as system properties before starting the server

* java.io.tmpdir
* OPENSHIFT_INTERNAL_IP
* OPENSHIFT_INTERNAL_PORT
