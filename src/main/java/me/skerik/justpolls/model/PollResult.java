package me.skerik.justpolls.model;

import java.util.ArrayList;
import java.util.List;

/** Counts of a poll with the derived numbers (total, percent, winners). Never contains who voted. */
public record PollResult(Poll poll, int[] counts) {

    public int total() {
        int total = 0;
        for (int c : counts) total += c;
        return total;
    }

    /** Share of option {@code i} in percent, 0 when nobody voted. */
    public double percent(int i) {
        int total = total();
        return total == 0 ? 0 : counts[i] * 100.0 / total;
    }

    /** Indexes with the most votes; empty when nobody voted (several = tie). */
    public List<Integer> winners() {
        List<Integer> winners = new ArrayList<>();
        int max = 0;
        for (int c : counts) max = Math.max(max, c);
        if (max == 0) return winners;
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] == max) winners.add(i);
        }
        return winners;
    }

    public boolean isWinner(int i) {
        return winners().contains(i);
    }
}
