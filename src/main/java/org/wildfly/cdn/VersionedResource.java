package org.wildfly.cdn;

import com.github.zafarkhaja.semver.Version;

/**
 * @author Heiko Braun
 * @since 24/02/15
 */
class VersionedResource implements Comparable<VersionedResource> {
    private final Version version;
    private final String resourceName;

    public VersionedResource(Version version, String resourceName) {
        this.version = version;
        this.resourceName = resourceName;
    }

    public Version getVersion() {
        return version;
    }

    public String getResourceName() {
        return resourceName;
    }

    @Override
    public int compareTo(VersionedResource o) {
        return o.version.compareTo(version);
    }
}
