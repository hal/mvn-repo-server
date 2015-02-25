package org.wildfly.cdn;

import com.github.zafarkhaja.semver.Version;

/**
 * @author Heiko Braun
 * @since 24/02/15
 */
class VersionedResource implements Comparable<VersionedResource> {
    private final Version version;
    private final String resourceName;
    private String artefactUrl = null;

    public VersionedResource(Version version, String resourceName) {
        this.version = version;
        this.resourceName = resourceName;
    }

    public VersionedResource(Version version, String resourceName, String artefactUrl) {
        this.version = version;
        this.resourceName = resourceName;
        this.artefactUrl = artefactUrl;
    }

    public Version getVersion() {
        return version;
    }

    public String getResourceName() {
        return resourceName;
    }

    public String getArtefactUrl() {
        return artefactUrl;
    }

    @Override
    public int compareTo(VersionedResource o) {
        return o.version.compareTo(version);
    }
}
