/**
 * Mirrors the document tree of ETSI, <a href="https://www.etsi.org/deliver/">www.etsi.org/deliver</a>, to a local folder.
 * The logging implementation is not required: logback is bound to SLF4J as its service provider
 */
// the spotbugs annotations jar is an automatic module: it has no module descriptor, only an Automatic-Module-Name
@SuppressWarnings("requires-automatic")
module com.github.sftwnd.etsiorg {
    requires org.slf4j;
    // compile-only: Lombok generates the code, the null-contract annotations have CLASS retention
    requires static lombok;
    requires static com.github.spotbugs.annotations;
}
