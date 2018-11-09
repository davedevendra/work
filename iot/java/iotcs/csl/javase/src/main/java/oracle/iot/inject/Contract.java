package oracle.iot.inject;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declaration of an annotation found in the Oracle IoT Client Gateway
 * code. This annotation is not used in the client library, but is
 * declared here so that code may be shared between the client library
 * and the gateway.
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE})
public @interface Contract {
}
