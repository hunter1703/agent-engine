package com.agentengine.util.agents.agui;

import com.agentengine.util.common.beans.FileDetails;

/**
 * Custom AG-UI event emitted when a message part contains an attachment (e.g. an image produced by
 * the agent). Emitted after any text chunks and before the {@code TextMessageEndEvent} of the
 * parent message, so consumers can associate the attachment with the correct message.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code parentMessageId} – ID of the {@code TextMessageStartEvent} this attachment belongs to
 *   <li>{@code fileDetails}     – fileDetails
 * </ul>
 */
public final class AttachmentEvent extends BaseCustomEvent {

    private String parentMessageId;
    private FileDetails fileDetails;

    public AttachmentEvent() {
        super("attachment");
    }

    public AttachmentEvent(final String parentMessageId, final FileDetails fileDetails) {
        super("attachment");
        this.parentMessageId = parentMessageId;
        this.fileDetails = fileDetails;
    }

    public String getParentMessageId() {
        return parentMessageId;
    }

    public void setParentMessageId(final String parentMessageId) {
        this.parentMessageId = parentMessageId;
    }

    public FileDetails getFileDetails() {
        return fileDetails;
    }

    public void setFileDetails(final FileDetails fileDetails) {
        this.fileDetails = fileDetails;
    }
}
