/* MKoda 
 * Peer to Peer File Sharing System with a search engine
 * Please follow the readme file for instructions on how to setup and run the system 
 * Written by : Arun Natarajan, Manikandan Sivanesan, Koushik Krishnakumar, Dinesh Radha Kirshan
*/
package server;

import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import java.lang.*;

import client.*;

import trigest.*;
import packetformat.*;
import general.*;

class clientSocket implements Runnable {

	private Socket socket;
	public Properties prop = new Properties();
	public genfunc gfns = new genfunc();
	public Connection con;
	public Statement stmt;
	public PreparedStatement pstmt;
	public ResultSet rs;
	public server c_server;

	clientSocket(Socket socket, server c_server) {
		this.socket = socket;
		this.c_server = c_server;
	}
/*
 * Implements the runnable interface for new incoming TCP client socket. Checks the packet type from the stream and start processing it.
	*/
	public void run() {
		byte[] buffer = new byte[16];
		byte[] payload;
		byte[] tcp_packet;
		pack tcp_pack;
		try {
			
			prop.load(this.getClass().getResourceAsStream("/Resolver.Properties"));
			InputStream in = socket.getInputStream();
			OutputStream out = socket.getOutputStream();
			
			//Reads the incoming TCP stream
			in.read(buffer, 0, buffer.length);

//			System.out.println("Printing the buffer");
//			gfns.printbary(buffer);

			tcp_pack = new pack(buffer, buffer.length, socket.getInetAddress(),socket.getPort());
			
			payload = new byte[tcp_pack.getPaylength()];
			//Reads the payload
			in.read(payload, 0, payload.length);
			
			
			tcp_packet = new byte[buffer.length + payload.length];
			System.arraycopy(buffer, 0, tcp_packet, 0, buffer.length);
			System.arraycopy(payload, 0, tcp_packet, buffer.length,payload.length);

			tcp_pack = new pack(tcp_packet, tcp_packet.length, socket.getInetAddress(), socket.getPort());
			
			//Reads the username in a byte array
			byte[] usrname = new byte[gfns.convBaryInt(new byte[] {
					tcp_pack.data[0], tcp_pack.data[1] })];
			System.arraycopy(tcp_pack.data, 2, usrname, 0, usrname.length);

			// Update the port no with the user's registered port
			// because the port no got here is temporary tcp port the client used.
			tcp_pack.setPort_no(getPortno(new String(usrname)));


//			System.out.println("New tcp connection");
			
			//Checks the active status of the user and sends USER NOT ACTIVE  message with the packet type as 99
			if (!valid_active_user(tcp_pack, new String(usrname))) {
				System.out.println("User " + new String(usrname) +"not currently active. Please login");
				c_server.send_reply(tcp_pack, (byte) 99, (byte) 0);
//				System.out.println("sent a UPD packet");
				out.write(0);
					
			} 
			
			//Uploads the file digest and other published file info to the database if the packet type is 3
			else if (tcp_pack.getPkttype() == 3) {
				out.write((byte) upload_file_digest(tcp_pack));
			}
			
			//Provides the search results for the text searched query if the packet type is 4
			else if (tcp_pack.getPkttype() == 4) {

				text_searched(out, tcp_pack);
//				System.out.println("Key search request received");

			}
			
			//Updates the user's published file which are consistent and removes the inconsistent file entries from the database for the packet type 24
			else if(tcp_pack.getPkttype() == 24){

				out.write((byte)update_consistent_SHA(out,tcp_pack));
			}
			
			// For other print User inactive
			else {
				System.out.println(new String(usrname) + " not currently active. Please login");
			}
			out.close();
			in.close();
			// socket.close();

		} catch (IOException e) {
			e.printStackTrace();
		} catch (SQLException ex) {
			System.out.println("SQLException: " + ex.getMessage());
			System.out.println("SQLState: " + ex.getSQLState());
			System.out.println("VendorError: " + ex.getErrorCode());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
	
	/*
	 * Function Name	:	update_consistent_SHA()
	 * Description 		:	This function updates the FILE TABLE in the database with the consistent SHA entries and update those entries as active 
	 * 						for the user. 
	 * Param out		: 	Stream to be written back through TCP
	 * param tcp_pack	:	Stream received for updating consistent SHA 
	 * Returns 			: 	Insertion success or failure	
	*/


	private int update_consistent_SHA(OutputStream out, pack tcp_pack) throws Exception {

//		System.out.println("In the update consistent SHA");

		int index = 0, rec_count = 0;
		int lengthF = 0;
		byte[] SHA=new byte[20];
		
		//username and its length
		byte[] user_len=new byte[2];
		System.arraycopy(tcp_pack.getData(), index,user_len, 0, 2);
		index+=2;

		byte[] usrname=new byte[gfns.convBaryInt(user_len)];
		System.arraycopy(tcp_pack.getData(), index,usrname, 0, gfns.convBaryInt(user_len));
		index+=gfns.convBaryInt(user_len);
		
		//Number of SHA entries
		byte[] rec_count_bytes=new byte[2];
		System.arraycopy(tcp_pack.getData(), index,rec_count_bytes, 0, 2);
		index+=2;
		rec_count=gfns.convBaryInt(rec_count_bytes);

//		System.out.println("loength us " + tcp_pack.getData().length + " " + rec_count);
		
		//SHA entries
		byte[] SHA_array = new byte[rec_count * 20];
		System.arraycopy(tcp_pack.getData(), index,SHA_array, 0, rec_count*20);
		index+=rec_count*20;


		//Connecting to the db
		con = DriverManager.getConnection("jdbc:mysql://localhost/bootstrap?"+ "user=root&password=mysqlpwd");
		
		//Updating each SHA entries with the status as active
		index=0;
		for(int i=0;i < rec_count;i++){

			System.arraycopy(SHA_array, index, SHA, 0, 20);
			index+=20;

			String sql = "UPDATE FILETABLE SET ACTIVE_STATUS = 1, IPADDR = ?, PORT_NO = ? WHERE MSGDST= ? AND USER = ? ";
			pstmt = con.prepareStatement(sql);
			
			
			//Setting the ? in the sql query
			pstmt.setString(1, tcp_pack.getIP().getHostAddress());
			pstmt.setInt(2, tcp_pack.getPort_no());
			pstmt.setString(3, gfns.ByteArraytohexString(SHA));
			pstmt.setString(4, new String(usrname));
			pstmt.executeUpdate();
			con.close();

		}
		System.out.println(new String(usrname) + " user's file publish consistency verified");

//		String sql = "DELETE FROM FILETABLE WHERE ACTIVE_STATUS = 0 AND USER = ? ";
//	
//		pstmt = con.prepareStatement(sql);
//		pstmt.setString(1, new String(usrname));
//		pstmt.executeUpdate();
//
		return 1;

	}
	/*
	 * Function Name	:	getPortno()
	 * Description 		:	This function returns the port number used by the user
	 * Param usrname	: 	Username
	 * 
	 * Returns 			: 	Port Number
	*/

	private int getPortno(String usrname) throws Exception {

		int portno = 0;

		con = DriverManager.getConnection("jdbc:mysql://localhost/bootstrap?"+ "user=root&password=mysqlpwd");
		String sql = "SELECT PORT_NO from  IPTABLE where HOST=?";
		pstmt = con.prepareStatement(sql);

		pstmt.setString(1, usrname);
		rs = pstmt.executeQuery();
		while (rs.next()) {
			portno = Integer.parseInt(rs.getString("PORT_NO"));
			break;
		}
		con.close();
		return portno;
		
	}
	
	/*
	 * Function Name	:	text_searched()
	 * Description 		:	This function returns the port number used by the user
	 * Param tcp_pack	: 	Stream received for returning the search results for the searched text query 
	 * param out		: 	Stream to be written
	 * Returns 			: 	Null
	*/

	public void text_searched(OutputStream out, pack tcp_pack)	throws SQLException, Exception {

		System.out.println("Search query received from " + tcp_pack.getIP().getHostAddress());
		
		//Allocating memory to hold 8192 bits
		//BitSet class allows to manipulate in bits
		BitSet text_search_bits = new BitSet(8192);
		BitSet filedigest_bits = new BitSet(8192);

		int index = 0, rec_count = 0;
		int lengthF = 0;

		// The last 1024 is the signature of the searched string
		byte[] text_searched = new byte[1024];
		// gfns.printbary(tcp_pack.getData());

		System.arraycopy(tcp_pack.getData(), tcp_pack.getData().length - 1024,text_searched, 0, 1024);
		text_search_bits = gfns.fromByteArray(text_searched);

		// gfns.printbary(text_searched);
		
		
		//Retrieve the results which matches the searched text query and provide it in descending order
		con = DriverManager.getConnection("jdbc:mysql://localhost/bootstrap?"+ "user=root&password=mysqlpwd");
		String sql = "SELECT * from  FILETABLE WHERE ACTIVE_STATUS = ? ORDER BY DOWNLOADS DESC";
		pstmt = con.prepareStatement(sql);

		pstmt.setInt(1, 1);
		rs = pstmt.executeQuery();

		while (rs.next()) {
			
			//Compares each bit in the text searched digest and filedigest of each file in the database
			byte[] temp_holder = new byte[256];
			filedigest_bits = gfns.fromByteArray(gfns.hexStringToByteArray(rs.getString("FILEDIGEST")));
			filedigest_bits.and(text_search_bits);
			if (filedigest_bits.equals(text_search_bits)) {
				// System.out.println("a hit");
				
				
				//Stores each row which matches the text searched query in temp holder variable
				index = 0;
				System.arraycopy(gfns.getIpAsArrayOfByte(rs.getString("IPADDR")), 0,
						temp_holder, index, 4);
				index += 4;

				System.arraycopy(gfns.convIntBary_2(rs.getInt("PORT_NO")), 0,
						temp_holder, index, 2);
				index += 2;

				System.arraycopy(gfns.hexStringToByteArray(rs
						.getString("MSGDST")), 0, temp_holder, index, 20);
				index += 20;

				System.arraycopy(gfns.convIntBary(rs.getInt("FILESIZE")), 0,
						temp_holder, index, 4);
				index += 4;

				System.arraycopy(gfns.convIntBary_2(rs.getInt("DOWNLOADS")), 0,
						temp_holder, index, 2);
				index += 2;

				lengthF = rs.getString("FILENAME").length();
				System.arraycopy(gfns.convIntBary_2(lengthF), 0, temp_holder,
						index, 2);
				index += 2;

				System.arraycopy(rs.getString("FILENAME").getBytes(), 0,
						temp_holder, index,
						rs.getString("FILENAME").getBytes().length);
				index += rs.getString("FILENAME").getBytes().length;

				lengthF = rs.getString("ABSTRACT").length();
				System.arraycopy(gfns.convIntBary_2(lengthF), 0, temp_holder,
						index, 2);
				index += 2;

				System.arraycopy(rs.getString("ABSTRACT").getBytes(), 0,
						temp_holder, index,
						rs.getString("ABSTRACT").getBytes().length);
				index += rs.getString("ABSTRACT").getBytes().length;

				lengthF = rs.getString("USER").length();
				System.arraycopy(gfns.convIntBary_2(lengthF), 0, temp_holder,
						index, 2);
				index += 2;

				System.arraycopy(rs.getString("USER").getBytes(), 0,
						temp_holder, index,
						rs.getString("USER").getBytes().length);
				index += rs.getString("USER").getBytes().length;
				
				//Sends the record length(row length) 
				out.write(gfns.convIntBary_2(index));
				//Sends the actual record(row)
				out.write(temp_holder, 0, index);
				rec_count++;
			}
		}
		
		//Sends the number of records matching the query
		out.write(gfns.convIntBary_2(0));
		con.close();

	}
	
	/*
	 * Function Name	:	valid_active_user()
	 * Description 		:	This function checks the user status as active or inactive 
	 * Param tcp_pack	: 	Stream received for returning the search results for the searched text query 
	 * param usrname	: 	Username
	 * Returns 			: 	Valid User or Invalid user
	*/

	private boolean valid_active_user(pack tcp_pack, String usrname)
	throws SQLException {
		int Result = 0;

		con = DriverManager.getConnection("jdbc:mysql://localhost/bootstrap?"
				+ "user=root&password=mysqlpwd");
		String sql = "SELECT ACTIVE_STATUS from  IPTABLE where IPADDR=? AND HOST=?";
		pstmt = con.prepareStatement(sql);

		pstmt.setString(1, tcp_pack.getIP().getHostAddress()); // Setting the
		// status as
		// active
		pstmt.setString(2, usrname);
		rs = pstmt.executeQuery();
		while (rs.next()) {
			Result = Integer.parseInt(rs.getString("ACTIVE_STATUS"));
//			System.out.println("User active Status checked" + Result);
			break;
		}
		
		con.close();
		if (Result != 0)
			return true;
		else
			return false;
	}
	
	/*
	 * Function Name	:	upload_file_digest()
	 * Description 		:	This function inserts the file entries which are obtained from the peer while publishing.  
	 * Param tcp_pack	: 	Stream received for inserting file entry in the db
	 * Returns 			: 	Upoload successful or unsucccessful
	*/

	public int upload_file_digest(pack tcp_pack) {
		
		System.out.println("New file upload received from " + tcp_pack.getIP().getHostAddress());

		byte[] lengthF = new byte[2];
		int aIndex = 0, result = 0;
		
		//Copying the file entries into individual fields
		System.arraycopy(tcp_pack.getData(), 0, lengthF, 0, 2);
		byte[] usrname = new byte[gfns.convBaryInt(lengthF)];
		System.arraycopy(tcp_pack.getData(), 2, usrname, 0, gfns
				.convBaryInt(lengthF));
		aIndex += (2 + gfns.convBaryInt(lengthF));

		System.arraycopy(tcp_pack.getData(), aIndex, lengthF, 0, 2);
		byte[] filename = new byte[gfns.convBaryInt(lengthF)];
		System.arraycopy(tcp_pack.getData(), aIndex + 2, filename, 0, gfns
				.convBaryInt(lengthF));
		aIndex += (2 + gfns.convBaryInt(lengthF));

		System.arraycopy(tcp_pack.getData(), aIndex, lengthF, 0, 2);
		byte[] abtract = new byte[gfns.convBaryInt(lengthF)];
		System.arraycopy(tcp_pack.getData(), aIndex + 2, abtract, 0, gfns
				.convBaryInt(lengthF));
		aIndex += (2 + gfns.convBaryInt(lengthF));

		byte[] filesize = new byte[4];
		System.arraycopy(tcp_pack.getData(), aIndex, filesize, 0, 4);
		aIndex += 4;

		byte[] filedigest = new byte[1024];
		System.arraycopy(tcp_pack.getData(), aIndex, filedigest, 0, 1024);
		aIndex += 1024;

		byte[] filemd = new byte[20];
		System.arraycopy(tcp_pack.getData(), aIndex, filemd, 0, 20);
		aIndex += 20;

		try {

			con = DriverManager
			.getConnection("jdbc:mysql://localhost/bootstrap?"+ "user=root&password=mysqlpwd");
			String sql = "INSERT INTO FILETABLE (IPADDR, USER, ACTIVE_STATUS, FILENAME, FILESIZE, FILEDIGEST, MSGDST,ABSTRACT,DOWNLOADS,PORT_NO) VALUES(?,?,?,?,?,?,?,?,?,?)";
			pstmt = con.prepareStatement(sql);
			pstmt.clearParameters();
			
			//Setting the parameters
			pstmt.setString(1, tcp_pack.getIP().getHostAddress());
			pstmt.setString(2, new String(usrname));
			pstmt.setInt(3, 1);
			pstmt.setString(4, new String(filename));
			pstmt.setInt(5, gfns.convBaryInt(filesize));
			String hexString_fd = gfns.ByteArraytohexString(filedigest);
			pstmt.setString(6, hexString_fd);
			String hexString_md = gfns.ByteArraytohexString(filemd);
			pstmt.setString(7, hexString_md);
			pstmt.setString(8, new String(abtract));
			pstmt.setInt(9, 0);
			pstmt.setInt(10, tcp_pack.getPort_no());

			result = pstmt.executeUpdate();
			if (result == 1) {
				System.out.println("Insertion successful");
			} else {
				System.out.println("Insertion failed");
			}
			
			con.close();

		}

		catch (SQLException ex) {
			System.out.println("SQLException: " + ex.getMessage());
			System.out.println("SQLState: " + ex.getSQLState());
			System.out.println("VendorError: " + ex.getErrorCode());

		}
		
		return result;

	}

}
/*
 * Class Name : Server

*/
public class server implements Runnable {

	public Properties prop = new Properties();
	public genfunc gfns = new genfunc();
	public DatagramSocket pocket;
	public Connection con;
	public Statement stmt;
	public PreparedStatement pstmt;
	public ResultSet rs;
	public InetAddress myAddr;
	public Map<String, Integer> user_status_hash = new LinkedHashMap<String, Integer>();
	public int myPort;
	
	//Implements the runnable interface of the class server
	public void run() {

		stmt = null;
		pstmt = null;
		rs = null;
		
		//Registering the mysql driver
		try {
			Class.forName("com.mysql.jdbc.Driver").newInstance();
		} catch (Exception ex) {
			System.out.println("Problem with registering the driver" + ex);
		}
		
		//Getting the connection to the databasse
		try {
			con = DriverManager
			.getConnection("jdbc:mysql://localhost/bootstrap?"
					+ "user=root&password=mysqlpwd");
		}

		catch (SQLException ex) {
			System.out.println("SQLException: " + ex.getMessage());
			System.out.println("SQLState: " + ex.getSQLState());
			System.out.println("VendorError: " + ex.getErrorCode());

		}

		try {
			prop.load(this.getClass().getResourceAsStream(
			"/Resolver.Properties"));
			
			//Server Address and server port
			myinet4addr getmyaddr = new myinet4addr();
			myAddr = getmyaddr.getMy4Ia();
			myPort = Integer.parseInt(prop.getProperty("Server_Port"));
			System.out.println("Using my IP address " + myAddr.getHostAddress() + " and port " + myPort);
			
			//Starting a separate thread for TCP server
			tcp_server t1 = new tcp_server(this);
			new Thread(t1).start();
			
			//Opening UDP port and start listening for incoming packets
			open_udp_port();


		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
	
	/*
	 * Function Name:	open_udp_port()()
	 * Description 	:	This function opens the UDP port and listens for any incoming 
	 * 					UDP packets.
	 * Param 		: 	Null
	 * Returns 		: 	Null
	*/

	// Open a UDP pocket and wait for ever
	public void open_udp_port() throws Exception {

		try {		
			System.out.println("Opening my UDP Port ");
			pocket = new DatagramSocket(myPort);

			// Creating a packet to hold the incoming UDP packet
			DatagramPacket packet = new DatagramPacket(new byte[256], 256);
			
			
			//Wait for ever
			while (true) {
				try {
					pocket.receive(packet);
					// System.out.println("packet received");
					process_pocket(packet);
					if (1 == 0) {
						break;
					}
				} catch (IOException e) {
					System.out.println(e);
				}
			}
			pocket.close();
		} catch (IOException ioe) {
			System.out.println("Error:" + ioe);
		}

	}
	
	/*
	 * Function Name:	process_pocket()()
	 * Description 	:	This function processes the UDP packet based on the packet type.
	 * Param packet	: 	UDP packet
	 * Returns 		: 	Null
	*/

	private void process_pocket(DatagramPacket packet) throws Exception {

		pack udp_pack = new pack(packet.getData(), packet.getLength(), packet.getAddress(), packet.getPort());
		byte result;

		// gfns.printbary(udp_pack.data);
		// System.out.println("packet length is " + packet.getLength());
		switch (udp_pack.getPkttype()) {
		
		
		//Packet for login
		case 1:
			
			//Stores the first byte of value returned by the validate_login method
			result = validate_login(udp_pack)[0];
			
			//If login is success the user's active status is updated
			if (result != (byte) 255) {
//				System.out.println("Login is success");
				update_status_host_table(udp_pack, 1);
			} else {
//				System.out.println("Login is failure");
			}
			
			//Send a reply to the user about the login success of failure
			send_reply(udp_pack, (byte) 81, result);

			break;
		
//			Packet for updating  user status to inactive while client exiting 
		case 2:
			update_status_host_table(udp_pack, 0);
			break;
		//Packet for updating the user status to active used by keep alive timer
		case 3:
			// System.out.println("received 3");
			update_status_host_table(udp_pack, 1);
			break;
		//Packet for  Registering New User
		case 5:
			
			register_user(udp_pack);
			break;
//			Packet for removing file entry from the file table published by the user  
		case 51:
			remove_file_entry(udp_pack);
			break;
//			Packet for  updating download count after successful download of each file
		case 31:
			update_dload_count(udp_pack);
			break;
		}

	}
	
	/*
	 * Function Name:	remove_file_entry()()
	 * Description 	:	This function removes the file entry from the db which is requested by the user. 
	 * Param udpPack: 	UDP packet
	 * Returns 		: 	Null
	*/

	private void remove_file_entry(pack udpPack) {

		byte[] lenghtF = new byte[2];
		int aIndex = 0;

		
		System.arraycopy(udpPack.data, 0, lenghtF, 0, 2);
		byte[] usrname = new byte[gfns.convBaryInt(lenghtF)];
		System
		.arraycopy(udpPack.data, 2, usrname, 0, gfns
				.convBaryInt(lenghtF));
		aIndex += 2 + gfns.convBaryInt(lenghtF);

		System.arraycopy(udpPack.data, aIndex, lenghtF, 0, 2);
		byte[] md = new byte[gfns.convBaryInt(lenghtF)];
		System.arraycopy(udpPack.data, aIndex + 2, md, 0, gfns
				.convBaryInt(lenghtF));
		aIndex += 2 + gfns.convBaryInt(lenghtF);

		stmt = null;
		pstmt = null;
		rs = null;
		System.out.println("Removing file entry for user " + new String(usrname));
		try {
			con = DriverManager
			.getConnection("jdbc:mysql://localhost/bootstrap?"
					+ "user=root&password=mysqlpwd");
			String sql = " DELETE from FILETABLE where USER = ? AND MSGDST = ?";
			pstmt = con.prepareStatement(sql);

			pstmt.setString(1, new String(usrname)); // Setting the status as
			// active
			pstmt.setString(2, gfns.ByteArraytohexString(md));
			pstmt.executeUpdate();
			con.close();
		}

		catch (SQLException ex) {
			System.out.println("SQLException: " + ex.getMessage());
			System.out.println("SQLState: " + ex.getSQLState());
			System.out.println("VendorError: " + ex.getErrorCode());

		}

	}

	/*
	 * Function Name:	update_dload_count()
	 * Description 	:	This function updates the download count of after successful download of each file.
	 * Param udpPack: 	UDP packet
	 * Returns 		: 	Null
	*/
	private void update_dload_count(pack udp_pack) {
		stmt = null;
		pstmt = null;
		rs = null;

		byte[] lenghtF = new byte[2];
		int index = 0;

		System.arraycopy(udp_pack.data, 0, lenghtF, 0, 2);
		byte[] usrname = new byte[gfns.convBaryInt(lenghtF)];
		System.arraycopy(udp_pack.data, 2, usrname, 0, gfns
				.convBaryInt(lenghtF));
		index = 2 + gfns.convBaryInt(lenghtF);

		System.arraycopy(udp_pack.data, index, lenghtF, 0, 2);
		byte[] md = new byte[gfns.convBaryInt(lenghtF)];
		System.arraycopy(udp_pack.data, 2 + index, md, 0, gfns
				.convBaryInt(lenghtF));
		System.out.println("updating download count for user " + new String(usrname));
		try {
			con = DriverManager
			.getConnection("jdbc:mysql://localhost/bootstrap?"
					+ "user=root&password=mysqlpwd");
			
			//Setting the parameters
			String sql = "UPDATE FILETABLE SET DOWNLOADS = DOWNLOADS + 1 WHERE USER = ? AND MSGDST = ?";
			
			pstmt = con.prepareStatement(sql);
			pstmt.setString(1, new String(usrname));// Setting the ip address
			pstmt.setString(2, gfns.ByteArraytohexString(md));// Setting the ip
			// address

			pstmt.executeUpdate();
			con.close();
		}

		catch (SQLException ex) {
			System.out.println("SQLException: " + ex.getMessage());
			System.out.println("SQLState: " + ex.getSQLState());
			System.out.println("VendorError: " + ex.getErrorCode());

		}

	}
	/*
	 * Function Name:	register_user()
	 * Description 	:	This function registers a new user with the username, password and email. 
	 * Param udpPack: 	UDP packet
	 * Returns 		: 	Null
	*/

	public void register_user(pack udpPack) throws Exception {
		byte[] lenghtF = new byte[2];
		int aIndex = 0, result = 0;
		
		//Username
		System.arraycopy(udpPack.data, 0, lenghtF, 0, 2);
		byte[] usrname = new byte[gfns.convBaryInt(lenghtF)];
		System.arraycopy(udpPack.data, 2, usrname, 0, gfns.convBaryInt(lenghtF));
		aIndex += 2 + gfns.convBaryInt(lenghtF);

		System.out.println("User: " + lenghtF);
		gfns.printbary(usrname);

		//Password
		System.arraycopy(udpPack.data, aIndex, lenghtF, 0, 2);
		byte[] passwd = new byte[gfns.convBaryInt(lenghtF)];
		System.arraycopy(udpPack.data, aIndex + 2, passwd, 0, gfns.convBaryInt(lenghtF));
		aIndex += 2 + gfns.convBaryInt(lenghtF);

		//System.out.println("Pwd: " + passwd);
		gfns.printbary(passwd);
		
		//Email
		System.arraycopy(udpPack.data, aIndex, lenghtF, 0, 2);
		byte[] email = new byte[gfns.convBaryInt(lenghtF)];
		System.arraycopy(udpPack.data, aIndex + 2, email, 0, gfns.convBaryInt(lenghtF));
		aIndex += 2 + gfns.convBaryInt(lenghtF);

		stmt = null;
		pstmt = null;
		rs = null;

		System.out.println("New user Registration -- " + new String(usrname));
		
		try {
			
			con = DriverManager.getConnection("jdbc:mysql://localhost/bootstrap?"+ "user=root&password=mysqlpwd");
			String sql = "INSERT INTO IPTABLE (IPADDR, HOST, PASSWORD, ACTIVE_STATUS, EMAIL,PORT_NO) VALUES(?,?,?,?,?,?)";
			pstmt = con.prepareStatement(sql);
			pstmt.clearParameters();
			//Setting the parameters
			pstmt.setString(1, udpPack.getIP().getHostAddress());
			pstmt.setString(2, new String(usrname));
			pstmt.setString(3, gfns.ByteArraytohexString(passwd));
			pstmt.setInt(4, 0);
			pstmt.setString(5, new String(email));
			pstmt.setInt(6, udpPack.getPort_no());
			result = pstmt.executeUpdate();
			if (result == 1) {
				System.out.println("Insertion successful");
			} else {
				System.out.println("Insertion failed");
			}
			
			//Sending the status about the insertion to the user
			send_reply(udpPack, (byte) 6, (byte) result);
			con.close();
		}

		catch (SQLException ex) {
			send_reply(udpPack, (byte) 6, (byte) 0);
			System.out.println("SQLException: " + ex.getMessage());
			System.out.println("SQLState: " + ex.getSQLState());
			System.out.println("VendorError: " + ex.getErrorCode());

		}

	}
	/*
	 * Function Name:	update_status_host_table()
	 * Description 	:	This function updates the active status of user in the db. 
	 * Param udpPack: 	UDP packet
	 * param status	:	Active status of user
	 * Returns 		: 	Null
	*/

	public void update_status_host_table(pack udp_pack, int status) {

		stmt = null;
		pstmt = null;
		rs = null;

		InetAddress IPAddr = udp_pack.getIP();

		byte[] lenghtF = new byte[2];

		System.arraycopy(udp_pack.data, 0, lenghtF, 0, 2);
		byte[] usrname = new byte[gfns.convBaryInt(lenghtF)];
		System.arraycopy(udp_pack.data, 2, usrname, 0, gfns
				.convBaryInt(lenghtF));
		
		//Adding the username and his active status in a hash map
		user_status_hash.put(new String(usrname), status);

		System.out.println("Updating user status for  " + new String(usrname) + " to " + status);
		try {
			con = DriverManager
			.getConnection("jdbc:mysql://localhost/bootstrap?"
					+ "user=root&password=mysqlpwd");
			
			//If active status
			if (status == 1) {
				String sql = "UPDATE IPTABLE SET ACTIVE_STATUS = ?, IPADDR = ?,PORT_NO = ? WHERE HOST = ?";
				
				//Setting the parameters
				pstmt = con.prepareStatement(sql);
				pstmt.setString(4, new String(usrname));// Setting the USERNAME
				pstmt.setInt(3, udp_pack.getPort_no());
				pstmt.setString(2, IPAddr.getHostAddress());
				pstmt.setInt(1, status); // Setting the status of F as active
				pstmt.executeUpdate();

				//				String sql1 = "UPDATE FILETABLE SET ACTIVE_STATUS = ?, IPADDR = ?,PORT_NO = ?  WHERE USER = ?";
				//				pstmt = con.prepareStatement(sql1);
				//				pstmt.setString(2, IPAddr.getHostAddress());
				//				pstmt.setInt(3, udp_pack.getPort_no());
				//				pstmt.setString(4, new String(usrname));// Setting the USERNAME
				//				pstmt.setInt(1, status); // Setting the status of F as active
				//				pstmt.executeUpdate();

			}
			
			//If inactive status
			else {
				String sql = "UPDATE IPTABLE SET ACTIVE_STATUS = ? WHERE IPADDR = ? AND HOST = ?";
				String sql1 = "UPDATE FILETABLE SET ACTIVE_STATUS = ? WHERE IPADDR = ? AND USER = ?";
				
				pstmt = con.prepareStatement(sql);
//				Setting the parameters
				pstmt.setString(3, new String(usrname));
				pstmt.setString(2, IPAddr.getHostAddress());// Setting the ip
				// address
				pstmt.setInt(1, status); // Setting the status of F as active
				pstmt.executeUpdate();

				pstmt = con.prepareStatement(sql1);
				pstmt.setString(3, new String(usrname));
				pstmt.setString(2, IPAddr.getHostAddress());// Setting the ip
				// address
				pstmt.setInt(1, status); // Setting the status of F as active
				pstmt.executeUpdate();
			}
			con.close();
		}

		catch (SQLException ex) {
			System.out.println("SQLException: " + ex.getMessage());
			System.out.println("SQLState: " + ex.getSQLState());
			System.out.println("VendorError: " + ex.getErrorCode());

		}
	}

	/*
	 * Function Name:	send_reply()
	 * Description 	:	This function updates the active status of user in the db. 
	 * Param reqPack: 	UDP packet
	 * param code 	:	Code which is updated in packet type 
	 * param res	:	Result
	 * Returns 		: 	Null
	*/
	public void send_reply(pack reqPack, byte code, byte res) throws Exception {
		// InetAddress myAddr;
		int servPort, cliPort;
		pack udp_pack;
		DatagramPacket pack;
		byte[] payload;

		switch (code) {
		
		//Reply for login status
		case (byte) 81:
			if (res != (byte) 255) {
				res = (byte) 1;
			} else
				res = (byte) 0;

		// myAddr=InetAddress.getLocalHost();

		//servPort = Integer.parseInt(prop.getProperty("Server_UDP_Port"));

		InetAddress cliAddr = reqPack.getIP();
		cliPort = reqPack.getPort_no();
		payload = new byte[] { 00, 01, res };
		udp_pack = new pack((byte) 81, (int) 1234, (byte) 16, myAddr,
				myPort, payload.length, payload);

		pack = new DatagramPacket(udp_pack.getPacket(), udp_pack
				.getPacket().length, cliAddr, cliPort);
		pocket.send(pack);
		break;
		
		//reply for insertion success or failure
		case (byte) 6:

			// myAddr=InetAddress.getLocalHost();

			//servPort = Integer.parseInt(prop.getProperty("Server_UDP_Port"));

			cliAddr = reqPack.getIP();
		cliPort = reqPack.getPort_no();
		//			cliPort = Integer.parseInt(prop.getProperty("Client_UDP_Port"));
		payload = new byte[] { 00, 01, res };
		udp_pack = new pack((byte) 6, (int) 1234, (byte) 16, myAddr,
				myPort, payload.length, payload);

		pack = new DatagramPacket(udp_pack.getPacket(), udp_pack
				.getPacket().length, cliAddr, cliPort);
		pocket.send(pack);
		break;
		
		//Notifying the user that he is not active 
		case (byte) 99:

			// myAddr=InetAddress.getLocalHost();

			//servPort = Integer.parseInt(prop.getProperty("Server_UDP_Port"));

		cliAddr = reqPack.getIP();
		cliPort =reqPack.getPort_no();
		//			cliPort = Integer.parseInt(prop.getProperty("Client_UDP_Port"));
		payload = new byte[] { 00, 01, res };
		udp_pack = new pack((byte) 99, (int) 1234, (byte) 16, myAddr,
				myPort, payload.length, payload);

		pack = new DatagramPacket(udp_pack.getPacket(), udp_pack
				.getPacket().length, cliAddr, cliPort);
		pocket.send(pack);
		break;

		}

	}
	
	/*
	 * Function Name:	validate_login()
	 * Description 	:	This function validates the user whether he is a valid or invalid based on his username and password 
	 * Param udpPack: 	UDP packet
	 * Returns 		: 	username in byte array format
	*/

	private byte[] validate_login(pack udpPack) {

		boolean blnResult = false;

		byte[] lenghtF = new byte[2];
		int aIndex = 0;
		
		//username and length
		System.arraycopy(udpPack.data, 0, lenghtF, 0, 2);
		byte[] usrname = new byte[gfns.convBaryInt(lenghtF)];
		System
		.arraycopy(udpPack.data, 2, usrname, 0, gfns
				.convBaryInt(lenghtF));
		aIndex += 2 + gfns.convBaryInt(lenghtF);
		
		//Password and length
		System.arraycopy(udpPack.data, aIndex, lenghtF, 0, 2);
		byte[] passwd = new byte[gfns.convBaryInt(lenghtF)];
		System.arraycopy(udpPack.data, aIndex + 2, passwd, 0, gfns
				.convBaryInt(lenghtF));
		aIndex += 2 + gfns.convBaryInt(lenghtF);



		stmt = null;
		pstmt = null;
		rs = null;

		try {
			con = DriverManager
			.getConnection("jdbc:mysql://localhost/bootstrap?"
					+ "user=root&password=mysqlpwd");
			String sql = "SELECT PASSWORD from  IPTABLE where HOST=?";
			pstmt = con.prepareStatement(sql);
			
			pstmt.setString(1, new String(usrname)); // Setting the status as
			// active
			rs = pstmt.executeQuery();
			while (rs.next()) {
				
				//Validating the password
				blnResult = Arrays.equals(passwd, gfns.hexStringToByteArray(rs.getString(1)));
				break;
			}
			con.close();

		}

		catch (SQLException ex) {
			System.out.println("SQLException: " + ex.getMessage());
			System.out.println("SQLState: " + ex.getSQLState());
			System.out.println("VendorError: " + ex.getErrorCode());

		}

		if (blnResult)
			return usrname;

		return new byte[] { (byte) 255 };
	}

	public static void main(String[] args) throws Exception {
		
		
		server s1 = new server();
		server_keepAlive ska = new server_keepAlive(s1);
		
		new Thread(ska).start();
		//		new Thread(t1).start();
		new Thread(s1).start();

	}
}
//Class to manage TCP connecitions in the server
class tcp_server implements Runnable {

	public Properties prop = new Properties();
	server c_server;

	public tcp_server(server s1) throws IOException {
		this.c_server = s1;
		prop.load(this.getClass().getResourceAsStream("/Resolver.Properties"));

	}

	public void run() {

		try {
			
			//Opens the TCP port and listens for any incoming TCP connections
			open_tcp_port();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
	/*
	 * Function Name:	open_tcp_port()
	 * Description 	:	This function opens the TCP port and starts a thread separately for TCP connections 
	 * Param 		: 	Null
	 * Returns 		: 	Null
	*/
	public void open_tcp_port() throws IOException {

		//	int servPort = Integer.parseInt(prop.getProperty("Server_TCP_Port"));

		ServerSocket server = new ServerSocket(c_server.myPort);
		System.out.println("Opening TCP Port on " + c_server.myPort);
		clientSocket newclient;

		while (true) {

			try {
				newclient = new clientSocket(server.accept(), c_server);
				Thread t = new Thread(newclient);
//				System.out.println("Creating a new thread for this req");
				//Starting a thread
				t.start();

			} catch (IOException e) {
				System.out.println("Server Accept failed" + e);
			}

		}

	}
}

//Class to run keep alive thread
class server_keepAlive implements Runnable {

	public Properties prop = new Properties();
	public genfunc gfns = new genfunc();
	public Connection con;
	public Statement stmt;
	public PreparedStatement pstmt;
	public ResultSet rs;
	public server k_server;

	server_keepAlive(server k_server) {
		this.k_server = k_server;
	}

	public void run() {
		while (true) {
			try {
				
				//Hash Map for user and his status
				create_hash(k_server.user_status_hash);
				// System.out.println("start sleep");
				Thread.currentThread();
				Thread.sleep(300000);//Sleep for 300 seconds
				System.out.println("Keep Alive Timer - Checking status");
				
				//
				String usrname;
				Iterator it = k_server.user_status_hash.keySet().iterator();
				while (it.hasNext()) {
					usrname = (String) it.next();
					if (k_server.user_status_hash.get(usrname) == 0) {
						System.out.println("usr name " + usrname + " - login timed out ");
						update_status(usrname, 0);
					}
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/*
	 * Function Name:	update_status()
	 * Description 	:	This function updates the user status into active or inactive
	 * Param usrname: 	username
	 * param int	:	active or inactive
	 * Returns 		: 	Null
	*/
	private void update_status(String usrname, int i) {
		stmt = null;
		pstmt = null;
		rs = null;

		try {
			con = DriverManager
			.getConnection("jdbc:mysql://localhost/bootstrap?"
					+ "user=root&password=mysqlpwd");

			String sql = "UPDATE IPTABLE SET ACTIVE_STATUS = ? WHERE HOST = ?";
			String sql1 = "UPDATE FILETABLE SET ACTIVE_STATUS = ? WHERE USER = ?";

			pstmt = con.prepareStatement(sql);
			pstmt.setString(2, usrname);
			pstmt.setInt(1, 0); // Setting the status of F as active
			pstmt.executeUpdate();

			pstmt = con.prepareStatement(sql1);
			pstmt.setString(2, usrname);
			pstmt.setInt(1, 0); // Setting the status of F as active
			pstmt.executeUpdate();
			con.close();
		}

		catch (SQLException ex) {
			System.out.println("SQLException: " + ex.getMessage());
			System.out.println("SQLState: " + ex.getSQLState());
			System.out.println("VendorError: " + ex.getErrorCode());

		}

	}
	
	/*
	 * Function Name		:	create_hash()
	 * Description 			:	This function creates an empty hash with the username and the status
	 * Param user_stat_hash	: 	User and the status
	 * Returns 				:	Null
	*/

	public void create_hash(Map<String, Integer> user_stat_hash)
	throws Exception {
		user_stat_hash.clear();
		con = DriverManager.getConnection("jdbc:mysql://localhost/bootstrap?"+ "user=root&password=mysqlpwd");
		String sql = "SELECT HOST from  IPTABLE where ACTIVE_STATUS=?";
		pstmt = con.prepareStatement(sql);

		pstmt.setInt(1, 1); // Setting the status as active
		rs = pstmt.executeQuery();
		while (rs.next()) {
			user_stat_hash.put(rs.getString("HOST"), 0);
		}
		con.close();
	}
}


