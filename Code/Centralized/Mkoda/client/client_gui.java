/* MKoda 
 * Peer to Peer File Sharing System with a search engine
 * Please follow the readme file for instructions on how to setup and run the system 
 * Written by : Arun Natarajan, Manikandan Sivanesan, Koushik Krishnakumar, Dinesh Radha Kirshan
*/
package client;


import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.UIManager;
import net.sbbi.upnp.impls.InternetGatewayDevice;
import net.sbbi.upnp.messages.UPNPResponseException;
import java.awt.Dimension;
import java.awt.Toolkit;

import packetformat.*;
import trigest.trigest;
import general.*;

/**
 * @author
 * 
 */

public class client_gui extends JFrame implements ActionListener, Runnable {
	public JLabel resolver, local, filepath, dnsserv, pwd, resolver1, dnsserv1,
			dnsserv2, page_display, Username_Reg, Pwd_Reg, Email_Reg, Pub_file,
			Pub_file_size, Pub_abstract, Pub_file_label, Pwd_Reg_Confirm,
			server_info;

	public JTextField localtxt, urltxt, filearea, pwdtxt, localtxt_home,
			Username_Reg_txt, Pwd_Reg_txt, Email_Reg_txt, Pwd_Reg_txt_confirm;

	public JTextArea[] sresult_dis = new JTextArea[10];

	public JTextArea Pub_abstract_area, Log_area;

	public JButton exit, submit, screen2, search_home, exit_home, publish_home,
			previous_page, next_page, Register, Register_login, Pub_ok,
			unPublish, Remove_files, cancel_unpublish, back_Reg_frame,
			Pub_cancel, log, Log_exit;

	public JButton[] sresult_but = new JButton[10];

	public JFrame frame, frame1, Reg_frame, Pub_frame, unPublish_Frame,
			Log_frame;

	public JList list;

	public DefaultListModel listModel;

	logMessages logWin;

	Map<String, String> fileHash = new LinkedHashMap<String, String>();

	public InetAddress myAddr, servAddr;

	public search_result[] sresult = new search_result[100];

	public int sresults = 0, sresults_sh = 0;

	public int download_reply = 0;

	public String myname;

	public File publish_file;

	String[] farray;

	int present_file_count;

	public int myPort, servPort;

	public File SHA_path_file;

	public int file_inconsistent = 0;

	public boolean mapped = false;

	public InternetGatewayDevice testIGD;

	public Properties prop = new Properties();

	public Thread thread;

	public genfunc gfns = new genfunc();

	DatagramSocket pocket;

	/*
	 * Type : constructor
	 * 
	 */
	public client_gui() throws Exception {
		super();

		// Loads the Server address and server port from /Resolver.Properties
		// file
		prop.load(this.getClass().getResourceAsStream("/Resolver.Properties"));

		// Generates a random number and chooses that port for client
		Random randomGenerator = new Random();
		int randomInt;
		do {
			randomInt = randomGenerator.nextInt(48000);
		} while (randomInt < 33000);

		// Local host address being stored in the variable myaddr
		myinet4addr getmyaddr = new myinet4addr();
		myAddr = getmyaddr.getMy4Ia();

		myPort = randomInt;

		getDesign();
		logWin.area.append("Created Gui Interfaces. " + "\n\n");

		create_upnp_portmapping();

		servAddr = InetAddress.getByAddress(gfns.getIpAsArrayOfByte(prop
				.getProperty("Server")));
		servPort = Integer.parseInt(prop.getProperty("Server_Port"));

		System.out.println("Using my IP address " + myAddr.getHostAddress()
				+ " Port " + myPort + " and Server IP address "
				+ servAddr.getHostAddress() + " Port " + servPort);

		logWin.area.append("Using my IP address " + myAddr.getHostAddress()
				+ " Port " + myPort + "\n");
		logWin.area.append("Server IP address " + servAddr.getHostAddress()
				+ " Port " + servPort);

		server_info.setText("            Server : " + servAddr.getHostAddress()
				+ " (" + servPort + ")");
		submit.setEnabled(true);

		// TCP server started as separate thread and will start listening for
		// any incoming connections
		tcp_server tcp_server = new tcp_server(this);
		new Thread(tcp_server).start();

		// Starts the UDP server and listens for the UDP packets as a a separate
		// thread
		open_udp_port();
		thread = new Thread(this);
		thread.start();
	}

	/*
	 * Function Name: create_upnp_portmapping() Description : This function
	 * creates a mapping entry for the local host IP address and the local port
	 * used in the routing device using the UPNP protocol Param : Null Returns :
	 * Null
	 */private void create_upnp_portmapping() {
		int discoveryTimeout = 2000; // 5 secs to receive a response from
										// devices
		try {
			InternetGatewayDevice[] IGDs = InternetGatewayDevice
					.getDevices(discoveryTimeout);
			if (IGDs != null) {
				// let's the the first device found
				testIGD = IGDs[0];
				String localHostIP = myAddr.getHostAddress();
				System.out.println("Found device " + localHostIP + " "
						+ testIGD.getIGDRootDevice().getModelName());
				logWin.area.append("Found device " + localHostIP + " "
						+ testIGD.getIGDRootDevice().getModelName() + "\n");
				// now let's open the port
				// localHostIP="192.168.0.199";
				// we assume that localHostIP is something else than 127.0.0.1
				mapped = testIGD.addPortMapping("dgoogle_pm", null, myPort,
						myPort, localHostIP, 0, "TCP");

				mapped = testIGD.addPortMapping("dgoogle_pm", null, myPort,
						myPort, localHostIP, 0, "UDP");

				if (mapped) {
					logWin.area.append("Port " + myPort + " mapped to "
							+ localHostIP + " using uPNP Protocol\n");
				}
			}
		} catch (IOException ex) {
			// some IO Exception occured during communication with device
		} catch (UPNPResponseException respEx) {
			// oups the IGD did not like something !!
		}

	}

	/*
	 * Function Name: delete_upnp_portmapping() Description : This function
	 * deletes the mapping entry for the local host IP address and the local
	 * port used in the routing device using the UPNP protocol Param : Null
	 * Returns : Null
	 */
	private void delete_upnp_portmapping() throws Exception {
		if (mapped) {
			testIGD.deletePortMapping(null, myPort, "TCP");
			testIGD.deletePortMapping(null, myPort, "UDP");
			System.out.println("Port " + myPort + " unmapped");
		}

	}

	/*
	 * Function Name: load_file_hash() Description : This function checks for
	 * the consistency of the published files. It will check files published by
	 * the user from the SHA_path_[username] file and check the consistency of
	 * each files.If any inconsistency found the file entry will be removed from
	 * the SHA_path_[username] file and updated SHA_path_[username] file will be
	 * loaded. Param : Null Returns : Null
	 */
	private void load_file_hash() throws Exception {
		logWin.area.append("\n\n" + "Checking local file " + SHA_path_file
				+ "'s consistency");
		BufferedReader triRead1 = new BufferedReader(new FileReader(
				SHA_path_file));
		String line = null;
		fileHash.clear();

		while ((line = triRead1.readLine()) != null) {
			// Reading each entry of the SHA_path_file
			String SHA = line.substring(0, 40);
			String Path = line.substring(40);
			if (pubfile_consistent_check(SHA, Path)) {
				// System.out.println((Path + "is consistent"));

				// Consistent file added to a Hash Map
				fileHash.put(line.substring(0, 40), line.substring(40));
			} else {
				logWin.area.append("\n" + "File \"" + Path
						+ "\" Inconsistent -- Removed");
			}
		}

		triRead1.close();

		FileOutputStream erasor = new FileOutputStream(SHA_path_file);
		erasor.write((new String()).getBytes());
		erasor.close();

		FileWriter fw = new FileWriter(SHA_path_file, true);

		// Writes the updated HASH Map to the new SHA_path_file after removing
		// the inconsistent entries

		for (Iterator it = fileHash.entrySet().iterator(); it.hasNext();) {

			Map.Entry e = (Map.Entry) it.next();
			fw.write(e.getKey().toString());// appends the string to the file
			fw.write(e.getValue().toString());
			fw.write("\n");

		}
		logWin.area.append("\n" + "Local publish file " + SHA_path_file
				+ " consistent");
		fw.close();

	}

	/*
	 * Function Name: pubfile_consistent_check() Description : This function
	 * checks the consistency of each file entry by opening the file using the
	 * path and determining the new SHA for the file and compares it with the
	 * old SHA.If they are not equal inconsistency found and will return a false
	 * value.And if both SHA are equal it will return a true value. Param SHA :
	 * file identifier param Path: file path Returns : Consistent or
	 * Inconsistent
	 */

	private boolean pubfile_consistent_check(String SHA, String Path)
			throws Exception {

		// Message Digest
		MessageDigest md = MessageDigest.getInstance("SHA-1");
		FileInputStream unid;
		try {

			// Opening the file using the Path
			unid = new FileInputStream((new File(Path)));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			return false;
		}
		byte[] file_cont = new byte[unid.available()];
		unid.read(file_cont);

		// Determining the SHA message digest for the file
		byte[] mdigest = md.digest(file_cont);

		// Comparing the old SHA with the newly determined SHA
		if (Arrays.equals(gfns.hexStringToByteArray(SHA), mdigest)) {
			return true;
		}

		else
			return false;

	}

	/*
	 * Function Name: open_udp_port()() Description : This function opens the
	 * UDP port and listens for any incoming UDP packets. Param : Null Returns :
	 * Null
	 */

	public void open_udp_port() {

		try {
			// int servPort =
			// Integer.parseInt(prop.getProperty("Client_UDP_Port"));

			pocket = new DatagramSocket(myPort);
			// Creating a packet to hold the incoming UDP packet
			// DatagramPacket packet=new DatagramPacket(new byte[256],256);
			logWin.area.append("\n\n" + "Opened UDP Port on seperate thread");
		} catch (IOException ioe) {
			System.out.println("Error:" + ioe);
		}

	}

	/*
	 * Function Name: run() Description : This function implements the runnable
	 * interface for the UDP.Processes the incoming UDP packet. Param : Null
	 * Returns : Null
	 */

	public void run() {
		DatagramPacket packet = new DatagramPacket(new byte[256], 256);
		while (true) {
			try {
				if (pocket.isClosed()) {
					break;
				}
				pocket.receive(packet);
				// System.out.println("packet received");
				process_pocket(packet);
				if (pocket.isClosed()) {
					break;
				}
			} catch (IOException e) {
				System.out.println(e);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		// pocket.close();
	}

	/*
	 * Function Name: process_pocket() Description : This function processes the
	 * UDP packet based on its packet type. Param packet : UDP packet Returns :
	 * Null
	 */
	private void process_pocket(DatagramPacket packet) throws Exception {
		pack udp_pack = new pack(packet.getData(), packet.getLength(), packet
				.getAddress(), packet.getPort());

		switch (udp_pack.getPkttype()) {

		// Packet type for login reply
		case 81:

			// Check whether the login is successful
			if (login_reply(udp_pack)) {
				logWin.area.append("\n\n"
						+ "Successfully Logged into server with username -- "
						+ myname);
				dnsserv1.setText("Welcome " + myname);
				String sha_pf = "SHA_Path" + "_" + myname;
				SHA_path_file = new File(sha_pf);

				/*
				 * Creates a SHA_path file for the user if it doesnt exist to
				 * store the mapping between the SHA for the file and its path
				 */
				if (!SHA_path_file.exists()) {
					logWin.area.append("\n\n" + "Local publish file \""
							+ SHA_path_file
							+ "\" doesnot exist. Creating new File.");
					SHA_path_file.createNewFile();
				}

				// Loads the SHA_path file and publish only the consistent files
				load_file_hash();
				publish_consistent_SHA();

				// Starts the client keepalive thread
				client_keepAlive cka = new client_keepAlive(this);

				open_frame1();
				new Thread(cka).start();
			} else {
				JOptionPane.showMessageDialog(null,
						"Check Username and Password", "Error",
						JOptionPane.INFORMATION_MESSAGE);
			}
			break;

		// Packet type for download request from other peer
		case 21: {
			// System.out.println("Download request received ");

			// Sends the reply for a download request
			send_dload_reply(udp_pack);

		}
			break;

		// Packet type to acknowledge the successful download
		case 22: {
			download_reply = udp_pack.data[udp_pack.getPaylength() - 1];
			// System.out.println("Download request succesfull with reply " +
			// download_reply);
		}
			break;

		// Packet type for reply of the registration request
		case 6: {
			// Checks whether the data contains success message(255)
			if (udp_pack.getData()[udp_pack.getData().length - 1] == 1) {
				JOptionPane.showMessageDialog(null, "Registered",
						"New User Registration",
						JOptionPane.INFORMATION_MESSAGE);
				Reg_frame.setVisible(false);
				frame.setVisible(true);
			}

			// Clears the entered text in the text boxes
			else {
				JOptionPane.showMessageDialog(null,
						"Registration failed - User Name already exists",
						"New User Registration",
						JOptionPane.INFORMATION_MESSAGE);
				Username_Reg_txt.setText("");
				Pwd_Reg_txt.setText("");
				Email_Reg_txt.setText("");
			}
		}
			break;

		// Packet type when the active status of the client becomes invalid and
		// the client is asked to relogin
		case 99: {
			logWin.area.append("\n\n" + "Some error in login Exiting...");
			JOptionPane.showMessageDialog(null,
					"Please Login again - Exiting Now", "Login timed out",
					JOptionPane.INFORMATION_MESSAGE);
			client_exit();
		}
			break;
		}

	}

	/*
	 * Function Name: publish_consistent_SHA() Description : This function loads
	 * the updated SHA_path file hash after checking consistency.It establishes
	 * a TCP connection with the server and sends it the server. Param : Null
	 * Returns : Null
	 */

	private void publish_consistent_SHA() throws IOException {

		Socket clientSocket = new Socket(servAddr, servPort);
		// int cliPort = clientSocket.getLocalPort();
		OutputStream os = clientSocket.getOutputStream();
		InputStream in = clientSocket.getInputStream();
		int index = 0;

		byte[] payload = new byte[4 + fileHash.size() * 20 + myname.length()];

		// Adding the length of username to the payload
		System.arraycopy(gfns.convIntBary_2(myname.length()), 0, payload,
				index, 2);
		index += 2;

		// Adding the user to the payload
		System.arraycopy(myname.getBytes(), 0, payload, index, myname.length());
		index += myname.length();

		// Adding the number of file to be published to the payload
		byte[] record_no = new byte[2];

		record_no = gfns.convIntBary_2(fileHash.size());
		System.arraycopy(record_no, 0, payload, index, 2);
		index += 2;

		// Adding the files to be published to the payload
		for (Iterator it = fileHash.entrySet().iterator(); it.hasNext();) {

			Map.Entry e = (Map.Entry) it.next();
			System.arraycopy(gfns.hexStringToByteArray(e.getKey().toString()),
					0, payload, index, 20);
			index += 20;

		}

		// Constructing a stream with the header and the payload
		pack tcp_stream = new pack((byte) 24, (int) 5678, (byte) 16, myAddr,
				myPort, (int) payload.length, payload);

		// Sending the stream by establishing a TCP connection
		if (clientSocket.isConnected()) {
			os.write(tcp_stream.getPacket());
		}

		if (in.read() == 1) {

			logWin.area.append("\n"
					+ "Published files consistency established with server ");
		}

		else {
			logWin.area.append("\n"
					+ "Error in Published files consistency with server ");
		}

		os.close();
		in.close();
		clientSocket.close();

	}

	/*
	 * Function Name : send_dload_reply() Description : This function checks
	 * whether it contains requested filedigest and if it has and if it is
	 * consistent ,it sends a positive reply that allows the peer to download
	 * the file. If it does not contain the requested file digest it sends a
	 * negative reply. Param udppack : Udp packet Returns : Null
	 */

	private void send_dload_reply(pack udpPack) throws Exception {
		// gfns.printbary(udpPack.getData());
		byte res = 1;

		// Checks whether it contains the requested file digest in its hash
		if (fileHash.containsKey(gfns.ByteArraytohexString(udpPack.getData()))) {
			byte[] reqMsgDst = udpPack.getData();
			String filepath = fileHash.get(gfns.ByteArraytohexString(udpPack
					.getData()));

			// Checking for consistency
			if (file_consistent(reqMsgDst, filepath)) {

				res = 2;
				logWin.area.append("\n\n"
						+ "Responded positively for file download request "
						+ filepath + " from IP " + udpPack.getIP());
			}

			else {
				file_inconsistent = 1;
				logWin.area.append("\n\n" + filepath
						+ " -- File inconsistent, please unpublish ");
			}
		}

		// InetAddress myAddr=InetAddress.getLocalHost();
		// int servPort = Integer.parseInt(prop.getProperty("Client_UDP_Port"));

		InetAddress cliAddr = udpPack.getIP();
		int cliPort = udpPack.getPort_no();
		byte[] payload = new byte[] { 00, 01, res };
		pack udp_pack = new pack((byte) 22, (int) 1234, (byte) 16, myAddr,
				myPort, payload.length, payload);

		// Constructing a UDP packet and sending along with the reply in payload
		DatagramPacket pack = new DatagramPacket(udp_pack.getPacket(), udp_pack
				.getPacket().length, cliAddr, cliPort);
		pocket.send(pack);

	}

	/*
	 * Function Name : file_consistent() Description : This function computes
	 * the SHA for the file using the given file path and then compares it with
	 * the given file SHA identifier. Param reqMsgDst : file identifier in byte
	 * array format param filepath : path of the file Returns : consistent or
	 * inconsistent
	 */
	private boolean file_consistent(byte[] reqMsgDst, String filepath)
			throws Exception {

		File file = new File(filepath);

		MessageDigest md = MessageDigest.getInstance("SHA-1");

		// Reading the file using the path
		FileInputStream unid = new FileInputStream(file);
		byte[] file_cont = new byte[unid.available()];
		unid.read(file_cont);
		// Computing the SHA ie file identifier for the read file
		byte[] mdigest = md.digest(file_cont);

		// Comparing if they are equal
		if (Arrays.equals(mdigest, reqMsgDst)) {
			return true;
		}

		else {
			return false;
		}

	}

	/*
	 * Function Name: login_reply() Description : This function returns true or
	 * false depending on whether the packet payloads contains byte equiavalent
	 * of value 1 Param udpPack: UDP packet Returns : success or failure
	 */

	private boolean login_reply(pack udpPack) {

		boolean blnResult = false;

		if (udpPack.data[udpPack.getPaylength() - 1] == (byte) 1)
			blnResult = true;

		return blnResult;
	}

	/*
	 * Function Name: getDesign() Description : This function designs the GUI
	 * part of the system,adding components along with an action listener.
	 * 
	 * Param : Null Returns : Null
	 */

	public void getDesign() {
		String inf = "com.sun.java.swing.plaf.windows.WindowsLookAndFeel";
		try {
			UIManager.setLookAndFeel(inf);
		} catch (Exception e) {

		}

		frame = new JFrame("USER LOGIN SCREEN");
		frame.setLayout(new GridLayout(1, 2));

		JPanel panel = new JPanel();
		panel.setLayout(null);
		panel.setBorder(BorderFactory.createTitledBorder("USER LOGIN"));
		resolver = new JLabel("User Name:");
		resolver.setFont(new Font("TimesNewRoman", Font.CENTER_BASELINE, 12));
		resolver.setBounds(70, 110, 100, 20);
		panel.add(resolver);

		dnsserv = new JLabel("MKoda Search");
		dnsserv.setFont(new Font("TimesNewRoman", Font.CENTER_BASELINE, 18));
		dnsserv.setBounds(200, 60, 180, 20);
		panel.add(dnsserv);

		localtxt = new JTextField(30);
		// localtxt.setFont(new Font("Tw Cen MT",Font.CENTER_BASELINE,14));
		localtxt.setBounds(210, 110, 150, 20);
		panel.add(localtxt);

		pwd = new JLabel("Password:");
		pwd.setFont(new Font("TimesNewRoman", Font.CENTER_BASELINE, 12));
		pwd.setBounds(70, 180, 100, 20);
		panel.add(pwd);

		pwdtxt = new JPasswordField(15);
		pwdtxt.setBounds(210, 180, 150, 20);
		panel.add(pwdtxt);

		server_info = new JLabel("Resolving Server IP/NAT.... Please wait...");
		server_info
				.setFont(new Font("TimesNewRoman", Font.CENTER_BASELINE, 11));
		server_info.setBounds(240, 20, 400, 20);
		panel.add(server_info);

		submit = new JButton("Log in");
		submit.setBounds(70, 280, 10, 20);
		panel.add(submit);
		submit.setEnabled(false);

		exit = new JButton("Exit");
		exit.setBounds(370, 280, 100, 20);
		panel.add(exit);

		screen2 = new JButton("Screen2");
		screen2.setBounds(300, 300, 75, 20);
		panel.add(screen2);
		screen2.setVisible(false);

		Register_login = new JButton("New User");
		Register_login.setBounds(220, 280, 120, 20);
		panel.add(Register_login);

		frame.add(panel);

		frame.setSize(520, 350);

		Toolkit toolkit = getToolkit();
		Dimension size = toolkit.getScreenSize();
		frame.setLocation(size.width / 4, size.height / 4);

		frame.setVisible(true);
		submit.addActionListener(this);
		exit.addActionListener(this);
		screen2.addActionListener(this);
		Register_login.addActionListener(this);

		// second frame

		frame1 = new JFrame("MKoda - Search engine");
		frame1.setLayout(new GridLayout(1, 2));
		frame1.setLocation(size.width / 5 - getWidth() / 5, size.height / 15
				- getHeight() / 15);

		JPanel panel1 = new JPanel();
		panel1.setLayout(null);
		panel1.setBorder(BorderFactory.createTitledBorder("Search Files"));
		resolver1 = new JLabel("Enter the text to be searched:");
		resolver1.setFont(new Font("TimesNewRoman", Font.CENTER_BASELINE, 12));
		resolver1.setBounds(50, 80, 230, 20);
		panel1.add(resolver1);

		// Adding to second frame

		dnsserv1 = new JLabel("Search");
		dnsserv1.setFont(new Font("TimesNewRoman", Font.CENTER_BASELINE, 18));
		dnsserv1.setBounds(300, 30, 180, 20);
		panel1.add(dnsserv1);

		localtxt_home = new JTextField(40);
		localtxt_home.setBounds(300, 80, 150, 20);
		panel1.add(localtxt_home);

		search_home = new JButton("Search");
		search_home.setBounds(500, 80, 120, 20);
		panel1.add(search_home);

		previous_page = new JButton("Previous");
		previous_page.setBounds(50, 115, 120, 20);
		panel1.add(previous_page);
		previous_page.setEnabled(false);

		page_display = new JLabel("");
		page_display
				.setFont(new Font("TimesNewRoman", Font.CENTER_BASELINE, 12));
		page_display.setBounds(220, 115, 100, 20);
		panel1.add(page_display);
		page_display.setEnabled(true);

		next_page = new JButton("Next");
		next_page.setBounds(330, 115, 120, 20);
		panel1.add(next_page);
		next_page.setEnabled(false);

		// for providing search results
		for (int i = 0; i < 10; i++) {
			sresult_dis[i] = new JTextArea();
			sresult_dis[i].setBounds(50, 150 + i * 40, 400, 35);
			panel1.add(sresult_dis[i]);
			sresult_dis[i].setEditable(false);

			sresult_but[i] = new JButton("Download");
			sresult_but[i].setBounds(500, 160 + i * 40, 120, 20);
			panel1.add(sresult_but[i]);
			sresult_but[i].setEnabled(false);
		}

		publish_home = new JButton("Publish");
		publish_home.setBounds(50, 570, 120, 20);
		panel1.add(publish_home);

		exit_home = new JButton("Log Out");
		exit_home.setBounds(500, 570, 120, 20);
		panel1.add(exit_home);

		log = new JButton("Messages");
		log.setBounds(350, 570, 120, 20);
		panel1.add(log);
		log.addActionListener(this);

		unPublish = new JButton("UnPublish");
		unPublish.setBounds(200, 570, 120, 20);
		panel1.add(unPublish);

		// //Log frame
		logWin = new logMessages();
		logWin.frame.setLocation(size.width / 4, size.height / 4);

		// Log_frame = new JFrame("Logs");
		// Log_frame.setLayout(new GridLayout(1,2));
		//		
		// JPanel Log_panel = new JPanel();
		// Log_panel.setLayout(null);
		// Log_panel.setBorder(BorderFactory.createTitledBorder("Log Panel"));
		// Log_frame.setLocation(size.width/4 , size.height/4 );
		//		
		// Log_area = new JTextArea("h");
		// Log_area.setBounds(20, 20, 540, 200);
		// Log_area.setEditable(false);
		// JScrollPane Log_scroll = new JScrollPane(Log_area);
		// Log_panel.add(Log_area);
		// Log_panel.add(Log_scroll);
		// Log_frame.add(Log_panel);
		//		
		// Log_frame.setSize(600,300);
		// //Log_frame.setVisible(true);
		// //Log_panel.add(Log_area);
		//
		// Log_exit = new JButton("Close");
		// Log_exit.setBounds(50, 310, 120,20);
		// logWin.panel.add(Log_exit);
		// Log_exit.addActionListener(this);

		// Registration Frame

		Reg_frame = new JFrame("Registration Screen");
		Reg_frame.setLayout(new GridLayout(1, 2));
		JPanel panel2 = new JPanel();
		panel2.setLayout(null);
		panel2
				.setBorder(BorderFactory
						.createTitledBorder("Registration Panel"));
		Reg_frame.setLocation(size.width / 4, size.height / 4);

		Username_Reg = new JLabel("User Name:");
		Username_Reg
				.setFont(new Font("TimesNewRoman", Font.CENTER_BASELINE, 12));
		Username_Reg.setBounds(90, 100, 100, 20);
		panel2.add(Username_Reg);

		Username_Reg_txt = new JTextField(30);
		Username_Reg_txt.setBounds(230, 100, 150, 20);
		panel2.add(Username_Reg_txt);

		Pwd_Reg = new JLabel("Password:");
		Pwd_Reg.setFont(new Font("TimesNewRoman", Font.CENTER_BASELINE, 12));
		Pwd_Reg.setBounds(90, 160, 100, 20);
		panel2.add(Pwd_Reg);

		Pwd_Reg_Confirm = new JLabel("Confirm Password:");
		Pwd_Reg_Confirm.setFont(new Font("TimesNewRoman", Font.CENTER_BASELINE,
				12));
		Pwd_Reg_Confirm.setBounds(90, 220, 150, 20);
		panel2.add(Pwd_Reg_Confirm);

		Pwd_Reg_txt = new JPasswordField(15);
		Pwd_Reg_txt.setBounds(230, 160, 150, 20);
		panel2.add(Pwd_Reg_txt);

		Pwd_Reg_txt_confirm = new JPasswordField(15);
		Pwd_Reg_txt_confirm.setBounds(230, 220, 150, 20);
		panel2.add(Pwd_Reg_txt_confirm);

		Email_Reg = new JLabel("Email ID:");
		Email_Reg.setFont(new Font("TimesNewRoman", Font.CENTER_BASELINE, 12));
		Email_Reg.setBounds(90, 280, 100, 20);
		panel2.add(Email_Reg);

		Email_Reg_txt = new JTextField(30);
		Email_Reg_txt.setBounds(230, 280, 150, 20);
		panel2.add(Email_Reg_txt);

		dnsserv2 = new JLabel("Register New User");
		dnsserv2.setFont(new Font("TimesNewRoman", Font.CENTER_BASELINE, 18));
		dnsserv2.setBounds(150, 30, 300, 50);
		panel2.add(dnsserv2);

		Register = new JButton("Register");
		Register.setBounds(90, 350, 120, 20);
		panel2.add(Register);
		Register.addActionListener(this);

		back_Reg_frame = new JButton("Cancel");
		back_Reg_frame.setBounds(260, 350, 120, 20);
		panel2.add(back_Reg_frame);
		back_Reg_frame.addActionListener(this);

		Reg_frame.add(panel2);
		Reg_frame.setSize(500, 500);
		Reg_frame.setVisible(false);

		// For Showing Published Data

		Pub_frame = new JFrame("Publish File");
		Pub_frame.setLayout(new GridLayout(1, 2));
		JPanel Pub_panel = new JPanel();
		Pub_panel.setLayout(null);
		Pub_panel.setBorder(BorderFactory.createTitledBorder("Publish File"));
		Pub_frame.setLocation(size.width / 4, size.height / 8);

		// Pub_file = new JLabel("File Name:");
		// Pub_file.setFont(new Font("TimesNewRoman",Font.CENTER_BASELINE,12));
		// Pub_file.setBounds(70,80,100,20);
		// Pub_panel.add(Pub_file);

		Pub_file_size = new JLabel("...");
		Pub_file_size.setFont(new Font("TimesNewRoman", Font.CENTER_BASELINE,
				12));
		Pub_file_size.setBounds(70, 80, 500, 20);
		Pub_panel.add(Pub_file_size);

		Pub_file_label = new JLabel(" ");
		Pub_file_label.setBounds(70, 110, 450, 20);
		Pub_panel.add(Pub_file_label);

		Pub_abstract = new JLabel("Abstract / Comments :");
		Pub_abstract
				.setFont(new Font("TimesNewRoman", Font.CENTER_BASELINE, 12));
		Pub_abstract.setBounds(70, 140, 300, 20);
		Pub_panel.add(Pub_abstract);

		Pub_abstract_area = new JTextArea();
		Pub_abstract_area.setFont(new Font("TimesNewRoman",
				Font.CENTER_BASELINE, 12));
		Pub_abstract_area.setBounds(70, 160, 360, 300);
		Pub_panel.add(Pub_abstract_area);

		Pub_ok = new JButton("Ok");
		Pub_ok.setBounds(200, 490, 100, 20);
		Pub_panel.add(Pub_ok);
		Pub_ok.addActionListener(this);

		Pub_cancel = new JButton("Cancel");
		Pub_cancel.setBounds(330, 490, 100, 20);
		Pub_panel.add(Pub_cancel);
		Pub_cancel.addActionListener(this);

		Pub_frame.add(Pub_panel);
		Pub_frame.setSize(600, 600);
		Pub_frame.setVisible(false);

		// UnPublish Frame
		unPublish_Frame = new JFrame("UnPublish");
		unPublish_Frame.setLayout(new GridLayout(1, 2));
		JPanel unPublish_panel = new JPanel();
		unPublish_panel.setLayout(null);
		unPublish_panel.setBorder(BorderFactory
				.createTitledBorder("UnPublish Files"));
		unPublish_Frame.setLocation(size.width / 4, size.height / 8);

		Remove_files = new JButton("Remove Files");
		Remove_files.setBounds(300, 50, 140, 20);
		unPublish_panel.add(Remove_files);

		cancel_unpublish = new JButton("Cancel");
		cancel_unpublish.setBounds(300, 530, 140, 20);
		unPublish_panel.add(cancel_unpublish);

		// JCheckBox j1 = new JCheckBox();
		// j1.setBounds(50, 100, 50, 50);
		// unPublish_panel.add(j1);

		listModel = new DefaultListModel();

		list = new JList(listModel);
		// list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setLayoutOrientation(JList.VERTICAL);
		list.setVisibleRowCount(-1);
		list.setBounds(50, 50, 200, 500);

		JScrollPane listScroller = new JScrollPane(list);
		listScroller.setPreferredSize(new Dimension(250, 80));
		unPublish_panel.add(list);

		unPublish_Frame.add(unPublish_panel);
		unPublish_Frame.setSize(500, 600);
		unPublish_Frame.setVisible(false);

		Remove_files.addActionListener(this);
		cancel_unpublish.addActionListener(this);

		search_home.addActionListener(this);
		exit_home.addActionListener(this);
		publish_home.addActionListener(this);
		next_page.addActionListener(this);
		previous_page.addActionListener(this);
		unPublish.addActionListener(this);

		for (int i = 0; i < 10; i++) {
			sresult_but[i].addActionListener(this);
		}

		frame1.add(panel1);
		frame1.setSize(700, 650);
		frame1.setVisible(false);
	}

	/*
	 * Function Name: actionPerformed() Description : This function implements
	 * the ActionListener Interface for each components in the frame
	 * 
	 * Param : ActionEvent Returns : Null
	 */

	public void actionPerformed(ActionEvent ae) {

		// Action to be performed for the exit button
		if (ae.getSource() == exit) {
			// System.exit(0);
			try {

				// Removes the IP address and port mapping from the routing
				// device
				delete_upnp_portmapping();
				// Exit the system
				System.exit(0);
				// client_exit();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		else if (ae.getSource() == screen2) {
			unPublish_Frame.setVisible(true);
			// open_frame1();
		}

		// Action to be performed for the submit button
		else if (ae.getSource() == submit) {

			// Retrives the user name and password from the login textboxes and
			// send them
			String username = localtxt.getText();
			String password = pwdtxt.getText();
			myname = username;
			try {
				send_login(username, password);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		// Action to be performed for the search button
		else if (ae.getSource() == search_home) {
			// Retrieve the text from the text box
			String txtsearch = localtxt_home.getText();

			// If it is empty text box ask to enter some text
			if (txtsearch.length() == 0) {
				JOptionPane.showMessageDialog(null,
						"Enter some text to search", "Search",
						JOptionPane.INFORMATION_MESSAGE);
				return;
			}

			// Clean the results and send the entered text query
			try {
				clean_results();
				send_txtsearch(txtsearch);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			show_results(1);

		}

		// 0-> current page 1->next page -1-> previous page
		// Action to be performed for the next page button
		else if (ae.getSource() == next_page) {
			logWin.area.append("\n\n" + "Updating results page with results");

			// Shows the next 10 results if it exist
			show_results(1);
		}

		// Action to be performed for the next page button
		else if (ae.getSource() == previous_page) {
			// Shows the previous 10 results if it exist
			logWin.area.append("\n\n" + "Updating results page with results");
			sresults_sh -= 2;
			show_results(-1);
		}

		// Action to be performed for the publish button
		else if (ae.getSource() == publish_home) {

			try {

				// Chooses the file using dialog box
				final JFileChooser jfc = new JFileChooser();
				jfc.showOpenDialog(this);
				if (jfc.getSelectedFile() != null) {
					// publishdata("test",jfc.getSelectedFile());
					publish_file = jfc.getSelectedFile();
					Pub_file_size.setText("File :   " + publish_file.getName()
							+ "    (" + publish_file.length() + " bytes)");
					Pub_file_label.setText(publish_file.getPath());
					Pub_file_label.setFont(new Font("TimesNewRoman",
							Font.CENTER_BASELINE, 12));

					// frame1.setVisible(false);
					Pub_frame.setVisible(true);

				}
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			// JOptionPane.showMessageDialog(null, "Published","Publish",
			// JOptionPane.INFORMATION_MESSAGE);

		}
		// Action to be performed for the unpublish button

		else if (ae.getSource() == unPublish) {

			// Adds the files stored in the file hash to the list
			unPublish_Frame.setVisible(true);

			publish_home.setEnabled(false);

			listModel.removeAllElements();
			Iterator it = fileHash.keySet().iterator();
			farray = new String[fileHash.size()];
			File fname;
			present_file_count = 0;
			while (it.hasNext()) {
				farray[present_file_count] = (String) it.next();
				fname = new File(fileHash.get(farray[present_file_count]));
				listModel.addElement(fname.getName());
				present_file_count++;
			}

		}

		else if (ae.getSource() == log) {
			logWin.frame.setVisible(true);

		}

		else if (ae.getSource() == Log_exit) {
			logWin.frame.setVisible(false);
		}

		else if (ae.getSource() == cancel_unpublish) {

			unPublish_Frame.setVisible(false);
			publish_home.setEnabled(true);
			// listModel.clear();

		}
		// Action to be performed for the remove files button
		else if (ae.getSource() == Remove_files) {
			// Removes the selected file from the list as well as from the file
			// hash
			int list_sel_index = list.getSelectedIndex();
			if (list_sel_index == -1) {
				return;
			}
			// list.getSele
			// System.out.println(list_sel_index);
			listModel.remove(list_sel_index);
			logWin.area.append("\n\n" + "Unpublishing file "
					+ fileHash.get(farray[list_sel_index]));
			try {
				remove_file_hash(farray[list_sel_index]);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			Iterator it = fileHash.keySet().iterator();
			farray = new String[fileHash.size()];
			File fname;
			present_file_count = 0;
			while (it.hasNext()) {
				farray[present_file_count] = (String) it.next();
				fname = new File(fileHash.get(farray[present_file_count]));
				present_file_count++;
			}

		}

		else if (ae.getSource() == Pub_ok) {
			try {
				publishdata(localtxt.getText(), publish_file, Pub_abstract_area
						.getText());
				Pub_frame.setVisible(false);
				// System.out.println("Abstract: "+ Pub_abstract_area.getText()
				// );
				frame1.setVisible(true);
				Pub_abstract_area.setText("");
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}

		else if (ae.getSource() == Pub_cancel) {
			Pub_frame.setVisible(false);
			frame1.setVisible(true);
		}

		else if (ae.getSource() == Register_login) {

			frame.setVisible(false);
			Reg_frame.setVisible(true);

		}

		else if (ae.getSource() == Register) {

			String new_username = Username_Reg_txt.getText();
			String new_password = Pwd_Reg_txt.getText();
			String new_password_confirm = Pwd_Reg_txt_confirm.getText();
			String new_email = Email_Reg_txt.getText();

			// Compares whether the password and confirm password are same
			try {
				if (new_password.equals(new_password_confirm)) {
					sendReg(new_username, new_password, new_email);
				} else {
					JOptionPane.showMessageDialog(null,
							"Retype Password - Passwords doesn't match",
							"Password Mismatch",
							JOptionPane.INFORMATION_MESSAGE);
				}

			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}

		else if (ae.getSource() == exit_home) {
			try {
				client_exit();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			// System.exit(0);
		} else if (ae.getSource() == back_Reg_frame) {
			Reg_frame.setVisible(false);
			frame.setVisible(true);
		}

		// Download the selected file from the search results
		for (int i = 0; i < 10; i++) {

			if (ae.getSource() == sresult_but[i])
				// JOptionPane.showMessageDialog(null, "Enter some text to
				// search "+ sresults_sh,"Search",
				// JOptionPane.INFORMATION_MESSAGE);
				download_file(i + ((sresults_sh - 1) * 10));
		}

	}

	/*
	 * Function Name: load_farray() Description : This function loads the file
	 * entries in the filehash. Param : Null Returns : Null
	 */

	private void load_farray() {
		Iterator it = fileHash.keySet().iterator();
		farray = new String[fileHash.size()];
		File fname;
		present_file_count = 0;
		while (it.hasNext()) {
			farray[present_file_count] = (String) it.next();
			fname = new File(fileHash.get(farray[present_file_count]));
			listModel.addElement(fname.getName());
			present_file_count++;
		}

	}

	/*
	 * Function Name: sendReg() Description : This function constructs the
	 * packet with the username, password and email and send the packet. Param
	 * user : Username param pwd : password of user param email : Email ID
	 * Returns : Null
	 */

	// Registration
	public void sendReg(String user, String pwd, String email) throws Exception {
		// InetAddress myAddr=InetAddress.getLocalHost();
		// InetAddress servAddr=InetAddress.getLocalHost();

		// int servPort = Integer.parseInt(prop.getProperty("Server_UDP_Port"));
		// int cliPort = Integer.parseInt(prop.getProperty("Client_UDP_Port"));

		// User name & length in byte array
		byte[] b1 = gfns.convIntBary_2(user.length());
		byte[] b2 = user.getBytes();

		// Password and length in byte array
		MessageDigest md = MessageDigest.getInstance("SHA-1");
		byte[] b4 = md.digest(pwd.getBytes());
		byte[] b3 = gfns.convIntBary_2(b4.length);

		System.out.println("PWD:");
		// gfns.printbary(b4);
		// Email and length
		byte[] b5 = gfns.convIntBary_2(email.length());
		byte[] b6 = email.getBytes();

		byte[] payload = new byte[2 * 3 + b2.length + b4.length + b6.length];

		System.arraycopy(b1, 0, payload, 0, b1.length);
		System.arraycopy(b2, 0, payload, b1.length, b2.length);
		System.arraycopy(b3, 0, payload, b1.length + b2.length, b3.length);
		System.arraycopy(b4, 0, payload, b1.length + b2.length + b3.length,
				b4.length);
		System.arraycopy(b5, 0, payload, b1.length + b2.length + b3.length
				+ b4.length, b5.length);
		System.arraycopy(b6, 0, payload, b1.length + b2.length + b3.length
				+ b4.length + b5.length, b6.length);

		pack udp_pack_reg = new pack((byte) 5, (int) 1601, (byte) 16, myAddr,
				myPort, (int) payload.length, payload);
		DatagramPacket pack_reg = new DatagramPacket(udp_pack_reg.getPacket(),
				udp_pack_reg.getPacket().length, servAddr, servPort);

		pocket.send(pack_reg);

	}

	/*
	 * Function Name: download_file() Description : This function tries
	 * downloading the selected file from the search results if there is
	 * positive reply from the other peer which contains the file and if there
	 * is negative reply inform the user that DOWNLOAD FAILED. Param i : i th
	 * file entry from the search results Returns : Null
	 */

	private void download_file(int i) {
		logWin.area.append("\n\n" + "Trying download file : "
				+ sresult[i].getResultCont() + " \n Sending Request .");
		download_reply = 0;
		try {
			for (int j = 0; j < 10; j++) {

				// Send download UDP request for the ith entry file in search
				// result
				send_dload_udp_request(i);
				// Wait till you get a download reply
				Thread.currentThread();
				Thread.sleep(500);

				if (download_reply != 0) {
					break;
				}
			}
			// System.out.println("Staring download file from " +
			// download_reply);

			// if download reply is negative notify the user as FILE NOT FOUND
			if (download_reply == 1 || download_reply == 0) {
				logWin.area.append("\n"
						+ "Download failed -- file not found in server");
				JOptionPane.showMessageDialog(null,
						"ERROR : File not found on client", "Download Failed",
						JOptionPane.INFORMATION_MESSAGE);
				sresult[i].dload_status = 0;
				sresult_but[i - (sresults_sh - 1) * 10].setEnabled(false);
			}

			// if download reply is positive choose the path to save the file
			else if (download_reply == 2) {
				final JFileChooser jfc = new JFileChooser();
				jfc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				jfc.showSaveDialog(this);
				String folder_name = "";
				if (jfc.getSelectedFile() != null) {
					folder_name = jfc.getSelectedFile().getAbsolutePath() + "/";
				}
				File file = new File(folder_name + sresult[i].getFilename());
				logWin.area
						.append("\n"
								+ "Trying Download -- file found in server, storing in path"
								+ file.getAbsolutePath());

				// Try establishing the connection with the peer which allowed
				// to download the file and notify the server whether the
				// download is success or not
				if (establish_download(i, file) == 1) {
					logWin.area.append("\n" + "File Succesfully Downloaded");
					JOptionPane.showMessageDialog(null,
							"File Succesfully Downloaded", "Download Success",
							JOptionPane.INFORMATION_MESSAGE);
					if (!myname.equals(sresult[i].getUser())) {
						notify_bserver_dload(i, 1);
					}
				} else {
					logWin.area.append("\n\n"
							+ "Error in File Download - Please try again");
					JOptionPane.showMessageDialog(null,
							"Error in File Download - Please try again",
							"Download Failed", JOptionPane.INFORMATION_MESSAGE);

				}
				// notify_bserver_dload(i,1);
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	/*
	 * Function Name: notify_bserver_dload() Description : This function tries
	 * downloading the selected file from the search results if there is
	 * positive reply from the other peer which contains the file and if there
	 * is negative reply inform the user that DOWNLOAD FAILED. Param i : ith
	 * file entry in the search results param j : download success or failure
	 * Returns : Null
	 */

	private void notify_bserver_dload(int i, int j) throws Exception {
		logWin.area.append("\n\n"
				+ "Notifying server about successful download");
		// int servPort = Integer.parseInt(prop.getProperty("Server_UDP_Port"));
		// int cliPort = Integer.parseInt(prop.getProperty("Client_UDP_Port"));

		// Username and its length
		byte[] ba1 = gfns.convIntBary_2(sresult[i].getUser().length());
		byte[] ba2 = sresult[i].getUser().getBytes();

		// Message digest(File identifier) and its length
		byte[] ba4 = sresult[i].getMD();
		byte[] ba3 = gfns.convIntBary_2(ba4.length);

		byte[] payload = new byte[2 * 2 + ba2.length + ba4.length + 1];

		System.arraycopy(ba1, 0, payload, 0, ba1.length);
		System.arraycopy(ba2, 0, payload, ba1.length, ba2.length);
		System.arraycopy(ba3, 0, payload, ba1.length + ba2.length, ba3.length);
		System.arraycopy(ba4, 0, payload, ba1.length + ba2.length + ba3.length,
				ba4.length);
		payload[payload.length - 1] = (byte) j;

		// Constructs a UDP packet with the username,file identifier and file
		// download result as payload
		pack udp_pack = new pack((byte) 31, (int) 1234, (byte) 16, myAddr,
				myPort, (int) payload.length, payload);

		// Send the UDP packet to the server
		DatagramPacket pack = new DatagramPacket(udp_pack.getPacket(), udp_pack
				.getPacket().length, servAddr, servPort);
		pocket.send(pack);

	}

	/*
	 * Function Name: establish_download() Description : This function tries
	 * downloading the selected file from the search results if there is
	 * positive reply from the other peer which contains the file and if there
	 * is negative reply inform the user that DOWNLOAD FAILED. Param i : ith
	 * file entry in search results param file : file to be downloaded Returns :
	 * file download success or failure
	 */

	private int establish_download(int i, File file) throws Exception {
		logWin.area.append("\n"
				+ "Creating a TCP Connection for file download.");
		// InetAddress myAddr=InetAddress.getLocalHost();
		InetAddress dloadServAddr = sresult[i].getIP();
		int dloadServPort = sresult[i].getPortNo();
		// int servPort = Integer.parseInt(prop.getProperty("Client_TCP_Port"));

		Socket clientSocket = null;
		int cliPort = 0;
		try {
			clientSocket = new Socket(dloadServAddr, dloadServPort);

			cliPort = clientSocket.getLocalPort();

			OutputStream os = clientSocket.getOutputStream();
			InputStream in = clientSocket.getInputStream();

			// Setting the payload with message digest(file identifier)
			byte[] payload = sresult[i].MD;

			pack tcp_stream = new pack((byte) 25, (int) 5678, (byte) 16,
					myAddr, cliPort, (int) payload.length, payload);

			// System.out.println("Payload size:" + payload.length);
			// Using a TCP connection send the payload with the message digest
			if (clientSocket.isConnected()) {
				os.write(tcp_stream.getPacket());
			}

			FileOutputStream prf = new FileOutputStream(file);

			// byte[] buf_dload_file=new byte[sresult[i].filesize];
			// in.read(buf_dload_file);

			// prf.write(buf_dload_file);

			// Download the file with the buffer size as 100 bytes
			byte[] intmp = new byte[100];
			int rbyt, fsiz = 0;
			// byte[] buff=new byte[(int)file.length()];
			while (sresult[i].filesize - fsiz >= 100) {
				rbyt = in.read(intmp);

				prf.write(intmp, 0, rbyt);

				fsiz += rbyt;

			}

			rbyt = in.read(intmp, 0, sresult[i].filesize - fsiz);
			prf.write(intmp, 0, rbyt);

			clientSocket.close();
			prf.close();
			os.close();
			in.close();
		} catch (Exception e) {
			e.printStackTrace();
			return 0;
		}
		return 1;

	}

	/*
	 * Function Name: send_dload_udp_request() Description : This function sends
	 * the UDP packet request to download the i th entry in the file Param i :
	 * ith file entry in search results Returns : Null
	 */

	void send_dload_udp_request(int i) throws Exception {

		logWin.area.append("\n" + " Sending file download UDP Request");
		// InetAddress myAddr=InetAddress.getLocalHost();
		InetAddress dloadServAddr = sresult[i].getIP();
		int dloadServPort = sresult[i].getPortNo();

		// int servPort = Integer.parseInt(prop.getProperty("Client_UDP_Port"));
		// int cliPort = Integer.parseInt(prop.getProperty("Client_UDP_Port"));

		byte[] payload = sresult[i].MD;

		pack udp_pack = new pack((byte) 21, (int) 1234, (byte) 16, myAddr,
				myPort, (int) payload.length, payload);

		DatagramPacket pack = new DatagramPacket(udp_pack.getPacket(), udp_pack
				.getPacket().length, dloadServAddr, dloadServPort);
		pocket.send(pack);

	}

	/*
	 * Function Name: clean_results() Description : This function clears the
	 * entire search results. Param : Null Returns : Null
	 */

	private void clean_results() {
		for (int i = 0; i < sresults; i++) {
			sresult[i] = null;
		}
		sresults = 0;
		sresults_sh = 0;
		previous_page.setEnabled(false);
		next_page.setEnabled(false);
		clean_page();
	}

	/*
	 * Function Name: clean_page() Description : This function clears the
	 * results shown in current page. Param : Null Returns : Null
	 */

	private void clean_page() {

		for (int i = 0; i < 10; i++) {
			sresult_dis[i].setText(new String(""));
			sresult_but[i].setEnabled(false);
		}
		page_display.setText("");

	}

	/*
	 * Function Name : show_results() Description : This function shows the
	 * results depending on the direction whether it is previous page or next
	 * page Param direction : Previous or Next Returns : Null
	 */
	private void show_results(int direction) {
		clean_page();
		// TODO Auto-generated method stub
		if (sresults == -1) {
			logWin.area.append("\n" + "No results found in server ");
			JOptionPane.showMessageDialog(null, "Sorry, No results found",
					"Search", JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		// Shows the results the setting the download button status as enabled
		// or not depending on any previous download

		for (int i = 0; (i + sresults_sh * 10) <= sresults && i < 10; i++) {
			sresult_dis[i].setText(sresult[i + sresults_sh * 10]
					.getResultCont());
			if (sresult[i + sresults_sh * 10].getDloadStatus() != 0)
				sresult_but[i].setEnabled(true);
			// else
			// sresult_but[i].setEnabled(false);
		}
		sresults_sh++;

		previous_page.setEnabled(true);
		next_page.setEnabled(true);
		page_display.setEnabled(true);

		// Setting the page number
		page_display.setText("Page " + sresults_sh + "/" + (sresults / 10 + 1));

		if (sresults_sh <= 1) {
			previous_page.setEnabled(false);

		}
		if (sresults_sh * 10 > sresults) {
			next_page.setEnabled(false);

		}

	}

	/*
	 * Function Name : client_exit() Description : This function makes the
	 * client to send a UDP packet to the server about its departure and deletes
	 * its entry in the routing device. Param : Null Returns : Null
	 */

	private void client_exit() throws Exception {
		// int servPort = Integer.parseInt(prop.getProperty("Server_UDP_Port"));
		// int cliPort = Integer.parseInt(prop.getProperty("Client_UDP_Port"));

		byte[] ba1 = gfns.convIntBary_2(myname.length());
		byte[] ba2 = myname.getBytes();

		byte[] payload = new byte[2 + ba2.length + 3];

		System.arraycopy(ba1, 0, payload, 0, ba1.length);
		System.arraycopy(ba2, 0, payload, ba1.length, ba2.length);

		// Constructs the UDP packet with username as the payload

		// byte[] payload=new byte[]{00, 01, 00};
		pack udp_pack = new pack((byte) 2, (int) 1234, (byte) 16, myAddr,
				myPort, (int) payload.length, payload);

		DatagramPacket pack = new DatagramPacket(udp_pack.getPacket(), udp_pack
				.getPacket().length, servAddr, servPort);
		pocket.send(pack);
		pocket.close();

		delete_upnp_portmapping();

		System.exit(0);

	}

	/*
	 * Function Name : send_txtsearch() Description : This function establishes
	 * a TCP connection with the server and sends username and text search
	 * queryand receives the corresponding search results. Param txtsearch :
	 * textsearchquery Returns : Null
	 */

	private void send_txtsearch(String txtsearch) throws Exception {

		// InetAddress myAddr=InetAddress.getLocalHost();
		// InetAddress servAddr=InetAddress.getLocalHost();

		// int servPort = Integer.parseInt(prop.getProperty("Server_TCP_Port"));
		// int cliPort = Integer.parseInt(prop.getProperty("Client_TCP_Port"));

		Socket clientSocket = new Socket(servAddr, servPort);
		OutputStream out = clientSocket.getOutputStream();
		InputStream in = clientSocket.getInputStream();

		byte[] lengthF = new byte[2];
		byte[] temp_holder;
		int index = -1;

		trigest textdigest = new trigest(txtsearch);

		// send user name also

		byte[] ba1 = gfns.convIntBary_2(myname.length());
		byte[] ba2 = myname.getBytes();

		// Text search query
		byte[] ba3 = textdigest.getSignature();

		byte[] payload = new byte[2 + ba2.length + ba3.length];

		System.arraycopy(ba1, 0, payload, 0, ba1.length);
		System.arraycopy(ba2, 0, payload, ba1.length, ba2.length);
		System.arraycopy(ba3, 0, payload, ba1.length + ba2.length, ba3.length);

		pack tcp_stream = new pack((byte) 4, (int) 7890, (byte) 16, myAddr,
				clientSocket.getPort(), payload.length, payload);

		if (clientSocket.isConnected()) {

			// gfns.printbary(tcp_stream.getPacket());
			out.write(tcp_stream.getPacket());
			logWin.area.append("\n\n" + "Sending search request for phrase \""
					+ txtsearch + "\".");
		}

		// Receives the number of results to be retrieved
		in.read(lengthF);

		// Receives the search results
		while (gfns.convBaryInt(lengthF) != 0) {
			index++;
			temp_holder = new byte[gfns.convBaryInt(lengthF)];
			in.read(temp_holder);
			// gfns.printbary(temp_holder);
			sresult[index] = new search_result(temp_holder);
			in.read(lengthF);
		}
		sresults = index;
		logWin.area.append("\n" + "Received " + (index + 1) + " results");

		clientSocket.close();
		out.close();
		in.close();
	}

	public void open_frame1() {
		frame.setVisible(false);
		frame1.setVisible(true);
	}

	/*
	 * Function Name : send_login() Description : This function sends the
	 * username and password in a UDP packet to the server. Param user :
	 * Username param pwd : Password Returns : Null
	 */

	// Sending username and pwd to server
	public void send_login(String user, String pwd) throws Exception {

		// InetAddress myAddr=InetAddress.getLocalHost();
		// InetAddress servAddr=InetAddress.getLocalHost();

		// int servPort = Integer.parseInt(prop.getProperty("Server_UDP_Port"));
		// int cliPort = Integer.parseInt(prop.getProperty("Client_UDP_Port"));

		// Username and its length
		byte[] ba1 = gfns.convIntBary_2(user.length());
		byte[] ba2 = user.getBytes();

		// Password and its length
		MessageDigest md = MessageDigest.getInstance("SHA-1");
		byte[] ba4 = md.digest(pwd.getBytes());
		byte[] ba3 = gfns.convIntBary_2(ba4.length);

		byte[] payload = new byte[2 * 2 + ba2.length + ba4.length];

		System.arraycopy(ba1, 0, payload, 0, ba1.length);
		System.arraycopy(ba2, 0, payload, ba1.length, ba2.length);
		System.arraycopy(ba3, 0, payload, ba1.length + ba2.length, ba3.length);
		System.arraycopy(ba4, 0, payload, ba1.length + ba2.length + ba3.length,
				ba4.length);

		// Construction the UDP paket with username and password as its payload
		pack udp_pack = new pack((byte) 1, (int) 1234, (byte) 16, myAddr,
				myPort, (int) payload.length, payload);

		DatagramPacket pack = new DatagramPacket(udp_pack.getPacket(), udp_pack
				.getPacket().length, servAddr, servPort);
		// DatagramSocket sock = new DatagramSocket(cliPort);
		pocket.send(pack);
		// pocket.close();

	}

	/*
	 * Function Name : publishdata() Description : This function establishes TCP
	 * connection and publishes the filename, filesize, filedigest,
	 * messagedigest(file identifier) , abstract. Param user : Username param
	 * file : File to be published param abtract : Abstract of the file Returns :
	 * Successfully Published file's SHA(file identifier)
	 */

	public String publishdata(String user, File file, String abtract)
			throws Exception {

		// InetAddress myAddr=InetAddress.getLocalHost();
		// InetAddress servAddr=InetAddress.getLocalHost();

		logWin.area.append("\n\n" + "Publishing file -- "
				+ file.getAbsolutePath());

		if (abtract == null) {
			abtract = new String("---");
		}

		// int servPort = Integer.parseInt(prop.getProperty("Server_TCP_Port"));
		// int cliPort = Integer.parseInt(prop.getProperty("Client_TCP_Port"));
		// int servPort=22886;
		// int cliPort=12487;

		Socket clientSocket = new Socket(servAddr, servPort);
		int cliPort = clientSocket.getLocalPort();
		OutputStream os = clientSocket.getOutputStream();
		InputStream in = clientSocket.getInputStream();

		String filename = file.getName();

		// Getting the filedigest of the file to be published
		trigest filedigest = new trigest(file);
		// trigest textdigest=new trigest("aaa");

		// gfns.printbary(textdigest.getSignature());
		// gfns.printbary(filedigest.getSignature());
		// System.out.println("text digest in HexString format" +
		// gfns.ByteArraytohexString(textdigest.getSignature()));
		// System.out.println("file digest in HexString format" +
		// gfns.ByteArraytohexString(filedigest.getSignature()));

		// Username & length
		byte[] user_len = gfns.convIntBary_2(user.length());
		byte[] username = user.getBytes();

		// Filename & length
		byte[] fname_len = gfns.convIntBary_2(filename.length());
		byte[] fname = filename.getBytes();

		// Abstract * length
		// String abtract="ABSTRACT";
		byte[] abtract_len = gfns.convIntBary_2(abtract.length());
		byte[] fabstract = abtract.getBytes();

		// Filesize
		long filesize = file.length();
		byte[] fsize = new byte[] { (byte) (filesize >>> 24),
				(byte) (filesize >>> 16), (byte) (filesize >>> 8),
				(byte) filesize };

		// FileDigest
		byte[] fdigest = filedigest.getSignature();

		// Message Digest
		MessageDigest md = MessageDigest.getInstance("SHA-1");
		FileInputStream unid = new FileInputStream(file);
		byte[] file_cont = new byte[unid.available()];
		unid.read(file_cont);
		byte[] mdigest = md.digest(file_cont);

		// Copying all the published info into a single payload

		int index = 0;
		byte[] payload = new byte[user_len.length + username.length
				+ fname_len.length + fname.length + abtract_len.length
				+ fabstract.length + fsize.length + fdigest.length
				+ mdigest.length];

		System.arraycopy(user_len, 0, payload, 0, 2);
		index += 2;

		System.arraycopy(username, 0, payload, index, username.length);
		index += username.length;

		System.arraycopy(fname_len, 0, payload, index, 2);
		index += 2;

		System.arraycopy(fname, 0, payload, index, fname.length);
		index += fname.length;

		System.arraycopy(abtract_len, 0, payload, index, 2);
		index += 2;

		System.arraycopy(fabstract, 0, payload, index, fabstract.length);
		index += fabstract.length;

		System.arraycopy(fsize, 0, payload, index, 4);
		index += 4;

		System.arraycopy(fdigest, 0, payload, index, fdigest.length);
		index += fdigest.length;

		System.arraycopy(mdigest, 0, payload, index, mdigest.length);
		index += mdigest.length;

		// gfns.printbary(payload);
		// gfns.printbary(payload);
		pack tcp_stream = new pack((byte) 3, (int) 5678, (byte) 16, myAddr,
				myPort, (int) payload.length, payload);

		// System.out.println("Payload size:" + payload.length);
		if (clientSocket.isConnected()) {
			os.write(tcp_stream.getPacket());
		}
		String file_sha1 = gfns.ByteArraytohexString(mdigest);

		// Receives whether the publish is success or not
		if (in.read() == 1) {
			logWin.area.append("\n" + "Publish Successful");
			JOptionPane.showMessageDialog(null, "Publish successful",
					"Publish", JOptionPane.INFORMATION_MESSAGE);
			add_file_hash(file, file_sha1);

		} else {
			logWin.area.append("\n" + "Publish failed");
			JOptionPane.showMessageDialog(null, "Publish failed", "Publish",
					JOptionPane.INFORMATION_MESSAGE);
		}
		clientSocket.close();
		os.close();

		return (file_sha1);
	}

	/*
	 * Function Name : add_file_hash() Description : This function adds
	 * successfully published file and the file identifier to the local file
	 * SHA_path file and add it hash map. Param file : Successfully published
	 * file param file_sha1 : Successfully published File identifier Returns :
	 * Null
	 */

	private void add_file_hash(File file, String file_sha1) {

		// Adds the file identifier and the absolute path of the file in the
		// hash
		fileHash.put(file_sha1, file.getAbsolutePath());

		try {
			FileWriter fw = new FileWriter(SHA_path_file, true);
			fw.write(file_sha1);// appends the string to the file
			fw.write(file.getAbsolutePath());// Absolute path of the file
			fw.write("\n");
			fw.close();
		}

		catch (IOException ioe) {
			System.err.println("IOException: " + ioe.getMessage());
		}

	}

	/*
	 * Function Name : remove_file_hash() Description : This function removes
	 * the file identifier entry from the hash and also it removes the entry
	 * from the locally stored file(SHA_path file) Request the server to remove
	 * the file using the file identifier frm the database. param file_sha1 :
	 * Successfully published File identifier Returns : Null
	 */

	private void remove_file_hash(String file_sha1) throws Exception {

		// Removes the entry from the hash
		fileHash.remove(file_sha1);

		// Create a new SHA_path_file and update it with the newly updated
		// filehash
		try {
			FileWriter fw = new FileWriter(SHA_path_file);
			Iterator it = fileHash.keySet().iterator();
			File fname;
			String ts;
			while (it.hasNext()) {
				ts = (String) it.next();
				fw.write(ts);// appends the string to the file
				ts = fileHash.get(ts);
				fw.write(ts);
				fw.write("\n");
			}
			fw.close();
		}

		catch (IOException ioe) {
			System.err.println("IOException: " + ioe.getMessage());
		}

		// int servPort = Integer.parseInt(prop.getProperty("Server_UDP_Port"));
		// int cliPort = Integer.parseInt(prop.getProperty("Client_UDP_Port"));

		// Sends a UDP packet to the server requesting it to remove the file
		// identifier from the database.
		byte[] ba1 = gfns.convIntBary_2(myname.length());
		byte[] ba2 = myname.getBytes();

		byte[] ba4 = gfns.hexStringToByteArray(file_sha1);
		byte[] ba3 = gfns.convIntBary_2(ba4.length);

		byte[] payload = new byte[2 * 2 + ba2.length + ba4.length];

		System.arraycopy(ba1, 0, payload, 0, ba1.length);
		System.arraycopy(ba2, 0, payload, ba1.length, ba2.length);
		System.arraycopy(ba3, 0, payload, ba1.length + ba2.length, ba3.length);
		System.arraycopy(ba4, 0, payload, ba1.length + ba2.length + ba3.length,
				ba4.length);

		pack udp_pack = new pack((byte) 51, (int) 1234, (byte) 16, myAddr,
				myPort, (int) payload.length, payload);

		DatagramPacket pack = new DatagramPacket(udp_pack.getPacket(), udp_pack
				.getPacket().length, servAddr, servPort);
		// DatagramSocket sock = new DatagramSocket(cliPort);
		pocket.send(pack);
		// pocket.close();

	}

	public static void main(String args[]) throws Exception {
		client_gui t1 = new client_gui();
	}

}

class search_result {

	public genfunc gfns = new genfunc();

	public byte ID[] = new byte[4];

	public byte IP[] = new byte[4];

	public byte port_no[] = new byte[2];

	public byte filename[];

	public byte abtract[];

	public byte user[];

	public byte MD[] = new byte[20];

	public int filesize;

	public int downloads;

	public int dload_status = 1;

	/*
	 * Type : Constructor Copies the search results in a content array
	 * 
	 */
	public search_result(byte[] content) {
		super();
		byte[] temp;
		int index = 0, size;

		System.arraycopy(content, index, IP, 0, IP.length);
		index += 4;

		System.arraycopy(content, index, port_no, 0, port_no.length);
		index += 2;

		System.arraycopy(content, index, MD, 0, MD.length);
		index += 20;

		temp = new byte[4];
		System.arraycopy(content, index, temp, 0, temp.length);
		index += 4;
		filesize = gfns.convBaryInt(temp);

		temp = new byte[2];
		System.arraycopy(content, index, temp, 0, temp.length);
		index += 2;
		downloads = gfns.convBaryInt(temp);

		temp = new byte[2];
		System.arraycopy(content, index, temp, 0, temp.length);
		index += 2;
		size = gfns.convBaryInt(temp);

		filename = new byte[size];
		System.arraycopy(content, index, filename, 0, size);
		index += size;

		temp = new byte[2];
		System.arraycopy(content, index, temp, 0, temp.length);
		index += 2;
		size = gfns.convBaryInt(temp);

		abtract = new byte[size];
		System.arraycopy(content, index, abtract, 0, size);
		index += size;

		temp = new byte[2];
		System.arraycopy(content, index, temp, 0, temp.length);
		index += 2;
		size = gfns.convBaryInt(temp);

		user = new byte[size];
		System.arraycopy(content, index, user, 0, size);
		index += size;

	}

	// Getter Methods
	public String getResultCont() {
		// String tstring="File Name : " + this.getFilename() + " Size : " +
		// filesize + " User : " + this.getUser() + " Dloads : " + downloads +
		// "\n" + this.getAbtract();
		String tstring = this.getFilename() + " (" + filesize + " bytes, "
				+ this.downloads + " downloads) by : " + this.getUser() + "\n"
				+ this.getAbtract();
		// System.out.println(tstring);

		return new String(tstring);
	}

	public String getAbtract() {
		return new String(abtract);
	}

	public int getDownloads() {
		return downloads;
	}

	public int getDloadStatus() {
		return dload_status;
	}

	public String getFilename() {
		return new String(filename);
	}

	public int getFilesize() {
		return filesize;
	}

	public genfunc getGfns() {
		return gfns;
	}

	public byte[] getID() {
		return ID;
	}

	public InetAddress getIP() {
		try {
			return InetAddress.getByAddress(IP);
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	public byte[] getMD() {
		return MD;
	}

	public int getPortNo() {
		return gfns.convBaryInt(port_no);
	}

	public String getUser() {
		return new String(user);
	}

}

/*
 * Class : tcp_server
 */
class tcp_server implements Runnable {

	public Properties prop = new Properties();

	public client_gui cgi;

	public tcp_server(client_gui cgi) throws IOException {
		this.cgi = cgi;
	}

	// Opens the tcp port and starts the TCP server. Listens for any incoming
	// TCP connecions
	public void run() {
		try {
			cgi.logWin.area.append("\n\n" + "Opening TCP Port");
			open_tcp_port();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	/*
	 * Function Name : open_tcp_port() Description : This function listens for
	 * any new incoming connections and creates a new separate thread for each
	 * incoming connection param : Null Returns : Null
	 */

	public void open_tcp_port() throws IOException {

		ServerSocket server = new ServerSocket(cgi.myPort);
		clienttcpSocket newclient;
		while (true) {

			try {
				newclient = new clienttcpSocket(server.accept(), cgi);
				Thread t = new Thread(newclient);
				cgi.logWin.area.append("\n\n"
						+ "Creating a new thread for a TCP request");
				t.start();

			} catch (IOException e) {
				cgi.logWin.area.append("\n\n" + "TCP Server Accept failed" + e);
			}

		}

	}
}

class clienttcpSocket implements Runnable {

	private Socket socket;

	public genfunc gfns = new genfunc();

	public Connection con;

	public Statement stmt;

	public PreparedStatement pstmt;

	public ResultSet rs;

	public client_gui cgi;

	clienttcpSocket(Socket socket, client_gui cgi) {
		this.socket = socket;
		this.cgi = cgi;
	}

	// Implements the runnable interface for the client socket
	// Reads incoming TCP connection and reads the file identifier. After
	// identifying the file using the file identifier
	// it will send the file to the peer requesting that particular file

	public void run() {
		byte[] buffer = new byte[16];
		byte[] payload;
		pack tcp_pack;
		Map<String, String> fileHash = new LinkedHashMap<String, String>();

		try {
			BufferedReader triRead1 = new BufferedReader(new FileReader(
					cgi.SHA_path_file));
			String line = null;

			// Reads each line from the SHA_path file and adds it to the hash
			// map fileHash
			while ((line = triRead1.readLine()) != null) {
				fileHash.put(line.substring(0, 40), line.substring(40));
			}
			triRead1.close();

			InputStream in = socket.getInputStream();
			OutputStream out = socket.getOutputStream();

			in.read(buffer, 0, buffer.length);

			// System.out.println("Printing the buffer");
			// gfns.printbary(buffer);

			// Reads the incoming TCP connection
			tcp_pack = new pack(buffer, buffer.length, socket.getInetAddress(),
					socket.getPort());
			payload = new byte[tcp_pack.getPaylength()];
			in.read(payload, 0, payload.length);

			// Identifies the requested file to be downloaded
			cgi.logWin.area.append("\n\n"
					+ "Received TCP download request for :  "
					+ fileHash.get(gfns.ByteArraytohexString(payload)));
			File file = new File(fileHash.get(gfns
					.ByteArraytohexString(payload)));
			FileInputStream hit_file = new FileInputStream(file);
			byte[] buff = new byte[(int) file.length()];
			hit_file.read(buff);
			// Sending to the requested peer
			out.write(buff);

			out.close();
			in.close();
			cgi.logWin.area.append("\n" + "File sent Successfully.");

		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
}

// Keep Alive timer which sends an hello packet to the server in an interval of
// 140s

class client_keepAlive implements Runnable {
	public client_gui cgi;

	public genfunc gfns = new genfunc();

	client_keepAlive(client_gui cgi) {
		this.cgi = cgi;
	}

	public void run() {
		while (true) {
			try {
				Thread.currentThread();
				Thread.sleep(140000);
				send_hello_pack();
				// String usrname=cgi.myname;
				// cgi.send
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/*
	 * Function Name : send_hello_pack() Description : This function sends a UDP
	 * packet as hello packet with the username as its payload param : Null
	 * Returns : Null
	 */
	private void send_hello_pack() throws Exception {

		// int servPort =
		// Integer.parseInt(cgi.prop.getProperty("Server_UDP_Port"));
		// int cliPort =
		// Integer.parseInt(cgi.prop.getProperty("Client_UDP_Port"));

		byte[] ba1 = gfns.convIntBary_2(cgi.myname.length());
		byte[] ba2 = cgi.myname.getBytes();

		byte[] payload = new byte[2 + ba2.length];

		System.arraycopy(ba1, 0, payload, 0, ba1.length);
		System.arraycopy(ba2, 0, payload, ba1.length, ba2.length);

		pack udp_pack = new pack((byte) 3, (int) 1234, (byte) 16, cgi.myAddr,
				cgi.myPort, payload.length, payload);

		DatagramPacket pack = new DatagramPacket(udp_pack.getPacket(), udp_pack
				.getPacket().length, cgi.servAddr, cgi.servPort);
		cgi.pocket.send(pack);
	}

}