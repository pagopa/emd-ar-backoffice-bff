package it.gov.pagopa.emd.ar.backoffice.service.tpp;

import freemarker.core.ParseException;
import freemarker.core.InvalidReferenceException;
import freemarker.template.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TemplateRendererServiceTest {

    private TemplateRendererService templateRendererService;
    private Configuration freemarkerConfig;

    @BeforeEach
    void setUp() {
        freemarkerConfig = new Configuration(Configuration.VERSION_2_3_35);
        
        templateRendererService = new TemplateRendererService(freemarkerConfig);
    }

    @Test
    void render_withNullTemplate_returnsEmptyString() throws Exception {
        String result = templateRendererService.render(null);
        
        assertThat(result).isEmpty();
    }

    @Test
    void render_withBlankTemplate_returnsEmptyString() throws Exception {
        String result = templateRendererService.render("   \n  ");
        
        assertThat(result).isEmpty();
    }

    @Test
    void render_withValidTemplateNoVariables_returnsUnchangedString() throws Exception {
        String templateSource = "Testo statico senza variabili.";
        
        String result = templateRendererService.render(templateSource);
        
        assertThat(result).isEqualTo("Testo statico senza variabili.");
    }

    @Test
    void render_withValidTemplateAndVariables_replacesVariablesCorrectly() throws Exception {
        String templateSource = "Messaggio: ${messageId}. Titolo: ${title}. Workflow: ${workflowType}. Check: ${associatedPayment?c}";
        
        String result = templateRendererService.render(templateSource);
        
        assertThat(result).isEqualTo("Messaggio: MSG-123456. Titolo: Titolo di Prova. Workflow: STANDARD. Check: true");
    }

    @Test
    void render_withMissingVariableInMap_throwsException() {
        String templateSource = "Test ${variabileInesistente}";
        
        assertThrows(InvalidReferenceException.class, () -> {
            templateRendererService.render(templateSource);
        });
    }

    @Test
    void render_withInvalidFreemarkerSyntax_throwsException() {
        String templateSource = "Errore di sintassi: ${messageId";
        
        assertThrows(ParseException.class, () -> {
            templateRendererService.render(templateSource);
        });
    }
}