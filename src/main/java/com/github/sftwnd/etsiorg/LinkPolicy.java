package com.github.sftwnd.etsiorg;

import edu.umd.cs.findbugs.annotations.NonNull;
import lombok.Getter;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Policy of the links followed while the start folder of the site is mirrored: a link has to have the scheme, host
 * and port of the start folder, a safe path inside it no deeper than {@link #MAX_DEPTH}, and every resource is visited once
 */
public class LinkPolicy {

    /**
     * Maximal number of path segments of a resource below the start folder
     */
    static final int MAX_DEPTH = 32;

    private static final String HTTP = "http";
    private static final String HTTPS = "https";

    /**
     * Normalized uri of the start folder: lower case scheme and host, the path ends with '/'
     */
    @Getter
    private final URI root;
    private final List<String> rootSegments;
    private final Set<List<String>> visited = ConcurrentHashMap.newKeySet();

    /**
     * Constructor of the link policy
     * @param root uri of the start folder
     * @throws IllegalArgumentException if the uri is not an http or https uri of a folder with a safe path,
     *                                  or has user info, query or fragment
     */
    public LinkPolicy(@NonNull URI root) {
        Objects.requireNonNull(root, "LinkPolicy::new - root is null");
        if (root.isOpaque() || !(HTTP.equalsIgnoreCase(root.getScheme()) || HTTPS.equalsIgnoreCase(root.getScheme()))) {
            throw new IllegalArgumentException("Only http and https are supported: " + Printable.of(root));
        }
        if (root.getHost() == null || root.getRawUserInfo() != null || root.getRawQuery() != null || root.getRawFragment() != null) {
            throw new IllegalArgumentException("Start uri has to consist of a scheme, host, port and path: " + Printable.of(root));
        }
        this.rootSegments = ResourcePath.segments(root.getRawPath().isEmpty() ? root.resolve("/") : root);
        StringBuilder path = new StringBuilder("/");
        this.rootSegments.forEach(segment -> path.append(segment).append('/'));
        try {
            this.root = new URI(root.getScheme().toLowerCase(Locale.ROOT), null, root.getHost().toLowerCase(Locale.ROOT),
                    root.getPort(), path.toString(), null, null);
        } catch (URISyntaxException usex) {
            throw new IllegalArgumentException("Unable to normalize the start uri: " + Printable.of(root), usex);
        }
    }

    /**
     * Check that the link may be followed
     * @param uri absolute uri of the link
     * @return true if the link has the scheme, host and port of the start folder and a safe path inside it
     */
    public boolean accepts(@NonNull URI uri) {
        Objects.requireNonNull(uri, "LinkPolicy::accepts - uri is null");
        if (uri.isOpaque() || !this.root.getScheme().equalsIgnoreCase(uri.getScheme())
                || !this.root.getHost().equalsIgnoreCase(uri.getHost()) || port(this.root) != port(uri)
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            return false;
        }
        try {
            List<String> segments = ResourcePath.segments(uri);
            return segments.size() >= this.rootSegments.size()
                    && segments.size() - this.rootSegments.size() <= MAX_DEPTH
                    && segments.subList(0, this.rootSegments.size()).equals(this.rootSegments);
        } catch (IllegalArgumentException iaex) {
            return false;
        }
    }

    /**
     * Register the visit of the accepted link
     * @param uri accepted uri of the link
     * @return true for the first visit of the resource, false if it has been visited already
     */
    public boolean visit(@NonNull URI uri) {
        return this.visited.add(ResourcePath.segments(Objects.requireNonNull(uri, "LinkPolicy::visit - uri is null")));
    }

    /**
     * Port of the uri with the default port of the scheme
     * @param uri http or https uri
     * @return port of the uri
     */
    private static int port(@NonNull URI uri) {
        return uri.getPort() >= 0 ? uri.getPort() : HTTPS.equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

}
