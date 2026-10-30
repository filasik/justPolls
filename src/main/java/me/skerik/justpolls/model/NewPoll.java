package me.skerik.justpolls.model;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Everything needed to create a poll.
 *
 * @param startAt       epoch millis; 0 or a time in the past means "now"
 * @param openDelayMs   per-poll delay of the automatic menu opening, -1 = use {@code auto-open.on-start-delay}
 * @param permission    players need it to see and vote in this poll; empty = everybody with justpolls.vote
 * @param configKey     key under {@code polls:} in config.yml when the poll comes from the config
 * @param repeatMs      config polls only: create the next poll this long after the previous one started, 0 = never
 */
public record NewPoll(String title, String description, List<String> options, long durationMs, long startAt,
                      boolean discord, boolean autoOpen, long openDelayMs, String permission,
                      @Nullable String configKey, long repeatMs, String createdBy) {
}
