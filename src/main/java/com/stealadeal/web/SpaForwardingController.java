package com.stealadeal.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the single-page app shell for client-side routes. Any GET that
 * is not an API call, an infrastructure path, or a static file (no dot
 * in the final segment) is forwarded to the bundled index.html so deep
 * links and refreshes resolve to the SPA instead of 404ing.
 *
 * REST controllers under /api are more specific and still match first;
 * static assets are served by the resource handler because they contain
 * a file extension and are excluded by the [^.]* path pattern.
 */
@Controller
public class SpaForwardingController {

    private static final String SHELL = "forward:/index.html";

    @GetMapping(value = "/{path:^(?!api$|actuator$|h2-console$|assets$)[^.]*}")
    public String forwardRootRoute() {
        return SHELL;
    }

    @GetMapping(value = "/{path:^(?!api$|actuator$|h2-console$|assets$)[^.]*}/**")
    public String forwardNestedRoute() {
        return SHELL;
    }
}
