/* MKoda 
 * Peer to Peer File Sharing System with a search engine
 * Please follow the readme file for instructions on how to setup and run the system 
 * Written by : Arun Natarajan, Manikandan Sivanesan, Koushik Krishnakumar, Dinesh Radha Kirshan
*/
package client;

import general.genfunc;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.Random;

import packetformat.pack;


public class super_peer implements Runnable{

	public client_gui cgi;
	public InetAddress sp_contact_ip;
	public int sp_contact_port;
	public int loadFactor=0;

	public Connection con;
	public Statement stmt;
	public PreparedStatement pstmt;
	public ResultSet rs;

	public genfunc gfns = new genfunc();


	
	public ArrayList <hash_coord> my_hspace = new ArrayList <hash_coord>();
	public ArrayList <speers> sp_peers = new ArrayList <speers>();

	public ArrayList <Integer> recent_que = new ArrayList <Integer>();
	public ArrayList <child> children = new ArrayList <child>();
	public String myTable;
/*
Generates  a list of super peers
*/
	public void populate_speers () {

		this.sp_peers.clear();
		hash_coord hc = my_hspace.get(0);
		for (Iterator iterator = hc.neighbours.iterator(); iterator.hasNext();) {
			this.sp_peers.add((speers) iterator.next());

		}
	}
/*
	Type :Constructor 
	It creates a super peer and intialise the hash space for the super peer
	
*/
	public super_peer(client_gui cgi, InetAddress sp_address, int sp_port) {
		//	super();
		this.cgi = cgi;
		this.sp_contact_ip=sp_address;
		this.sp_contact_port=sp_port;
		myTable=cgi.myname + "_FILETABLE";
		
		//Allocates the whole hash space for the first super peer entering into the system
		if (sp_address == null){
			//my_hash_co = new hash_coord();
			hash_coord my_hash_co = new hash_coord();
			my_hash_co.setLower(new int[]{0,0,0,0,0});
			my_hash_co.setHigher(new int[]{1639,1639,1639,1639,1636});
			my_hash_co.setFiles(0);
			my_hspace.add(my_hash_co);
			System.out.println("Hurray.... I own the entire hash space... ");
			create_filetable();
			loadFactor+=table_entry_count();

		}
		
		//Get hash space from the already exisiting super peers
		else {
			System.out.println("I am going to get a split from  " + sp_address.getHostAddress() + " on port " + sp_port);
			Socket requestSocket = null;
			int cliPort = 0;
			try {
				requestSocket = new Socket(sp_address,sp_port);

				cliPort  = requestSocket.getLocalPort();

				OutputStream os = requestSocket.getOutputStream();
				InputStream in = requestSocket.getInputStream();

				byte[] payload = new byte[]{}; 

				pack tcp_stream = new pack((byte)71,(int)5678,(byte)16,cgi.myAddr,cgi.myPort,(int)payload.length,payload);

				if(requestSocket.isConnected()){
					os.write(tcp_stream.getPacket());
				}


				byte[] bary = new byte[in.read()];
				in.read(bary);
				gfns.printbary(bary);
				
				// Getting hash space from someother super peers
				addhash_space(bary);

				os.write(1);
				
				
				//Creates a new file table for each super peer which contains the file entries and the corresponding file info
				create_filetable();
				
				//Updates the loadfactor 
				loadFactor+=table_entry_count();
				
				// Getting the files in that particular hash space
				get_hash_files(os,in);

				os.write(1);

				requestSocket.close();
				os.close();
				in.close();
			} catch (Exception e) {
				e.printStackTrace();
				//return 0;
			}
		}

		cgi.suAddr=cgi.myAddr;
		cgi.suPort=cgi.myPort;
		child c = new child(cgi.myAddr,cgi.myPort);
		children.add(c);

	}

/*
 * 
 * Getting the hash files from someother super peer and inserts into super peer's filetable 

*/

	public int get_hash_files(OutputStream os, InputStream in) throws Exception {

		byte[] temp_holder ;
		byte[] tmp2=new byte[2];
		int no_of_files=0;
		while (true) {
			in.read(tmp2);
			if (gfns.convBaryInt(tmp2)==0) { break; }
			temp_holder = new byte[gfns.convBaryInt(tmp2)];
			in.read(temp_holder);

			int aIndex = 0;

			byte[] ipaddr = new byte[4];
			System.arraycopy(temp_holder, aIndex, ipaddr, 0, 4);
			aIndex += 4;

			byte[] port_no = new byte[2];
			System.arraycopy(temp_holder, aIndex, port_no, 0, 2);
			aIndex += 2;

			byte[] filemd = new byte[20];
			System.arraycopy(temp_holder, aIndex, filemd, 0, 20);
			aIndex += 20;

			byte[] filesize = new byte[4];
			System.arraycopy(temp_holder, aIndex, filesize, 0, 4);
			aIndex += 4;

			byte[] dload = new byte[2];
			System.arraycopy(temp_holder, aIndex, dload, 0, 2);
			aIndex += 2;

			byte[] filedigest = new byte[1024];
			System.arraycopy(temp_holder, aIndex, filedigest, 0, 1024);
			aIndex += 1024;					

			byte[] coord = new byte[10];
			System.arraycopy(temp_holder, aIndex, coord, 0, 10);
			aIndex += 10;

			System.arraycopy(temp_holder, aIndex, tmp2, 0, 2);
			byte[] filename = new byte[gfns.convBaryInt(tmp2)];
			System.arraycopy(temp_holder, aIndex + 2, filename, 0, gfns.convBaryInt(tmp2));
			aIndex += (2 + gfns.convBaryInt(tmp2));

			System.arraycopy(temp_holder, aIndex, tmp2, 0, 2);
			byte[] abtract = new byte[gfns.convBaryInt(tmp2)];
			System.arraycopy(temp_holder, aIndex + 2, abtract, 0, gfns.convBaryInt(tmp2));
			aIndex += (2 + gfns.convBaryInt(tmp2));


			System.arraycopy(temp_holder, aIndex, tmp2, 0, 2);
			byte[] usrname = new byte[gfns.convBaryInt(tmp2)];
			System.arraycopy(temp_holder, aIndex+2, usrname, 0, gfns.convBaryInt(tmp2));
			aIndex += (2 + gfns.convBaryInt(tmp2));


			try {

				con = DriverManager.getConnection("jdbc:mysql://localhost/bootdist?"+ "user=root&password=mysqlpwd");
				String sql = "INSERT INTO "+ myTable +" (IPADDR, USER, FILENAME, FILESIZE, FILEDIGEST, MSGDST,ABSTRACT,DOWNLOADS,PORT_NO,COORD) VALUES(?,?,?,?,?,?,?,?,?,?)";
				pstmt = con.prepareStatement(sql);
				pstmt.clearParameters();

				pstmt.setString(1, InetAddress.getByAddress(ipaddr).getHostAddress());
				pstmt.setString(2, new String(usrname));
				pstmt.setString(3, new String(filename));
				pstmt.setInt(4, gfns.convBaryInt(filesize));
				String hexString_fd = gfns.ByteArraytohexString(filedigest);
				pstmt.setString(5, hexString_fd);
				String hexString_md = gfns.ByteArraytohexString(filemd);
				pstmt.setString(6, hexString_md);
				pstmt.setString(7, new String(abtract));
				pstmt.setInt(8, gfns.convBaryInt(dload));
				pstmt.setInt(9, gfns.convBaryInt(port_no));
				pstmt.setString(10, gfns.ByteArraytohexString(coord));

				pstmt.executeUpdate();
				loadFactor++;
				con.close();
			}

			catch (SQLException ex) {
				System.out.println("SQLException: " + ex.getMessage());
				System.out.println("SQLState: " + ex.getSQLState());
				System.out.println("VendorError: " + ex.getErrorCode());
			}
		}
		return no_of_files;
	}


	//	@Override
	public void run() {
		System.out.println("This is a super peer");
	}
/*
 * Notifies the children and give the hash space to someother superpeer along with the fileinfo before shutting down

*/	public void shutdown_hash_space(){

		ArrayList <speers> update_list=new ArrayList <speers>();
		int[] higher_o= new int[5];
		int[] lower_o= new int[5];
		byte[] payload=null;
		byte[] return_bary=null;

		populate_speers();
		InetAddress ip_addr=sp_peers.get(0).IP_Addr;
		int port_no = sp_peers.get(0).port_no;
		
		
		notify_children(ip_addr,port_no);

		for (Iterator iterator = my_hspace.iterator(); iterator.hasNext();) {
			hash_coord hc = (hash_coord) iterator.next();


			higher_o=hc.getHigher();
			lower_o=hc.getLower();

			speers orig_sp=new speers(cgi.myAddr,cgi.myPort);
			speers new_sp=new speers(ip_addr,port_no);

			orig_sp.setHigher(higher_o);
			new_sp.setHigher(higher_o);
			orig_sp.setLower(lower_o);
			new_sp.setLower(lower_o);

			payload = new byte[26*2];
			System.arraycopy(orig_sp.get_in_bytes() , 0, payload, 0   , 26);
			System.arraycopy(new_sp.get_in_bytes()  , 0, payload, 26  , 26);

			return_bary = hc.get_in_bytes();

			for (Iterator iterator2 = hc.neighbours.iterator(); iterator2.hasNext();) {
				speers e = (speers) iterator2.next();

				Socket requestSocket = null;
				try {
					requestSocket = new Socket(e.getIP_Addr(),e.getPort_no());

					OutputStream os = requestSocket.getOutputStream();
					InputStream in = requestSocket.getInputStream();

					pack tcp_stream = new pack((byte)76,(int)5678,(byte)16,cgi.myAddr,cgi.myPort,(int)payload.length,payload);

					if(requestSocket.isConnected()){
						os.write(tcp_stream.getPacket());
					}

					in.read();
					requestSocket.close();
					os.close();
					in.close();
				} catch (Exception e1) {
					e1.printStackTrace();
					//return 0;
				}
			}

			// Now actually send the bytes to the next peer


			Socket requestSocket = null;
			try {
				requestSocket = new Socket(ip_addr,port_no);

				OutputStream os = requestSocket.getOutputStream();
				InputStream in = requestSocket.getInputStream();

			//	System.out.println("Shutdown : Copying this many bytes and they are "+ hc.get_in_bytes().length);
				payload = new byte[hc.get_in_bytes().length];
				System.arraycopy(hc.get_in_bytes(), 0, payload, 0, payload.length);
			//	gfns.printbary(payload);
				pack tcp_stream = new pack((byte)73,(int)5678,(byte)16,cgi.myAddr,cgi.myPort,(int)payload.length,payload);

				if(requestSocket.isConnected()){
					os.write(tcp_stream.getPacket());
				}

				in.read();
				
				send_hash_files(os, in, hc);
				os.write(gfns.convIntBary_2(0));

				in.read();
				requestSocket.close();
				os.close();
				in.close();
			} catch (Exception e1) {
				e1.printStackTrace();
				//return 0;
			}
		}
	}
/*
	Sends a UDP packet to each children to notify that superpeer is leaving
*/
	private void notify_children(InetAddress ia, int port_no) {

	//	System.out.println("giving ip address and port as " + ia.getHostAddress() + " " + port_no);

		byte[] payload = new byte[6];

		System.arraycopy(ia.getAddress(), 0, payload, 0, 4);
		System.arraycopy(gfns.convIntBary_2(port_no), 0, payload, 4, 2);

		for (Iterator iterator = children.iterator(); iterator.hasNext();) {
			child c = (child) iterator.next();


			pack udp_pack = new pack((byte) 8, (int) 1234, (byte) 16, cgi.myAddr,cgi.myPort, payload.length, payload);
			DatagramPacket pack = new DatagramPacket(udp_pack.getPacket(), udp_pack.getPacket().length, c.getIP_Addr(), c.getPort_no());
			try {
				cgi.pocket.send(pack);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
/*
 * Partitions the hashspace and give a chunk to other super peers

*/	public byte[] break_hash_space(InetAddress ip_addr, int port_no) {

		byte[] return_bary;
		//if there are many parts of the hash space that is managed, give any one hash space to the request, may be the first one
		hash_coord send_hash_coord = my_hspace.get(my_hspace.size()-1);
		int[] higher_o= new int[5];
		int[] lower_o= new int[5];
		int[] higher_n= new int[5];
		int[] lower_n = new int[5];
		byte byteval=75;
		byte[] payload=null;

		ArrayList <speers> update_list=new ArrayList <speers>();

		if (my_hspace.size()>1)
		{
			byteval=76;

			for (Iterator iterator = send_hash_coord.neighbours.iterator(); iterator.hasNext();) {
				speers e = (speers) iterator.next();
				update_list.add(e);
			}


			higher_o=send_hash_coord.getHigher();
			lower_o=send_hash_coord.getLower();

			speers orig_sp=new speers(cgi.myAddr,cgi.myPort);
			speers new_sp=new speers(ip_addr,port_no);

			orig_sp.setHigher(higher_o);
			new_sp.setHigher(higher_o);
			orig_sp.setLower(lower_o);
			new_sp.setLower(lower_o);

			payload = new byte[26*2];
			System.arraycopy(orig_sp.get_in_bytes() , 0, payload, 0   , 26);
			System.arraycopy(new_sp.get_in_bytes()  , 0, payload, 26  , 26);

			return_bary = send_hash_coord.get_in_bytes();
			my_hspace.remove(send_hash_coord);
		}
		//else if you have only one chunk, break it and give it to that guy
		else
		{
			byteval=75;

			speers orig_sp=new speers(cgi.myAddr,cgi.myPort);
			speers new_sp_1=new speers(cgi.myAddr,cgi.myPort);
			speers new_sp_2=new speers(ip_addr,port_no);

			hash_coord new_hc = new hash_coord();

			higher_o=send_hash_coord.getHigher();
			lower_o=send_hash_coord.getLower();
			orig_sp.setHigher(higher_o);
			orig_sp.setLower(lower_o);

			System.arraycopy(higher_o, 0, higher_n, 0, 5);
			System.arraycopy(lower_o, 0, lower_n, 0, 5);

			new_hc.setHigher(higher_n);
			new_hc.setLower(lower_n);

			new_sp_1.setHigher(higher_o);
			new_sp_1.setLower(lower_o);

			new_sp_2.setHigher(higher_o);
			new_sp_2.setLower(lower_o);

			for (Iterator iterator = send_hash_coord.neighbours.iterator(); iterator.hasNext();) {
				speers e = (speers) iterator.next();
				new_hc.neighbours.add(e);
				update_list.add(e);
			}


			//			System.out.println("These are the coordinates, before," );
			//			System.out.println("higher o " +  higher_o[0] + " " +higher_o[1] + " " +higher_o[2] + " " +higher_o[3] + " " +higher_o[4] + " ");
			//			System.out.println("lower o " +  lower_o[0] + " " +lower_o[1] + " " +lower_o[2] + " " +lower_o[3] + " " +lower_o[4] + " ");
			//			System.out.println("higher n " +  higher_n[0] + " " +higher_n[1] + " " +higher_n[2] + " " +higher_n[3] + " " +higher_n[4] + " ");
			//			System.out.println("lower n " +  lower_n[0] + " " +lower_n[1] + " " +lower_n[2] + " " +lower_n[3] + " " +lower_n[4] + " ");


			//			Random randomGenerator = new Random();
			//			int randomInt= randomGenerator.nextInt(5);
			//			//randomInt=0;
			//
			//			higher_o[randomInt]=higher_o[randomInt]-((int) Math.ceil((higher_o[randomInt]-lower_o[randomInt]) / 2));
			//			lower_n[randomInt]=higher_o[randomInt]+1;



			//Choose a random no and split based on that dimension, based on load
			Random randomGenerator = new Random();
			int randomInt= randomGenerator.nextInt(5);
				randomInt=0;

			stmt = null;
			pstmt = null;
			rs = null;

			ArrayList <Integer> points = new ArrayList <Integer>();

			try {
				con = DriverManager.getConnection("jdbc:mysql://localhost/bootdist?" + "user=root&password=mysqlpwd");
				String sql= "SELECT COORD from "  + myTable + " ";
				pstmt = con.prepareStatement(sql);
				rs=pstmt.executeQuery();

				while (rs.next()) {
					points.add(gfns.byteArraytoIntArray(gfns.hexStringToByteArray(rs.getString("COORD")))[randomInt]);
					//	System.out.println("adding " + gfns.byteArraytoIntArray(gfns.hexStringToByteArray(rs.getString("COORD")))[randomInt]);
				}
				con.close();
			}
			catch (SQLException ex) {
				System.out.println("SQLException: " + ex.getMessage());
				System.out.println("SQLState: " + ex.getSQLState());
				System.out.println("VendorError: " + ex.getErrorCode());
			}

			Collections.sort(points);
			//points.get(points.size()/2);
			//	System.out.println("this is the points size " + points.size()+ "size/2 is " + points.size() / 2 + " and value is "+ points.get(points.size() / 2));
			higher_o[randomInt]=points.get(points.size()/2);
			lower_n[randomInt]=higher_o[randomInt]+1;			


			send_hash_coord.setHigher(higher_o);
			new_hc.setLower(lower_n);

			new_sp_1.setHigher(higher_o);
			new_sp_2.setLower(lower_n);


			speers my_sp=new speers(cgi.myAddr,cgi.myPort);
			my_sp.setHigher(higher_o);
			my_sp.setLower(lower_o);
			new_hc.neighbours.add(my_sp);
			//	validate_neighbours(new_hc);


			my_sp=new speers(ip_addr,port_no);
			my_sp.setHigher(higher_n);
			my_sp.setLower(lower_n);
			//my_sp.setHspace_cord(new_hc);
			send_hash_coord.neighbours.add(my_sp);
			//			validate_neighbours(send_hash_coord);

			my_hspace.clear();
			my_hspace.add(send_hash_coord);

			return_bary = new_hc.get_in_bytes();	


			//notify the neighbors about the split

			payload = new byte[26 * 3]; 

			System.arraycopy(orig_sp.get_in_bytes() , 0, payload, 0   , 26);
			System.arraycopy(new_sp_1.get_in_bytes(), 0, payload, 26  , 26);
			System.arraycopy(new_sp_2.get_in_bytes(), 0, payload, 26*2, 26);
		}

		for (Iterator iterator = update_list.iterator(); iterator.hasNext();) {
			speers sp = (speers) iterator.next();


			Socket requestSocket = null;
			int cliPort = 0;
			try {
				//System.out.println(("Connect to IP address and port "+ sp.getIP_Addr() + " " + sp.getPort_no()) + " "); 
				requestSocket = new Socket(sp.getIP_Addr(),sp.getPort_no());

				cliPort  = requestSocket.getLocalPort();

				OutputStream os = requestSocket.getOutputStream();
				InputStream in = requestSocket.getInputStream();

				pack tcp_stream = new pack((byte)byteval,(int)5678,(byte)16,cgi.myAddr,cgi.myPort,(int)payload.length,payload);

				if(requestSocket.isConnected()){
					os.write(tcp_stream.getPacket());
				}

				in.read();

				requestSocket.close();
				os.close();
				in.close();
			} catch (Exception e) {
				e.printStackTrace();
				//return 0;
			}
		}
		return return_bary;
	}
/*
 * Super peer updates the hash space information provided by the other super peers.


*/	public void update_neighbours(byte[] repdata, int type) {

		byte[] orig_hsp=new byte[26];
		byte[] new_hsp_1=new byte[26];
		byte[] new_hsp_2=new byte[26];

		System.arraycopy(repdata, 0, orig_hsp, 0, 26);
		System.arraycopy(repdata, 26, new_hsp_1, 0, 26);
		speers new_sp_1=new speers(new_hsp_1);
		speers new_sp_2=null;

		if(type==1){
			System.arraycopy(repdata, 26*2, new_hsp_2, 0, 26);
			new_sp_2=new speers(new_hsp_2);

		}

		for (Iterator iterator = my_hspace.iterator(); iterator.hasNext();) {
			hash_coord hc = (hash_coord) iterator.next();
			int match =0;
			speers sp=null;
			for (Iterator iterator2 = hc.neighbours.iterator(); iterator2.hasNext();) {
				sp = (speers) iterator2.next();
		//		System.out.println("thi is my ip "+ sp.getIP_Addr().getHostAddress() + " and port no "+ sp.getPort_no());
				if (Arrays.equals(sp.get_in_bytes(), orig_hsp)){
					match=1;
					break;
				}
			}
			if (match==1) {
				match=0;					
				hc.neighbours.remove(sp);
				hc.neighbours.add(new_sp_1);
				if (type==1){
					hc.neighbours.add(new_sp_2);
				}
			}
		}

	}
/*
 * Adds the hashspace whose coordinates are in byte array format

*/	public void addhash_space(byte[] bary) {

		int[] iary= new int[5];

		byte tmpa[]=new byte[2];

	//	System.out.println("This os the bary received in a hash space");
	//	gfns.printbary(bary);

		hash_coord my_hash_co = new hash_coord();

		for (int i = 0; i < iary.length; i++) {
			System.arraycopy(bary, i*2, tmpa, 0 , 2);
			iary[i]=gfns.convBaryInt(tmpa);
		}
		my_hash_co.setLower(iary);

		for (int i = 0; i < iary.length; i++) {
			System.arraycopy(bary, i*2+10, tmpa , 0, 2);
			iary[i]=gfns.convBaryInt(tmpa);
		}
		my_hash_co.setHigher(iary);

		System.arraycopy(bary, 20, tmpa, 0, 2);
		my_hash_co.setFiles(gfns.convBaryInt(tmpa));

		speers sp_thc;
		//	hash_coord thc;
		int index=23;
		byte spbary[]=new byte[26]; 
		for (int i = 0; i < bary[22]; i++) {

			System.arraycopy(bary, index, spbary, 0, 26);
			index+=26;
			sp_thc=new speers(spbary);
			my_hash_co.neighbours.add(sp_thc);

		}
		my_hspace.add(my_hash_co);


	}



/*
	File table created in each super peer to manage the files published by the peers
*/
	void create_filetable(){

		stmt = null;
		pstmt = null;
		rs = null;

	//	System.out.println("Creating new file table for the user " + cgi.myname);

		try {

			con = DriverManager.getConnection("jdbc:mysql://localhost/bootdist?" + "user=root&password=mysqlpwd");
			String sql= "CREATE TABLE " + myTable +" (IPADDR varchar(15)," +" USER varchar(40) , FILEDIGEST varchar(2048)," +
			" FILENAME varchar(200), FILESIZE int(11), MSGDST varchar(40), " +"ABSTRACT varchar(100), DOWNLOADS int(10), PORT_NO double(17,0), COORD varchar(20)," +
			" PRIMARY KEY (USER, MSGDST))";
			pstmt = con.prepareStatement(sql);
			pstmt.executeUpdate();
			con.close();
		}

		catch (SQLException ex) {

			System.out.println("SQLException: " + ex.getMessage());
			System.out.println("SQLState: " + ex.getSQLState());
			System.out.println("VendorError: " + ex.getErrorCode());

		}

	}

	// No of entries in the file table of a super peer

	private int table_entry_count() {

		int no_of_entries=0;

		stmt = null;
		pstmt = null;
		rs = null;

		try {
			con = DriverManager.getConnection("jdbc:mysql://localhost/bootdist?" + "user=root&password=mysqlpwd");
			String sql= "SELECT COUNT(*) from "  + myTable + " ";
			pstmt = con.prepareStatement(sql);
			rs=pstmt.executeQuery();

			while (rs.next()) {
				no_of_entries=rs.getInt("COUNT(*)");
				break;
			}
			con.close();
		}

		catch (SQLException ex) {
			System.out.println("SQLException: " + ex.getMessage());
			System.out.println("SQLState: " + ex.getSQLState());
			System.out.println("VendorError: " + ex.getErrorCode());
		}

	//	System.out.println("the no of entries " + no_of_entries);

		return no_of_entries;
	}




/*
	Super peer adding the filedigest published by the peers in its filetable

*/
	public int upload_file_digest(pack tcp_pack) throws Exception {

		System.out.println("New file upload received from " + tcp_pack.getIP().getHostAddress());

		byte[] lengthF = new byte[2];
		int aIndex = 0, result = 0;

		System.arraycopy(tcp_pack.getData(), 0, lengthF, 0, 2);
		byte[] usrname = new byte[gfns.convBaryInt(lengthF)];
		System.arraycopy(tcp_pack.getData(), 2, usrname, 0, gfns.convBaryInt(lengthF));
		aIndex += (2 + gfns.convBaryInt(lengthF));

		System.arraycopy(tcp_pack.getData(), aIndex, lengthF, 0, 2);
		byte[] filename = new byte[gfns.convBaryInt(lengthF)];
		System.arraycopy(tcp_pack.getData(), aIndex + 2, filename, 0, gfns.convBaryInt(lengthF));
		aIndex += (2 + gfns.convBaryInt(lengthF));

		System.arraycopy(tcp_pack.getData(), aIndex, lengthF, 0, 2);
		byte[] abtract = new byte[gfns.convBaryInt(lengthF)];
		System.arraycopy(tcp_pack.getData(), aIndex + 2, abtract, 0, gfns.convBaryInt(lengthF));
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

		int[] f_coord = file_coord(filedigest);

		if (is_myhash_space(f_coord)) {
			try {

				con = DriverManager.getConnection("jdbc:mysql://localhost/bootdist?"+ "user=root&password=mysqlpwd");
				String sql = "INSERT INTO "+ myTable +" (IPADDR, USER, FILENAME, FILESIZE, FILEDIGEST, MSGDST,ABSTRACT,DOWNLOADS,PORT_NO,COORD) VALUES(?,?,?,?,?,?,?,?,?,?)";
				pstmt = con.prepareStatement(sql);
				pstmt.clearParameters();

				pstmt.setString(1, tcp_pack.getHeader_IP().getHostAddress());
				pstmt.setString(2, new String(usrname));
				pstmt.setString(3, new String(filename));
				pstmt.setInt(4, gfns.convBaryInt(filesize));
				String hexString_fd = gfns.ByteArraytohexString(filedigest);
				pstmt.setString(5, hexString_fd);
				String hexString_md = gfns.ByteArraytohexString(filemd);
				pstmt.setString(6, hexString_md);
				pstmt.setString(7, new String(abtract));
				pstmt.setInt(8, 0);
				pstmt.setInt(9, tcp_pack.getHeader_port_no());
				pstmt.setString(10, gfns.ByteArraytohexString(gfns.intArraytoByteArray(f_coord)));

				result = pstmt.executeUpdate();
				loadFactor++;
				con.close();
			}

			catch (SQLException ex) {
				System.out.println("SQLException: " + ex.getMessage());
				System.out.println("SQLState: " + ex.getSQLState());
				System.out.println("VendorError: " + ex.getErrorCode());
			}


			if (result == 0) {
			} else {
				result =1;
			}
		}
		else {

			InetAddress ia = null;
			int port =0 ;

			for (Iterator iterator = my_hspace.iterator(); iterator.hasNext();) {
				hash_coord hc = (hash_coord) iterator.next();
				speers sp=null;
				for (Iterator iterator2 = hc.neighbours.iterator(); iterator2.hasNext();) {
					speers e = (speers) iterator2.next();
					if (point_check(f_coord, e.getLower(), e.getHigher())){
						ia=e.getIP_Addr();
						port=e.getPort_no();
						break;
					}
				}
			}
			if ( ia == null ){
				result = 0;
				System.out.println(" Error some where the hash space not found at all, coords are " + f_coord[0] + " "+ f_coord[1] + " "+ f_coord[2] + " "+ f_coord[3] + " "+ f_coord[4] + " ");				
			}

			Socket clientSocket=new Socket(ia,port);
			OutputStream os = clientSocket.getOutputStream();
			InputStream in = clientSocket.getInputStream();

			if(clientSocket.isConnected()){
				os.write(tcp_pack.getPacket());
			}

			if (in.read()==1){
				result =1;
			}
			else{
				result=0;
			}
			clientSocket.close();
			os.close();
		}


		if (result == 0) {
			System.out.println("Insertion failed");
		} else {
			System.out.println("Insertion successful");
			result =1;
		}

		return result;

	}
/*
 * Checks whether it belongs to its hash space based on the given coordinates
	
*/
	private boolean is_myhash_space(int[] f_coord) {

		boolean result = false;

		for (Iterator iterator = my_hspace.iterator(); iterator.hasNext();) {
			hash_coord hc = (hash_coord) iterator.next();
			result = point_check(f_coord, hc.getLower(), hc.getHigher());
			if ( result == true ) { return true; }
		}
		return false;
	}



	public static boolean point_check(int [] x, int[] lower, int[] higher)
	{

		boolean result = true ;
		for(int i = 0; i<5; i++)
		{
			if((x[i] < lower[i]) ||  (x[i] > higher[i])){
				result = false;
				break;
			}
		}
		return result;
	}

//  File coordinates returned in integer array
	public int[] file_coord(byte[] b) {
		//byte[] b = {(byte) 128,(byte) 128};
		BitCounter bd = new BitCounter(b);
		int bitcount = 0,count =0, j=0;
		int[] IntArray = new int[5];
		while(!bd.isEnd())
		{
			count++;
			if(bd.nextBit()==1) {
				bitcount++;
			}

			if(count == 1639)
			{
				count = 0;
				IntArray[j] = bitcount;
				bitcount =0;
				j++;
			}


		}
		return IntArray;

	}
/*
 * Unpublish the file info from the super peer file table if it belongs to its hash space else 
 * send it to the respective super peer

*/	public void unpublish_file(pack udpPack) throws Exception {

		byte[] tmp2 = new byte[2];
		int aIndex = 0;

		int[] f_coord = new int[5];

		System.arraycopy(udpPack.data, 0, tmp2, 0, 2);
		byte[] usrname = new byte[gfns.convBaryInt(tmp2)];
		System.arraycopy(udpPack.data, 2, usrname, 0, gfns.convBaryInt(tmp2));
		aIndex += 2 + gfns.convBaryInt(tmp2);

		System.arraycopy(udpPack.data, aIndex, tmp2, 0, 2);
		byte[] md = new byte[gfns.convBaryInt(tmp2)];
		System.arraycopy(udpPack.data, aIndex + 2, md, 0, gfns.convBaryInt(tmp2));
		aIndex += 2 + gfns.convBaryInt(tmp2);

		for (int j = 0; j < f_coord.length; j++) {
			System.arraycopy(udpPack.data, aIndex, tmp2, 0 , 2);
			aIndex+=2;
			f_coord[j]=gfns.convBaryInt(tmp2);
		}

		if (is_myhash_space(f_coord)) {

			stmt = null;
			pstmt = null;
			rs = null;
			System.out.println("Removing file entry for user " + new String(usrname));
			try {
				con = DriverManager
				.getConnection("jdbc:mysql://localhost/bootdist?"+ "user=root&password=mysqlpwd");
				String sql = " DELETE from "+ myTable + " where USER = ? AND MSGDST = ?";
				pstmt = con.prepareStatement(sql);

				pstmt.setString(1, new String(usrname)); 
				pstmt.setString(2, gfns.ByteArraytohexString(md));
				pstmt.executeUpdate();
				loadFactor--;
				con.close();
			}

			catch (SQLException ex) {
				System.out.println("SQLException: " + ex.getMessage());
				System.out.println("SQLState: " + ex.getSQLState());
				System.out.println("VendorError: " + ex.getErrorCode());

			}
		}
		else {

			InetAddress ia = null;
			int port =0 ;

			for (Iterator iterator = my_hspace.iterator(); iterator.hasNext();) {
				hash_coord hc = (hash_coord) iterator.next();
				speers sp=null;
				for (Iterator iterator2 = hc.neighbours.iterator(); iterator2.hasNext();) {
					speers e = (speers) iterator2.next();
					if (point_check(f_coord, e.getLower(), e.getHigher())){
						ia=e.getIP_Addr();
						port=e.getPort_no();
						break;
					}
				}
			}

			DatagramPacket pack = new DatagramPacket(udpPack.getPacket(),udpPack.getPacket().length,ia,port);					    
			cgi.pocket.send(pack);

		}

	}

/*
Searches the file in each super peer and the query will be sent only once to each super peer.
If search querry matches the result is sent in UDP packet

*/
	public void file_search(pack udpPack) throws Exception {

		//System.out.println("Search query received from " + udpPack.getHeader_IP().getHostAddress() + " Port " + udpPack.getHeader_port_no());
		
		
		if ( recent_que.contains(udpPack.getID())){
			return;
		}
		recent_que.add(udpPack.getID());
		
		BitSet text_search_bits = new BitSet(8192);
		BitSet filedigest_bits = new BitSet(8192);

		int index = 0, rec_count = 0;
		int lengthF = 0;

		// The last 1024 is the signature of the searched string
		byte[] text_searched = new byte[1024];

		System.arraycopy(udpPack.getData(), udpPack.getData().length - 1024,text_searched, 0, 1024);
		text_search_bits = gfns.fromByteArray(text_searched);

		con = DriverManager.getConnection("jdbc:mysql://localhost/bootdist?"+ "user=root&password=mysqlpwd");
		String sql = "SELECT * from  " + myTable + " ORDER BY DOWNLOADS DESC";
		pstmt = con.prepareStatement(sql);
		rs = pstmt.executeQuery();

		while (rs.next()) {

			byte[] temp_holder = new byte[256];
			filedigest_bits = gfns.fromByteArray(gfns.hexStringToByteArray(rs.getString("FILEDIGEST")));
			filedigest_bits.and(text_search_bits);
			if (filedigest_bits.equals(text_search_bits)) {
				// System.out.println("a hit");

				index = 0;
				System.arraycopy(gfns.getIpAsArrayOfByte(rs.getString("IPADDR")), 0,temp_holder, index, 4);
				index += 4;

				System.arraycopy(gfns.convIntBary_2(rs.getInt("PORT_NO")), 0,temp_holder, index, 2);
				index += 2;

				System.arraycopy(gfns.hexStringToByteArray(rs.getString("MSGDST")), 0, temp_holder, index, 20);
				index += 20;

				System.arraycopy(gfns.convIntBary(rs.getInt("FILESIZE")), 0,temp_holder, index, 4);
				index += 4;

				System.arraycopy(gfns.convIntBary_2(rs.getInt("DOWNLOADS")), 0,temp_holder, index, 2);
				index += 2;

				lengthF = rs.getString("FILENAME").length();
				System.arraycopy(gfns.convIntBary_2(lengthF), 0, temp_holder,index, 2);
				index += 2;

				System.arraycopy(rs.getString("FILENAME").getBytes(), 0,temp_holder, index,rs.getString("FILENAME").getBytes().length);
				index += rs.getString("FILENAME").getBytes().length;

				lengthF = rs.getString("ABSTRACT").length();
				System.arraycopy(gfns.convIntBary_2(lengthF), 0, temp_holder,index, 2);
				index += 2;

				System.arraycopy(rs.getString("ABSTRACT").getBytes(), 0,temp_holder, index,rs.getString("ABSTRACT").getBytes().length);
				index += rs.getString("ABSTRACT").getBytes().length;

				lengthF = rs.getString("USER").length();
				System.arraycopy(gfns.convIntBary_2(lengthF), 0, temp_holder,index, 2);
				index += 2;

				System.arraycopy(rs.getString("USER").getBytes(), 0,temp_holder, index,rs.getString("USER").getBytes().length);
				index += rs.getString("USER").getBytes().length;


				pack udp_pack = new pack((byte)122, (int) udpPack.getID(), (byte) 16, cgi.myAddr,cgi.myPort, temp_holder.length, temp_holder);

				DatagramPacket pack = new DatagramPacket(udp_pack.getPacket(), udp_pack.getPacket().length, udpPack.getHeader_IP(), udpPack.getHeader_port_no());
				cgi.pocket.send(pack);

			}
		}
		con.close();
	}
/*
 * Transfers the hash files from one super peer to another super peer 

*/	public int send_hash_files(OutputStream out, InputStream in, hash_coord hc) throws Exception {

		int no_of_files=0;
		// send all the files
		con = DriverManager.getConnection("jdbc:mysql://localhost/bootdist?"+ "user=root&password=mysqlpwd");
		String sql = "SELECT * from  " + myTable + " ";
		pstmt = con.prepareStatement(sql);
		rs = pstmt.executeQuery();
		int index,lengthF;
		while (rs.next()) {
			if ( hc==null){
				if (is_myhash_space(gfns.byteArraytoIntArray(gfns.hexStringToByteArray(rs.getString("COORD"))))) { continue; }
			}else if ( ! point_check( gfns.byteArraytoIntArray(gfns.hexStringToByteArray(rs.getString("COORD"))) , hc.getLower(), hc.getHigher()) ) { 
				continue; 
			}

			byte[] temp_holder = new byte[1500];

			index = 0;
			System.arraycopy(gfns.getIpAsArrayOfByte(rs.getString("IPADDR")), 0,temp_holder, index, 4);
			index += 4;

			System.arraycopy(gfns.convIntBary_2(rs.getInt("PORT_NO")), 0,temp_holder, index, 2);
			index += 2;

			String md=rs.getString("MSGDST");
			System.arraycopy(gfns.hexStringToByteArray(rs.getString("MSGDST")), 0, temp_holder, index, 20);
			index += 20;

			System.arraycopy(gfns.convIntBary(rs.getInt("FILESIZE")), 0,temp_holder, index, 4);
			index += 4;

			System.arraycopy(gfns.convIntBary_2(rs.getInt("DOWNLOADS")), 0,temp_holder, index, 2);
			index += 2;

			System.arraycopy(gfns.hexStringToByteArray(rs.getString("FILEDIGEST")), 0,temp_holder, index, 1024);
			index += 1024;

			System.arraycopy(gfns.hexStringToByteArray(rs.getString("COORD")), 0,temp_holder, index, 10);
			index += 10;

			lengthF = rs.getString("FILENAME").length();
			System.arraycopy(gfns.convIntBary_2(lengthF), 0, temp_holder,index, 2);
			index += 2;

			System.arraycopy(rs.getString("FILENAME").getBytes(), 0,temp_holder, index,rs.getString("FILENAME").getBytes().length);
			index += rs.getString("FILENAME").getBytes().length;

			lengthF = rs.getString("ABSTRACT").length();
			System.arraycopy(gfns.convIntBary_2(lengthF), 0, temp_holder,index, 2);
			index += 2;

			System.arraycopy(rs.getString("ABSTRACT").getBytes(), 0,temp_holder, index,rs.getString("ABSTRACT").getBytes().length);
			index += rs.getString("ABSTRACT").getBytes().length;

			lengthF = rs.getString("USER").length();
			System.arraycopy(gfns.convIntBary_2(lengthF), 0, temp_holder,index, 2);
			index += 2;

			String usrname=rs.getString("USER");
			System.arraycopy(rs.getString("USER").getBytes(), 0,temp_holder, index,rs.getString("USER").getBytes().length);
			index += rs.getString("USER").getBytes().length;

			out.write(gfns.convIntBary_2(index));
			out.write(temp_holder, 0, index);

			
			stmt  = null;
			pstmt = null;
			//rs = null;
			try {
				con = DriverManager.getConnection("jdbc:mysql://localhost/bootdist?"+ "user=root&password=mysqlpwd");
				String sql1 = " DELETE from "+ myTable + " where USER = ? AND MSGDST = ?";
				pstmt = con.prepareStatement(sql1);

				pstmt.setString(1, usrname); 
				pstmt.setString(2, md);
				pstmt.executeUpdate();
				loadFactor--;
				con.close();
			}

			catch (SQLException ex) {
				System.out.println("SQLException: " + ex.getMessage());
				System.out.println("SQLState: " + ex.getSQLState());
				System.out.println("VendorError: " + ex.getErrorCode());
			}
		}
		con.close();
		return no_of_files;				
	}
/*
	Updates the download after succeessful download
*/	public void update_dload_count(pack udp_pack) {
		stmt = null;
		pstmt = null;
		rs = null;

		byte[] lenghtF = new byte[2];
		int index = 0;

		System.arraycopy(udp_pack.data, 0, lenghtF, 0, 2);
		byte[] usrname = new byte[gfns.convBaryInt(lenghtF)];
		System.arraycopy(udp_pack.data, 2, usrname, 0, gfns.convBaryInt(lenghtF));
		index = 2 + gfns.convBaryInt(lenghtF);

		System.arraycopy(udp_pack.data, index, lenghtF, 0, 2);
		byte[] md = new byte[gfns.convBaryInt(lenghtF)];
		System.arraycopy(udp_pack.data, 2 + index, md, 0, gfns.convBaryInt(lenghtF));
		System.out.println("updating download count for user " + new String(usrname));
		try {
			con = DriverManager.getConnection("jdbc:mysql://localhost/bootdist?"+ "user=root&password=mysqlpwd");
			String sql = "UPDATE "+ myTable +"  SET DOWNLOADS = DOWNLOADS + 1 WHERE USER = ? AND MSGDST = ?";

			pstmt = con.prepareStatement(sql);
			pstmt.setString(1, new String(usrname));// Setting the ip address
			pstmt.setString(2, gfns.ByteArraytohexString(md));// Setting the ip

			pstmt.executeUpdate();
			con.close();
		}

		catch (SQLException ex) {
			System.out.println("SQLException: " + ex.getMessage());
			System.out.println("SQLState: " + ex.getSQLState());
			System.out.println("VendorError: " + ex.getErrorCode());
		}
	}


	public void update_children(pack udpPack) {

		child c = new child(udpPack.getHeader_IP(),udpPack.getHeader_port_no());
		children.add(c);

	}
/*
Sends the search query to the super peers based on the level chosen and sends it to the super peer only once.

	*/public void send_fsearch(pack udpPack) throws Exception {

		int no_of_split=cgi.search_hash_depth_split;
		int length_of_split=(1639/no_of_split)+1;
		

		ArrayList <child> search_sps=new ArrayList <child>();

		child c=null;
		for (Iterator iterator = children.iterator(); iterator.hasNext();) {
			c = (child) iterator.next();
			if ( c.getPort_no() == udpPack.getHeader_port_no() && c.getIP_Addr() == udpPack.getHeader_IP()){
				break;
			}
		}
		if (c == null){
			return;
		}
		//System.out.println("I got my chile " + no_of_split + " " + length_of_split);
		
		if (c.getSearch_no()==udpPack.getID()){
			c.setSearch_hash_portion(c.getSearch_hash_portion()+1);
		//	System.out.println("Increasing the get s hash port now its "+ c.getSearch_hash_portion());
		}
		else {
			c.setSearch_hash_portion(1);
			c.setSearch_no(udpPack.getID());
		//	System.out.println("resetting the  hash port now"); 
			search_sps.add(new child(cgi.myAddr, cgi.myPort));
		}

		hash_coord hc = my_hspace.get(0);
		for (Iterator iterator = hc.neighbours.iterator(); iterator.hasNext();) {
			speers sp = (speers) iterator.next();
			//System.out.println("checking this guy " + sp.getIP_Addr() + " " + sp.getPort_no() + " hash port  " + c.getSearch_hash_portion() );
			if (  (length_of_split*c.getSearch_hash_portion() < sp.getLower()[0]) || (length_of_split*(c.getSearch_hash_portion()-1) > sp.getHigher()[0]) ){
//			if ((sp.getHigher()[0] > length_of_split*c.getSearch_hash_portion() && sp.getLower()[0] > length_of_split*c.getSearch_hash_portion()) ||
//					(sp.getHigher()[0] < (length_of_split-1)*c.getSearch_hash_portion() && sp.getLower()[0] < (length_of_split-1)*c.getSearch_hash_portion())){
				continue;
			}
			if ( sp.getLower()[0] >=  (length_of_split*(c.getSearch_hash_portion()-1)) &&  sp.getLower()[0] < length_of_split*c.getSearch_hash_portion() ){
				search_sps.add(new child(sp.getIP_Addr(), sp.getPort_no()));
			}
				//System.out.println("adding this guy " + sp.getIP_Addr() + " " + sp.getPort_no() );

		}
		
		if (c.getSearch_hash_portion()==no_of_split){
			c.setSearch_hash_portion(0);
			///send packet to change the button to search from next
		//	System.out.println("send the packet for reset");
		
			pack udp_pack = new pack((byte) 123, (int) udpPack.getID(), (byte) 16, cgi.myAddr,cgi.myPort, 0, new byte[]{});
			DatagramPacket pack = new DatagramPacket(udp_pack.getPacket(), udp_pack.getPacket().length, udpPack.getHeader_IP(), udpPack.getHeader_port_no());
			try {
				cgi.pocket.send(pack);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		
		}
		
		
		
		for (Iterator iterator = search_sps.iterator(); iterator.hasNext();) {
			child child = (child) iterator.next();
		//	System.out.println("sending to this guy " + child.getIP_Addr() + " " + child.getPort_no() );
			InetAddress ia = child.getIP_Addr();
			int port =child.getPort_no();

			udpPack.setTTL((byte)(udpPack.getTTL()-1));
			DatagramPacket pack = new DatagramPacket(udpPack.getPacket(), udpPack.getPacket().length,ia,port );
			cgi.pocket.send(pack);
		}
	}
}



class BitCounter {

	private byte[] data;
	private int count;
	private int bitCount;

	public BitCounter(byte[] data)
	{
		this.data = data;
		this.count = 0;
		this.bitCount = 0;
	}


	public boolean isEnd()
	{
		return count==data.length;
	}

	public int nextBit()
	{
		byte d = data[count];
		int bit = (d & (1 << bitCount)) >>> bitCount;

		bitCount++;

		if(bitCount==8)
		{
			bitCount = 0;
			count++;
		}

		return bit;
	}
}


class child {

	InetAddress IP_Addr;
	int port_no;
	int search_no;
	int search_hash_portion;

	public child(InetAddress headerIP, int headerPortNo) {
		this.IP_Addr = headerIP;
		this.port_no = headerPortNo;
	}



	public void setSearch_no(int searchNo) {
		search_no = searchNo;
	}



	public void setSearch_hash_portion(int searchHashPortion) {
		search_hash_portion = searchHashPortion;
	}



	public int getSearch_no() {
		return search_no;
	}

	public int getSearch_hash_portion() {
		return search_hash_portion;
	}


	public InetAddress getIP_Addr() {
		return IP_Addr;
	}

	public void setIP_Addr(InetAddress iPAddr) {
		IP_Addr = iPAddr;
	}

	public int getPort_no() {
		return port_no;
	}

	public void setPort_no(int portNo) {
		port_no = portNo;
	}




}


class speers {

	InetAddress IP_Addr;
	int port_no;
	public int[] lower  = new int[5];
	public int[] higher = new int[5];

	public genfunc gfns = new genfunc();

	public speers(InetAddress ipAddr, int Port) {
		this.IP_Addr=ipAddr;
		this.port_no=Port;
	}
	//hash_coord hspace_cord;

	public speers() {
		// TODO Auto-generated constructor stub
	}

	public speers(byte[] bary) {

		byte[] tmpa = new byte[2];
		byte[] tmpa4 = new byte[4];

		int index=0;

		System.arraycopy(bary, index, tmpa4 , 0, 4);
		try {
			IP_Addr = InetAddress.getByAddress(tmpa4);
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		index+=4;

		System.arraycopy(bary, index, tmpa , 0, 2);
		port_no = gfns.convBaryInt(tmpa);
		index+=2;

		//thc=new hash_coord();
		for (int j = 0; j < lower.length; j++) {
			System.arraycopy(bary, index, tmpa, 0 , 2);
			index+=2;
			lower[j]=gfns.convBaryInt(tmpa);
		}

		for (int j = 0; j < higher.length; j++) {
			System.arraycopy(bary, index, tmpa, 0 , 2);
			index+=2;
			higher[j]=gfns.convBaryInt(tmpa);
		}		
	}

	public InetAddress getIP_Addr() {
		return IP_Addr;
	}
	public void setIP_Addr(InetAddress iPAddr) {
		IP_Addr = iPAddr;
	}
	public int getPort_no() {
		return port_no;
	}
	public void setPort_no(int portNo) {
		port_no = portNo;
	}
	//	public hash_coord getHspace_cord() {
	//		return hspace_cord;
	//	}
	//	public void setHspace_cord(hash_coord hspaceCord) {
	//		hspace_cord = hspaceCord;
	//	}
	public int[] getLower() {
		return lower;
	}
	public void setLower(int[] lower) {
		System.arraycopy(lower, 0, this.lower, 0, lower.length);
	}
	public int[] getHigher() {
		return higher;
	}
	public void setHigher(int[] higher) {
		System.arraycopy(higher, 0, this.higher, 0, higher.length);
	}


	public byte[] get_in_bytes() {


		byte[] bary= new byte[26];
		int index=0;
		int[] temp=new int[5];

		System.arraycopy(this.IP_Addr.getAddress(),0,bary,index,4);
		System.arraycopy(gfns.convIntBary_2(this.port_no),0,bary,index+4,2);
		index+=6;

		System.arraycopy(this.lower,0,temp,0,5);
		for (int i = 0; i < temp.length; i++) {
			System.arraycopy(gfns.convIntBary_2(temp[i]), 0, bary, index, 2);
			index+=2;
		}

		System.arraycopy(this.higher,0,temp,0,5);
		for (int i = 0; i < temp.length; i++) {
			System.arraycopy(gfns.convIntBary_2(temp[i]), 0, bary, index, 2);
			index+=2;
		}

		return bary;
	}

}



class hash_coord {

	public int[] lower  = new int[5];
	public int[] higher = new int[5];

	public ArrayList <speers> neighbours=new ArrayList <speers>();

	public int files;
	public genfunc gfns = new genfunc();

	public int getFiles() {
		return files;
	}
	public byte[] get_in_bytes() {

		byte[] bary= new byte[22 + 1 + (neighbours.size() *26) ];

		for (int i = 0; i < lower.length; i++) {
			System.arraycopy(gfns.convIntBary_2(lower[i]), 0, bary, i*2, 2);
		}
		for (int i = 0; i < higher.length; i++) {
			System.arraycopy(gfns.convIntBary_2(higher[i]), 0, bary, i*2+10, 2);
		}
		System.arraycopy(gfns.convIntBary_2(files), 0, bary, 20, 2);
		int index=22;

		bary[index]=(byte)neighbours.size();

		index++;


		int[] temp=new int[5];
		for (Iterator iterator = neighbours.iterator(); iterator.hasNext();) {
			speers sp = (speers) iterator.next();
			System.arraycopy(sp.get_in_bytes(), 0, bary, index, 26);
			index+=26;
			//			System.arraycopy(sp.getIP_Addr().getAddress(),0,bary,index,4);
			//			System.arraycopy(gfns.convIntBary_2(sp.getPort_no()),0,bary,index+4,2);
			//			index+=6;
			//
			//			System.arraycopy(sp.getLower(),0,temp,0,5);
			//			for (int i = 0; i < temp.length; i++) {
			//				System.arraycopy(gfns.convIntBary_2(temp[i]), 0, bary, index, 2);
			//				index+=2;
			//			}
			//
			//			System.arraycopy(sp.getHigher(),0,temp,0,5);
			//			for (int i = 0; i < temp.length; i++) {
			//				System.arraycopy(gfns.convIntBary_2(temp[i]), 0, bary, index, 2);
			//				index+=2;
			//			}
		}
		return bary;
	}


	public void setFiles(int files) {
		this.files = files;
	}
	public int[] getLower() {
		return lower;
	}
	public void setLower(int[] lower) {
		System.arraycopy(lower, 0, this.lower, 0, lower.length);
		//this.lower = lower;
	}
	public int[] getHigher() {
		return higher;
	}
	public void setHigher(int[] higher) {
		System.arraycopy(higher, 0, this.higher, 0, higher.length);
		//this.higher = higher;
	}

}