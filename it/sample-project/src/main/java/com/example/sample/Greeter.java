package com.example.sample;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

/** Uses one type from each dependency so the module is a realistic jdoc target. */
public final class Greeter {

    private Greeter() {
    }

    /** Greets a name, or the world when the name is blank, and appends a short digest. */
    public static String greet(@Nullable String name) {
        String who = StringUtils.isBlank(name) ? "world" : name.strip();
        return "Hello, " + who + " (" + DigestUtils.sha256Hex(who).substring(0, 8) + ")";
    }
}
