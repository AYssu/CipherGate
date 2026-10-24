package com.ayssu.ciphergate.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Properties;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PublicBuildInfoControllerTest {
    @Test void exposesOnlyVersionAndNeverOtherBuildProperties() throws Exception {
        var properties = new Properties();
        properties.setProperty("version", "1.2.3");
        properties.setProperty("group", "private-group");
        properties.setProperty("name", "private-project-name");
        properties.setProperty("time", "2026-10-24T00:00:00Z");
        var controller = new PublicBuildInfoController(new BuildProperties(properties));
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(get("/api/config/public/build-info"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"version\":\"1.2.3\"}"))
                .andExpect(jsonPath("$.length()").value(1));
    }
}
