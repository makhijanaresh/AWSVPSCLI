package com.vps.cli.dto;

public class InstanceInfo {

	private String username;
	private String password;
	private String java;
	private TomcatInfo tomcat;
	private MysqlInfo mysql;
	public String getUsername() {
		return username;
	}
	public void setUsername(String username) {
		this.username = username;
	}
	public String getPassword() {
		return password;
	}
	public void setPassword(String password) {
		this.password = password;
	}
	public String getJava() {
		return java;
	}
	public void setJava(String java) {
		this.java = java;
	}
	public TomcatInfo getTomcat() {
		return tomcat;
	}
	public void setTomcat(TomcatInfo tomcat) {
		this.tomcat = tomcat;
	}
	public MysqlInfo getMysql() {
		return mysql;
	}
	public void setMysql(MysqlInfo mysql) {
		this.mysql = mysql;
	}
	
	
	
	
}
