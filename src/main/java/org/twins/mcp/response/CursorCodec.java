package org.twins.mcp.response;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.twins.mcp.error.ToolInputInvalid;

/**
 * Opaque pagination cursor (ARCH-13; Story 1.5 AC-6). Encodes {@code {page, pageSize, filterHash}}
 * as base64url JSON, where {@code filterHash} is SHA-256 of the request filter. The hash is the
 * critical guard: an agent cannot replay a cursor against a different filter —
 * {@link #verifyFilter(Cursor, String)} rejects the mismatch with {@link ToolInputInvalid}.
 *
 * <p>Spring bean (ObjectMapper-injected for testability); the codec itself is stateless.
 */
@Component
public class CursorCodec {

    private final ObjectMapper mapper;

    public CursorCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** Decoded cursor payload. */
    public record Cursor(int page, int pageSize, String filterHash) {}

    /** Encodes the page position together with a hash of the supplied filter JSON. */
    public String encode(int page, int pageSize, String filterJson) {
        try {
            String hash = sha256Hex(filterJson == null ? "" : filterJson);
            Map<String, Object> payload = Map.of(
                    "page", page,
                    "pageSize", pageSize,
                    "filterHash", hash);
            byte[] json = mapper.writeValueAsBytes(payload);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to encode pagination cursor", e);
        }
    }

    /** Decodes an opaque cursor; throws {@link ToolInputInvalid} if it is missing or malformed. */
    public Cursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            throw new ToolInputInvalid(
                    "Missing pagination cursor",
                    List.of("a cursor is required to fetch the next page"));
        }
        try {
            byte[] json = Base64.getUrlDecoder().decode(cursor);
            return mapper.readValue(json, Cursor.class);
        } catch (IllegalArgumentException | JacksonException e) {
            throw new ToolInputInvalid(
                    "Invalid pagination cursor",
                    List.of("cursor is not a valid opaque token — restart from the first page"));
        }
    }

    /**
     * Rejects a cursor whose stored filter hash does not match the current request's filter —
     * prevents inconsistent pages when the agent changes the filter mid-pagination (AC-6).
     */
    public void verifyFilter(Cursor cursor, String currentFilterJson) {
        String currentHash = sha256Hex(currentFilterJson == null ? "" : currentFilterJson);
        if (!currentHash.equals(cursor.filterHash())) {
            throw new ToolInputInvalid(
                    "Pagination cursor does not match the current filter",
                    List.of("the cursor was issued for a different filter; request the first page without a cursor"));
        }
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
