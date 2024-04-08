package no.auke.m2.proxy;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller("/")
public class MainController {
    private static final Logger log = LoggerFactory.getLogger(MainController.class);
    @Get(uri = "", produces = MediaType.APPLICATION_JSON)
    public HttpResponse<?> mainPage() {
        return HttpResponse.status(HttpStatus.OK)
                .header("x-fms-after-timestamp", String.valueOf(10))
                .body("{hello: hello}");
    }
}
