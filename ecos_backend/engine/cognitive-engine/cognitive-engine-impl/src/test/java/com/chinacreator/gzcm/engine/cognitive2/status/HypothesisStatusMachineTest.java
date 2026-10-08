package com.chinacreator.gzcm.engine.cognitive2.status;

import com.chinacreator.gzcm.engine.cognitive.api.HypothesisStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** F05-05：假设状态四值可写 + 存量 VALID/INVALIDATED/ARCHIVED 只读别名（R-15 ①）。 */
class HypothesisStatusMachineTest {

    @Test
    void onlyFourCanonicalStatusesAreWritable() {
        assertEquals(4, HypothesisStatus.values().length, "终态必须恰 4 值");
        assertTrue(HypothesisStatus.canWrite("PROPOSED"));
        assertTrue(HypothesisStatus.canWrite("EVIDENCED"));
        assertTrue(HypothesisStatus.canWrite("BELIEVED"));
        assertTrue(HypothesisStatus.canWrite("REFUTED"));

        assertFalse(HypothesisStatus.canWrite("VALID"), "VALID 只读别名，禁写入");
        assertFalse(HypothesisStatus.canWrite("INVALIDATED"));
        assertFalse(HypothesisStatus.canWrite("ARCHIVED"));
        assertFalse(HypothesisStatus.canWrite(null));
        assertFalse(HypothesisStatus.canWrite("BOGUS"));
        assertFalse(HypothesisStatus.canWrite(""));

        assertTrue(HypothesisStatus.isLegacyAlias("VALID"));
        assertTrue(HypothesisStatus.isLegacyAlias("INVALIDATED"));
        assertTrue(HypothesisStatus.isLegacyAlias("ARCHIVED"));
        assertFalse(HypothesisStatus.isLegacyAlias("BELIEVED"));
    }

    @Test
    void legacyAliasesMapReadSideOnly() {
        assertEquals(HypothesisStatus.BELIEVED, HypothesisStatus.fromDbValue("VALID"));
        assertEquals(HypothesisStatus.REFUTED,  HypothesisStatus.fromDbValue("INVALIDATED"));
        assertEquals(HypothesisStatus.REFUTED,  HypothesisStatus.fromDbValue("ARCHIVED"));
        assertEquals(HypothesisStatus.PROPOSED, HypothesisStatus.fromDbValue("PROPOSED"));
        assertEquals(HypothesisStatus.REFUTED,  HypothesisStatus.fromDbValue(" REFUTED "), "大小写不敏感是可选行为，这里已带空格");
        assertEquals(null, HypothesisStatus.fromDbValue("BOGUS"));
    }

    @Test
    void nonCanonicalWriteIsRejected() {
        AssertWritable w = new AssertWritable();
        assertThrows(IllegalStateException.class, () -> w.write("VALID"));
        assertThrows(IllegalStateException.class, () -> w.write(""));
        w.write("BELIEVED");
    }

    static final class AssertWritable {
        void write(String status) {
            if (HypothesisStatus.fromDbValue(status) == null || !HypothesisStatus.canWrite(status)) {
                throw new IllegalStateException("status not writable: " + status);
            }
        }
    }
}
