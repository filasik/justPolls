package me.skerik.justpolls.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Pseudonymous voter ids. The database never stores a player's UUID next to a vote, only
 * SHA-256(salt : poll : uuid). It is enough to answer "has this player voted in this poll?" and keeps a
 * database dump from showing who voted for what. (The salt lives in the same database, so this is
 * privacy by design, not protection against an attacker with full database access.)
 */
public final class Voters {

    private Voters() {
    }

    public static String hash(String salt, long pollId, UUID player) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha.digest((salt + ':' + pollId + ':' + player).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
