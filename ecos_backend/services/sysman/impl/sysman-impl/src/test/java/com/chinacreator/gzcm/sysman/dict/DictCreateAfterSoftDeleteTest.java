package com.chinacreator.gzcm.sysman.dict;

import com.chinacreator.gzcm.sysman.dao.DictDao;
import com.chinacreator.gzcm.sysman.dict.entity.SysDict;
import com.chinacreator.gzcm.sysman.dict.service.impl.DictService;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * D79 回归：字典项删除是逻辑删除（status→inactive），唯一索引不含 status。
 *
 * <p>缺陷形态：创建前只判「active 是否已存在」，同键 inactive 行会让 INSERT 撞
 * {@code sys_dict_dict_type_dict_code_key} ⇒ 500「创建失败」，且用户无法重建同名项。
 * 修法：先查任意状态，inactive 行就地复活。</p>
 *
 * <p>两条用例成对：一条证「inactive ⇒ 走 revive 不 INSERT」，一条证「真不存在 ⇒ 仍走 INSERT」，
 * 防止把逻辑改成「永远不插入」也判绿。</p>
 */
class DictCreateAfterSoftDeleteTest {

    private static SysDict dict(String code, String status) {
        SysDict d = new SysDict();
        d.setDictType("t_probe");
        d.setDictCode(code);
        d.setDictLabel("探针");
        d.setStatus(status);
        d.setSortOrder(1);
        return d;
    }

    private DictService serviceWith(DictDao dao) {
        // 构造器会 refreshCache()：listAllActive 必须给空列表，否则 NPE
        when(dao.listAllActive()).thenReturn(Collections.emptyList());
        return new DictService(dao);
    }

    @Test
    void 同键存在inactive行时复活而不INSERT() {
        DictDao dao = mock(DictDao.class);
        SysDict dead = dict("item_a", "inactive");
        when(dao.findAnyStatus("t_probe", "item_a")).thenReturn(dead);
        when(dao.listByType("t_probe")).thenReturn(List.of());
        doAnswer(inv -> {
            // revive 之后 getDictItem 走缓存回源：这里回一条 active 即可
            when(dao.listByType("t_probe")).thenReturn(List.of(dict("item_a", "active")));
            return null;
        }).when(dao).revive(any(SysDict.class), anyString(), anyString());

        DictService service = serviceWith(dao);
        SysDict created = service.createDictItem(dict("item_a", "active"));

        verify(dao).revive(any(SysDict.class), eqType(), eqCode());
        verify(dao, never()).insert(any(SysDict.class));
        assertNotNull(created, "复活后应能读回该字典项");
        assertEquals("item_a", created.getDictCode());
    }

    @Test
    void 真不存在时仍走INSERT() {
        DictDao dao = mock(DictDao.class);
        when(dao.findAnyStatus("t_probe", "item_b")).thenReturn(null);
        when(dao.maxSortOrder("t_probe")).thenReturn(3);
        when(dao.listByType("t_probe")).thenReturn(List.of());
        doAnswer(inv -> {
            when(dao.listByType("t_probe")).thenReturn(List.of(dict("item_b", "active")));
            return null;
        }).when(dao).insert(any(SysDict.class));

        DictService service = serviceWith(dao);
        SysDict created = service.createDictItem(dict("item_b", "active"));

        verify(dao).insert(any(SysDict.class));
        verify(dao, never()).revive(any(SysDict.class), anyString(), anyString());
        assertNotNull(created);
    }

    private static String eqType() {
        return org.mockito.ArgumentMatchers.eq("t_probe");
    }

    private static String eqCode() {
        return org.mockito.ArgumentMatchers.eq("item_a");
    }
}
