package com.chinacreator.gzcm.runtime.core.monitor.server.warn.dao;

import java.util.List;

import com.chinacreator.gzcm.runtime.core.monitor.warn.bean.MonitorWarnContact;
import com.chinacreator.gzcm.runtime.core.monitor.warn.common.bean.WarnTypeBean;
import com.chinacreator.gzcm.runtime.core.common.util.LegacyListInfo;

/**
 * com.chinacreator.warn.server.tddxmonitorwarncontact.dao.
 * IMonitorWarnContactDao
 * <p>
 * Copyright: Chinacreator (c) 2013-01-14
 * </p>
 * <p>
 * Company: 婵犫偓閺嵮冪缂佸鍨甸崹杈ㄧ┍閳╁啩绱栭柟鍨涘亾闁哄牜鍨甸崑鍌涚閼恒儲绠掗梻鍕姇閸欐洟宕?
 * </p>
 * 
 */
public interface IMonitorWarnContactDao {

	/**
	 * 闁哄被鍎撮妤冪箔閿曗偓閹酣寮堕垾鍙夘偨闁汇劌瀚伴。鈺冩媰閿曚椒绮撶紒顖濐唺濮瑰姊块崱妤佸€?	 * 
	 * @param condition
	 *            闁哄被鍎撮妤呭级閳ュ弶顐?	 * @return
	 * @throws Exception
	 * @generated
	 */
	public List<MonitorWarnContact> find(MonitorWarnContact condition)
			throws Exception;

	public List<WarnTypeBean> findWarnTypes(String dbname) throws Exception;
	/**
	 * 闁告帒妫濋妴澶愬级閳ュ弶顐介柡灞诲劥椤曟锛愰崟顕呭妳闁艰鲸姊婚柈瀛樼?
	 * 
	 * @param offset
	 *            闁告帒妫濋妴澶婎嚕閳ь剚鎱ㄧ€ｂ晝绉寸紓?
	 * @param pageSize
	 *            婵絽绻橀妴澶愬及閸撗佷粵閻犱焦婢樼紞宥夊极?
	 * @param condition
	 *            condition 闁哄被鍎撮妤呭级閳ュ弶顐?	 * @return LegacyListInfo 闁告帒妫濋妴澶嬬┍閳╁啩绱栭悗鐢殿攰閽?
	 * @throws Exception
	 * @generated
	 */
	public LegacyListInfo findByPage(Integer offset, Integer pageSize,
			MonitorWarnContact condition) throws Exception;

	/**
	 * 婵烇綀顕ф慨鐐达紣閸曨噮鍔呴柤杈ㄦ⒒闁瓨绂?
	 * 
	 * @param item
	 *            濡澘瀚鐔兼嚂閺冨倿鍏囧ù婊冩惈椤曨喚鎸?
	 * @return 婵烇綀顕ф慨鐐电磼閹惧浜?true濞戞挾鍎ら崹姘跺礉閻曞倻绀塮alse濞戞挸鎼妵鎴犳嫻?
	 * @throws Exception
	 * @generated
	 */
	public boolean add(MonitorWarnContact item) throws Exception;

	/**
	 * 闁告帞濞€濞呭孩锛愰崟顕呭妳闁艰鲸姊婚柈瀛樼?
	 * 
	 * @param item
	 *            濡澘瀚鐔兼嚂閺冨倿鍏囧ù婊冩惈閻ㄦ繄鎲楅崨顒€鐦滈梺娆惧枛閸炲鈧?
	 * @return 闁告帞濞€濞呭海绱掗幘瀵镐函,true濞戞挾鍎ら崹姘跺礉閻曞倻绀塮alse濞戞挸鎼妵鎴犳嫻?
	 * @throws Exception
	 * @generated
	 */
	public boolean delete(MonitorWarnContact item) throws Exception;

	/**
	 * 濞ｅ浂鍠楅弫鍏硷紣閸曨噮鍔呴柤杈ㄦ⒒闁瓨绂?
	 * 
	 * @param item
	 *            鐎垫澘鎳嶉幈銊╁绩瑜版帩鏆曢悹鈧敃浣风矒缂侇垵顔婂Ч澶屸偓鐢殿攰閽?
	 * @return 濞ｅ浂鍠楅弫鑲╃磼閹惧浜?true濞戞挾鍎ら崹姘跺礉閻曞倻绀塮alse濞戞挸鎼妵鎴犳嫻?
	 * @throws Exception
	 * @generated
	 */
	public boolean update(MonitorWarnContact item) throws Exception;
	
	public void insertWarnTypes(List<WarnTypeBean> list, String dbname) throws Exception;

	public void updateWarnTypes(List<WarnTypeBean> list, String dbname) throws Exception;
}

