package com.chinacreator.gzcm.runtime.core.monitor.monitorobject.dao;

import java.util.List;

import com.chinacreator.gzcm.runtime.core.monitor.bean.MonitorObjectBean;
import com.chinacreator.gzcm.runtime.core.monitor.monitorobject.bean.MonitorObjectParams;
import com.chinacreator.gzcm.runtime.core.common.util.LegacyListInfo;

public interface IMonitorObjectDao {

	/**
	 * 闁哄被鍎撮妤冪箔閿曗偓閹酣寮堕垾鍙夘偨闁汇劌瀚ú鍐箳瑜嶉顔炬寬閳ュ啿鐏欓悶?
	 * @param condition 闁哄被鍎撮妤呭级閳ュ弶顐?	 * @param sortBy 闁圭儤甯掔花顓㈠级閳ュ弶顐?闁?-"鐎殿喒鍋撳鍓侇棎閵嗗啰绮堝ú顏咁€栭幖鏉戠箲鐢捇宕氬Δ瀣闁告熬绠戦崹顖炲及椤栨艾纾抽幖鏉戠箞閳ь剙鍊搁々?-name"閻炴稏鍔庨妵姘跺箰婢跺﹥鍊崇紒澶夊嵆濡鹃攱鎯旇箛銉х"name"閻炴稏鍔庨妵姘跺箰婢跺﹥鍊崇紒澶嬫緲瀹曞本鎯旇箛鏂跨瑩閹? 
	 * @return
	 * @throws Exception
	 */
	public List<MonitorObjectBean> find(MonitorObjectBean condition) throws Exception;
	
		/**
	 * 闁告帒妫濋妴澶愬级閳ュ弶顐介柡灞诲劥椤曟鎯勯幋鐐蹭粯閻庣數顢婇挅?
	 * @param offset 闁告帒妫濋妴澶婎嚕閳ь剚鎱ㄧ€ｂ晝绉寸紓?
	 * @param pageSize 婵絽绻橀妴澶愬及閸撗佷粵閻犱焦婢樼紞宥夊极?
	 * @param condition 闁哄被鍎撮妤呭级閳ュ弶顐?	 * @param sortBy 闁圭儤甯掔花顓㈠级閳ュ弶顐?闁?-"鐎殿喒鍋撳鍓侇棎閵嗗啰绮堝ú顏咁€栭幖鏉戠箲鐢捇宕氬Δ瀣闁告熬绠戦崹顖炲及椤栨艾纾抽幖鏉戠箞閳ь剙鍊搁々?-name"閻炴稏鍔庨妵姘跺箰婢跺﹥鍊崇紒澶夊嵆濡鹃攱鎯旇箛銉х"name"閻炴稏鍔庨妵姘跺箰婢跺﹥鍊崇紒澶嬫緲瀹曞本鎯旇箛鏂跨瑩閹? 
	 * @return LegacyListInfo 闁告帒妫濋妴澶嬬┍閳╁啩绱栭悗鐢殿攰閽?
	 * @throws Exception
	 */
	public LegacyListInfo findByPage(Integer offset, Integer pageSize, MonitorObjectBean condition) throws Exception;
	
		/**
	 * 婵烇綀顕ф慨鐐烘儎閹寸偛浠橀悗鐢殿攰閽?
	 * @param item 闁烩晜鍨剁敮鍓佲偓鐢殿攰閽栧嫮鈧數顢婇挅?
	 * @return 婵烇綀顕ф慨鐐电磼閹惧浜?true濞戞挾鍎ら崹姘跺礉閻曞倻绀塮alse濞戞挸鎼妵鎴犳嫻?
	 * @throws Exception
	 */
	public void add(MonitorObjectBean item) throws Exception;
	
		/**
	 * 闁告帞濞€濞呭酣鎯勯幋鐐蹭粯閻庣數顢婇挅?
	 * @param id 闁烩晜鍨剁敮鍓佲偓鐢殿攰閽栧嚘d
	 * @return 闁告帞濞€濞呭海绱掗幘瀵镐函,true濞戞挾鍎ら崹姘跺礉閻曞倻绀塮alse濞戞挸鎼妵鎴犳嫻?
	 * @throws Exception
	 */
	public boolean delete(String id) throws Exception;
	
		/**
	 * 濞ｅ浂鍠楅弫濂告儎閹寸偛浠橀悗鐢殿攰閽?
	 * @param item 鐎垫澘鎳嶉幈銊╁绩閻熼偊鍤犻悹?
	 * @return 濞ｅ浂鍠楅弫鑲╃磼閹惧浜?true濞戞挾鍎ら崹姘跺礉閻曞倻绀塮alse濞戞挸鎼妵鎴犳嫻?
	 * @throws Exception
	 */
	public boolean update(MonitorObjectBean item) throws Exception;
	
	public MonitorObjectBean findById(String monitorObjId)throws Exception;
	
	public void addMonitorObjectParams(List<MonitorObjectParams> list)throws Exception;
	
	/**
	 * 闁哄秷顫夊畵渚€骞愰崶銊у灱濡炪倗娅㈢槐婵嗩嚗濡も偓閸╁矂鎯勯幋鐐蹭粯閻庣數顢婇挅?闁烩晜鍨剁敮鍓佲偓鐢殿攰閽栧嫰宕犻崨顓熷創閺夆晝鍋炵敮鎾矗閸屾稒娈?	 * @param itemPath
	 * @return
	 * @throws Exception
	 */
	public List<MonitorObjectBean> getMonitorObjectsByItem(String itemPath)throws Exception;
	
	public void deleteMonitorObjParams(String monitorObjId)throws Exception;
	
	public List<MonitorObjectParams> findParamsByMonitorObjId(String monitorObjId)throws Exception;
	
	public List<MonitorObjectBean> getHavenHostOrDB(String plugin_name)throws Exception;
	
	public String getMonitorTargetsDetail(String plugin_name,String monitor_object_id) throws Exception;
	
	public void updateUsableStatusAndLastMonitorTime(MonitorObjectBean bean)throws Exception;
	
	public List<String> getObjectMonitorTargets(String obj_id,String plugin_name) throws Exception;
}

