package com.ayssu.ciphergate.controller;

import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Public deployment verification intentionally exposes only the release version. */
@RestController
public class PublicBuildInfoController {
    private final BuildProperties build;

    public PublicBuildInfoController(BuildProperties build) {
        this.build = build;
    }

    @GetMapping("/api/config/public/build-info")
    public Map<String, String> buildInfo() {
        return Map.of("version", build.getVersion());
    }
}
