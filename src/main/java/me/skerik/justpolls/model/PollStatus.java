package me.skerik.justpolls.model;

public enum PollStatus {
    /** Created, will start at {@code startAt}. */
    SCHEDULED,
    ACTIVE,
    ENDED,
    CANCELLED;

    public boolean isOpen() {
        return this == SCHEDULED || this == ACTIVE;
    }
}
