package com.chinacreator.gzcm.runtime.core.monitor.bean;

import java.io.Serializable;
import java.util.Calendar;

public class StrategyTimeBean implements Serializable{
	
	public final static int INTERVAL = 1;
	// 婢?
	public final static int DAILY = 2;
	// 閸?
	public final static int WEEKLY = 3;
	// 閺?
	public final static int MONTHLY = 4;
	
	public int getSchedulerType() {
		return schedulerType;
	}

	public void setSchedulerType(int schedulerType) {
		this.schedulerType = schedulerType;
	}

	public int getIntervalSeconds() {
		return intervalSeconds;
	}

	public void setIntervalSeconds(int intervalSeconds) {
		this.intervalSeconds = intervalSeconds;
	}

	public int getIntervalMinutes() {
		return intervalMinutes;
	}

	public void setIntervalMinutes(int intervalMinutes) {
		this.intervalMinutes = intervalMinutes;
	}

	public int getDayOfMonth() {
		return dayOfMonth;
	}

	public void setDayOfMonth(int dayOfMonth) {
		this.dayOfMonth = dayOfMonth;
	}

	public int getWeekDay() {
		return weekDay;
	}

	public void setWeekDay(int weekDay) {
		this.weekDay = weekDay;
	}

	public int getMinutes() {
		return minutes;
	}

	public void setMinutes(int minutes) {
		this.minutes = minutes;
	}

	public int getHour() {
		return hour;
	}

	public void setHour(int hour) {
		this.hour = hour;
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private int schedulerType = 1;
	/**
	 * 鐠嬪啫瀹崇猾璇茬€锋稉鐚寸窗閺冨爼妫块梻鎾閿涘瞼顫?
	 */
	private int intervalSeconds = 0;
	/**
	 * 鐠嬪啫瀹崇猾璇茬€锋稉鐚寸窗閺冨爼妫块梻鎾閿涘苯鍨?
	 */
	private int intervalMinutes = 60;
	/**
	 * 閺冦儲婀?
	 */
	private int dayOfMonth = 1;

	/**
	 * 閺勭喐婀?
	 */
	private int weekDay = 1;
	/**
	 * 閸掑棝鎸?
	 */
	private int minutes = 0;
	/**
	 * 鐏忓繑妞?
	 */
	private int hour = 12;
	
	
	public StrategyTimeBean() {
	}

	/**
	 * 閼惧嘲褰囨稉瀣╃濞嗏剝澧界悰灞炬闂傝揪绱欏В顐ゎ潡閿?
	 * @return
	 */
	public long getNextExecutionTime() {
		switch (schedulerType) {
		case INTERVAL:
			return getNextIntervalExecutionTime();
		case DAILY:
			return getNextDailyExecutionTime();
		case WEEKLY:
			return getNextWeeklyExecutionTime();
		case MONTHLY:
			return getNextMonthlyExecutionTime();
		default:
			break;
		}
		return 0;
	}

	private long getNextIntervalExecutionTime() {
		return intervalSeconds * 1000 + intervalMinutes * 1000 * 60;
	}

	private long getNextMonthlyExecutionTime() {
		Calendar calendar = Calendar.getInstance();

		long nowMillis = calendar.getTimeInMillis();
		int amHour = hour;
		if (amHour > 12) {
			amHour = amHour - 12;
			calendar.set(Calendar.AM_PM, Calendar.PM);
		} else {
			calendar.set(Calendar.AM_PM, Calendar.AM);
		}
		calendar.set(Calendar.HOUR, amHour);
		calendar.set(Calendar.MINUTE, minutes);
		calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
		if (calendar.getTimeInMillis() <= nowMillis) {
			calendar.add(Calendar.MONTH, 1);
		}
		return calendar.getTimeInMillis() - nowMillis;
	}

	private long getNextWeeklyExecutionTime() {
		Calendar calendar = Calendar.getInstance();

		long nowMillis = calendar.getTimeInMillis();
		int amHour = hour;
		if (amHour > 12) {
			amHour = amHour - 12;
			calendar.set(Calendar.AM_PM, Calendar.PM);
		} else {
			calendar.set(Calendar.AM_PM, Calendar.AM);
		}
		calendar.set(Calendar.HOUR, amHour);
		calendar.set(Calendar.MINUTE, minutes);
		calendar.set(Calendar.DAY_OF_WEEK, weekDay + 1);
		if (calendar.getTimeInMillis() <= nowMillis) {
			calendar.add(Calendar.WEEK_OF_YEAR, 1);
		}
		return calendar.getTimeInMillis() - nowMillis;
	}

	private long getNextDailyExecutionTime() {
		Calendar calendar = Calendar.getInstance();

		long nowMillis = calendar.getTimeInMillis();
		int amHour = hour;
		if (amHour > 12) {
			amHour = amHour - 12;
			calendar.set(Calendar.AM_PM, Calendar.PM);
		} else {
			calendar.set(Calendar.AM_PM, Calendar.AM);
		}
		calendar.set(Calendar.HOUR, amHour);
		calendar.set(Calendar.MINUTE, minutes);
		if (calendar.getTimeInMillis() <= nowMillis) {
			calendar.add(Calendar.DAY_OF_MONTH, 1);// 瑜版挸澧犻弮鍫曟？閸旂姳绔存径?
		}
		// 婵″倹鐏夎ぐ鎾冲閻ㄥ嫭妞傞梻鏉戠毈娴滃氦顔曠純顔兼儙閸斻劎娈戦弮鍫曟？閿涘苯鍨弮鍫曟？缁涘绶熼梹鍨娑撹桨琚遍懓鍛瀹割噯绱濋崣宥勭閸掓瑤璐熺粭顑跨癌婢垛晞顔曠€规氨娈戦弮鍫曟？娑撳骸缍嬮崜宥嗘闂傜繝绠ｅ?
		return calendar.getTimeInMillis() - nowMillis;
	}

}
