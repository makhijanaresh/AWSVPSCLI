package com.vps.cli.dto;

public class TomcatInfo {

	
	private String connectorPort;
	private String serverPort;
	private String version;
	public String getVersion() {
		return version;
	}
	public void setVersion(String version) {
		this.version = version;
	}
	public String getConnectorPort() {
		return connectorPort;
	}
	public void setConnectorPort(String connectorPort) {
		this.connectorPort = connectorPort;
	}
	public String getServerPort() {
		return serverPort;
	}
	public void setServerPort(String serverPort) {
		this.serverPort = serverPort;
	}
	
	
}
