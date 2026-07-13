package co.za.tveco.bff.service.messaging.meta;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Meta WhatsApp Cloud API response for successful message delivery.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MetaWhatsAppResponse {

    @JsonProperty("messages")
    private Message[] messages;

    @JsonProperty("contacts")
    private Contact[] contacts;

    @JsonProperty("error")
    private Error error;

    public String getMessageId() {
        if (messages != null && messages.length > 0) {
            return messages[0].id;
        }
        return null;
    }

    public boolean isSuccess() {
        return error == null && messages != null && messages.length > 0;
    }

    public String getErrorMessage() {
        if (error != null) {
            return error.message + " (code: " + error.code + ")";
        }
        return null;
    }

    public Message[] getMessages() {
        return messages;
    }

    public void setMessages(Message[] messages) {
        this.messages = messages;
    }

    public Contact[] getContacts() {
        return contacts;
    }

    public void setContacts(Contact[] contacts) {
        this.contacts = contacts;
    }

    public Error getError() {
        return error;
    }

    public void setError(Error error) {
        this.error = error;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Message {
        @JsonProperty("id")
        public String id;

        @JsonProperty("message_status")
        public String messageStatus;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Contact {
        @JsonProperty("input")
        public String input;

        @JsonProperty("wa_id")
        public String waId;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Error {
        @JsonProperty("message")
        public String message;

        @JsonProperty("type")
        public String type;

        @JsonProperty("code")
        public int code;

        @JsonProperty("error_data")
        public ErrorData errorData;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorData {
        @JsonProperty("messaging_product")
        public String messagingProduct;

        @JsonProperty("details")
        public String details;
    }
}
