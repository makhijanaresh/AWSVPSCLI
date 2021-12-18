package com.vps.cli;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest;
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest;
import com.amazonaws.services.ec2.model.CreateSecurityGroupResult;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceType;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.IpRange;
import com.amazonaws.services.ec2.model.RebootInstancesRequest;
import com.amazonaws.services.ec2.model.RebootInstancesResult;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.vps.cli.dto.InstanceInfo;
import com.vps.cli.dto.MysqlInfo;
import com.vps.cli.dto.TomcatInfo;

public class InstanceCreator {

	static AmazonEC2 ec2 = null;
	static {
		BasicAWSCredentials awsCreds = new BasicAWSCredentials("access_key",
				"secret_key");
		ec2 = AmazonEC2ClientBuilder.standard().withCredentials(new AWSStaticCredentialsProvider(awsCreds)).build();

	}

	public static void createInstance(String filePath) {
		ObjectMapper mapper = new ObjectMapper();

		try {
			Semaphore semaphore = new Semaphore(2);
			List<InstanceInfo> sample = mapper.readValue(new File(filePath), new TypeReference<List<InstanceInfo>>() {
			});

			for (InstanceInfo instance : sample) {
				MyThread m1 = new MyThread(semaphore, Thread.currentThread().getName(), instance);
				m1.start();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public static void test(InstanceInfo instanceInfo) throws Exception {

		System.out.println("-------------------Server creation started------------");
		// createKeyPair(instanceInfo.getUserName());
		String ubuntuImageId = "ami-0c1a7f89451184c8b";
		RunInstancesRequest request;
		request = new RunInstancesRequest();
		request.setImageId(ubuntuImageId);
		request.setInstanceType(InstanceType.T2Micro);
		request.setKeyName("key_pair_file_name_without_.pem_extension");
		request.setMinCount(1);
		request.setMaxCount(1);
		request.setMinCount(1);

		Collection<String> collection = Arrays.asList(createSecurityGroup(instanceInfo.getUsername()));
		request.setSecurityGroupIds(collection);

		RunInstancesResult result = ec2.runInstances(request);
		Thread.sleep(6000);
		String instanceId = result.getReservation().getInstances().get(0).getInstanceId();
		String publicIp = ec2
				.describeInstances(new DescribeInstancesRequest()
						.withInstanceIds(result.getReservation().getInstances().get(0).getInstanceId()))
				.getReservations().stream().map(Reservation::getInstances).flatMap(List::stream).findFirst()
				.map(Instance::getPublicIpAddress).orElse(null);
	

		try {
			System.out.println("-------------Server Created-------------- for user " + instanceInfo.getUsername());
			System.out.println("-------------Creating user ---------------" + instanceInfo.getUsername());
			createUser(ec2, instanceInfo, publicIp, instanceId);

			System.err.println("-------------User Created successfully--------" + instanceInfo.getUsername());
			System.out
					.println("--------------Installing Java------------------ for user " + instanceInfo.getUsername());
			installJava(publicIp, instanceInfo.getJava());
			System.out.println("-------Java Installed Successfully------------- for user" + instanceInfo.getUsername());
			System.out.println("--------Installing Tomcat----------------- for user " + instanceInfo.getUsername());
			installTomcat(ec2, instanceInfo.getTomcat(), publicIp, instanceInfo.getUsername());
			System.out
					.println("--------Tomcat Installed Successfully----------- for user " + instanceInfo.getUsername());
			System.out.println("------Installing Mysql----------------- for user:" + instanceInfo.getUsername());
			installMysql(ec2, instanceInfo.getMysql(), instanceInfo.getUsername(), instanceInfo.getPassword(),
					publicIp);
			System.out.println("----Mysql Installed Successfully----------- for user:" + instanceInfo.getUsername());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.err.println("===============Public ip:" + publicIp + " for user:" + instanceInfo.getUsername());
		System.out.println("------Thank you---------4");
		System.err.println("-------------------------------------------Server Ip:"+publicIp+" For user:"+instanceInfo.getUsername());

	}

	public static String createSecurityGroup(String userName) {
		CreateSecurityGroupRequest create_request = new CreateSecurityGroupRequest()
				.withGroupName(userName + "my-security-group").withDescription("My Security Group Description");

		String securityGroupId = null;
		CreateSecurityGroupResult securityResult = ec2.createSecurityGroup(create_request);
		securityGroupId = securityResult.getGroupId();
		List<IpPermission> ipPermissions = new ArrayList<>();
		IpPermission ipPermission_22 = new IpPermission();
		IpRange ip_range_22 = new IpRange().withCidrIp("0.0.0.0/0");

		ipPermission_22 = new IpPermission().withIpProtocol("tcp").withToPort(Integer.parseInt("22"))
				.withFromPort(Integer.parseInt("22")).withIpv4Ranges(ip_range_22);
		ipPermissions.add(ipPermission_22);

		AuthorizeSecurityGroupIngressRequest auth_request = new AuthorizeSecurityGroupIngressRequest()
				.withGroupName(userName + "my-security-group").withIpPermissions(ipPermissions);

		ec2.authorizeSecurityGroupIngress(auth_request);
		return securityGroupId;
	}

	public static Session getJschSession(String serverIp) throws Exception {
		JSch jsch = new JSch();
		int portNumber = 22;
		String pemFilePath = "full_path_of_pem_file_with_.pem_extension";
		jsch.addIdentity(pemFilePath);
		System.err.println("serverIp:" + serverIp);
		Session session = null;
		boolean notConnected = true;
		while (notConnected) {
			try {
				session = jsch.getSession("ubuntu", serverIp, portNumber);
				Properties properties = new Properties();
				properties.put("StrictHostKeyChecking", "no");
				session.setConfig(properties);
				session.connect(25000);
				notConnected = false;
			} catch (Exception e) {
				Thread.sleep(3000);
				System.err.println("Trying to connect to session");
				// e.printStackTrace();

			}
		}
		return session;
	}

	public static void rebootInstane(AmazonEC2 ec2, String instanceId) throws InterruptedException {
		RebootInstancesRequest request = new RebootInstancesRequest().withInstanceIds(instanceId);
		RebootInstancesResult response = ec2.rebootInstances(request);
		System.err.println("Rebooting Instance wait for 5 seconds");
		Thread.sleep(5000);
	}

	public static void createUser(AmazonEC2 ec2, InstanceInfo instanceInfo, String serverIp, String instanceId)
			throws Exception {
		Session session = getJschSession(serverIp);
		ChannelExec channelExec = (ChannelExec) session.openChannel("exec");

		channelExec.setCommand("sudo useradd -m -p $(openssl passwd  -1 " + instanceInfo.getPassword()
				+ ") -s /bin/bash  -G sudo " + instanceInfo.getUsername() + ";"
				+ "sudo sed -i 's/PasswordAuthentication no/PasswordAuthentication Yes/' /etc/ssh/sshd_config;sudo apt-get update -y;sudo apt-get install expect -y");
		channelExec.connect(5000);
		System.err.println("Closing Jsch Session Connection");
		channelExec.disconnect();
		session.disconnect();
		rebootInstane(ec2, instanceId);
	}

	public static void installJava(String serverIp, String version) {
		try {
			Thread.sleep(5000);
			String command = null;
			if (version.equals("17")) {
				command = "sudo apt-get update -y;sudo apt-get install openjdk-17-jdk -y;";
				//command += "sudo update-java-alternatives -s $(sudo update-java-alternatives -l | grep 17 | cut -d ' ' -f1) || echo '.'";

			} else if (version.equals("16")) {
				command = "sudo apt-get update -y;sudo apt-get install openjdk-16-jdk -y;";
				//command += "sudo update-java-alternatives -s $(sudo update-java-alternatives -l | grep 16 | cut -d ' ' -f1) || echo '.'";
			}
			Session session = getJschSession(serverIp);
			ChannelExec channelExec = (ChannelExec) session.openChannel("exec");
			channelExec.setCommand(command);

			InputStream in = channelExec.getInputStream();
			channelExec.connect(5000);
			byte[] tmp = new byte[1024];

			while (true) {

				while (in.available() > 0) {
					int i = in.read(tmp, 0, 1024);
					if (i < 0)
						break;
					//System.out.print(new String(tmp, 0, i));
				}

				if (channelExec.isClosed()) {
					if (in.available() > 0)
						continue;
					System.out.println("exit-status: " + channelExec.getExitStatus());
					if (channelExec.getExitStatus() == 0) {
						// installationStatus = "Java Installed Successfully";
					} else {
						// installationStatus = "Java Installation failed";
						System.err.println("Java version:"+version+" instllation failer for server:"+serverIp);
					}
					break;
				}
				/*
				 * try { Thread.sleep(1000); } catch (Exception ee) { }
				 */
			}
			System.err.println("Closing Jsch Session Connection");
			channelExec.disconnect();
			session.disconnect();

		} catch (Exception e) {
			e.printStackTrace();
			// installationStatus = "Some error occured while installing java";
		}
	}

	public static void installTomcat(AmazonEC2 ec2, TomcatInfo tomcatInfo, String serverIp, String username) {
		String tarPath = null;

		if (tomcatInfo.getVersion().equals(("9"))) {
			tarPath = "https://dlcdn.apache.org/tomcat/tomcat-9/v9.0.56/bin/apache-tomcat-9.0.56.tar.gz";
		}
		if (tomcatInfo.getVersion().equals(("8"))) {
			tarPath = "https://dlcdn.apache.org/tomcat/tomcat-8/v8.5.73/bin/apache-tomcat-8.5.73.tar.gz";
		}
		if (tomcatInfo.getVersion().equals(("10"))) {
			tarPath = "https://dlcdn.apache.org/tomcat/tomcat-10/v10.0.14/bin/apache-tomcat-10.0.14.tar.gz";
		}

		downloadJarFile(serverIp, tomcatInfo, username, tarPath);
		modifySecurityGroup(ec2, username, tomcatInfo.getConnectorPort());

	}

	public static void modifySecurityGroup(AmazonEC2 ec2, String userName, String port) {
		AuthorizeSecurityGroupIngressRequest ingress = new AuthorizeSecurityGroupIngressRequest();
		ingress.withGroupName(userName + "my-security-group");
		IpPermission ipPermission = new IpPermission();
		List<IpPermission> ipPermissions = new ArrayList<>();
		IpRange ip_range = new IpRange().withCidrIp("0.0.0.0/0");

		ipPermission = new IpPermission().withIpProtocol("tcp").withToPort(Integer.parseInt(port))
				.withFromPort(Integer.parseInt(port)).withIpv4Ranges(ip_range);
		ipPermissions.add(ipPermission);
		ingress.withIpPermissions(ipPermissions);
		ec2.authorizeSecurityGroupIngress(ingress);

	}

	private static void downloadJarFile(String serverIp, TomcatInfo tomcatInfo, String username, String tarPath) {
		try {
			String rgx = "([^\\/]+$)";
			Matcher m = Pattern.compile(rgx).matcher(tarPath);
			if (m.find()) {
				String finalVersion = m.group(1);
				finalVersion = finalVersion.substring(0, finalVersion.length() - 7);
				tomcatInfo.setVersion(finalVersion);
			}
			String userName = username;
			Session session = getJschSession(serverIp);
			ChannelExec channelExec = (ChannelExec) session.openChannel("exec");
			String finalCommand = "cd /home/" + userName + "/;sudo wget " + tarPath + ";" + "sudo tar xvzf "
					+ tomcatInfo.getVersion() + ".tar.gz" + ";"
					+ "sudo sed -i 's/<Connector port=\"8080\"/<Connector port=\"" + tomcatInfo.getConnectorPort()
					+ "\"/' /home/" + userName + "/" + tomcatInfo.getVersion() + "/conf/server.xml;"
					+ "sudo sed -i 's/<Server port=\"8005\"/<Server port=\"" + tomcatInfo.getServerPort()
					+ "\"/' /home/" + userName + "/" + tomcatInfo.getVersion() + "/conf/server.xml;" + "sudo chown "
					+ userName + " -R  /home/" + userName + "/*";

			channelExec.setCommand(finalCommand);
			Thread.sleep(2000);
			InputStream in = channelExec.getInputStream();
			channelExec.connect(25000);
			byte[] tmp = new byte[1024];

			while (true) {

				
				  while (in.available() > 0) { int i = in.read(tmp, 0, 1024); if (i < 0) break;
				//  System.out.print(new String(tmp, 0, i));
				  }
				 

				if (channelExec.isClosed()) {
					if (in.available() > 0)
						continue;
					System.out.println("exit-status: " + channelExec.getExitStatus());
					if (channelExec.getExitStatus() == 0) {
						// installationStatus = "Tomcat Installed Successfully";
					} else {
						// installationStatus = "Tomcat Installation failed";
					}
					break;
				}
				/*
				 * try { Thread.sleep(1000); } catch (Exception ee) { }
				 */
			}
			Thread.sleep(2000);
			System.err.println("Closing Jsch Session Connection");
			channelExec.disconnect();
			session.disconnect();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void installMysql(AmazonEC2 ec2, MysqlInfo mysqlInfo, String username, String password, String ip) {
		modifySecurityGroup(ec2, username, mysqlInfo.getPort());
		String additionalUserName = mysqlInfo.getAdditionalUser();
		String additionalPassword = mysqlInfo.getAdditionalUserPassword();
		copyExpFile(ip, username, password);

		try {

			Session session = getJschSession(ip);
			ChannelExec channelExec = (ChannelExec) session.openChannel("exec");
			StringBuilder command = new StringBuilder("");
			command.append(" cd /home/" + username + "/;");
			command.append("sudo chmod 777 autoexpectfile.exp;");
			command.append(" sudo apt update -y;");
			command.append("sudo apt-get install expect -y;");
			command.append("sudo apt-get install mysql-server -y;");
			command.append("sleep 5;");
			command.append("expect autoexpectfile.exp;");
			command.append("sudo sed -i 's/root/" + mysqlInfo.getRootPassword() + "/' autoexpectfile.exp;");
			command.append("sleep 3;");
			command.append("sudo sed -i 's/127.0.0.1/0.0.0.0/' /etc/mysql/mysql.conf.d/mysqld.cnf;");
			command.append("sudo sed -i 's/3306/" + mysqlInfo.getPort() + "/' /etc/mysql/mysql.conf.d/mysqld.cnf;");
			command.append("sudo sed -i 's/# port/port/' /etc/mysql/mysql.conf.d/mysqld.cnf;");
			command.append("sudo service mysql restart;");
			command.append("sleep 3;");
			command.append("sudo mysql -e '");
			if (mysqlInfo.getAllowRemoteLogin()) {
				command.append("create user \"" + additionalUserName + "\"@\"%\" identified by \"" + additionalPassword
						+ "\"'");
			} else {
				command.append("create user \"" + additionalUserName + "\"@\"localhost\" identified by \""
						+ additionalPassword + "\"'");
			}
			System.out.println(command.toString());
			channelExec.setCommand(command.toString());
			InputStream in = channelExec.getInputStream();
			channelExec.connect(5000);
			byte[] tmp = new byte[1024];

			while (true) {

				while (in.available() > 0) {
					int i = in.read(tmp, 0, 1024);
					if (i < 0)
						break;
				//	System.out.print(new String(tmp, 0, i));
				}

				if (channelExec.isClosed()) {
					if (in.available() > 0)
						continue;
					System.out.println("exit-status: " + channelExec.getExitStatus());
					if (channelExec.getExitStatus() == 0) {
						// installationStatus = "Mysql Installed Successfully";
						System.out.println("-------Mysql installed successfully------");
						Thread.sleep(2000);
					} else {
						System.out.println("----------- Mysql installation failed------");
						Thread.sleep(2000);
						// installationStatus = "Mysql Installation failed";
					}
					break;
				}
			}
			System.err.println("Closing Jsch Session Connection");
			channelExec.disconnect();
			session.disconnect();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void copyExpFile(String serverIp, String userName, String password) {
		try {
			JSch jsch = new JSch();
			Session session = jsch.getSession(userName, serverIp);
			session.setPassword(password);
			Properties properties = new Properties();
			properties.put("StrictHostKeyChecking", "no");
			session.setConfig(properties);
			session.connect();
			Channel channel = session.openChannel("sftp");
			channel.connect();
			ChannelSftp sftpChannel = (ChannelSftp) channel;

			// sftpChannel.put("D:\\Imp2\\CloudEngineering\\Lecture19\\installyMysql.sh",
			// "/home/" + userName + "/");
			sftpChannel.put("path_of_auto_exp_file_with_extension",
					"/home/" + userName + "/");
			sftpChannel.exit();
			channel.disconnect();
			session.disconnect();
			do {
				Thread.sleep(1000);
			} while (channel.isEOF() == false);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

}

class MyThread extends Thread {
	Semaphore sem;
	String threadName;
	private InstanceInfo instanceInfo;

	public MyThread(Semaphore sem, String threadName, InstanceInfo instanceInfo) {
		super(threadName);
		this.sem = sem;
		this.threadName = threadName;
		this.instanceInfo = instanceInfo;
	}

	@Override
	public void run() {

		System.out.println("Starting " + threadName);
		try {
			// First, get a permit.
			System.out.println(threadName + " is waiting for a permit.");

			// acquiring the lock
			sem.acquire();

			System.out.println(threadName + " gets a permit.");

			InstanceCreator.test(instanceInfo);
		} catch (Exception exc) {
			System.out.println(exc);
		}

		// Release the permit.
		System.out.println(threadName + " releases the permit.");
		sem.release();
	}
}
