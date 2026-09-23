package co.za.tveco.bff.service.messaging.email;

/**
 * Brand-consistent HTML email templates for TVECO account recovery messages.
 * <p>
 * Colors and typography mirror the TVECO Operations Hub UI:
 * night background #0A0C0F, card #111318, border #252B35,
 * white text #F0F4F8, muted text #8A99AE, brand orange #FF6B00.
 * <p>
 * Templates use table-based layout and inline styles for maximum
 * compatibility across email clients (Outlook, Gmail, Apple Mail, etc.).
 */
final class EmailTemplates {

    private static final String LOGO_URL = "https://tveco.co.za/assets/logo/tvec-logo-on-dark.png";
    private static final String NIGHT = "#0A0C0F";
    private static final String CARD = "#111318";
    private static final String BORDER = "#252B35";
    private static final String WHITE = "#F0F4F8";
    private static final String MUTED = "#8A99AE";
    private static final String ORANGE = "#FF6B00";
    private static final String FONT_STACK = "'Segoe UI', Helvetica, Arial, sans-serif";

    private EmailTemplates() {
    }

    static String passwordResetHtml(String resetLink) {
        String content = ""
                + heading("Password Reset")
                + paragraph("We received a request to reset the password for your TVECO account.")
                + paragraph("Click the button below to set a new password. This link is valid for the next <strong>30 minutes</strong>.")
                + button(resetLink, "Reset Password")
                + fallbackLink(resetLink)
                + divider()
                + smallMuted("If you did not request this, no action is required — your password will remain unchanged. "
                        + "For security, never share this link with anyone.");
        return wrap(content);
    }

    static String otpHtml(String otp, String purpose) {
        boolean isUsernameRecovery = "USERNAME_RECOVERY".equals(purpose);
        String title = isUsernameRecovery ? "Username Recovery" : "Password Reset Code";
        String intro = isUsernameRecovery
                ? "Use the one-time code below to verify your identity and recover your TVECO account username."
                : "Use the one-time code below to verify your identity and reset your TVECO account password.";

        String content = ""
                + heading(title)
                + paragraph(intro)
                + otpCode(otp)
                + paragraph("This code expires in <strong>10 minutes</strong>.")
                + divider()
                + smallMuted("If you did not request this code, you can safely ignore this email. "
                        + "Never share this code with anyone, including TVECO staff.");
        return wrap(content);
    }

    // ── Layout building blocks ──────────────────────────────────────────────

    private static String wrap(String innerContent) {
        return "<!DOCTYPE html>"
                + "<html lang=\"en\"><head><meta charset=\"UTF-8\" />"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />"
                + "<title>TVECO</title></head>"
                + "<body style=\"margin:0;padding:0;background-color:" + NIGHT + ";\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"background-color:" + NIGHT + ";padding:32px 16px;\">"
                + "<tr><td align=\"center\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"max-width:520px;\">"

                // Top accent bar
                + "<tr><td style=\"height:4px;line-height:4px;font-size:0;background-color:" + ORANGE
                + ";border-radius:8px 8px 0 0;\">&nbsp;</td></tr>"

                // Card
                + "<tr><td style=\"background-color:" + CARD + ";border:1px solid " + BORDER
                + ";border-top:none;border-radius:0 0 16px 16px;padding:36px 32px;\">"

                // Logo
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">"
                + "<tr><td align=\"center\" style=\"padding-bottom:28px;\">"
                + "<img src=\"" + LOGO_URL + "\" alt=\"TVECO\" height=\"36\" "
                + "style=\"height:36px;display:block;border:0;\" />"
                + "</td></tr></table>"

                + innerContent

                + "</td></tr>"

                // Footer
                + "<tr><td style=\"padding:24px 8px 0;\">"
                + "<p style=\"margin:0;font-family:" + FONT_STACK + ";font-size:11px;line-height:1.6;color:"
                + MUTED + ";text-align:center;\">"
                + "TVECO &middot; Transport &amp; Export Operations<br />"
                + "This is an automated message from TVECO Operations Hub."
                + "</p></td></tr>"

                + "</table></td></tr></table>"
                + "</body></html>";
    }

    private static String heading(String text) {
        return "<h1 style=\"margin:0 0 16px;font-family:" + FONT_STACK + ";font-size:22px;line-height:1.3;"
                + "font-weight:700;letter-spacing:0.5px;color:" + WHITE + ";text-align:center;\">"
                + escape(text) + "</h1>";
    }

    private static String paragraph(String htmlText) {
        return "<p style=\"margin:0 0 20px;font-family:" + FONT_STACK + ";font-size:15px;line-height:1.6;"
                + "color:" + WHITE + ";text-align:center;\">" + htmlText + "</p>";
    }

    private static String smallMuted(String text) {
        return "<p style=\"margin:0;font-family:" + FONT_STACK + ";font-size:12px;line-height:1.6;"
                + "color:" + MUTED + ";text-align:center;\">" + escape(text) + "</p>";
    }

    private static String button(String href, String label) {
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">"
                + "<tr><td align=\"center\" style=\"padding:8px 0 24px;\">"
                + "<a href=\"" + href + "\" target=\"_blank\" "
                + "style=\"display:inline-block;background-color:" + ORANGE + ";color:#ffffff;"
                + "font-family:" + FONT_STACK + ";font-size:15px;font-weight:700;text-decoration:none;"
                + "padding:14px 32px;border-radius:8px;letter-spacing:0.3px;\">"
                + escape(label) + "</a>"
                + "</td></tr></table>";
    }

    private static String fallbackLink(String href) {
        return "<p style=\"margin:0 0 24px;font-family:" + FONT_STACK + ";font-size:12px;line-height:1.6;"
                + "color:" + MUTED + ";text-align:center;word-break:break-all;\">"
                + "Button not working? Copy and paste this link into your browser:<br />"
                + "<a href=\"" + href + "\" style=\"color:" + ORANGE + ";text-decoration:underline;\">"
                + href + "</a></p>";
    }

    private static String otpCode(String otp) {
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">"
                + "<tr><td align=\"center\" style=\"padding:4px 0 24px;\">"
                + "<div style=\"display:inline-block;background-color:" + NIGHT + ";border:1px solid " + BORDER
                + ";border-radius:10px;padding:16px 28px;\">"
                + "<span style=\"font-family:'Courier New',monospace;font-size:32px;font-weight:700;"
                + "letter-spacing:10px;color:" + ORANGE + ";\">" + escape(otp) + "</span>"
                + "</div></td></tr></table>";
    }

    private static String divider() {
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">"
                + "<tr><td style=\"padding:4px 0 20px;\">"
                + "<div style=\"height:1px;line-height:1px;font-size:0;background-color:" + BORDER + ";\">&nbsp;</div>"
                + "</td></tr></table>";
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
