package mk.ripp.peer;

import mk.ripp.peer.model.RipDataRequest;
import mk.ripp.peer.model.RipDataResponse;
import mk.ripp.peer.model.RipDataStatus;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Binary layout for chunk requests and responses on a data channel. */
public final class RipDataCodec {
    /** WebRTC rejects a single data-channel message around this size, so responses are framed. */
    private static final int MAX_PAYLOAD = 16 * 1024;
    private static final int REQUEST_BYTES = 16 + Integer.BYTES;
    private static final int FRAME_HEADER = 16 + Integer.BYTES + Byte.BYTES + Integer.BYTES + Integer.BYTES + Integer.BYTES;

    private RipDataCodec() {
    }

    public static ByteBuffer encodeRequest(RipDataRequest request) {
        ByteBuffer buffer = ByteBuffer.allocate(REQUEST_BYTES);
        putUuid(buffer, request.ripId());
        buffer.putInt(request.sequence());
        buffer.flip();
        return buffer;
    }

    public static RipDataRequest decodeRequest(ByteBuffer buffer) {
        ByteBuffer input = buffer.duplicate();
        if (input.remaining() < REQUEST_BYTES) {
            throw new IllegalArgumentException("RipDataRequest is too short");
        }
        UUID ripId = getUuid(input);
        int sequence = input.getInt();
        return new RipDataRequest(ripId, sequence);
    }

    public static List<ByteBuffer> encodeResponse(RipDataResponse response) {
        byte[] data = response.data() == null ? new byte[0] : response.data();
        int count = Math.max(1, (data.length + MAX_PAYLOAD - 1) / MAX_PAYLOAD);
        List<ByteBuffer> frames = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int offset = index * MAX_PAYLOAD;
            int length = Math.min(MAX_PAYLOAD, Math.max(0, data.length - offset));
            ByteBuffer buffer = ByteBuffer.allocate(FRAME_HEADER + length);
            putUuid(buffer, response.ripId());
            buffer.putInt(response.sequence());
            buffer.put(status(response.status()));
            buffer.putInt(index);
            buffer.putInt(count);
            buffer.putInt(length);
            if (length > 0) {
                buffer.put(data, offset, length);
            }
            buffer.flip();
            frames.add(buffer);
        }
        return frames;
    }

    /** Collects the frames of one response. Returns the response once every frame has arrived. */
    public static final class Assembler {
        private UUID ripId;
        private int sequence;
        private RipDataStatus status;
        private byte[][] pieces;
        private int received;

        public RipDataResponse add(ByteBuffer frame) {
            ByteBuffer input = frame.duplicate();
            if (input.remaining() < FRAME_HEADER) {
                throw new IllegalArgumentException("RipDataResponse frame is too short");
            }
            UUID id = getUuid(input);
            int frameSequence = input.getInt();
            RipDataStatus frameStatus = status(input.get());
            int index = input.getInt();
            int count = input.getInt();
            int length = input.getInt();
            if (count <= 0 || index < 0 || index >= count || length < 0 || input.remaining() < length) {
                throw new IllegalArgumentException("RipDataResponse frame is invalid");
            }
            if (pieces == null) {
                ripId = id;
                sequence = frameSequence;
                status = frameStatus;
                pieces = new byte[count][];
            } else if (!ripId.equals(id) || sequence != frameSequence || status != frameStatus || pieces.length != count) {
                throw new IllegalArgumentException("RipDataResponse frames do not belong together");
            }
            if (pieces[index] != null) {
                throw new IllegalArgumentException("Duplicate RipDataResponse frame " + index);
            }
            byte[] payload = new byte[length];
            input.get(payload);
            pieces[index] = payload;
            received++;
            if (received < count) {
                return null;
            }
            int total = 0;
            for (byte[] piece : pieces) {
                total += piece.length;
            }
            byte[] data = new byte[total];
            int offset = 0;
            for (byte[] piece : pieces) {
                System.arraycopy(piece, 0, data, offset, piece.length);
                offset += piece.length;
            }
            return new RipDataResponse(ripId, sequence, status, status == RipDataStatus.OK ? data : null);
        }
    }

    private static void putUuid(ByteBuffer buffer, UUID id) {
        buffer.putLong(id.getMostSignificantBits());
        buffer.putLong(id.getLeastSignificantBits());
    }

    private static UUID getUuid(ByteBuffer buffer) {
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    private static byte status(RipDataStatus status) {
        return switch (status) {
            case OK -> 0;
            case NOT_FOUND -> 1;
            case ERROR -> 2;
        };
    }

    private static RipDataStatus status(byte status) {
        return switch (status) {
            case 0 -> RipDataStatus.OK;
            case 1 -> RipDataStatus.NOT_FOUND;
            case 2 -> RipDataStatus.ERROR;
            default -> throw new IllegalArgumentException("Unknown rip data status " + status);
        };
    }
}
