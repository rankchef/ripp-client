package mk.ripp.peer.seeder;

import mk.ripp.file.FileSplitter;
import mk.ripp.file.model.FileChunk;
import mk.ripp.peer.model.RipDataResponse;
import mk.ripp.peer.model.RipDataStatus;
import mk.ripp.store.SeedingRipStore;

import java.util.Optional;
import java.util.UUID;

public final class RipDataSource {
    private final SeedingRipStore rips;
    private final FileSplitter files;

    public RipDataSource(SeedingRipStore rips, FileSplitter files) {
        this.rips = rips;
        this.files = files;
    }

    public RipDataResponse read(UUID ripId, int sequence) {
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must be >= 0");
        }
        Optional<SeedingRipStore.SeededRip> rip = rips.find(ripId);
        if (rip.isEmpty()) {
            return new RipDataResponse(ripId, sequence, RipDataStatus.NOT_FOUND, null);
        }
        try {
            FileChunk chunk = files.readChunk(rip.get().file(), sequence, rip.get().chunkSize());
            return new RipDataResponse(ripId, sequence, RipDataStatus.OK, chunk.getData());
        } catch (IndexOutOfBoundsException e) {
            return new RipDataResponse(ripId, sequence, RipDataStatus.NOT_FOUND, null);
        } catch (Exception e) {
            return new RipDataResponse(ripId, sequence, RipDataStatus.ERROR, null);
        }
    }
}
