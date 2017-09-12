package domain;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.UnknownHostException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

import org.apache.commons.io.FileUtils;

import util.ConverterUtil;

public class CDNLabConfig {
	private static String websiteURL = "";
	private static String workingDir = "";
	private static String connectionString = "";
	private static String dbUsername = "";
	private static String dbPassword = "";
	private static String routerSet = "";
	private static Map<String, Resource> resourcesMap = null;
	private static Map<String, List<AccessItem>> clientAccessMap = new HashMap<>();
	//private static String clientsDefinition = "";
	private static List<Client> clientsList=new LinkedList<>();
	private static String clientsConnections = "";
	private static List<Server> surrogateServersList = new LinkedList<>();
	private static List<String> serversConnections = new LinkedList<>();
	private static CDNLabConfig cdnLabConfig;
	private static Long lastEpochTimeUsed = new Long(0);
	private static IPRegionSet ipRegionSet; 

	public static CDNLabConfig getInstance() {
		if (cdnLabConfig == null)
			cdnLabConfig = new CDNLabConfig();
		return cdnLabConfig;
	}

	public String getWorkingDir() {
		return workingDir;
	}

	public void setWorkingDir(String workingDir) {
		this.workingDir = workingDir;
	}

	public String getConnectionString() {
		return connectionString;
	}

	public void setConnectionString(String connectionString) {
		this.connectionString = connectionString;
	}

	public static boolean testDBConnection(String connectionString,
			String username, String password) {
		try {
			Class.forName("com.mysql.jdbc.Driver");
			System.out.println("MySQL JDBC Driver Registered!");
			Connection connection = DriverManager.getConnection(
					connectionString, username, password);
			connection.close();
			return true;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	public static void generateContents(String logFilePath, String WebsiteURL_)
			throws NumberFormatException, ParseException,
			ClassNotFoundException, SQLException, IOException {
		websiteURL = WebsiteURL_;
		//
		BufferedReader br = new BufferedReader(new FileReader(logFilePath));
		String accessLine;
		Long minEpochTime = new Long(0);
		Long maxEpochTime = new Long(0);
		List<AccessItem> accessList = new LinkedList<>();
		resourcesMap = new HashMap<>();
		clientAccessMap = new HashMap<>();
		Connection connection = getConnection();
		
		//load regions to memory
		String sql = "SELECT startRange,endRange,CountryCode FROM ip2location;";
		PreparedStatement statement = connection.prepareStatement(sql);
		ResultSet resultSet = statement.executeQuery();
		List<IPRegion> ip_regions=new ArrayList<IPRegion>();
		while(resultSet.next()){
			ip_regions.add(
					new IPRegion(
							resultSet.getLong("startRange"), 
							resultSet.getLong("endRange"), 
							resultSet.getString("CountryCode")
							)
					);
		}
		
		ipRegionSet=new IPRegionSet(ip_regions);
		
		while ((accessLine = br.readLine()) != null) {
			try {
				AccessItem accessItem = new AccessItem();
				StringTokenizer tokenizer = new StringTokenizer(accessLine, "	");
				accessItem.setIP(tokenizer.nextToken());
				accessItem
						.setAccessTime(Long.valueOf(ConverterUtil
								.dateToEpochTime(tokenizer.nextToken().replaceAll("\"", "")
										.substring(1, 20))));
				// break the request parts
				StringTokenizer tokenizer_request = new StringTokenizer(
						tokenizer.nextToken(), " ");
				accessItem.setAccessType(tokenizer_request.nextToken()
						.substring(1));
				accessItem.setResourceName(tokenizer_request.nextToken());
				accessItem.setProtocol(tokenizer_request.nextToken());
				accessItem.setResult(tokenizer_request.nextToken());
				accessItem.setSize(tokenizer_request.nextToken());
				// start access time on zero
				if (minEpochTime == 0
						|| minEpochTime >= accessItem.getAccessTime()) {
					minEpochTime = accessItem.getAccessTime();
				}
				if (maxEpochTime < accessItem.getAccessTime()) {
					maxEpochTime = accessItem.getAccessTime();
				}
				//

				// detect clients region
				//String sql = "SELECT CountryCode FROM clients_geolocations WHERE IP = ? ";
				/*sql = "SELECT CountryCode FROM ip2location WHERE startRange <= ? and endRange >= ?";
				statement = connection.prepareStatement(sql);
				statement.setLong(1, ConverterUtil.ipToLong(accessItem.getIP()));
				statement.setLong(2, ConverterUtil.ipToLong(accessItem.getIP()));
				resultSet = statement.executeQuery();
				if (resultSet.next()) {
					accessItem.setClientName("clients_"
							+ resultSet.getString(1));
				} else
					continue;*/
				String countryCode=ipRegionSet.findCountryCode(ConverterUtil.ipToLong(accessItem.getIP()));
				if(countryCode.equals(""))
					continue;
				else
					accessItem.setClientName("clients_"	+countryCode);

				//
				accessList.add(accessItem);
				resourcesMap.put(accessItem.getResourceName(), new Resource(
						accessItem.getResourceName(), accessItem.getSize()));
				if (clientAccessMap.get(accessItem.getClientName()) == null) {
					clientAccessMap.put(accessItem.getClientName(),
							new ArrayList<AccessItem>());
				}
				clientAccessMap.get(accessItem.getClientName()).add(accessItem);
				System.out.println(accessItem.getClientName() + "->"
						+ accessItem.getResourceName());
			} catch (NoSuchElementException e) {
				System.err.println("ERR");
				continue;
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				throw e;
			}
		}

		// ----------------------------------------------- create output folders
		// and files
		String websiteName = WebsiteURL_.replace(".", "_");
		File file = new File(workingDir + "\\sites");
		if (!file.exists())
			file.mkdir();
		
		file = new File(workingDir + "\\sites\\" + websiteName);
		if (!file.exists())
			file.mkdir();
		//
		File site_def_output = new File(workingDir + "\\sites\\" + websiteName
				+ "\\site_definition.pagedef");
		if (!site_def_output.exists())
			site_def_output.createNewFile();
		PrintWriter writer_site_def = new PrintWriter(new BufferedWriter(
				new FileWriter(site_def_output)));
		// write out the resources

		writer_site_def.println("[HTML]");
		if (resourcesMap.get("/") != null) {
			Resource resource = resourcesMap.get("/");
			writer_site_def.println(resource.getResourceName()
					+ ";blank.pagedef;" + resource.getSize());
			resourcesMap.remove("/");
		}
		for (Resource resource : resourcesMap.values()) {
			writer_site_def.println(resource.getResourceName()
					.replace("[", "_").replace("]", "_").replace("%", "")
					.replace(";", "")
					+ ";blank.pagedef;" + resource.getSize());
			System.out.println("resource -> " + resource.getResourceName());
		}

		site_def_output = new File(workingDir + "\\sites\\" + websiteName
				+ "\\" + "blank.pagedef");
		if (!site_def_output.exists())
			site_def_output.createNewFile();
		writer_site_def.close();

		// -----------------------------------------------------------------
		PrintWriter writer_client_browse;
		file = new File(workingDir + "\\sites\\browse");
		if (!file.exists())
			file.mkdir();
		for (String client : clientAccessMap.keySet()) {
			File client_browse_output = new File(workingDir + "\\sites\\browse"
					+ "\\browse_" + client + ".script");
			if (!client_browse_output.exists())
				client_browse_output.createNewFile();
			writer_client_browse = new PrintWriter(new BufferedWriter(
					new FileWriter(client_browse_output)));
			// write out the resources
			List<AccessItem> accessItems = clientAccessMap.get(client);
			Collections.sort(accessItems);
			lastEpochTimeUsed = new Long(0);
			for (AccessItem access : accessItems) {
				// System.out.println("---- time: "+access.getAccessTime());
				String browse_line = epochTimeToSimTime(minEpochTime,
						maxEpochTime, Long.valueOf(access.getAccessTime()))
						+ ";"
						+ WebsiteURL_
						+ access.getResourceName().replace("[", "_")
								.replace("]", "_").replace("%", "")
								.replace(";", "");
				writer_client_browse.println(browse_line);
				System.out.println(browse_line);
			}
			writer_client_browse.close();
		}
		connection.close();
	}

	public static void importClients(String clientsFilePath)
			throws SQLException, ClassNotFoundException, IOException {
		Connection connection = null;
		setClientsConnections("");
		// create output folders and files
		// File file = new File(workingDir + "\\output");
		// if (!file.exists())
		// file.mkdir();
		//
		connection = getConnection();
		// read list of clients
		BufferedReader br = new BufferedReader(new FileReader(clientsFilePath));
		String clientLine;
		List<Client> clientList = new ArrayList<>();
		Map<String, String> country_cliDef = new HashMap<>();
		while ((clientLine = br.readLine()) != null) {
			try {
				StringTokenizer tokenizer = new StringTokenizer(clientLine, ",");
				Client tmpClient = new Client();
				tmpClient.setIP_address_on_web(tokenizer.nextToken());
				tmpClient.setLatitude(tokenizer.nextToken());
				tmpClient.setLongitude(tokenizer.nextToken());
				System.out.println(tmpClient.getIP_address_on_web());
				clientList.add(tmpClient);
			} catch (NoSuchElementException e) {
				continue;
			}
		}
		br.close();
		// while ((clientIP = br.readLine()) != null) {
		String sql = "delete from clients_geolocations";
		PreparedStatement statement = connection.prepareStatement(sql);
		statement.executeUpdate();
		for (Client client : clientList) {
			System.out.println("CURRENT CLIENT _________________ "
					+ client.getIP_address_on_web());

			// fetch the closest country to this client
			/*sql = "SELECT *, " + "SQRT(POW(latitude-" + client.getLatitude()
					+ ",2)+POW(longitude-" + client.getLongitude() + ",2)) "
					+ "as distance FROM countryas " + "WHERE active!='false' "
					+ "ORDER BY distance asc ";
			System.out.println(sql);
			sql = "SELECT "
					+ "CountryCode, "
					+ "SQRT(POW(latitude-"+ client.getLatitude() + ",2)+POW(longitude-"+ client.getLongitude() + ",2)) as distance "
					+ "FROM ip2location ORDER BY distance asc LIMIT 1";
			statement = connection.prepareStatement(sql);
			// statement.setString(1, "%" + clinetIP_toSearch + "%");

			// ------
			ResultSet resultSet = statement.executeQuery();
			if (resultSet.next()) {*/
				//String countryCode = resultSet.getString("countryCode");
			String countryCode = ipRegionSet.findCountryCode(ConverterUtil.ipToLong(client.getIP_address_on_web()));
			if(!countryCode.equals("")){
				// insert clients and their corresponding locations into db;
				sql = "INSERT INTO clients_geolocations values (?,?,?,?)";
				statement = connection.prepareStatement(sql);
				statement.setString(1, client.getIP_address_on_web());
				statement.setString(2, client.getLatitude());
				statement.setString(3, client.getLongitude());
				statement.setString(4, countryCode);
				statement.executeUpdate();
				// if this country has already a client

				if (country_cliDef.get(countryCode) != null)
					continue;

				client.setName("clients_" + countryCode);
				String cli_def = defineClient(client);
				System.out.println("==============================");
				System.out.println(cli_def);
				country_cliDef.put(countryCode, cli_def);

				//setClientsDefinition(getClientsDefinition() + (cli_def + "\n"));
				client.setDefinition(cli_def);
				clientsList.add(client);
				//
				String cli_connection = connectClientToCountryRouter(client,
						countryCode);
				System.out.println(cli_connection);
				setClientsConnections(getClientsConnections()
						+ (cli_connection + "\n"));
			}

		}
	}

	public static void createServers(String originRouterToConnect,
			List<String> surrogateRouterToConnect)
			throws ClassNotFoundException, SQLException {

		//serversDefinitions.add(defineServer(originRouterToConnect, true));
		serversConnections.add(connectServerToRouter(originRouterToConnect,
				true));
		for (String sRouter : surrogateRouterToConnect) {
			Server server = new Server();
			server.setName("surrogateServer_"+sRouter.substring(9, 11));
			server.setDefinition(defineServer(sRouter, false));
			// get location info
			Connection connection = getConnection();
			//String sql = "SELECT * FROM countryas WHERE countryCode = ? ";
			String sql="SELECT "
						+"* ,"
						+ "SQRT("
						+ "POW(latitude-(SELECT AVG(Latitude) FROM ip2location WHERE CountryCode=?),2)+"
						+ "POW(longitude-(SELECT AVG(Longitude) FROM ip2location WHERE CountryCode=?),2)) as distance "
						+ "FROM ip2location WHERE CountryCode=? ORDER BY distance asc LIMIT 1";
	
			PreparedStatement statement = connection.prepareStatement(sql);
			statement.setString(1, sRouter.substring(9, 11));
			statement.setString(2, sRouter.substring(9, 11));
			statement.setString(3, sRouter.substring(9, 11));
			
			ResultSet resultSet = statement.executeQuery();
			if (resultSet.next()) {
				//server.setIP(resultSet.getString("IP"));
				server.setIP(ConverterUtil.longToIp(resultSet.getLong("startRange")));
				server.setLatitude(resultSet.getString("latitude"));
				server.setLongitude(resultSet.getString("longitude"));
			}
			//
			surrogateServersList.add(server);
			// serversDefinitions.add(defineServer(sRouter, false));
			serversConnections.add(connectServerToRouter(sRouter, false));
		}
	}

	public static String generateSimulationFiles() throws IOException {
		File file = new File(workingDir + "\\cdnlab");
		if (!file.exists())
			file.mkdir();

		File src = new File(System.getProperty("user.dir")
				+ "\\cdnlab_files\\cdnlab");
		File target = new File(workingDir + "\\cdnlab");
		// copy source files
		FileUtils.copyDirectory(src, target);
		// copy website files
		src = new File(workingDir + "\\sites");
		target = new File(workingDir + "\\cdnlab\\sites");
		FileUtils.copyDirectory(src, target);
		// create omnetpp.ini file
		File omnetpp_ini = new File(workingDir + "\\cdnlab\\omnetpp.ini");
		PrintWriter writer=new PrintWriter(omnetpp_ini);
		writer.print(generateOmnetppIniFile());
		writer.close();
		// create network (NED file) file
		File network_ned = new File(workingDir + "\\cdnlab\\CDN_InfrastructureByCountry.ned");
		writer=new PrintWriter(network_ned);
		writer.print(generateNetwork());
		writer.close();
		
		return workingDir + "\\cdnlab";
	}

	public static Connection getConnection() throws ClassNotFoundException,
			SQLException {
		Class.forName("com.mysql.jdbc.Driver");
		System.out.println("MySQL JDBC Driver Registered!");
		Connection connection = DriverManager.getConnection(connectionString,
				dbUsername, dbPassword);
		return connection;
	}

	public String getDbUsername() {
		return dbUsername;
	}

	public void setDbUsername(String dbUsername) {
		this.dbUsername = dbUsername;
	}

	public String getDbPassword() {
		return dbPassword;
	}

	public void setDbPassword(String dbPassword) {
		this.dbPassword = dbPassword;
	}

	public static boolean checkPhase1() {
		if (workingDir != "" && connectionString != "" && dbUsername != ""
				&& dbPassword != "")
			return true;
		return false;
	}

	public static boolean checkPhase2() {
		if (routerSet != "")
			return true;
		return false;
	}

	public static boolean checkPhase3() {
		if (resourcesMap != null)
			return true;
		return false;
	}

	public static boolean checkPhase4() {
		// if(workingDir!="" && connectionString!="" && dbUsername!="" &&
		// dbPassword!="")
		return true;
		// return false;
	}

	public static boolean checkPhase5() {
		// if(workingDir!="" && connectionString!="" && dbUsername!="" &&
		// dbPassword!="")
		return true;
		// return false;
	}

	public static boolean checkPhase6() {
		// if(workingDir!="" && connectionString!="" && dbUsername!="" &&
		// dbPassword!="")
		return true;
		// return false;
	}

	public static String getRouterSet() {
		return routerSet;
	}

	public static void setRouterSet(String routerSet) {
		CDNLabConfig.routerSet = routerSet;
	}

	public static double epochTimeToSimTime(long minEpoch, long maxEpoch,
			long epochTime) {
		/*
		 * min simTime = 0 max simTime = 9223372 normalization function:
		 * 
		 * (maxSim-minSim)(x - minEpoch) f(x) = ----------------------------- +
		 * minSim maxEpoch - minEpoch
		 */
		Long normalized_val = ((9200000) * (epochTime - minEpoch))
				/ (maxEpoch - minEpoch);
		if (normalized_val <= lastEpochTimeUsed)
			normalized_val = lastEpochTimeUsed + 1;

		lastEpochTimeUsed = normalized_val;

		return normalized_val;
	}

	public static String defineClient(Client client) {
		String standardHost = "		" + client.getName();
		standardHost += ": Client {\n" + "		parameters:\n"
				+ "			@display(\"i=device/laptop_l;t=["
				+ client.getIP_address_on_web() + "];is=vs\");\n"
				+ "			IP_address_on_web = \"" + client.getIP_address_on_web()
				+ "\";\n" + "			latitude = " + client.getLatitude() + ";\n"
				+ "			longitude = " + client.getLongitude() + ";\n		}";

		return standardHost;
	}

	public static String defineServer(String routerToConnect,
			boolean isOriginServer) {
		String serverDef = "";
		if (isOriginServer) {
			serverDef = "		originServer: StandardHost {\n"
					+ "			@display(\"i=device/server;is=vs\");\n" + "		}";
		} else {
			String serverID = routerToConnect.substring(9, 11);
			serverDef = "		surrogateServer_" + serverID + ": StandardHost {\n"
					+ "			@display(\"i=device/server2;is=vs\");\n" + "		}";
		}

		return serverDef;
	}

	public static String connectClientToCountryRouter(Client client,
			String countryName) {
		String connection = "        " + client.getName();
		connection += ".ethg++ <--> gigabitline <--> asRouter_" + countryName
				+ ".ethg++;";
		return connection;
	}

	public static String connectServerToRouter(String routerToConnect,
			boolean isOriginServer) {
		String connection = "";
		if (isOriginServer) {
			connection = "        originServer.ethg++ <--> gigabitline <--> "
					+ routerToConnect
							.substring(0, routerToConnect.indexOf(" "))
					+ ".ethg++;";
		} else {
			String serverID = routerToConnect.substring(9, 11);
			connection = "        surrogateServer_"
					+ serverID
					+ ".ethg++ <--> gigabitline <--> "
					+ routerToConnect
							.substring(0, routerToConnect.indexOf(" "))
					+ ".ethg++;";
		}
		return connection;
	}

	public static String generateOmnetppIniFile() {
		String omnetpp_ini = "";
		omnetpp_ini = "# ----------------------------------------------------------------------------\n"
				+ "#\n" + "# CDNLab Project\n" + "# "
				+ websiteURL
				+ "\n"
				+ "#\n"
				+ "# --------------------------------------------------------------------------\n"
				+ "[General]\n"
				+ "network = CDN_InfrastructureByCountry\n"
				+ "\n"
				+ "cmdenv-express-mode = true\n"
				+ "\n"
				+ "tkenv-plugin-path = ../../../../etc/plugins\n"
				+ "tkenv-default-run = 1\n"
				+ "#sim-time-limit = 100d\n"
				+ "\n"
				+ "# Controller\n"
				+ "**.controller.logLevel = 0\n"
				+ "**.controller.config = xmldoc(\"configs/controller_cfg.xml\",\"//controller-profile[@id='uniform']\")\n"
				+ "**.controller.events = \"\"\n"
				+ "**.controller.eventsSection = \"\"\n"
				+ "**.controller.cdnMode = true\n"
				+ "# udp app (off)\n"
				+ "**.numUdpApps = 0\n"
				+ "\n"
				+ "# tcp apps\n"
				+ "**.client*.numTcpApps = 1\n"
				+ "**.client*.tcpApp[0].typename = \"HttpCDNBrowser\"\n"
				+ "**.client*.tcpApp[0].httpProtocol = 11\n"
				+ "**.client*.tcpApp[0].logLevel = 2\n"
				+ "**.client*.tcpApp[0].logFile = \"\" # Logging disabled\n"
				+ "**.client*.tcpApp[0].config = xmldoc(\"configs/browser_cfg.xml\",\"//user-profile[@id='normal']\")\n"
				+ "**.client*.tcpApp[0].activationTime = 0.0\n"
				+ "\n"
				+ "# Servers (Generically)\n"
				+ "**.*Server*.numTcpApps = 1\n"
				+ "**.*Server*.tcpApp[0].hostName = \""
				+ websiteURL
				+ "\"\n"
				+ "**.*Server*.tcpApp[0].port = 80\n"
				+ "**.*Server*.tcpApp[0].httpProtocol = 11\n"
				+ "**.*Server*.tcpApp[0].logLevel = 2\n"
				+ "**.*Server*.tcpApp[0].logFile = \"\" # Logging disabled\n"
				+ "**.*Server*.tcpApp[0].siteDefinition = \"\" # This will be set for origin server later on \n"
				+ "**.*Server*.tcpApp[0].config = xmldoc(\"configs/server_cfg.xml\",\"//server-profile[@id='normal']\")\n"
				+ "**.*Server*.tcpApp[0].activationTime = 0.0\n"
				+ "\n"
				+ "# tcp settings\n"
				+ "**.tcp.mss = 1024\n"
				+ "**.tcp.advertisedWindow = 14336  # 14*mss\n"
				+ "**.tcp.tcpAlgorithmClass = \"TCPReno\"\n"
				+ "**.tcp.recordStats = true\n"
				+ "\n"
				+ "# ip settings\n"
				+ "**.routingFile = \"\"\n"
				+ "**.ip.procDelay = 10000us\n"
				+ "**.*client*.IPForward = false\n"
				+ "**.*Server*.IPForward = false\n"
				+ "\n"
				+ "# ARP configuration\n"
				+ "**.arp.retryTimeout = 1s\n"
				+ "**.arp.retryCount = 3\n"
				+ "**.arp.cacheTimeout = 100s\n"
				+ "**.networkLayer.proxyARP = true  # Host's is hardwired \"false\"\n"
				+ "\n"
				+ "# NIC configuration\n"
				+ "**.ppp[*].queueType = \"DropTailQueue\" # in routers\n"
				+ "**.ppp[*].queue.frameCapacity = 10  # in routers\n"
				+ "\n"
				+ "[Config scripted]\n"
				+ "# The single server uses a scripted site definition. The browser executes scripted\n"
				+ "# events which request valid pages from the server. This should result in only valid\n"
				+ "# responses.\n\n"
				+

				"# Origin Servers\n"
				+ "**.*originServer*.tcpApp[0].typename = \"OriginServer\"\n"
				+ "**.*originServer*.tcpApp[0].siteDefinition = \"sites/"
				+ websiteURL.replace(".", "_")
				+ "/site_definition.pagedef"
				+ "\"\n"
				+ "**.*originServer*.tcpApp[0].tcpApp[0].IP_address_on_web = \"\"\n"
				+ "**.*originServer*.tcpApp[0].tcpApp[0].latitude = 0\n"
				+ "**.*originServer*.tcpApp[0].tcpApp[0].longitude = 0\n"
				+ "\n"

				+"# Surrogate Servers\n"
				+ "**.*surrogateServer*.tcpApp[0].typename = \"SurrogateServer\"\n";
		for (Server server : surrogateServersList) {
			omnetpp_ini += "**."+ server.getName()	+ ".tcpApp[0].IP_address_on_web = \""+server.getIP()+"\" \n";
			omnetpp_ini += "**."+ server.getName() + ".tcpApp[0].latitude = "+server.getLatitude()+" \n";
			omnetpp_ini += "**."+ server.getName() + ".tcpApp[0].longitude = "+server.getLongitude()+" \n";
		}

		omnetpp_ini += "\n#CLIENTS\n";
		// -------------------------
		for (String client : clientAccessMap.keySet()) {
			
			omnetpp_ini += "**." + client
					+ ".tcpApp[0].scriptFile = \"sites/browse/browse_" + client
					+ ".script\"\n";
		}
		return omnetpp_ini;
	}

	public static String generateNetwork(){
		String network="";
		network+=
				"package inet.cdnlab;\n"+
		"import inet.nodes.inet.Router;\n"+
		"import inet.world.httptools.HttpController;\n"+
		"import inet.networklayer.autorouting.ipv4.IPv4NetworkConfigurator;\n"+
		"import inet.nodes.inet.StandardHost;\n"+
		"import inet.cdnlab.controller.CDNController;\n"+
		"\n"+
		"network CDN_InfrastructureByCountry\n"+
		"{\n"+
		"    @display(\"bgb=1700,755;bgl=78;bgi=maps/world,s\");\n"+
		"    types:\n"+
		"        channel gigabitline extends ned.DatarateChannel\n"+
		"        {\n"+
		"            parameters:\n"+
		"                delay = 0.1us;\n"+
		"                datarate = 1000Mbps;\n"+
		"        }\n"+
		"	submodules:\n"+
        "		controller: CDNController {\n"+
        "			@display(\"p=30,23;i=block/cogwheel;is=vs\");\n"+
        "		}\n"+
        "		configurator: IPv4NetworkConfigurator {\n"+
        "			@display(\"p=1681,32\");\n"+
        "		}\n"+
		"		originServer: StandardHost {\n"+
		"            @display(\"p=441,176;i=device/server,,0;is=vs\");\n"+
		"        }\n";
		for(Server server:surrogateServersList){
			network+=server.getDefinition()+"\n";
		}
		network+="\n//CLIENTS\n";
		//network+= clientsDefinition;
		for(Client client: clientsList){
			network+=client.getDefinition()+"\n";
		}
		network+="\n//---------------------------------- ROUTERS\n";
		network+=getCountryRoutersAndConnections();
		//other connections
		network+="\n";
		for(String connection:serversConnections){
			network+=connection+"\n";
		}
		network+="\n";
		network+=clientsConnections;
		network+="\n}";
		return network;
	}
	
	public static String getCountryRoutersAndConnections(){
		String routers="";
		routers=
			"	asRouter_LV: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[LATVIA];is=vs\");\n"+
			"               latitude = 57;\n"+
			"               longitude = 25;\n"+
			"               country = \"LV\";\n"+
			"        }\n\n"+

			"       asRouter_LU: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[LUXEMBOURG];is=vs\");\n"+
			"               latitude = 50;\n"+
			"               longitude = 6;\n"+
			"               country = \"LU\";\n"+
			"        }\n\n"+

			"       asRouter_LT: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[LITHUANIA];is=vs\");\n"+
			"               latitude = 55;\n"+
			"               longitude = 24;\n"+
			"               country = \"LT\";\n"+
			"        }\n\n"+

			"       asRouter_VN: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[VIET NAM];is=vs\");\n"+
			"               latitude = 11;\n"+
			"               longitude = 107;\n"+
			"               country = \"VN\";\n"+
			"        }\n\n"+

			"       asRouter_VI: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[VIRGIN ISLANDS (U.S.)];is=vs\");\n"+
			"               latitude = 18;\n"+
			"               longitude = -65;\n"+
			"               country = \"VI\";\n"+
			"        }\n\n"+

			"       asRouter_DZ: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[ALGERIA];is=vs\");\n"+
			"               latitude = 28;\n"+
			"               longitude = 3;\n"+
			"               country = \"DZ\";\n"+
			"        }\n\n"+

			"       asRouter_VG: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[VIRGIN ISLANDS (BRITISH)];is=vs\");\n"+
			"               latitude = 18;\n"+
			"               longitude = -65;\n"+
			"               country = \"VG\";\n"+
			"        }\n\n"+

			"       asRouter_VE: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[VENEZUELA];is=vs\");\n"+
			"               latitude = 10;\n"+
			"               longitude = -67;\n"+
			"               country = \"VE\";\n"+
			"        }\n\n"+

			"       asRouter_MG: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[MADAGASCAR];is=vs\");\n"+
			"               latitude = -19;\n"+
			"               longitude = 48;\n"+
			"               country = \"MG\";\n"+
			"        }\n\n"+

			"       asRouter_MH: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[MARSHALL ISLANDS];is=vs\");\n"+
			"               latitude = 7;\n"+
			"               longitude = 171;\n"+
			"               country = \"MH\";\n"+
			"        }\n\n"+

			"       asRouter_DO: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[DOMINICAN REPUBLIC];is=vs\");\n"+
			"               latitude = 18;\n"+
			"               longitude = -70;\n"+
			"               country = \"DO\";\n"+
			"        }\n\n"+

			"       asRouter_ME: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[MONTENEGRO];is=vs\");\n"+
			"               latitude = 42;\n"+
			"               longitude = 19;\n"+
			"               country = \"ME\";\n"+
			"        }\n\n"+

			"       asRouter_MK: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[MACEDONIA];is=vs\");\n"+
			"               latitude = 42;\n"+
			"               longitude = 21;\n"+
			"               country = \"MK\";\n"+
			"        }\n\n"+

			"       asRouter_DE: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[GERMANY];is=vs;p=965,144\");\n"+
			"               latitude = 51;\n"+
			"               longitude = 9;\n"+
			"               country = \"DE\";\n"+
			"        }\n\n"+

			"       asRouter_UZ: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[UZBEKISTAN];is=vs\");\n"+
			"               latitude = 41;\n"+
			"               longitude = 69;\n"+
			"               country = \"UZ\";\n"+
			"        }\n\n"+

			"       asRouter_MC: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[MONACO];is=vs\");\n"+
			"               latitude = 44;\n"+
			"               longitude = 7;\n"+
			"               country = \"MC\";\n"+
			"        }\n\n"+

			"       asRouter_MD: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[MOLDOVA  REPUBLIC OF];is=vs\");\n"+
			"               latitude = 47;\n"+
			"               longitude = 29;\n"+
			"               country = \"MD\";\n"+
			"        }\n\n"+

			"       asRouter_DK: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[DENMARK];is=vs\");\n"+
			"               latitude = 56;\n"+
			"               longitude = 12;\n"+
			"               country = \"DK\";\n"+
			"        }\n\n"+

			"       asRouter_MA: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[MOROCCO];is=vs;p=756,389\");\n"+
			"               latitude = 34;\n"+
			"               longitude = -6;\n"+
			"               country = \"MA\";\n"+
			"        }\n\n"+

			"       asRouter_MU: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[MAURITIUS];is=vs\");\n"+
			"               latitude = -20;\n"+
			"               longitude = 58;\n"+
			"               country = \"MU\";\n"+
			"        }\n\n"+

			"       asRouter_US: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[UNITED STATES];is=vs;p=339,217\");\n"+
			"               latitude = 42;\n"+
			"               longitude = -71;\n"+
			"               country = \"US\";\n"+
			"        }\n\n"+

			"       asRouter_MX: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[MEXICO];is=vs;p=264,330\");\n"+
			"               latitude = 19;\n"+
			"               longitude = -99;\n"+
			"               country = \"MX\";\n"+
			"        }\n\n"+

			"       asRouter_MZ: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[MOZAMBIQUE];is=vs\");\n"+
			"               latitude = -26;\n"+
			"               longitude = 33;\n"+
			"               country = \"MZ\";\n"+
			"        }\n\n"+

			"       asRouter_MY: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[MALAYSIA];is=vs\");\n"+
			"               latitude = 2;\n"+
			"               longitude = 112;\n"+
			"               country = \"MY\";\n"+
			"        }\n\n"+

			"       asRouter_EU: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[];is=vs\");\n"+
			"               latitude = 47;\n"+
			"               longitude = 8;\n"+
			"               country = \"EU\";\n"+
			"        }\n\n"+

			"       asRouter_MQ: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[MARTINIQUE];is=vs\");\n"+
			"               latitude = 15;\n"+
			"               longitude = -61;\n"+
			"               country = \"MQ\";\n"+
			"        }\n\n"+

			"       asRouter_UG: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[UGANDA];is=vs\");\n"+
			"               latitude = 1;\n"+
			"               longitude = 32;\n"+
			"               country = \"UG\";\n"+
			"        }\n\n"+

			"       asRouter_MT: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[MALTA];is=vs\");\n"+
			"               latitude = 36;\n"+
			"               longitude = 14;\n"+
			"               country = \"MT\";\n"+
			"        }\n\n"+

			"       asRouter_UA: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[UKRAINE];is=vs\");\n"+
			"               latitude = 50;\n"+
			"               longitude = 31;\n"+
			"               country = \"UA\";\n"+
			"        }\n\n"+

			"       asRouter_NG: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[NIGERIA];is=vs\");\n"+
			"               latitude = 10;\n"+
			"               longitude = 8;\n"+
			"               country = \"NG\";\n"+
			"        }\n\n"+

			"       asRouter_NI: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[NICARAGUA];is=vs\");\n"+
			"               latitude = 12;\n"+
			"               longitude = -86;\n"+
			"               country = \"NI\";\n"+
			"        }\n\n"+

			"       asRouter_ES: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[SPAIN];is=vs\");\n"+
			"               latitude = 40;\n"+
			"               longitude = -4;\n"+
			"               country = \"ES\";\n"+
			"        }\n\n"+

			"       asRouter_NL: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[NETHERLANDS];is=vs\");\n"+
			"               latitude = 52;\n"+
			"               longitude = 4;\n"+
			"               country = \"NL\";\n"+
			"        }\n\n"+

			"       asRouter_EG: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[EGYPT];is=vs;p=729,276\");\n"+
			"               latitude = 27;\n"+
			"               longitude = 30;\n"+
			"               country = \"EG\";\n"+
			"        }\n\n"+

			"       asRouter_TZ: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[TANZANIA  UNITED REPUBLIC OF];is=vs\");\n"+
			"               latitude = -6;\n"+
			"               longitude = 35;\n"+
			"               country = \"TZ\";\n"+
			"        }\n\n"+

			"       asRouter_EE: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[ESTONIA];is=vs;p=1065,159\");\n"+
			"               latitude = 59;\n"+
			"               longitude = 24;\n"+
			"               country = \"EE\";\n"+
			"        }\n\n"+

			"       asRouter_TT: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[TRINIDAD AND TOBAGO];is=vs\");\n"+
			"               latitude = 11;\n"+
			"               longitude = -61;\n"+
			"               country = \"TT\";\n"+
			"        }\n\n"+

			"       asRouter_TW: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[TAIWAN  PROVINCE OF CHINA];is=vs\");\n"+
			"               latitude = 25;\n"+
			"               longitude = 122;\n"+
			"               country = \"TW\";\n"+
			"        }\n\n"+

			"       asRouter_GE: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[GEORGIA];is=vs\");\n"+
			"               latitude = 42;\n"+
			"               longitude = 43;\n"+
			"               country = \"GE\";\n"+
			"        }\n\n"+

			"       asRouter_NZ: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[NEW ZEALAND];is=vs;p=1465,550\");\n"+
			"               latitude = -41;\n"+
			"               longitude = 174;\n"+
			"               country = \"NZ\";\n"+
			"        }\n\n"+

			"       asRouter_GA: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[GABON];is=vs\");\n"+
			"               latitude = -1;\n"+
			"               longitude = 12;\n"+
			"               country = \"GA\";\n"+
			"        }\n\n"+

			"       asRouter_GB: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[UNITED KINGDOM];is=vs\");\n"+
			"               latitude = 54;\n"+
			"               longitude = -2;\n"+
			"               country = \"GB\";\n"+
			"        }\n\n"+

			"       asRouter_NO: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[NORWAY];is=vs\");\n"+
			"               latitude = 63;\n"+
			"               longitude = 10;\n"+
			"               country = \"NO\";\n"+
			"        }\n\n"+

			"       asRouter_OM: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[OMAN];is=vs\");\n"+
			"               latitude = 24;\n"+
			"               longitude = 59;\n"+
			"               country = \"OM\";\n"+
			"        }\n\n"+

			"       asRouter_FR: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[FRANCE];is=vs\");\n"+
			"               latitude = 46;\n"+
			"               longitude = 2;\n"+
			"               country = \"FR\";\n"+
			"        }\n\n"+

			"       asRouter_FO: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[FAROE ISLANDS];is=vs\");\n"+
			"               latitude = 62;\n"+
			"               longitude = -7;\n"+
			"               country = \"FO\";\n"+
			"        }\n\n"+

			"       asRouter_FK: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[FALKLAND ISLANDS (MALVINAS)];is=vs\");\n"+
			"               latitude = -52;\n"+
			"               longitude = -59;\n"+
			"               country = \"FK\";\n"+
			"        }\n\n"+

			"       asRouter_FM: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[MICRONESIA  FEDERATED STATES OF];is=vs;p=1311,223\");\n"+
			"               latitude = 7;\n"+
			"               longitude = 158;\n"+
			"               country = \"FM\";\n"+
			"        }\n\n"+

			"       asRouter_FI: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[FINLAND];is=vs\");\n"+
			"               latitude = 60;\n"+
			"               longitude = 25;\n"+
			"               country = \"FI\";\n"+
			"        }\n\n"+

			"       asRouter_WS: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[SAMOA];is=vs\");\n"+
			"               latitude = -14;\n"+
			"               longitude = -172;\n"+
			"               country = \"WS\";\n"+
			"        }\n\n"+

			"       asRouter_PL: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[POLAND];is=vs\");\n"+
			"               latitude = 53;\n"+
			"               longitude = 19;\n"+
			"               country = \"PL\";\n"+
			"        }\n\n"+

			"       asRouter_GU: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[GUAM];is=vs\");\n"+
			"               latitude = 13;\n"+
			"               longitude = 145;\n"+
			"               country = \"GU\";\n"+
			"        }\n\n"+

			"       asRouter_GT: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[GUATEMALA];is=vs\");\n"+
			"               latitude = 15;\n"+
			"               longitude = -91;\n"+
			"               country = \"GT\";\n"+
			"        }\n\n"+

			"       asRouter_PH: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[PHILIPPINES];is=vs;p=1199,234\");\n"+
			"               latitude = 15;\n"+
			"               longitude = 121;\n"+
			"               country = \"PH\";\n"+
			"        }\n\n"+

			"       asRouter_GR: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[GREECE];is=vs\");\n"+
			"               latitude = 38;\n"+
			"               longitude = 24;\n"+
			"               country = \"GR\";\n"+
			"        }\n\n"+

			"       asRouter_PK: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[PAKISTAN];is=vs\");\n"+
			"               latitude = 25;\n"+
			"               longitude = 67;\n"+
			"               country = \"PK\";\n"+
			"        }\n\n"+

			"       asRouter_PF: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[FRENCH POLYNESIA];is=vs\");\n"+
			"               latitude = -18;\n"+
			"               longitude = -150;\n"+
			"               country = \"PF\";\n"+
			"        }\n\n"+

			"       asRouter_PA: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[PANAMA];is=vs\");\n"+
			"               latitude = 9;\n"+
			"               longitude = -80;\n"+
			"               country = \"PA\";\n"+
			"        }\n\n"+

			"       asRouter_GI: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[GIBRALTAR];is=vs\");\n"+
			"               latitude = 36;\n"+
			"               longitude = -5;\n"+
			"               country = \"GI\";\n"+
			"        }\n\n"+

			"       asRouter_GH: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[GHANA];is=vs\");\n"+
			"               latitude = 8;\n"+
			"               longitude = -2;\n"+
			"               country = \"GH\";\n"+
			"        }\n\n"+

			"       asRouter_A2: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[];is=vs\");\n"+
			"               latitude = 0;\n"+
			"               longitude = 0;\n"+
			"               country = \"A2\";\n"+
			"        }\n\n"+

			"       asRouter_A1: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[];is=vs;p=440,417\");\n"+
			"               latitude = 0;\n"+
			"               longitude = 0;\n"+
			"               country = \"A1\";\n"+
			"        }\n\n"+

			"       asRouter_HK: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[HONG KONG];is=vs\");\n"+
			"               latitude = 22;\n"+
			"               longitude = 114;\n"+
			"               country = \"HK\";\n"+
			"        }\n\n"+

			"       asRouter_ZA: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[SOUTH AFRICA];is=vs;p=900,379\");\n"+
			"               latitude = -29;\n"+
			"               longitude = 24;\n"+
			"               country = \"ZA\";\n"+
			"        }\n\n"+

			"       asRouter_RE: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[REUNION];is=vs;p=508,234\");\n"+
			"               latitude = -21;\n"+
			"               longitude = 55;\n"+
			"               country = \"RE\";\n"+
			"        }\n\n"+

			"       asRouter_HN: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[HONDURAS];is=vs\");\n"+
			"               latitude = 14;\n"+
			"               longitude = -88;\n"+
			"               country = \"HN\";\n"+
			"        }\n\n"+

			"       asRouter_HR: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[CROATIA (local name: Hrvatska)];is=vs\");\n"+
			"               latitude = 46;\n"+
			"               longitude = 16;\n"+
			"               country = \"HR\";\n"+
			"        }\n\n"+

			"       asRouter_RO: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[ROMANIA];is=vs\");\n"+
			"               latitude = 45;\n"+
			"               longitude = 24;\n"+
			"               country = \"RO\";\n"+
			"        }\n\n"+

			"       asRouter_HU: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[HUNGARY];is=vs\");\n"+
			"               latitude = 47;\n"+
			"               longitude = 19;\n"+
			"               country = \"HU\";\n"+
			"        }\n\n"+

			"       asRouter_ZM: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[ZAMBIA];is=vs;p=211,121\");\n"+
			"               latitude = -15;\n"+
			"               longitude = 30;\n"+
			"               country = \"ZM\";\n"+
			"        }\n\n"+

			"       asRouter_ID: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[INDONESIA];is=vs;p=1148,254\");\n"+
			"               latitude = -6;\n"+
			"               longitude = 107;\n"+
			"               country = \"ID\";\n"+
			"        }\n\n"+

			"       asRouter_IE: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[IRELAND];is=vs\");\n"+
			"               latitude = 53;\n"+
			"               longitude = -6;\n"+
			"               country = \"IE\";\n"+
			"        }\n\n"+

			"       asRouter_AT: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[AUSTRIA];is=vs\");\n"+
			"               latitude = 48;\n"+
			"               longitude = 16;\n"+
			"               country = \"AT\";\n"+
			"        }\n\n"+

			"       asRouter_AS: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[AMERICAN SAMOA];is=vs\");\n"+
			"               latitude = -14;\n"+
			"               longitude = -171;\n"+
			"               country = \"AS\";\n"+
			"        }\n\n"+

			"       asRouter_AR: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[ARGENTINA];is=vs;p=555,507\");\n"+
			"               latitude = -35;\n"+
			"               longitude = -59;\n"+
			"               country = \"AR\";\n"+
			"        }\n\n"+

			"       asRouter_IL: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[ISRAEL];is=vs\");\n"+
			"               latitude = 31;\n"+
			"               longitude = 35;\n"+
			"               country = \"IL\";\n"+
			"        }\n\n"+

			"       asRouter_AX: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[];is=vs\");\n"+
			"               latitude = 60;\n"+
			"               longitude = 20;\n"+
			"               country = \"AX\";\n"+
			"        }\n\n"+

			"       asRouter_IM: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[];is=vs\");\n"+
			"               latitude = 54;\n"+
			"               longitude = -5;\n"+
			"               country = \"IM\";\n"+
			"        }\n\n"+

			"       asRouter_AW: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[ARUBA];is=vs\");\n"+
			"               latitude = 13;\n"+
			"               longitude = -70;\n"+
			"               country = \"AW\";\n"+
			"        }\n\n"+

			"       asRouter_IN: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[INDIA];is=vs;p=1138,305\");\n"+
			"               latitude = 13;\n"+
			"               longitude = 78;\n"+
			"               country = \"IN\";\n"+
			"        }\n\n"+

			"       asRouter_AU: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[AUSTRALIA];is=vs;p=1400,561\");\n"+
			"               latitude = -27;\n"+
			"               longitude = 133;\n"+
			"               country = \"AU\";\n"+
			"        }\n\n"+

			"       asRouter_IQ: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[IRAQ];is=vs\");\n"+
			"               latitude = 33;\n"+
			"               longitude = 44;\n"+
			"               country = \"IQ\";\n"+
			"        }\n\n"+

			"       asRouter_IR: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[IRAN (ISLAMIC REPUBLIC OF)];is=vs;p=1027,266\");\n"+
			"               latitude = 33;\n"+//(33+90)*4.19
			"               longitude = 51;\n"+//(51+180)*4.72
			"               country = \"IR\";\n"+
			"        }\n\n"+

			"       asRouter_AZ: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[AZERBAIJAN];is=vs\");\n"+
			"               latitude = 40;\n"+
			"               longitude = 50;\n"+
			"               country = \"AZ\";\n"+
			"        }\n\n"+

			"       asRouter_IS: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[ICELAND];is=vs\");\n"+
			"               latitude = 65;\n"+
			"               longitude = -18;\n"+
			"               country = \"IS\";\n"+
			"        }\n\n"+

			"       asRouter_IT: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[ITALY];is=vs\");\n"+
			"               latitude = 44;\n"+
			"               longitude = 13;\n"+
			"               country = \"IT\";\n"+
			"        }\n\n"+

			"       asRouter_BA: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[BOSNIA AND HERZEGOWINA];is=vs\");\n"+
			"               latitude = 45;\n"+
			"               longitude = 17;\n"+
			"               country = \"BA\";\n"+
			"        }\n\n"+

			"       asRouter_PT: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[PORTUGAL];is=vs\");\n"+
			"               latitude = 39;\n"+
			"               longitude = -8;\n"+
			"               country = \"PT\";\n"+
			"        }\n\n"+

			"       asRouter_AG: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[ANTIGUA AND BARBUDA];is=vs\");\n"+
			"               latitude = 17;\n"+
			"               longitude = -62;\n"+
			"               country = \"AG\";\n"+
			"        }\n\n"+

			"       asRouter_PR: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[PUERTO RICO];is=vs\");\n"+
			"               latitude = 18;\n"+
			"               longitude = -67;\n"+
			"               country = \"PR\";\n"+
			"        }\n\n"+

			"       asRouter_AE: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[UNITED ARAB EMIRATES];is=vs\");\n"+
			"               latitude = 25;\n"+
			"               longitude = 55;\n"+
			"               country = \"AE\";\n"+
			"        }\n\n"+

			"       asRouter_AF: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[AFGHANISTAN];is=vs;p=1077,247\");\n"+
			"               latitude = 35;\n"+
			"               longitude = 69;\n"+
			"               country = \"AF\";\n"+
			"        }\n\n"+

			"       asRouter_AL: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[ALBANIA];is=vs\");\n"+
			"               latitude = 41;\n"+
			"               longitude = 20;\n"+
			"               country = \"AL\";\n"+
			"        }\n\n"+

			"       asRouter_JE: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[];is=vs\");\n"+
			"               latitude = 49;\n"+
			"               longitude = -2;\n"+
			"               country = \"JE\";\n"+
			"        }\n\n"+

			"       asRouter_AO: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[ANGOLA];is=vs\");\n"+
			"               latitude = -9;\n"+
			"               longitude = 13;\n"+
			"               country = \"AO\";\n"+
			"        }\n\n"+

			"       asRouter_PY: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[PARAGUAY];is=vs\");\n"+
			"               latitude = -22;\n"+
			"               longitude = -60;\n"+
			"               country = \"PY\";\n"+
			"        }\n\n"+

			"       asRouter_AP: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[];is=vs\");\n"+
			"               latitude = 35;\n"+
			"               longitude = 105;\n"+
			"               country = \"AP\";\n"+
			"        }\n\n"+

			"       asRouter_AM: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[ARMENIA];is=vs;p=1026,229\");\n"+
			"               latitude = 40;\n"+
			"               longitude = 45;\n"+
			"               country = \"AM\";\n"+
			"        }\n\n"+

			"       asRouter_AN: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[NETHERLANDS ANTILLES];is=vs\");\n"+
			"               latitude = 12;\n"+
			"               longitude = -69;\n"+
			"               country = \"AN\";\n"+
			"        }\n\n"+

			"       asRouter_JP: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[JAPAN];is=vs;p=1426,115\");\n"+
			"               latitude = 36;\n"+
			"               longitude = 140;\n"+
			"               country = \"JP\";\n"+
			"        }\n\n"+

			"       asRouter_BY: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[BELARUS];is=vs;p=167,166\");\n"+
			"               latitude = 54;\n"+
			"               longitude = 28;\n"+
			"               country = \"BY\";\n"+
			"        }\n\n"+

			"       asRouter_JO: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[JORDAN];is=vs;p=1015,300\");\n"+
			"               latitude = 32;\n"+
			"               longitude = 36;\n"+
			"               country = \"JO\";\n"+
			"        }\n\n"+

			"       asRouter_BS: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[BAHAMAS];is=vs\");\n"+
			"               latitude = 25;\n"+
			"               longitude = -77;\n"+
			"               country = \"BS\";\n"+
			"        }\n\n"+

			"       asRouter_TJ: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[TAJIKISTAN];is=vs\");\n"+
			"               latitude = 39;\n"+
			"               longitude = 69;\n"+
			"               country = \"TJ\";\n"+
			"        }\n\n"+

			"       asRouter_JM: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[JAMAICA];is=vs\");\n"+
			"               latitude = 18;\n"+
			"               longitude = -77;\n"+
			"               country = \"JM\";\n"+
			"        }\n\n"+

			"       asRouter_TH: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[THAILAND];is=vs\");\n"+
			"               latitude = 8;\n"+
			"               longitude = 98;\n"+
			"               country = \"TH\";\n"+
			"        }\n\n"+

			"       asRouter_TN: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[TUNISIA];is=vs;p=837,379\");\n"+
			"               latitude = 34;\n"+
			"               longitude = 9;\n"+
			"               country = \"TN\";\n"+
			"        }\n\n"+

			"       asRouter_CA: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[CANADA];is=vs;p=386,149\");\n"+
			"               latitude = 44;\n"+
			"               longitude = -79;\n"+
			"               country = \"CA\";\n"+
			"        }\n\n"+

			"       asRouter_TR: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[TURKEY];is=vs\");\n"+
			"               latitude = 41;\n"+
			"               longitude = 29;\n"+
			"               country = \"TR\";\n"+
			"        }\n\n"+

			"       asRouter_BZ: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[BELIZE];is=vs\");\n"+
			"               latitude = 17;\n"+
			"               longitude = -89;\n"+
			"               country = \"BZ\";\n"+
			"        }\n\n"+

			"       asRouter_BF: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[BURKINA FASO];is=vs\");\n"+
			"               latitude = 13;\n"+
			"               longitude = -2;\n"+
			"               country = \"BF\";\n"+
			"        }\n\n"+

			"       asRouter_SV: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[EL SALVADOR];is=vs\");\n"+
			"               latitude = 14;\n"+
			"               longitude = -89;\n"+
			"               country = \"SV\";\n"+
			"        }\n\n"+

			"       asRouter_BG: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[BULGARIA];is=vs\");\n"+
			"               latitude = 42;\n"+
			"               longitude = 23;\n"+
			"               country = \"BG\";\n"+
			"        }\n\n"+

			"       asRouter_BH: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[BAHRAIN];is=vs\");\n"+
			"               latitude = 26;\n"+
			"               longitude = 51;\n"+
			"               country = \"BH\";\n"+
			"        }\n\n"+

			"       asRouter_BB: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[BARBADOS];is=vs\");\n"+
			"               latitude = 13;\n"+
			"               longitude = -60;\n"+
			"               country = \"BB\";\n"+
			"        }\n\n"+

			"       asRouter_BD: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[BANGLADESH];is=vs;p=1236,354\");\n"+
			"               latitude = 24;\n"+
			"               longitude = 90;\n"+
			"               country = \"BD\";\n"+
			"        }\n\n"+

			"       asRouter_BE: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[BELGIUM];is=vs\");\n"+
			"               latitude = 51;\n"+
			"               longitude = 3;\n"+
			"               country = \"BE\";\n"+
			"        }\n\n"+

			"       asRouter_BN: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[BRUNEI DARUSSALAM];is=vs\");\n"+
			"               latitude = 5;\n"+
			"               longitude = 115;\n"+
			"               country = \"BN\";\n"+
			"        }\n\n"+

			"       asRouter_BO: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[BOLIVIA];is=vs\");\n"+
			"               latitude = -12;\n"+
			"               longitude = -66;\n"+
			"               country = \"BO\";\n"+
			"        }\n\n"+

			"       asRouter_KH: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[CAMBODIA];is=vs\");\n"+
			"               latitude = 12;\n"+
			"               longitude = 105;\n"+
			"               country = \"KH\";\n"+
			"        }\n\n"+

			"       asRouter_KG: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[KYRGYZSTAN];is=vs;p=639,40\");\n"+
			"               latitude = 43;\n"+
			"               longitude = 75;\n"+
			"               country = \"KG\";\n"+
			"        }\n\n"+

			"       asRouter_KE: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[KENYA];is=vs;p=900,428\");\n"+
			"               latitude = 1;\n"+
			"               longitude = 38;\n"+
			"               country = \"KE\";\n"+
			"        }\n\n"+

			"       asRouter_BM: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[BERMUDA];is=vs;p=319,305\");\n"+
			"               latitude = 32;\n"+
			"               longitude = -65;\n"+
			"               country = \"BM\";\n"+
			"        }\n\n"+

			"       asRouter_SD: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[SUDAN];is=vs\");\n"+
			"               latitude = 15;\n"+
			"               longitude = 30;\n"+
			"               country = \"SD\";\n"+
			"        }\n\n"+

			"       asRouter_CZ: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[CZECH REPUBLIC];is=vs\");\n"+
			"               latitude = 50;\n"+
			"               longitude = 14;\n"+
			"               country = \"CZ\";\n"+
			"        }\n\n"+

			"       asRouter_SC: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[SEYCHELLES];is=vs\");\n"+
			"               latitude = -5;\n"+
			"               longitude = 56;\n"+
			"               country = \"SC\";\n"+
			"        }\n\n"+

			"       asRouter_CY: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[CYPRUS];is=vs\");\n"+
			"               latitude = 35;\n"+
			"               longitude = 33;\n"+
			"               country = \"CY\";\n"+
			"        }\n\n"+

			"       asRouter_SE: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[SWEDEN];is=vs;p=1028,126\");\n"+
			"               latitude = 59;\n"+
			"               longitude = 18;\n"+
			"               country = \"SE\";\n"+
			"        }\n\n"+

			"       asRouter_KR: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[KOREA  REPUBLIC OF];is=vs;p=1354,126\");\n"+
			"               latitude = 37;\n"+
			"               longitude = 127;\n"+
			"               country = \"KR\";\n"+
			"        }\n\n"+

			"       asRouter_SG: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[SINGAPORE];is=vs\");\n"+
			"               latitude = 1;\n"+
			"               longitude = 104;\n"+
			"               country = \"SG\";\n"+
			"        }\n\n"+

			"       asRouter_KM: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[COMOROS];is=vs\");\n"+
			"               latitude = -12;\n"+
			"               longitude = 44;\n"+
			"               country = \"KM\";\n"+
			"        }\n\n"+

			"       asRouter_SI: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[SLOVENIA];is=vs\");\n"+
			"               latitude = 46;\n"+
			"               longitude = 15;\n"+
			"               country = \"SI\";\n"+
			"        }\n\n"+

			"       asRouter_SL: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[SIERRA LEONE];is=vs\");\n"+
			"               latitude = 8;\n"+
			"               longitude = -12;\n"+
			"               country = \"SL\";\n"+
			"        }\n\n"+

			"       asRouter_KW: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[KUWAIT];is=vs\");\n"+
			"               latitude = 29;\n"+
			"               longitude = 48;\n"+
			"               country = \"KW\";\n"+
			"        }\n\n"+

			"       asRouter_SK: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[SLOVAKIA (Slovak Republic)];is=vs\");\n"+
			"               latitude = 48;\n"+
			"               longitude = 17;\n"+
			"               country = \"SK\";\n"+
			"        }\n\n"+

			"       asRouter_KY: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[CAYMAN ISLANDS];is=vs;p=371,273\");\n"+
			"               latitude = 19;\n"+
			"               longitude = -81;\n"+
			"               country = \"KY\";\n"+
			"        }\n\n"+

			"       asRouter_SM: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[SAN MARINO];is=vs\");\n"+
			"               latitude = 44;\n"+
			"               longitude = 12;\n"+
			"               country = \"SM\";\n"+
			"        }\n\n"+

			"       asRouter_KZ: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[KAZAKHSTAN];is=vs\");\n"+
			"               latitude = 52;\n"+
			"               longitude = 75;\n"+
			"               country = \"KZ\";\n"+
			"        }\n\n"+

			"       asRouter_SO: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[SOMALIA];is=vs\");\n"+
			"               latitude = 10;\n"+
			"               longitude = 49;\n"+
			"               country = \"SO\";\n"+
			"        }\n\n"+

			"       asRouter_LA: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[LAOS ];is=vs\");\n"+
			"               latitude = 18;\n"+
			"               longitude = 103;\n"+
			"               country = \"LA\";\n"+
			"        }\n\n"+

			"       asRouter_RS: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[SERBIA];is=vs\");\n"+
			"               latitude = 45;\n"+
			"               longitude = 20;\n"+
			"               country = \"RS\";\n"+
			"        }\n\n"+

			"       asRouter_RU: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[RUSSIAN FEDERATION];is=vs;p=1277,117\");\n"+
			"               latitude = 56;\n"+
			"               longitude = 38;\n"+
			"               country = \"RU\";\n"+
			"        }\n\n"+

			"       asRouter_CH: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[SWITZERLAND];is=vs\");\n"+
			"               latitude = 46;\n"+
			"               longitude = 6;\n"+
			"               country = \"CH\";\n"+
			"        }\n\n"+

			"       asRouter_LB: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[LEBANON];is=vs\");\n"+
			"               latitude = 34;\n"+
			"               longitude = 36;\n"+
			"               country = \"LB\";\n"+
			"        }\n\n"+

			"       asRouter_RW: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[RWANDA];is=vs\");\n"+
			"               latitude = -2;\n"+
			"               longitude = 30;\n"+
			"               country = \"RW\";\n"+
			"        }\n\n"+

			"       asRouter_CD: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[CONGO  THE DRC];is=vs\");\n"+
			"               latitude = 0;\n"+
			"               longitude = 25;\n"+
			"               country = \"CD\";\n"+
			"        }\n\n"+

			"       asRouter_LI: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[LIECHTENSTEIN];is=vs\");\n"+
			"               latitude = 47;\n"+
			"               longitude = 10;\n"+
			"               country = \"LI\";\n"+
			"        }\n\n"+

			"       asRouter_CR: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[COSTA RICA];is=vs\");\n"+
			"               latitude = 10;\n"+
			"               longitude = -84;\n"+
			"               country = \"CR\";\n"+
			"        }\n\n"+

			"       asRouter_CO: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[COLOMBIA];is=vs;p=494,466\");\n"+
			"               latitude = 5;\n"+
			"               longitude = -74;\n"+
			"               country = \"CO\";\n"+
			"        }\n\n"+

			"       asRouter_CN: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[CHINA];is=vs;p=1277,249\");\n"+
			"               latitude = 40;\n"+
			"               longitude = 116;\n"+
			"               country = \"CN\";\n"+
			"        }\n\n"+

			"       asRouter_SA: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[SAUDI ARABIA];is=vs\");\n"+
			"               latitude = 25;\n"+
			"               longitude = 47;\n"+
			"               country = \"SA\";\n"+
			"        }\n\n"+

			"       asRouter_CL: AS_Router {\n"+
			"           parameters:\n"+
			"               @display(\"t=[CHILE];is=vs\");\n"+
			"               latitude = -33;\n"+
			"               longitude = -71;\n"+
			"               country = \"CL\";\n"+
			"        }\n\n"+


			"    connections:\n"+
			"       asRouter_IT.ethg++ <--> gigabitline <--> asRouter_SM.ethg++;\n"+
			"       asRouter_DK.ethg++ <--> gigabitline <--> asRouter_SE.ethg++;\n"+
			"       asRouter_AT.ethg++ <--> gigabitline <--> asRouter_HR.ethg++;\n"+
			"       asRouter_AX.ethg++ <--> gigabitline <--> asRouter_SE.ethg++;\n"+
			"       asRouter_BE.ethg++ <--> gigabitline <--> asRouter_NL.ethg++;\n"+
			"       asRouter_BG.ethg++ <--> gigabitline <--> asRouter_MK.ethg++;\n"+
			"       asRouter_CH.ethg++ <--> gigabitline <--> asRouter_FR.ethg++;\n"+
			"       asRouter_CH.ethg++ <--> gigabitline <--> asRouter_EU.ethg++;\n"+
			"       asRouter_GE.ethg++ <--> gigabitline <--> asRouter_AM.ethg++;\n"+
			"        //asRouter_HU.ethg++ <--> gigabitline <--> asRouter_SK.ethg++;\n"+
			"       asRouter_LV.ethg++ <--> gigabitline <--> asRouter_LT.ethg++;\n"+
			"       asRouter_RO.ethg++ <--> gigabitline <--> asRouter_BG.ethg++;\n"+
			"       asRouter_SI.ethg++ <--> gigabitline <--> asRouter_HR.ethg++;\n"+
			"       asRouter_AT.ethg++ <--> gigabitline <--> asRouter_SI.ethg++;\n"+
			"       asRouter_AT.ethg++ <--> gigabitline <--> asRouter_CZ.ethg++;\n"+
			"       asRouter_BA.ethg++ <--> gigabitline <--> asRouter_SI.ethg++;\n"+
			"       asRouter_BE.ethg++ <--> gigabitline <--> asRouter_LU.ethg++;\n"+
			"       asRouter_DE.ethg++ <--> gigabitline <--> asRouter_LU.ethg++;\n"+
			"       asRouter_EU.ethg++ <--> gigabitline <--> asRouter_MC.ethg++;\n"+
			"       asRouter_FI.ethg++ <--> gigabitline <--> asRouter_LV.ethg++;\n"+
			"       asRouter_GB.ethg++ <--> gigabitline <--> asRouter_IM.ethg++;\n"+
			"       asRouter_LT.ethg++ <--> gigabitline <--> asRouter_RU.ethg++;\n"+
			"       asRouter_ME.ethg++ <--> gigabitline <--> asRouter_RS.ethg++;\n"+
			"       asRouter_NL.ethg++ <--> gigabitline <--> asRouter_LU.ethg++;\n"+
			"       asRouter_AF.ethg++ <--> gigabitline <--> asRouter_TJ.ethg++;\n"+
			"       asRouter_CH.ethg++ <--> gigabitline <--> asRouter_LI.ethg++;\n"+
			"       asRouter_CH.ethg++ <--> gigabitline <--> asRouter_LU.ethg++;\n"+
			"       asRouter_DE.ethg++ <--> gigabitline <--> asRouter_LI.ethg++;\n"+
			"       asRouter_DE.ethg++ <--> gigabitline <--> asRouter_EU.ethg++;\n"+
			"       asRouter_DK.ethg++ <--> gigabitline <--> asRouter_NO.ethg++;\n"+
			"       asRouter_ES.ethg++ <--> gigabitline <--> asRouter_GI.ethg++;\n"+
			"       asRouter_HU.ethg++ <--> gigabitline <--> asRouter_SI.ethg++;\n"+
			"       asRouter_IT.ethg++ <--> gigabitline <--> asRouter_SI.ethg++;\n"+
			"       asRouter_MD.ethg++ <--> gigabitline <--> asRouter_UA.ethg++;\n"+
			"       asRouter_MD.ethg++ <--> gigabitline <--> asRouter_RO.ethg++;\n"+
			"       asRouter_PL.ethg++ <--> gigabitline <--> asRouter_SK.ethg++;\n"+
			"       asRouter_SK.ethg++ <--> gigabitline <--> asRouter_CZ.ethg++;\n"+
			"       asRouter_TR.ethg++ <--> gigabitline <--> asRouter_RO.ethg++;\n"+
			"       asRouter_AT.ethg++ <--> gigabitline <--> asRouter_DE.ethg++;\n"+
			"       asRouter_AT.ethg++ <--> gigabitline <--> asRouter_PL.ethg++;\n"+
			"       asRouter_BE.ethg++ <--> gigabitline <--> asRouter_FR.ethg++;\n"+
			"       asRouter_CZ.ethg++ <--> gigabitline <--> asRouter_DE.ethg++;\n"+
			"       asRouter_DE.ethg++ <--> gigabitline <--> asRouter_NL.ethg++;\n"+
			"       asRouter_DE.ethg++ <--> gigabitline <--> asRouter_DK.ethg++;\n"+
			"       asRouter_EE.ethg++ <--> gigabitline <--> asRouter_LT.ethg++;\n"+
			"       asRouter_ES.ethg++ <--> gigabitline <--> asRouter_PT.ethg++;\n"+
			"       asRouter_FI.ethg++ <--> gigabitline <--> asRouter_RU.ethg++;\n"+
			"       asRouter_GR.ethg++ <--> gigabitline <--> asRouter_BG.ethg++;\n"+
			"       asRouter_HU.ethg++ <--> gigabitline <--> asRouter_AT.ethg++;\n"+
			"       asRouter_HU.ethg++ <--> gigabitline <--> asRouter_PL.ethg++;\n"+
			"       asRouter_IT.ethg++ <--> gigabitline <--> asRouter_LI.ethg++;\n"+
			"       asRouter_IT.ethg++ <--> gigabitline <--> asRouter_AT.ethg++;\n"+
			"       asRouter_LA.ethg++ <--> gigabitline <--> asRouter_TH.ethg++;\n"+
			"       asRouter_BE.ethg++ <--> gigabitline <--> asRouter_EU.ethg++;\n"+
			"       asRouter_BE.ethg++ <--> gigabitline <--> asRouter_GB.ethg++;\n"+
			"       asRouter_BE.ethg++ <--> gigabitline <--> asRouter_CH.ethg++;\n"+
			"       asRouter_CH.ethg++ <--> gigabitline <--> asRouter_NL.ethg++;\n"+
			"       asRouter_CZ.ethg++ <--> gigabitline <--> asRouter_HU.ethg++;\n"+
			"       asRouter_CZ.ethg++ <--> gigabitline <--> asRouter_PL.ethg++;\n"+
			"       asRouter_CZ.ethg++ <--> gigabitline <--> asRouter_DK.ethg++;\n"+
			"       asRouter_EU.ethg++ <--> gigabitline <--> asRouter_IT.ethg++;\n"+
			"       asRouter_EU.ethg++ <--> gigabitline <--> asRouter_FR.ethg++;\n"+
			"       asRouter_EU.ethg++ <--> gigabitline <--> asRouter_NL.ethg++;\n"+
			"       asRouter_FI.ethg++ <--> gigabitline <--> asRouter_LT.ethg++;\n"+
			"       asRouter_FR.ethg++ <--> gigabitline <--> asRouter_LU.ethg++;\n"+
			"       asRouter_FR.ethg++ <--> gigabitline <--> asRouter_GB.ethg++;\n"+
			"       asRouter_HU.ethg++ <--> gigabitline <--> asRouter_BG.ethg++;\n"+
			"       asRouter_IT.ethg++ <--> gigabitline <--> asRouter_SK.ethg++;\n"+
			"       asRouter_NO.ethg++ <--> gigabitline <--> asRouter_SE.ethg++;\n"+
			"       asRouter_TR.ethg++ <--> gigabitline <--> asRouter_BG.ethg++;\n"+
			"       asRouter_AT.ethg++ <--> gigabitline <--> asRouter_CH.ethg++;\n"+
			"       asRouter_BE.ethg++ <--> gigabitline <--> asRouter_LI.ethg++;\n"+
			"       asRouter_CD.ethg++ <--> gigabitline <--> asRouter_UG.ethg++;\n"+
			"       asRouter_CH.ethg++ <--> gigabitline <--> asRouter_IT.ethg++;\n"+
			"       asRouter_CZ.ethg++ <--> gigabitline <--> asRouter_EU.ethg++;\n"+
			"       asRouter_CZ.ethg++ <--> gigabitline <--> asRouter_IT.ethg++;\n"+
			"       asRouter_DE.ethg++ <--> gigabitline <--> asRouter_FR.ethg++;\n"+
			"       asRouter_EG.ethg++ <--> gigabitline <--> asRouter_JO.ethg++;\n"+
			"       asRouter_FR.ethg++ <--> gigabitline <--> asRouter_NL.ethg++;\n"+
			"       asRouter_GB.ethg++ <--> gigabitline <--> asRouter_NL.ethg++;\n"+
			"       asRouter_GB.ethg++ <--> gigabitline <--> asRouter_IE.ethg++;\n"+
			"       asRouter_GR.ethg++ <--> gigabitline <--> asRouter_RO.ethg++;\n"+
			"       asRouter_MD.ethg++ <--> gigabitline <--> asRouter_BG.ethg++;\n"+
			"       asRouter_PL.ethg++ <--> gigabitline <--> asRouter_LT.ethg++;\n"+
			"       asRouter_PL.ethg++ <--> gigabitline <--> asRouter_LV.ethg++;\n"+
			"       asRouter_PL.ethg++ <--> gigabitline <--> asRouter_SI.ethg++;\n"+
			"       asRouter_PL.ethg++ <--> gigabitline <--> asRouter_HR.ethg++;\n"+
			"       asRouter_SE.ethg++ <--> gigabitline <--> asRouter_LV.ethg++;\n"+
			"       asRouter_SE.ethg++ <--> gigabitline <--> asRouter_EE.ethg++;\n"+
			"       asRouter_TR.ethg++ <--> gigabitline <--> asRouter_CY.ethg++;\n"+
			"       asRouter_AZ.ethg++ <--> gigabitline <--> asRouter_IR.ethg++;\n"+
			"       asRouter_BE.ethg++ <--> gigabitline <--> asRouter_DK.ethg++;\n"+
			"       asRouter_CA.ethg++ <--> gigabitline <--> asRouter_US.ethg++;\n"+
			"       asRouter_DK.ethg++ <--> gigabitline <--> asRouter_NL.ethg++;\n"+
			"       asRouter_ES.ethg++ <--> gigabitline <--> asRouter_FR.ethg++;\n"+
			"       asRouter_HK.ethg++ <--> gigabitline <--> asRouter_TW.ethg++;\n"+
			"       asRouter_HU.ethg++ <--> gigabitline <--> asRouter_RO.ethg++;\n"+
			"       asRouter_ID.ethg++ <--> gigabitline <--> asRouter_SG.ethg++;\n"+
			"       asRouter_KE.ethg++ <--> gigabitline <--> asRouter_TZ.ethg++;\n"+
			"       asRouter_MK.ethg++ <--> gigabitline <--> asRouter_SI.ethg++;\n"+
			"       asRouter_RO.ethg++ <--> gigabitline <--> asRouter_HR.ethg++;\n"+
			"       asRouter_SG.ethg++ <--> gigabitline <--> asRouter_TH.ethg++;\n"+
			"       asRouter_DK.ethg++ <--> gigabitline <--> asRouter_AX.ethg++;\n"+
			"       asRouter_DK.ethg++ <--> gigabitline <--> asRouter_LU.ethg++;\n"+
			"       asRouter_FO.ethg++ <--> gigabitline <--> asRouter_GB.ethg++;\n"+
			"       asRouter_GB.ethg++ <--> gigabitline <--> asRouter_LU.ethg++;\n"+
			"       asRouter_KE.ethg++ <--> gigabitline <--> asRouter_RW.ethg++;\n"+
			"       asRouter_LV.ethg++ <--> gigabitline <--> asRouter_UA.ethg++;\n"+
			"       asRouter_MY.ethg++ <--> gigabitline <--> asRouter_SG.ethg++;\n"+
			"       asRouter_RO.ethg++ <--> gigabitline <--> asRouter_UA.ethg++;\n"+
			"       asRouter_RU.ethg++ <--> gigabitline <--> asRouter_UA.ethg++;\n"+
			"       asRouter_SE.ethg++ <--> gigabitline <--> asRouter_LT.ethg++;\n"+
			"       asRouter_TR.ethg++ <--> gigabitline <--> asRouter_UA.ethg++;\n"+
			"       asRouter_GR.ethg++ <--> gigabitline <--> asRouter_CY.ethg++;\n"+
			"       asRouter_HU.ethg++ <--> gigabitline <--> asRouter_LI.ethg++;\n"+
			"       asRouter_SG.ethg++ <--> gigabitline <--> asRouter_VN.ethg++;\n"+
			"       asRouter_TR.ethg++ <--> gigabitline <--> asRouter_LB.ethg++;\n"+
			"       asRouter_CN.ethg++ <--> gigabitline <--> asRouter_KR.ethg++;\n"+
			"       asRouter_FI.ethg++ <--> gigabitline <--> asRouter_SE.ethg++;\n"+
			"       asRouter_ID.ethg++ <--> gigabitline <--> asRouter_MY.ethg++;\n"+
			"       asRouter_LB.ethg++ <--> gigabitline <--> asRouter_AM.ethg++;\n"+
			"       asRouter_SE.ethg++ <--> gigabitline <--> asRouter_SK.ethg++;\n"+
			"       asRouter_UA.ethg++ <--> gigabitline <--> asRouter_BG.ethg++;\n"+
			"       asRouter_UA.ethg++ <--> gigabitline <--> asRouter_EE.ethg++;\n"+
			"       asRouter_BN.ethg++ <--> gigabitline <--> asRouter_SG.ethg++;\n"+
			"       asRouter_CN.ethg++ <--> gigabitline <--> asRouter_AP.ethg++;\n"+
			"       asRouter_GR.ethg++ <--> gigabitline <--> asRouter_UA.ethg++;\n"+
			"       asRouter_IE.ethg++ <--> gigabitline <--> asRouter_PT.ethg++;\n"+
			"       asRouter_MD.ethg++ <--> gigabitline <--> asRouter_SK.ethg++;\n"+
			"       asRouter_MD.ethg++ <--> gigabitline <--> asRouter_RU.ethg++;\n"+
			"       asRouter_US.ethg++ <--> gigabitline <--> asRouter_BM.ethg++;\n"+
			"       asRouter_FI.ethg++ <--> gigabitline <--> asRouter_NO.ethg++;\n"+
			"       asRouter_GB.ethg++ <--> gigabitline <--> asRouter_LI.ethg++;\n"+
			"       asRouter_GR.ethg++ <--> gigabitline <--> asRouter_IL.ethg++;\n"+
			"       asRouter_IE.ethg++ <--> gigabitline <--> asRouter_LU.ethg++;\n"+
			"       asRouter_JP.ethg++ <--> gigabitline <--> asRouter_KR.ethg++;\n"+
			"       asRouter_KR.ethg++ <--> gigabitline <--> asRouter_TW.ethg++;\n"+
			"       asRouter_MD.ethg++ <--> gigabitline <--> asRouter_CY.ethg++;\n"+
			"       asRouter_MD.ethg++ <--> gigabitline <--> asRouter_SI.ethg++;\n"+
			"       asRouter_ES.ethg++ <--> gigabitline <--> asRouter_IE.ethg++;\n"+
			"       asRouter_ES.ethg++ <--> gigabitline <--> asRouter_IM.ethg++;\n"+
			"       asRouter_MD.ethg++ <--> gigabitline <--> asRouter_FI.ethg++;\n"+
			"       asRouter_SK.ethg++ <--> gigabitline <--> asRouter_EE.ethg++;\n"+
			"       asRouter_TR.ethg++ <--> gigabitline <--> asRouter_SK.ethg++;\n"+
			"       asRouter_ES.ethg++ <--> gigabitline <--> asRouter_LI.ethg++;\n"+
			"       asRouter_LV.ethg++ <--> gigabitline <--> asRouter_RU.ethg++;\n"+
			"       asRouter_NI.ethg++ <--> gigabitline <--> asRouter_US.ethg++;\n"+
			"       asRouter_NO.ethg++ <--> gigabitline <--> asRouter_LV.ethg++;\n"+
			"       asRouter_RU.ethg++ <--> gigabitline <--> asRouter_GE.ethg++;\n"+
			"       asRouter_TR.ethg++ <--> gigabitline <--> asRouter_GE.ethg++;\n"+
			"       asRouter_FI.ethg++ <--> gigabitline <--> asRouter_RO.ethg++;\n"+
			"       asRouter_HK.ethg++ <--> gigabitline <--> asRouter_AP.ethg++;\n"+
			"       asRouter_TW.ethg++ <--> gigabitline <--> asRouter_CN.ethg++;\n"+
			"       asRouter_CR.ethg++ <--> gigabitline <--> asRouter_US.ethg++;\n"+
			"       asRouter_GR.ethg++ <--> gigabitline <--> asRouter_LT.ethg++;\n"+
			"       asRouter_IE.ethg++ <--> gigabitline <--> asRouter_MC.ethg++;\n"+
			"       asRouter_IE.ethg++ <--> gigabitline <--> asRouter_LI.ethg++;\n"+
			"       asRouter_IE.ethg++ <--> gigabitline <--> asRouter_GI.ethg++;\n"+
			"       asRouter_NO.ethg++ <--> gigabitline <--> asRouter_LT.ethg++;\n"+
			"       asRouter_TR.ethg++ <--> gigabitline <--> asRouter_RU.ethg++;\n"+
			"       asRouter_CN.ethg++ <--> gigabitline <--> asRouter_HK.ethg++;\n"+
			"       asRouter_FI.ethg++ <--> gigabitline <--> asRouter_BG.ethg++;\n"+
			"       asRouter_IE.ethg++ <--> gigabitline <--> asRouter_NO.ethg++;\n"+
			"       asRouter_KZ.ethg++ <--> gigabitline <--> asRouter_PK.ethg++;\n"+
			"       asRouter_RU.ethg++ <--> gigabitline <--> asRouter_AZ.ethg++;\n"+
			"       asRouter_BS.ethg++ <--> gigabitline <--> asRouter_CA.ethg++;\n"+
			"       asRouter_CA.ethg++ <--> gigabitline <--> asRouter_BM.ethg++;\n"+
			"       asRouter_GR.ethg++ <--> gigabitline <--> asRouter_LV.ethg++;\n"+
			"       asRouter_HK.ethg++ <--> gigabitline <--> asRouter_KR.ethg++;\n"+
			"       asRouter_MY.ethg++ <--> gigabitline <--> asRouter_HK.ethg++;\n"+
			"       asRouter_MY.ethg++ <--> gigabitline <--> asRouter_CN.ethg++;\n"+
			"       asRouter_BN.ethg++ <--> gigabitline <--> asRouter_TW.ethg++;\n"+
			"       asRouter_GR.ethg++ <--> gigabitline <--> asRouter_IQ.ethg++;\n"+
			"       asRouter_JP.ethg++ <--> gigabitline <--> asRouter_TW.ethg++;\n"+
			"       asRouter_TW.ethg++ <--> gigabitline <--> asRouter_VN.ethg++;\n"+
			"       asRouter_AM.ethg++ <--> gigabitline <--> asRouter_OM.ethg++;\n"+
			"       asRouter_BF.ethg++ <--> gigabitline <--> asRouter_MA.ethg++;\n"+
			"       asRouter_ES.ethg++ <--> gigabitline <--> asRouter_FO.ethg++;\n"+
			"       asRouter_ID.ethg++ <--> gigabitline <--> asRouter_VN.ethg++;\n"+
			"       asRouter_HK.ethg++ <--> gigabitline <--> asRouter_SG.ethg++;\n"+
			"       asRouter_KR.ethg++ <--> gigabitline <--> asRouter_AP.ethg++;\n"+
			"       asRouter_BS.ethg++ <--> gigabitline <--> asRouter_US.ethg++;\n"+
			"       asRouter_CN.ethg++ <--> gigabitline <--> asRouter_JP.ethg++;\n"+
			"       asRouter_GT.ethg++ <--> gigabitline <--> asRouter_US.ethg++;\n"+
			"       asRouter_KY.ethg++ <--> gigabitline <--> asRouter_US.ethg++;\n"+
			"       asRouter_MY.ethg++ <--> gigabitline <--> asRouter_TW.ethg++;\n"+
			"       asRouter_TW.ethg++ <--> gigabitline <--> asRouter_TH.ethg++;\n"+
			"       asRouter_US.ethg++ <--> gigabitline <--> asRouter_DO.ethg++;\n"+
			"       asRouter_ES.ethg++ <--> gigabitline <--> asRouter_NO.ethg++;\n"+
			"       asRouter_ID.ethg++ <--> gigabitline <--> asRouter_PH.ethg++;\n"+
			"       asRouter_AP.ethg++ <--> gigabitline <--> asRouter_PH.ethg++;\n"+
			"       asRouter_HK.ethg++ <--> gigabitline <--> asRouter_JP.ethg++;\n"+
			"       asRouter_CA.ethg++ <--> gigabitline <--> asRouter_PR.ethg++;\n"+
			"       asRouter_ID.ethg++ <--> gigabitline <--> asRouter_HK.ethg++;\n"+
			"       asRouter_IN.ethg++ <--> gigabitline <--> asRouter_SG.ethg++;\n"+
			"       asRouter_CA.ethg++ <--> gigabitline <--> asRouter_HN.ethg++;\n"+
			"       asRouter_CA.ethg++ <--> gigabitline <--> asRouter_GT.ethg++;\n"+
			"       asRouter_CN.ethg++ <--> gigabitline <--> asRouter_BD.ethg++;\n"+
			"       asRouter_NO.ethg++ <--> gigabitline <--> asRouter_PT.ethg++;\n"+
			"       asRouter_AG.ethg++ <--> gigabitline <--> asRouter_CA.ethg++;\n"+
			"       asRouter_AU.ethg++ <--> gigabitline <--> asRouter_ID.ethg++;\n"+
			"       asRouter_CA.ethg++ <--> gigabitline <--> asRouter_AN.ethg++;\n"+
			"       asRouter_MY.ethg++ <--> gigabitline <--> asRouter_AP.ethg++;\n"+
			"       asRouter_ZA.ethg++ <--> gigabitline <--> asRouter_KE.ethg++;\n"+
			"       asRouter_KR.ethg++ <--> gigabitline <--> asRouter_VN.ethg++;\n"+
			"       asRouter_KZ.ethg++ <--> gigabitline <--> asRouter_AP.ethg++;\n"+
			"       asRouter_MY.ethg++ <--> gigabitline <--> asRouter_GU.ethg++;\n"+
			"       asRouter_BN.ethg++ <--> gigabitline <--> asRouter_KR.ethg++;\n"+
			"       asRouter_ID.ethg++ <--> gigabitline <--> asRouter_IN.ethg++;\n"+
			"       asRouter_KE.ethg++ <--> gigabitline <--> asRouter_NG.ethg++;\n"+
			"       asRouter_KZ.ethg++ <--> gigabitline <--> asRouter_KW.ethg++;\n"+
			"       asRouter_KR.ethg++ <--> gigabitline <--> asRouter_TH.ethg++;\n"+
			"       asRouter_BN.ethg++ <--> gigabitline <--> asRouter_JP.ethg++;\n"+
			"       asRouter_GA.ethg++ <--> gigabitline <--> asRouter_MA.ethg++;\n"+
			"       asRouter_KZ.ethg++ <--> gigabitline <--> asRouter_IN.ethg++;\n"+
			"       asRouter_KZ.ethg++ <--> gigabitline <--> asRouter_SA.ethg++;\n"+
			"       asRouter_AU.ethg++ <--> gigabitline <--> asRouter_SG.ethg++;\n"+
			"       asRouter_ID.ethg++ <--> gigabitline <--> asRouter_AP.ethg++;\n"+
			"       asRouter_KE.ethg++ <--> gigabitline <--> asRouter_GH.ethg++;\n"+
			"       asRouter_AR.ethg++ <--> gigabitline <--> asRouter_CO.ethg++;\n"+
			"       asRouter_AU.ethg++ <--> gigabitline <--> asRouter_GU.ethg++;\n"+
			"       asRouter_JP.ethg++ <--> gigabitline <--> asRouter_VN.ethg++;\n"+
			"       asRouter_AM.ethg++ <--> gigabitline <--> asRouter_IN.ethg++;\n"+
			"       asRouter_JP.ethg++ <--> gigabitline <--> asRouter_MY.ethg++;\n"+
			"       asRouter_KZ.ethg++ <--> gigabitline <--> asRouter_CN.ethg++;\n"+
			"       asRouter_PT.ethg++ <--> gigabitline <--> asRouter_IL.ethg++;\n"+
			"       asRouter_KE.ethg++ <--> gigabitline <--> asRouter_DZ.ethg++;\n"+
			"       asRouter_JP.ethg++ <--> gigabitline <--> asRouter_TH.ethg++;\n"+
			"       asRouter_KZ.ethg++ <--> gigabitline <--> asRouter_IL.ethg++;\n"+
			"       asRouter_IL.ethg++ <--> gigabitline <--> asRouter_A2.ethg++;\n"+
			"       asRouter_KZ.ethg++ <--> gigabitline <--> asRouter_VN.ethg++;\n"+
			"       asRouter_AR.ethg++ <--> gigabitline <--> asRouter_PA.ethg++;\n"+
			"       asRouter_AM.ethg++ <--> gigabitline <--> asRouter_GI.ethg++;\n"+
			"       asRouter_KE.ethg++ <--> gigabitline <--> asRouter_SL.ethg++;\n"+
			"       asRouter_AM.ethg++ <--> gigabitline <--> asRouter_IM.ethg++;\n"+
			"       asRouter_IQ.ethg++ <--> gigabitline <--> asRouter_PT.ethg++;\n"+
			"       asRouter_AM.ethg++ <--> gigabitline <--> asRouter_FO.ethg++;\n"+
			"       asRouter_PT.ethg++ <--> gigabitline <--> asRouter_SA.ethg++;\n"+
			"       asRouter_BZ.ethg++ <--> gigabitline <--> asRouter_AR.ethg++;\n"+
			"       asRouter_PT.ethg++ <--> gigabitline <--> asRouter_KE.ethg++;\n"+
			"       asRouter_AM.ethg++ <--> gigabitline <--> asRouter_TH.ethg++;\n"+
			"       asRouter_FK.ethg++ <--> gigabitline <--> asRouter_MQ.ethg++;\n"+
			"       asRouter_AU.ethg++ <--> gigabitline <--> asRouter_AP.ethg++;\n"+
			"       asRouter_AU.ethg++ <--> gigabitline <--> asRouter_IN.ethg++;\n"+
			"       asRouter_FK.ethg++ <--> gigabitline <--> asRouter_KY.ethg++;\n"+
			"       asRouter_PT.ethg++ <--> gigabitline <--> asRouter_PK.ethg++;\n"+
			"       asRouter_FK.ethg++ <--> gigabitline <--> asRouter_BM.ethg++;\n"+
			"       asRouter_AU.ethg++ <--> gigabitline <--> asRouter_AF.ethg++;\n"+
			"       asRouter_AU.ethg++ <--> gigabitline <--> asRouter_SO.ethg++;\n"+
			"       asRouter_AU.ethg++ <--> gigabitline <--> asRouter_SA.ethg++;\n"+
			"       asRouter_AF.ethg++ <--> gigabitline <--> asRouter_MH.ethg++;\n"+
			"       asRouter_EE.ethg++ <--> gigabitline <--> asRouter_PH.ethg++;\n"+
			"       asRouter_FK.ethg++ <--> gigabitline <--> asRouter_MC.ethg++;\n"+
			"       asRouter_FK.ethg++ <--> gigabitline <--> asRouter_IL.ethg++;\n"+
			"       asRouter_FK.ethg++ <--> gigabitline <--> asRouter_AE.ethg++;\n"+
			"       asRouter_A2.ethg++ <--> gigabitline <--> asRouter_AS.ethg++;\n"+
			"       asRouter_AF.ethg++ <--> gigabitline <--> asRouter_WS.ethg++;\n"+

			"       asRouter_SD.ethg++ <--> gigabitline <--> asRouter_GB.ethg++;\n"+
			"       asRouter_RE.ethg++ <--> gigabitline <--> asRouter_FR.ethg++;\n"+
			"       asRouter_JE.ethg++ <--> gigabitline <--> asRouter_A1.ethg++;\n"+
			"       asRouter_GB.ethg++ <--> gigabitline <--> asRouter_TN.ethg++;\n"+
			"       asRouter_FM.ethg++ <--> gigabitline <--> asRouter_ID.ethg++;\n"+
			"       asRouter_VE.ethg++ <--> gigabitline <--> asRouter_EU.ethg++;\n"+
			"       asRouter_AL.ethg++ <--> gigabitline <--> asRouter_A1.ethg++;\n"+
			"       asRouter_AO.ethg++ <--> gigabitline <--> asRouter_FR.ethg++;\n"+
			"       asRouter_AW.ethg++ <--> gigabitline <--> asRouter_IE.ethg++;\n"+
			"       asRouter_BY.ethg++ <--> gigabitline <--> asRouter_US.ethg++;\n"+
			"       asRouter_PF.ethg++ <--> gigabitline <--> asRouter_US.ethg++;\n"+
			"       asRouter_RU.ethg++ <--> gigabitline <--> asRouter_KG.ethg++;\n"+
			"       asRouter_SV.ethg++ <--> gigabitline <--> asRouter_A1.ethg++;\n"+
			"       asRouter_MY.ethg++ <--> gigabitline <--> asRouter_NZ.ethg++;\n"+
			"       asRouter_JM.ethg++ <--> gigabitline <--> asRouter_US.ethg++;\n"+
			"       asRouter_KM.ethg++ <--> gigabitline <--> asRouter_US.ethg++;\n"+
			"       asRouter_UZ.ethg++ <--> gigabitline <--> asRouter_RU.ethg++;\n"+
			"       asRouter_BE.ethg++ <--> gigabitline <--> asRouter_MU.ethg++;\n"+
			"       asRouter_MT.ethg++ <--> gigabitline <--> asRouter_GB.ethg++;\n"+
			"       asRouter_PY.ethg++ <--> gigabitline <--> asRouter_FR.ethg++;\n"+
			"       asRouter_BH.ethg++ <--> gigabitline <--> asRouter_GB.ethg++;\n"+
			"       asRouter_MX.ethg++ <--> gigabitline <--> asRouter_US.ethg++;\n"+
			"       asRouter_MG.ethg++ <--> gigabitline <--> asRouter_SE.ethg++;\n"+
			"       asRouter_CL.ethg++ <--> gigabitline <--> asRouter_CA.ethg++;\n"+
			"       asRouter_VI.ethg++ <--> gigabitline <--> asRouter_US.ethg++;\n"+
			"       asRouter_KZ.ethg++ <--> gigabitline <--> asRouter_EG.ethg++;\n"+
			"       asRouter_TT.ethg++ <--> gigabitline <--> asRouter_CA.ethg++;\n"+
			"       asRouter_IS.ethg++ <--> gigabitline <--> asRouter_IE.ethg++;\n"+
			"       asRouter_BB.ethg++ <--> gigabitline <--> asRouter_IE.ethg++;\n"+
			"       asRouter_CD.ethg++ <--> gigabitline <--> asRouter_A1.ethg++;\n"+
			"       asRouter_A1.ethg++ <--> gigabitline <--> asRouter_US.ethg++;\n"+
			"       asRouter_ES.ethg++ <--> gigabitline <--> asRouter_SC.ethg++;\n"+
			"       asRouter_A1.ethg++ <--> gigabitline <--> asRouter_VG.ethg++;\n"+
			"       asRouter_A1.ethg++ <--> gigabitline <--> asRouter_BO.ethg++;\n"+
			"       asRouter_A1.ethg++ <--> gigabitline <--> asRouter_KH.ethg++;\n"+
			"       asRouter_FR.ethg++ <--> gigabitline <--> asRouter_MZ.ethg++;\n"+
			"       asRouter_RS.ethg++ <--> gigabitline <--> asRouter_BG.ethg++;\n"+
			"       asRouter_MA.ethg++ <--> gigabitline <--> asRouter_ZA.ethg++;\n"+
			"       asRouter_CO.ethg++ <--> gigabitline <--> asRouter_MX.ethg++;\n";
		return routers;
	}

	public static String getClientsConnections() {
		return clientsConnections;
	}

	public static void setClientsConnections(String clientsConnections) {
		CDNLabConfig.clientsConnections = clientsConnections;
	}

	public static List<Server> getSurrogateServersList() {
		return surrogateServersList;
	}

	public static void setSurrogateServersList(List<Server> surrogateServersList) {
		CDNLabConfig.surrogateServersList = surrogateServersList;
	}

	public static List<Client> getClientsList() {
		return clientsList;
	}

	public static void setClientsList(List<Client> clientsList) {
		CDNLabConfig.clientsList = clientsList;
	}
}
