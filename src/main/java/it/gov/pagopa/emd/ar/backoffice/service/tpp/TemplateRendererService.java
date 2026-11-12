package it.gov.pagopa.emd.ar.backoffice.service.tpp;

import java.io.StringReader;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import freemarker.template.Configuration;
import freemarker.template.Template;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.TemplateResourceLimitException;
import lombok.extern.slf4j.Slf4j;

/**
 * Service dedicated to rendering externally-provided FreeMarker templates.
 *
 * <p>It enforces several verifiable resource-containment limits, because the engine
 * security settings alone (SAFER_RESOLVER / disabled built-in API, configured in
 * {@code FreeMarkerConfig}) do not bound CPU/memory usage:</p>
 * <ol>
 *   <li><strong>Input limit</strong>: the template source cannot exceed
 *       {@link #maxTemplateSize} characters (default 8&nbsp;KB). It cuts off the most
 *       obvious abuses before compilation even happens.</li>
 *   <li><strong>Loop/recursion directive ban</strong>: templates (both the stored ones
 *       and those submitted to the preview) cannot use {@code #list}, {@code #items},
 *       {@code #sep}, {@code #foreach}, {@code #while}, {@code #macro}, {@code #function}.
 *       Removing iterative/recursive constructs eliminates the "CPU loop" attack class at
 *       the root (including loops that produce no output and that a plain output limit
 *       would not intercept).</li>
 *   <li><strong>Output limit</strong>: rendering is aborted as soon as the output exceeds
 *       {@link #maxOutputSize} characters (default 10&nbsp;KB). It blocks templates that
 *       produce huge strings.</li>
 *   <li><strong>Execution budget</strong>: FreeMarker's {@code process()} is blocking and
 *       runs on a dedicated thread with a timeout ({@link #renderTimeoutMs}, default 2s).
 *       On expiry the task is cancelled ({@code future.cancel(true)}) and the Writer, which
 *       checks the interruption status on every write, aborts the rendering. This is the
 *       final safety net against any pathological execution not covered by the previous
 *       defenses.</li>
 * </ol>
 *
 * <p>The defenses are layered (defense in depth): even if the syntactic ban were bypassed,
 * the output cap and the execution budget remain active, verifiable guarantees.</p>
 *
 * <p>The injected {@link Configuration} is a shared singleton bean and is never mutated
 * here: its security settings are centralized in {@code FreeMarkerConfig}.</p>
 */
@Slf4j
@Service
public class TemplateRendererService {

    /**
     * Forbidden loop/recursion directives. Matches both the opening form
     * ({@code <#list ...>}) and the closing form ({@code </#list>}). The {@code DOTALL}
     * and {@code CASE_INSENSITIVE} flags ensure a match even on multi-line templates and
     * regardless of case. The {@code \b} boundary after the name avoids false positives on
     * directives that share a common prefix.
     */
    private static final Pattern FORBIDDEN_DIRECTIVES = Pattern.compile(
            ".*</?#(list|items|sep|foreach|while|macro|function)\\b.*",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    /** Global (read-only) FreeMarker engine configuration. */
    private final Configuration freemarkerCfg;

    /** Maximum number of characters allowed in the template source. */
    private final int maxTemplateSize;

    /** Maximum number of characters allowed in the rendered output. */
    private final int maxOutputSize;

    /** Maximum rendering execution budget, in milliseconds. */
    private final long renderTimeoutMs;

    /**
     * Pool dedicated solely to rendering untrusted templates, isolated from the
     * application/Reactor threads. The threads are daemons: a runaway rendering does not
     * prevent the JVM from shutting down.
     */
    private final ExecutorService renderExecutor;

    public TemplateRendererService(
            Configuration freemarkerCfg,
            @Value("${template.renderer.max-template-size:8192}") int maxTemplateSize,
            @Value("${template.renderer.max-output-size:10240}") int maxOutputSize,
            @Value("${template.renderer.timeout-ms:2000}") long renderTimeoutMs,
            @Value("${template.renderer.pool-size:4}") int poolSize) {
        this.freemarkerCfg = freemarkerCfg;
        this.maxTemplateSize = maxTemplateSize;
        this.maxOutputSize = maxOutputSize;
        this.renderTimeoutMs = renderTimeoutMs;

        final AtomicLong counter = new AtomicLong();
        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "template-renderer-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        this.renderExecutor = Executors.newFixedThreadPool(Math.max(1, poolSize), tf);
    }

    /**
     * Compiles and renders the provided template against mock data, enforcing the input,
     * output and execution-time limits.
     *
     * @param templateSource the template source provided by the user
     * @return the rendered string (always within {@code maxOutputSize} characters)
     * @throws TemplateResourceLimitException if a limit is exceeded (input, output or time)
     * @throws Exception if the template has syntax errors or other rendering errors
     */
    public String render(String templateSource) throws Exception {
        if (templateSource == null || templateSource.isBlank()) {
            return "";
        }

        // Input: fail-fast before compiling.
        if (templateSource.length() > maxTemplateSize) {
            throw new TemplateResourceLimitException(
                    "Template too large: " + templateSource.length()
                            + " characters (maximum allowed " + maxTemplateSize + ").");
        }

        // Loop/recursion ban: removes the "CPU loop" attack class at the root,
        // including loops that produce no output.
        if (FORBIDDEN_DIRECTIVES.matcher(templateSource).matches()) {
            throw new TemplateResourceLimitException(
                    "Loop or macro directives (#list, #items, #sep, #foreach, #while, "
                            + "#macro, #function) are not allowed for security reasons.");
        }

        Callable<String> renderTask = () -> doRender(templateSource);

        Future<String> future = renderExecutor.submit(renderTask);
        try {
            // Execution budget (final safety net).
            return future.get(renderTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // Real interruption of the rendering thread: we do not let the task keep running.
            future.cancel(true);
            throw new TemplateResourceLimitException(
                    "Rendering aborted: execution budget of "
                            + renderTimeoutMs + " ms exceeded.");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw new TemplateResourceLimitException("Error while rendering the template.", cause);
        }
    }

    /**
     * Performs the actual rendering on the dedicated thread. It writes to a Writer that
     * enforces the output limit and aborts if the thread has been interrupted
     * (cooperation with the timeout).
     */
    private String doRender(String templateSource) throws Exception {
        Map<String, Object> mockData = getMockData();

        Template temp = new Template("preview", new StringReader(templateSource), freemarkerCfg);

        StringBuilder output = new StringBuilder();
        Writer limitedWriter = new Writer() {
            private int currentSize = 0;

            @Override
            public void write(char[] cbuf, int off, int len) {
                // Cooperation point with the timeout: if the thread has been interrupted
                // (future.cancel(true)), we stop the rendering immediately.
                if (Thread.currentThread().isInterrupted()) {
                    throw new TemplateResourceLimitException(
                            "Rendering aborted: execution budget exceeded.");
                }
                // Output cap.
                if ((long) currentSize + len > maxOutputSize) {
                    throw new TemplateResourceLimitException(
                            "Maximum allowed output size exceeded ("
                                    + maxOutputSize + " characters).");
                }
                output.append(cbuf, off, len);
                currentSize += len;
            }

            @Override
            public void flush() {
                // No-op: output is accumulated in memory, there is nothing to flush.
            }

            @Override
            public void close() {
                // No-op: no native resources (files/sockets) to release.
            }
        };

        temp.process(mockData, limitedWriter);
        return output.toString();
    }

    /**
     * Mock data used for the preview: no database query, just sample values to show how the
     * rendered message will look.
     */
    private Map<String, Object> getMockData() {
        Map<String, Object> mockData = new HashMap<>();
        mockData.put("messageId", "MSG-123456");
        mockData.put("recipientId", "REC-001");
        mockData.put("triggerDateTimeUTC", "2026-01-01T10:00:00Z");
        mockData.put("triggerDateTime", "2026-01-01 12:00:00");
        mockData.put("messageUrl", "https://example.com/msg/123");
        mockData.put("senderDescription", "Descrizione Mittente PagoPA");
        mockData.put("originId", "ORIGIN-99");
        mockData.put("title", "Titolo di Prova");
        mockData.put("content", "Questo è un contenuto di esempio trasformato.");
        mockData.put("analogSchedulingDate", "2026-01-01");
        mockData.put("workflowType", "STANDARD");
        mockData.put("associatedPayment", true);
        return mockData;
    }
}
