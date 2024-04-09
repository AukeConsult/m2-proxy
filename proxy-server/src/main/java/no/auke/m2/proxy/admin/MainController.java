package no.auke.m2.proxy.admin;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

@Controller()
public class MainController {
    @Get(uri = "", produces = MediaType.APPLICATION_JSON)
    public HttpResponse<?> mainPage() {
        return HttpResponse.status(HttpStatus.OK).body("{hello: hello}");
    }
}
