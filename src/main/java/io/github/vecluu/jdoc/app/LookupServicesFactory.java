package io.github.vecluu.jdoc.app;

import io.github.vecluu.jdoc.lookup.LookupServices;

/** Creates a fresh set of lookup services for one call (their caches are per call). */
@FunctionalInterface
public interface LookupServicesFactory {

    /** New lookup services; the caller closes them. */
    LookupServices create();
}
