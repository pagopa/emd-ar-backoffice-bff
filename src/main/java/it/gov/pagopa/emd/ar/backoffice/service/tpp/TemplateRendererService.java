package it.gov.pagopa.emd.ar.backoffice.service.tpp;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import freemarker.template.Configuration;
import freemarker.template.Template;

@Service
public class TemplateRendererService {

    private final Configuration freemarkerCfg;

    public TemplateRendererService(Configuration freemarkerCfg) {
        this.freemarkerCfg = freemarkerCfg;
    }

    public String render(String templateSource) throws Exception {
        if (templateSource == null || templateSource.isBlank()) return "";

        // Mock data
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

        Template temp = new Template("preview", new StringReader(templateSource), freemarkerCfg);
        StringWriter out = new StringWriter();
        temp.process(mockData, out);
        return out.toString();
    }
}
