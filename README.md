
# Maven Repository Proxy

A simple proxy for HAL artefacts that reside with the JBoss maven repository.

## Run the server locally

`mvn exec:java -Dexec.mainClass="org.wildfly.cdn.Proxy"`

## Run it on openshift

`java -jar target/mvn-cdn-jar-with-dependencies.jar -Djava.io.tmpdir=<tmp.dir>`

## Supported Parameters (`-Dkey=value`)

- java.io.tmpdir
- OPENSHIFT\_INTERNAL\_IP
- OPENSHIFT\_INTERNAL'_PORT



