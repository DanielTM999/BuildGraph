package dtm.builder;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UserArgsPerformanceTest {
    @Test void acceptsAutomaticJobsAndMemoryAndCacheOptions() {
        var args = new UserArgs(new String[]{"build", "-j", "auto", "--memory-limit", "8G", "--memory-per-job", "768MiB",
                "--cache-dir", "somewhere", "--cache-size", "2G", "--profile-build", "--no-cache", "--memory-reserve", "512M"});
        assertFalse(args.isInvalidCommand()); assertEquals(0, args.getJobs());
        assertEquals(8L << 30, args.getBuildOptions().memoryLimit());
        assertEquals(768L << 20, args.getBuildOptions().memoryPerJob());
        assertEquals(2L << 30, args.getBuildOptions().cacheSize());
        assertEquals(512L << 20, args.getBuildOptions().memoryReserve());
        assertTrue(args.getBuildOptions().profile()); assertFalse(args.getBuildOptions().sharedCache());
        assertEquals(12, new UserArgs(new String[]{"build", "-j", "12"}).getJobs());
    }
    @Test void rejectsInvalidOrOverflowedSizesAndJobCountsWithoutCrashing() {
        for (String value : new String[]{"0", "-1", "xyz", "9999999999999999999999999G", "4.2G"})
            assertTrue(new UserArgs(new String[]{"build", "--memory-limit", value}).isInvalidCommand(), value);
        for (String value : new String[]{"0", "-1", "xyz", "9999999999999999999999999"})
            assertTrue(new UserArgs(new String[]{"build", "-j", value}).isInvalidCommand(), value);
        assertTrue(new UserArgs(new String[]{"build", "--memory-per-job"}).isInvalidCommand());
    }
}
