package main.walksy.lib.control.internal;

import main.walksy.lib.control.api.ClientInfo;
import main.walksy.lib.control.api.ReportedMod;
import main.walksy.lib.control.api.Restriction;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/*
 * Wire format shared with the WalksyLib client. Numbers are VarInts; a String is a VarInt byte
 * length then UTF-8; a record is a VarInt byte length then its fields.
 *
 *   walksylib:control  version, mod count, mod record*, lifted count, lifted record*
 *     mod record       mod id, boolean whole mod off, reason, feature count, feature record*
 *     feature record   feature id, reason
 *     lifted record    mod id, feature id ("" = whole mod), reason it is back on
 *
 *   walksylib:hello    version, mod count, mod record*
 *     mod record       mod id, display name, boolean whole mod allowed, feature count, feature record*
 *     feature record   feature id
 *
 * Changing the format (keeps old clients and old plugins working with new ones):
 * - Only append: add fields at the end of a record, or sections at the end of a message. Readers
 *   ignore leftover bytes in a record or after the last section they know, and treat a missing
 *   trailing section as empty.
 * - Anything else (removing, reordering or retyping a field) must bump PROTOCOL. A reader drops
 *   a message whose version it doesn't know, so nothing gets switched off.
 */
public final class ControlProtocol {
    public static final int PROTOCOL = 4;
    public static final String CONTROL_CHANNEL = "walksylib:control";
    public static final String HELLO_CHANNEL = "walksylib:hello";
    public static final int MAX_DISPLAY_NAME_LENGTH = 64;
    public static final int MAX_REASON_LENGTH = 512;
    public static final int MAX_COUNT = 512;

    private ControlProtocol() {
    }

    static byte[] encode(final Snapshot snapshot, final Map<Restriction, String> lifted) {
        final Writer out = new Writer();
        out.varInt(PROTOCOL);
        final List<Map.Entry<String, Snapshot.ModEntry>> mods = capped(List.copyOf(snapshot.mods().entrySet()));
        out.varInt(mods.size());
        for (final Map.Entry<String, Snapshot.ModEntry> mod : mods) {
            out.record(body -> {
                body.string(mod.getKey());
                body.bool(mod.getValue().wholeMod());
                body.string(mod.getValue().modReason());
                final List<Map.Entry<String, String>> features = capped(List.copyOf(mod.getValue().features().entrySet()));
                body.varInt(features.size());
                for (final Map.Entry<String, String> feature : features) {
                    body.record(entry -> {
                        entry.string(feature.getKey());
                        entry.string(feature.getValue());
                    });
                }
            });
        }
        final List<Map.Entry<Restriction, String>> lifts = capped(List.copyOf(lifted.entrySet()));
        out.varInt(lifts.size());
        for (final Map.Entry<Restriction, String> lift : lifts) {
            out.record(body -> {
                body.string(lift.getKey().modId());
                body.string(lift.getKey().isWholeMod() ? "" : lift.getKey().feature());
                body.string(lift.getValue());
            });
        }
        return out.bytes();
    }

    static ClientInfo decodeHello(final byte[] bytes) {
        final ClientInfo raw = parseHello(bytes);
        final List<ReportedMod> clean = new ArrayList<>(raw.mods().size());
        final Set<String> seen = new HashSet<>();
        for (final ReportedMod mod : raw.mods()) {
            if (!Restriction.isValidId(mod.modId())) {
                continue;
            }
            final String modId = mod.modId().toLowerCase(Locale.ROOT);
            if (!seen.add(modId)) {
                continue;
            }
            final Set<String> features = new LinkedHashSet<>();
            for (final String feature : mod.features()) {
                if (Restriction.isValidId(feature)) {
                    features.add(feature.toLowerCase(Locale.ROOT));
                }
            }
            final String name = displayName(mod.displayName());
            clean.add(new ReportedMod(modId, name.isEmpty() ? modId : name, mod.wholeMod(), List.copyOf(features)));
        }
        return new ClientInfo(raw.protocol(), clean);
    }

    static String displayName(final String raw) {
        final StringBuilder out = new StringBuilder(Math.min(raw.length(), MAX_DISPLAY_NAME_LENGTH));
        boolean space = false;
        for (int i = 0; i < raw.length() && out.length() < MAX_DISPLAY_NAME_LENGTH; i++) {
            final char c = raw.charAt(i);
            if (Character.isWhitespace(c) || Character.isSpaceChar(c)) {
                space = !out.isEmpty();
                continue;
            }
            if (Character.isISOControl(c) || c == '§' || Character.getType(c) == Character.FORMAT
                    || (Character.isSurrogate(c) && !isPairedSurrogate(raw, i))) {
                continue;
            }
            if (space) {
                out.append(' ');
                space = false;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static boolean isPairedSurrogate(final String text, final int index) {
        final char c = text.charAt(index);
        if (Character.isHighSurrogate(c)) {
            return index + 1 < text.length() && Character.isLowSurrogate(text.charAt(index + 1));
        }
        return index > 0 && Character.isHighSurrogate(text.charAt(index - 1));
    }

    private static ClientInfo parseHello(final byte[] bytes) {
        try {
            final Reader in = new Reader(ByteBuffer.wrap(bytes));
            final int protocol = in.varInt();
            if (protocol != PROTOCOL) {
                throw new IllegalArgumentException("client speaks protocol " + protocol + ", this plugin speaks " + PROTOCOL);
            }
            final int count = in.count();
            final List<ReportedMod> mods = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                final Reader mod = in.record();
                final String modId = mod.string();
                final String displayName = mod.string();
                final boolean wholeMod = mod.bool();
                final int featureCount = mod.count();
                final List<String> features = new ArrayList<>(featureCount);
                for (int j = 0; j < featureCount; j++) {
                    features.add(mod.record().string());
                }
                mods.add(new ReportedMod(modId, displayName, wholeMod, features));
            }
            return new ClientInfo(protocol, mods);
        } catch (final IllegalArgumentException e) {
            throw e;
        } catch (final RuntimeException e) {
            throw new IllegalArgumentException("malformed hello: " + e, e);
        }
    }

    private static <T> List<T> capped(final List<T> items) {
        return items.size() <= MAX_COUNT ? items : items.subList(0, MAX_COUNT);
    }

    private static final class Writer {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        void varInt(final int value) {
            int remaining = value;
            while ((remaining & ~0x7F) != 0) {
                this.out.write((remaining & 0x7F) | 0x80);
                remaining >>>= 7;
            }
            this.out.write(remaining);
        }

        void bool(final boolean value) {
            this.out.write(value ? 1 : 0);
        }

        void string(final String value) {
            final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            this.varInt(bytes.length);
            this.out.writeBytes(bytes);
        }

        void record(final Consumer<Writer> body) {
            final Writer inner = new Writer();
            body.accept(inner);
            final byte[] bytes = inner.bytes();
            this.varInt(bytes.length);
            this.out.writeBytes(bytes);
        }

        byte[] bytes() {
            return this.out.toByteArray();
        }
    }

    private record Reader(ByteBuffer in) {

        int varInt() {
                int value = 0;
                for (int shift = 0; shift < 35; shift += 7) {
                    final byte b = this.in.get();
                    value |= (b & 0x7F) << shift;
                    if ((b & 0x80) == 0) {
                        return value;
                    }
                }
                throw new IllegalArgumentException("VarInt too big");
            }

            int count() {
                final int count = this.varInt();
                if (count < 0 || count > MAX_COUNT) {
                    throw new IllegalArgumentException("count " + count + " out of range");
                }
                return count;
            }

            boolean bool() {
                return this.in.get() != 0;
            }

            String string() {
                final int length = this.varInt();
                if (length < 0 || length > this.in.remaining()) {
                    throw new IllegalArgumentException("string length " + length + " out of range");
                }
                final byte[] bytes = new byte[length];
                this.in.get(bytes);
                return new String(bytes, StandardCharsets.UTF_8);
            }

            Reader record() {
                final int length = this.varInt();
                if (length < 0 || length > this.in.remaining()) {
                    throw new IllegalArgumentException("record length " + length + " out of range");
                }
                final ByteBuffer slice = this.in.slice(this.in.position(), length);
                this.in.position(this.in.position() + length);
                return new Reader(slice);
            }
        }
}
