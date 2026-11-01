package io.github.vecluu.jdoc.index;

import io.github.vecluu.jdoc.error.IndexException;
import io.github.vecluu.jdoc.model.RenderedDoc;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Compact binary encoding of a {@link RenderedDoc} for the {@code *_docs} tables.
 *
 * <p>Layout: one flag byte, then the body. The body is a sequence of unsigned LEB128 varints and length-prefixed UTF-8
 * strings: description, first sentence (or, when it is a prefix of the description, only its length in chars), the
 * number of block tags, the tags. Bodies of at least {@link #DEFLATE_THRESHOLD} bytes are deflated (raw deflate, no
 * header) when that makes them smaller; the inflated length is then written in front of the deflated bytes.
 * Stateless and thread-safe.
 */
final class DocCodec {

    /** Bodies shorter than this are stored as they are: deflate gains little on a sentence or two. */
    static final int DEFLATE_THRESHOLD = 160;

    private static final int INHERITS_DOC = 1;
    private static final int DEFLATED = 1 << 1;
    private static final int FIRST_SENTENCE_IS_PREFIX = 1 << 2;
    private static final int KNOWN_FLAGS = INHERITS_DOC | DEFLATED | FIRST_SENTENCE_IS_PREFIX;

    private DocCodec() {
    }

    /** Encodes a doc. */
    static byte[] encode(RenderedDoc doc) {
        int flags = doc.inheritsDoc() ? INHERITS_DOC : 0;
        Body body = new Body();
        body.string(doc.description());
        if (!doc.firstSentence().isEmpty() && doc.description().startsWith(doc.firstSentence())) {
            flags |= FIRST_SENTENCE_IS_PREFIX;
            body.varint(doc.firstSentence().length());
        } else {
            body.string(doc.firstSentence());
        }
        body.varint(doc.blockTags().size());
        doc.blockTags().forEach(body::string);
        byte[] plain = body.toByteArray();
        if (plain.length >= DEFLATE_THRESHOLD) {
            byte[] deflated = deflate(plain);
            Body packed = new Body();
            packed.varint(plain.length);
            packed.bytes(deflated);
            if (packed.size() < plain.length) {
                return withFlags(flags | DEFLATED, packed.toByteArray());
            }
        }
        return withFlags(flags, plain);
    }

    /**
     * Decodes what {@link #encode(RenderedDoc)} produced.
     *
     * @throws IndexException if the bytes are not a valid encoding
     */
    static RenderedDoc decode(byte[] encoded) {
        if (encoded == null || encoded.length == 0 || (encoded[0] & ~KNOWN_FLAGS) != 0) {
            throw new IndexException("corrupt doc in the index (bad header)");
        }
        int flags = encoded[0];
        try {
            Reader reader = new Reader(encoded, 1);
            if ((flags & DEFLATED) != 0) {
                int length = reader.varint();
                reader = new Reader(inflate(encoded, reader.position, length), 0);
            }
            String description = reader.string();
            String firstSentence = (flags & FIRST_SENTENCE_IS_PREFIX) != 0
                    ? description.substring(0, reader.varint())
                    : reader.string();
            int tagCount = reader.varint();
            List<String> tags = new ArrayList<>(Math.min(tagCount, 64));
            for (int i = 0; i < tagCount; i++) {
                tags.add(reader.string());
            }
            return new RenderedDoc(description, firstSentence, tags, (flags & INHERITS_DOC) != 0);
        } catch (IndexOutOfBoundsException | NegativeArraySizeException | DataFormatException e) {
            throw new IndexException("corrupt doc in the index: " + e.getMessage(), e);
        }
    }

    private static byte[] withFlags(int flags, byte[] body) {
        byte[] result = new byte[body.length + 1];
        result[0] = (byte) flags;
        System.arraycopy(body, 0, result, 1, body.length);
        return result;
    }

    private static byte[] deflate(byte[] plain) {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        try {
            deflater.setInput(plain);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(plain.length / 2 + 16);
            byte[] buffer = new byte[Math.max(64, plain.length)];
            while (!deflater.finished()) {
                out.write(buffer, 0, deflater.deflate(buffer));
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    private static byte[] inflate(byte[] encoded, int offset, int length) throws DataFormatException {
        Inflater inflater = new Inflater(true);
        try {
            inflater.setInput(encoded, offset, encoded.length - offset);
            byte[] plain = new byte[length];
            int filled = 0;
            while (filled < length) {
                int n = inflater.inflate(plain, filled, length - filled);
                if (n == 0 && (inflater.finished() || inflater.needsInput() || inflater.needsDictionary())) {
                    throw new DataFormatException("deflated doc is truncated");
                }
                filled += n;
            }
            return plain;
        } finally {
            inflater.end();
        }
    }

    /** Growing output buffer with varint and string writers. */
    private static final class Body extends ByteArrayOutputStream {

        Body() {
            super(256);
        }

        void varint(int value) {
            int rest = value;
            while ((rest & ~0x7f) != 0) {
                write((rest & 0x7f) | 0x80);
                rest >>>= 7;
            }
            write(rest);
        }

        void string(String text) {
            byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
            varint(utf8.length);
            bytes(utf8);
        }

        void bytes(byte[] data) {
            write(data, 0, data.length);
        }
    }

    /** Reads varints and strings from a byte array. */
    private static final class Reader {
        private final byte[] data;
        private int position;

        Reader(byte[] data, int position) {
            this.data = data;
            this.position = position;
        }

        int varint() {
            int value = 0;
            for (int shift = 0; shift < 35; shift += 7) {
                byte b = data[position++];
                value |= (b & 0x7f) << shift;
                if ((b & 0x80) == 0) {
                    if (value < 0) {
                        throw new IndexOutOfBoundsException("negative length " + value);
                    }
                    return value;
                }
            }
            throw new IndexOutOfBoundsException("varint too long");
        }

        String string() {
            int length = varint();
            if (length > data.length - position) {
                throw new IndexOutOfBoundsException("string of " + length + " bytes past the end");
            }
            String text = new String(data, position, length, StandardCharsets.UTF_8);
            position += length;
            return text;
        }
    }
}
