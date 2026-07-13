package org.twins.mcp.secrets;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Layout;
import ch.qos.logback.core.LayoutBase;

/**
 * Logback {@link Layout} that delegates rendering to a wrapped layout and then passes the
 * rendered string through {@link SecretsSanitiser}.
 *
 * <p>This is the chokepoint ARCH-8 calls for — every log line, regardless of the underlying
 * encoder, gets sanitised. Patterns are alphanumeric so JSON-escaping ({@code \"} for quotes,
 * etc.) does not interfere with redaction; only string content is matched.
 *
 * <p>Wired in {@code logback-spring.xml}:
 * <pre>{@code
 * <encoder class="ch.qos.logback.core.encoder.LayoutWrappingEncoder">
 *   <layout class="org.twins.mcp.secrets.SanitisingLayout">
 *     <delegate class="net.logstash.logback.layout.LogstashLayout"/>
 *   </layout>
 * </encoder>
 * }</pre>
 *
 * <p>If anything throws, the original rendered output is returned (sanitiser never blocks
 * logging — AC-8).
 */
public class SanitisingLayout extends LayoutBase<ILoggingEvent> {

    private Layout<ILoggingEvent> delegate;

    public void setDelegate(Layout<ILoggingEvent> delegate) {
        this.delegate = delegate;
    }

    public Layout<ILoggingEvent> getDelegate() {
        return delegate;
    }

    @Override
    public String doLayout(ILoggingEvent event) {
        if (delegate == null) {
            return "[REDACTED:error]";
        }
        try {
            String rendered = delegate.doLayout(event);
            return SecretsSanitiser.getInstance().sanitise(rendered);
        } catch (Throwable t) {
            try {
                return delegate.doLayout(event);
            } catch (Throwable t2) {
                return "[REDACTED:error]";
            }
        }
    }

    @Override
    public void start() {
        if (delegate == null) {
            addError("SanitisingLayout requires a <delegate> layout");
            return;
        }
        // Logback nested layouts are started automatically when added to a parent that is
        // started (e.g., LayoutWrappingEncoder). If the delegate has not been started yet,
        // start it now.
        if (!delegate.isStarted()) {
            delegate.setContext(getContext());
            delegate.start();
        }
        super.start();
    }
}
