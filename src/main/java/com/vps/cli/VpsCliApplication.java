package com.vps.cli;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class VpsCliApplication {
	

	public static void main(String[] args) {
	//	String path=args[0];
		String path="D:\\POCProjects\\VPSCli\\data.json";
		//SpringApplication.run(VpsCliApplication.class, args);
		Logger.getLogger("com.amazonaws.*").setLevel(Level.OFF);
	//	Logger.getLogger("com.amazonaws.auth.AWS4Signer").setLevel(Level.OFF);
		System.err.println("Hello World:"+path);
		InstanceCreator.createInstance(path);
	}

}
