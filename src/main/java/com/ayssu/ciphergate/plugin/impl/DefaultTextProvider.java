package com.ayssu.ciphergate.plugin.impl;

import com.ayssu.ciphergate.plugin.TextProvider;
import org.springframework.stereotype.Component;

@Component
public class DefaultTextProvider implements TextProvider {

    @Override
    public String pluginId() {
        return "default-text";
    }

    @Override
    public String getText() {
        return "hello world";
    }
}
