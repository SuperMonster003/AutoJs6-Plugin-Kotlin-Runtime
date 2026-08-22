package org.autojs.plugin.jvmsource.kotlin.compat;

import java.io.File;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;

/** Resolves the archive or class-directory root for Kotlin compiler resources on JVM and ART. */
public final class KotlinCompilerResourceRoot {

    private KotlinCompilerResourceRoot() {
    }

    public static File resolve(Class<?> type) {
        String classResource = type.getName().replace('.', '/') + ".class";
        URL location = type.getResource('/' + classResource);
        String resourcePath = classResource;
        if (location == null) {
            ClassLoader loader = type.getClassLoader();
            location = loader == null ? null : loader.getResource("AndroidManifest.xml");
            resourcePath = "AndroidManifest.xml";
        }
        if (location == null) {
            throw new IllegalStateException("Kotlin compiler resource root is unavailable");
        }
        File root = archiveOrDirectoryRoot(location, resourcePath).getAbsoluteFile();
        if (!root.exists() || (!root.isFile() && !root.isDirectory())) {
            throw new IllegalStateException("Kotlin compiler resource root is invalid");
        }
        return root;
    }

    private static File archiveOrDirectoryRoot(URL location, String resourcePath) {
        try {
            if ("jar".equals(location.getProtocol())) {
                JarURLConnection connection = (JarURLConnection) location.openConnection();
                connection.setUseCaches(false);
                return new File(connection.getJarFileURL().toURI());
            }
            if ("file".equals(location.getProtocol())) {
                File root = new File(location.toURI());
                int segments = resourcePath.split("/").length;
                for (int index = 0; index < segments; index++) {
                    root = root.getParentFile();
                    if (root == null) {
                        throw new IllegalStateException("Kotlin compiler resource path is malformed");
                    }
                }
                return root;
            }
            String external = location.toExternalForm();
            int separator = external.indexOf("!/");
            if (separator > 0) {
                String archive = external.substring(0, separator);
                if (archive.startsWith("jar:")) archive = archive.substring(4);
                return new File(URI.create(archive));
            }
        } catch (java.net.URISyntaxException | java.io.IOException | IllegalArgumentException error) {
            throw new IllegalStateException("Unable to resolve the Kotlin compiler resource root", error);
        }
        throw new IllegalStateException("Unsupported Kotlin compiler resource protocol");
    }
}
