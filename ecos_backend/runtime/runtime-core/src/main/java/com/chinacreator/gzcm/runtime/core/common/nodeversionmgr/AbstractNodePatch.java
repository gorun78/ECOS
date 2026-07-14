package com.chinacreator.gzcm.runtime.core.common.nodeversionmgr;

import java.io.File;
import java.io.IOException;

/**
 * Abstract node patch handler
 * Provides basic operations for node patch files
 * 
 * @author CDRC Runtime Team
 */
public class AbstractNodePatch {
	
	private String fileName;
	private static boolean remote = false; // Default to local
	
	/**
	 * Constructor
	 * 
	 * @param fileName Patch file name
	 */
	public AbstractNodePatch(String fileName) {
		this.fileName = fileName;
	}
	
	/**
	 * Delete the patch file
	 * 
	 * @throws IOException If deletion fails
	 */
	public void delete() throws IOException {
		File file = new File(getLocalPath(fileName, null));
		if (file.exists()) {
			if (!file.delete()) {
				throw new IOException("Failed to delete patch file: " + fileName);
			}
		}
	}
	
	/**
	 * Get local path of the patch file
	 * 
	 * @param fileName Patch file name
	 * @param temp Temporary directory (can be null)
	 * @return Local file path
	 */
	public static String getLocalPath(String fileName, File temp) {
		// TODO: Implement actual path resolution logic
		// For now, return a placeholder path
		if (temp != null) {
			return new File(temp, fileName).getAbsolutePath();
		}
		// Default patch directory (should be configured)
		return System.getProperty("user.home") + File.separator + "patches" + File.separator + fileName;
	}
	
	/**
	 * Check if patch storage is remote
	 * 
	 * @return true if remote, false if local
	 */
	public static boolean isRemote() {
		return remote;
	}
	
	/**
	 * Set remote flag
	 * 
	 * @param remote true if remote, false if local
	 */
	public static void setRemote(boolean remote) {
		AbstractNodePatch.remote = remote;
	}
	
	/**
	 * Get file name
	 * 
	 * @return File name
	 */
	public String getFileName() {
		return fileName;
	}
}

