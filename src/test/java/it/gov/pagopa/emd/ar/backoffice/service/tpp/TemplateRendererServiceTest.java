package it.gov.pagopa.emd.ar.backoffice.service.tpp;

import freemarker.core.ParseException;
import freemarker.core.InvalidReferenceException;
import freemarker.template.Configuration;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.TemplateResourceLimitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link TemplateRendererService}, covering both the basic rendering
 * behaviour and the four resource-containment limits (input size, loop/recursion ban,
 * output size and execution budget).
 */
class TemplateRendererServiceTest {

    private TemplateRendererService templateRendererService;
    private Configuration freemarkerConfig;

    @BeforeEach
    void setUp() {
        freemarkerConfig = new Configuration(Configuration.VERSION_2_3_35);

        templateRendererService = new TemplateRendererService(
                freemarkerConfig,
                8192,
                10240,
                2000,
                2);
    }

    /** A null source renders to an empty string. */
    @Test
    void render_withNullTemplate_returnsEmptyString() throws Exception {
        String result = templateRendererService.render(null);

        assertThat(result).isEmpty();
    }

    /** A blank source renders to an empty string. */
    @Test
    void render_withBlankTemplate_returnsEmptyString() throws Exception {
        String result = templateRendererService.render("   \n  ");

        assertThat(result).isEmpty();
    }

    /** A static template with no variables is returned unchanged. */
    @Test
    void render_withValidTemplateNoVariables_returnsUnchangedString() throws Exception {
        String templateSource = "Testo statico senza variabili.";

        String result = templateRendererService.render(templateSource);

        assertThat(result).isEqualTo("Testo statico senza variabili.");
    }

    /** Interpolations are replaced with the corresponding mock-data values. */
    @Test
    void render_withValidTemplateAndVariables_replacesVariablesCorrectly() throws Exception {
        String templateSource = "Messaggio: ${messageId}. Titolo: ${title}. Workflow: ${workflowType}. Check: ${associatedPayment?c}";

        String result = templateRendererService.render(templateSource);

        assertThat(result).isEqualTo("Messaggio: MSG-123456. Titolo: Titolo di Prova. Workflow: STANDARD. Check: true");
    }

    /** Referencing a variable absent from the mock data fails. */
    @Test
    void render_withMissingVariableInMap_throwsException() {
        String templateSource = "Test ${variabileInesistente}";

        assertThrows(InvalidReferenceException.class, () -> {
            templateRendererService.render(templateSource);
        });
    }

    /** A malformed template (syntax error) fails at parse time. */
    @Test
    void render_withInvalidFreemarkerSyntax_throwsException() {
        String templateSource = "Errore di sintassi: ${messageId";

        assertThrows(ParseException.class, () -> {
            templateRendererService.render(templateSource);
        });
    }

    /** LIMIT 1: a source longer than the input cap is rejected before compilation. */
    @Test
    void render_withTemplateExceedingInputLimit_throwsResourceLimit() {
        TemplateRendererService svc = new TemplateRendererService(
                new Configuration(Configuration.VERSION_2_3_35), 5, 10240, 2000, 1);

        String templateSource = "Testo troppo lungo per il limite";

        assertThrows(TemplateResourceLimitException.class, () -> svc.render(templateSource));
    }

    /** LIMIT 2: the {@code #list} directive is rejected. */
    @Test
    void render_withListDirective_throwsResourceLimit() {
        String templateSource = "<#list 1..1000000000 as i>x</#list>";

        assertThrows(TemplateResourceLimitException.class,
                () -> templateRendererService.render(templateSource));
    }

    /** LIMIT 2: a CPU-burning loop that produces no output is blocked before execution. */
    @Test
    void render_withCpuRunawayNoOutput_isBlockedBeforeExecution() {
        String templateSource =
                "<#assign s=0><#list 1..2000000000 as i><#assign s=s+i></#list>${s}";

        assertThrows(TemplateResourceLimitException.class,
                () -> templateRendererService.render(templateSource));
    }

    /** LIMIT 2: the {@code #macro} directive is rejected. */
    @Test
    void render_withMacroDirective_throwsResourceLimit() {
        String templateSource = "<#macro m><#nested></#macro><@m>ciao</@m>";

        assertThrows(TemplateResourceLimitException.class,
                () -> templateRendererService.render(templateSource));
    }

    /** LIMIT 2: forbidden directives are detected on multi-line templates (DOTALL flag). */
    @Test
    void render_withMultilineListDirective_throwsResourceLimit() {
        String templateSource = "Riga 1\n<#list 1..10 as i>\n${i}\n</#list>\nFine";

        assertThrows(TemplateResourceLimitException.class,
                () -> templateRendererService.render(templateSource));
    }

    /** LIMIT 2: forbidden directives are detected regardless of case. */
    @Test
    void render_withListUppercaseDirective_throwsResourceLimit() {
        String templateSource = "<#LIST 1..10 as i>x</#LIST>";

        assertThrows(TemplateResourceLimitException.class,
                () -> templateRendererService.render(templateSource));
    }

    /** LIMIT 2: a plain word containing "list" is not a directive and is allowed. */
    @Test
    void render_withWordContainingListSubstring_isAllowed() throws Exception {
        String result = templateRendererService.render("Il listino prezzi è ${title}");

        assertThat(result).isEqualTo("Il listino prezzi è Titolo di Prova");
    }

    /** LIMIT 3: output exceeding the cap aborts rendering at runtime (no loop directive). */
    @Test
    void render_withOutputExceedingLimit_throwsResourceLimit() {
        TemplateRendererService svc = new TemplateRendererService(
                new Configuration(Configuration.VERSION_2_3_35), 8192, 10, 2000, 1);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            sb.append("${title}");
        }

        assertThrows(TemplateResourceLimitException.class, () -> svc.render(sb.toString()));
    }
}
