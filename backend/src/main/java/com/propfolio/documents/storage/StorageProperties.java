package com.propfolio.documents.storage;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from {@code app.storage.*}.
 *
 * @param localRoot folder for uploaded bills; relative paths resolve from the working directory
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(Path localRoot) {
}
