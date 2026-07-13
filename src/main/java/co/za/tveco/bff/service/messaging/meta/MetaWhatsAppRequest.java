package co.za.tveco.bff.service.messaging.meta;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Meta WhatsApp Cloud API request payload for sending messages.
 * Documentation: https://developers.facebook.com/docs/whatsapp/cloud-api/reference/messages
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MetaWhatsAppRequest {

    @JsonProperty("messaging_product")
    private String messagingProduct = "whatsapp";

    @JsonProperty("recipient_type")
    private String recipientType = "individual";

    @JsonProperty("to")
    private String to;

    @JsonProperty("type")
    private String type = "template";

    @JsonProperty("template")
    private Template template;

    public MetaWhatsAppRequest() {
    }

    public MetaWhatsAppRequest(String to, String templateName, List<String> parameters) {
        this.to = to;
        this.template = new Template(templateName, new Template.Language(), parameters);
    }

    public String getMessagingProduct() {
        return messagingProduct;
    }

    public void setMessagingProduct(String messagingProduct) {
        this.messagingProduct = messagingProduct;
    }

    public String getRecipientType() {
        return recipientType;
    }

    public void setRecipientType(String recipientType) {
        this.recipientType = recipientType;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Template getTemplate() {
        return template;
    }

    public void setTemplate(Template template) {
        this.template = template;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Template {
        @JsonProperty("name")
        private String name;

        @JsonProperty("language")
        private Language language;

        @JsonProperty("components")
        private List<Component> components;

        public Template() {
        }

        public Template(String name, Language language, List<String> parameters) {
            this.name = name;
            this.language = language;
            if (parameters != null && !parameters.isEmpty()) {
                this.components = List.of(new Component("body", new Component.Body(parameters)));
            }
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Language getLanguage() {
            return language;
        }

        public void setLanguage(Language language) {
            this.language = language;
        }

        public List<Component> getComponents() {
            return components;
        }

        public void setComponents(List<Component> components) {
            this.components = components;
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class Language {
            @JsonProperty("code")
            private String code = "en";

            public String getCode() {
                return code;
            }

            public void setCode(String code) {
                this.code = code;
            }
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Component {
        @JsonProperty("type")
        private String type;

        @JsonProperty("body")
        private Body body;

        public Component() {
        }

        public Component(String type, Body body) {
            this.type = type;
            this.body = body;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Body getBody() {
            return body;
        }

        public void setBody(Body body) {
            this.body = body;
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class Body {
            @JsonProperty("parameters")
            private List<Parameter> parameters;

            public Body() {
            }

            public Body(List<String> parameterValues) {
                this.parameters = parameterValues.stream()
                        .map(Parameter::new)
                        .toList();
            }

            public List<Parameter> getParameters() {
                return parameters;
            }

            public void setParameters(List<Parameter> parameters) {
                this.parameters = parameters;
            }
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class Parameter {
            @JsonProperty("type")
            private String type = "text";

            @JsonProperty("text")
            private String text;

            public Parameter() {
            }

            public Parameter(String text) {
                this.text = text;
            }

            public String getType() {
                return type;
            }

            public void setType(String type) {
                this.type = type;
            }

            public String getText() {
                return text;
            }

            public void setText(String text) {
                this.text = text;
            }
        }
    }
}
