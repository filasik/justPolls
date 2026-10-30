package me.skerik.justpolls.model;

public enum VoteOutcome {
    VOTED,
    /** The player moved an existing vote to another option (only with {@code voting.allow-change}). */
    CHANGED,
    ALREADY_VOTED,
    /** The poll ended, was cancelled or deleted in the meantime. */
    CLOSED,
    INVALID_OPTION
}
