package com.agentengine.util.agents.agui;

/**
 * Custom AG-UI event emitted when a message part contains an attachment (e.g. an image produced by
 * the agent). Emitted after any text chunks and before the {@code TextMessageEndEvent} of the
 * parent message, so consumers can associate the attachment with the correct message.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code parentMessageId} – ID of the {@code TextMessageStartEvent} this attachment belongs to
 *   <li>{@code fileName}        – generated file name derived from the MIME type
 *   <li>{@code mimeType}        – MIME type (e.g. {@code "image/png"})
 * </ul>
 */
public final class AttachmentEvent extends BaseCustomEvent {

    private String parentMessageId;
    private String fileName;
    private String mimeType;

    public AttachmentEvent() {
        super("attachment");
    }

    public AttachmentEvent(final String parentMessageId, final String fileName, final String mimeType) {
        super("attachment");
        this.parentMessageId = parentMessageId;
        this.fileName = fileName;
        this.mimeType = mimeType;
    }

    public String getParentMessageId() {
        return parentMessageId;
    }

    public void setParentMessageId(final String parentMessageId) {
        this.parentMessageId = parentMessageId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(final String fileName) {
        this.fileName = fileName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(final String mimeType) {
        this.mimeType = mimeType;
    }
}
