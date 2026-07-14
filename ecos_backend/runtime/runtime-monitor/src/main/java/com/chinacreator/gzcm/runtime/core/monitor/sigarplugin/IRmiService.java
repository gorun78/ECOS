package com.chinacreator.gzcm.runtime.core.monitor.sigarplugin;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;

public interface IRmiService extends Remote{

	public Map<String, String> statHostInfo() throws RemoteException,Exception;

	public Map<String, String> statCpuUserInfo() throws RemoteException;

	public Map<String, String> statDiskInfo(String childnew) throws RemoteException;

	public List<String> getAllDiskNames() throws RemoteException, Exception;
}
