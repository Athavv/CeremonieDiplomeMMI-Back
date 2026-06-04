package mmi.ceremonie.diplome.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lightweight keep-alive endpoint (no DB access) used by an external pinger
 * to prevent Render's free tier from spinning the service down.
 */
@RestController
@RequestMapping("/api/public")
public class PingController {

    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }
}
