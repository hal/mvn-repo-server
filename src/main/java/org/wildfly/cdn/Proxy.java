package org.wildfly.cdn;

/**
 * @author Heiko Braun
 * @since 24/02/15
 */
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static spark.Spark.*;

public class Proxy {
    public static void main(String[] args) {

        setPort(9090);

        get("/", (request, response) -> {
            InputStream input = Proxy.class.getResourceAsStream("/index.html");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            pipe(input, out);
            return  new String(out.toByteArray());
        });

        get("/hello/:name", (request, response) -> {
            return "Hello: " + request.params(":name");
        });
    }

    private static void pipe(InputStream is, OutputStream os) throws IOException {
        int n;
        byte[] buffer = new byte[1024];
        while ((n = is.read(buffer)) > -1) {
            os.write(buffer, 0, n);
        }
        os.close();
    }
}
