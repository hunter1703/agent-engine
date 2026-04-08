package com.agentengine.util.agents.beans;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.Objects;

public final class Confirmation {
    @NotBlank(message = "Confirmation Id is required")
    private String confirmationId;

    private Boolean confirmed;

    public Confirmation() {}

    private Map<String, Object> answer;

    public Confirmation(String confirmationId, Boolean confirmed, Map<String, Object> answer) {
        this.confirmationId = confirmationId;
        this.confirmed = confirmed;
        this.answer = answer;
    }

    public String getConfirmationId() {
        return confirmationId;
    }

    public Boolean getConfirmed() {
        return confirmed;
    }

    public Map<String, Object> getAnswer() {
        return answer;
    }

    public void setConfirmationId(final String confirmationId) {
        this.confirmationId = confirmationId;
    }

    public void setConfirmed(final Boolean confirmed) {
        this.confirmed = confirmed;
    }

    public void setAnswer(final Map<String, Object> answer) {
        this.answer = answer;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (Confirmation) obj;
        return Objects.equals(this.confirmationId, that.confirmationId)
                && Objects.equals(this.confirmed, that.confirmed)
                && Objects.equals(this.answer, that.answer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(confirmationId, confirmed, answer);
    }

    @Override
    public String toString() {
        return "Confirmation[" + "confirmationId="
                + confirmationId + ", " + "confirmed="
                + confirmed + ", " + "answer="
                + answer + ']';
    }
}
