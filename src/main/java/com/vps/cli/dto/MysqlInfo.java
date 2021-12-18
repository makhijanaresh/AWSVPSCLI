package com.vps.cli.dto;

public class MysqlInfo {

	private String rootPassword;
	private String additionalUser;
	private String additionalUserPassword;
	private Boolean allowRemoteLogin;
	private String port;
	
	public String getPort() {
		return port;
	}
	public void setPort(String port) {
		this.port = port;
	}
	public String getRootPassword() {
		return rootPassword;
	}
	public void setRootPassword(String rootPassword) {
		this.rootPassword = rootPassword;
	}
	public String getAdditionalUser() {
		return additionalUser;
	}
	public void setAdditionalUser(String additionalUser) {
		this.additionalUser = additionalUser;
	}
	public String getAdditionalUserPassword() {
		return additionalUserPassword;
	}
	public void setAdditionalUserPassword(String additionalUserPassword) {
		this.additionalUserPassword = additionalUserPassword;
	}
	public Boolean getAllowRemoteLogin() {
		return allowRemoteLogin;
	}
	public void setAllowRemoteLogin(Boolean allowRemoteLogin) {
		this.allowRemoteLogin = allowRemoteLogin;
	}
	
	
}
